// @description Academy dataset: exports "the files" — Northwind Company as the files other systems hand over (daily order CSVs with four schema changes, an Excel summary with its header on row 4, order lines as monthly/daily Parquet and CSV, sorted and unsorted Parquet, nested web orders, a paginated JSON API, an Iceberg table with two snapshots) plus manifest.json. Set SCALE below. Writes files only; the database is read, never changed.
// Bindings provided by GenericSeedExecutor:
//   dbSql  — groovy.sql.Sql connected to the database that holds northwind_co_<scale>
//   vendor — String (uppercase): POSTGRES, DUCKDB (the two supported so far)
//   log    — SLF4J Logger
//   params — Map; optional keys: SCALE, OUT_DIR, ICEBERG_LOCATION

import groovy.json.JsonSlurper
import groovy.sql.Sql
import groovy.transform.CompileStatic
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook

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

// Copies source rows into the private DuckDB this script works in. Kinds: int, str, date, dec2, ts.
// date refuses a value with a time of day and dec2 a value with more than two decimals: the files would lose it.
@CompileStatic
class Copy {
    static long rows(ResultSet rs, org.duckdb.DuckDBAppender app, List<String> kinds, String table) {
        long n = 0
        while (rs.next()) {
            app.beginRow()
            for (int i = 0; i < kinds.size(); i++) {
                Object v = rs.getObject(i + 1)
                if (v == null) { app.appendNull(); continue }
                switch (kinds[i]) {
                    case 'int': app.append(((Number) v).intValue()); break
                    case 'str': app.append(v.toString()); break
                    case 'ts': app.append(stamp(v)); break
                    case 'date':
                        LocalDateTime t = stamp(v)
                        if (t.toLocalTime() != java.time.LocalTime.MIDNIGHT)
                            throw new IllegalStateException("${table}: column ${i + 1} holds ${t}, a time of day; the files carry dates only".toString())
                        app.append(t.toLocalDate()); break
                    case 'dec2':
                        app.append(new BigDecimal(v.toString()).setScale(2, RoundingMode.UNNECESSARY)); break
                    default: throw new IllegalArgumentException(kinds[i])
                }
            }
            app.endRow()
            n++
        }
        return n
    }
    static LocalDateTime stamp(Object v) {
        if (v instanceof Timestamp) return ((Timestamp) v).toLocalDateTime()
        if (v instanceof LocalDateTime) return (LocalDateTime) v
        if (v instanceof java.sql.Date) return ((java.sql.Date) v).toLocalDate().atStartOfDay()
        if (v instanceof LocalDate) return ((LocalDate) v).atStartOfDay()
        return LocalDateTime.parse(v.toString().replace(' ', 'T'))
    }
}

// Avro object container files, as Iceberg's manifests and manifest lists are. No codec; one block. The writer walks
// the same schema maps that are written into the file header, so the bytes cannot drift from the declared schema.
@CompileStatic
class Avro {
    static void lng(ByteArrayOutputStream o, long v) {
        long z = (v << 1) ^ (v >> 63)
        while ((z & ~0x7FL) != 0) { o.write((int) ((z & 0x7F) | 0x80)); z >>>= 7 }
        o.write((int) z)
    }
    static void bytes(ByteArrayOutputStream o, byte[] b) { lng(o, b.length); o.write(b, 0, b.length) }
    static void str(ByteArrayOutputStream o, String s) { bytes(o, s.getBytes(StandardCharsets.UTF_8)) }
    static void value(ByteArrayOutputStream o, Object schema, Object v) {
        if (schema instanceof String) {
            switch ((String) schema) {
                case 'null': return
                case 'boolean': o.write(((Boolean) v) ? 1 : 0); return
                case 'int': case 'long': lng(o, ((Number) v).longValue()); return
                case 'string': str(o, (String) v); return
                case 'bytes': bytes(o, (byte[]) v); return
            }
            throw new IllegalArgumentException('avro type ' + schema)
        }
        if (schema instanceof List) {                       // ["null", T]
            if (v == null) lng(o, 0) else { lng(o, 1); value(o, ((List) schema).get(1), v) }
            return
        }
        Map s = (Map) schema
        if (s.get('type') == 'record') {
            for (Object f : (List) s.get('fields')) value(o, ((Map) f).get('type'), ((Map) v).get(((Map) f).get('name')))
            return
        }
        if (s.get('type') == 'array') {                     // a list, or an Iceberg map (logicalType map: key/value records)
            List items = new ArrayList()
            if (v instanceof Map) for (Object e : ((Map) v).entrySet()) {
                Map kv = new LinkedHashMap(); kv.put('key', ((Map.Entry) e).getKey()); kv.put('value', ((Map.Entry) e).getValue()); items.add(kv)
            } else items.addAll((List) v)
            if (!items.isEmpty()) { lng(o, items.size()); for (Object i : items) value(o, s.get('items'), i) }
            lng(o, 0)
            return
        }
        throw new IllegalArgumentException('avro schema ' + s)
    }
    static byte[] file(Object schema, String schemaJson, Map<String, Object> meta, List records, byte[] sync) {
        ByteArrayOutputStream o = new ByteArrayOutputStream()
        o.write('Obj'.getBytes(StandardCharsets.US_ASCII)); o.write(1)
        Map<String, String> all = new LinkedHashMap<String, String>()
        all.put('avro.schema', schemaJson); all.put('avro.codec', 'null')
        for (Map.Entry<String, Object> m : meta.entrySet()) all.put(m.getKey(), String.valueOf(m.getValue()))
        lng(o, all.size())
        for (Map.Entry<String, String> e : all.entrySet()) { str(o, e.getKey()); bytes(o, e.getValue().getBytes(StandardCharsets.UTF_8)) }
        lng(o, 0)
        o.write(sync)
        if (!records.isEmpty()) {
            ByteArrayOutputStream block = new ByteArrayOutputStream()
            for (Object r : records) value(block, schema, r)
            lng(o, records.size()); lng(o, block.size()); block.writeTo(o); o.write(sync)
        }
        return o.toByteArray()
    }
}

// ═════════════════════════════════════════════════════════════════════════════════════════════
// SCALE — 'S', 'M' or 'L': which Northwind Company to export (northwind_co_s gives northwind_co_files_s, and so on).
// Every file is exported at every scale; S is for opening files and reading them, M and L for the size lessons
// (row groups, small files, pruning). params.SCALE, when the Seed Data tab passes one, wins over this line.
// ═════════════════════════════════════════════════════════════════════════════════════════════
String SCALE = (params?.SCALE ?: 'S').toString().toUpperCase()
// Where the export goes: <OUT_DIR>/northwind_co_files_<scale>/. A relative path is taken from the app's folder, so
// the default lands next to db/docker-compose.yml, whose MinIO service publishes it (bucket "academy").
String OUT_DIR = (params?.OUT_DIR ?: 'db/academy-files').toString()
// The location written into the Iceberg table's metadata. Empty: the folder the table is written to (a file: URI,
// readable where it was written). To read it from MinIO instead, set s3://academy/northwind_co_files_<scale>/iceberg/order_lines
String ICEBERG_LOCATION = (params?.ICEBERG_LOCATION ?: '').toString()
// ═════════════════════════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────────────────────
// WHAT THIS IS
// Northwind Company as files: what an ERP drops on a share every night, what a finance team keeps in Excel, what a
// web shop's API returns, what a data lake stores. Every row comes from the installed Northwind Company of the same
// scale (as of 2024-12-31, the day it stops) — nothing is invented, only shaped the way each kind of file arrives.
// The Iceberg table's second snapshot adds the first day of Company's change log (academy-northwind-co-changes).
// Nothing random, nothing timed: the same source gives the same bytes, and manifest.json lists every file with its
// size and SHA-256. The database is only read. Design: kraft-src-company-biz/.docs/plan-academy-datasets.md, D4.
//
// THE FILES (in <OUT_DIR>/northwind_co_files_<scale>/)
//   orders/orders_YYYY-MM-DD.csv          30 files, 2024-12-02 .. 2024-12-31: the orders keyed into the ERP that
//                                         day, as its nightly export saw them (Status then; a ShippedDate only once
//                                         shipped). An order keyed late sits in the file of the day it was keyed,
//                                         with its earlier OrderDate. UTF-8, LF, header row, "" quoting, NULL
//                                         is an empty field. The layout changes four times:
//                                           v1 day 1   OrderID, CustomerID, EmployeeID, OrderDate, RequiredDate,
//                                                      ShippedDate, ShipVia, Freight, ShipName, ShipCity, ShipCountry,
//                                                      Status, DeliveryNotes
//                                           v2 day 11  ADDED      Channel, as the last column
//                                           v3 day 18  RENAMED    ShipVia becomes ShipperID
//                                           v4 day 24  RETYPED    Freight written with a decimal comma ("12,50")
//                                           v5 day 27  REORDERED  Channel moves to after CustomerID
//   order-summary.xlsx                    one sheet per month (2024-11, 2024-12): three title lines, the header on
//                                         row 4, one row per order. In 2024-12 Amount and Freight swap places.
//   order_lines/month=YYYY-MM/data_0.parquet        60 files — the order lines, one row per OrderID + ProductID
//   order_lines_csv/month=YYYY-MM/data_0.csv        the same 60 months as CSV
//   order_lines_daily/day=YYYY-MM-DD/data_0.parquet the same rows, one file per day (~1,800 small files)
//   order_lines_sorted.parquet            all order lines in one file, sorted by OrderDate
//   order_lines_unsorted.parquet          the same rows in scattered order: same data, useless min/max per row group
//   web_orders.parquet                    WebOrders with the payload typed: ship_to STRUCT, items LIST<STRUCT>
//   api/web-orders.json, api/web-orders/<cursor>.json
//                                         WebOrders as a paginated API: 100 per page, the payload as it arrived
//   iceberg/order_lines/                  an Iceberg (v2) table of the order lines, partitioned by month(OrderDate):
//                                         snapshot 1 = 2024-12-31, snapshot 2 = after change-log day 1
//   manifest.json                         written last: every file, set, schema, row count, checksum and plant
//
// The order-line columns: OrderID, OrderDate, CustomerID, ProductID, CategoryID, ShipCountry, Channel, Status,
// UnitPrice, Quantity, Discount. Freight is an order-level amount and stays out of line files: repeated on every
// line it would be summed once per line.
//
// WHO NEEDS WHAT — an index, so nothing here is removed as "unused" (search the tag to find the code):
//   FIXTURE   ETL S1 · 05    orders/ days 1–10 (v1, clean): load the daily files, reconcile to Orders by OrderID
//   PLANTED   ETL S1 · 10    order-summary.xlsx: header on row 4; the 2024-12 sheet moves a column
//   FIXTURE   ETL S1 · 10    api/: a paginated API with a next-page cursor, the payload untouched
//   FIXTURE   ETL S1 · 50    orders/: the files of the nightly load
//   PLANTED   ETL S2 · 07    orders/: the four layout changes (ADDED, RENAMED, RETYPED, REORDERED); daily volumes
//                            that swing; DeliveryNotes with a comma (a quoted field) on days without a change
//   FIXTURE   ETL S3 · 40    order_lines/ and order_lines_csv/: the same 60 months as Parquet and as CSV
//   FIXTURE   Data Warehousing S2 · 02   order_lines_sorted/_unsorted.parquet (row groups of 10,240; pruning
//                            evidence in manifest.json) and web_orders.parquet (nested types)
//   FIXTURE   Data Warehousing S2 · 17   order_lines/ (60 files) vs order_lines_daily/ (~1,800) on MinIO
//   FIXTURE   Data Warehousing S2 · 50   order_lines/ as the lake's fact files
//   FIXTURE   Data Warehousing S3 · 20   iceberg/order_lines: two snapshots, time travel, the metadata tree
// NOT HERE: the CRM export (academy-crm-export-install, EXPORT_DIR), Company's other tables, anything after day 1
// of the change log.
// ─────────────────────────────────────────────────────────────────────────────────────────────

