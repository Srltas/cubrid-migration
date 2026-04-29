package com.cmt.e2e.framework.verify;

import java.util.Map;

/**
 * Canonical CUBRID catalog queries used by {@link CatalogSnapshot}.
 *
 * <p>Each query:
 * <ul>
 *   <li>Has an explicit {@code ORDER BY} so output is deterministic
 *       (ARCHITECTURE.md §7).</li>
 *   <li>Filters out system owners ({@code DBA}, {@code PUBLIC}) so user
 *       objects don't drown in noise.</li>
 *   <li>Returns columns that PoC's {@code CubridMetadataAsserts}
 *       exercised, with {@code owner_name} added where the PoC relied on
 *       caller-supplied schema parameter.</li>
 * </ul>
 *
 * <p>The keys here ({@code "classes"}, {@code "indexes"} etc.) double as
 * snapshot file names: {@code snapshots/<scenario>/<key>.txt}.
 */
public final class CatalogQueries {

    private CatalogQueries() {}

    // class_name NOT LIKE 'flyway_%' filters Flyway-generated metadata
    // (flyway_schema_history + its index) from class/column/index queries.
    // Flyway is a seed implementation detail, not part of the migration
    // contract; filtering here keeps snapshots focused on user objects.

    private static final Map<String, String> QUERIES = Map.of(
        "classes", """
            SELECT owner_name, class_name, class_type
            FROM db_class
            WHERE owner_name NOT IN ('DBA', 'PUBLIC')
              AND class_name NOT LIKE 'flyway_%'
            ORDER BY owner_name, class_type, class_name
            """,
        "columns", """
            SELECT owner_name, class_name, attr_name, def_order,
                   data_type, prec, scale, is_nullable
            FROM db_attribute
            WHERE owner_name NOT IN ('DBA', 'PUBLIC')
              AND class_name NOT LIKE 'flyway_%'
            ORDER BY owner_name, class_name, def_order
            """,
        "indexes", """
            SELECT owner_name, class_name, index_name, is_unique,
                   is_primary_key, is_foreign_key, key_count, have_function
            FROM db_index
            WHERE owner_name NOT IN ('DBA', 'PUBLIC')
              AND class_name NOT LIKE 'flyway_%'
            ORDER BY owner_name, class_name, index_name
            """,
        "index_keys", """
            SELECT owner_name, class_name, index_name, key_attr_name,
                   key_order, asc_desc, func
            FROM db_index_key
            WHERE owner_name NOT IN ('DBA', 'PUBLIC')
              AND class_name NOT LIKE 'flyway_%'
            ORDER BY owner_name, class_name, index_name, key_order
            """,
        "serials", """
            SELECT name, current_val, increment_val, min_val
            FROM db_serial
            ORDER BY name
            """,
        "synonyms", """
            SELECT synonym_owner_name, synonym_name,
                   target_owner_name, target_name
            FROM db_synonym
            WHERE synonym_owner_name NOT IN ('DBA', 'PUBLIC')
            ORDER BY synonym_owner_name, synonym_name
            """,
        "grants", """
            SELECT grantor_name, grantee_name, object_type, object_name,
                   owner_name, auth_type, is_grantable
            FROM db_auth
            WHERE owner_name NOT IN ('DBA', 'PUBLIC')
            ORDER BY grantor_name, grantee_name, owner_name, object_name, auth_type
            """,
        "routines", """
            SELECT owner, sp_name, sp_type, authid
            FROM db_stored_procedure
            WHERE owner NOT IN ('DBA', 'PUBLIC')
            ORDER BY owner, sp_type, sp_name
            """
    );

    /**
     * Looks up a query by snapshot name.
     *
     * @throws IllegalArgumentException if no query with that name is registered
     */
    public static String byName(String name) {
        String sql = QUERIES.get(name);
        if (sql == null) {
            throw new IllegalArgumentException(
                "Unknown catalog query: '" + name + "'. Known: " + QUERIES.keySet());
        }
        return sql;
    }

    /** All known query names — used by {@link CatalogSnapshot#matchesAllSnapshots()}. */
    public static java.util.Set<String> names() {
        return QUERIES.keySet();
    }
}
