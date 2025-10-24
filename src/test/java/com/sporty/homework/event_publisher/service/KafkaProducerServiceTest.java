package com.sporty.homework.event_publisher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaProducerServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaProducerService kafkaProducerService;

    @BeforeEach
    void setUp() {
        kafkaProducerService = new KafkaProducerService(kafkaTemplate);
        // Set the topic name for testing
        ReflectionTestUtils.setField(kafkaProducerService, "eventScoresTopic", "event-scores");
    }

    @Test
    void shouldSendMessageToKafkaTopicWhenEventScoreUpdated() {
        // Given
        String eventId = "event-123";
        String currentScore = "1:0";

        // When
        kafkaProducerService.sendEventScore(eventId, currentScore);

        // Then
        verify(kafkaTemplate).send(eq("event-scores"), eq(eventId), any(String.class));
    }

    @Test
    void shouldSerializeEventScoreMessageToCorrectJsonFormat() throws Exception {
        // Given
        String eventId = "event-456";
        String currentScore = "2:1";
        ObjectMapper objectMapper = new ObjectMapper();

        // When
        kafkaProducerService.sendEventScore(eventId, currentScore);

        // Then
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("event-scores"), eq(eventId), valueCaptor.capture());

        String jsonMessage = valueCaptor.getValue();
        assertTrue(jsonMessage.contains("\"eventId\":\"event-456\""));
        assertTrue(jsonMessage.contains("\"currentScore\":\"2:1\""));
    }

    @Test
    void shouldHandleMultipleEventScoreUpdatesCorrectly() {
        // Given
        String eventId1 = "event-1";
        String currentScore1 = "0:0";
        String eventId2 = "event-2";
        String currentScore2 = "1:1";

        // When
        kafkaProducerService.sendEventScore(eventId1, currentScore1);
        kafkaProducerService.sendEventScore(eventId2, currentScore2);

        // Then
        verify(kafkaTemplate, times(2)).send(eq("event-scores"), any(String.class), any(String.class));
    }

    @Test
    void shouldNotThrowExceptionWhenKafkaSendFails() {
        // Given
        String eventId = "event-123";
        String currentScore = "1:0";
        doThrow(new RuntimeException("Kafka connection failed")).when(kafkaTemplate)
                .send(any(String.class), any(String.class), any(String.class));

        // When & Then
        assertDoesNotThrow(() -> kafkaProducerService.sendEventScore(eventId, currentScore));
    }
}