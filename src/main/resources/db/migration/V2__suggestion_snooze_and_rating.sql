-- ── Suggestion snooze & post-event rating ───────────────────────
ALTER TABLE suggestions ADD COLUMN snoozed_until DATE;
ALTER TABLE suggestions ADD COLUMN rating SMALLINT;
