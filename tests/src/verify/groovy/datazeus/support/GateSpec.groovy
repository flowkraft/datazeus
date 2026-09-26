package datazeus.support

import groovy.sql.Sql
import spock.lang.Specification

/**
 * Base for the dual-engine PUBLISH GATE. Each lesson *Spec extends this (or a base for its dataset,
 * such as {@link NorthwindCoGateSpec}) and runs every assertion on BOTH engines via
 * `where: engine << ENGINES` + `sqlFor(engine)`:
 *
 *   - duckdb   — a throwaway copy of the lesson's .duckdb file, the SAME file the DuckDB CLI lesson
 *                and the koans run on.
 *   - postgres — a REAL Postgres engine. We assert here too because identical data on two engines
 *                does NOT guarantee identical query results (date casts, NULL logic, integer
 *                division, identifier folding, default ordering, …).
 *
 * The dataset is `dataset()` + `schema()`, the same two overrides as KoanBase; by default the
 * classic datasets/northwind/northwind.duckdb. The engines are opened once per dataset and shared
 * by every spec in the run (see {@link GateEngines}), so adding lesson files costs nothing extra,
 * and no spec ever declares which tables to copy: the whole dataset is always there.
 *
 *   mvn test                     Docker: a throwaway PostgreSQL holding a copy of the DuckDB file
 *   PGHOST=localhost mvn test    the learner's PostgreSQL, with the dataset installed by DataPallas
 */
abstract class GateSpec extends Specification {

    static final List<String> ENGINES = ["duckdb", "postgres"]

    /** The .duckdb file the lesson runs on. */
    protected String dataset() { "../datasets/northwind/northwind.duckdb" }

    /** The schema inside it, or null for the file's default. */
    protected String schema() { null }

    /** The Sql for a given engine label — used by each feature's where: block. */
    protected Sql sqlFor(String engine) {
        GateEngines e = GateEngines.of(dataset(), schema())
        engine == "postgres" ? e.pg : e.duck
    }
}
