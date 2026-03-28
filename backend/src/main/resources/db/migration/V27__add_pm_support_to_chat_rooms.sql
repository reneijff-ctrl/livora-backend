ALTER TABLE chat_rooms_v2
    ADD COLUMN room_type VARCHAR(20) NOT NULL DEFAULT 'STREAM',
    ADD COLUMN viewer_id BIGINT NULL;

CREATE INDEX idx_chat_room_pm ON chat_rooms_v2 (creator_id, viewer_id, room_type);
