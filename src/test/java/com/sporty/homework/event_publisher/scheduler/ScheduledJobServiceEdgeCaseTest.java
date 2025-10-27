package com.sporty.homework.event_publisher.scheduler;

import com.sporty.homework.event_publisher.dto.SoccerScoreDto;
import com.sporty.homework.event_publisher.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledJobServiceEdgeCaseTest {

    @Mock
    private OutboxService outboxService;

    private ScheduledJobService scheduledJobService;

    @BeforeEach
    void setUp() {
        scheduledJobService = new ScheduledJobService(outboxService);
    }

    @Test
    void shouldReturnTrueForValidSoccerScore() {
        // Given
        SoccerScoreDto validScore = new SoccerScoreDto("event-123", "1:0");

        // When
        boolean result = scheduledJobService.isValidSoccerScore(validScore);

        // Then
        assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenSoccerScoreEventIdIsNull() {
        // Given
        SoccerScoreDto scoreWithNullEventId = new SoccerScoreDto(null, "1:0");

        // When
        boolean result = scheduledJobService.isValidSoccerScore(scoreWithNullEventId);

        // Then
        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenSoccerScoreEventIdIsEmpty() {
        // Given
        SoccerScoreDto scoreWithEmptyEventId = new SoccerScoreDto("", "1:0");

        // When
        boolean result = scheduledJobService.isValidSoccerScore(scoreWithEmptyEventId);

        // Then
        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenSoccerScoreEventIdIsWhitespace() {
        // Given
        SoccerScoreDto scoreWithWhitespaceEventId = new SoccerScoreDto("   ", "1:0");

        // When
        boolean result = scheduledJobService.isValidSoccerScore(scoreWithWhitespaceEventId);

        // Then
        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenSoccerScoreCurrentScoreIsNull() {
        // Given
        SoccerScoreDto scoreWithNullCurrentScore = new SoccerScoreDto("event-123", null);

        // When
        boolean result = scheduledJobService.isValidSoccerScore(scoreWithNullCurrentScore);

        // Then
        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenSoccerScoreCurrentScoreIsInvalidFormat() {
        // Given
        SoccerScoreDto scoreWithInvalidFormat = new SoccerScoreDto("event-123", "invalid");

        // When
        boolean result = scheduledJobService.isValidSoccerScore(scoreWithInvalidFormat);

        // Then
        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenSoccerScoreCurrentScoreDoesNotMatchXColonYPattern() {
        // Given
        SoccerScoreDto scoreWithWrongFormat1 = new SoccerScoreDto("event-123", "1-0");  // Wrong separator
        SoccerScoreDto scoreWithWrongFormat2 = new SoccerScoreDto("event-123", "1:");   // Missing second number
        SoccerScoreDto scoreWithWrongFormat3 = new SoccerScoreDto("event-123", ":0");   // Missing first number

        // When & Then
        assertFalse(scheduledJobService.isValidSoccerScore(scoreWithWrongFormat1));
        assertFalse(scheduledJobService.isValidSoccerScore(scoreWithWrongFormat2));
        assertFalse(scheduledJobService.isValidSoccerScore(scoreWithWrongFormat3));
    }

    @Test
    void shouldReturnTrueForValidScoreFormatsWithMultipleDigits() {
        // Given
        SoccerScoreDto scoreWithMultipleDigits = new SoccerScoreDto("event-123", "10:15");

        // When
        boolean result = scheduledJobService.isValidSoccerScore(scoreWithMultipleDigits);

        // Then
        assertTrue(result);
    }
}