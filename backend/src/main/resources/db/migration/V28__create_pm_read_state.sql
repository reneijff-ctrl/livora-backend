CREATE TABLE pm_read_state (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_message_id BIGINT,
    unread_count INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_pm_read_state_room_user UNIQUE (room_id, user_id),

    CONSTRAINT fk_pm_read_state_room
        FOREIGN KEY (room_id) REFERENCES chat_rooms_v2(id),

    CONSTRAINT fk_pm_read_state_user
        FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT fk_pm_read_state_message
        FOREIGN KEY (last_read_message_id) REFERENCES chat_messages(id)
);