if (!(SCALE in ['S', 'M', 'L'])) throw new IllegalArgumentException("SCALE must be S, M or L (got ${SCALE}).")
if (!(vendor in ['POSTGRES', 'DUCKDB'])) throw new IllegalArgumentException("Only PostgreSQL and DuckDB are supported so far (connection is ${vendor}).")
String DATASET = 'northwind_co_files'
String SRC = "northwind_co_${SCALE.toLowerCase()}".toString()
String CHANGES = "northwind_co_changes_${SCALE.toLowerCase()}".toString()
String NAME = "${DATASET}_${SCALE.toLowerCase()}".toString()
LocalDate AS_OF = LocalDate.of(2024, 12, 31)
LocalDate DAILY_FROM = LocalDate.of(2024, 12, 2)
int DAILY_DAYS = 30, ROW_GROUP_ROWS = 10240, API_PAGE_SIZE = 100
List<String> SHEETS = ['2024-11', '2024-12']
String PRUNE_MONTH = '2024-06'

def q = { String name -> "\"${name}\"".toString() }
def S = { String table -> "${SRC}.${q(table)}".toString() }
def exists = { String schema, String table ->
    dbSql.firstRow('SELECT COUNT(*) AS n FROM information_schema.tables WHERE table_schema = ? AND table_name = ?', [schema, table]).n as int > 0
}
if (!exists(SRC, '_dataset_info')) throw new IllegalStateException("Install Northwind Company scale ${SCALE} first (academy-northwind-co-install; schema ${SRC} not found).")

// ── the output folder ───────────────────────────────────────────────────────────────────────
File base = new File(OUT_DIR)
if (!base.isAbsolute()) base = new File(System.getProperty('PORTABLE_EXECUTABLE_DIR') ?: System.getProperty('DOCUMENTBURSTER_HOME') ?: System.getProperty('user.dir'), OUT_DIR)
File root = new File(base, NAME).canonicalFile
Path rootPath = root.toPath()
// A rerun replaces an earlier export (it has manifest.json, or .incomplete if it stopped half-way); any other
// folder of that name is somebody's, and is left alone.
if (root.exists()) {
    boolean ours = new File(root, 'manifest.json').exists() || new File(root, '.incomplete').exists() || root.list().length == 0
    if (!ours) throw new IllegalStateException("${root} exists and is not an earlier export (no manifest.json): move it away, or set OUT_DIR.")
    if (!root.deleteDir()) throw new IllegalStateException("Could not delete the earlier export in ${root} (a file open in another program?).")
}
root.mkdirs()
new File(root, '.incomplete').text = 'written by academy-files-export.groovy; manifest.json appears when the export is complete\n'
def fwd = { String p -> p.replace('\\', '/').replace("'", "''") }       // a path inside a DuckDB SQL string
def at = { String rel -> fwd(new File(root, rel).path) }
def relOf = { String f -> rootPath.relativize(new File(f).canonicalFile.toPath()).toString().replace('\\', '/') }

log.info("=== Files {} from {} on {} → {} ===", NAME, SRC, vendor, root)

// ── a private DuckDB, in memory, to shape and write the files ──────────────────────────────
// One thread: the same rows are written in the same order into the same files every run.
java.sql.Connection duckConn = new org.duckdb.DuckDBDriver().connect('jdbc:duckdb:', new Properties())
Sql mem = new Sql(duckConn)
def run = { String s -> mem.execute(s) }
def one = { String s -> mem.firstRow(s) }
def rowsOf = { String s -> mem.rows(s) }
run('SET threads = 1')
String duckVersion = one('SELECT version() AS v').v
Map<String, Integer> fileRows = new TreeMap<>()           // rows per file, for manifest.json
Map<String, List> fileDates = new TreeMap<>()             // min/max date per file

