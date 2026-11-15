-- History reads (T07): one product's movements, newest first (created_at DESC,
-- then id DESC to break ties). This index serves exactly that ordering and the
-- product_id filter so the page query never sorts the whole table.

CREATE INDEX ix_stock_movement_product_history
    ON stock_movement (product_id, created_at DESC, id DESC);
