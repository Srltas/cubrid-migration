package com.cmt.e2e.framework.assertion;

import static com.cmt.e2e.framework.assertion.DatabaseAsserts.row;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.cmt.e2e.framework.assertion.DatabaseAsserts.QueryExpectation;
import com.cmt.e2e.framework.db.containers.DatabaseContainer;

/**
 * Assertions for CUBRID catalog metadata created by CMT.
 *
 * <p>These checks intentionally verify a selected contract, not a full schema
 * diff. The test that calls this helper decides which objects matter for the
 * scenario.
 */
public final class CubridMetadataAsserts {
    private static final String CATALOG_USER = "dba";

    private CubridMetadataAsserts() {}

    public record ClassExpectation(String ownerName, String className, String classType) {}

    public record ColumnExpectation(
        String tableName,
        String columnName,
        int order,
        String dataType,
        int precision,
        int scale,
        String nullable
    ) {}

    public record IndexExpectation(
        String tableName,
        String indexName,
        String unique,
        String primaryKey,
        String foreignKey,
        int keyCount,
        String functionBased
    ) {}

    public record IndexKeyExpectation(
        String tableName,
        String indexName,
        String columnName,
        int order,
        String direction,
        String function
    ) {}

    public record SerialExpectation(
        String name,
        String currentValue,
        String incrementValue,
        String minValue
    ) {}

    public record SynonymExpectation(
        String synonymOwner,
        String synonymName,
        String targetOwner,
        String targetName
    ) {}

    public record GrantExpectation(
        String grantorName,
        String granteeName,
        String objectType,
        String objectName,
        String ownerName,
        String authType,
        String grantable
    ) {}

    public record RoutineExpectation(
        String ownerName,
        String routineName,
        String routineType,
        String authId
    ) {}

    public static ClassExpectation clazz(String ownerName, String className, String classType) {
        return new ClassExpectation(ownerName, className, classType);
    }

    public static ColumnExpectation column(
        String tableName,
        String columnName,
        int order,
        String dataType,
        int precision,
        int scale,
        String nullable
    ) {
        return new ColumnExpectation(tableName, columnName, order, dataType, precision, scale, nullable);
    }

    public static IndexExpectation index(
        String tableName,
        String indexName,
        boolean unique,
        boolean primaryKey,
        boolean foreignKey,
        int keyCount,
        boolean functionBased
    ) {
        return new IndexExpectation(
            tableName,
            indexName,
            yesNo(unique),
            yesNo(primaryKey),
            yesNo(foreignKey),
            keyCount,
            yesNo(functionBased)
        );
    }

    public static IndexKeyExpectation indexKey(
        String tableName,
        String indexName,
        String columnName,
        int order,
        String direction
    ) {
        return new IndexKeyExpectation(tableName, indexName, columnName, order, direction, null);
    }

    public static IndexKeyExpectation functionIndexKey(
        String tableName,
        String indexName,
        int order,
        String direction,
        String function
    ) {
        return new IndexKeyExpectation(tableName, indexName, null, order, direction, function);
    }

    public static SerialExpectation serial(
        String name,
        String currentValue,
        String incrementValue,
        String minValue
    ) {
        return new SerialExpectation(name, currentValue, incrementValue, minValue);
    }

    public static SynonymExpectation synonym(
        String synonymOwner,
        String synonymName,
        String targetOwner,
        String targetName
    ) {
        return new SynonymExpectation(synonymOwner, synonymName, targetOwner, targetName);
    }

    public static GrantExpectation grant(
        String grantorName,
        String granteeName,
        String objectType,
        String objectName,
        String ownerName,
        String authType,
        String grantable
    ) {
        return new GrantExpectation(grantorName, granteeName, objectType, objectName, ownerName, authType, grantable);
    }

    public static RoutineExpectation routine(
        String ownerName,
        String routineName,
        String routineType,
        String authId
    ) {
        return new RoutineExpectation(ownerName, routineName, routineType, authId);
    }

