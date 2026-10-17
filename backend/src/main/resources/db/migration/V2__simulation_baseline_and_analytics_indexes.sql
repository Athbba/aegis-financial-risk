-- Additive baseline: preserve actual creation/funding timestamps and every immutable record.
ALTER TABLE portfolio ADD COLUMN simulation_start_date date NOT NULL DEFAULT DATE '2022-01-03';
ALTER TABLE portfolio ADD CONSTRAINT supported_simulation_start CHECK (simulation_start_date >= DATE '2022-01-03');
CREATE INDEX order_portfolio_historical ON executed_order(portfolio_id, price_date, recorded_at, id);
CREATE INDEX allocation_sell_order ON sell_allocation(sell_order_id);
