-- Tip Menu Categories table
CREATE TABLE public.tip_menu_categories (
    id UUID PRIMARY KEY,
    creator_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    sort_order INT DEFAULT 0,
    is_enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tip_menu_categories_creator
    ON public.tip_menu_categories(creator_id);

-- Extend tip_actions with category support and ordering
ALTER TABLE public.tip_actions
    ADD COLUMN category_id UUID NULL,
    ADD COLUMN sort_order INT DEFAULT 0;

CREATE INDEX idx_tip_actions_creator
    ON public.tip_actions(creator_id);

ALTER TABLE public.tip_actions
    ADD CONSTRAINT fk_tip_actions_category
    FOREIGN KEY (category_id)
    REFERENCES public.tip_menu_categories(id)
    ON DELETE SET NULL;
