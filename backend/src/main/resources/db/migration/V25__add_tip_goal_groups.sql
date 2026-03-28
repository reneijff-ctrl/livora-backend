-- Tip Goal Groups: hierarchical milestone-based goal system
CREATE TABLE public.tip_goal_groups (
    id uuid NOT NULL,
    creator_id bigint NOT NULL,
    title character varying(100) NOT NULL,
    target_amount bigint NOT NULL,
    current_amount bigint NOT NULL DEFAULT 0,
    is_active boolean DEFAULT false,
    auto_reset boolean DEFAULT false,
    order_index integer DEFAULT 0,
    created_at timestamp(6) with time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tip_goal_groups_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_tip_goal_groups_creator ON public.tip_goal_groups(creator_id);

-- Add group reference to existing tip_goals for milestone support
ALTER TABLE public.tip_goals ADD COLUMN group_id uuid;

ALTER TABLE public.tip_goals
    ADD CONSTRAINT fk_tip_goals_group
    FOREIGN KEY (group_id) REFERENCES public.tip_goal_groups(id) ON DELETE SET NULL;

-- Add missing index on tip_goals.creator_id
CREATE INDEX idx_tip_goals_creator ON public.tip_goals(creator_id);
