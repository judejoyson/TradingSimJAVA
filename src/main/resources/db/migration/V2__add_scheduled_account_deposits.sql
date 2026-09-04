ALTER TABLE paper_accounts
    ADD COLUMN last_deposit_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE paper_accounts
    ADD COLUMN total_deposits NUMERIC(19, 2) DEFAULT 0 NOT NULL;

UPDATE paper_accounts
SET starting_cash = 250000.00,
    cash = cash + 150000.00,
    last_deposit_at = CURRENT_TIMESTAMP;

ALTER TABLE paper_accounts
    ALTER COLUMN last_deposit_at SET NOT NULL;
