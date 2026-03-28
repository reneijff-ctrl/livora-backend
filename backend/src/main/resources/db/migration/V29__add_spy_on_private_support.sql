-- Add spy-on-private columns to existing creator_private_settings table (idempotent)
ALTER TABLE public.creator_private_settings
    ADD COLUMN IF NOT EXISTS allow_spy_on_private boolean NOT NULL DEFAULT false;
ALTER TABLE public.creator_private_settings
    ADD COLUMN IF NOT EXISTS spy_price_per_minute bigint NOT NULL DEFAULT 25;
ALTER TABLE public.creator_private_settings
    ADD COLUMN IF NOT EXISTS max_spy_viewers integer DEFAULT 5;

-- Create private_spy_sessions table (idempotent)
CREATE TABLE IF NOT EXISTS public.private_spy_sessions (
    id uuid NOT NULL PRIMARY KEY,
    private_session_id uuid NOT NULL,
    spy_viewer_id bigint NOT NULL,
    spy_price_per_minute bigint NOT NULL,
    status varchar(20) NOT NULL,
    started_at timestamp,
    ended_at timestamp,
    last_billed_at timestamp,
    end_reason varchar(255),
    CONSTRAINT fk_spy_session_private_session FOREIGN KEY (private_session_id) REFERENCES public.private_sessions(id),
    CONSTRAINT fk_spy_session_viewer FOREIGN KEY (spy_viewer_id) REFERENCES public.users(id)
);

-- Create indexes (idempotent)
CREATE INDEX IF NOT EXISTS idx_spy_session_status ON public.private_spy_sessions(status);
CREATE INDEX IF NOT EXISTS idx_spy_session_parent ON public.private_spy_sessions(private_session_id);
CREATE INDEX IF NOT EXISTS idx_spy_session_viewer ON public.private_spy_sessions(spy_viewer_id);
