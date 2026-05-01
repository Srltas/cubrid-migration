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
 * <p>The keys here ({@code "classes"}, {@code "pk"} etc.) double as
 * snapshot file names: {@code snapshots/<scenario>/<key>.txt}.
 *
 * <p><b>Index queries — split by kind, with key columns inlined.</b>
 * {@code db_index} carries every index kind in one row set with
 * {@code is_primary_key}/{@code is_unique}/{@code is_foreign_key}/
 * {@code have_function} flags. Splitting into one snapshot per kind
 * makes a diff immediately tell you <em>which</em> category broke
 * (PK / FK / UK / plain) without mentally scanning flag columns. Each
 * per-kind query also joins {@code db_index_key} so the key columns
 * (and their order, asc/desc, and function expression) live in the
 * same file as the index identity — no need to bounce between
 * {@code pk.txt} and a separate {@code index_keys.txt}.
 *
 * <p>The categories are mutually exclusive and exhaustive:
 * <ul>
 *   <li>{@code pk}     — {@code is_primary_key = 'YES'}</li>
 *   <li>{@code fk}     — {@code is_foreign_key = 'YES'}</li>
 *   <li>{@code unique} — {@code is_unique = 'YES' AND is_primary_key = 'NO'}
 *       (unique non-PK)</li>
 *   <li>{@code indexes} — everything else (plain non-unique non-FK,
 *       including descending {@code idxd_*} and functional {@code idxf_*})</li>
 * </ul>
 *
 * <p>Per-bucket schema:
 * <ul>
 *   <li>PK / FK / UK — {@code (owner, class, index_name, key_attr_name,
 *       key_order, asc_desc)}. {@code func} is dropped because it is
 *       always NULL for these kinds. {@code asc_desc} is kept for
 *       schema uniformity even though PK/FK/UK keys are conventionally
 *       ASC.</li>
 *   <li>plain {@code indexes} — adds {@code func} since functional
 *       indexes ({@code idxf_*}) carry their expression here.</li>
 * </ul>
 * Composite indexes produce one row per key column; row count is the
 * key count, so an explicit {@code key_count} column is unnecessary.
 */
public final class CatalogQueries {

    private CatalogQueries() {}

    // class_name NOT LIKE 'flyway_%' filters Flyway-generated metadata
    // (flyway_schema_history + its index) from class/column/index queries.
    // Flyway is a seed implementation detail, not part of the migration
    // contract; filtering here keeps snapshots focused on user objects.

    private static final Map<String, String> QUERIES = Map.ofEntries(
        Map.entry("classes", """
            SELECT owner_name, class_name, class_type
            FROM db_class
            WHERE owner_name NOT IN ('DBA', 'PUBLIC')
              AND class_name NOT LIKE 'flyway_%'
            ORDER BY owner_name, class_type, class_name
            """),
        Map.entry("columns", """
            SELECT owner_name, class_name, attr_name, def_order,
                   data_type, prec, scale, is_nullable
            FROM db_attribute
            WHERE owner_name NOT IN ('DBA', 'PUBLIC')
              AND class_name NOT LIKE 'flyway_%'
            ORDER BY owner_name, class_name, def_order
            """),
        Map.entry("pk", """
            SELECT i.owner_name, i.class_name, i.index_name,
                   k.key_attr_name, k.key_order, k.asc_desc
            FROM db_index i
            JOIN db_index_key k
              ON k.owner_name = i.owner_name
             AND k.class_name = i.class_name
             AND k.index_name = i.index_name
            WHERE i.owner_name NOT IN ('DBA', 'PUBLIC')
              AND i.class_name NOT LIKE 'flyway_%'
              AND i.is_primary_key = 'YES'
            ORDER BY i.owner_name, i.class_name, i.index_name, k.key_order
            """),
        Map.entry("fk", """
            SELECT i.owner_name, i.class_name, i.index_name,
                   k.key_attr_name, k.key_order, k.asc_desc
            FROM db_index i
            JOIN db_index_key k
              ON k.owner_name = i.owner_name
             AND k.class_name = i.class_name
             AND k.index_name = i.index_name
            WHERE i.owner_name NOT IN ('DBA', 'PUBLIC')
              AND i.class_name NOT LIKE 'flyway_%'
              AND i.is_foreign_key = 'YES'
            ORDER BY i.owner_name, i.class_name, i.index_name, k.key_order
            """),
        Map.entry("unique", """
            SELECT i.owner_name, i.class_name, i.index_name,
                   k.key_attr_name, k.key_order, k.asc_desc
            FROM db_index i
            JOIN db_index_key k
              ON k.owner_name = i.owner_name
             AND k.class_name = i.class_name
             AND k.index_name = i.index_name
            WHERE i.owner_name NOT IN ('DBA', 'PUBLIC')
              AND i.class_name NOT LIKE 'flyway_%'
              AND i.is_unique = 'YES'
              AND i.is_primary_key = 'NO'
            ORDER BY i.owner_name, i.class_name, i.index_name, k.key_order
            """),
        Map.entry("indexes", """
            SELECT i.owner_name, i.class_name, i.index_name,
                   k.key_attr_name, k.key_order, k.asc_desc, k.func
            FROM db_index i
            JOIN db_index_key k
              ON k.owner_name = i.owner_name
             AND k.class_name = i.class_name
             AND k.index_name = i.index_name
            WHERE i.owner_name NOT IN ('DBA', 'PUBLIC')
              AND i.class_name NOT LIKE 'flyway_%'
              AND i.is_unique = 'NO'
              AND i.is_foreign_key = 'NO'
            ORDER BY i.owner_name, i.class_name, i.index_name, k.key_order
            """),
        Map.entry("serials", """
            SELECT name, current_val, increment_val, min_val
            FROM db_serial
            ORDER BY name
            """),
        Map.entry("synonyms", """
            SELECT synonym_owner_name, synonym_name,
                   target_owner_name, target_name
            FROM db_synonym
            WHERE synonym_owner_name NOT IN ('DBA', 'PUBLIC')
            ORDER BY synonym_owner_name, synonym_name
            """),
        Map.entry("grants", """
            SELECT grantor_name, grantee_name, object_type, object_name,
                   owner_name, auth_type, is_grantable
            FROM db_auth
            WHERE owner_name NOT IN ('DBA', 'PUBLIC')
            ORDER BY grantor_name, grantee_name, owner_name, object_name, auth_type
            """),
        Map.entry("routines", """
            SELECT owner, sp_name, sp_type, authid
            FROM db_stored_procedure
            WHERE owner NOT IN ('DBA', 'PUBLIC')
            ORDER BY owner, sp_type, sp_name
            """)
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
