package com.sporty.homework.event_publisher.scheduler;

import com.sporty.homework.event_publisher.dto.SoccerScoreDto;
import com.sporty.homework.event_publisher.service.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
public class ScheduledJobService {

    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final TaskScheduler taskScheduler;
    private final RestTemplate restTemplate = new RestTemplate();
    private final OutboxService outboxService;
    private static final String THREAD_NAME_PREFIX = "event-job-";

    @Value("${score.endpoint.url:http://localhost:8081}")
    private String baseUrl;

    public ScheduledJobService(OutboxService outboxService) {
        this.outboxService = outboxService;
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix(THREAD_NAME_PREFIX);
        scheduler.initialize();
        this.taskScheduler = scheduler;
    }

    public void startJob(String eventId) {
        // Stop any existing job for this event
        stopJob(eventId);

        // Create a new scheduled task that runs every 10 seconds to get soccer scores
        Runnable task = () -> {
            try {
                String url = baseUrl + "/" + eventId + "/score";
                ResponseEntity<SoccerScoreDto> response = restTemplate.getForEntity(url, SoccerScoreDto.class);
                SoccerScoreDto scoreDto = response.getBody();
                
                // Log the resulting JSON
                if (scoreDto != null) {
                    log.info("Score update for event {}: Event ID: {}, Current Score: {}", 
                        eventId, scoreDto.eventId(), scoreDto.currentScore());
                    
                    // Send the score to Kafka using outbox pattern
                    outboxService.saveMessageAndSendToKafka(scoreDto.eventId(), scoreDto.currentScore());
                } else {
                    log.info("Received null response for event: {}", eventId);
                }
                
            } catch (Exception e) {
                log.error("Error calling endpoint for event: {} - {}", eventId, e.getMessage());
            }
        };

        // Schedule the task to run every 10 seconds
        ScheduledFuture<?> scheduledTask = taskScheduler.scheduleAtFixedRate(
            task,
            Instant.now().plusSeconds(1), // Start after 1 second
            Duration.ofMillis(10000) // Every 10 seconds
        );

        scheduledTasks.put(eventId, scheduledTask);
        log.info("Started scheduled job for event: {}", eventId);
    }

    public void stopJob(String eventId) {
        ScheduledFuture<?> scheduledTask = scheduledTasks.get(eventId);
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(false);
            scheduledTasks.remove(eventId);
            log.info("Stopped scheduled job for event: {}", eventId);
        }
    }

    public boolean isJobRunning(String eventId) {
        ScheduledFuture<?> scheduledTask = scheduledTasks.get(eventId);
        return scheduledTask != null && !scheduledTask.isCancelled() && !scheduledTask.isDone();
    }

    @PreDestroy
    public void shutdown() {
        for (ScheduledFuture<?> task : scheduledTasks.values()) {
            if (!task.isCancelled()) {
                task.cancel(true);
            }
        }
        scheduledTasks.clear();
    }
}