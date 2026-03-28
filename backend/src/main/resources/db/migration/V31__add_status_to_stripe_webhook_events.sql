-- Add status and processed_at columns to stripe_webhook_events for atomic claim-based idempotency
ALTER TABLE public.stripe_webhook_events
    ADD COLUMN status character varying(20) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN processed_at timestamp(6) with time zone;

-- Update existing rows: they were already fully processed, so mark them COMPLETED with processed_at = received_at
UPDATE public.stripe_webhook_events SET status = 'COMPLETED', processed_at = received_at WHERE status = 'COMPLETED';

-- Add constraint to ensure valid status values
ALTER TABLE public.stripe_webhook_events
    ADD CONSTRAINT stripe_webhook_events_status_check
        CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'));
