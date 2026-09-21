// @description Academy dataset: "Northwind Company, landed" — the source copied as it arrives in a warehouse's raw layer (every column text, plus load metadata), then the change log applied day by day. Set SCALE and THROUGH_DAY below. Safe to run again: it only applies the days not yet applied.
// Bindings provided by GenericSeedExecutor:
//   dbSql  — groovy.sql.Sql connected to the target database
//   vendor — String (uppercase): POSTGRES, DUCKDB, CLICKHOUSE
//   log    — SLF4J Logger
//   params — Map; optional keys: SCALE, THROUGH_DAY, REBUILD

import groovy.transform.CompileStatic
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.DeserializationFeature

// The M and L checksum. The same class sits in academy-verify.groovy; the two must stay identical.
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

// ═════════════════════════════════════════════════════════════════════════════════════════════
// SCALE — 'S', 'M' or 'L': which Northwind Company (and which change log) to land. params.SCALE wins over this line.
// ═════════════════════════════════════════════════════════════════════════════════════════════
String SCALE = (params?.SCALE ?: 'S').toString().toUpperCase()
// THROUGH_DAY — how far into 2025 the landed copy has got: 0 is the 2024-12-31 snapshot alone, 90 the whole quarter
// (day 1 = 2025-01-01; 91 takes the few records that arrive on 1 April). A lesson says which day it needs.
// Run again with a larger number to land the days in between; a smaller number rebuilds from the snapshot.
int THROUGH_DAY = (params?.THROUGH_DAY ?: '0').toString() as int
// REBUILD — true drops the landed copy and starts again from the snapshot.
boolean REBUILD = (params?.REBUILD ?: 'false').toString().toBoolean()
int VERSION = 1
// ═════════════════════════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────────────────────
// WHAT THIS IS
// The first layer of a warehouse: the source as it was received, before anyone cleans it. Every table of Northwind
// Company, every column as text, nothing typed, nothing deduplicated; then each day of the change log
// (academy-northwind-co-changes.groovy) appended as it arrived. The staging lessons start here: cast, deduplicate,
// keep the latest version of each row, drop deletes, flag bad rows. Design: kraft-src-company-biz/.docs/plan-academy-
// datasets.md, D6.
//
// THE TABLES (schema northwind_co_raw_<scale>)
//   one per source table, same name, same columns (all text), plus:
//     _op           r  the 2024-12-31 snapshot     c  created    u  updated (the row after)    d  deleted (the row before)
//     _offset       the change log's offset (empty for the snapshot). A redelivered record repeats one.
//     _batch_id     snapshot, or d2-day-NNN for arrival day NNN
//     _source_file  the file the row came from: snapshot-2024-12-31/<table>.csv, or events-2025-MM-DD.jsonl
//     _loaded_at    when the row landed, on today's calendar so that "how fresh is this?" has a real answer: a run
//                   lands its days one real day apart, the last one ending now (THROUGH_DAY=90 from scratch: day 90
//                   is today, day 70 twenty days ago). It only ever grows — a later run's rows come after every row
//                   already there, so "_loaded_at > the last one I saw" never misses a row. (A run on the same day as
//                   the one before squeezes its days into the time since.) Not in the checksums.
//   Values are as the source wrote them: the snapshot's timestamps read 2024-01-01 00:00:00, the change log's
//   2025-01-01T00:00:00; the snapshot's prices 22.4100, the log's 22.41. Both cast cleanly; staging decides.
//   _control      the source's daily control totals, as sent (day, orders, order_lines, amount)
//   _rejects      records the load could not place: not JSON, an unknown table or op, no key, no row. With the reason.
//                 Everything else is landed, wrong or not — a negative quantity is staging's problem, not the loader's.
//   _load_audit   one row per landed batch: records read, landed, rejected
//   _dataset_info row counts and checksums for the day it has got to (academy-verify.groovy leaves _loaded_at out)
//
// WHO NEEDS WHAT
//   dbt S1 · 10 (sources, freshness: WebOrders stops landing after day 70), S1 · 20–45 (staging), S2 (snapshots,
//   incremental: run THROUGH_DAY forward and rebuild), Data Warehousing S1 · 35, ETL S3 (incremental loads)
//
// ENGINES: PostgreSQL, DuckDB and ClickHouse, the engine the source and the change log were installed on. On
// ClickHouse every text column is Nullable(String), the snapshot's text is made by toString (prices keep their
// trailing zeros, as PostgreSQL writes them), rows go in as batched INSERTs, and _dataset_info is updated with a
// lightweight DELETE.
// ─────────────────────────────────────────────────────────────────────────────────────────────

