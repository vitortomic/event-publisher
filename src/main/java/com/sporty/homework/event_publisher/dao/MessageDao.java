package com.sporty.homework.event_publisher.dao;

import com.sporty.homework.event_publisher.enums.MessageStatus;
import com.sporty.homework.event_publisher.model.Message;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageDao {

    @SqlUpdate("INSERT INTO message_outbox (event_id, event_type, payload, status, created_at, retry_count) " +
               "VALUES (:eventId, :eventType, :payload::jsonb, :status, :createdAt, :retryCount)")
    @GetGeneratedKeys
    Long insertMessage(@BindBean Message message);

    @SqlUpdate("UPDATE message_outbox SET status = :status, sent_at = :sentAt WHERE id = :id")
    void updateMessageStatus(@Bind("id") Long id, @Bind("status") MessageStatus status, @Bind("sentAt") LocalDateTime sentAt);

    @SqlUpdate("UPDATE message_outbox SET status = :status, retry_count = retry_count + 1, last_attempt_at = :lastAttemptAt WHERE id = :id")
    void markMessageAsFailed(@Bind("id") Long id, @Bind("status") MessageStatus status, @Bind("lastAttemptAt") LocalDateTime lastAttemptAt);

    @SqlQuery("SELECT * FROM message_outbox WHERE status = 'PENDING' ORDER BY created_at ASC")
    @RegisterBeanMapper(Message.class)
    List<Message> findPendingMessages();

    @SqlQuery("SELECT * FROM message_outbox WHERE status = 'FAILED' AND retry_count < 5 ORDER BY last_attempt_at ASC")
    @RegisterBeanMapper(Message.class)
    List<Message> findFailedMessages();
    
    @SqlQuery("SELECT * FROM message_outbox WHERE status = :status ORDER BY created_at ASC")
    @RegisterBeanMapper(Message.class)
    List<Message> findMessagesByStatus(@Bind("status") MessageStatus status);

}