CREATE TABLE IF NOT EXISTS offers (
    id VARCHAR(100) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    card_id VARCHAR(200) NOT NULL,
    card_name VARCHAR(300) NOT NULL,
    price NUMERIC(12, 2),
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_offers_created_at ON offers(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_offers_card_id ON offers(card_id);
CREATE INDEX IF NOT EXISTS idx_offers_user_id ON offers(user_id);
