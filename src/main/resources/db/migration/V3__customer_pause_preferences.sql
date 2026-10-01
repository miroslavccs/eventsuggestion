-- ── Vacation mode & per-category suggestion pause ───────────────
ALTER TABLE customer_preferences ADD COLUMN vacation_mode BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE pref_paused_categories (
    preference_id BIGINT      NOT NULL REFERENCES customer_preferences(id) ON DELETE CASCADE,
    category      VARCHAR(50) NOT NULL
);