    public static void expectClasses(
        DatabaseContainer dbContainer,
        String dbName,
        List<ClassExpectation> expectations
    ) {
        Set<String> owners = new LinkedHashSet<>();
        Set<String> classes = new LinkedHashSet<>();
        for (ClassExpectation expectation : expectations) {
            owners.add(expectation.ownerName());
            classes.add(expectation.className());
        }

        DatabaseAsserts.expectQueryResults(dbContainer, dbName, CATALOG_USER, List.of(
            new QueryExpectation(
                "CUBRID classes",
                """
                SELECT owner_name, class_name, class_type
                FROM db_class
                WHERE owner_name IN (%s)
                  AND class_name IN (%s)
                ORDER BY owner_name, class_type, class_name
                """.formatted(sqlIn(owners), sqlIn(classes)),
                expectations.stream()
                    .map(e -> row(e.ownerName(), e.className(), e.classType()))
                    .toList()
            )
        ));
    }

    public static void expectColumns(
        DatabaseContainer dbContainer,
        String dbName,
        String ownerName,
        List<ColumnExpectation> expectations
    ) {
        Set<String> tableNames = new LinkedHashSet<>();
        for (ColumnExpectation expectation : expectations) {
            tableNames.add(expectation.tableName());
        }

        DatabaseAsserts.expectQueryResults(dbContainer, dbName, CATALOG_USER, List.of(
            new QueryExpectation(
                "CUBRID columns of " + ownerName,
                """
                SELECT class_name, attr_name, def_order, data_type, prec, scale, is_nullable
                FROM db_attribute
                WHERE owner_name = %s
                  AND class_name IN (%s)
                ORDER BY class_name, def_order
                """.formatted(sqlString(ownerName), sqlIn(tableNames)),
                expectations.stream()
                    .map(e -> row(
                        e.tableName(),
                        e.columnName(),
                        Integer.toString(e.order()),
                        e.dataType(),
                        Integer.toString(e.precision()),
                        Integer.toString(e.scale()),
                        e.nullable()))
                    .toList()
            )
        ));
    }

    public static void expectIndexes(
        DatabaseContainer dbContainer,
        String dbName,
        String ownerName,
        List<IndexExpectation> expectations
    ) {
        Set<String> tableNames = new LinkedHashSet<>();
        for (IndexExpectation expectation : expectations) {
            tableNames.add(expectation.tableName());
        }

        DatabaseAsserts.expectQueryResults(dbContainer, dbName, CATALOG_USER, List.of(
            new QueryExpectation(
                "CUBRID indexes of " + ownerName,
                """
                SELECT class_name, index_name, is_unique, is_primary_key, is_foreign_key, key_count, have_function
                FROM db_index
                WHERE owner_name = %s
                  AND class_name IN (%s)
                ORDER BY class_name, index_name
                """.formatted(sqlString(ownerName), sqlIn(tableNames)),
                expectations.stream()
                    .map(e -> row(
                        e.tableName(),
                        e.indexName(),
                        e.unique(),
                        e.primaryKey(),
                        e.foreignKey(),
                        Integer.toString(e.keyCount()),
                        e.functionBased()))
                    .toList()
            )
        ));
    }

    public static void expectIndexKeys(
        DatabaseContainer dbContainer,
        String dbName,
        String ownerName,
        List<IndexKeyExpectation> expectations
    ) {
        Set<String> tableNames = new LinkedHashSet<>();
        Set<String> indexNames = new LinkedHashSet<>();
        for (IndexKeyExpectation expectation : expectations) {
            tableNames.add(expectation.tableName());
            indexNames.add(expectation.indexName());
        }

        DatabaseAsserts.expectQueryResults(dbContainer, dbName, CATALOG_USER, List.of(
            new QueryExpectation(
                "CUBRID index keys of " + ownerName,
                """
                SELECT class_name, index_name, key_attr_name, key_order, asc_desc, func
                FROM db_index_key
                WHERE owner_name = %s
                  AND class_name IN (%s)
                  AND index_name IN (%s)
                ORDER BY class_name, index_name, key_order
                """.formatted(sqlString(ownerName), sqlIn(tableNames), sqlIn(indexNames)),
                expectations.stream()
                    .map(e -> row(
                        e.tableName(),
                        e.indexName(),
                        e.columnName(),
                        Integer.toString(e.order()),
                        e.direction(),
                        e.function()))
                    .toList()
            )
        ));
    }

    public static void expectSerials(
        DatabaseContainer dbContainer,
        String dbName,
        List<SerialExpectation> expectations
    ) {
        Set<String> serialNames = new LinkedHashSet<>();
        for (SerialExpectation expectation : expectations) {
            serialNames.add(expectation.name());
        }

        DatabaseAsserts.expectQueryResults(dbContainer, dbName, CATALOG_USER, List.of(
            new QueryExpectation(
                "CUBRID serials",
                """
                SELECT name, current_val, increment_val, min_val
                FROM db_serial
                WHERE name IN (%s)
                ORDER BY name
                """.formatted(sqlIn(serialNames)),
                expectations.stream()
                    .map(e -> row(e.name(), e.currentValue(), e.incrementValue(), e.minValue()))
                    .toList()
            )
        ));
    }

