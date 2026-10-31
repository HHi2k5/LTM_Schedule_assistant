SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scheduling_entries' AND column_name='entry_key');
SET @ddl = IF(@exists_column=0, 'ALTER TABLE scheduling_entries ADD COLUMN entry_key VARCHAR(50) NULL', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
UPDATE scheduling_entries SET entry_key=CONCAT('LEGACY_', id) WHERE entry_key IS NULL;
ALTER TABLE scheduling_entries MODIFY COLUMN entry_key VARCHAR(50) NOT NULL;
SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scheduling_entries' AND column_name='locked');
SET @ddl = IF(@exists_column=0, 'ALTER TABLE scheduling_entries ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
SET @exists_index = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scheduling_entries' AND index_name='uq_version_entry_key');
SET @ddl = IF(@exists_index=0, 'ALTER TABLE scheduling_entries ADD CONSTRAINT uq_version_entry_key UNIQUE(version_id, entry_key)', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scheduling_plan_versions' AND column_name='adjustment_json');
SET @ddl = IF(@exists_column=0, 'ALTER TABLE scheduling_plan_versions ADD COLUMN adjustment_json LONGTEXT NULL', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scheduling_plan_versions' AND column_name='customized');
SET @ddl = IF(@exists_column=0, 'ALTER TABLE scheduling_plan_versions ADD COLUMN customized BOOLEAN NOT NULL DEFAULT FALSE', 'SELECT 1');
PREPARE upgrade_statement FROM @ddl;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;
UPDATE scheduling_plan_versions SET customized=TRUE WHERE visiting_change_json IS NOT NULL;
CREATE TABLE IF NOT EXISTS scheduling_changes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    source_version_id BIGINT NULL,
    result_version_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    actor VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    affected_entries INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    scope_json LONGTEXT NOT NULL,
    CONSTRAINT fk_change_plan FOREIGN KEY(plan_id) REFERENCES scheduling_plans(id),
    CONSTRAINT fk_change_source FOREIGN KEY(source_version_id) REFERENCES scheduling_plan_versions(id),
    CONSTRAINT fk_change_result FOREIGN KEY(result_version_id) REFERENCES scheduling_plan_versions(id),
    CONSTRAINT uq_change_result_action UNIQUE(result_version_id, action),
    INDEX ix_change_plan_time(plan_id, created_at)
);
