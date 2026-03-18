CREATE TABLE IF NOT EXISTS payment_webhook_events (
    event_id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    order_id VARCHAR(100),
    payload TEXT NOT NULL,
    received_at BIGINT NOT NULL,
    processed_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_payment_webhook_events_order_id ON payment_webhook_events (order_id);
CREATE INDEX IF NOT EXISTS idx_payment_webhook_events_event_type ON payment_webhook_events (event_type);
