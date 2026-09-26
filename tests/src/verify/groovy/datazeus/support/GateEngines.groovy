package datazeus.support

import groovy.sql.Sql
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.Blob

/**
 * The dual-engine publish gate's engines: ONE DuckDB + ONE PostgreSQL per dataset, opened on first
 * use and shared by every spec in the run. A dataset is a `.duckdb` file plus the schema its lessons
 * run on (null = the file's default schema), exactly the pair {@link GateSpec} names.
 *
 *   - duckdb   — a throwaway copy of the file, so the gate can't lock or change it: the SAME rows
 *                the koans run on.
 *   - postgres — PGHOST set: the learner's own PostgreSQL, where DataPallas installed the dataset
 *                (nothing is copied). Otherwise ONE Testcontainers postgres:16.2 for the whole run,
 *                into which each dataset's tables are copied with their declared types
 *                (DECIMAL(19,4) stays numeric(19,4), SMALLINT stays smallint, …), so rounding and
 *                division behave as they do after a real install. Each dataset lives in its own
 *                schema; a dataset with no schema goes to `public`.
 *
 * Both connections `SET search_path` to the schema, so lesson queries use plain table names. When
 * the dataset carries `_dataset_info`, BOTH engines are re-checksummed against it before any figure
 * is asserted: a spec never passes on data that is not the data the lessons were measured on.
 *
 * Starting PostgreSQL and copying is the expensive part, so it happens once per dataset per run;
 * teardown is at JVM shutdown, not per spec class.
 */
class GateEngines {

    // Pin to the version the DataPallas compose ships, so query semantics match CloudBeaver.
    static final String PG_IMAGE = "postgres:16.2"

    private static final Map<String, GateEngines> OPEN = [:]
    private static PostgreSQLContainer pgc   // null when asserting against a live PG (PGHOST set)

    final Sql duck
    final Sql pg
    private final File dbCopy

    /** The engines for `dataset` (a .duckdb path) on `schema`, opened the first time they are asked for. */
    static synchronized GateEngines of(String dataset, String schema) {
        OPEN.computeIfAbsent("${dataset}|${schema}".toString()) { new GateEngines(dataset, schema) }
    }

    private GateEngines(String dataset, String schema) {
        // The copy's file name becomes DuckDB's catalog name; it must not equal the schema, or
        // "northwind_co_s"."Orders" is ambiguous (catalog or schema). "gate-<file>-…" is safe.
        File src = new File(dataset)
        dbCopy = File.createTempFile("gate-${src.name - '.duckdb'}-", ".duckdb")
        Files.copy(src.toPath(), dbCopy.toPath(), StandardCopyOption.REPLACE_EXISTING)
        duck = Sql.newInstance("jdbc:duckdb:" + dbCopy.absolutePath, "org.duckdb.DuckDBDriver")
        if (schema) duck.execute("SET search_path = '${schema}'".toString())

        String duckSchema = schema ?: "main", pgSchema = schema ?: "public"
        List<String> tables = duck.rows("SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name",
                [duckSchema]).collect { it.table_name as String }

        String liveHost = System.getenv("PGHOST")
        if (liveHost) {
            String url = "jdbc:postgresql://${liveHost}:${env('PGPORT', '5432')}/${env('PGDATABASE', 'Northwind')}"
            pg = Sql.newInstance(url, env('PGUSER', 'postgres'), env('PGPASSWORD', 'postgres'), "org.postgresql.Driver")
            List<String> missing = tables - pg.rows("SELECT table_name FROM information_schema.tables WHERE table_schema = ?", [pgSchema]).collect { it.table_name as String }
            assert missing.isEmpty(): "PGHOST is set but ${url} has no ${missing.take(3)}… in schema ${pgSchema}: install " +
                    "${src.name}${schema ? ' (' + schema + ')' : ''} there first — tests/README.md says how."
        } else {
            pg = Sql.newInstance(container().jdbcUrl, pgc.username, pgc.password, "org.postgresql.Driver")
            copyTables(duck, duckSchema, pg, pgSchema, tables)
        }
        if (schema) pg.execute("SET search_path TO ${schema}".toString())

        if (schema && "_dataset_info" in tables) {
            ["duckdb": duck, "postgres": pg].each { engine, sql ->
                List<String> differ = DatasetChecksum.problems(sql, schema)
                assert differ.isEmpty(): "${engine}: ${schema} is not the dataset the lessons were measured on — " +
                        "reinstall it (DataPallas ▸ the connection ▸ Seed Data). Differs: ${differ}"
            }
        }

        Runtime.runtime.addShutdownHook(new Thread({
            try { duck.close() } catch (ignored) { }
            try { pg.close() } catch (ignored) { }
            try { dbCopy.delete() } catch (ignored) { }
        }))
    }

