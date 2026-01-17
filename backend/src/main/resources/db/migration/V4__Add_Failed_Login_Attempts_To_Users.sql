-- Migration to add failed_login_attempts and lockout_until to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER;
ALTER TABLE users ADD COLUMN IF NOT EXISTS lockout_until TIMESTAMP WITH TIME ZONE;
UPDATE users SET failed_login_attempts = 0 WHERE failed_login_attempts IS NULL;
ALTER TABLE users ALTER COLUMN failed_login_attempts SET DEFAULT 0;
ALTER TABLE users ALTER COLUMN failed_login_attempts SET NOT NULL;
