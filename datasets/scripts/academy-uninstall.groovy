// @description Academy dataset: removes one academy schema (drops it with everything inside). Set SCHEMA below. Touches nothing else — Northwind and your own tables stay.
// Bindings provided by GenericSeedExecutor:
//   dbSql  — groovy.sql.Sql connected to the target database
//   vendor — String (uppercase): POSTGRES, DUCKDB, CLICKHOUSE (where a schema is a database)
//   log   — SLF4J Logger
//   params — Map; optional key: SCHEMA (default northwind_co_s)
//
// Guarded: it only drops a schema whose name starts with an academy dataset prefix AND that contains an
// _dataset_info table, so a typo cannot drop a schema this family did not create.

String SCHEMA = (params?.SCHEMA ?: 'northwind_co_s').toString()
if (!(SCHEMA ==~ /northwind_co_[a-z0-9_]+/)) {
    throw new IllegalArgumentException("Refusing to drop '${SCHEMA}': only academy schemas (northwind_co_…) can be removed with this script.")
}
boolean CH = vendor == 'CLICKHOUSE'
boolean isAcademy = (CH ? dbSql.firstRow("SELECT count(*) AS n FROM system.tables WHERE database = ? AND name = '_dataset_info'".toString(), [SCHEMA])
                        : dbSql.firstRow("SELECT count(*) AS n FROM information_schema.tables WHERE table_schema = ? AND table_name = '_dataset_info'".toString(), [SCHEMA])).n as int > 0
if (!isAcademy) {
    log.info("Nothing to remove: {} is not installed (no _dataset_info table).", SCHEMA)
    return
}
dbSql.execute((CH ? "DROP DATABASE ${SCHEMA} SYNC" : "DROP SCHEMA ${SCHEMA} CASCADE").toString())
log.info("=== Removed academy schema {} on {} ===", SCHEMA, vendor)
