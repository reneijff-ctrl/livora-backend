CREATE TABLE content_unlocks (
                                 id UUID PRIMARY KEY,
                                 user_id BIGINT NOT NULL,
                                 content_id UUID NOT NULL,
                                 unlocked_at TIMESTAMP NOT NULL DEFAULT now(),

                                 CONSTRAINT fk_unlock_user
                                     FOREIGN KEY (user_id) REFERENCES users(id),

                                 CONSTRAINT fk_unlock_content
                                     FOREIGN KEY (content_id) REFERENCES content(id),

                                 CONSTRAINT unique_user_content UNIQUE (user_id, content_id)
);