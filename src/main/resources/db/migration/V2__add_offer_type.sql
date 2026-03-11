ALTER TABLE offers
    ADD COLUMN IF NOT EXISTS offer_type VARCHAR(10) NOT NULL DEFAULT 'SELL';

CREATE INDEX IF NOT EXISTS idx_offers_offer_type ON offers(offer_type);
