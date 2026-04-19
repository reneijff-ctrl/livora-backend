-- All production code has been migrated to chargeback_cases.
-- No service, repository, or foreign key references legacy_chargebacks at runtime.
-- Writes were frozen in a prior migration; this migration removes the table entirely.
DROP TABLE IF EXISTS legacy_chargebacks;
