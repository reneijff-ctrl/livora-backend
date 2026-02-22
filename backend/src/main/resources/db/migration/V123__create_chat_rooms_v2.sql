CREATE TABLE chat_rooms_v2 (
                               id BIGSERIAL PRIMARY KEY,
                               creator_id BIGINT NOT NULL,
                               status VARCHAR(50) NOT NULL,
                               is_live BOOLEAN DEFAULT FALSE,
                               created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                               activated_at TIMESTAMP,
                               CONSTRAINT fk_chat_rooms_v2_creator
                                   FOREIGN KEY (creator_id)
                                       REFERENCES creator_records(id)
                                       ON DELETE CASCADE
);

CREATE INDEX idx_chat_rooms_v2_creator_id
    ON chat_rooms_v2(creator_id);