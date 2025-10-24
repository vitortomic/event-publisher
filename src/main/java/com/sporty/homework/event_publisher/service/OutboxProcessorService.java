package com.sporty.homework.event_publisher.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxProcessorService {

    private final OutboxService outboxService;

    @Value("${outbox.processor.interval:10000}") // Default to 10 seconds
    private long processorInterval;

    /**
     * Scheduled task to process pending and failed messages
     */
    @Scheduled(fixedRateString = "${outbox.processor.interval:10000}") // Configurable interval, default to 10 seconds
    public void processOutboxMessages() {
        log.info("Processing outbox messages...");
        outboxService.processPendingMessages();
        log.info("Completed processing outbox messages");
    }
}