ALTER TABLE wallet_transactions DROP CONSTRAINT wallet_transactions_type_check;
ALTER TABLE wallet_transactions ADD CONSTRAINT wallet_transactions_type_check CHECK (type IN ('PURCHASE', 'TIP', 'CHAT', 'BADGE', 'PRIVATE_SHOW', 'LIVESTREAM_ADMISSION', 'MEDIA_UNLOCK'));
