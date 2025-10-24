package com.sporty.homework.event_publisher.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class Message {
    private Long id;
    private String eventType;
    private String payload;
    private String status; // PENDING, SENT, FAILED
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private int retryCount;
    private LocalDateTime lastAttemptAt;
}