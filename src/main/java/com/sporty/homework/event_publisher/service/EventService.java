package com.sporty.homework.event_publisher.service;

import com.sporty.homework.event_publisher.dao.EventDao;
import com.sporty.homework.event_publisher.model.Event;
import com.sporty.homework.event_publisher.dto.EventDto;
import com.sporty.homework.event_publisher.dto.CreateEventDto;
import com.sporty.homework.event_publisher.enums.EventStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventDao eventDao;

    public EventService(EventDao eventDao) {
        this.eventDao = eventDao;
    }

    @Transactional
    public void addEvent(CreateEventDto createEventDto) {
        eventDao.insertEvent(createEventDto.eventId(), createEventDto.status().name());
    }

    @Transactional
    public void updateEventStatus(String eventId, EventStatus status) {
        eventDao.updateEventStatus(eventId, status.name());
    }

    public EventDto findEventById(String eventId) {
        Event event = eventDao.findByEventId(eventId);
        if (event == null) {
            return null;
        }
        return new EventDto(event.getEventId(), event.getStatus());
    }
}