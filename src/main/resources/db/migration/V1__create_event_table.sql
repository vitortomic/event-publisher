-- V1__create_event_table.sql

CREATE TYPE event_status AS ENUM ('LIVE', 'NOT_LIVE');

CREATE TABLE event (
    event_id VARCHAR(255) PRIMARY KEY,
    status event_status NOT NULL
);