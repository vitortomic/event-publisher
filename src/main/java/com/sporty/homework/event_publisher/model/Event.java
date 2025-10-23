package com.sporty.homework.event_publisher.model;

import com.sporty.homework.event_publisher.enums.EventStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Event {
    private String eventId;
    private EventStatus status;
}