package com.sporty.homework.event_publisher.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventScoreMessageDto {
    private String eventId;
    private String currentScore;
}