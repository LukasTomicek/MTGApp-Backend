CREATE TABLE IF NOT EXISTS seller_payout_accounts (
    user_id VARCHAR(100) PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    connected_account_id VARCHAR(100) NOT NULL,
    details_submitted BOOLEAN NOT NULL DEFAULT FALSE,
    charges_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    payouts_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS trade_orders (
    id VARCHAR(100) PRIMARY KEY,
    chat_id VARCHAR(255) NOT NULL UNIQUE,
    card_id VARCHAR(200) NOT NULL,
    card_name VARCHAR(300) NOT NULL,
    buyer_user_id VARCHAR(100) NOT NULL,
    seller_user_id VARCHAR(100) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    platform_fee_minor BIGINT NOT NULL,
    seller_amount_minor BIGINT NOT NULL,
    payment_status VARCHAR(50) NOT NULL,
    payout_status VARCHAR(50) NOT NULL,
    checkout_session_id VARCHAR(255),
    payment_intent_id VARCHAR(255),
    paid_at BIGINT,
    paid_out_at BIGINT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_trade_orders_buyer_user_id ON trade_orders (buyer_user_id);
CREATE INDEX IF NOT EXISTS idx_trade_orders_seller_user_id ON trade_orders (seller_user_id);