try {

// ── 1. the source rows ──────────────────────────────────────────────────────────────────────
// Streamed (a PostgreSQL cursor needs a transaction and a fetch size) straight into the private DuckDB.
def stream = { String query, Closure body ->
    dbSql.withStatement { it.fetchSize = 10000 }
    try { dbSql.withTransaction { dbSql.query(query, body) } } finally { dbSql.withStatement { } }
}
def load = { String table, String ddl, List<String> kinds, String query ->
    run("CREATE TABLE ${table} (${ddl})")
    def app = duckConn.unwrap(org.duckdb.DuckDBConnection).createAppender('main', table)
    long n = 0
    try { stream(query) { ResultSet rs -> n = Copy.rows(rs, app, kinds, table) } } finally { app.close() }
    log.info("  read {}: {} rows", table, n)
    n
}
load('src_orders', 'OrderID INTEGER, CustomerID VARCHAR, EmployeeID INTEGER, OrderDate DATE, RequiredDate DATE, ShippedDate DATE, ' +
     'ShipVia INTEGER, Freight DECIMAL(10,2), ShipName VARCHAR, ShipCity VARCHAR, ShipCountry VARCHAR, Status VARCHAR, Channel VARCHAR, DeliveryNotes VARCHAR, CreatedAt TIMESTAMP',
     ['int', 'str', 'int', 'date', 'date', 'date', 'int', 'dec2', 'str', 'str', 'str', 'str', 'str', 'str', 'ts'],
     """SELECT "OrderID", "CustomerID", "EmployeeID", "OrderDate", "RequiredDate", "ShippedDate", "ShipVia", "Freight",
               "ShipName", "ShipCity", "ShipCountry", "Status", "Channel", "DeliveryNotes", "CreatedAt" FROM ${S('Orders')}""")
long lineCount = load('src_lines', 'OrderID INTEGER, ProductID INTEGER, UnitPrice DECIMAL(10,2), Quantity INTEGER, Discount DECIMAL(4,2)',
     ['int', 'int', 'dec2', 'int', 'dec2'],
     """SELECT "OrderID", "ProductID", "UnitPrice", "Quantity", "Discount" FROM ${S('Order Details')}""")
load('src_products', 'ProductID INTEGER, CategoryID INTEGER', ['int', 'int'], """SELECT "ProductID", "CategoryID" FROM ${S('Products')}""")
load('src_web', 'WebOrderID INTEGER, OrderID INTEGER, ReceivedAt TIMESTAMP, Payload VARCHAR', ['int', 'int', 'ts', 'str'],
     """SELECT "WebOrderID", "OrderID", "ReceivedAt", "Payload" FROM ${S('WebOrders')}""")
load('src_status', 'StatusChangeID INTEGER, OrderID INTEGER, Status VARCHAR, ChangedAt TIMESTAMP', ['int', 'int', 'str', 'ts'],
     """SELECT h."StatusChangeID", h."OrderID", h."Status", h."ChangedAt" FROM ${S('OrderStatusHistory')} h
        WHERE h."OrderID" IN (SELECT "OrderID" FROM ${S('Orders')} WHERE "CreatedAt" >= TIMESTAMP '${DAILY_FROM} 00:00:00'
                                                                   AND "CreatedAt" <  TIMESTAMP '${AS_OF.plusDays(1)} 00:00:00')""")
List<Map> sourceInfo = dbSql.rows("SELECT \"Dataset\", \"Scale\", \"TableName\", \"RowCount\", \"Checksum\" FROM ${SRC}._dataset_info ORDER BY \"TableName\"".toString()).collect { r -> r.collectEntries { k, v -> [(k.toString()): v] } }

// ── 2. the order lines ──────────────────────────────────────────────────────────────────────
List<List<String>> LINE_COLS = [['OrderID', 'INTEGER', 'int'], ['OrderDate', 'DATE', 'date'], ['CustomerID', 'VARCHAR', 'string'],
    ['ProductID', 'INTEGER', 'int'], ['CategoryID', 'INTEGER', 'int'], ['ShipCountry', 'VARCHAR', 'string'], ['Channel', 'VARCHAR', 'string'],
    ['Status', 'VARCHAR', 'string'], ['UnitPrice', 'DECIMAL(10,2)', 'decimal(10,2)'], ['Quantity', 'INTEGER', 'int'], ['Discount', 'DECIMAL(4,2)', 'decimal(4,2)']]
String COLS = LINE_COLS.collect { it[0] }.join(', ')
String SORTED = 'ORDER BY OrderDate, OrderID, ProductID'
String CSV_TYPES = '{' + LINE_COLS.collect { "'${it[0]}': '${it[1]}'" }.join(', ') + '}'
def linesOf = { String orders, String lines, String products -> """
    SELECT l.OrderID, o.OrderDate, o.CustomerID, l.ProductID, p.CategoryID, o.ShipCountry, o.Channel, o.Status, l.UnitPrice, l.Quantity, l.Discount
    FROM ${lines} l JOIN ${orders} o ON o.OrderID = l.OrderID JOIN ${products} p ON p.ProductID = l.ProductID""".toString() }
run("CREATE TABLE order_lines AS SELECT * FROM (${linesOf('src_orders', 'src_lines', 'src_products')}) ${SORTED}")
long olCount = one('SELECT COUNT(*) AS n FROM order_lines').n
if (olCount != lineCount) throw new IllegalStateException("${lineCount} Order Details rows but ${olCount} order lines: a line without its order or product.")

// The reference checksum comes from the source database itself, by its own query: every file below must hold
// exactly these rows (RowSum ignores order, so a sorted and a scattered copy sum the same).
RowSum sourceSum = new RowSum()
stream("""SELECT d."OrderID", CAST(o."OrderDate" AS DATE), o."CustomerID", d."ProductID", p."CategoryID", o."ShipCountry", o."Channel",
                 o."Status", d."UnitPrice", d."Quantity", d."Discount"
          FROM ${S('Order Details')} d JOIN ${S('Orders')} o ON o."OrderID" = d."OrderID" JOIN ${S('Products')} p ON p."ProductID" = d."ProductID\"""") {
    ResultSet rs -> while (rs.next()) sourceSum.add(rs, LINE_COLS.size())
}
if (sourceSum.count != olCount) throw new IllegalStateException("source query gives ${sourceSum.count} lines, the export ${olCount}")

String PARQUET = "FORMAT parquet, COMPRESSION zstd, ROW_GROUP_SIZE ${ROW_GROUP_ROWS}"
String SCATTERED = 'ORDER BY (OrderID::BIGINT * 2654435761 + ProductID::BIGINT * 40503) % 4294967296, OrderID, ProductID'
run("COPY (SELECT *, strftime(OrderDate, '%Y-%m') AS month FROM order_lines ${SORTED}) TO '${at('order_lines')}' (${PARQUET}, PARTITION_BY (month))")
run("COPY (SELECT *, strftime(OrderDate, '%Y-%m') AS month FROM order_lines ${SORTED}) TO '${at('order_lines_csv')}' (FORMAT csv, HEADER, PARTITION_BY (month))")
run("COPY (SELECT *, strftime(OrderDate, '%Y-%m-%d') AS day FROM order_lines ${SORTED}) TO '${at('order_lines_daily')}' (${PARQUET}, PARTITION_BY (day))")
run("COPY (SELECT * FROM order_lines ${SORTED}) TO '${at('order_lines_sorted.parquet')}' (${PARQUET})")
run("COPY (SELECT * FROM order_lines ${SCATTERED}) TO '${at('order_lines_unsorted.parquet')}' (${PARQUET})")

// Read every copy back: the same rows as the source, and each partition holds only its own month or day.
List<String> DECLARED = LINE_COLS.collect { "${it[0]} ${it[1]}".toString() }
def sha256 = { byte[] b -> MessageDigest.getInstance('SHA-256').digest(b).encodeHex().toString() }
Map<String, Map> lineSets = [
    'order_lines'         : [fn: 'read_parquet', glob: "${at('order_lines')}/*/*.parquet", opts: 'hive_partitioning = false', key: 'month', format: 'parquet',
                             partitioning: 'month=YYYY-MM (hive), one file per month', sort: 'OrderDate, OrderID, ProductID'],
    'order_lines_csv'     : [fn: 'read_csv', glob: "${at('order_lines_csv')}/*/*.csv", opts: "header = true, columns = ${CSV_TYPES}, hive_partitioning = false", key: 'month', format: 'csv',
                             partitioning: 'month=YYYY-MM (hive), one file per month', sort: 'OrderDate, OrderID, ProductID'],
    'order_lines_daily'   : [fn: 'read_parquet', glob: "${at('order_lines_daily')}/*/*.parquet", opts: 'hive_partitioning = false', key: 'day', format: 'parquet',
                             partitioning: 'day=YYYY-MM-DD (hive), one file per day', sort: 'OrderDate, OrderID, ProductID'],
    'order_lines_sorted'  : [fn: 'read_parquet', glob: at('order_lines_sorted.parquet'), opts: '', format: 'parquet',
                             partitioning: 'none', sort: 'OrderDate, OrderID, ProductID'],
    'order_lines_unsorted': [fn: 'read_parquet', glob: at('order_lines_unsorted.parquet'), opts: '', format: 'parquet',
                             partitioning: 'none', sort: 'none: scattered by a hash of OrderID, ProductID'],
]
def readOf = { Map s, boolean withFile -> "${s.fn}('${s.glob}'${s.opts ? ', ' + s.opts : ''}${withFile ? ', filename = true' : ''})".toString() }
lineSets.each { String set, Map s ->
    List<String> schema = rowsOf("DESCRIBE SELECT * FROM ${readOf(s, false)}").collect { "${it.column_name} ${it.column_type}".toString() }
    if (schema != DECLARED) throw new IllegalStateException("${set}: columns ${schema}, declared ${DECLARED}")
    RowSum sum = new RowSum()
    mem.query("SELECT ${COLS} FROM ${readOf(s, false)}".toString()) { ResultSet rs -> while (rs.next()) sum.add(rs, LINE_COLS.size()) }
    if (sum.count != sourceSum.count || sum.hex() != sourceSum.hex())
        throw new IllegalStateException("${set}: read back ${sum.count} rows (checksum ${sum.hex()}), the source has ${sourceSum.count} (${sourceSum.hex()})")
    String fmt = s.key == 'day' ? '%Y-%m-%d' : '%Y-%m'
    rowsOf("""SELECT filename AS f, COUNT(*) AS n, MIN(OrderDate) AS lo, MAX(OrderDate) AS hi,
                     MIN(strftime(OrderDate, '${fmt}')) AS plo, MAX(strftime(OrderDate, '${fmt}')) AS phi
              FROM ${readOf(s, true)} GROUP BY filename""").each { r ->
        String rel = relOf(r.f)
        if (s.key && !(r.plo == r.phi && rel.contains("/${s.key}=${r.plo}/")))
            throw new IllegalStateException("${rel} holds rows from ${r.plo} to ${r.phi}")
        fileRows[rel] = r.n as int
        fileDates[rel] = [r.lo.toString(), r.hi.toString()]
    }
    s.rows = sum.count
    s.logical_checksum = sum.hex()
    s.files = fileRows.keySet().count { it == set + '.parquet' || it.startsWith(set + '/') }
    s.schema = schema
    s.schema_fingerprint = sha256(schema.join('\n').getBytes('UTF-8'))
}
if (lineSets.order_lines.files != 60) log.warn("order_lines has {} months, not 60", lineSets.order_lines.files)
log.info("  order lines: {} rows in 5 copies ({} monthly files, {} daily), each read back equal to the source", olCount, lineSets.order_lines.files, lineSets.order_lines_daily.files)

// What min/max statistics buy: row groups a reader must open for one month, sorted vs scattered.
Map pruning = [filter: "OrderDate in ${PRUNE_MONTH}", row_group_rows: ROW_GROUP_ROWS]
['order_lines_sorted.parquet', 'order_lines_unsorted.parquet'].each { String f ->
    LocalDate lo = LocalDate.parse(PRUNE_MONTH + '-01'), hi = lo.plusMonths(1).minusDays(1)
    def r = one("""SELECT COUNT(*) AS groups,
                          COUNT(*) FILTER (WHERE CAST(stats_min AS DATE) <= DATE '${hi}' AND CAST(stats_max AS DATE) >= DATE '${lo}') AS read
                   FROM parquet_metadata('${at(f)}') WHERE path_in_schema = 'OrderDate'""")
    pruning[f] = [row_groups: r.groups as int, row_groups_to_read: r.read as int]
}
log.info("  pruning for {}: sorted reads {} of {} row groups, unsorted {} of {}", PRUNE_MONTH,
         pruning['order_lines_sorted.parquet'].row_groups_to_read, pruning['order_lines_sorted.parquet'].row_groups,
         pruning['order_lines_unsorted.parquet'].row_groups_to_read, pruning['order_lines_unsorted.parquet'].row_groups)

// ── 3. the daily order CSVs ─────────────────────────────────────────────────────────────────
// Each day's file is the nightly export: the orders keyed into the ERP that day (CreatedAt), as they stood at the
// end of it — the Status they had then (OrderStatusHistory), a ShippedDate only if they had shipped by then.
// An order keyed days after its OrderDate is in the file of the day it was keyed, with its own OrderDate: a
// nightly export can only ship what exists. Orders keyed after the last day are in no file.
run("""CREATE TABLE day_orders AS
    WITH d AS (SELECT *, CAST(CreatedAt AS DATE) AS KeyedOn FROM src_orders
               WHERE CreatedAt >= TIMESTAMP '${DAILY_FROM} 00:00:00' AND CreatedAt < TIMESTAMP '${AS_OF.plusDays(1)} 00:00:00'),
    s AS (SELECT h.OrderID, h.Status, row_number() OVER (PARTITION BY h.OrderID ORDER BY h.ChangedAt DESC, h.StatusChangeID DESC) AS rn
          FROM src_status h JOIN d ON d.OrderID = h.OrderID WHERE h.ChangedAt < d.KeyedOn + INTERVAL 1 DAY)
    SELECT d.* EXCLUDE (Status, ShippedDate, CreatedAt), s.Status, CASE WHEN d.ShippedDate <= d.KeyedOn THEN d.ShippedDate END AS ShippedDate
    FROM d LEFT JOIN s ON s.OrderID = d.OrderID AND s.rn = 1""")
long noStatus = one('SELECT COUNT(*) AS n FROM day_orders WHERE Status IS NULL').n
if (noStatus > 0) throw new IllegalStateException("${noStatus} orders keyed in the daily window have no status by the end of that day in OrderStatusHistory")

List<String> V1 = ['OrderID', 'CustomerID', 'EmployeeID', 'OrderDate', 'RequiredDate', 'ShippedDate', 'ShipVia', 'Freight',
                   'ShipName', 'ShipCity', 'ShipCountry', 'Status', 'DeliveryNotes']
List<Map> LAYOUTS = [
    [version: 1, day: 1,  change: null,        column: null,       columns: V1],
    [version: 2, day: 11, change: 'ADDED',     column: 'Channel',  columns: V1 + ['Channel']],
    [version: 3, day: 18, change: 'RENAMED',   column: 'ShipVia',  columns: V1.collect { it == 'ShipVia' ? 'ShipperID' : it } + ['Channel'],
     detail: 'ShipVia is now called ShipperID; same values'],
    [version: 4, day: 24, change: 'RETYPED',   column: 'Freight',  columns: V1.collect { it == 'ShipVia' ? 'ShipperID' : it } + ['Channel'],
     detail: 'Freight written with a decimal comma ("12,50"), so the field is quoted'],
    [version: 5, day: 27, change: 'REORDERED', column: 'Channel',
     columns: V1.collect { it == 'ShipVia' ? 'ShipperID' : it }.with { List c -> c.take(2) + ['Channel'] + c.drop(2) },
     detail: 'Channel moves from the last column to the third, after CustomerID'],
]
LAYOUTS[1].detail = 'Channel appears as the last column'
def csvField = { Object v ->
    if (v == null) return ''
    String s = v instanceof BigDecimal ? v.toPlainString() : v.toString()
    (s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')) ? '"' + s.replace('"', '""') + '"' : s
}
List<Map> dailyFiles = []
(1..DAILY_DAYS).each { int dayNo ->
    LocalDate day = DAILY_FROM.plusDays(dayNo - 1)
    Map layout = LAYOUTS.findAll { it.day <= dayNo }.last()
    List<String> cols = layout.columns
    String rel = "orders/orders_${day}.csv"
    File f = new File(root, rel); f.parentFile.mkdirs()
    List rows = rowsOf("SELECT * FROM day_orders WHERE KeyedOn = DATE '${day}' ORDER BY OrderID")
    // Text the ERP's users typed that needs quoting (a comma in DeliveryNotes, say) — the natural noise of ETL S2 · 07.
    int quoted = rows.count { r -> ['ShipName', 'ShipCity', 'ShipCountry', 'DeliveryNotes'].any { c -> r[c] != null && r[c].toString() =~ /[,"\r\n]/ } }
    f.withWriter('UTF-8') { w ->
        w.write(cols.join(',') + '\n')
        rows.each { r ->
            List<String> out = cols.collect { String c ->
                Object v = c == 'ShipperID' ? r.ShipVia : r[c]
                if (v instanceof java.sql.Date || v instanceof LocalDate) v = Copy.stamp(v).toLocalDate()
                if (c == 'Freight' && layout.version >= 4 && v != null) v = ((BigDecimal) v).toPlainString().replace('.', ',')
                csvField(v)
            }
            w.write(out.join(',') + '\n')
        }
    }
    // Read it back as a loader would: the header is the declared layout, the rows are that day's orders.
    List<String> header = rowsOf("DESCRIBE SELECT * FROM read_csv('${fwd(f.path)}', header = true, all_varchar = true, delim = ',', quote = '\"', escape = '\"')").collect { it.column_name }
    if (header != cols) throw new IllegalStateException("${rel}: header ${header}, declared ${cols}")
    def back = one("""SELECT COUNT(*) AS n, string_agg(OrderID, ',' ORDER BY CAST(OrderID AS INTEGER)) AS ids, SUM(CAST(replace(Freight, ',', '.') AS DECIMAL(10,2))) AS freight
                      FROM read_csv('${fwd(f.path)}', header = true, all_varchar = true, delim = ',', quote = '"', escape = '"')""")
    String ids = rows.collect { it.OrderID }.join(',')
    BigDecimal freight = rows.sum(BigDecimal.ZERO) { (it.Freight ?: BigDecimal.ZERO) as BigDecimal }
    if (back.n != rows.size() || (rows && back.ids != ids) || (rows && (back.freight as BigDecimal) != freight))
        throw new IllegalStateException("${rel}: read back ${back.n} orders (Freight ${back.freight}), wrote ${rows.size()} (${freight})")
    fileRows[rel] = rows.size()
    List<LocalDate> dates = rows.collect { Copy.stamp(it.OrderDate).toLocalDate() }
    if (rows) fileDates[rel] = [dates.min().toString(), dates.max().toString()]
    dailyFiles << [day: dayNo, date: day.toString(), file: rel, orders: rows.size(), layout: layout.version, quoted_text_fields: quoted,
                   keyed_after_order_date: dates.count { it < day }]
}
List<Integer> changeDays = LAYOUTS.collect { it.day as int }.drop(1)
List quotedQuietDays = dailyFiles.findAll { it.quoted_text_fields > 0 && !(it.day in changeDays) }.collect { it.date }
if (!quotedQuietDays) log.warn("no day without a layout change has a quoted text field at scale {}: the quoted-comma noise of ETL S2 · 07 is missing", SCALE)
log.info("  daily orders: {} files, {}–{} orders a day, {} keyed after their OrderDate; quoted text fields on {}", DAILY_DAYS,
         dailyFiles.min { it.orders }.orders, dailyFiles.max { it.orders }.orders, dailyFiles.sum { it.keyed_after_order_date }, quotedQuietDays ?: 'no quiet day')

// ── 4. the Excel summary ────────────────────────────────────────────────────────────────────
// What a finance team keeps: a title block, the header on row 4, one row per order. The second month's sheet
// swaps Amount and Freight — the header says so, and a loader that reads by position loads Freight as Amount.
File xlsx = new File(root, 'order-summary.xlsx')
List<Map> sheetInfo = []
XSSFWorkbook wb = new XSSFWorkbook()
def dataFormat = wb.creationHelper.createDataFormat()
def dateStyle = wb.createCellStyle(); dateStyle.dataFormat = dataFormat.getFormat('dd/mm/yyyy')
def moneyStyle = wb.createCellStyle(); moneyStyle.dataFormat = dataFormat.getFormat('#,##0.00')
def headFont = wb.createFont(); headFont.bold = true
def headStyle = wb.createCellStyle(); headStyle.font = headFont
def titleFont = wb.createFont(); titleFont.bold = true; titleFont.fontHeightInPoints = (short) 14
def titleStyle = wb.createCellStyle(); titleStyle.font = titleFont
Map<String, Integer> WIDTHS = [OrderID: 10, OrderDate: 12, CustomerID: 12, Status: 11, Lines: 7, Amount: 13, Freight: 11]
SHEETS.each { String month ->
    boolean moved = month == SHEETS.last()
    List<String> cols = ['OrderID', 'OrderDate', 'CustomerID', 'Status', 'Lines'] + (moved ? ['Freight', 'Amount'] : ['Amount', 'Freight'])
    List rows = rowsOf("""SELECT o.OrderID, o.OrderDate, o.CustomerID, o.Status, COUNT(l.OrderID) AS Lines,
                                 COALESCE(ROUND(SUM(l.UnitPrice * l.Quantity * (1 - l.Discount)), 2), 0) AS Amount, o.Freight
                          FROM src_orders o LEFT JOIN src_lines l ON l.OrderID = o.OrderID
                          WHERE strftime(o.OrderDate, '%Y-%m') = '${month}' GROUP BY ALL ORDER BY o.OrderID""")
    LocalDate first = LocalDate.parse(month + '-01')
    def sh = wb.createSheet(month)
    def t = sh.createRow(0).createCell(0); t.setCellValue('Northwind Company — orders by month'); t.cellStyle = titleStyle
    sh.createRow(1).createCell(0).setCellValue(first.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + ' ' + first.year)
    sh.createRow(2).createCell(0).setCellValue('From the ERP, 31 December 2024. Amount: the order lines after discount, before freight.')
    def head = sh.createRow(3)
    cols.eachWithIndex { String c, int j -> def h = head.createCell(j); h.setCellValue(c); h.cellStyle = headStyle; sh.setColumnWidth(j, WIDTHS[c] * 256) }
    rows.eachWithIndex { r, int i ->
        def x = sh.createRow(4 + i)
        cols.eachWithIndex { String c, int j ->
            def cell = x.createCell(j)
            Object v = r[c]
            if (c == 'OrderDate') { cell.setCellValue(Copy.stamp(v).toLocalDate()); cell.cellStyle = dateStyle }
            else if (c in ['Amount', 'Freight']) { cell.setCellValue((v as BigDecimal).doubleValue()); cell.cellStyle = moneyStyle }
            else if (v instanceof Number) cell.setCellValue((v as Number).doubleValue())
            else cell.setCellValue(v.toString())
        }
    }
    sh.createFreezePane(0, 4)
    sheetInfo << [sheet: month, title_rows: 3, header_row: 4, columns: cols, orders: rows.size(),
                  amount: rows.sum(BigDecimal.ZERO) { it.Amount as BigDecimal }, freight: rows.sum(BigDecimal.ZERO) { it.Freight as BigDecimal },
                  planted: moved ? 'Amount and Freight swap places (columns F and G)' : null]
}
def core = wb.properties.coreProperties
core.creator = 'academy-files-export'
core.title = 'Northwind Company — orders by month'
Date stamp = Date.from(AS_OF.atStartOfDay().toInstant(ZoneOffset.UTC))
core.setCreated(Optional.of(stamp))
core.setModified(Optional.of(stamp))
ByteArrayOutputStream rawXlsx = new ByteArrayOutputStream()
wb.write(rawXlsx); wb.close()
// The same workbook twice gives the same bytes: the zip entries get a fixed time (POI stamps them with now).
xlsx.withOutputStream { os ->
    ZipOutputStream zout = new ZipOutputStream(os)
    ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(rawXlsx.toByteArray()))
    for (ZipEntry e = zin.nextEntry; e != null; e = zin.nextEntry) {
        ZipEntry n = new ZipEntry(e.name); n.setTimeLocal(AS_OF.atStartOfDay())
        zout.putNextEntry(n); zout << zin; zout.closeEntry()
    }
    zout.finish()
}
new XSSFWorkbook(new ByteArrayInputStream(xlsx.bytes)).withCloseable { XSSFWorkbook back ->
    sheetInfo.each { Map si ->
        def sh = back.getSheet(si.sheet)
        List<String> header = (0..<si.columns.size()).collect { sh.getRow(3).getCell(it).stringCellValue }
        int amountCol = header.indexOf('Amount')
        BigDecimal amount = (4..sh.lastRowNum).sum(BigDecimal.ZERO) { new BigDecimal(sh.getRow(it).getCell(amountCol).numericCellValue.toString()) }
        if (header != si.columns || sh.lastRowNum - 3 != si.orders || amount.setScale(2, RoundingMode.HALF_UP) != si.amount)
            throw new IllegalStateException("order-summary.xlsx ${si.sheet}: read back ${header}, ${sh.lastRowNum - 3} rows, Amount ${amount}")
    }
}
log.info("  workbook: {}", sheetInfo.collect { "${it.sheet} ${it.orders} orders" }.join(', '))

// ── 5. web orders, typed (Parquet with nested types) ───────────────────────────────────────
// The payload typed the way a warehouse stores it: ship_to a STRUCT, items a LIST of STRUCTs. The web shop sent
// some qty as text ("12") and some unit_price with a decimal comma ("19,96"); here they are numbers (counted in
// manifest.json). A cast that fails stops the export — nothing is dropped quietly.
run('''CREATE TABLE web_orders AS
    SELECT WebOrderID, OrderID, ReceivedAt,
           json_extract_string(Payload, '$.order_ref') AS order_ref,
           json_extract_string(Payload, '$.customer') AS customer,
           CAST(json_extract_string(Payload, '$.placed_at') AS TIMESTAMP) AS placed_at,
           CASE WHEN json_type(Payload, '$.ship_to') = 'OBJECT'
                THEN struct_pack(city := json_extract_string(Payload, '$.ship_to.city'), country := json_extract_string(Payload, '$.ship_to.country')) END AS ship_to,
           list_transform(CAST(json_extract(Payload, '$.items') AS JSON[]), i -> struct_pack(
               sku := json_extract_string(i, '$.sku'),
               qty := CAST(json_extract_string(i, '$.qty') AS INTEGER),
               unit_price := CAST(replace(json_extract_string(i, '$.unit_price'), ',', '.') AS DECIMAL(10,2)),
               discount := CAST(json_extract_string(i, '$.discount') AS DECIMAL(4,2)))) AS items
    FROM src_web''')
def webStats = one('''SELECT COUNT(*) AS items,
                             COUNT(*) FILTER (WHERE json_type(i, '$.qty') = 'VARCHAR') AS qty_as_text,
                             COUNT(*) FILTER (WHERE json_type(i, '$.unit_price') = 'VARCHAR') AS unit_price_as_text,
                             COUNT(*) FILTER (WHERE json_extract_string(i, '$.unit_price') LIKE '%,%') AS unit_price_decimal_comma
                      FROM (SELECT unnest(CAST(json_extract(Payload, '$.items') AS JSON[])) AS i FROM src_web)''')
long webCount = one('SELECT COUNT(*) AS n FROM web_orders').n
long typedItems = one('SELECT COALESCE(SUM(len(items)), 0) AS n FROM web_orders').n
long noShipTo = one('SELECT COUNT(*) AS n FROM web_orders WHERE ship_to IS NULL').n
if (typedItems != (webStats.items as long)) throw new IllegalStateException("web_orders: ${webStats.items} items in the payloads, ${typedItems} typed")
run("COPY (SELECT * FROM web_orders ORDER BY WebOrderID) TO '${at('web_orders.parquet')}' (${PARQUET})")
long webDiff = one("""SELECT (SELECT COUNT(*) FROM (SELECT * FROM read_parquet('${at('web_orders.parquet')}') EXCEPT ALL SELECT * FROM web_orders))
                           + (SELECT COUNT(*) FROM (SELECT * FROM web_orders EXCEPT ALL SELECT * FROM read_parquet('${at('web_orders.parquet')}'))) AS n""").n
if (webDiff != 0) throw new IllegalStateException("web_orders.parquet reads back ${webDiff} rows different")
fileRows['web_orders.parquet'] = webCount as int
Map webInfo = [rows: webCount, items: typedItems, without_ship_to: noShipTo, qty_as_text: webStats.qty_as_text,
               unit_price_as_text: webStats.unit_price_as_text, unit_price_decimal_comma: webStats.unit_price_decimal_comma,
               schema: rowsOf("DESCRIBE SELECT * FROM read_parquet('${at('web_orders.parquet')}')").collect { "${it.column_name} ${it.column_type}".toString() }]
log.info("  web_orders.parquet: {} orders, {} items ({} qty and {} unit_price sent as text, now typed)", webCount, typedItems, webStats.qty_as_text, webStats.unit_price_as_text)

// ── 6. web orders as an API ─────────────────────────────────────────────────────────────────
// GET api/web-orders.json, then follow page.next. The cursor names the last WebOrderID of the page before
// (base64url of "after:<id>"), as cursor APIs do; "order" is the payload exactly as the web shop sent it.
def cursorOf = { int id -> Base64.urlEncoder.withoutPadding().encodeToString("after:${id}".toString().getBytes('UTF-8')) }
def STAMP = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
List<String> page = []
int lastId = -1, pages = 0
String pageRel = 'api/web-orders.json'
def flush = { boolean more ->
    String next = more ? cursorOf(lastId) : null
    String nextRel = more ? "api/web-orders/${next}.json".toString() : null
    File f = new File(root, pageRel); f.parentFile.mkdirs()
    f.setText('{"data": [\n' + page.join(',\n') + '\n],\n "page": {"size": ' + API_PAGE_SIZE + ', "count": ' + page.size() +
              ', "next_cursor": ' + (next ? '"' + next + '"' : 'null') + ', "next": ' + (nextRel ? '"' + nextRel + '"' : 'null') + '}}\n', 'UTF-8')
    fileRows[pageRel] = page.size()
    pages++
    page = []
    pageRel = nextRel
}
List<Integer> webIds = []
mem.query('SELECT WebOrderID, OrderID, ReceivedAt, Payload FROM src_web ORDER BY WebOrderID') { ResultSet rs ->
    while (rs.next()) {
        if (page.size() == API_PAGE_SIZE) flush(true)
        lastId = rs.getInt(1)
        webIds << lastId
        Object orderId = rs.getObject(2)
        page << ('  {"id": ' + lastId + ', "order_id": ' + (orderId == null ? 'null' : orderId) + ', "received_at": "' +
                 Copy.stamp(rs.getObject(3)).format(STAMP) + '", "order": ' + rs.getString(4) + '}')
    }
}
flush(false)
// Walk the chain as a client would: every page parses, and the pages hold every web order once, in order.
List<Integer> walked = []
for (String rel = 'api/web-orders.json'; rel != null; ) {
    def p = new JsonSlurper().parse(new File(root, rel), 'UTF-8')
    walked.addAll(p.data.collect { it.id as int })
    rel = p.page.next
}
if (walked != webIds) throw new IllegalStateException("api/: following next gives ${walked.size()} web orders, the source has ${webIds.size()}")
log.info("  api: {} pages of up to {}", pages, API_PAGE_SIZE)

// ── 7. the Iceberg table ────────────────────────────────────────────────────────────────────
// iceberg/order_lines: format v2, the order lines partitioned by month(OrderDate), one data file per month.
// Snapshot 1 is Company on 2024-12-31. Snapshot 2 applies the first arrival day of the change log
// (northwind_co_changes_<scale>) to Orders, Order Details and Products: months whose lines changed get a new
// file (the old one is marked deleted), months that are new get their first file. Metadata, manifest lists
// and manifests are written here by the Iceberg spec (Avro, field ids); ids and UUIDs derive from the dataset's
// name, so a rerun writes the same table.
File tableDir = new File(root, 'iceberg/order_lines')
String tableLoc = ICEBERG_LOCATION ?: { String p = tableDir.path.replace('\\', '/'); 'file://' + (p.startsWith('/') ? '' : '/') + p }()
tableLoc = tableLoc.replaceAll('/+$', '')
// The |1| is a fixed part of every id: the table's UUIDs and snapshot ids are built from it, so it never changes.
def uuid = { String what -> UUID.nameUUIDFromBytes("${NAME}|1|${what}".toString().getBytes('UTF-8')).toString() }
def digest = { String what -> MessageDigest.getInstance('SHA-256').digest("${NAME}|1|${what}".toString().getBytes('UTF-8')) }
def snapshotIdOf = { String what -> new BigInteger(1, digest("snapshot|${what}")).longValue() & Long.MAX_VALUE }
def syncOf = { String file -> Arrays.copyOf(digest("sync|${file}"), 16) }
def le4 = { int v -> java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(v).array() }
def monthOrdinal = { String m -> ((m.substring(0, 4) as int) - 1970) * 12 + (m.substring(5, 7) as int) - 1 }
def instant = { long ms -> java.time.Instant.ofEpochMilli(ms).toString() }
// Iceberg's single-value serialization, for the column bounds
def bound = { String type, Object v ->
    if (type == 'int') return le4((v as Number).intValue())
    if (type == 'date') return le4((int) Copy.stamp(v).toLocalDate().toEpochDay())
    if (type == 'string') return v.toString().getBytes('UTF-8')
    int scale = (type =~ /,(\d+)\)/)[0][1] as int
    return new BigDecimal(v.toString()).setScale(scale, RoundingMode.UNNECESSARY).unscaledValue().toByteArray()
}
def toJson = { Object o -> groovy.json.JsonOutput.toJson(o) }
Map tableSchema = [type: 'struct', 'schema-id': 0,
                   fields: LINE_COLS.withIndex().collect { c, i -> [id: i + 1, name: c[0], required: false, type: c[2]] }]
List specFields = [[name: 'OrderDate_month', transform: 'month', 'source-id': 2, 'field-id': 1000]]
String FIELD_IDS = '{' + LINE_COLS.withIndex().collect { c, i -> "${c[0]}: ${i + 1}" }.join(', ') + '}'

// the Avro schemas of a manifest (manifest_entry) and a manifest list (manifest_file), Iceberg v2
def fld = { String name, Object type, int id, boolean optional = false ->
    optional ? [name: name, type: ['null', type], default: null, 'field-id': id] : [name: name, type: type, 'field-id': id] }
def mapOf = { int k, int v, String vt ->
    [type: 'array', logicalType: 'map', items: [type: 'record', name: "k${k}_v${v}".toString(),
        fields: [[name: 'key', type: 'int', 'field-id': k], [name: 'value', type: vt, 'field-id': v]]]] }
Map entrySchema = [type: 'record', name: 'manifest_entry', fields: [
    fld('status', 'int', 0), fld('snapshot_id', 'long', 1, true), fld('sequence_number', 'long', 3, true), fld('file_sequence_number', 'long', 4, true),
    fld('data_file', [type: 'record', name: 'r2', fields: [
        fld('content', 'int', 134), fld('file_path', 'string', 100), fld('file_format', 'string', 101),
        fld('partition', [type: 'record', name: 'r102', fields: [fld('OrderDate_month', 'int', 1000, true)]], 102),
        fld('record_count', 'long', 103), fld('file_size_in_bytes', 'long', 104),
        fld('value_counts', mapOf(119, 120, 'long'), 109, true), fld('null_value_counts', mapOf(121, 122, 'long'), 110, true),
        fld('lower_bounds', mapOf(126, 127, 'bytes'), 125, true), fld('upper_bounds', mapOf(129, 130, 'bytes'), 128, true)]], 2)]]
Map listSchema = [type: 'record', name: 'manifest_file', fields: [
    fld('manifest_path', 'string', 500), fld('manifest_length', 'long', 501), fld('partition_spec_id', 'int', 502), fld('content', 'int', 517),
    fld('sequence_number', 'long', 515), fld('min_sequence_number', 'long', 516), fld('added_snapshot_id', 'long', 503),
    fld('added_files_count', 'int', 504), fld('existing_files_count', 'int', 505), fld('deleted_files_count', 'int', 506),
    fld('added_rows_count', 'long', 512), fld('existing_rows_count', 'long', 513), fld('deleted_rows_count', 'long', 514),
    fld('partitions', [type: 'array', 'element-id': 508, items: [type: 'record', name: 'r508', fields: [
        fld('contains_null', 'boolean', 509), fld('contains_nan', 'boolean', 518, true),
        fld('lower_bound', 'bytes', 510, true), fld('upper_bound', 'bytes', 511, true)]]], 507, true)]]

// one data file: the month's lines, with Iceberg field ids, and the column statistics its manifest entry carries
def writeDataFile = { String table, String month, int seq ->
    String rel = "data/OrderDate_month=${month}/${String.format('%05d', seq)}-${month}.parquet"
    File f = new File(tableDir, rel); f.parentFile.mkdirs()
    run("COPY (SELECT ${COLS} FROM ${table} WHERE strftime(OrderDate, '%Y-%m') = '${month}' ${SORTED}) TO '${fwd(f.path)}' (${PARQUET}, FIELD_IDS ${FIELD_IDS})")
    def st = one('SELECT COUNT(*) AS n, ' + LINE_COLS.withIndex().collect { c, i -> "COUNT(${c[0]}) AS c${i}, MIN(${c[0]}) AS lo${i}, MAX(${c[0]}) AS hi${i}" }.join(', ') +
                 " FROM read_parquet('${fwd(f.path)}')")
    long n = st.n as long
    Map values = [:], nulls = [:], lows = [:], highs = [:]
    LINE_COLS.eachWithIndex { c, int i ->
        values[i + 1] = n
        nulls[i + 1] = n - (st["c${i}".toString()] as long)
        if (st["lo${i}".toString()] != null) { lows[i + 1] = bound(c[2], st["lo${i}".toString()]); highs[i + 1] = bound(c[2], st["hi${i}".toString()]) }
    }
    fileRows['iceberg/order_lines/' + rel] = n as int
    [local: f, month: month, seq: seq,
     data_file: [content: 0, file_path: "${tableLoc}/${rel}".toString(), file_format: 'PARQUET', partition: [OrderDate_month: monthOrdinal(month)],
                 record_count: n, file_size_in_bytes: f.length(), value_counts: values, null_value_counts: nulls, lower_bounds: lows, upper_bounds: highs]]
}
def entryOf = { Map df, int status, long snapshot, long seq -> [status: status, snapshot_id: snapshot, sequence_number: seq, file_sequence_number: seq, data_file: df.data_file] }
def writeManifest = { String name, List<Map> entries, long snapshot, long seq ->
    byte[] b = Avro.file(entrySchema, toJson(entrySchema), [schema: toJson(tableSchema), 'schema-id': '0', 'partition-spec': toJson(specFields),
                         'partition-spec-id': '0', 'format-version': '2', content: 'data'], entries, syncOf(name))
    File f = new File(tableDir, "metadata/${name}"); f.parentFile.mkdirs(); f.bytes = b
    List<Map> live = entries.findAll { it.status != 2 }
    List<Integer> months = entries.collect { it.data_file.partition.OrderDate_month as int }
    def rowsWith = { int status -> entries.findAll { it.status == status }.sum(0L) { it.data_file.record_count as long } }
    [manifest_path: "${tableLoc}/metadata/${name}".toString(), manifest_length: (long) b.length, partition_spec_id: 0, content: 0,
     sequence_number: seq, min_sequence_number: live ? live.min { it.sequence_number }.sequence_number : seq, added_snapshot_id: snapshot,
     added_files_count: entries.count { it.status == 1 }, existing_files_count: entries.count { it.status == 0 }, deleted_files_count: entries.count { it.status == 2 },
     added_rows_count: rowsWith(1), existing_rows_count: rowsWith(0), deleted_rows_count: rowsWith(2),
     partitions: [[contains_null: false, contains_nan: false, lower_bound: le4(months.min()), upper_bound: le4(months.max())]]]
}
def writeList = { long snapshot, Long parent, long seq, List<Map> manifests ->
    String name = "snap-${snapshot}-1-${uuid("list|${snapshot}")}.avro"
    byte[] b = Avro.file(listSchema, toJson(listSchema), ['snapshot-id': "${snapshot}", 'parent-snapshot-id': "${parent == null ? 'null' : parent}",
                         'sequence-number': "${seq}", 'format-version': '2'], manifests, syncOf(name))
    new File(tableDir, "metadata/${name}").bytes = b
    "${tableLoc}/metadata/${name}".toString()
}
def totals = { List<Map> liveFiles -> ['total-records': "${liveFiles.sum(0L) { it.data_file.record_count }}".toString(),
    'total-files-size': "${liveFiles.sum(0L) { it.data_file.file_size_in_bytes }}".toString(), 'total-data-files': "${liveFiles.size()}".toString(),
    'total-delete-files': '0', 'total-position-deletes': '0', 'total-equality-deletes': '0'] }
def metadataOf = { List<Map> snapshots, List<Map> metadataLog ->
    Map current = snapshots.last()
    ['format-version': 2, 'table-uuid': uuid('table'), location: tableLoc, 'last-sequence-number': current['sequence-number'],
     'last-updated-ms': current['timestamp-ms'], 'last-column-id': LINE_COLS.size(), 'current-schema-id': 0, schemas: [tableSchema],
     'default-spec-id': 0, 'partition-specs': [['spec-id': 0, fields: specFields]], 'last-partition-id': 1000,
     'default-sort-order-id': 0, 'sort-orders': [['order-id': 0, fields: []]], properties: ['write.format.default': 'parquet'],
     'current-snapshot-id': current['snapshot-id'], refs: [main: ['snapshot-id': current['snapshot-id'], type: 'branch']],
     snapshots: snapshots, 'snapshot-log': snapshots.collect { ['timestamp-ms': it['timestamp-ms'], 'snapshot-id': it['snapshot-id']] },
     'metadata-log': metadataLog]
}
def writeMetadata = { int v, Map m -> new File(tableDir, "metadata/v${v}.metadata.json").setText(groovy.json.JsonOutput.prettyPrint(toJson(m)) + '\n', 'UTF-8') }
// what a snapshot holds must be exactly the rows it stands for
def checkSnapshot = { String what, List<Map> liveFiles, RowSum expected ->
    RowSum got = new RowSum()
    String paths = liveFiles.collect { "'${fwd(it.local.path)}'" }.join(', ')
    mem.query("SELECT ${COLS} FROM read_parquet([${paths}])".toString()) { ResultSet rs -> while (rs.next()) got.add(rs, LINE_COLS.size()) }
    if (got.count != expected.count || got.hex() != expected.hex())
        throw new IllegalStateException("Iceberg ${what}: its files hold ${got.count} rows (${got.hex()}), expected ${expected.count} (${expected.hex()})")
}

// snapshot 1: Company on 2024-12-31
long snap1 = snapshotIdOf('1'), ts1 = AS_OF.atTime(23, 59, 59).toInstant(ZoneOffset.UTC).toEpochMilli()
List<String> months = rowsOf("SELECT DISTINCT strftime(OrderDate, '%Y-%m') AS m FROM order_lines ORDER BY 1").collect { it.m }
Map<String, Map> files1 = months.collectEntries { [(it): writeDataFile('order_lines', it, 1)] }
checkSnapshot('snapshot 1', files1.values() as List, sourceSum)
Map manifest1 = writeManifest("${uuid('manifest|1')}-m0.avro", files1.values().collect { entryOf(it, 1, snap1, 1) }, snap1, 1)
Map snapshot1 = ['snapshot-id': snap1, 'sequence-number': 1, 'timestamp-ms': ts1, 'manifest-list': writeList(snap1, null, 1, [manifest1]),
                 summary: [operation: 'append', 'added-data-files': "${files1.size()}".toString(), 'added-records': "${olCount}".toString(),
                           'added-files-size': "${files1.values().sum(0L) { it.data_file.file_size_in_bytes }}".toString(),
                           'changed-partition-count': "${files1.size()}".toString()] + totals(files1.values() as List),
                 'schema-id': 0]
writeMetadata(1, metadataOf([snapshot1], []))
Map icebergInfo = [location: tableLoc, format_version: 2, partitioning: 'month(OrderDate)',
                   snapshots: [[snapshot_id: snap1, sequence_number: 1, timestamp: instant(ts1), metadata: 'iceberg/order_lines/metadata/v1.metadata.json',
                                operation: 'append', what: "Company on ${AS_OF}".toString(), data_files: files1.size(), records: olCount]]]
int currentVersion = 1

// snapshot 2: the change log's first arrival day
String skip2 = null
if (!exists(CHANGES, 'event_log')) skip2 = "no change log (${CHANGES}): install academy-northwind-co-changes at scale ${SCALE} for the second snapshot".toString()
List<Map> events = []
Map changeInfo = [:]
LocalDateTime lastArrival = null
if (!skip2) {
    LocalDate day1 = Copy.stamp(dbSql.firstRow("SELECT MIN(arrival_time) AS t FROM ${CHANGES}.event_log".toString()).t).toLocalDate()
    Set<String> seen = [] as Set
    int unreadable = 0, unknownOp = 0, twice = 0, otherTables = 0
    Map<String, Integer> tables = new TreeMap<>()
    dbSql.rows("SELECT seq, arrival_time, line FROM ${CHANGES}.event_log WHERE arrival_time < TIMESTAMP '${day1.plusDays(1)} 00:00:00' ORDER BY seq".toString()).each { r ->
        LocalDateTime t = Copy.stamp(r.arrival_time)
        if (lastArrival == null || t > lastArrival) lastArrival = t
        def e = null
        try { e = new JsonSlurper().parseText(r.line.toString()) } catch (Exception ignored) { }
        if (!(e instanceof Map) || !e.event_id || !e.table || !e.event_time) { unreadable++; return }
        if (!seen.add(e.event_id.toString())) { twice++; return }
        if (!(e.table in ['Orders', 'Order Details', 'Products'])) { otherTables++; return }
        if (!(e.op in ['c', 'u', 'd'])) { unknownOp++; return }
        if (!(e.key instanceof Map) || (e.op != 'd' && !(e.after instanceof Map))) { unreadable++; return }
        tables[e.table] = (tables[e.table] ?: 0) + 1
        events << e
    }
    // applied in the order they happened: a late arrival lands where its event_time puts it
    events.sort { a, b -> a.event_time.toString() <=> b.event_time.toString() ?: ((a.tx ?: 0) as long) <=> ((b.tx ?: 0) as long) ?: a.event_id.toString() <=> b.event_id.toString() }
    changeInfo = [schema: CHANGES, arrival_day: day1.toString(), events_applied: events.size(), applied_by_table: tables,
                  skipped: [delivered_twice: twice, unreadable: unreadable, unknown_op: unknownOp, other_tables: otherTables]]
    if (!events) skip2 = "the change log's first day (${day1}) has no Orders, Order Details or Products events".toString()
}
if (!skip2) {
    run('CREATE TABLE ib_orders (OrderID INTEGER PRIMARY KEY, OrderDate DATE, CustomerID VARCHAR, ShipCountry VARCHAR, Channel VARCHAR, Status VARCHAR)')
    run('INSERT INTO ib_orders SELECT OrderID, OrderDate, CustomerID, ShipCountry, Channel, Status FROM src_orders')
    run('CREATE TABLE ib_lines (OrderID INTEGER, ProductID INTEGER, UnitPrice DECIMAL(10,2), Quantity INTEGER, Discount DECIMAL(4,2), PRIMARY KEY (OrderID, ProductID))')
    run('INSERT INTO ib_lines SELECT * FROM src_lines')
    run('CREATE TABLE ib_products (ProductID INTEGER PRIMARY KEY, CategoryID INTEGER)')
    run('INSERT INTO ib_products SELECT * FROM src_products')
    def dateOf = { Object v -> LocalDateTime t = Copy.stamp(v); if (t.toLocalTime() != java.time.LocalTime.MIDNIGHT) throw new IllegalStateException("change log: OrderDate ${v} has a time of day"); t.toLocalDate().toString() }
    def dec2 = { Object v -> v == null ? null : new BigDecimal(v.toString()).setScale(2, RoundingMode.UNNECESSARY) }
    List<String> offKey = []                                  // a create for a row that exists, or a change for one that does not
    events.each { e ->
        Map k = e.key, a = e.after
        if (e.table == 'Orders') {
            boolean has = mem.firstRow('SELECT COUNT(*) AS n FROM ib_orders WHERE OrderID = ?', [k.OrderID as int]).n > 0
            if ((e.op == 'c') == has) offKey << e.event_id
            if (e.op == 'd') mem.execute('DELETE FROM ib_orders WHERE OrderID = ?', [k.OrderID as int])
            else mem.execute('INSERT OR REPLACE INTO ib_orders VALUES (?, CAST(? AS DATE), ?, ?, ?, ?)',
                             [a.OrderID as int, dateOf(a.OrderDate), a.CustomerID, a.ShipCountry, a.Channel, a.Status])
        } else if (e.table == 'Order Details') {
            boolean has = mem.firstRow('SELECT COUNT(*) AS n FROM ib_lines WHERE OrderID = ? AND ProductID = ?', [k.OrderID as int, k.ProductID as int]).n > 0
            if ((e.op == 'c') == has) offKey << e.event_id
            if (e.op == 'd') mem.execute('DELETE FROM ib_lines WHERE OrderID = ? AND ProductID = ?', [k.OrderID as int, k.ProductID as int])
            else mem.execute('INSERT OR REPLACE INTO ib_lines VALUES (?, ?, ?, ?, ?)',
                             [a.OrderID as int, a.ProductID as int, dec2(a.UnitPrice), a.Quantity as int, dec2(a.Discount)])
        } else {
            boolean has = mem.firstRow('SELECT COUNT(*) AS n FROM ib_products WHERE ProductID = ?', [k.ProductID as int]).n > 0
            if ((e.op == 'c') == has) offKey << e.event_id
            if (e.op == 'd') mem.execute('DELETE FROM ib_products WHERE ProductID = ?', [k.ProductID as int])
            else mem.execute('INSERT OR REPLACE INTO ib_products VALUES (?, ?)', [a.ProductID as int, a.CategoryID as int])
        }
    }
    if (offKey) skip2 = ("${CHANGES} does not continue this ${SRC} (${offKey.size()} events, e.g. ${offKey.take(3)}, create rows that exist or change rows " +
                         "that do not): reinstall academy-northwind-co-changes at scale ${SCALE}").toString()
}
if (skip2) {
    log.warn("Iceberg: snapshot 1 only — {}", skip2)
    icebergInfo.second_snapshot_skipped = skip2
} else {
    run("CREATE TABLE order_lines_2 AS ${linesOf('ib_orders', 'ib_lines', 'ib_products')}")
    RowSum sum2 = new RowSum()
    mem.query("SELECT ${COLS} FROM order_lines_2".toString()) { ResultSet rs -> while (rs.next()) sum2.add(rs, LINE_COLS.size()) }
    List<String> changed = rowsOf("""SELECT DISTINCT strftime(OrderDate, '%Y-%m') AS m FROM (
            (SELECT ${COLS} FROM order_lines EXCEPT ALL SELECT ${COLS} FROM order_lines_2)
            UNION ALL (SELECT ${COLS} FROM order_lines_2 EXCEPT ALL SELECT ${COLS} FROM order_lines)) ORDER BY 1""").collect { it.m }
    Set<String> months2 = rowsOf("SELECT DISTINCT strftime(OrderDate, '%Y-%m') AS m FROM order_lines_2").collect { it.m } as Set
    long snap2 = snapshotIdOf('2'), ts2 = lastArrival.toInstant(ZoneOffset.UTC).toEpochMilli()
    List<Map> added = changed.findAll { it in months2 }.collect { writeDataFile('order_lines_2', it, 2) }
    List<Map> replaced = changed.findAll { files1.containsKey(it) }.collect { files1[it] }
    List<Map> kept = files1.findAll { !(it.key in changed) }.values() as List
    checkSnapshot('snapshot 2', kept + added, sum2)
    Map addedManifest = writeManifest("${uuid('manifest|2|added')}-m0.avro", added.collect { entryOf(it, 1, snap2, 2) }, snap2, 2)
    List<Map> manifests2
    if (!replaced) manifests2 = [addedManifest, manifest1]      // an append: the first manifest carries over as it is
    else manifests2 = [addedManifest, writeManifest("${uuid('manifest|2|rewritten')}-m1.avro",
                           kept.collect { entryOf(it, 0, snap1, 1) } + replaced.collect { entryOf(it, 2, snap2, 1) }, snap2, 2)]
    long addedRecords = added.sum(0L) { it.data_file.record_count }, deletedRecords = replaced.sum(0L) { it.data_file.record_count }
    String operation = replaced ? 'overwrite' : 'append'
    Map snapshot2 = ['snapshot-id': snap2, 'parent-snapshot-id': snap1, 'sequence-number': 2, 'timestamp-ms': ts2,
                     'manifest-list': writeList(snap2, snap1, 2, manifests2),
                     summary: [operation: operation, 'added-data-files': "${added.size()}".toString(), 'deleted-data-files': "${replaced.size()}".toString(),
                               'added-records': "${addedRecords}".toString(), 'deleted-records': "${deletedRecords}".toString(),
                               'added-files-size': "${added.sum(0L) { it.data_file.file_size_in_bytes }}".toString(),
                               'removed-files-size': "${replaced.sum(0L) { it.data_file.file_size_in_bytes }}".toString(),
                               'changed-partition-count': "${changed.size()}".toString()] + totals(kept + added),
                     'schema-id': 0]
    writeMetadata(2, metadataOf([snapshot1, snapshot2], [['timestamp-ms': ts1, 'metadata-file': "${tableLoc}/metadata/v1.metadata.json".toString()]]))
    currentVersion = 2
    icebergInfo.snapshots << [snapshot_id: snap2, parent_snapshot_id: snap1, sequence_number: 2, timestamp: instant(ts2),
                              metadata: 'iceberg/order_lines/metadata/v2.metadata.json', operation: operation,
                              what: "after change-log day 1 (${changeInfo.arrival_day})".toString(), months_changed: changed,
                              data_files_added: added.size(), data_files_deleted: replaced.size(), records: sum2.count,
                              records_added: sum2.count - olCount, change_log: changeInfo]
    log.info("  iceberg: snapshot 2 {} — {} events of {} change {} month(s) {}; {} → {} lines",
             operation, events.size(), changeInfo.arrival_day, changed.size(), changed, olCount, sum2.count)
}
new File(tableDir, 'metadata/version-hint.text').text = "${currentVersion}"
icebergInfo.current_metadata = "iceberg/order_lines/metadata/v${currentVersion}.metadata.json".toString()
log.info("  iceberg: {} snapshot(s), {} data files, location {}", icebergInfo.snapshots.size(),
         fileRows.keySet().count { it.startsWith('iceberg/') }, tableLoc)

// ── 8. manifest.json ────────────────────────────────────────────────────────────────────────
// Written last: its presence says the export is complete. Every file with its size and SHA-256; no clock time.
List<Map> files = []
List<String> paths = []
Files.walk(rootPath).withCloseable { walk -> walk.filter { Files.isRegularFile(it) }.forEach { paths << rootPath.relativize(it).toString().replace('\\', '/') } }
paths.sort().each { String rel ->
    if (rel in ['manifest.json', '.incomplete']) return
    File f = new File(root, rel)
    MessageDigest md = MessageDigest.getInstance('SHA-256')
    f.eachByte(1 << 16) { byte[] b, int n -> md.update(b, 0, n) }
    Map entry = [path: rel, bytes: f.length(), sha256: md.digest().encodeHex().toString()]
    if (fileRows.containsKey(rel)) entry.rows = fileRows[rel]
    if (fileDates.containsKey(rel)) { entry.min_order_date = fileDates[rel][0]; entry.max_order_date = fileDates[rel][1] }
    files << entry
}
def setOf = { String prefix -> files.findAll { it.path == prefix || it.path.startsWith(prefix + '/') } }
Map manifest = [
    dataset: DATASET, scale: SCALE, name: NAME, as_of: AS_OF.toString(),
    source: [schema: SRC, dataset_info: sourceInfo],
    generator: [script: 'academy-files-export.groovy', duckdb: duckVersion, poi: org.apache.poi.Version.version],
    parquet: [writer: "DuckDB ${duckVersion}".toString(), compression: 'zstd', row_group_rows: ROW_GROUP_ROWS, statistics: 'min/max per row group'],
    sets: [
        [name: 'orders', path: 'orders/', format: 'csv', files: DAILY_DAYS, rows: dailyFiles.sum { it.orders },
         what: "the nightly export of the orders keyed each day from ${DAILY_FROM} to ${AS_OF}, as they stood that night".toString(),
         keyed_after_order_date: dailyFiles.sum { it.keyed_after_order_date }],
        [name: 'order-summary', path: 'order-summary.xlsx', format: 'xlsx', files: 1, sheets: sheetInfo]] +
        lineSets.collect { String name, Map s ->
            String path = s.glob.toString().contains('*') ? name : name + '.parquet'
            [name: name, path: s.glob.toString().contains('*') ? name + '/' : path, format: s.format, files: s.files, rows: s.rows,
             bytes: setOf(path).sum(0L) { it.bytes }, partitioning: s.partitioning, sort: s.sort,
             schema: s.schema, schema_fingerprint: s.schema_fingerprint, logical_checksum: s.logical_checksum]
        } + [
        [name: 'web_orders', path: 'web_orders.parquet', format: 'parquet', files: 1] + webInfo,
        [name: 'api', path: 'api/', format: 'json', files: pages, rows: webIds.size(), page_size: API_PAGE_SIZE, first_page: 'api/web-orders.json',
         cursor: 'base64url (no padding) of "after:<last WebOrderID of the previous page>"', next: 'a path from the folder manifest.json is in'],
        [name: 'iceberg/order_lines', path: 'iceberg/order_lines/', format: 'iceberg', files: setOf('iceberg/order_lines').size()] + icebergInfo],
    daily_orders: [layouts: LAYOUTS.collect { [version: it.version, from_day: it.day, from_date: DAILY_FROM.plusDays((it.day as int) - 1).toString(),
                                               change: it.change, column: it.column, detail: it.detail, columns: it.columns] },
                   days: dailyFiles, quoted_text_on_days_without_a_change: quotedQuietDays],
    pruning: pruning,
    object_store: [bucket: 'academy', prefix: "${NAME}/".toString(), s3: "s3://academy/${NAME}/".toString(), http: "http://localhost:9100/academy/${NAME}/".toString(),
                   how: 'docker compose up -d minio minio-academy-files (db/docker-compose.yml) publishes db/academy-files; the port is MINIO_PORT in db/.env',
                   iceberg_location: "s3://academy/${NAME}/iceberg/order_lines".toString(),
                   iceberg_note: 'rerun with ICEBERG_LOCATION set to iceberg_location for a table readers open on MinIO; as written, it opens where it was written'],
    files: files,
]
new File(root, 'manifest.json').setText(groovy.json.JsonOutput.prettyPrint(toJson(manifest)) + '\n', 'UTF-8')
new File(root, '.incomplete').delete()

log.info("=== {} written to {}: {} files, {} bytes; {} order lines, {} daily order files, Iceberg {} snapshot(s) ===",
         NAME, root, files.size(), files.sum(0L) { it.bytes }, olCount, DAILY_DAYS, icebergInfo.snapshots.size())

} finally {
    mem.close()
}
