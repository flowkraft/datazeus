package datazeus.support

import groovy.sql.Sql
import spock.lang.Specification

/**
 * Base for the dual-engine PUBLISH GATE. Each lesson *Spec extends this and runs every
 * assertion on BOTH engines via `where: engine << ENGINES` + `sqlFor(engine)`:
 *
 *   - duckdb   — the bundled datasets/northwind/northwind.duckdb (throwaway copy), the SAME
 *                file the DuckDB CLI lesson and the koans run on.
 *   - postgres — a REAL Postgres engine. We assert here too because identical data on two
 *                engines does NOT guarantee identical query results (date casts, NULL logic,
 *                integer division, identifier folding, default ordering, …).
 *
 * The engines themselves — ONE DuckDB + ONE Postgres, the WHOLE Northwind seeded ONCE — are a
 * process-wide singleton (see NorthwindEngines), started on first use and shared across every
 * spec in the run. So adding more lesson files costs nothing extra, and no spec ever declares
 * which tables to seed: the entire database is always available.
 *
 * Default run needs only a running Docker engine. `PGHOST=localhost mvn test` checks the
 * literal CloudBeaver DB (must be started via DataPallas ▸ Starter Packs ▸ Northwind DB).
 */
abstract class NorthwindGateSpec extends Specification {

    static final List<String> ENGINES = ["duckdb", "postgres"]

    /** The Sql for a given engine label — used by each feature's where: block. */
    protected Sql sqlFor(String engine) {
        engine == "postgres" ? NorthwindEngines.pg() : NorthwindEngines.duck()
    }
}
