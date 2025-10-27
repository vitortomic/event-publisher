package com.sporty.homework.event_publisher.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporty.homework.event_publisher.dao.EventDao;
import com.sporty.homework.event_publisher.dao.MessageDao;
import com.sporty.homework.event_publisher.dto.CreateEventDto;
import com.sporty.homework.event_publisher.dto.EventScoreMessageDto;
import com.sporty.homework.event_publisher.dto.UpdateEventStatusDto;
import com.sporty.homework.event_publisher.enums.EventStatus;
import com.sporty.homework.event_publisher.model.Event;
import com.sporty.homework.event_publisher.model.Message;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventPublishingEndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EventDao eventDao;

    @Autowired
    private MessageDao messageDao;

    @Value("${kafka.topic.event-scores}")
    private String eventScoresTopic;

    private static KafkaConsumer<String, String> consumer;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // These properties should match your docker-compose.yml setup
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/event_publisher");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("kafka.topic.event-scores", () -> "event-scores");
    }

    @BeforeAll
    static void setUpKafkaConsumer() {
        // Set up a Kafka consumer to receive messages
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-test-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList("event-scores"));
    }

    @AfterAll
    static void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    @Order(1)
    void testCreateEventSuccessfully() {
        // Given
        String eventId = "e2e-test-event-123";
        CreateEventDto createEventDto = new CreateEventDto(eventId, EventStatus.LIVE);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity("/events", createEventDto, String.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Event created successfully"));

        // Verify the event was saved to the database
        Event savedEvent = eventDao.findByEventId(eventId);
        assertNotNull(savedEvent);
        assertEquals(eventId, savedEvent.getEventId());
        assertEquals("LIVE", savedEvent.getStatus().name());
    }

    @Test
    @Order(2)
    void testUpdateEventStatusAndVerifyMessageInDatabase() throws InterruptedException {
        // Given
        String eventId = "e2e-test-event-123";
        UpdateEventStatusDto updateEventStatusDto = new UpdateEventStatusDto(EventStatus.LIVE);

        // When
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UpdateEventStatusDto> requestEntity = new HttpEntity<>(updateEventStatusDto, headers);
        ResponseEntity<String> response = restTemplate.exchange("/events/{id}/status", HttpMethod.PUT, requestEntity, String.class, eventId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Event status updated successfully"));

        // Verify the event status was updated in the database
        Event updatedEvent = eventDao.findByEventId(eventId);
        assertNotNull(updatedEvent);
        assertEquals("LIVE", updatedEvent.getStatus().name());

        // Wait a bit for the outbox processor to handle the message
        Thread.sleep(3000);

        // Verify that a message was created in the outbox
        List<Message> pendingMessages = messageDao.findPendingMessages(100);
        Optional<Message> messageForEvent = pendingMessages.stream()
                .filter(msg -> msg.getPayload().contains(eventId))
                .findFirst();

        // The message should no longer be in PENDING status (it should be SENT or FAILED)
        assertTrue(messageForEvent.isEmpty() || !"PENDING".equals(messageForEvent.get().getStatus()),
                "Message should no longer be in PENDING status");

        // Check for messages with SENT status that match our event
        List<Message> sentMessages = messageDao.findMessagesByStatus("SENT", 100);
        Message sentMessage = sentMessages.stream()
                .filter(msg -> msg.getPayload().contains(eventId))
                .findFirst()
                .orElse(null);

        assertNotNull(sentMessage, "Message should exist with SENT status");
        assertEquals("EVENT_SCORE_UPDATE", sentMessage.getEventType());
        assertTrue(sentMessage.getPayload().contains("\"0:0\""), "Payload should contain initial score '0:0'");
    }

    @Test
    @Order(3)
    void testKafkaMessageConsumption() throws InterruptedException {
        // Given
        String eventId = "e2e-test-event-123";
        String expectedScore = "0:0";

        // Send another message to Kafka to verify we can consume it
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            String payload = "{\"eventId\":\"" + eventId + "\",\"currentScore\":\"" + expectedScore + "\"}";
            ProducerRecord<String, String> record = new ProducerRecord<>("event-scores", eventId, payload);
            producer.send(record);
            producer.flush();
        }

        // When - poll for messages from Kafka
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        // Then - verify we received the message
        assertFalse(records.isEmpty(), "Should have received at least one message from Kafka");

        boolean found = false;
        for (ConsumerRecord<String, String> record : records) {
            if (eventId.equals(record.key())) {
                found = true;
                assertTrue(record.value().contains(eventId), "Message value should contain the event ID");
                assertTrue(record.value().contains(expectedScore), "Message value should contain the score");
                break;
            }
        }

        assertTrue(found, "Should have found message with matching event ID");
    }

    @Test
    @Order(4)
    void testCompleteEndToEndFlow() throws Exception {
        // Step 1: Create a new event
        String eventId = "e2e-complete-flow-456";
        CreateEventDto createEventDto = new CreateEventDto(eventId, EventStatus.NOT_LIVE);
        
        ResponseEntity<String> createResponse = restTemplate.postForEntity("/events", createEventDto, String.class);
        assertEquals(HttpStatus.OK, createResponse.getStatusCode());
        assertTrue(createResponse.getBody().contains("Event created successfully"));

        // Step 2: Update the event to LIVE status
        UpdateEventStatusDto updateEventStatusDto = new UpdateEventStatusDto(EventStatus.LIVE);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UpdateEventStatusDto> requestEntity = new HttpEntity<>(updateEventStatusDto, headers);
        ResponseEntity<String> updateResponse = restTemplate.exchange("/events/{id}/status", HttpMethod.PUT, requestEntity, String.class, eventId);
        
        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());
        assertTrue(updateResponse.getBody().contains("Event status updated successfully"));

        // Step 3: Verify event status in database
        Event updatedEvent = eventDao.findByEventId(eventId);
        assertNotNull(updatedEvent);
        assertEquals("LIVE", updatedEvent.getStatus().name());

        // Step 4: Wait for outbox processing
        Thread.sleep(3000);

        // Step 5: Verify message in database with SENT status
        List<Message> sentMessages = messageDao.findMessagesByStatus("SENT", 100);
        Message messageForEvent = sentMessages.stream()
                .filter(msg -> msg.getPayload().contains(eventId))
                .findFirst()
                .orElse(null);

        assertNotNull(messageForEvent, "Message should exist with SENT status for the event");
        assertEquals("EVENT_SCORE_UPDATE", messageForEvent.getEventType());
        assertTrue(messageForEvent.getPayload().contains("\"0:0\""), "Message payload should contain initial score '0:0'");

        // Step 6: Send a score update message to verify Kafka consumption
        String updatedScore = "1:0";
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            String payload = "{\"eventId\":\"" + eventId + "\",\"currentScore\":\"" + updatedScore + "\"}";
            ProducerRecord<String, String> record = new ProducerRecord<>("event-scores", eventId, payload);
            producer.send(record);
            producer.flush();
        }

        // Step 7: Consume and verify the message from Kafka
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
        assertFalse(records.isEmpty(), "Should have received messages from Kafka");

        boolean found = false;
        for (ConsumerRecord<String, String> record : records) {
            if (eventId.equals(record.key()) && record.value().contains(updatedScore)) {
                found = true;
                
                // Parse the JSON payload to verify structure
                try {
                    EventScoreMessageDto messageDto = objectMapper.readValue(record.value(), EventScoreMessageDto.class);
                    assertEquals(eventId, messageDto.getEventId());
                    assertEquals(updatedScore, messageDto.getCurrentScore());
                } catch (Exception e) {
                    fail("Could not parse message payload as JSON: " + record.value());
                }
                break;
            }
        }

        assertTrue(found, "Should have found message with matching event ID and score");
    }
}