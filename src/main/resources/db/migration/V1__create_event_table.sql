-- V1__create_event_table.sql


CREATE TABLE event (
    event_id VARCHAR(255) PRIMARY KEY,
    event_status VARCHAR(20) CHECK (event_status IN ('LIVE', 'NOT_LIVE'))
);