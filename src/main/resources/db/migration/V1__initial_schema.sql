-- =============================================================
--  LiveLife – initial database schema
--  V1__initial_schema.sql
-- =============================================================

-- ── Customers ─────────────────────────────────────────────────
CREATE TABLE customers (
    id                 BIGSERIAL    PRIMARY KEY,
    email              VARCHAR(255) NOT NULL UNIQUE,
    password           VARCHAR(255) NOT NULL,
    first_name         VARCHAR(255) NOT NULL,
    last_name          VARCHAR(255) NOT NULL,
    gender             VARCHAR(50),
    age                INTEGER,

    -- Embedded address
    street             VARCHAR(255),
    city               VARCHAR(255),
    state              VARCHAR(255),
    country            VARCHAR(255),
    zip_code           VARCHAR(50),

    education          VARCHAR(255),
    current_employment VARCHAR(255),
    created_at         TIMESTAMP,
    updated_at         TIMESTAMP
);

-- ── Customer preferences ──────────────────────────────────────
CREATE TABLE customer_preferences (
    id               BIGSERIAL     PRIMARY KEY,
    customer_id      BIGINT        NOT NULL UNIQUE REFERENCES customers(id) ON DELETE CASCADE,
    likes_traveling  BOOLEAN       NOT NULL DEFAULT FALSE,
    likes_nightlife  BOOLEAN       NOT NULL DEFAULT FALSE,
    additional_notes VARCHAR(1000),
    learned_profile  VARCHAR(4000)
);

-- ElementCollection: sports
CREATE TABLE pref_sports (
    preference_id BIGINT       NOT NULL REFERENCES customer_preferences(id) ON DELETE CASCADE,
    sport         VARCHAR(255)
);

-- ElementCollection: hobbies
CREATE TABLE pref_hobbies (
    preference_id BIGINT       NOT NULL REFERENCES customer_preferences(id) ON DELETE CASCADE,
    hobby         VARCHAR(255)
);

-- ElementCollection: art interests
CREATE TABLE pref_art (
    preference_id BIGINT       NOT NULL REFERENCES customer_preferences(id) ON DELETE CASCADE,
    art_interest  VARCHAR(255)
);

-- ── Suggestions ───────────────────────────────────────────────
CREATE TABLE suggestions (
    id                    BIGSERIAL    PRIMARY KEY,
    customer_id           BIGINT       NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    category              VARCHAR(50)  NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    description           VARCHAR(2000),
    location              VARCHAR(255),
    estimated_cost        VARCHAR(255),
    suggested_date        DATE,
    reason_for_suggestion VARCHAR(1000),
    status                VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    feedback_comment      VARCHAR(1000),
    created_at            TIMESTAMP,
    responded_at          TIMESTAMP,
    notification_read     BOOLEAN      NOT NULL DEFAULT FALSE
);

-- ── Indexes ───────────────────────────────────────────────────
CREATE INDEX idx_suggestions_customer_id        ON suggestions(customer_id);
CREATE INDEX idx_suggestions_category           ON suggestions(category);
CREATE INDEX idx_suggestions_status             ON suggestions(status);
CREATE INDEX idx_suggestions_notification_read  ON suggestions(notification_read);
CREATE INDEX idx_suggestions_created_at         ON suggestions(created_at DESC);