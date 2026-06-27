package datazeus.support

import groovy.sql.Sql
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Base for the dual-engine PUBLISH GATE. Each lesson *Spec extends this and runs every
 * assertion on BOTH engines via `where: engine << ENGINES` + `sqlFor(engine)`:
 *
 *   - duckdb   — the bundled datasets/northwind/northwind.duckdb (throwaway copy), the SAME
 *                file the DuckDB CLI lesson and the koans run on.
 *   - postgres — a REAL Postgres engine. We assert here too because identical data on two
 *                engines does NOT guarantee identical query results (date casts, NULL logic,
 *                integer division, identifier folding, default ordering, …). By default this
 *                is an ephemeral Testcontainers postgres:16.2 (the version DataPallas ships),
 *                seeded from the duckdb — both are the same NorthwindDataGenerator output, so
 *                the data is identical by construction. Set PGHOST to instead assert against
 *                the LIVE DataPallas "Northwind (PostgreSQL)" the learner uses in CloudBeaver.
 *
 * Default run needs only a running Docker engine. `PGHOST=localhost mvn test` checks the
 * literal CloudBeaver DB (must be started via DataPallas ▸ Starter Packs ▸ Northwind DB).
 */
abstract class NorthwindGateSpec extends Specification {

    static final List<String> ENGINES = ["duckdb", "postgres"]

    // Pin to the version the DataPallas compose ships, so query semantics match CloudBeaver.
    static final String PG_IMAGE = "postgres:16.2"

    // Tables the gate's queries touch — seeded into the throwaway PG. Extend as lessons grow.
    static final List<String> SEED_TABLES = ["Customers", "Orders"]

    @Shared Sql duck
    @Shared Sql pg
    @Shared File dbCopy
    @Shared PostgreSQLContainer pgc   // null when asserting against a live PG (PGHOST set)

    def setupSpec() {
        // --- DuckDB: throwaway copy so the gate can't lock or mutate the canonical file ---
        File src = new File("../datasets/northwind/northwind.duckdb")
        dbCopy = File.createTempFile("gate-northwind-", ".duckdb")
        Files.copy(src.toPath(), dbCopy.toPath(), StandardCopyOption.REPLACE_EXISTING)
        duck = Sql.newInstance("jdbc:duckdb:" + dbCopy.absolutePath, "org.duckdb.DuckDBDriver")

        // --- Postgres: live DataPallas instance if PGHOST is set, else Testcontainers ---
        String liveHost = System.getenv("PGHOST")
        if (liveHost) {
            String url = "jdbc:postgresql://${liveHost}:${env('PGPORT', '5432')}/${env('PGDATABASE', 'Northwind')}"
            pg = Sql.newInstance(url, env('PGUSER', 'postgres'), env('PGPASSWORD', 'postgres'), "org.postgresql.Driver")
            assert pg.firstRow('SELECT count(*) AS n FROM "Orders"').n > 0:
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
            pg = Sql.newInstance(pgc.jdbcUrl, pgc.username, pgc.password, "org.postgresql.Driver")
            NorthwindSeed.copy(duck, pg, SEED_TABLES)
        }
    }

    def cleanupSpec() {
        try {
            duck?.close()
        } finally {
            try {
                pg?.close()
            } finally {
                try {
                    pgc?.stop()
                } finally {
                    dbCopy?.delete()
                }
            }
        }
    }

    /** The Sql for a given engine label — used by each feature's where: block. */
    protected Sql sqlFor(String engine) {
        engine == "postgres" ? pg : duck
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key)
        (v == null || v.isEmpty()) ? fallback : v
    }
}
