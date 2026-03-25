CREATE TABLE streams (
    id UUID PRIMARY KEY,
    creator_id BIGINT NOT NULL,
    title VARCHAR(255),
    is_live BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMP(6) WITH TIME ZONE,
    ended_at TIMESTAMP(6) WITH TIME ZONE,
    mediasoup_room_id UUID,
    stream_key VARCHAR(255) UNIQUE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_streams_creator FOREIGN KEY (creator_id) REFERENCES users(id)
);

CREATE INDEX idx_streams_creator_id ON streams(creator_id);
CREATE INDEX idx_streams_is_live ON streams(is_live);
CREATE INDEX idx_streams_mediasoup_room_id ON streams(mediasoup_room_id);
