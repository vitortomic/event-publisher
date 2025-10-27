package com.sporty.homework.event_publisher.controller;

import com.sporty.homework.event_publisher.dto.UpdateEventStatusDto;
import com.sporty.homework.event_publisher.enums.EventStatus;
import com.sporty.homework.event_publisher.service.EventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.util.AssertionErrors.assertTrue;

@ExtendWith(MockitoExtension.class)
class EventControllerEdgeCaseTest {

    @Mock
    private EventService eventService;

    @InjectMocks
    private EventController eventController;

    @Test
    void shouldReturnBadRequestWhenStatusIsNull() {
        // Given
        String eventId = "event-123";
        UpdateEventStatusDto updateEventStatusDto = new UpdateEventStatusDto(null);

        // When
        ResponseEntity<Map<String, String>> response = eventController.updateEventStatus(eventId, updateEventStatusDto);

        // Then
        assertEquals(400, response.getStatusCodeValue());
        assertTrue("Response should contain error message", 
                   response.getBody().containsKey("error"));
        assertTrue("Error message should be about null status", 
                   response.getBody().get("error").contains("Status cannot be null"));
        verify(eventService, never()).updateEventStatus(any(), any());
    }

    @Test
    void shouldReturnBadRequestWhenStatusIsInvalid() {
        // This test is a bit tricky since the JSON deserialization would normally
        // prevent invalid enum values from being passed as UpdateEventStatusDto
        // However, we can test error handling by using reflection or by testing
        // the service method behavior
        
        // For now, let's test the scenario where the service throws IllegalArgumentException
        String eventId = "event-123";
        UpdateEventStatusDto updateEventStatusDto = new UpdateEventStatusDto(EventStatus.LIVE);
        
        doThrow(new IllegalArgumentException("Invalid status")).when(eventService)
            .updateEventStatus(eq(eventId), any(EventStatus.class));

        // When
        ResponseEntity<Map<String, String>> response = eventController.updateEventStatus(eventId, updateEventStatusDto);

        // Then
        assertEquals(400, response.getStatusCodeValue());
        assertTrue("Response should contain error message", 
                   response.getBody().containsKey("error"));
        verify(eventService).updateEventStatus(eq(eventId), eq(EventStatus.LIVE));
    }
}