if (!(SCALE in ['S', 'M', 'L'])) throw new IllegalArgumentException("SCALE must be S, M or L (got ${SCALE}).")
if (!(vendor in ['POSTGRES', 'DUCKDB', 'CLICKHOUSE'])) throw new IllegalArgumentException("Only PostgreSQL, DuckDB and ClickHouse are supported (connection is ${vendor}).")
if (THROUGH_DAY < 0 || THROUGH_DAY > 100) throw new IllegalArgumentException("THROUGH_DAY must be 0..100 (got ${THROUGH_DAY}).")
String DATASET = 'northwind_co_raw'
String SRC = "northwind_co_${SCALE.toLowerCase()}".toString()
String CHG = "northwind_co_changes_${SCALE.toLowerCase()}".toString()
String DST = "${DATASET}_${SCALE.toLowerCase()}".toString()
LocalDate DAY_ZERO = LocalDate.of(2024, 12, 31)
DateTimeFormatter TS_CSV = DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss')
LocalDateTime NOW = LocalDateTime.now().withNano(0)
def q = { String name -> "\"${name}\"".toString() }
def T = { String schema, String table -> "${schema}.${q(table)}".toString() }
boolean CH = vendor == 'CLICKHOUSE'
def exists = { String schema, String table ->
    CH ? dbSql.firstRow('SELECT COUNT(*) AS n FROM system.tables WHERE database = ? AND name = ?', [schema, table]).n as int > 0
       : dbSql.firstRow('SELECT COUNT(*) AS n FROM information_schema.tables WHERE table_schema = ? AND table_name = ?', [schema, table]).n as int > 0
}
// A timestamp literal.
def tsLit = { LocalDateTime t -> CH ? "toDateTime('${t.format(TS_CSV)}')".toString() : "CAST('${t.format(TS_CSV)}' AS TIMESTAMP)".toString() }

// ── ClickHouse ──────────────────────────────────────────────────────────────────────────────
// The tables are written in the DDL PostgreSQL and DuckDB share and translated, as in academy-northwind-co-install:
// VARCHAR → String, INTEGER → Int32, BIGINT → Int64, TIMESTAMP → DateTime, a column that may be NULL → Nullable(…),
// ENGINE = MergeTree (ORDER BY tuple(): these tables have no key; the rows are the arrival order's).
def chType = { String type, String col ->
    String u = type.toUpperCase().replaceAll('\\s', '')
    if (u.startsWith('VARCHAR') || u == 'TEXT') return 'String'
    if (u == 'INTEGER') return 'Int32'
    if (u == 'SMALLINT') return 'Int16'
    if (u == 'BIGINT') return 'Int64'
    if (u.startsWith('DECIMAL')) return u.replace('DECIMAL', 'Decimal')
    if (u == 'TIMESTAMP') return 'DateTime'
    if (u == 'DATE') return 'Date'
    if (u == 'BOOLEAN') return 'Bool'
    throw new IllegalArgumentException("No ClickHouse type for ${type} (column ${col}).")
}
def chDdl = { String ddl ->
    def m = ddl =~ /(?s)^\s*CREATE TABLE\s+([^(]+?)\s*\((.*)\)\s*$/
    if (!m.matches()) throw new IllegalArgumentException("Not a CREATE TABLE: ${ddl}")
    String name = m.group(1), body = m.group(2)
    List<String> parts = []
    StringBuilder part = new StringBuilder()
    int depth = 0
    for (char c : body.toCharArray()) {
        if (c == '(' as char) depth++
        if (c == ')' as char) depth--
        if (c == ',' as char && depth == 0) { parts << part.toString().trim(); part.setLength(0) } else part.append(c)
    }
    parts << part.toString().trim()
    String tableKey = parts.find { it.toUpperCase().startsWith('PRIMARY KEY') }
    List<String> key = tableKey ? (tableKey =~ /\(([^)]*)\)/)[0][1].toString().split(',').collect { it.trim() } : []
    List<String> defs = parts.findAll { !it.toUpperCase().startsWith('PRIMARY KEY') }.collect { String p ->
        def c = p =~ /(?s)^("[^"]+"|\w+)\s+(\w+(?:\s*\([^)]*\))?)(.*)$/
        if (!c.matches()) throw new IllegalArgumentException("Cannot read column '${p}' in ${name}.")
        String col = c.group(1), rest = c.group(3).toUpperCase()
        if (rest.contains('PRIMARY KEY')) key << col
        String type = chType(c.group(2), col.replace('"', ''))
        "${col} ${rest.contains('NOT NULL') || rest.contains('PRIMARY KEY') || col in key ? type : "Nullable(${type})"}".toString()
    }
    "CREATE TABLE ${name} (${defs.join(', ')}) ENGINE = MergeTree ORDER BY ${key ? "(${key.join(', ')})" : 'tuple()'}".toString()
}
def createTable = { String ddl -> dbSql.execute(CH ? chDdl(ddl) : ddl) }
// Times go to ClickHouse as text, which it stores as that same wall-clock value on any server.
def chValue = { v ->
    if (v instanceof LocalDateTime) return (v as LocalDateTime).format(TS_CSV)
    if (v instanceof GString) return v.toString()
    return v
}

