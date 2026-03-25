ALTER TABLE public.stream_rooms ADD COLUMN is_live boolean NOT NULL DEFAULT false;
CREATE INDEX idx_stream_room_live ON public.stream_rooms (is_live);
