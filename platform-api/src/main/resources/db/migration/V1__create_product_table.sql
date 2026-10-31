CREATE TABLE product (
    id          UUID           NOT NULL,
    sku         VARCHAR(255)   NOT NULL,
    name        VARCHAR(255)   NOT NULL,
    description TEXT,
    price       NUMERIC(19, 2) NOT NULL,
    version     BIGINT         NOT NULL,
    CONSTRAINT pk_product PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_product_sku ON product (sku);
