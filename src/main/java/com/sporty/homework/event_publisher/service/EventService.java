package com.sporty.homework.event_publisher.service;

import com.sporty.homework.event_publisher.dao.EventDao;
import com.sporty.homework.event_publisher.model.Event;
import com.sporty.homework.event_publisher.dto.EventDto;
import com.sporty.homework.event_publisher.dto.CreateEventDto;
import com.sporty.homework.event_publisher.enums.EventStatus;
import com.sporty.homework.event_publisher.scheduler.ScheduledJobService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventDao eventDao;
    private final ScheduledJobService scheduledJobService;

    public EventService(EventDao eventDao, ScheduledJobService scheduledJobService) {
        this.eventDao = eventDao;
        this.scheduledJobService = scheduledJobService;
    }

    @Transactional
    public void addEvent(CreateEventDto createEventDto) {
        eventDao.insertEvent(createEventDto.eventId(), createEventDto.status().name());
        
        // If the initial status is LIVE, start the job
        if (createEventDto.status() == EventStatus.LIVE) {
            scheduledJobService.startJob(createEventDto.eventId());
        }
    }

    @Transactional
    public void updateEventStatus(String eventId, EventStatus status) {
        eventDao.updateEventStatus(eventId, status.name());
        
        // Manage the scheduled job based on status
        if (status == EventStatus.LIVE) {
            scheduledJobService.startJob(eventId);
        } else if (status == EventStatus.NOT_LIVE) {
            scheduledJobService.stopJob(eventId);
        }
    }

    public EventDto findEventById(String eventId) {
        Event event = eventDao.findByEventId(eventId);
        if (event == null) {
            return null;
        }
        return new EventDto(event.getEventId(), event.getStatus());
    }
}