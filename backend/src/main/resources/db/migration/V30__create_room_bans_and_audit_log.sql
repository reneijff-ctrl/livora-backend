-- Creator room bans (temp + permanent)
CREATE TABLE IF NOT EXISTS creator_room_bans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    issued_by_user_id BIGINT NOT NULL,
    ban_type VARCHAR(20) NOT NULL,
    reason TEXT,
    expires_at TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT fk_room_ban_target FOREIGN KEY (target_user_id) REFERENCES users(id),
    CONSTRAINT fk_room_ban_issuer FOREIGN KEY (issued_by_user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_room_ban_creator ON creator_room_bans(creator_id);
CREATE INDEX IF NOT EXISTS idx_room_ban_target ON creator_room_bans(target_user_id);
CREATE INDEX IF NOT EXISTS idx_room_ban_creator_target ON creator_room_bans(creator_id, target_user_id);
CREATE INDEX IF NOT EXISTS idx_room_ban_expires ON creator_room_bans(expires_at);

-- Moderation audit log
CREATE TABLE IF NOT EXISTS moderation_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action_type VARCHAR(50) NOT NULL,
    creator_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    target_username VARCHAR(255),
    actor_user_id BIGINT NOT NULL,
    actor_username VARCHAR(255),
    actor_role VARCHAR(50) NOT NULL,
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_creator ON moderation_audit_log(creator_id);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON moderation_audit_log(created_at);
