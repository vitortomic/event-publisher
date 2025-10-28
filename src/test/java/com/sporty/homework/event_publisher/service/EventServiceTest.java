package com.sporty.homework.event_publisher.service;

import com.sporty.homework.event_publisher.dao.EventDao;
import com.sporty.homework.event_publisher.dto.CreateEventDto;
import com.sporty.homework.event_publisher.dto.EventDto;
import com.sporty.homework.event_publisher.enums.EventStatus;
import com.sporty.homework.event_publisher.model.Event;
import com.sporty.homework.event_publisher.scheduler.ScheduledJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventDao eventDao;

    @Mock
    private ScheduledJobService scheduledJobService;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventDao, scheduledJobService);
    }

    @Test
    void shouldAddEventToDatabaseWhenAddEventCalled() {
        // Given
        CreateEventDto createEventDto = new CreateEventDto("event-123", EventStatus.LIVE);

        // When
        eventService.addEvent(createEventDto);

        // Then
        verify(eventDao).insertEvent(eq("event-123"), eq(EventStatus.LIVE));
    }

    @Test
    void shouldStartJobWhenEventStatusIsLiveOnCreation() {
        // Given
        CreateEventDto createEventDto = new CreateEventDto("event-123", EventStatus.LIVE);

        // When
        eventService.addEvent(createEventDto);

        // Then
        verify(scheduledJobService).startJob("event-123");
    }

    @Test
    void shouldNotStartJobWhenEventStatusIsNotLiveOnCreation() {
        // Given
        CreateEventDto createEventDto = new CreateEventDto("event-123", EventStatus.NOT_LIVE);

        // When
        eventService.addEvent(createEventDto);

        // Then
        verify(scheduledJobService, never()).startJob(any());
    }

    @Test
    void shouldUpdateEventStatusInDatabase() {
        // Given
        String eventId = "event-123";
        EventStatus newStatus = EventStatus.NOT_LIVE;

        // When
        eventService.updateEventStatus(eventId, newStatus);

        // Then
        verify(eventDao).updateEventStatus(eq("event-123"), eq(EventStatus.NOT_LIVE));
    }

    @Test
    void shouldStartJobWhenUpdatingEventStatusToLive() {
        // Given
        String eventId = "event-123";
        EventStatus newStatus = EventStatus.LIVE;

        // When
        eventService.updateEventStatus(eventId, newStatus);

        // Then
        verify(scheduledJobService).startJob("event-123");
    }

    @Test
    void shouldStopJobWhenUpdatingEventStatusToNotLive() {
        // Given
        String eventId = "event-123";
        EventStatus newStatus = EventStatus.NOT_LIVE;

        // When
        eventService.updateEventStatus(eventId, newStatus);

        // Then
        verify(scheduledJobService).stopJob("event-123");
    }

    @Test
    void shouldReturnEventDtoWhenFindingEventById() {
        // Given
        String eventId = "event-123";
        Event mockEvent = new Event();
        mockEvent.setEventId("event-123");
        mockEvent.setStatus(EventStatus.LIVE);
        when(eventDao.findByEventId(eventId)).thenReturn(mockEvent);

        // When
        EventDto result = eventService.findEventById(eventId);

        // Then
        assertNotNull(result);
        assertEquals("event-123", result.eventId());
        assertEquals(EventStatus.LIVE, result.status());
    }

    @Test
    void shouldReturnNullWhenEventDoesNotExist() {
        // Given
        String eventId = "non-existent";
        when(eventDao.findByEventId(eventId)).thenReturn(null);

        // When
        EventDto result = eventService.findEventById(eventId);

        // Then
        assertNull(result);
    }
}