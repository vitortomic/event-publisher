package com.sporty.homework.event_publisher.dao;

import com.sporty.homework.event_publisher.model.Message;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageDao {

    @SqlUpdate("INSERT INTO message_outbox (event_type, payload, status, created_at, retry_count) " +
               "VALUES (:eventType, :payload, :status, :createdAt, :retryCount)")
    void insertMessage(@BindBean Message message);

    @SqlUpdate("UPDATE message_outbox SET status = :status, sent_at = :sentAt WHERE id = :id")
    void updateMessageStatus(@Bind("id") Long id, @Bind("status") String status, @Bind("sentAt") LocalDateTime sentAt);

    @SqlUpdate("UPDATE message_outbox SET status = :status, retry_count = retry_count + 1, last_attempt_at = :lastAttemptAt WHERE id = :id")
    void markMessageAsFailed(@Bind("id") Long id, @Bind("status") String status, @Bind("lastAttemptAt") LocalDateTime lastAttemptAt);

    @SqlQuery("SELECT * FROM message_outbox WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit")
    @RegisterBeanMapper(Message.class)
    List<Message> findPendingMessages(@Bind("limit") int limit);

    @SqlQuery("SELECT * FROM message_outbox WHERE status = 'FAILED' AND retry_count < 5 ORDER BY last_attempt_at ASC LIMIT :limit")
    @RegisterBeanMapper(Message.class)
    List<Message> findFailedMessages(@Bind("limit") int limit);
}