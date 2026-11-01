-- Inventory (T06): default warehouse, stock levels, receipts and append-only
-- movement history. The API never accepts or returns a warehouse; v1 has one
-- default warehouse, created here by migration (spec, PRD 5.4).

CREATE TABLE warehouse (
    id UUID NOT NULL,
    CONSTRAINT pk_warehouse PRIMARY KEY (id)
);

INSERT INTO warehouse (id) VALUES ('11111111-1111-7111-8111-111111111111');

-- One row per product x warehouse, created on the first receipt. The CHECK is
-- the last line of defence for the stock invariant 0 <= reserved <= on_hand.
CREATE TABLE stock_level (
    id           UUID   NOT NULL,
    product_id   UUID   NOT NULL,
    warehouse_id UUID   NOT NULL,
    on_hand      BIGINT NOT NULL,
    reserved     BIGINT NOT NULL,
    CONSTRAINT pk_stock_level PRIMARY KEY (id),
    CONSTRAINT fk_stock_level_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_stock_level_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT ck_stock_level_reserved CHECK (reserved >= 0 AND reserved <= on_hand)
);

CREATE UNIQUE INDEX ux_stock_level_product_warehouse ON stock_level (product_id, warehouse_id);

CREATE TABLE stock_receipt (
    id           UUID        NOT NULL,
    product_id   UUID        NOT NULL,
    warehouse_id UUID        NOT NULL,
    quantity     BIGINT      NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_stock_receipt PRIMARY KEY (id),
    CONSTRAINT fk_stock_receipt_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_stock_receipt_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT ck_stock_receipt_quantity CHECK (quantity > 0)
);

-- Append-only history. A movement points at its source; in T06 that is always a
-- receipt (RECEIPT). Orders add their types and source column when they arrive (T08).
CREATE TABLE stock_movement (
    id               UUID        NOT NULL,
    type             VARCHAR(16) NOT NULL,
    product_id       UUID        NOT NULL,
    warehouse_id     UUID        NOT NULL,
    quantity         BIGINT      NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    stock_receipt_id UUID,
    CONSTRAINT pk_stock_movement PRIMARY KEY (id),
    CONSTRAINT fk_stock_movement_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_stock_movement_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT fk_stock_movement_receipt FOREIGN KEY (stock_receipt_id) REFERENCES stock_receipt (id),
    CONSTRAINT ck_stock_movement_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_movement_type CHECK (type IN ('RECEIPT'))
);
