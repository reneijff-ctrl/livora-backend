-- Add 'CRITICAL' to various risk level check constraints

-- rule_fraud_signals
ALTER TABLE rule_fraud_signals DROP CONSTRAINT rule_fraud_signals_risk_level_check;
ALTER TABLE rule_fraud_signals ADD CONSTRAINT rule_fraud_signals_risk_level_check 
    CHECK (risk_level = ANY (ARRAY['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']::text[]));

-- fraud_decisions
ALTER TABLE fraud_decisions DROP CONSTRAINT fraud_decisions_risk_level_check;
ALTER TABLE fraud_decisions ADD CONSTRAINT fraud_decisions_risk_level_check 
    CHECK (risk_level = ANY (ARRAY['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']::text[]));

-- fraud_risk_assessments
ALTER TABLE fraud_risk_assessments DROP CONSTRAINT fraud_risk_assessments_risk_level_check;
ALTER TABLE fraud_risk_assessments ADD CONSTRAINT fraud_risk_assessments_risk_level_check 
    CHECK (risk_level = ANY (ARRAY['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']::text[]));

-- fraud_signals
ALTER TABLE fraud_signals DROP CONSTRAINT fraud_signals_risk_level_check;
ALTER TABLE fraud_signals ADD CONSTRAINT fraud_signals_risk_level_check 
    CHECK (risk_level = ANY (ARRAY['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']::text[]));

-- risk_decision_audits
ALTER TABLE risk_decision_audits DROP CONSTRAINT risk_decision_audits_risk_level_check;
ALTER TABLE risk_decision_audits ADD CONSTRAINT risk_decision_audits_risk_level_check 
    CHECK (risk_level = ANY (ARRAY['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']::text[]));

-- users (fraud_risk_level)
ALTER TABLE users DROP CONSTRAINT users_fraud_risk_level_check;
ALTER TABLE users ADD CONSTRAINT users_fraud_risk_level_check 
    CHECK (fraud_risk_level = ANY (ARRAY['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']::text[]));

-- payout_holds
ALTER TABLE payout_holds DROP CONSTRAINT payout_holds_risk_level_check;
ALTER TABLE payout_holds ADD CONSTRAINT payout_holds_risk_level_check 
    CHECK (risk_level = ANY (ARRAY['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']::text[]));

-- payout_hold_policies
ALTER TABLE payout_hold_policies DROP CONSTRAINT payout_hold_policies_risk_level_check;
ALTER TABLE payout_hold_policies ADD CONSTRAINT payout_hold_policies_risk_level_check 
    CHECK (risk_level = ANY (ARRAY['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']::text[]));
