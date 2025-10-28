package com.sporty.homework.event_publisher.dao;

import com.sporty.homework.event_publisher.enums.EventStatus;
import com.sporty.homework.event_publisher.model.Event;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class EventDaoFunctionalTest {

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
    private EventDao eventDao;

    @Test
    void testInsertEvent() {
        String eventId = "event-123";
        EventStatus status = EventStatus.LIVE;

        // Insert the event
        eventDao.insertEvent(eventId, status);

        // Verify the event was inserted by finding it
        Event foundEvent = eventDao.findByEventId(eventId);
        assertNotNull(foundEvent);
        assertEquals(eventId, foundEvent.getEventId());
        assertEquals(status, foundEvent.getStatus()); // Convert enum to string for comparison
    }

    @Test
    void testUpdateEventStatus() {
        String eventId = "event-456";
        EventStatus initialStatus = EventStatus.NOT_LIVE;
        EventStatus updatedStatus = EventStatus.LIVE;

        // Insert the event with initial status
        eventDao.insertEvent(eventId, initialStatus);

        // Verify initial status
        Event foundEvent = eventDao.findByEventId(eventId);
        assertNotNull(foundEvent);
        assertEquals(initialStatus, foundEvent.getStatus()); // Convert enum to string for comparison

        // Update the event status
        eventDao.updateEventStatus(eventId, updatedStatus);

        // Verify the status was updated
        Event updatedEvent = eventDao.findByEventId(eventId);
        assertNotNull(updatedEvent);
        assertEquals(updatedStatus, updatedEvent.getStatus()); // Convert enum to string for comparison
    }

    @Test
    void testFindByEventId() {
        String eventId1 = "event-789";
        String eventId2 = "event-abc";
        EventStatus status1 = EventStatus.LIVE;
        EventStatus status2 = EventStatus.NOT_LIVE;

        // Insert two events with valid statuses
        eventDao.insertEvent(eventId1, status1);
        eventDao.insertEvent(eventId2, status2);

        // Find the first event
        Event foundEvent1 = eventDao.findByEventId(eventId1);
        assertNotNull(foundEvent1);
        assertEquals(eventId1, foundEvent1.getEventId());
        assertEquals(status1, foundEvent1.getStatus()); // Convert enum to string for comparison

        // Find the second event
        Event foundEvent2 = eventDao.findByEventId(eventId2);
        assertNotNull(foundEvent2);
        assertEquals(eventId2, foundEvent2.getEventId());
        assertEquals(status2, foundEvent2.getStatus()); // Convert enum to string for comparison

        // Try to find a non-existent event
        Event nonExistentEvent = eventDao.findByEventId("non-existent");
        assertNull(nonExistentEvent);
    }
}