ALTER TABLE clinic ADD COLUMN city VARCHAR(255);

CREATE INDEX idx_clinic_city_lower ON clinic (lower(city));