// ── 0. what is there ─────────────────────────────────────────────────────────────────────────
if (!exists(SRC, '_dataset_info')) throw new IllegalStateException("Install Northwind Company scale ${SCALE} first (academy-northwind-co-install; schema ${SRC} not found).")
if (THROUGH_DAY > 0 && !exists(CHG, 'event_log'))
    throw new IllegalStateException("THROUGH_DAY ${THROUGH_DAY} needs the change log: run academy-northwind-co-changes at scale ${SCALE} first (schema ${CHG} not found).")
List<String> tables = CH
    ? dbSql.rows('SELECT name AS table_name FROM system.tables WHERE database = ? AND name <> ? ORDER BY name', [SRC, '_dataset_info']).collect { it.table_name as String }
    : dbSql.rows('SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_name <> ? ORDER BY table_name', [SRC, '_dataset_info']).collect { it.table_name as String }
Map<String, List<String>> columnsOf = tables.collectEntries { String t ->
    [t, CH ? dbSql.rows('SELECT name AS column_name FROM system.columns WHERE database = ? AND table = ? ORDER BY position', [SRC, t]).collect { it.column_name as String }
           : dbSql.rows('SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position', [SRC, t]).collect { it.column_name as String }]
}
int applied = -1
if (!REBUILD && exists(DST, '_load_audit')) applied = (dbSql.firstRow("SELECT MAX(arrival_day) AS d FROM ${T(DST, '_load_audit')}".toString()).d ?: 0) as int
if (applied > THROUGH_DAY) {
    log.info("  {} has landed through day {}; day {} was asked for, so it is rebuilt from the snapshot", DST, applied, THROUGH_DAY)
    applied = -1
}
log.info("=== Landed copy {} v{} on {}: {} {} ===", DST, VERSION, vendor,
         applied < 0 ? 'building from the snapshot, then through day' : "day ${applied} → day".toString(), THROUGH_DAY)
if (applied == THROUGH_DAY) { log.info("=== {} is already at day {}: nothing to land ===", DST, THROUGH_DAY); return }

// _loaded_at: this run's days (firstDay..THROUGH_DAY) laid out one real day apart and ending now — or closer together,
// when that would reach back before the last row already landed.
int firstDay = applied < 0 ? 0 : applied + 1
long spanDays = THROUGH_DAY - firstDay + 1
long daySeconds = 86_400
if (applied >= 0) {
    Object last = dbSql.firstRow("SELECT ${CH ? 'toString(MAX("_loaded_at"))' : 'MAX("_loaded_at")'} AS t FROM ${T(DST, '_load_audit')}".toString()).t
    LocalDateTime prev = last instanceof Timestamp ? ((Timestamp) last).toLocalDateTime()
                       : last instanceof CharSequence ? LocalDateTime.parse(last.toString().replace(' ', 'T')) : last as LocalDateTime
    daySeconds = Math.max(1L, Math.min(daySeconds, ChronoUnit.SECONDS.between(prev, NOW).intdiv(spanDays) as long))
}
LocalDateTime runStart = NOW.minusSeconds(spanDays * daySeconds)
def loadedAt = { int arrivalDay, java.time.LocalTime time ->
    runStart.plusSeconds((long) (((arrivalDay - firstDay) + time.toSecondOfDay() / 86_400d) * daySeconds))
}
String META_COLS = '"_op" VARCHAR(1), "_offset" BIGINT, "_batch_id" VARCHAR(20), "_source_file" VARCHAR(60), "_loaded_at" TIMESTAMP'
List<String> META = ['_op', '_offset', '_batch_id', '_source_file', '_loaded_at']

