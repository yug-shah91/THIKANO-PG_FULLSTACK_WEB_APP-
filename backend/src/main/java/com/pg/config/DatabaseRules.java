package com.pg.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Two database rules that JPA annotations can not express. Runs once at startup, safe to repeat.
 *
 *  1. A person can have MANY stays over time (one tenants row per stay), but only ONE active stay.
 *     That is a "partial unique index": unique(user_id) WHERE is_active = true.
 *  2. Older databases still have the old "unique(user_id)" constraint from when a user could only
 *     ever have one tenant row. It would block a second stay, so it is dropped.
 *
 * If the rules can not be applied (for example old data already has two active stays for one person),
 * the application REFUSES TO START with a clear message, instead of running unprotected.
 *
 * Two modes:
 *  - Flyway OFF (default, FLYWAY_ENABLED=false): this class APPLIES the rules, then verifies them.
 *  - Flyway ON  (FLYWAY_ENABLED=true): migration V2__one_active_stay_per_user.sql applies them
 *    (versioned, recorded in flyway_schema_history); this class only VERIFIES, as a safety net.
 * See README, "Flyway".
 */
@Component
@Order(0)
@Slf4j
public class DatabaseRules implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final boolean flywayAppliesRules;

    public DatabaseRules(JdbcTemplate jdbc, @Value("${spring.flyway.enabled:false}") boolean flywayEnabled) {
        this.jdbc = jdbc;
        this.flywayAppliesRules = flywayEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Any failure here STOPS the application on purpose. Running without this rule would let
        // two active stays exist for one person, and nobody would notice.
        if (!flywayAppliesRules) {
            apply();
        }
        verify();
        log.info("Database rule verified: one active stay per person");
    }

    private void apply() {
        List<String> legacy = jdbc.queryForList(
                "SELECT con.conname FROM pg_constraint con "
                        + "JOIN pg_class rel ON rel.oid = con.conrelid "
                        + "JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = ANY (con.conkey) "
                        + "WHERE rel.relname = 'tenants' AND con.contype = 'u' "
                        + "AND att.attname = 'user_id' AND array_length(con.conkey, 1) = 1", String.class);
        for (String name : legacy) {
            jdbc.execute("ALTER TABLE tenants DROP CONSTRAINT \"" + name.replace("\"", "") + "\"");
            log.info("Dropped old constraint {} so a person can have a second stay", name);
        }

        List<Long> duplicates = jdbc.queryForList(
                "SELECT user_id FROM tenants WHERE is_active = true GROUP BY user_id HAVING COUNT(*) > 1", Long.class);
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("Cannot start: these user ids have more than one ACTIVE stay in the "
                    + "tenants table: " + duplicates + ". Set is_active = false on the extra rows, then restart.");
        }

        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_one_active_stay "
                + "ON tenants (user_id) WHERE is_active = true");
    }

    /**
     * "CREATE INDEX IF NOT EXISTS" does nothing when an index with that NAME already exists, even if
     * it is the wrong index. So read the real definition back from PostgreSQL and compare every part:
     * table, one column (user_id), UNIQUE, valid, btree, and the WHERE condition (is_active = true).
     */
    private void verify() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT rel.relname AS tbl, i.indisunique AS uniq, i.indisvalid AS valid, i.indnatts AS cols, "
                        + "(SELECT a.attname FROM pg_attribute a WHERE a.attrelid = i.indrelid AND a.attnum = i.indkey[0]) AS col, "
                        + "pg_get_expr(i.indpred, i.indrelid) AS pred, pg_get_indexdef(i.indexrelid) AS def "
                        + "FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid JOIN pg_class rel ON rel.oid = i.indrelid "
                        + "WHERE c.relname = 'uq_tenant_one_active_stay' AND c.relnamespace = current_schema()::regnamespace");
        if (rows.size() != 1) {
            throw new IllegalStateException("Cannot start: the unique index uq_tenant_one_active_stay "
                    + "(one active stay per person) does not exist.");
        }
        Map<String, Object> r = rows.get(0);
        // PostgreSQL prints the condition as "is_active" or "(is_active = true)" depending on the version
        String pred = String.valueOf(r.get("pred")).toLowerCase().replaceAll("[()\\s]", "");
        boolean ok = "tenants".equals(r.get("tbl"))
                && Boolean.TRUE.equals(r.get("uniq"))
                && Boolean.TRUE.equals(r.get("valid"))
                && r.get("cols") instanceof Number cols && cols.intValue() == 1 // smallint may arrive as Short or Integer
                && "user_id".equals(r.get("col"))
                && String.valueOf(r.get("def")).toLowerCase().contains("using btree")
                && (pred.equals("is_active") || pred.equals("is_active=true"));
        if (!ok) {
            throw new IllegalStateException("Cannot start: index uq_tenant_one_active_stay exists but is NOT "
                    + "the expected \"UNIQUE (user_id) WHERE is_active = true\". Found: " + r.get("def")
                    + " (valid=" + r.get("valid") + "). Drop it and restart.");
        }
    }
}
