package com.sporty.homework.event_publisher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporty.homework.event_publisher.dao.MessageDao;
import com.sporty.homework.event_publisher.dto.EventScoreMessageDto;
import com.sporty.homework.event_publisher.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private MessageDao messageDao;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new OutboxService(messageDao, kafkaTemplate);
        // Set the topic name for testing
        ReflectionTestUtils.setField(outboxService, "eventScoresTopic", "event-scores");
    }

    @Test
    void shouldSaveMessageToOutboxAndSendToKafkaSuccessfully() {
        // Given
        String eventId = "event-123";
        String currentScore = "1:0";
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        // Mock successful Kafka send
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                    new org.springframework.kafka.support.SendResult<String, String>(null, null)));

        // When
        outboxService.saveMessageAndSendToKafka(eventId, currentScore);

        // Then
        verify(messageDao).insertMessage(messageCaptor.capture());
        Message savedMessage = messageCaptor.getValue();
        assertEquals("EVENT_SCORE_UPDATE", savedMessage.getEventType());
        assertEquals("PENDING", savedMessage.getStatus());
        assertTrue(savedMessage.getCreatedAt() != null);

        // Verify Kafka was called and status was updated to SENT
        verify(kafkaTemplate).send(eq("event-scores"), eq(eventId), anyString());
        verify(messageDao).updateMessageStatus(anyLong(), eq("SENT"), any(LocalDateTime.class));
    }

    @Test
    void shouldSaveMessageToOutboxAndMarkAsFailedWhenKafkaSendFails() {
        // Given
        String eventId = "event-123";
        String currentScore = "1:0";
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        // Mock failed Kafka send
        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> failedFuture = new CompletableFuture<>();
        RuntimeException exception = new RuntimeException("Kafka connection failed");
        failedFuture.completeExceptionally(exception);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failedFuture);

        // When
        outboxService.saveMessageAndSendToKafka(eventId, currentScore);

        // Then
        verify(messageDao).insertMessage(messageCaptor.capture());
        Message savedMessage = messageCaptor.getValue();
        assertEquals("EVENT_SCORE_UPDATE", savedMessage.getEventType());
        assertEquals("PENDING", savedMessage.getStatus());

        // Verify Kafka was called and status was marked as FAILED
        verify(kafkaTemplate).send(eq("event-scores"), eq(eventId), anyString());
        verify(messageDao).markMessageAsFailed(anyLong(), eq("FAILED"), any(LocalDateTime.class));
    }

    @Test
    void shouldProcessPendingMessagesSuccessfully() {
        // Given
        Message pendingMessage = new Message();
        pendingMessage.setId(1L);
        pendingMessage.setPayload("{\"eventId\":\"event-123\",\"currentScore\":\"2:1\"}");
        pendingMessage.setEventType("EVENT_SCORE_UPDATE");
        
        when(messageDao.findPendingMessages(100)).thenReturn(Arrays.asList(pendingMessage));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                    new org.springframework.kafka.support.SendResult<String, String>(null, null)));

        // When
        outboxService.processPendingMessages();

        // Then
        verify(messageDao).updateMessageStatus(eq(1L), eq("SENT"), any(LocalDateTime.class));
    }

    @Test
    void shouldRetryFailedMessagesSuccessfully() {
        // Given
        Message failedMessage = new Message();
        failedMessage.setId(2L);
        failedMessage.setPayload("{\"eventId\":\"event-456\",\"currentScore\":\"0:0\"}");
        failedMessage.setEventType("EVENT_SCORE_UPDATE");
        failedMessage.setStatus("FAILED");
        failedMessage.setRetryCount(1);
        
        when(messageDao.findFailedMessages(100)).thenReturn(Arrays.asList(failedMessage));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                    new org.springframework.kafka.support.SendResult<String, String>(null, null)));

        // When
        outboxService.processPendingMessages();

        // Then
        verify(messageDao).updateMessageStatus(eq(2L), eq("SENT"), any(LocalDateTime.class));
    }

    @Test
    void shouldHandleMultipleEventScoreUpdatesCorrectly() {
        // Given
        String eventId1 = "event-1";
        String currentScore1 = "0:0";
        String eventId2 = "event-2";
        String currentScore2 = "1:1";

        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                    new org.springframework.kafka.support.SendResult<String, String>(null, null)));

        // When
        outboxService.saveMessageAndSendToKafka(eventId1, currentScore1);
        outboxService.saveMessageAndSendToKafka(eventId2, currentScore2);

        // Then
        verify(messageDao, times(2)).insertMessage(any(Message.class));
        verify(messageDao, times(2)).updateMessageStatus(anyLong(), eq("SENT"), any(LocalDateTime.class));
    }

    @Test
    void shouldHandleKafkaTimeoutCorrectly() {
        // Given
        String eventId = "event-123";
        String currentScore = "1:0";
        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> timeoutFuture = new CompletableFuture<>();
        timeoutFuture.completeExceptionally(new java.util.concurrent.TimeoutException("Kafka send timeout"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(timeoutFuture);

        // When
        outboxService.saveMessageAndSendToKafka(eventId, currentScore);

        // Then
        verify(messageDao).insertMessage(any(Message.class));
        verify(messageDao).markMessageAsFailed(anyLong(), eq("FAILED"), any(LocalDateTime.class));
    }
}