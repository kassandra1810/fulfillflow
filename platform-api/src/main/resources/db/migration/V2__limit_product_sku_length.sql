-- sku is at most 64 characters (spec, decided in the T02 review).
ALTER TABLE product ALTER COLUMN sku TYPE VARCHAR(64);
