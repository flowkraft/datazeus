// @description Academy dataset: re-counts and re-checksums an installed academy schema against its _dataset_info table, and logs OK or which table differs. Set SCHEMA below.
// Bindings provided by GenericSeedExecutor:
//   dbSql  — groovy.sql.Sql connected to the target database
//   vendor — String (uppercase): POSTGRES, DUCKDB, CLICKHOUSE
//   log   — SLF4J Logger
//   params — Map; optional key: SCHEMA (default northwind_co_s)
//
// Use it when a learner's numbers do not match a lesson: if this says OK, the data is exactly what the lesson was
// measured on, and the difference is in the query. The canonical form is the install script's (and Northwind's
// freeze test's): every column, names sorted case-insensitively; NULL as <NULL>; numbers with trailing zeros
// stripped; timestamps yyyy-MM-dd HH:mm:ss; dates yyyy-MM-dd.
//   Scale S:    rows tab-joined and sorted; SHA-256 of the rows joined by newlines.
//   Scales M, L: the sum of every row's SHA-256 modulo 2^256 — row order does not matter, nothing is sorted or held.
// A _loaded_at column is left out: the landed copy (academy-northwind-co-raw-install) stamps it relative to the day it
// ran, so that freshness checks see a recent load; everything else in its rows is fixed.
// On ClickHouse the columns come from system.columns, and a date, time or boolean is asked for as text (toString),
// which is the canonical form already.

import groovy.transform.CompileStatic
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

String SCHEMA = (params?.SCHEMA ?: 'northwind_co_s').toString()
if (!(SCHEMA ==~ /[a-z0-9_]+/)) throw new IllegalArgumentException("SCHEMA must be a plain schema name (got ${SCHEMA}).")

// The M and L checksum. The same class sits in academy-northwind-co-install.groovy; the two must stay identical.
@CompileStatic
class RowSum {
    static final BigInteger MOD = BigInteger.ONE.shiftLeft(256)
    final MessageDigest sha = MessageDigest.getInstance('SHA-256')
    final StringBuilder line = new StringBuilder(256)
    BigInteger sum = BigInteger.ZERO
    int count = 0
    void add(java.sql.ResultSet r, int columns) {
        line.setLength(0)
        for (int i = 1; i <= columns; i++) { if (i > 1) line.append('\t'); line.append(canon(r.getObject(i))) }
        sum = sum.add(new BigInteger(1, sha.digest(line.toString().getBytes('UTF-8'))))
        count++
    }
    String hex() { String.format('%064x', sum.mod(MOD)) }
    static String canon(Object v) {
        if (v == null) return '<NULL>'
        if (v instanceof Boolean) return ((Boolean) v) ? 'true' : 'false'
        if (v instanceof Number) { String s = new BigDecimal(v.toString()).stripTrailingZeros().toPlainString(); return s == '-0' ? '0' : s }
        if (v instanceof Timestamp) return time(((Timestamp) v).toLocalDateTime().toString())
        if (v instanceof LocalDateTime) return time(v.toString())
        if (v instanceof java.sql.Date) return ((java.sql.Date) v).toLocalDate().toString()
        if (v instanceof LocalDate) return v.toString()
        return v.toString()
    }
    static String time(String iso) { iso.replace('T', ' ').padRight(19, ':00').substring(0, 19) }
}

def q = { String name -> "\"${name}\"".toString() }
// Reads a big table in pieces: PostgreSQL otherwise fetches every row into memory before the first one is seen.
def eachRowStreamed = { String sql, Closure c ->
    if (vendor != 'POSTGRES') { dbSql.eachRow(sql, c); return }
    def conn = dbSql.connection
    boolean autoCommit = conn.autoCommit
    conn.autoCommit = false
    try { dbSql.withStatement { it.fetchSize = 10_000 }; dbSql.eachRow(sql, c) }
    finally { dbSql.withStatement { it.fetchSize = 0 }; conn.commit(); conn.autoCommit = autoCommit }
}

List expected
try {
    expected = dbSql.rows("SELECT \"Dataset\", \"Scale\", \"TableName\", \"RowCount\", \"Checksum\" FROM ${SCHEMA}.\"_dataset_info\" ORDER BY \"TableName\"".toString())
} catch (Exception e) {
    log.error("NOT INSTALLED: schema {} has no _dataset_info table ({}). Run academy-northwind-co-install first.", SCHEMA, e.message)
    throw e
}
String scale = expected[0].Scale
log.info("=== Verifying {} ({} scale {}) on {} ===", SCHEMA, expected[0].Dataset, scale, vendor)

List<String> problems = []
expected.each { row ->
    String table = row.TableName
    List<String> cols = vendor == 'CLICKHOUSE'
        ? dbSql.rows("SELECT name, type FROM system.columns WHERE database = ? AND table = ?".toString(), [SCHEMA, table])
               .findAll { it.name != '_loaded_at' }.sort { a, b -> (a.name as String).compareToIgnoreCase(b.name as String) }
               .collect { (it.type as String) ==~ /^(Nullable\()?(Date|Date32|DateTime|Bool)\b.*/ ? "toString(${q(it.name as String)}) AS ${q(it.name as String)}".toString() : q(it.name as String) }
        : dbSql.rows("SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?".toString(), [SCHEMA, table])
               .collect { it.column_name as String }.findAll { it != '_loaded_at' }.sort { a, b -> a.compareToIgnoreCase(b) }.collect { q(it) }
    if (cols.isEmpty()) { problems << "${table}: table is missing".toString(); return }
    String select = "SELECT ${cols.join(', ')} FROM ${SCHEMA}.${q(table)}".toString()
    String hex
    int count
    if (scale == 'S') {
        List<String> rows = []
        dbSql.eachRow(select) { r -> rows << (1..cols.size()).collect { RowSum.canon(r.getObject(it)) }.join('\t') }
        rows.sort()
        count = rows.size()
        hex = MessageDigest.getInstance('SHA-256').digest(rows.join('\n').getBytes('UTF-8')).collect { String.format('%02x', it) }.join()
    } else {
        RowSum rs = new RowSum()
        int n = cols.size()
        eachRowStreamed(select) { r -> rs.add(r, n) }
        count = rs.count
        hex = rs.hex()
    }
    if (count != (row.RowCount as int)) problems << "${table}: ${count} rows, expected ${row.RowCount}".toString()
    else if (hex != row.Checksum) problems << "${table}: same row count, different content (someone changed rows)".toString()
    else log.info("  {}: {} rows OK", table, count)
}
if (problems) {
    problems.each { log.error("DIFFERS — {}", it) }
    throw new IllegalStateException("${SCHEMA} does not match its install: ${problems.size()} table(s) differ. Reinstall it to get the lesson's data back.")
}
log.info("=== OK: all {} tables in {} match their install exactly ===", expected.size(), SCHEMA)
