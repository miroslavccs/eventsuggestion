-- ── Shared suggestion content (split from per-customer suggestions) ─────────
CREATE TABLE suggestion_contents (
    id             BIGSERIAL    PRIMARY KEY,
    category       VARCHAR(50)  NOT NULL,
    title          VARCHAR(255) NOT NULL,
    description    VARCHAR(2000),
    location       VARCHAR(255),
    estimated_cost VARCHAR(255),
    suggested_date DATE,
    city           VARCHAR(255),
    created_at     TIMESTAMP
);

CREATE INDEX idx_suggestion_contents_city_category ON suggestion_contents(city, category);

-- No production deployment exists yet; existing suggestion rows are dev/test data only and are
-- cleared rather than backfilled 1:1 into suggestion_contents (content sharing only applies to
-- suggestions generated after this migration).
DELETE FROM suggestions;

ALTER TABLE suggestions ADD COLUMN content_id BIGINT NOT NULL REFERENCES suggestion_contents(id);
ALTER TABLE suggestions DROP COLUMN category;
ALTER TABLE suggestions DROP COLUMN title;
ALTER TABLE suggestions DROP COLUMN description;
ALTER TABLE suggestions DROP COLUMN location;
ALTER TABLE suggestions DROP COLUMN estimated_cost;

CREATE INDEX idx_suggestions_content_id ON suggestions(content_id);