    /** The one Testcontainers PostgreSQL of the run, started on first use. */
    private static PostgreSQLContainer container() {
        if (pgc) return pgc
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            throw new IllegalStateException(
                "Running these tests needs Docker to be installed and started — they spin up PostgreSQL in a " +
                "throwaway container. Or set PGHOST to a PostgreSQL where the lessons' datasets are installed.")
        }
        pgc = new PostgreSQLContainer(DockerImageName.parse(PG_IMAGE))
        pgc.start()
        Runtime.runtime.addShutdownHook(new Thread({ try { pgc.stop() } catch (ignored) { } }))
        pgc
    }

    /** Copies the tables, with their declared column types, from a DuckDB schema into a PostgreSQL one. */
    private static void copyTables(Sql duck, String duckSchema, Sql pg, String pgSchema, List<String> tables) {
        pg.execute("CREATE SCHEMA IF NOT EXISTS ${q(pgSchema)}".toString())
        tables.each { table ->
            List cols = duck.rows('''SELECT column_name, data_type FROM information_schema.columns
                                     WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position''', [duckSchema, table])
            // .toString(): GString + String stays a GString, and Sql.execute would bind the
            // identifiers as ? parameters -- PostgreSQL then sees CREATE TABLE $1.$2.
            pg.execute(("CREATE TABLE ${q(pgSchema)}.${q(table)} (" + cols.collect { "${q(it.column_name as String)} ${pgType(it.data_type as String)}" }.join(", ") + ")").toString())
            String insert = "INSERT INTO ${q(pgSchema)}.${q(table)} VALUES (" + cols.collect { "?" }.join(", ") + ")"
            List<List> rows = []
            duck.eachRow("SELECT * FROM ${q(duckSchema)}.${q(table)}".toString()) { r ->
                rows << (1..cols.size()).collect { def v = r.getObject(it); v instanceof Blob ? v.getBytes(1, (int) v.length()) : v }
            }
            if (rows) pg.withBatch(1000, insert) { ps -> rows.each { ps.addBatch(it) } }
        }
    }

    private static String pgType(String duckType) {
        String t = duckType.toUpperCase()
        if (t.startsWith("DECIMAL")) return t.replace("DECIMAL", "numeric")
        switch (t) {
            case "INTEGER": return "integer"
            case "SMALLINT":
            case "TINYINT": return "smallint"
            case "BIGINT": return "bigint"
            case "HUGEINT": return "numeric(38,0)"
            case "BOOLEAN": return "boolean"
            case "DATE": return "date"
            case "TIME": return "time"
            case "TIMESTAMP": return "timestamp"
            case "TIMESTAMP WITH TIME ZONE": return "timestamptz"
            case "DOUBLE": return "double precision"
            case "FLOAT": return "real"
            case "BLOB": return "bytea"
            default: return "varchar"
        }
    }

    private static String q(String id) { '"' + id.replace('"', '""') + '"' }

    private static String env(String key, String fallback) {
        String v = System.getenv(key)
        (v == null || v.isEmpty()) ? fallback : v
    }
}
