package com.sporty.homework.event_publisher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporty.homework.event_publisher.dto.EventScoreMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${kafka.topic.event-scores:event-scores}")
    private String eventScoresTopic;

    public void sendEventScore(String eventId, String currentScore) {
        try {
            // Create the DTO object
            EventScoreMessageDto message = new EventScoreMessageDto(eventId, currentScore);
            
            // Convert to JSON string
            String messageJson = objectMapper.writeValueAsString(message);

            // Send to Kafka topic
            kafkaTemplate.send(eventScoresTopic, eventId, messageJson);
            
            log.info("Sent score update to Kafka topic '{}': {}", eventScoresTopic, messageJson);
        } catch (Exception e) {
            log.error("Error sending message to Kafka", e);
        }
    }
}