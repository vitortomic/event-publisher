package com.sporty.homework.event_publisher.model;

import com.sporty.homework.event_publisher.enums.MessageStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class Message {
    private Long id;
    private String eventId;
    private String eventType;
    private String payload;
    private MessageStatus status; // PENDING, SENT, FAILED, PERMANENTLY_FAILED
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private int retryCount;
    private LocalDateTime lastAttemptAt;
}