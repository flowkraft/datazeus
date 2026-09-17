package datazeus.support

import groovy.sql.Sql
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Process-wide singleton for the Northwind Company gate (Learn SQL Series 2 onwards): ONE DuckDB
 * and ONE PostgreSQL, both on schema `northwind_co_s`, shared by every spec in the run — the
 * Northwind Company twin of {@link NorthwindEngines}.
 *
 *   - duckdb   — a throwaway copy of datasets/northwind-co/northwind_co.duckdb, the file the
 *                Series 2 koans run on. It is exactly the output of the Seed Data script
 *                academy-northwind-co-install.groovy run on a DuckDB connection.
 *   - postgres — PGHOST set: the learner's own PostgreSQL, where the Seed Data tab installed
 *                `northwind_co_s` (nothing is seeded). Otherwise ONE Testcontainers postgres:16.2,
 *                into which the DuckDB tables are copied with their declared types
 *                (DECIMAL(19,4) stays numeric(19,4), SMALLINT stays smallint, …), so rounding
 *                and division behave as they do after a real install.
 *
 * Both connections run `SET search_path` to `northwind_co_s`, as the lesson tells the learner to,
 * so every lesson query is written with Northwind's plain table names. Before any figure is
 * asserted, BOTH engines are re-checksummed against `_dataset_info`: a spec never passes on data
 * that is not Northwind Company S v1.
 */
class NorthwindCoEngines {

    static final String SCHEMA = "northwind_co_s"
    static final String PG_IMAGE = "postgres:16.2"

    private static Sql duckdb
    private static Sql postgres
    private static PostgreSQLContainer pgc
    private static File dbCopy
    private static boolean ready = false

    static synchronized void init() {
        if (ready)
            return

        // The copy's file name becomes DuckDB's catalog name; it must not be "northwind_co_s", or
        // "northwind_co_s"."Orders" is ambiguous (catalog or schema). "gate-northwind-co-…" is safe.
        File src = new File("../datasets/northwind-co/northwind_co.duckdb")
        dbCopy = File.createTempFile("gate-northwind-co-", ".duckdb")
        Files.copy(src.toPath(), dbCopy.toPath(), StandardCopyOption.REPLACE_EXISTING)
        duckdb = Sql.newInstance("jdbc:duckdb:" + dbCopy.absolutePath, "org.duckdb.DuckDBDriver")
        duckdb.execute("SET search_path = '${SCHEMA}'".toString())

        String liveHost = System.getenv("PGHOST")
        if (liveHost) {
            String url = "jdbc:postgresql://${liveHost}:${env('PGPORT', '5432')}/${env('PGDATABASE', 'Northwind')}"
            postgres = Sql.newInstance(url, env('PGUSER', 'postgres'), env('PGPASSWORD', 'postgres'), "org.postgresql.Driver")
            assert postgres.firstRow("SELECT count(*) AS n FROM information_schema.tables WHERE table_schema = ? AND table_name = '_dataset_info'", [SCHEMA]).n == 1:
                    "PGHOST is set but ${url} has no ${SCHEMA} — install it: DataPallas ▸ the PostgreSQL connection ▸ " +
                    "Seed Data ▸ 'Academy dataset: Northwind Company' ▸ Run."
        } else {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                throw new IllegalStateException(
                    "Running these tests needs Docker to be installed and started — they spin up PostgreSQL in a " +
                    "throwaway container. Or set PGHOST to a PostgreSQL where ${SCHEMA} is installed.")
            }
            pgc = new PostgreSQLContainer(DockerImageName.parse(PG_IMAGE))
            pgc.start()
            postgres = Sql.newInstance(pgc.jdbcUrl, pgc.username, pgc.password, "org.postgresql.Driver")
            copySchema(duckdb, postgres)
        }
        postgres.execute("SET search_path TO ${SCHEMA}".toString())

        ["duckdb": duckdb, "postgres": postgres].each { engine, sql ->
            List<String> differ = DatasetChecksum.problems(sql, SCHEMA)
            assert differ.isEmpty(): "${engine}: ${SCHEMA} is not the Northwind Company S the lessons were measured on — " +
                    "reinstall it (Seed Data ▸ Academy dataset: Northwind Company). Differs: ${differ}"
        }

        Runtime.runtime.addShutdownHook(new Thread({
            try { duckdb?.close() } catch (ignored) { }
            try { postgres?.close() } catch (ignored) { }
            try { pgc?.stop() } catch (ignored) { }
            try { dbCopy?.delete() } catch (ignored) { }
        }))

        ready = true
    }

    static Sql duck() { init(); duckdb }

    static Sql pg() { init(); postgres }

    /** Copies every table of the schema, with its declared column types, from DuckDB into PostgreSQL. */
    private static void copySchema(Sql duck, Sql pg) {
        pg.execute("CREATE SCHEMA ${SCHEMA}".toString())
        List<String> tables = duck.rows("SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name",
                [SCHEMA]).collect { it.table_name as String }
        tables.each { table ->
            List cols = duck.rows('''SELECT column_name, data_type FROM information_schema.columns
                                     WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position''', [SCHEMA, table])
            pg.execute("CREATE TABLE ${SCHEMA}.${q(table)} (" + cols.collect { "${q(it.column_name as String)} ${pgType(it.data_type as String)}" }.join(", ") + ")")
            String insert = "INSERT INTO ${SCHEMA}.${q(table)} VALUES (" + cols.collect { "?" }.join(", ") + ")"
            List<List> rows = []
            duck.eachRow("SELECT * FROM ${SCHEMA}.${q(table)}".toString()) { r -> rows << (1..cols.size()).collect { r.getObject(it) } }
            if (rows) pg.withBatch(1000, insert) { ps -> rows.each { ps.addBatch(it) } }
        }
    }

    private static String pgType(String duckType) {
        String t = duckType.toUpperCase()
        if (t.startsWith("DECIMAL")) return t.replace("DECIMAL", "numeric")
        switch (t) {
            case "INTEGER": return "integer"
            case "SMALLINT": return "smallint"
            case "BIGINT": return "bigint"
            case "BOOLEAN": return "boolean"
            case "DATE": return "date"
            case "TIMESTAMP": return "timestamp"
            case "DOUBLE": return "double precision"
            default: return "varchar"
        }
    }

    private static String q(String id) { '"' + id.replace('"', '""') + '"' }

    private static String env(String key, String fallback) {
        String v = System.getenv(key)
        (v == null || v.isEmpty()) ? fallback : v
    }
}
