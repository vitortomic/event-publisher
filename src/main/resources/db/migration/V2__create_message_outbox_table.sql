-- V2__create_message_outbox_table.sql

CREATE TABLE message_outbox (
    id SERIAL PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    retry_count INTEGER DEFAULT 0,
    last_attempt_at TIMESTAMP NULL
);

-- Create indexes for efficient querying
CREATE INDEX idx_message_outbox_status ON message_outbox(status);
CREATE INDEX idx_message_outbox_created_at ON message_outbox(created_at);
CREATE INDEX idx_message_outbox_last_attempt ON message_outbox(last_attempt_at) WHERE status = 'FAILED';