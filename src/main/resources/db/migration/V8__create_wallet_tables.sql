CREATE TABLE IF NOT EXISTS user_wallets (
    user_id VARCHAR(100) PRIMARY KEY,
    credits_balance INTEGER NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS wallet_purchases (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    store_platform VARCHAR(20) NOT NULL,
    product_id VARCHAR(120) NOT NULL,
    store_transaction_id VARCHAR(255) NOT NULL,
    purchase_token TEXT,
    credits_granted INTEGER NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (store_platform, store_transaction_id)
);

CREATE TABLE IF NOT EXISTS wallet_ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    entry_type VARCHAR(40) NOT NULL,
    amount INTEGER NOT NULL,
    reference_type VARCHAR(40) NOT NULL,
    reference_id VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wallet_purchases_user_id
    ON wallet_purchases (user_id);

CREATE INDEX IF NOT EXISTS idx_wallet_ledger_entries_user_id
    ON wallet_ledger_entries (user_id, created_at DESC);
