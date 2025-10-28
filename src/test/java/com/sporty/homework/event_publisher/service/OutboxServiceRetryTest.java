package com.sporty.homework.event_publisher.service;

import com.sporty.homework.event_publisher.dao.MessageDao;
import com.sporty.homework.event_publisher.enums.MessageStatus;
import com.sporty.homework.event_publisher.model.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceRetryTest {

    @Test
    void shouldMarkMessageAsPermanentlyFailedAfterMaxRetries() {
        // Given: A message that fails to send to Kafka consistently
        String eventId = "test-event";
        String currentScore = "1:0";
        Long messageId = 1L;
        
        // Setup mocks
        MessageDao messageDao = mock(MessageDao.class);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        
        OutboxService outboxService = new OutboxService(messageDao, kafkaTemplate);
        ReflectionTestUtils.setField(outboxService, "eventScoresTopic", "test-event-scores");
        
        // Setup the DAO mock to return a message marked as FAILED with max retry count
        Message failedMessage = new Message();
        failedMessage.setId(messageId);
        failedMessage.setEventId(eventId);
        failedMessage.setPayload("{\"eventId\":\"test-event\",\"currentScore\":\"1:0\"}");
        failedMessage.setStatus(MessageStatus.FAILED);
        failedMessage.setRetryCount(5); // At max retry count
        
        // Configure the Kafka template to always fail
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedFuture);
        
        // Configure DAO methods
        when(messageDao.findFailedMessages()).thenReturn(List.of(failedMessage));
        when(messageDao.findPendingMessages()).thenReturn(List.of());
        
        // When: The processPendingMessages method is called (which also processes failed messages)
        outboxService.processPendingMessages();
        
        // Then: The message should be marked as PERMANENTLY_FAILED
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<MessageStatus> statusCaptor = ArgumentCaptor.forClass(MessageStatus.class);
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        
        verify(messageDao).updateMessageStatus(idCaptor.capture(), statusCaptor.capture(), timeCaptor.capture());
        
        assertEquals(messageId, idCaptor.getValue());
        assertEquals(MessageStatus.PERMANENTLY_FAILED, statusCaptor.getValue());
    }

    @Test
    void shouldRetryFailedMessageAndEventuallyMarkAsPermanentlyFailed() {
        // Given: A message that initially fails to send to Kafka
        String eventId = "test-event";
        String currentScore = "1:0";
        Long messageId = 2L;  // Use different ID to avoid conflicts
        
        // Setup mocks
        MessageDao messageDao = mock(MessageDao.class);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        
        OutboxService outboxService = new OutboxService(messageDao, kafkaTemplate);
        ReflectionTestUtils.setField(outboxService, "eventScoresTopic", "test-event-scores");
        
        // Setup message with retry count < max retries (5)
        Message failedMessage = new Message();
        failedMessage.setId(messageId);
        failedMessage.setEventId(eventId);
        failedMessage.setPayload("{\"eventId\":\"test-event\",\"currentScore\":\"1:0\"}");
        failedMessage.setStatus(MessageStatus.FAILED);
        failedMessage.setRetryCount(3); // Less than max retry count

        // Configure the Kafka template to always fail
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedFuture);
        
        // Configure DAO methods
        when(messageDao.findFailedMessages()).thenReturn(List.of(failedMessage));
        when(messageDao.findPendingMessages()).thenReturn(List.of());
        
        // When: The processPendingMessages method is called
        outboxService.processPendingMessages();
        
        // Then: The message should still be marked as FAILED (not permanently failed yet)
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<MessageStatus> statusCaptor = ArgumentCaptor.forClass(MessageStatus.class);
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        
        verify(messageDao).markMessageAsFailed(idCaptor.capture(), statusCaptor.capture(), timeCaptor.capture());
        
        assertEquals(messageId, idCaptor.getValue());
        assertEquals(MessageStatus.FAILED, statusCaptor.getValue());
    }

    @Test
    void shouldRetryFailedMessageSuccessfullyAfterMultipleFailures() {
        // Given: A message that was previously failed but will now succeed
        String eventId = "test-event";
        String currentScore = "1:0";
        Long messageId = 3L; // Use different ID to avoid conflicts
        
        // Setup mocks
        MessageDao messageDao = mock(MessageDao.class);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        
        OutboxService outboxService = new OutboxService(messageDao, kafkaTemplate);
        ReflectionTestUtils.setField(outboxService, "eventScoresTopic", "test-event-scores");
        
        // Setup message with retry count < max retries (5)
        Message failedMessage = new Message();
        failedMessage.setId(messageId);
        failedMessage.setEventId(eventId);
        failedMessage.setPayload("{\"eventId\":\"test-event\",\"currentScore\":\"1:0\"}");
        failedMessage.setStatus(MessageStatus.FAILED);
        failedMessage.setRetryCount(2); // Less than max retry count

        // Configure the Kafka template to succeed this time
        org.apache.kafka.clients.producer.RecordMetadata recordMetadata = mock(org.apache.kafka.clients.producer.RecordMetadata.class);
        SendResult<String, String> sendResult = new SendResult<>(null, recordMetadata);
        CompletableFuture<SendResult<String, String>> successfulFuture = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(successfulFuture);
        
        // Configure DAO methods
        when(messageDao.findFailedMessages()).thenReturn(List.of(failedMessage));
        when(messageDao.findPendingMessages()).thenReturn(List.of());
        
        // When: The processPendingMessages method is called
        outboxService.processPendingMessages();
        
        // Then: The message should be marked as SENT
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<MessageStatus> statusCaptor = ArgumentCaptor.forClass(MessageStatus.class);
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        
        verify(messageDao).updateMessageStatus(idCaptor.capture(), statusCaptor.capture(), timeCaptor.capture());
        
        assertEquals(messageId, idCaptor.getValue());
        assertEquals(MessageStatus.SENT, statusCaptor.getValue());
    }

    @Test
    void shouldHandleKafkaTimeoutAndMarkAsFailed() {
        // Given: Kafka template that times out
        String eventId = "test-event";
        String currentScore = "1:0";
        
        // Setup mocks
        MessageDao messageDao = mock(MessageDao.class);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        
        OutboxService outboxService = new OutboxService(messageDao, kafkaTemplate);
        ReflectionTestUtils.setField(outboxService, "eventScoresTopic", "test-event-scores");
        
        // Configure the Kafka template to timeout
        CompletableFuture<SendResult<String, String>> timeoutFuture = new CompletableFuture<>();
        timeoutFuture.completeExceptionally(new TimeoutException("Kafka timeout"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(timeoutFuture);
        
        // When: saveMessageAndSendToKafka is called
        outboxService.saveMessageAndSendToKafka(eventId, currentScore);
        
        // Then: The message should be marked as failed
        verify(messageDao).markMessageAsFailed(anyLong(), eq(MessageStatus.FAILED), any(LocalDateTime.class));
    }

    @Test
    void shouldHandleKafkaExecutionExceptionAndMarkAsFailed() {
        // Given: Kafka template that throws ExecutionException
        String eventId = "test-event";
        String currentScore = "1:0";
        
        // Setup mocks
        MessageDao messageDao = mock(MessageDao.class);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        
        OutboxService outboxService = new OutboxService(messageDao, kafkaTemplate);
        ReflectionTestUtils.setField(outboxService, "eventScoresTopic", "test-event-scores");
        
        // Configure the Kafka template to fail with ExecutionException
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new ExecutionException(new RuntimeException("Kafka error")));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedFuture);
        
        // When: saveMessageAndSendToKafka is called
        outboxService.saveMessageAndSendToKafka(eventId, currentScore);
        
        // Then: The message should be marked as failed
        verify(messageDao).markMessageAsFailed(anyLong(), eq(MessageStatus.FAILED), any(LocalDateTime.class));
    }
}