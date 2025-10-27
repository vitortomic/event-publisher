package com.sporty.homework.event_publisher.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext
class KafkaMessageDeliveryFunctionalTest {

    @Value("${spring.kafka.producer.bootstrap-servers:localhost:9092}")
    private String kafkaUrl;

    @Test
    void testKafkaProducerCanSendMessage() {
        String topic = "event-scores";
        String key = "test-key-123";
        String value = "{\"eventId\":\"test-key-123\",\"currentScore\":\"1:0\"}";
        
        // Create producer properties
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        
        // Test that we can create a producer and send a message
        assertDoesNotThrow(() -> {
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
                producer.send(record);
                producer.flush();
            }
        });
    }
    
    @Test
    void testKafkaConsumerCanReceiveMessage() {
        String topic = "event-scores";
        String key = "consumer-test-456";
        String value = "{\"eventId\":\"consumer-test-456\",\"currentScore\":\"2:1\"}";
        
        // Create producer to send a message
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, 0,  key, value);
            producer.send(record);
            producer.flush();
        }
        
        // Create consumer properties
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        
        // Test that we can create a consumer and receive the message
        org.apache.kafka.clients.consumer.KafkaConsumer<String, String> consumer = 
            new org.apache.kafka.clients.consumer.KafkaConsumer<>(consumerProps);
            
        consumer.subscribe(java.util.Collections.singletonList(topic));
        
        // Poll for messages
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
        
        // Check if we received any messages
        assertFalse(records.isEmpty(), "Should have received at least one message");
        
        // Verify the content of at least one message
        boolean found = false;
        for (ConsumerRecord<String, String> record : records) {
            if (key.equals(record.key()) && value.equals(record.value())) {
                found = true;
                break;
            }
        }
        
        assertTrue(found, "Should have received the message we sent");
        consumer.close();
    }
    
    @Test
    void testMultipleMessagesCanBeSentAndReceived() {
        String topic = "event-scores";
        String[] keys = {"multi-test-1", "multi-test-2", "multi-test-3"};
        String[] values = {
            "{\"eventId\":\"multi-test-1\",\"currentScore\":\"0:0\"}",
            "{\"eventId\":\"multi-test-2\",\"currentScore\":\"1:1\"}",
            "{\"eventId\":\"multi-test-3\",\"currentScore\":\"2:2\"}"
        };
        
        // Send multiple messages
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            for (int i = 0; i < keys.length; i++) {
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, keys[i], values[i]);
                producer.send(record);
            }
            producer.flush();
        }
        
        // Receive and verify messages
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "multi-test-group-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        
        org.apache.kafka.clients.consumer.KafkaConsumer<String, String> consumer = 
            new org.apache.kafka.clients.consumer.KafkaConsumer<>(consumerProps);
            
        consumer.subscribe(java.util.Collections.singletonList(topic));
        
        // Poll for messages (might need multiple polls to get all messages)
        int receivedCount = 0;
        long endTime = System.currentTimeMillis() + 10000; // 10 second timeout
        
        while (receivedCount < keys.length && System.currentTimeMillis() < endTime) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            receivedCount += records.count();
        }
        
        assertTrue(receivedCount >= keys.length, "Should have received all messages");
        consumer.close();
    }
}