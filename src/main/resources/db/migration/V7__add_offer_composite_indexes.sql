CREATE INDEX IF NOT EXISTS idx_offers_user_type_created_at
    ON offers(user_id, offer_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_offers_type_created_at
    ON offers(offer_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_offers_card_type_created_at
    ON offers(card_id, offer_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_offers_card_name_type_created_at
    ON offers(card_name, offer_type, created_at DESC);
