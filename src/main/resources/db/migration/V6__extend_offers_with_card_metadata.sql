ALTER TABLE offers
    ADD COLUMN IF NOT EXISTS card_type_line VARCHAR(300),
    ADD COLUMN IF NOT EXISTS card_image_url TEXT;

CREATE INDEX IF NOT EXISTS idx_offers_card_name ON offers(card_name);
