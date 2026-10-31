SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teachers' AND column_name='auto_provisioned');
SET @ddl = IF(@exists_column=0, 'ALTER TABLE teachers ADD COLUMN auto_provisioned BOOLEAN NOT NULL DEFAULT FALSE', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
