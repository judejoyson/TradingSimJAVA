ALTER TABLE paper_accounts
    ADD COLUMN mode VARCHAR(20) DEFAULT 'NORMAL' NOT NULL;

UPDATE paper_accounts
SET cash = cash - 150000.00 - total_deposits,
    starting_cash = 100000.00,
    total_deposits = 0,
    mode = 'NORMAL';
