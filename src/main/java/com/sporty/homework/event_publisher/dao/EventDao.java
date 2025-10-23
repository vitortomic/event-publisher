package com.sporty.homework.event_publisher.dao;

import com.sporty.homework.event_publisher.model.Event;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;

public interface EventDao {

    @SqlUpdate("INSERT INTO event (event_id, event_status) VALUES (:eventId, :status)")
    void insertEvent(@Bind("eventId") String eventId, @Bind("status") String status);

    @SqlUpdate("UPDATE event SET event_status = :status WHERE event_id = :eventId")
    void updateEventStatus(@Bind("eventId") String eventId, @Bind("status") String status);

    @SqlQuery("SELECT * FROM event WHERE event_id = :eventId")
    @RegisterBeanMapper(Event.class)
    Event findByEventId(@Bind("eventId") String eventId);
}