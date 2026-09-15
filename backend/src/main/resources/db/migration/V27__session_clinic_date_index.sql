-- 042-day-sheet-hardening: supports the session list's clinic+date-range filter
-- (ClinicSessionListController) - no index currently covers this query, and Postgres
-- does not auto-index foreign key columns (research.md R8).
CREATE INDEX idx_session_clinic_id_session_date ON session (clinic_id, session_date);