    public static void expectSynonyms(
        DatabaseContainer dbContainer,
        String dbName,
        List<SynonymExpectation> expectations
    ) {
        Set<String> synonymOwners = new LinkedHashSet<>();
        Set<String> synonymNames = new LinkedHashSet<>();
        for (SynonymExpectation expectation : expectations) {
            synonymOwners.add(expectation.synonymOwner());
            synonymNames.add(expectation.synonymName());
        }

        DatabaseAsserts.expectQueryResults(dbContainer, dbName, CATALOG_USER, List.of(
            new QueryExpectation(
                "CUBRID synonyms",
                """
                SELECT synonym_owner_name, synonym_name, target_owner_name, target_name
                FROM db_synonym
                WHERE synonym_owner_name IN (%s)
                  AND synonym_name IN (%s)
                ORDER BY synonym_owner_name, synonym_name
                """.formatted(sqlIn(synonymOwners), sqlIn(synonymNames)),
                expectations.stream()
                    .map(e -> row(e.synonymOwner(), e.synonymName(), e.targetOwner(), e.targetName()))
                    .toList()
            )
        ));
    }

    public static void expectGrants(
        DatabaseContainer dbContainer,
        String dbName,
        List<GrantExpectation> expectations
    ) {
        Set<String> grantors = new LinkedHashSet<>();
        Set<String> grantees = new LinkedHashSet<>();
        Set<String> objectNames = new LinkedHashSet<>();
        Set<String> ownerNames = new LinkedHashSet<>();
        for (GrantExpectation expectation : expectations) {
            grantors.add(expectation.grantorName());
            grantees.add(expectation.granteeName());
            objectNames.add(expectation.objectName());
            ownerNames.add(expectation.ownerName());
        }

        DatabaseAsserts.expectQueryResults(dbContainer, dbName, CATALOG_USER, List.of(
            new QueryExpectation(
                "CUBRID grants",
                """
                SELECT grantor_name, grantee_name, object_type, object_name, owner_name, auth_type, is_grantable
                FROM db_auth
                WHERE grantor_name IN (%s)
                  AND grantee_name IN (%s)
                  AND object_name IN (%s)
                  AND owner_name IN (%s)
                ORDER BY grantor_name, grantee_name, owner_name, object_name, auth_type
                """.formatted(sqlIn(grantors), sqlIn(grantees), sqlIn(objectNames), sqlIn(ownerNames)),
                expectations.stream()
                    .map(e -> row(
                        e.grantorName(),
                        e.granteeName(),
                        e.objectType(),
                        e.objectName(),
                        e.ownerName(),
                        e.authType(),
                        e.grantable()))
                    .toList()
            )
        ));
    }

    public static void expectRoutines(
        DatabaseContainer dbContainer,
        String dbName,
        List<RoutineExpectation> expectations
    ) {
        Set<String> owners = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        for (RoutineExpectation expectation : expectations) {
            owners.add(expectation.ownerName());
            names.add(expectation.routineName());
        }

        DatabaseAsserts.expectQueryResults(dbContainer, dbName, CATALOG_USER, List.of(
            new QueryExpectation(
                "CUBRID stored routines",
                """
                SELECT owner, sp_name, sp_type, authid
                FROM db_stored_procedure
                WHERE owner IN (%s)
                  AND sp_name IN (%s)
                ORDER BY sp_type, sp_name
                """.formatted(sqlIn(owners), sqlIn(names)),
                expectations.stream()
                    .map(e -> row(e.ownerName(), e.routineName(), e.routineType(), e.authId()))
                    .toList()
            )
        ));
    }

    private static String yesNo(boolean value) {
        return value ? "YES" : "NO";
    }

    private static String sqlIn(Set<String> values) {
        return values.stream()
            .map(CubridMetadataAsserts::sqlString)
            .reduce((left, right) -> left + ", " + right)
            .orElseThrow(() -> new IllegalArgumentException("SQL IN values must not be empty."));
    }

    private static String sqlString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SQL string value must not be blank.");
        }
        return "'" + value.replace("'", "''") + "'";
    }
}
