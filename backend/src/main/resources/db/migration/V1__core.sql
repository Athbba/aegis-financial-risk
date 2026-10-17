CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    created_at timestamptz NOT NULL
);
CREATE TABLE asset (
    symbol varchar(12) PRIMARY KEY,
    name varchar(100) NOT NULL,
    sector varchar(60) NOT NULL,
    currency varchar(3) NOT NULL CHECK (currency = 'USD'),
    dataset_version varchar(40) NOT NULL
);
CREATE TABLE historical_price (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    asset_symbol varchar(12) NOT NULL REFERENCES asset(symbol),
    price_date date NOT NULL,
    close_price numeric(19,4) NOT NULL CHECK (close_price > 0),
    UNIQUE (asset_symbol, price_date)
);
CREATE TABLE portfolio (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES app_user(id),
    name varchar(80) NOT NULL,
    currency varchar(3) NOT NULL CHECK (currency = 'USD'),
    cash numeric(19,2) NOT NULL CHECK (cash >= 0),
    last_trade_date date,
    created_at timestamptz NOT NULL
);
CREATE INDEX portfolio_owner_created ON portfolio(owner_id, created_at, id);
CREATE TABLE executed_order (
    id uuid PRIMARY KEY,
    portfolio_id uuid NOT NULL REFERENCES portfolio(id),
    asset_symbol varchar(12) NOT NULL REFERENCES asset(symbol),
    side varchar(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity numeric(19,6) NOT NULL CHECK (quantity > 0),
    execution_price numeric(19,4) NOT NULL CHECK (execution_price > 0),
    gross_amount numeric(19,2) NOT NULL CHECK (gross_amount > 0),
    realized_pnl numeric(19,2) NOT NULL,
    price_date date NOT NULL,
    executed_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL,
    idempotency_key varchar(80) NOT NULL,
    UNIQUE(portfolio_id, idempotency_key)
);
CREATE INDEX order_portfolio_recorded ON executed_order(portfolio_id, recorded_at DESC, id);
CREATE TABLE position_lot (
    id uuid PRIMARY KEY,
    portfolio_id uuid NOT NULL REFERENCES portfolio(id),
    asset_symbol varchar(12) NOT NULL REFERENCES asset(symbol),
    buy_order_id uuid NOT NULL UNIQUE REFERENCES executed_order(id),
    original_quantity numeric(19,6) NOT NULL CHECK (original_quantity > 0),
    remaining_quantity numeric(19,6) NOT NULL CHECK (remaining_quantity >= 0 AND remaining_quantity <= original_quantity),
    remaining_cost numeric(19,2) NOT NULL CHECK (remaining_cost >= 0),
    acquired_at timestamptz NOT NULL,
    sequence_id bigint GENERATED ALWAYS AS IDENTITY,
    CHECK (remaining_quantity > 0 OR remaining_cost = 0)
);
CREATE INDEX lot_fifo ON position_lot(portfolio_id, asset_symbol, acquired_at, sequence_id) WHERE remaining_quantity > 0;
CREATE TABLE sell_allocation (
    id uuid PRIMARY KEY,
    sell_order_id uuid NOT NULL REFERENCES executed_order(id),
    lot_id uuid NOT NULL REFERENCES position_lot(id),
    quantity numeric(19,6) NOT NULL CHECK (quantity > 0),
    cost numeric(19,2) NOT NULL CHECK (cost >= 0)
);
CREATE TABLE cash_ledger (
    id uuid PRIMARY KEY,
    portfolio_id uuid NOT NULL REFERENCES portfolio(id),
    order_id uuid UNIQUE REFERENCES executed_order(id),
    kind varchar(12) NOT NULL CHECK (kind IN ('FUNDING','BUY','SELL')),
    amount numeric(19,2) NOT NULL,
    balance_after numeric(19,2) NOT NULL CHECK (balance_after >= 0),
    recorded_at timestamptz NOT NULL,
    CHECK ((kind = 'BUY' AND amount < 0 AND order_id IS NOT NULL) OR
           (kind = 'SELL' AND amount > 0 AND order_id IS NOT NULL) OR
           (kind = 'FUNDING' AND amount > 0 AND order_id IS NULL))
);
CREATE UNIQUE INDEX one_initial_funding ON cash_ledger(portfolio_id) WHERE kind = 'FUNDING';
CREATE INDEX ledger_portfolio_recorded ON cash_ledger(portfolio_id, recorded_at DESC, id);
CREATE TABLE audit_event (
    id uuid PRIMARY KEY,
    portfolio_id uuid NOT NULL REFERENCES portfolio(id),
    actor_id uuid NOT NULL REFERENCES app_user(id),
    event_type varchar(40) NOT NULL,
    reference_id uuid NOT NULL,
    recorded_at timestamptz NOT NULL
);
CREATE INDEX audit_portfolio_recorded ON audit_event(portfolio_id, recorded_at DESC, id);
CREATE FUNCTION reject_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Financial history is append-only';
END;
$$;
CREATE TRIGGER immutable_ledger BEFORE UPDATE OR DELETE ON cash_ledger FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER immutable_orders BEFORE UPDATE OR DELETE ON executed_order FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER immutable_allocations BEFORE UPDATE OR DELETE ON sell_allocation FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER immutable_audit BEFORE UPDATE OR DELETE ON audit_event FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
