-- 058-remove-reserved-capacity: the buffer-slot / reserved-capacity mechanism is removed
-- entirely. No dependent constraint, index, or foreign key references this column.
ALTER TABLE slot DROP COLUMN is_buffer;
