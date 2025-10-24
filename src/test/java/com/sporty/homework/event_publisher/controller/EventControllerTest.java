package com.sporty.homework.event_publisher.controller;

import com.sporty.homework.event_publisher.dto.CreateEventDto;
import com.sporty.homework.event_publisher.dto.EventDto;
import com.sporty.homework.event_publisher.dto.UpdateEventStatusDto;
import com.sporty.homework.event_publisher.enums.EventStatus;
import com.sporty.homework.event_publisher.service.EventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    @Mock
    private EventService eventService;

    @InjectMocks
    private EventController eventController;

    @Captor
    private ArgumentCaptor<CreateEventDto> createEventDtoCaptor;

    @Test
    void shouldAddEventSuccessfully() {
        // Given
        CreateEventDto createEventDto = new CreateEventDto("event-123", EventStatus.LIVE);

        // When
        ResponseEntity<EventDto> response = eventController.addEvent(createEventDto);

        // Then
        assertEquals(200, response.getStatusCodeValue());
        verify(eventService).addEvent(createEventDtoCaptor.capture());
        assertEquals("event-123", createEventDtoCaptor.getValue().eventId());
        assertEquals(EventStatus.LIVE, createEventDtoCaptor.getValue().status());
    }

    @Test
    void shouldUpdateEventStatusSuccessfully() {
        // Given
        String eventId = "event-123";
        UpdateEventStatusDto updateEventStatusDto = new UpdateEventStatusDto(EventStatus.NOT_LIVE);

        // When
        ResponseEntity<Map<String, String>> response = eventController.updateEventStatus(eventId, updateEventStatusDto);

        // Then
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().containsKey("message"));
        verify(eventService).updateEventStatus(eventId, EventStatus.NOT_LIVE);
    }

    @Test
    void shouldGetEventByIdSuccessfully() {
        // Given
        String eventId = "event-123";
        EventDto expectedEventDto = new EventDto("event-123", EventStatus.LIVE);
        when(eventService.findEventById(eventId)).thenReturn(expectedEventDto);

        // When
        ResponseEntity<EventDto> response = eventController.getEventById(eventId);

        // Then
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(expectedEventDto, response.getBody());
        verify(eventService).findEventById(eventId);
    }

    @Test
    void shouldReturnNotFoundWhenEventDoesNotExist() {
        // Given
        String eventId = "non-existent";
        when(eventService.findEventById(eventId)).thenReturn(null);

        // When
        ResponseEntity<EventDto> response = eventController.getEventById(eventId);

        // Then
        assertEquals(404, response.getStatusCodeValue());
        assertNull(response.getBody());
        verify(eventService).findEventById(eventId);
    }
}