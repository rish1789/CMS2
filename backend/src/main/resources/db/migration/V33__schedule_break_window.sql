-- 055-schedule-break-window: an optional single break inside a Schedule's start/end range
-- (e.g. a lunch gap), so one Schedule/Session can cover a whole day instead of a doctor
-- needing two separate Schedules (and therefore two Day Sheet rows) for the same date.
-- Both nullable: no break window is the default, unchanged behavior for every existing row.
ALTER TABLE schedule ADD COLUMN break_start_time TIME NULL;
ALTER TABLE schedule ADD COLUMN break_end_time TIME NULL;

-- Session snapshots its own break window at generation time, same as start_time/end_time,
-- so a later Schedule edit never retroactively changes an already-generated Session.
ALTER TABLE session ADD COLUMN break_start_time TIME NULL;
ALTER TABLE session ADD COLUMN break_end_time TIME NULL;
