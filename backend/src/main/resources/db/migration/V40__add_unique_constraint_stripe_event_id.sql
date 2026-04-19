-- Fix 1: Enforce idempotency on Stripe webhook events at the DB level.
-- The baseline schema (V1) already created webhook_events_stripe_event_id_key.
-- This migration is a safe no-op guard: it adds the constraint only if it is somehow
-- absent (e.g. manual drop, schema drift). Uses the canonical constraint name from V1.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'webhook_events_stripe_event_id_key'
          AND conrelid = 'webhook_events'::regclass
    ) THEN
        ALTER TABLE webhook_events
            ADD CONSTRAINT webhook_events_stripe_event_id_key UNIQUE (stripe_event_id);
    END IF;
END;
$$;
