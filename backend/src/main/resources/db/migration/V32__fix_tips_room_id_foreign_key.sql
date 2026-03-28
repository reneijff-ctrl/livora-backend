-- Fix tips.room_id and tip_records.room_id FK: was referencing stream_rooms(id)
-- but JPA entities map to Stream (streams table).
-- Existing rows may contain room_id values not present in streams — clean those first.

-- Step 1: Drop old FK constraints pointing to stream_rooms
ALTER TABLE ONLY public.tips
    DROP CONSTRAINT IF EXISTS fk9c7d1irowgbvc5o8yywq6hu09;

ALTER TABLE ONLY public.tip_records
    DROP CONSTRAINT IF EXISTS fkc0fyhnn475qi4nvrx8awhquyr;

-- Step 2: Clean invalid data — set room_id to NULL where it references a non-existent stream
UPDATE public.tips
    SET room_id = NULL
    WHERE room_id IS NOT NULL
      AND room_id NOT IN (SELECT id FROM public.streams);

UPDATE public.tip_records
    SET room_id = NULL
    WHERE room_id IS NOT NULL
      AND room_id NOT IN (SELECT id FROM public.streams);

-- Step 3: Add correct FK constraints pointing to streams
ALTER TABLE ONLY public.tips
    ADD CONSTRAINT fk_tips_room_id_streams FOREIGN KEY (room_id) REFERENCES public.streams(id);

ALTER TABLE ONLY public.tip_records
    ADD CONSTRAINT fk_tip_records_room_id_streams FOREIGN KEY (room_id) REFERENCES public.streams(id);
