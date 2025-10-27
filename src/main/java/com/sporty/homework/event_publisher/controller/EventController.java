package com.sporty.homework.event_publisher.controller;

import com.sporty.homework.event_publisher.dto.EventDto;
import com.sporty.homework.event_publisher.dto.CreateEventDto;
import com.sporty.homework.event_publisher.dto.UpdateEventStatusDto;
import com.sporty.homework.event_publisher.service.EventService;
import com.sporty.homework.event_publisher.enums.EventStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventDto> addEvent(@RequestBody CreateEventDto createEventDto) {
        eventService.addEvent(createEventDto);
        EventDto eventDto = new EventDto(createEventDto.eventId(), createEventDto.status());
        return ResponseEntity.ok(eventDto);
    }

    @PutMapping("/{eventId}/status")
    public ResponseEntity<Map<String, String>> updateEventStatus(
            @PathVariable String eventId,
            @RequestBody UpdateEventStatusDto updateEventStatusDto) {
        
        // Validate the status is one of the allowed values
        if (updateEventStatusDto.status() == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Status cannot be null. Allowed values are: LIVE, NOT_LIVE"));
        }
        
        try {
            // This will throw IllegalArgumentException if not a valid enum value
            eventService.updateEventStatus(eventId, updateEventStatusDto.status());
            return ResponseEntity.ok(Map.of("message", "Event status updated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid status. Allowed values are: LIVE, NOT_LIVE"));
        }
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventDto> getEventById(@PathVariable String eventId) {
        EventDto eventDto = eventService.findEventById(eventId);
        if (eventDto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(eventDto);
    }
}