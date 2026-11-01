-- Second line of defence for "price > 0" (PRD 5.1); the API validates it first.
ALTER TABLE product ADD CONSTRAINT ck_product_price_positive CHECK (price > 0);
