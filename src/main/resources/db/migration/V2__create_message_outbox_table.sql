-- V2__create_message_outbox_table.sql

CREATE TABLE message_outbox (
    id SERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload jsonb NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'PERMANENTLY_FAILED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    retry_count INTEGER DEFAULT 0,
    last_attempt_at TIMESTAMP NULL,
    CONSTRAINT fk_message_event FOREIGN KEY (event_id) REFERENCES event(event_id)
);

-- Create indexes for efficient querying
CREATE INDEX idx_message_outbox_status ON message_outbox(status);
CREATE INDEX idx_message_outbox_created_at ON message_outbox(created_at);
CREATE INDEX idx_message_outbox_last_attempt ON message_outbox(last_attempt_at) WHERE status = 'FAILED';
CREATE INDEX idx_message_outbox_event_id ON message_outbox(event_id);