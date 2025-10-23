package com.sporty.homework.event_publisher.dto;

import com.sporty.homework.event_publisher.enums.EventStatus;

public record CreateEventDto(String eventId, EventStatus status) {}