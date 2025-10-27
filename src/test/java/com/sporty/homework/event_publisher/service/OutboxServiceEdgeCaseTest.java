package com.sporty.homework.event_publisher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporty.homework.event_publisher.dao.MessageDao;
import com.sporty.homework.event_publisher.dto.EventScoreMessageDto;
import com.sporty.homework.event_publisher.model.Message;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceEdgeCaseTest {

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
    void shouldNotSaveMessageWhenEventIdIsNull() {
        // Given
        String eventId = null;
        String currentScore = "1:0";

        // When
        outboxService.saveMessageAndSendToKafka(eventId, currentScore);

        // Then
        verify(messageDao, never()).insertMessage(any(Message.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shouldNotSaveMessageWhenEventIdIsEmpty() {
        // Given
        String eventId = "";
        String currentScore = "1:0";

        // When
        outboxService.saveMessageAndSendToKafka(eventId, currentScore);

        // Then
        verify(messageDao, never()).insertMessage(any(Message.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shouldNotSaveMessageWhenEventIdIsWhitespace() {
        // Given
        String eventId = "   ";
        String currentScore = "1:0";

        // When
        outboxService.saveMessageAndSendToKafka(eventId, currentScore);

        // Then
        verify(messageDao, never()).insertMessage(any(Message.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shouldNotSaveMessageWhenScoreFormatIsInvalid() {
        // Given
        String eventId = "event-123";
        String currentScore = "invalid-score";

        // When
        outboxService.saveMessageAndSendToKafka(eventId, currentScore);

        // Then
        verify(messageDao, never()).insertMessage(any(Message.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shouldNotSaveMessageWhenScoreFormatDoesNotMatchXColonY() {
        // Given
        String eventId = "event-123";
        String currentScore = "1-0"; // Wrong format, should be "1:0"

        // When
        outboxService.saveMessageAndSendToKafka(eventId, currentScore);

        // Then
        verify(messageDao, never()).insertMessage(any(Message.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shouldMarkMessageAsPermanentlyFailedAfterMaxRetries() {
        // Given
        Message failedMessage = new Message();
        failedMessage.setId(1L);
        failedMessage.setPayload("{\"eventId\":\"event-123\",\"currentScore\":\"2:1\"}");
        failedMessage.setEventType("EVENT_SCORE_UPDATE");
        failedMessage.setStatus("FAILED");
        failedMessage.setRetryCount(5); // At max retry count
        
        when(messageDao.findFailedMessages(100)).thenReturn(Arrays.asList(failedMessage));
        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> failedFuture = new CompletableFuture<>();
        RuntimeException exception = new RuntimeException("Kafka connection failed");
        failedFuture.completeExceptionally(exception);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failedFuture);

        // When
        outboxService.processPendingMessages();

        // Then
        verify(messageDao).updateMessageStatus(eq(1L), eq("PERMANENTLY_FAILED"), any(LocalDateTime.class));
    }

    @Test
    void shouldNotExceedMaxRetriesAndMarkAsPermanentlyFailed() {
        // Given
        Message failedMessage = new Message();
        failedMessage.setId(2L);
        failedMessage.setPayload("{\"eventId\":\"event-456\",\"currentScore\":\"0:0\"}");
        failedMessage.setEventType("EVENT_SCORE_UPDATE");
        failedMessage.setStatus("FAILED");
        failedMessage.setRetryCount(6); // Beyond max retry count
        
        when(messageDao.findFailedMessages(100)).thenReturn(Arrays.asList(failedMessage));
        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> failedFuture = new CompletableFuture<>();
        RuntimeException exception = new RuntimeException("Kafka connection failed");
        failedFuture.completeExceptionally(exception);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failedFuture);

        // When
        outboxService.processPendingMessages();

        // Then
        verify(messageDao).updateMessageStatus(eq(2L), eq("PERMANENTLY_FAILED"), any(LocalDateTime.class));
    }
}