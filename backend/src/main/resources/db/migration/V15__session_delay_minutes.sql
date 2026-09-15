-- 026-session-delay-tracking: the current, last-recalculated delay figure for a Fixed-Time
-- Session (null = no outstanding delay, whether never triggered or computed to zero/negative -
-- research.md R2). Written only by SessionDelayService.recalculate, at exactly two trigger
-- points; never recomputed on read.
ALTER TABLE session ADD COLUMN delay_minutes INTEGER;