// Rows go to a CSV file and in with one COPY — row-by-row INSERTs take minutes at L. ClickHouse takes batched
// INSERTs, 20,000 rows at a time (each batch is one INSERT, one part on disk).
int BATCH = 20_000
def flushBatch = { Map s ->
    List<List> buf = s.buf as List<List>
    if (!buf) return
    String sql = "INSERT INTO ${T(DST, s.table as String)} (${(s.cols as List<String>).collect { q(it) }.join(', ')}) VALUES (${(s.cols as List).collect { '?' }.join(', ')})".toString()
    dbSql.withBatch(buf.size(), sql) { ps -> buf.each { ps.addBatch(it.collect(chValue)) } }
    buf.clear()
}
def csvValue = { v ->
    if (v == null) return '\\N'
    if (v instanceof CharSequence) return '"' + v.toString().replace('"', '""') + '"'
    if (v instanceof LocalDateTime) return ((LocalDateTime) v).format(TS_CSV)
    v.toString()
}
def openSink = { String table, List<String> cols ->
    if (CH) return [table: table, cols: cols, n: 0L, buf: []]
    File f = File.createTempFile("${DST}-", '.csv')
    [table: table, cols: cols, n: 0L, file: f, out: new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), 'UTF-8'), 1 << 20)]
}
def put = { Map s, List row ->
    s.n = (s.n as long) + 1
    if (CH) { (s.buf as List) << row; if ((s.buf as List).size() == BATCH) flushBatch(s); return }
    Writer w = s.out as Writer; w.write(row.collect(csvValue).join(',')); w.write('\n')
}
def closeSink = { Map s ->
    if (CH) { flushBatch(s); return }
    (s.out as Writer).close()
    File f = s.file as File
    try {
        if ((s.n as long) == 0) return
        String target = "${T(DST, s.table as String)} (${(s.cols as List<String>).collect { q(it) }.join(', ')})"
        if (vendor == 'DUCKDB') {
            String path = f.absolutePath.replace('\\', '/').replace("'", "''")
            dbSql.execute("COPY ${target} FROM '${path}' (FORMAT csv, HEADER false, DELIMITER ',', QUOTE '\"', ESCAPE '\"', NULL '\\N')".toString())
        } else {
            def copyApi = dbSql.connection.unwrap(Class.forName('org.postgresql.PGConnection')).getCopyAPI()
            f.withReader('UTF-8') { r -> copyApi.copyIn("COPY ${target} FROM STDIN WITH (FORMAT csv, NULL '\\N')".toString(), r) }
        }
    } finally { f.delete() }
}

// ── 1. the snapshot: every table as text, as exported on 2024-12-31 ─────────────────────────────
Set<String> touched = new TreeSet<>()
if (applied < 0) {
    if (CH) {
        dbSql.execute("DROP DATABASE IF EXISTS ${DST} SYNC".toString())
        dbSql.execute("CREATE DATABASE ${DST}".toString())
    } else {
        dbSql.execute("DROP SCHEMA IF EXISTS ${DST} CASCADE".toString())
        dbSql.execute("CREATE SCHEMA ${DST}".toString())
    }
    LocalDateTime snapshotAt = loadedAt(0, java.time.LocalTime.of(1, 0))
    tables.each { String t ->
        List<String> cols = columnsOf[t]
        createTable("CREATE TABLE ${T(DST, t)} (${cols.collect { "${q(it)} VARCHAR" }.join(', ')}, ${META_COLS})".toString())
        // ClickHouse: toString keeps a NULL a NULL (CAST to String would refuse it); the setting keeps 22.4100 as 22.4100.
        dbSql.execute("""INSERT INTO ${T(DST, t)} SELECT ${cols.collect { CH ? "toString(${q(it)})" : "CAST(${q(it)} AS VARCHAR)" }.join(', ')},
                         'r', NULL, 'snapshot', 'snapshot-2024-12-31/${t.replace(' ', '_')}.csv', ${tsLit(snapshotAt)}
                         FROM ${T(SRC, t)}${CH ? ' SETTINGS output_format_decimal_trailing_zeros = 1' : ''}""".toString())
        touched << t
    }
    createTable("CREATE TABLE ${T(DST, '_control')} (\"day\" VARCHAR, \"orders\" VARCHAR, \"order_lines\" VARCHAR, \"amount\" VARCHAR, ${META_COLS})".toString())
    createTable("CREATE TABLE ${T(DST, '_rejects')} (\"reason\" VARCHAR(40), \"line\" VARCHAR, ${META_COLS})".toString())
    createTable("CREATE TABLE ${T(DST, '_load_audit')} (\"_batch_id\" VARCHAR(20), \"arrival_day\" INTEGER, \"records\" INTEGER, \"landed\" INTEGER, \"rejected\" INTEGER, \"_loaded_at\" TIMESTAMP)".toString())
    dbSql.execute("INSERT INTO ${T(DST, '_load_audit')} VALUES ('snapshot', 0, 0, 0, 0, ${tsLit(snapshotAt)})".toString())
    touched.addAll(['_control', '_rejects', '_load_audit'])
    log.info("  snapshot: {} tables landed as text", tables.size())
    applied = 0
}

