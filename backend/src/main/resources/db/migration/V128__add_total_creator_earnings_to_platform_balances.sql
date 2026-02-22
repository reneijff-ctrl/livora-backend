DO $$
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'platform_balances'
    ) THEN
ALTER TABLE platform_balances
    ADD COLUMN IF NOT EXISTS total_creator_earnings DECIMAL(19,2) DEFAULT 0.00 NOT NULL;
END IF;
END
$$;