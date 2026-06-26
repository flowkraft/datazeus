package datazeus.support

import groovy.sql.Sql

import java.sql.ResultSetMetaData
import java.sql.Types

/**
 * Copies tables from the bundled DuckDB into a (throwaway) Postgres so the gate can run the
 * lesson's queries on a REAL Postgres engine with the EXACT same data.
 *
 * This is legitimate because both the shipped northwind.duckdb and the live DataPallas
 * Postgres are populated by the one NorthwindDataGenerator (see
 * NorthwindManager#initializeDatabaseWithGenerator, which runs for every vendor) — so the
 * duckdb IS the canonical data, and replaying it into Postgres reproduces what CloudBeaver
 * shows. Identifiers are preserved verbatim (quoted PascalCase, spaces ok e.g. "Order Details").
 *
 * Used only for the default Testcontainers path; when PGHOST points at the live DB we don't seed.
 */
class NorthwindSeed {

    static void copy(Sql duck, Sql pg, List<String> tables) {
        for (String table : tables) {
            copyTable(duck, pg, table)
        }
    }

    private static void copyTable(Sql duck, Sql pg, String table) {
        // 1) introspect columns (name + JDBC type) from the duckdb
        List<Map> cols = []
        duck.query('SELECT * FROM ' + q(table) + ' LIMIT 0') { rs ->
            ResultSetMetaData meta = rs.metaData
            for (int i = 1; i <= meta.columnCount; i++) {
                cols << [name: meta.getColumnName(i), type: pgType(meta.getColumnType(i))]
            }
        }

        // 2) create the matching table in Postgres
        String ddl = "CREATE TABLE ${q(table)} (" +
                cols.collect { "${q(it.name)} ${it.type}" }.join(", ") + ")"
        pg.execute(ddl)

        // 3) copy every row across
        String columnList = cols.collect { q(it.name) }.join(", ")
        String placeholders = cols.collect { "?" }.join(", ")
        String insert = "INSERT INTO ${q(table)} (${columnList}) VALUES (${placeholders})"

        List<List> rows = []
        duck.eachRow('SELECT * FROM ' + q(table)) { row ->
            rows << cols.collect { row[it.name] }
        }
        if (!rows.isEmpty()) {
            pg.withBatch(insert) { ps -> rows.each { ps.addBatch(it) } }
        }
    }

    /** Map a java.sql.Types code (read from the duckdb result) to a Postgres column type. */
    private static String pgType(int sqlType) {
        switch (sqlType) {
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
                return "integer"
            case Types.BIGINT:
                return "bigint"
            case Types.DECIMAL:
            case Types.NUMERIC:
                return "numeric"
            case Types.DOUBLE:
            case Types.FLOAT:
            case Types.REAL:
                return "double precision"
            case Types.BOOLEAN:
            case Types.BIT:
                return "boolean"
            case Types.DATE:
                return "date"
            case Types.TIME:
                return "time"
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return "timestamp"
            default:
                return "text"
        }
    }

    /** Quote an identifier for SQL, doubling any embedded quotes. */
    private static String q(String id) {
        '"' + id.replace('"', '""') + '"'
    }
}
