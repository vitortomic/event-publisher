package com.sporty.homework.event_publisher.dao;

import com.sporty.homework.event_publisher.enums.EventStatus;
import com.sporty.homework.event_publisher.enums.MessageStatus;
import com.sporty.homework.event_publisher.model.Message;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class MessageDaoFunctionalTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MessageDao messageDao;

    @Autowired
    private EventDao eventDao;

    @Test
    void testInsertMessage() {
        eventDao.insertEvent("event-123", EventStatus.LIVE);
        // Create a message to insert
        Message message = new Message();
        message.setEventId("event-123");
        message.setEventType("EVENT_SCORE_UPDATE");
        message.setPayload("{\"eventId\":\"event-123\",\"currentScore\":\"1:0\"}");
        message.setStatus(MessageStatus.PENDING);
        message.setCreatedAt(LocalDateTime.now());
        message.setRetryCount(0);

        // Insert the message
        Long messageId = messageDao.insertMessage(message);

        // Verify the message was inserted with a valid ID
        assertNotNull(messageId);
        assertTrue(messageId > 0);

        // Check that the message exists in the database with correct values
        // This requires a select query which isn't directly available in MessageDao
        // We'll test this by trying to find pending messages and seeing if our message appears
        List<Message> pendingMessages = messageDao.findPendingMessages();
        assertTrue(pendingMessages.stream().anyMatch(m -> m.getId().equals(messageId)));
    }

    @Test
    void testUpdateMessageStatus() {
        eventDao.insertEvent("event-456", EventStatus.LIVE);
        // First insert a message
        Message message = new Message();
        message.setEventId("event-456");
        message.setEventType("EVENT_SCORE_UPDATE");
        message.setPayload("{\"eventId\":\"event-456\",\"currentScore\":\"2:1\"}");
        message.setStatus(MessageStatus.PENDING);
        message.setCreatedAt(LocalDateTime.now());
        message.setRetryCount(0);

        Long messageId = messageDao.insertMessage(message);
        assertNotNull(messageId);

        // Update the message status
        LocalDateTime now = LocalDateTime.now();
        messageDao.updateMessageStatus(messageId, MessageStatus.SENT, now);

        // Verify the update by finding pending messages (should not include our updated message)
        List<Message> pendingMessages = messageDao.findPendingMessages();
        assertFalse(pendingMessages.stream().anyMatch(m -> m.getId().equals(messageId)));
    }

    @Test
    void testMarkMessageAsFailed() {
        eventDao.insertEvent("event-789", EventStatus.LIVE);
        // First insert a message
        Message message = new Message();
        message.setEventId("event-789");
        message.setEventType("EVENT_SCORE_UPDATE");
        message.setPayload("{\"eventId\":\"event-789\",\"currentScore\":\"3:2\"}");
        message.setStatus(MessageStatus.PENDING);
        message.setCreatedAt(LocalDateTime.now());
        message.setRetryCount(0);

        Long messageId = messageDao.insertMessage(message);
        assertNotNull(messageId);

        // Mark the message as failed
        LocalDateTime now = LocalDateTime.now();
        messageDao.markMessageAsFailed(messageId, MessageStatus.FAILED, now);

        // Verify the update by finding pending messages (should not include our updated message)
        List<Message> pendingMessages = messageDao.findPendingMessages();
        assertFalse(pendingMessages.stream().anyMatch(m -> m.getId().equals(messageId)));

        // Verify that the message can be found in failed messages (retry count < 5)
        List<Message> failedMessages = messageDao.findFailedMessages();
        assertTrue(failedMessages.stream().anyMatch(m -> m.getId().equals(messageId)));
    }

    @Test
    void testFindPendingMessages() {
        eventDao.insertEvent("pending-1", EventStatus.LIVE);
        eventDao.insertEvent("pending-2", EventStatus.LIVE);
        eventDao.insertEvent("sent-1", EventStatus.LIVE);
        // Insert multiple messages with different statuses
        Message pendingMessage1 = new Message();
        pendingMessage1.setEventId("pending-1");
        pendingMessage1.setEventType("EVENT_SCORE_UPDATE");
        pendingMessage1.setPayload("{\"eventId\":\"pending-1\",\"currentScore\":\"1:0\"}");
        pendingMessage1.setStatus(MessageStatus.PENDING);
        pendingMessage1.setCreatedAt(LocalDateTime.now());
        pendingMessage1.setRetryCount(0);
        messageDao.insertMessage(pendingMessage1);

        Message pendingMessage2 = new Message();
        pendingMessage2.setEventId("pending-2");
        pendingMessage2.setEventType("EVENT_SCORE_UPDATE");
        pendingMessage2.setPayload("{\"eventId\":\"pending-2\",\"currentScore\":\"2:1\"}");
        pendingMessage2.setStatus(MessageStatus.PENDING);
        pendingMessage2.setCreatedAt(LocalDateTime.now());
        pendingMessage2.setRetryCount(0);
        messageDao.insertMessage(pendingMessage2);

        Message sentMessage = new Message();
        sentMessage.setEventId("sent-1");
        sentMessage.setEventType("EVENT_SCORE_UPDATE");
        sentMessage.setPayload("{\"eventId\":\"sent-1\",\"currentScore\":\"0:0\"}");
        sentMessage.setStatus(MessageStatus.SENT);
        sentMessage.setCreatedAt(LocalDateTime.now());
        sentMessage.setRetryCount(0);
        Long sentMessageId = messageDao.insertMessage(sentMessage);

        // Now update the sent message status
        messageDao.updateMessageStatus(sentMessageId, MessageStatus.SENT, LocalDateTime.now());

        // Find pending messages (limit to 10)
        List<Message> pendingMessages = messageDao.findPendingMessages();

        // Should contain our two pending messages but not the sent one
        assertTrue(pendingMessages.size() >= 2); // Could have other pending messages from other tests
        assertTrue(pendingMessages.stream().anyMatch(m -> m.getPayload().contains("pending-1")));
        assertTrue(pendingMessages.stream().anyMatch(m -> m.getPayload().contains("pending-2")));
        assertFalse(pendingMessages.stream().anyMatch(m -> m.getId().equals(sentMessageId)));
    }

    @Test
    void testFindFailedMessages() {
        eventDao.insertEvent("failed-1", EventStatus.LIVE);
        eventDao.insertEvent("failed-2", EventStatus.LIVE);
        eventDao.insertEvent("perm-failed", EventStatus.LIVE);
        // Insert multiple messages with different statuses
        Message failedMessage1 = new Message();
        failedMessage1.setEventId("failed-1");
        failedMessage1.setEventType("EVENT_SCORE_UPDATE");
        failedMessage1.setPayload("{\"eventId\":\"failed-1\",\"currentScore\":\"0:1\"}");
        failedMessage1.setStatus(MessageStatus.FAILED);
        failedMessage1.setCreatedAt(LocalDateTime.now());
        failedMessage1.setRetryCount(1); // Less than 5 retries
        Long failedId1 = messageDao.insertMessage(failedMessage1);
        messageDao.markMessageAsFailed(failedId1, MessageStatus.FAILED, LocalDateTime.now());

        Message failedMessage2 = new Message();
        failedMessage2.setEventId("failed-2");
        failedMessage2.setEventType("EVENT_SCORE_UPDATE");
        failedMessage2.setPayload("{\"eventId\":\"failed-2\",\"currentScore\":\"1:1\"}");
        failedMessage2.setStatus(MessageStatus.FAILED);
        failedMessage2.setCreatedAt(LocalDateTime.now());
        failedMessage2.setRetryCount(3); // Less than 5 retries
        Long failedId2 = messageDao.insertMessage(failedMessage2);
        messageDao.markMessageAsFailed(failedId2, MessageStatus.FAILED, LocalDateTime.now());

        // This one has exceeded retry count, so shouldn't appear in findFailedMessages
        Message permanentlyFailedMessage = new Message();
        permanentlyFailedMessage.setEventId("perm-failed");
        permanentlyFailedMessage.setEventType("EVENT_SCORE_UPDATE");
        permanentlyFailedMessage.setPayload("{\"eventId\":\"perm-failed\",\"currentScore\":\"2:2\"}");
        permanentlyFailedMessage.setStatus(MessageStatus.PERMANENTLY_FAILED);
        permanentlyFailedMessage.setCreatedAt(LocalDateTime.now());
        permanentlyFailedMessage.setRetryCount(6); // More than 5 retries
        Long permFailedId = messageDao.insertMessage(permanentlyFailedMessage);
        messageDao.updateMessageStatus(permFailedId, MessageStatus.PERMANENTLY_FAILED, LocalDateTime.now());

        // Find failed messages
        List<Message> failedMessages = messageDao.findFailedMessages();

        // Should contain our two failed messages but not the permanently failed one
        assertEquals(2, failedMessages.size());
        assertTrue(failedMessages.stream().anyMatch(m -> m.getPayload().contains("failed-1")));
        assertTrue(failedMessages.stream().anyMatch(m -> m.getPayload().contains("failed-2")));
        assertFalse(failedMessages.stream().anyMatch(m -> m.getId().equals(permFailedId)));
    }
}