package datazeus.support

import groovy.sql.Sql
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Process-wide singleton for the dual-engine publish gate: ONE DuckDB connection + ONE
 * Postgres, with the WHOLE Northwind seeded ONCE, shared across every *Spec in the run.
 *
 * Starting Postgres and seeding is the expensive part. Doing it per spec class does not scale
 * (hundreds of lesson files would mean hundreds of container starts), so it happens exactly
 * once here — guarded by {@code ready} — and every spec reuses the same engines. The koans are
 * read-only (they teach SELECT), so a single shared, seeded DB is safe to share.
 *
 *   - duckdb   — a single throwaway copy of datasets/northwind/northwind.duckdb (so the gate
 *                can't lock/mutate the canonical file), the SAME data the koans run on.
 *   - postgres — a real engine. Default: ONE ephemeral Testcontainers postgres:16.2 (the
 *                version DataPallas ships), seeded with the ENTIRE duckdb (every table) so any
 *                current or future lesson can query any table with no per-lesson config. Set
 *                PGHOST to instead assert against the LIVE DataPallas "Northwind (PostgreSQL)"
 *                the learner uses in CloudBeaver — then we don't seed, we use what's there.
 *
 * Teardown is at JVM shutdown, not per spec class, so all specs share the one set of engines.
 */
class NorthwindEngines {

    // Pin to the version the DataPallas compose ships, so query semantics match CloudBeaver.
    static final String PG_IMAGE = "postgres:16.2"

    private static Sql duckdb
    private static Sql postgres
    private static PostgreSQLContainer pgc   // null when asserting against a live PG (PGHOST set)
    private static File dbCopy
    private static boolean ready = false

    /** Start + seed the engines exactly once for the whole test run. */
    static synchronized void init() {
        if (ready)
            return

        // --- DuckDB: ONE throwaway copy so the gate can't lock or mutate the canonical file ---
        File src = new File("../datasets/northwind/northwind.duckdb")
        dbCopy = File.createTempFile("gate-northwind-", ".duckdb")
        Files.copy(src.toPath(), dbCopy.toPath(), StandardCopyOption.REPLACE_EXISTING)
        duckdb = Sql.newInstance("jdbc:duckdb:" + dbCopy.absolutePath, "org.duckdb.DuckDBDriver")

        // --- Postgres: live DataPallas instance if PGHOST is set, else ONE Testcontainers PG ---
        String liveHost = System.getenv("PGHOST")
        if (liveHost) {
            String url = "jdbc:postgresql://${liveHost}:${env('PGPORT', '5432')}/${env('PGDATABASE', 'Northwind')}"
            postgres = Sql.newInstance(url, env('PGUSER', 'postgres'), env('PGPASSWORD', 'postgres'), "org.postgresql.Driver")
            assert postgres.firstRow('SELECT count(*) AS n FROM "Orders"').n > 0:
                    "PGHOST is set but ${url} is not a seeded Northwind — start it via " +
                    "DataPallas ▸ Apps / Starter Packs ▸ Northwind DB (PostgreSQL) ▸ Start."
        } else {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                throw new IllegalStateException(
                    "Running these tests needs Docker to be installed and started — they spin up " +
                    "PostgreSQL in a throwaway container. Install Docker (Docker Desktop) and start " +
                    "it, then run again. Or set PGHOST to point at an already-running Northwind PostgreSQL.")
            }
            pgc = new PostgreSQLContainer(DockerImageName.parse(PG_IMAGE))
            pgc.start()
            postgres = Sql.newInstance(pgc.jdbcUrl, pgc.username, pgc.password, "org.postgresql.Driver")
            NorthwindSeed.copyAll(duckdb, postgres)   // whole Northwind, once
        }

        // Tear down once, at JVM shutdown — NOT per spec class (all specs share these).
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

    private static String env(String key, String fallback) {
        String v = System.getenv(key)
        (v == null || v.isEmpty()) ? fallback : v
    }
}
