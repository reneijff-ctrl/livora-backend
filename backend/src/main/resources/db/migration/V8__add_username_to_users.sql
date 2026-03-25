-- Step 1: Add the column as nullable
ALTER TABLE public.users ADD COLUMN username VARCHAR(30);

-- Step 2: Create temporary index for performance during data population
CREATE INDEX idx_temp_username ON public.users (username);

-- Step 3: Populate the column using a DO block to handle complex logic and uniqueness
DO $$
DECLARE
    u_rec RECORD;
    v_base_username TEXT;
    v_final_username TEXT;
    v_counter INTEGER;
BEGIN
    FOR u_rec IN SELECT id, email FROM public.users ORDER BY id LOOP
        -- Extract prefix from email, convert to lowercase, and strip invalid characters
        v_base_username := LOWER(SPLIT_PART(u_rec.email, '@', 1));
        v_base_username := REGEXP_REPLACE(v_base_username, '[^a-z0-9_]', '', 'g');
        
        -- Fallback to user_{id} if the result is empty
        IF v_base_username = '' THEN
            v_base_username := 'user_' || u_rec.id;
        END IF;
        
        -- Truncate to maximum allowed length (30)
        v_base_username := LEFT(v_base_username, 30);
        
        v_final_username := v_base_username;
        v_counter := 1;
        
        -- Resolve duplicates by suffixing _1, _2, etc.
        -- The temporary index idx_temp_username makes this check fast
        WHILE EXISTS (SELECT 1 FROM public.users WHERE username = v_final_username) LOOP
            -- Truncate base name if necessary to fit the suffix within 30 characters
            v_final_username := LEFT(v_base_username, 30 - LENGTH('_' || v_counter)) || '_' || v_counter;
            v_counter := v_counter + 1;
        END LOOP;
        
        -- Update the user with the generated unique username
        UPDATE public.users SET username = v_final_username WHERE id = u_rec.id;
    END LOOP;
END $$;

-- Step 4: Enforce data integrity constraints
ALTER TABLE public.users ALTER COLUMN username SET NOT NULL;
ALTER TABLE public.users ADD CONSTRAINT users_username_key UNIQUE (username);

-- Step 5: Drop the temporary index
DROP INDEX idx_temp_username;