// ── 2. the change log, one arrival day at a time ────────────────────────────────────────────────
if (THROUGH_DAY > applied) {
    ObjectMapper JSON = new ObjectMapper().configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
    Map<String, Map> sinks = [:]
    def sinkFor = { String t -> sinks.computeIfAbsent(t) { openSink(t, t == '_control' ? ['day', 'orders', 'order_lines', 'amount'] + META : t == '_rejects' ? ['reason', 'line'] + META : columnsOf[t] + META) } }
    def text = { JsonNode n -> n == null || n.isNull() ? null : n.isNumber() ? n.decimalValue().toPlainString() : n.isValueNode() ? n.asText() : n.toString() }
    List<List> audit = []
    Map<Integer, List<Integer>> perDay = [:].withDefault { [0, 0, 0] }             // records, landed, rejected
    LocalDateTime from = DAY_ZERO.plusDays(applied + 1).atStartOfDay(), upTo = DAY_ZERO.plusDays(THROUGH_DAY + 1).atStartOfDay()
    // ClickHouse hands the arrival time over as text (a DateTime would pass through the driver's time zone).
    dbSql.eachRow("""SELECT "offset", ${CH ? 'toString(arrival_time) AS arrival_text' : 'arrival_time'}, line FROM ${CHG}.event_log
                     WHERE arrival_time >= ${tsLit(from)} AND arrival_time < ${tsLit(upTo)} ORDER BY seq""".toString()) { r ->
        LocalDateTime arrival = CH ? LocalDateTime.parse(r.getString('arrival_text').replace(' ', 'T')) : r.getTimestamp('arrival_time').toLocalDateTime()
        int dn = (int) ChronoUnit.DAYS.between(DAY_ZERO, arrival.toLocalDate())
        long offset = r.getLong('offset')
        String line = r.getString('line')
        String batch = String.format('d2-day-%03d', dn), file = "events-${arrival.toLocalDate()}.jsonl".toString()
        List meta = [null, offset, batch, file, loadedAt(dn, arrival.toLocalTime())]
        perDay[dn][0]++
        String reason = null
        JsonNode e = null
        try { e = JSON.readTree(line) } catch (Exception ignored) { reason = 'not JSON' }
        String table = e?.path('table')?.asText(null), op = e?.path('op')?.asText(null)
        JsonNode row = op == 'd' ? e?.get('before') : e?.get('after'), key = e?.get('key')
        if (reason == null) {
            if (table != '_control' && !columnsOf.containsKey(table)) reason = 'unknown table'
            else if (!(op in ['c', 'u', 'd'])) reason = 'unknown op'
            else if (key == null || !key.isObject() || key.size() == 0 || key.elements().any { it.isNull() }) reason = 'no key'
            else if (row == null || !row.isObject()) reason = 'no row'
        }
        if (reason) {
            meta[0] = op?.take(1)
            put(sinkFor('_rejects'), [reason, line] + meta)
            perDay[dn][2]++
            return
        }
        meta[0] = op
        if (table == '_control') put(sinkFor('_control'), ['day', 'orders', 'order_lines', 'amount'].collect { text(row.get(it)) } + meta)
        else put(sinkFor(table), columnsOf[table].collect { text(row.get(it)) } + meta)
        perDay[dn][1]++
    }
    sinks.values().each { Map s -> closeSink(s); touched << (s.table as String); log.info("  {}: {} rows landed", s.table, s.n) }
    ((applied + 1)..THROUGH_DAY).each { int dn ->
        List<Integer> c = perDay[dn]
        audit << [String.format('d2-day-%03d', dn), dn, c[0], c[1], c[2], loadedAt(dn, java.time.LocalTime.of(23, 59))]
    }
    Map s = openSink('_load_audit', ['_batch_id', 'arrival_day', 'records', 'landed', 'rejected', '_loaded_at'])
    audit.each { put(s, it) }
    closeSink(s)
    touched << '_load_audit'
    log.info("  days {}–{}: {} records, {} landed, {} rejected", applied + 1, THROUGH_DAY, audit.sum { it[2] }, audit.sum { it[3] }, audit.sum { it[4] })
}

