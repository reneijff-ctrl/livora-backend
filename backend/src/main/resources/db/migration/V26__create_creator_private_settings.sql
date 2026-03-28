CREATE TABLE public.creator_private_settings (
    creator_id bigint NOT NULL PRIMARY KEY,
    enabled boolean NOT NULL DEFAULT false,
    price_per_minute bigint NOT NULL DEFAULT 50
);
