package com.sporty.homework.event_publisher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporty.homework.event_publisher.dao.MessageDao;
import com.sporty.homework.event_publisher.dto.EventScoreMessageDto;
import com.sporty.homework.event_publisher.enums.MessageStatus;
import com.sporty.homework.event_publisher.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final MessageDao messageDao;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${kafka.topic.event-scores:event-scores}")
    private String eventScoresTopic;

    @Transactional
    public void saveMessageAndSendToKafka(String eventId, String currentScore) {
        try {
            // Validate input parameters
            if (eventId == null || eventId.trim().isEmpty()) {
                log.error("Invalid event ID: {}", eventId);
                return;
            }
            
            if (currentScore == null || !currentScore.matches("^\\d+:\\d+$")) {
                log.error("Invalid score format: {}", currentScore);
                return;
            }

            // Create the message to be stored in the outbox
            EventScoreMessageDto eventScoreMessage = new EventScoreMessageDto(eventId, currentScore);
            String payload = objectMapper.writeValueAsString(eventScoreMessage);

            // Create and save the outbox message
            Message outboxMessage = new Message();
            outboxMessage.setEventId(eventId);
            outboxMessage.setEventType("EVENT_SCORE_UPDATE");
            outboxMessage.setPayload(payload);
            outboxMessage.setStatus(MessageStatus.PENDING);
            outboxMessage.setCreatedAt(LocalDateTime.now());
            outboxMessage.setRetryCount(0);

            Long messageId = messageDao.insertMessage(outboxMessage);
            log.info("Saved message to outbox for event: {} with ID: {}", eventId, messageId);

            // Attempt to send to Kafka and update status
            if (sendMessageToKafka(eventId, payload, messageId)) {
                messageDao.updateMessageStatus(messageId, MessageStatus.SENT, LocalDateTime.now());
                log.info("Successfully sent message to Kafka and updated status for event: {}", eventId);
            } else {
                messageDao.markMessageAsFailed(messageId, MessageStatus.FAILED, LocalDateTime.now());
                log.error("Failed to send message to Kafka for event: {}, saved to outbox for retry", eventId);
            }
        } catch (Exception e) {
            log.error("Error in outbox pattern for event: {}", eventId, e);
        }
    }

    private boolean sendMessageToKafka(String eventId, String payload, Long messageId) {
        try {
            // Send message to Kafka and wait for the result with timeout to ensure delivery
            var sendResult = kafkaTemplate.send(eventScoresTopic, eventId, payload).get(5, java.util.concurrent.TimeUnit.SECONDS);
            return sendResult.getRecordMetadata() != null;
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Timeout sending message to Kafka for message ID: {}", messageId, e);
            return false;
        } catch (ExecutionException e) {
            log.error("Failed to send message to Kafka for message ID: {}", messageId, e.getCause());
            return false;
        } catch (Exception e) {
            log.error("Failed to send message to Kafka for message ID: {}", messageId, e);
            return false;
        }
    }

    public void processPendingMessages() {
        // Process pending messages
        List<Message> pendingMessages = messageDao.findPendingMessages();
        for (Message message : pendingMessages) {
            processMessage(message);
        }

        // Process failed messages (with retry logic)
        List<Message> failedMessages = messageDao.findFailedMessages();
        for (Message message : failedMessages) {
            processMessage(message);
        }
    }

    private void processMessage(Message message) {
        try {
            if (sendMessageToKafka(extractEventId(message.getPayload()), message.getPayload(), message.getId())) {
                messageDao.updateMessageStatus(message.getId(), MessageStatus.SENT, LocalDateTime.now());
                log.info("Successfully sent previously failed message to Kafka with ID: {}", message.getId());
            } else {
                // Check retry count and update accordingly
                if (message.getRetryCount() < 5) { // Max 5 retries
                    messageDao.markMessageAsFailed(message.getId(), MessageStatus.FAILED, LocalDateTime.now());
                    log.warn("Failed to send message to Kafka after retry, ID: {}, retry count: {}", 
                             message.getId(), message.getRetryCount() + 1);
                } else {
                    // Mark as permanently failed after max retries
                    messageDao.updateMessageStatus(message.getId(), MessageStatus.PERMANENTLY_FAILED, LocalDateTime.now());
                    log.error("Message permanently failed after max retries, ID: {}", message.getId());
                }
            }
        } catch (Exception e) {
            log.error("Error processing message with ID: {}", message.getId(), e);
            // Check retry count before incrementing
            if (message.getRetryCount() < 5) {
                messageDao.markMessageAsFailed(message.getId(), MessageStatus.FAILED, LocalDateTime.now());
            } else {
                messageDao.updateMessageStatus(message.getId(), MessageStatus.PERMANENTLY_FAILED, LocalDateTime.now());
            }
        }
    }

    private String extractEventId(String payload) {
        try {
            // Parse the JSON payload to extract the event ID
            EventScoreMessageDto eventScoreMessage = objectMapper.readValue(payload, EventScoreMessageDto.class);
            return eventScoreMessage.getEventId();
        } catch (Exception e) {
            log.error("Failed to extract event ID from payload", e);
            return null;
        }
    }
}