// ── 3. _dataset_info: counts and checksums of the tables that changed (the canonical form of academy-verify.groovy) ──
if (!exists(DST, '_dataset_info'))
    createTable("CREATE TABLE ${T(DST, '_dataset_info')} (\"Dataset\" VARCHAR(40), \"Version\" INTEGER, \"Scale\" VARCHAR(2), \"TableName\" VARCHAR(40), \"RowCount\" INTEGER, \"Checksum\" VARCHAR(64))".toString())
// A table's columns for the checksum, _loaded_at left out, sorted case-insensitively, as SELECT expressions.
// ClickHouse is asked for any date, time or boolean as text (toString), which is the canonical form already.
def checksumColumns = { String table ->
    if (!CH) return dbSql.rows('SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?', [DST, table])
                         .collect { it.column_name as String }.findAll { it != '_loaded_at' }.sort { a, b -> a.compareToIgnoreCase(b) }.collect { q(it) }
    dbSql.rows('SELECT name, type FROM system.columns WHERE database = ? AND table = ?', [DST, table])
         .findAll { it.name != '_loaded_at' }
         .sort { a, b -> (a.name as String).compareToIgnoreCase(b.name as String) }
         .collect { (it.type as String) ==~ /^(Nullable\()?(Date|Date32|DateTime|Bool)\b.*/ ? "toString(${q(it.name as String)}) AS ${q(it.name as String)}".toString() : q(it.name as String) }
}
touched.each { String t ->
    List<String> cols = checksumColumns(t)
    String select = "SELECT ${cols.join(', ')} FROM ${T(DST, t)}".toString()
    int n
    String hex
    if (SCALE == 'S') {
        List<String> rows = []
        dbSql.eachRow(select) { r -> rows << (1..cols.size()).collect { RowSum.canon(r.getObject(it)) }.join('\t') }
        rows.sort()
        n = rows.size()
        hex = MessageDigest.getInstance('SHA-256').digest(rows.join('\n').getBytes('UTF-8')).collect { String.format('%02x', it) }.join()
    } else {
        RowSum rs = new RowSum()
        int k = cols.size()
        if (vendor == 'POSTGRES') {
            def conn = dbSql.connection
            boolean autoCommit = conn.autoCommit
            conn.autoCommit = false
            try { dbSql.withStatement { it.fetchSize = 10_000 }; dbSql.eachRow(select) { r -> rs.add(r, k) } }
            finally { dbSql.withStatement { it.fetchSize = 0 }; conn.commit(); conn.autoCommit = autoCommit }
        } else dbSql.eachRow(select) { r -> rs.add(r, k) }
        n = rs.count
        hex = rs.hex()
    }
    dbSql.execute("DELETE FROM ${T(DST, '_dataset_info')} WHERE \"TableName\" = ?".toString(), [t])
    dbSql.execute("INSERT INTO ${T(DST, '_dataset_info')} VALUES (?, ?, ?, ?, ?, ?)".toString(), [DATASET, VERSION, SCALE, t, n, hex])
}
log.info("=== {} v{} landed through day {} ({}): {} tables re-counted; _loaded_at runs up to {} ===", DST, VERSION, THROUGH_DAY,
         THROUGH_DAY == 0 ? 'the 2024-12-31 snapshot' : DAY_ZERO.plusDays(THROUGH_DAY).toString(), touched.size(), NOW.withNano(0))
