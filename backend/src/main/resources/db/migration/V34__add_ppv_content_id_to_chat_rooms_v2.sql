ALTER TABLE chat_rooms_v2
    ADD COLUMN IF NOT EXISTS ppv_content_id UUID;
