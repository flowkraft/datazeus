// @description Academy dataset: generates "Northwind Company Changes" — the company's next 90 days (Jan–Mar 2025) as a replayable change-data log — from an installed Northwind Company schema of the same scale. Set SCALE below. Idempotent — drops and recreates the schema.
// Bindings provided by GenericSeedExecutor:
//   dbSql  — groovy.sql.Sql connected to the target database
//   vendor — String (uppercase): POSTGRES, DUCKDB, CLICKHOUSE
//   log    — SLF4J Logger
//   params — Map; optional keys: SCALE, EXPORT_DIR (also write the log as one JSONL file per arrival day),
//            FIGURES_FILE (write the counts the curriculum quotes as YAML; see section 6b)

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import groovy.transform.CompileStatic

// Weighted picks by binary search over running totals: the first index whose running total exceeds x.
@CompileStatic
class Cumulative {
    final double[] cum
    Cumulative(List<Number> weights) { cum = new double[weights.size()]; double acc = 0; for (int i = 0; i < cum.length; i++) { acc += weights[i].doubleValue(); cum[i] = acc } }
    double total() { cum[cum.length - 1] }
    int find(double x) { int lo = 0, hi = cum.length - 1; while (lo < hi) { int mid = (lo + hi) >>> 1; if (x < cum[mid]) hi = mid else lo = mid + 1 }; lo }
}

// One event of the log, already as the JSON it is written as: its key, and its rows as the end of its line. At L the
// log carries over half a million events, and as maps they would not fit in the Java heap of the DataPallas that runs this.
@CompileStatic
class Event {
    final String table, op, key
    String rows                     // ,"before":{...},"after":{...}}
    Event(String table, String op, String key, String rows) { this.table = table; this.op = op; this.key = key; this.rows = rows }
}

// ═════════════════════════════════════════════════════════════════════════════════════════════
// SCALE — 'S', 'M' or 'L', the same as the installed Northwind Company it continues: northwind_co_s gets
// northwind_co_changes_s, and so on. params.SCALE, when the Seed Data tab passes one, wins over this line.
// ═════════════════════════════════════════════════════════════════════════════════════════════
String SCALE = (params?.SCALE ?: 'S').toString().toUpperCase()
// Also write the log as files, one per arrival day (events-2025-01-01.jsonl ...), for the lessons that read files.
// Empty: the database table only.
String EXPORT_DIR = (params?.EXPORT_DIR ?: '').toString()
// Where to write the figures manifest (section 6b). Empty and no EXPORT_DIR: not written at all.
String FIGURES_FILE = (params?.FIGURES_FILE ?: '').toString()
// ═════════════════════════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────────────────────
// WHAT THIS IS
// Northwind Company stops on 2024-12-31. This is what happened next: 1 January to 31 March 2025, as the company's
// database changes arrive at a consumer, one event per changed row. It continues the source; it never
// contradicts it. Its orders go on from the source's last OrderID, its customers and prices change from their values
// on 2024-12-31, the source's open orders ship in January and its unpaid invoices get paid. Nothing random between
// runs: the same source always gives the same log (_dataset_info). Design: kraft-src-company-biz/.docs/plan-academy-
// datasets.md, D2. The log is applied to the landed copy by academy-northwind-co-raw-install.groovy (THROUGH_DAY).
//
// THE TABLES
//   event_log   one row per delivery, in the order the consumer received them:
//                 seq           delivery order (1, 2, 3 ...)
//                 offset        the event's position in the log. A redelivered stretch repeats offsets it already had.
//                 arrival_time  when the consumer received it. Day N of the log is arrival day N (2025-01-01 = 1).
//                 line          the event, one JSON object:
//                   {"event_id", "tx", "table", "op" (c create, u update, d delete), "key", "event_time",
//                    "before" (d, u), "after" (c, u)}
//                 event_time is when the row changed in the source (for an order, when it was keyed).
//                 Rows use the source's column names; dates are yyyy-MM-dd, timestamps yyyy-MM-ddTHH:mm:ss.
//                 "table": "_control" is the source's own daily count, sent at 02:00 for the day before:
//                 {"day", "orders", "order_lines", "amount"} of the orders keyed that day.
//   _plants     every planted incident: what, which lesson, which arrival day, where to find it
//   _dataset_info  row counts and checksums (academy-verify.groovy reads it)
//
// THE BUSINESS, at the source's own rates (so M and L carry the same story at their size)
//   orders       as many a day as the same quarter of 2024 had, plus 10% growth (S ≈ 7 a day); 1–6 lines each at
//                the price of the moment they were keyed; ~1.5% keyed 1–10 days after their OrderDate, as in the
//                source; shipped 2–7 days later (2% two weeks late), cancelled 1.5% of the time, paid 10–49 days
//                after shipping. Each step is an update to Orders (UpdatedAt moves) plus the rows it creates:
//                OrderStatusHistory, Invoices, WebOrders for web orders.
//   customers    as many changes (Segment, City, ContactName) as the source averages over 90 days; each is an
//                update to Customers plus a CustomerChanges row. New customers at the source's joining rate, at
//                least five.
//   prices       the source's rate, about one a week at S: an update to Products plus a PriceChanges row.
//   Not here: StockMovements, CourierConfirmations, SalesTargets and the reference tables do not change.
//
// PLANTED (every one is in _plants, with the lesson that needs it)
//   Delivery      3% of transactions arrive 1–5 days late, 5% a few minutes out of order; 0.5% are delivered twice
//                 (same event_id, a new offset); a few are sent again 8–10 days later, outside a 7-day dedup
//                 window; on day 60 a stretch of day 59's offsets is delivered again (a consumer restart); one
//                 truncated, unparseable line on day 52 (the intact one follows it); on day 58, 14:00–14:20, a burst
//                 of records with no usable key, op or table, which the apply step rejects; one order create
//                 delivered twice INSIDE the last day, so one duplicate sits in a single daily partition while
//                 every other duplicate in the log straddles two of them or is far back in history.
//   Deletes       an order line removed from an open order (Orders.UpdatedAt does not move); an order keyed in error
//                 and deleted 25 minutes later.
//   Watermarks    on days 15, 40 and 65 the day's last Orders update shares its UpdatedAt, to the minute, with one
//                 that arrives just after midnight (> misses it, >= does not); two orders dated the last day of a
//                 month but keyed in the next.
//   Dimensions    new customers whose first web order arrives before the customer record (it is keyed later);
//                 one new customer whose record never arrives; a big customer's Segment change and a price change,
//                 each with an order keyed BEFORE the change that arrives AFTER it (as-of join vs lookup).
//   Day 45        the bad load: an Orders row repeating an existing OrderID, a line with a negative Quantity, an
//                 order for a CustomerID that does not exist, a status typed as 'shipped '. One row each.
//   Freshness     the WebOrders feed stops after day 70 while Orders keeps arriving.
//   Status        an order cancelled and reinstated, then shipped.
//
// WHO NEEDS WHAT
//   ETL S2 · 10, S3 · 15, S4 · 00–45    the log itself: CDC, event time, dedup, poison records, replay, reconciling
//   ETL S2 (gates), dbt S2 · 50          day 45
//   dbt S1 · 10                          the stopped WebOrders feed (through the raw copy's _loaded_at)
//   dbt S2 (snapshots, incremental), S3  the customer/price changes and order updates, applied day by day
//   dbt S3 · 20                          the duplicate inside the last day: unique over a window vs over all history
//   Data Warehousing S1 · 35, S3 · 25    incremental loads and streaming ingestion
// ENGINES: PostgreSQL, DuckDB and ClickHouse, the engine the source was installed on. The same source gives the same
// log on all three: ClickHouse is asked for its dates and times as text, in the form the JSON uses.
// NOT HERE: the transform bug of ETL S4 · 05/40, which is the lesson's own code, not the data.
// ─────────────────────────────────────────────────────────────────────────────────────────────

if (!(SCALE in ['S', 'M', 'L'])) throw new IllegalArgumentException("SCALE must be S, M or L (got ${SCALE}).")
if (!(vendor in ['POSTGRES', 'DUCKDB', 'CLICKHOUSE'])) throw new IllegalArgumentException("Only PostgreSQL, DuckDB and ClickHouse are supported (connection is ${vendor}).")
String DATASET = 'northwind_co_changes'
String SRC = "northwind_co_${SCALE.toLowerCase()}".toString()
String DST = "${DATASET}_${SCALE.toLowerCase()}".toString()
LocalDate DAY_ZERO = LocalDate.of(2024, 12, 31)
LocalDate FIRST = LocalDate.of(2025, 1, 1), LAST = LocalDate.of(2025, 3, 31)
int DAYS = (int) ChronoUnit.DAYS.between(DAY_ZERO, LAST)                    // 90
int WEB_FEED_LAST_DAY = 70, BAD_DAY = 45, POISON_DAY = 52, REJECT_DAY = 58, REDELIVERY_DAY = 60
List<Integer> TIE_DAYS = [15, 40, 65]

def S = { String table -> "${SRC}.\"${table}\"".toString() }
def T = { String table -> "${DST}.${table}".toString() }
def count = { String sql -> (dbSql.firstRow(sql.toString()).values().first() as Number) }

// ── ClickHouse ──────────────────────────────────────────────────────────────────────────────
// Reads: a date or time comes back as text — yyyy-MM-dd, yyyy-MM-ddTHH:mm:ss — never as a driver object, which would
// pass through the driver's time zone. So SELECT * becomes the table's columns, in order, with those wrapped (selectAll).
// A SELECT alias in ClickHouse also answers to its name in WHERE; these reads filter on the table's own column, so
// they qualify it and ask for column names first (CH_READ). Writes: the DDL below is translated as in
// academy-northwind-co-install (VARCHAR → String, INTEGER → Int32, TIMESTAMP → DateTime, MergeTree ordered by the
// primary key) and the rows go in as batched INSERTs, times as text.
boolean CH = vendor == 'CLICKHOUSE'
String CH_READ = CH ? ' SETTINGS prefer_column_name_to_alias = 1' : ''
def ts = { String d -> CH ? "toDateTime('${d} 00:00:00')".toString() : "TIMESTAMP '${d}'".toString() }
def selectAll = { String table, String alias ->
    if (!CH) return "${alias}.*".toString()
    dbSql.rows("SELECT name, type FROM system.columns WHERE database = ? AND table = ? ORDER BY position".toString(), [SRC, table]).collect {
        String c = "\"${it.name}\"", t = it.type, x = "${alias}.${c}"
        if (t ==~ /^(Nullable\()?DateTime\b.*/) return "formatDateTime(${x}, '%Y-%m-%dT%H:%i:%S') AS ${c}".toString()
        if (t ==~ /^(Nullable\()?Date(32)?\b.*/) return "toString(${x}) AS ${c}".toString()
        "${x} AS ${c}".toString()
    }.join(', ')
}
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
DateTimeFormatter CH_TS = DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss')
def chValue = { v ->
    if (v instanceof Timestamp) return (v as Timestamp).toLocalDateTime().format(CH_TS)
    if (v instanceof LocalDateTime) return (v as LocalDateTime).format(CH_TS)
    if (v instanceof java.sql.Date || v instanceof LocalDate) return v.toString()
    if (v instanceof GString) return v.toString()
    return v
}

// ── 0. the source must be there, and be what it says it is ─────────────────────────────────────
List srcInfo
try { srcInfo = dbSql.rows("SELECT \"Dataset\", \"Scale\", \"TableName\" FROM ${S('_dataset_info')}".toString()) }
catch (Exception e) { throw new IllegalStateException("Install Northwind Company scale ${SCALE} first (schema ${SRC} not found: ${e.message}).") }
if (srcInfo.isEmpty() || srcInfo[0].Scale != SCALE || srcInfo[0].Dataset != 'northwind_co')
    throw new IllegalStateException("${SRC} is not Northwind Company scale ${SCALE}.")
if (!srcInfo.any { it.TableName == 'OrderStatusHistory' })
    throw new IllegalStateException("${SRC} was installed by an older academy-northwind-co-install (no OrderStatusHistory). Reinstall it.")
log.info("=== Change log {} from {} on {}: {} .. {} ===", DST, SRC, vendor, FIRST, LAST)

// ── helpers ──────────────────────────────────────────────────────────────────────────────────
// One random stream per concern, seeded by name: a change to one rule moves only the rows it is about.
// The |1| is a fixed part of every seed: every row, checksum and quoted figure is built from it, so it never changes.
def rnd = { String name -> new Random(("${DATASET}|1|${SCALE}|${name}".toString()).hashCode() * 2654435761L) }
def pick = { Random r, List xs -> xs[r.nextInt(xs.size())] }
def money = { double v -> new BigDecimal(v).setScale(2, RoundingMode.HALF_UP) }
def weighted = { Random r, List<Double> w ->
    double t = w.sum() as double, x = r.nextDouble() * t, acc = 0
    for (int i = 0; i < w.size(); i++) { acc += w[i]; if (x < acc) return i }
    w.size() - 1
}
DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
// Every value as it goes into the JSON: timestamps and dates as text, numbers without trailing zeros.
def jv
jv = { Object v ->
    if (v == null) return null
    if (v instanceof Timestamp) return ((Timestamp) v).toLocalDateTime().format(ISO)
    if (v instanceof LocalDateTime) return ((LocalDateTime) v).format(ISO)
    if (v instanceof java.sql.Date) return ((java.sql.Date) v).toLocalDate().toString()
    if (v instanceof LocalDate) return v.toString()
    if (v instanceof BigDecimal) return new BigDecimal(((BigDecimal) v).stripTrailingZeros().toPlainString())
    if (v instanceof Short || v instanceof Byte) return ((Number) v).intValue()
    if (v instanceof Map) { Map m = new LinkedHashMap(); ((Map) v).each { k, x -> m[k.toString()] = jv(x) }; return m }
    v
}
def rowOf = { Map r -> Map m = new LinkedHashMap(); r.each { k, v -> m[k.toString()] = jv(v) }; m }
ObjectMapper JSON = new ObjectMapper()
JSON.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, false)
def at = { LocalDate d, int h, int m -> d.atTime(h, m) }
def dayNo = { LocalDateTime t -> (int) ChronoUnit.DAYS.between(DAY_ZERO, t.toLocalDate()) }
def ldt = { Object v -> v == null ? null : v instanceof Timestamp ? ((Timestamp) v).toLocalDateTime() : v instanceof CharSequence ? LocalDateTime.parse(v.toString().replace(' ', 'T')) : v as LocalDateTime }
def day = { Object v -> v == null ? null : v instanceof java.sql.Date ? ((java.sql.Date) v).toLocalDate() : v instanceof CharSequence ? LocalDate.parse(v.toString().substring(0, 10)) : v as LocalDate }

// ── 1. what the source looks like on 2024-12-31 ─────────────────────────────────────────────
int nextOrderId = (count("SELECT MAX(\"OrderID\") FROM ${S('Orders')}") as int) + 1
int nextInvoiceId = (count("SELECT MAX(\"InvoiceID\") FROM ${S('Invoices')}") as int) + 1
int nextStatusId = (count("SELECT MAX(\"StatusChangeID\") FROM ${S('OrderStatusHistory')}") as int) + 1
int nextWebId = (count("SELECT MAX(\"WebOrderID\") FROM ${S('WebOrders')}") as int) + 1
int nextCustChangeId = (count("SELECT MAX(\"CustomerChangeID\") FROM ${S('CustomerChanges')}") as int) + 1
int nextPriceChangeId = (count("SELECT MAX(\"PriceChangeID\") FROM ${S('PriceChanges')}") as int) + 1

// RATES, from the source: the same quarter a year earlier plus the source's 10% a year; changes and joiners per day
// averaged over the years the source has them.
double ORDERS_PER_DAY = (count("SELECT COUNT(*) FROM ${S('Orders')} WHERE \"OrderDate\" >= ${ts('2024-01-01')} AND \"OrderDate\" < ${ts('2024-04-01')}") as double) / 91 * 1.1
def perDay = { String table ->
    Map r = CH ? dbSql.firstRow("SELECT COUNT(*) AS n, toString(MIN(\"ChangedDate\")) AS lo, toString(MAX(\"ChangedDate\")) AS hi FROM ${S(table)}".toString())
               : dbSql.firstRow("SELECT COUNT(*) AS n, MIN(\"ChangedDate\") AS lo, MAX(\"ChangedDate\") AS hi FROM ${S(table)}".toString())
    (r.n as double) / Math.max(1, ChronoUnit.DAYS.between(day(r.lo), day(r.hi)))
}
double CUSTOMER_CHANGES_PER_DAY = perDay('CustomerChanges'), PRICE_CHANGES_PER_DAY = perDay('PriceChanges')
double JOINERS_PER_DAY = (count("SELECT COUNT(*) FROM ${S('Customers')} WHERE \"CreatedAt\" >= ${ts('2020-01-01')}") as double) / 1827
int N_CUSTOMER_CHANGES = Math.max(4, Math.round(CUSTOMER_CHANGES_PER_DAY * DAYS) as int)
int N_PRICE_CHANGES = Math.max(4, Math.round(PRICE_CHANGES_PER_DAY * DAYS) as int)
int N_JOINERS = Math.max(5, Math.round(JOINERS_PER_DAY * DAYS) as int)
int N_FACT_FIRST = Math.max(2, N_JOINERS.intdiv(5))                            // first order before the customer record
log.info("  rates from the source: {} orders a day, {} customer changes, {} price changes, {} new customers in {} days",
         String.format('%.1f', ORDERS_PER_DAY), N_CUSTOMER_CHANGES, N_PRICE_CHANGES, N_JOINERS, DAYS)

// Customers, as rows, and who buys: everyone who ordered in the last half of 2024, as often as they did in 2024,
// mostly from the rep who served them most.
Map<String, Map> customers = new LinkedHashMap<>()
dbSql.rows("SELECT ${selectAll('Customers', 'x')} FROM ${S('Customers')} x ORDER BY x.\"CustomerID\"${CH_READ}".toString()).each { customers[it.CustomerID as String] = rowOf(it) }
Map<String, Map<Integer, Integer>> repCounts = [:]
Map<String, Integer> orders2024 = [:]
dbSql.rows("""SELECT "CustomerID", "EmployeeID", COUNT(*) AS n, MAX("OrderDate") AS last FROM ${S('Orders')}
              WHERE "OrderDate" >= ${ts('2024-01-01')} GROUP BY "CustomerID", "EmployeeID" ORDER BY 1, 2""".toString()).each {
    String c = it.CustomerID
    repCounts.computeIfAbsent(c) { [:] }[it.EmployeeID as int] = it.n as int
    orders2024[c] = (orders2024[c] ?: 0) + (it.n as int)
}
Set<String> activeH2 = dbSql.rows("SELECT DISTINCT \"CustomerID\" FROM ${S('Orders')} WHERE \"OrderDate\" >= ${ts('2024-07-01')} ORDER BY 1".toString())
                            .collect { it.CustomerID as String } as Set
List<String> buyers = customers.keySet().findAll { it in activeH2 }.toList()
Map<String, Integer> repOf = buyers.collectEntries { c -> [c, repCounts[c].max { a, b -> a.value <=> b.value ?: b.key <=> a.key }.key] }
List<Integer> reps = repCounts.values().collectMany { it.keySet() }.unique().sort()
Map<String, String> officeIp = [:]
dbSql.rows("""SELECT o."CustomerID" AS "CustomerID", w."ClientIP" AS "ClientIP", COUNT(*) AS n FROM ${S('WebOrders')} w JOIN ${S('Orders')} o ON o."OrderID" = w."OrderID"
              GROUP BY 1, 2 ORDER BY 1, 3 DESC, 2""".toString()).each { if (!officeIp.containsKey(it.CustomerID)) officeIp[it.CustomerID as String] = it.ClientIP as String }
// Cities, regions and the shapes of names, addresses and phone numbers — new customers are made from these.
Map<String, String> regionOf = [:]
dbSql.rows("SELECT DISTINCT TRIM(\"ShipCity\") AS c, \"ShipRegion\" AS r FROM ${S('Orders')} ORDER BY 1, 2".toString()).each { if (!regionOf.containsKey(it.c)) regionOf[it.c as String] = it.r as String }
Map<String, List<String>> citiesOf = [:].withDefault { [] }
customers.values().each { Map c -> String city = (c.City as String).trim(); if (city.toLowerCase() != city && city.toUpperCase() != city && !(city in citiesOf[c.Country])) citiesOf[c.Country as String] << city }
citiesOf.values().each { it.sort() }

// Products that are still sold, how popular each was in 2024, and their price now.
Map<Integer, Map> products = new LinkedHashMap<>()
dbSql.rows("SELECT ${selectAll('Products', 'x')} FROM ${S('Products')} x ORDER BY x.\"ProductID\"${CH_READ}".toString()).each { products[it.ProductID as int] = rowOf(it) }
Map<Integer, Integer> popularity = [:]
dbSql.rows("""SELECT d."ProductID" AS "ProductID", COUNT(*) AS n FROM ${S('Order Details')} d JOIN ${S('Orders')} o ON o."OrderID" = d."OrderID"
              WHERE o."OrderDate" >= ${ts('2024-01-01')} GROUP BY 1 ORDER BY 1""".toString()).each { popularity[it.ProductID as int] = it.n as int }
List<Integer> onSale = products.values().findAll { !(it.Discontinued as boolean) && popularity[it.ProductID as int] }.collect { it.ProductID as int }

// Still open on 2024-12-31 (placed in December; older 'Open' orders are the phantom sales of Learn SQL S2 · 60 and stay
// as they are), and invoices not yet paid.
List<Map> carryOrders = dbSql.rows("SELECT ${selectAll('Orders', 'x')} FROM ${S('Orders')} x WHERE x.\"Status\" = 'Open' AND x.\"OrderDate\" >= ${ts('2024-12-01')} ORDER BY x.\"OrderID\"${CH_READ}".toString()).collect { rowOf(it) }
Map<Integer, List<Map>> carryLines = [:].withDefault { [] }
dbSql.rows("""SELECT ${selectAll('Order Details', 'd')} FROM ${S('Order Details')} d JOIN ${S('Orders')} o ON o."OrderID" = d."OrderID"
              WHERE o."Status" = 'Open' AND o."OrderDate" >= ${ts('2024-12-01')} ORDER BY d."OrderID", d."ProductID"${CH_READ}""".toString()).each { carryLines[it.OrderID as int] << rowOf(it) }
List<Map> unpaid = dbSql.rows("SELECT ${selectAll('Invoices', 'x')} FROM ${S('Invoices')} x WHERE x.\"PaidDate\" IS NULL ORDER BY x.\"InvoiceID\"${CH_READ}".toString()).collect { rowOf(it) }
log.info("  source on 2024-12-31: {} customers ({} buying), {} products on sale, {} open orders to ship, {} invoices to be paid",
         customers.size(), buyers.size(), onSale.size(), carryOrders.size(), unpaid.size())

// ── 2. transactions: what changed in the source, and when ─────────────────────────────────────
// A transaction is one commit: its events share "tx" and arrive together. Built in any order, sorted by time later.
List<Map> txs = []
List<List> plants = []                                  // [plant, lesson, detail, locator Closure → [day, ref]]
// Every row handed to an event is final: whatever changes a row later changes a copy of it.
def ev = { String table, String op, Map key, Map before, Map after ->
    new Event(table, op, JSON.writeValueAsString(jv(key)),
              ',"before":' + JSON.writeValueAsString(jv(before)) + ',"after":' + JSON.writeValueAsString(jv(after)) + '}')
}
def addTx = { LocalDateTime t, String kind, List<Map> events, Map extra = [:] ->
    Map tx = [time: t, n: txs.size(), kind: kind, events: events] + extra
    txs << tx
    tx
}
def tsv = { LocalDateTime t -> t.format(ISO) }

// ── 2a. customers: changes and joiners, in time order, so each change starts from the value before it ──
Random rCC = rnd('CustomerChanges'), rJ = rnd('Customers.New'), rPC = rnd('PriceChanges')
// What a customer looked like at a moment: [from, row copy] steps, first the 2024-12-31 row.
Map<String, List<List>> custHistory = [:]
customers.each { id, row -> custHistory[id] = [[LocalDateTime.of(2000, 1, 1, 0, 0), new LinkedHashMap(row)]] }
def custAt = { String id, LocalDateTime t ->
    Map v = null
    for (List step : custHistory[id]) { if (!(step[0] as LocalDateTime).isAfter(t)) v = step[1] as Map else break }
    v
}
List<List> dimChanges = []                                                 // [time, kind, payload]
N_CUSTOMER_CHANGES.times {
    LocalDate d = FIRST.plusDays(rCC.nextInt(DAYS))
    LocalDateTime t = at(d, 8 + rCC.nextInt(10), rCC.nextInt(60))
    String who = pick(rCC, buyers)
    String attr = pick(rCC, ['Segment', 'Segment', 'City', 'ContactName'])
    dimChanges << [t, 'customer', [id: who, attr: attr, r1: rCC.nextInt(1 << 20), r2: rCC.nextInt(1 << 20)]]
}
// PLANTED — ETL S4 · 25, Data Warehousing S3 · 10: the biggest buyer moves segment on 12 February; one of its orders
// keyed on the 11th reaches the log on the 13th, after the change.
String bigBuyer = buyers.max { a, b -> orders2024[a] <=> orders2024[b] ?: b <=> a }
LocalDateTime SEGMENT_CHANGE_AT = LocalDateTime.of(2025, 2, 12, 10, 5)
dimChanges << [SEGMENT_CHANGE_AT, 'customer', [id: bigBuyer, attr: 'Segment', r1: 1, r2: 0, planted: true]]
// Joiners: created on a working hour of a day in the first 80, their first order 1–10 days later; the planted ones
// have a web order keyed 20 minutes to 5 hours BEFORE their record (credit control opens web accounts later).
List<String> A = customers.values().collect { (it.CompanyName as String).split(' ')[0] }.unique().sort()
List<String> B = customers.values().collect { (it.CompanyName as String).split(' ').drop(1).join(' ') }.findAll { it }.unique().sort()
List<String> FIRST_NAMES = customers.values().collect { (it.ContactName as String).split(' ')[0].capitalize() }.unique().sort()
List<String> LAST_NAMES = customers.values().collect { (it.ContactName as String).split(' ').drop(1).join(' ').capitalize() }.findAll { it }.unique().sort()
List<String> TITLES = customers.values().collect { it.ContactTitle as String }.findAll { it }.unique().sort()
List<String> STREETS = customers.values().collect { (it.Address as String).replaceFirst(/^\d+ /, '') }.unique().sort()
Set<String> names = customers.values().collect { it.CompanyName as String } as Set
Set<String> ids = new HashSet<>(customers.keySet())
def reshape = { Random r, String like -> like == null ? null : like.collect { Character.isDigit(it as char) ? String.valueOf(r.nextInt(10)) : it }.join() }
List<Map> joiners = []
N_JOINERS.times { int j ->
    LocalDate d = FIRST.plusDays(rJ.nextInt(80))
    LocalDateTime created = at(d, 8 + rJ.nextInt(9), rJ.nextInt(60))
    String name = null
    for (int tries = 0; tries < 200 && name == null; tries++) { String n = "${pick(rJ, A)} ${pick(rJ, B)}".toString(); if (n.length() <= 40 && names.add(n)) name = n }
    if (name == null) { name = "${pick(rJ, A)} ${pick(rJ, B)} ${j + 2}".toString(); names.add(name) }
    String base = name.replaceAll('[^A-Za-z]', '').toUpperCase().padRight(5, 'X').substring(0, 5), id = base
    for (int k = 1; !ids.add(id); k++) id = base.substring(0, 4) + k
    Map like = customers.values().toList()[rJ.nextInt(customers.size())]
    String country = like.Country, city = pick(rJ, citiesOf[country] ?: [(like.City as String).trim()])
    Map row = new LinkedHashMap()
    row.CustomerID = id; row.CompanyName = name; row.ContactName = "${pick(rJ, FIRST_NAMES)} ${pick(rJ, LAST_NAMES)}".toString()
    row.ContactTitle = pick(rJ, TITLES); row.Address = "${1 + rJ.nextInt(300)} ${pick(rJ, STREETS)}".toString()
    row.City = city; row.Region = regionOf[city]; row.PostalCode = reshape(rJ, like.PostalCode as String); row.Country = country
    row.Phone = reshape(rJ, like.Phone as String); row.Fax = null
    row.Email = "buying@${name.toLowerCase().replaceAll('[^a-z]', '')}.example".toString()
    row.Segment = pick(rJ, ['Retail', 'Retail', 'Restaurant', 'Wholesale'])
    int lead = 1 + rJ.nextInt(10), leadMinutes = 20 + rJ.nextInt(280), ip = rJ.nextInt(254) + 1
    boolean factFirst = j < N_FACT_FIRST, never = j == N_JOINERS - 1
    LocalDateTime firstOrder = factFirst ? created.minusMinutes(leadMinutes) : at(d.plusDays(lead), 8 + rJ.nextInt(10), rJ.nextInt(60))
    if (firstOrder.hour < 8) firstOrder = firstOrder.withHour(8)
    if (factFirst && !firstOrder.isBefore(created)) created = firstOrder.plusMinutes(leadMinutes)
    row.CreatedAt = tsv(created); row.UpdatedAt = tsv(created)
    joiners << [id: id, row: row, created: created, firstOrder: firstOrder, factFirst: factFirst, never: never, rep: pick(rJ, reps),
                ip: "203.0.113.${ip}".toString()]
    dimChanges << [created, 'joiner', [id: id]]
}
Map<String, Map> joinerById = joiners.collectEntries { [it.id, it] }
N_PRICE_CHANGES.times {
    LocalDate d = FIRST.plusDays(rPC.nextInt(DAYS))
    dimChanges << [at(d, 8 + rPC.nextInt(10), rPC.nextInt(60)), 'price', [id: pick(rPC, onSale), up: 0.02 + rPC.nextDouble() * 0.10]]
}
// PLANTED — ETL S4 · 25, Data Warehousing S3 · 10: the most popular product's price goes up on 19 February; an order
// for it keyed on the 18th arrives on the 20th.
int hotProduct = onSale.max { a, b -> popularity[a] <=> popularity[b] ?: b <=> a }
LocalDateTime PRICE_CHANGE_AT = LocalDateTime.of(2025, 2, 19, 9, 10)
dimChanges << [PRICE_CHANGE_AT, 'price', [id: hotProduct, up: 0.08, planted: true]]
dimChanges.sort { a, b -> (a[0] as LocalDateTime) <=> (b[0] as LocalDateTime) ?: a[1] <=> b[1] }

Map<Integer, List<List>> priceHistory = [:]
products.each { id, row -> priceHistory[id] = [[LocalDateTime.of(2000, 1, 1, 0, 0), row.UnitPrice as BigDecimal]] }
def priceAt = { int id, LocalDateTime t ->
    BigDecimal v = null
    for (List step : priceHistory[id]) { if (!(step[0] as LocalDateTime).isAfter(t)) v = step[1] as BigDecimal else break }
    v
}
Map missingCustomer = joiners.find { it.never }
dimChanges.each { List c ->
    LocalDateTime t = c[0]
    Map p = c[2]
    if (c[1] == 'joiner') {
        Map j = joinerById[p.id]
        customers[j.id] = j.row
        custHistory[j.id] = [[t, new LinkedHashMap(j.row)]]
        j.tx = addTx(t, 'customer-new', [ev('Customers', 'c', [CustomerID: j.id], null, new LinkedHashMap(j.row))], [drop: j.never])
    } else if (c[1] == 'customer') {
        Map now = customers[p.id], before = new LinkedHashMap(now), after = new LinkedHashMap(now)
        String attr = p.attr, oldV = now[attr]
        String newV
        if (attr == 'Segment') { List<String> other = ['Retail', 'Restaurant', 'Wholesale'].findAll { it != oldV }; newV = other[(p.r1 as int) % other.size()] }
        else if (attr == 'City') { List<String> other = (citiesOf[now.Country] ?: []).findAll { it != oldV?.trim() }; newV = other ? other[(p.r1 as int) % other.size()] : oldV }
        else newV = "${FIRST_NAMES[(p.r1 as int) % FIRST_NAMES.size()]} ${LAST_NAMES[(p.r2 as int) % LAST_NAMES.size()]}".toString()
        if (newV == oldV) return
        after[attr] = newV
        if (attr == 'City') after.Region = regionOf[newV]
        after.UpdatedAt = tsv(t)
        customers[p.id] = after
        custHistory[p.id] << [t, new LinkedHashMap(after)]
        Map tx = addTx(t, 'customer-change', [ev('Customers', 'u', [CustomerID: p.id], before, new LinkedHashMap(after)),
                                             ev('CustomerChanges', 'c', [CustomerChangeID: nextCustChangeId],
                                                null, [CustomerChangeID: nextCustChangeId, CustomerID: p.id, ChangedDate: t.toLocalDate().toString(),
                                                       Attribute: attr, OldValue: oldV, NewValue: newV])])
        nextCustChangeId++
        if (p.planted) plants << ['as-of-segment', 'ETL S4 · 25, Data Warehousing S3 · 10',
                                  "${p.id} (the biggest buyer) moves from ${oldV} to ${newV}; an order keyed the day before arrives after it".toString(), tx]
    } else {
        Map now = products[p.id], before = new LinkedHashMap(now), after = new LinkedHashMap(now)
        BigDecimal oldP = now.UnitPrice as BigDecimal, newP = new BigDecimal((oldP as double) * (1 + (p.up as double))).setScale(2, RoundingMode.HALF_UP)
        after.UnitPrice = jv(newP); after.UpdatedAt = tsv(t)
        products[p.id] = after
        priceHistory[p.id] << [t, newP]
        Map tx = addTx(t, 'price-change', [ev('Products', 'u', [ProductID: p.id], before, new LinkedHashMap(after)),
                                          ev('PriceChanges', 'c', [PriceChangeID: nextPriceChangeId], null,
                                             [PriceChangeID: nextPriceChangeId, ProductID: p.id, ChangedDate: t.toLocalDate().toString(), OldPrice: jv(oldP), NewPrice: jv(newP)])])
        nextPriceChangeId++
        if (p.planted) plants << ['as-of-price', 'ETL S4 · 25, Data Warehousing S3 · 10',
                                  "product ${p.id} goes from ${jv(oldP)} to ${jv(newP)}; an order for it keyed the day before arrives after it".toString(), tx]
    }
}

// ── 2b. orders: planned first, numbered in the order they were keyed ─────────────────────────────
Random rO = rnd('Orders'), rL = rnd('Order Details'), rCreated = rnd('Orders.CreatedAt'), rChannel = rnd('Orders.Channel'),
       rDN = rnd('Orders.DeliveryNotes'), rW = rnd('WebOrders'), rS = rnd('Orders.Fate'), rSess = rnd('Orders.Sessions')
double QTY_BUDGET = 1100
Cumulative buyerWeights = new Cumulative(buyers.collect { (orders2024[it] ?: 1) as double })
List<String> NOTES = ['Deliver to the back entrance.', 'Loading bay closes at 15:00.', 'Leave with reception if nobody answers.',
                      'Pallets only, no loose boxes.', 'Cold chain: straight to the chiller, please.', 'Invoice to head office, goods to the shop.',
                      'Please call ahead: {contact}, {phone}.', 'Ask for {first} at goods-in.', 'Phone {phone} on arrival.',
                      'If late, tell {contact} before noon.']
List<Map> plans = []
Cumulative productWeights = new Cumulative(onSale.collect { popularity[it] })
// Every roll is drawn for every order, before any decision uses it (the source's rule).
def plan = { LocalDate orderDate, Map force = [:] ->
    int h = 8 + rCreated.nextInt(10), m = rCreated.nextInt(60)
    double keyedLateRoll = rCreated.nextDouble()
    int keyedLateDays = 1 + rCreated.nextInt(10)
    int who = buyerWeights.find(rO.nextDouble() * buyerWeights.total())
    int shipVia = weighted(rO, [0.75d, 0.12d, 0.08d, 0.05d]) + 1
    double repRoll = rO.nextDouble()
    int otherRep = rO.nextInt(reps.size())
    double freightRate = 0.02 + rO.nextDouble() * 0.03
    int nLines = weighted(rL, [0.26d, 0.32d, 0.20d, 0.12d, 0.07d, 0.03d]) + 1
    List<List> picks = (1..6).collect { [rL.nextInt(1 << 30), rL.nextInt(1 << 30), pick(rL, ['0', '0', '0', '0.05', '0.1', '0.15'])] }
    int shipDays = 2 + rS.nextInt(6), lateDays = 15 + rS.nextInt(10), shipH = 9 + rS.nextInt(9), shipM = rS.nextInt(60), payDays = 10 + rS.nextInt(40)
    double lateRoll = rS.nextDouble(), cancelRoll = rS.nextDouble()
    int cancelH = 10 + rS.nextInt(7), cancelM = rS.nextInt(60)
    double channelRoll = rChannel.nextDouble(), noteRoll = rDN.nextDouble()
    int noteKind = rDN.nextInt(NOTES.size())
    List<Integer> webRolls = (1..14).collect { rW.nextInt(25) }
    double ipRoll = rW.nextDouble()
    int ipHost = rW.nextInt(254) + 1
    double sessionRoll = rSess.nextDouble()
    int sessionGap = 3 + rSess.nextInt(23)

    LocalDateTime created = at(orderDate, h, m)
    if (keyedLateRoll < 0.015) created = created.plusDays(keyedLateDays)
    Map p = [orderDate: orderDate, created: created, customer: buyers[who], shipVia: shipVia, repRoll: repRoll, otherRep: reps[otherRep],
             freightRate: freightRate, nLines: nLines, picks: picks, shipDays: shipDays, late: lateRoll < 0.02, lateDays: lateDays,
             shipH: shipH, shipM: shipM, payDays: payDays, cancel: cancelRoll < 0.015, cancelH: cancelH, cancelM: cancelM,
             channel: channelRoll < 0.50 ? 'Web' : channelRoll < 0.60 ? 'EDI' : channelRoll < 0.85 ? 'Phone' : 'Sales rep',
             note: noteRoll < 0.05 ? noteKind : -1, webRolls: webRolls, ipRoll: ipRoll, ipHost: ipHost,
             session: sessionRoll < 0.03, sessionGap: sessionGap] + force
    plans << p
    p
}
(1..DAYS).each { int dn ->
    LocalDate d = DAY_ZERO.plusDays(dn)
    int n = (int) Math.round(ORDERS_PER_DAY * (0.75 + 0.5 * rO.nextDouble()))
    n.times { plan(d) }
}
// Sessions (ETL S4 · 00): now and then a web customer comes back minutes later for something forgotten.
plans.findAll { it.session && it.channel == 'Web' && !(it.created as LocalDateTime).isAfter(at(LAST, 17, 0)) }.each { Map p ->
    plan(p.orderDate as LocalDate, [created: (p.created as LocalDateTime).plusMinutes(p.sessionGap as int), customer: p.customer, channel: 'Web',
                                    session: false, cancel: false, note: -1, followUp: true])
}
// Each joiner's first order, and one or two more; the planted ones are web orders keyed before the customer record.
joiners.each { Map j ->
    plan((j.firstOrder as LocalDateTime).toLocalDate(), [created: j.firstOrder, customer: j.id, channel: j.factFirst ? 'Web' : 'Phone',
                                                         cancel: false, rep: j.rep, joinerFirst: true,
                                                         arriveAt: j.factFirst ? (j.firstOrder as LocalDateTime).plusSeconds(14) : null])
    plan((j.firstOrder as LocalDateTime).toLocalDate().plusDays(9), [customer: j.id, rep: j.rep])
}
// PLANTED orders with a fixed role.
Map segmentOrder = plan(SEGMENT_CHANGE_AT.toLocalDate().minusDays(1), [created: SEGMENT_CHANGE_AT.minusDays(1).withHour(15).withMinute(40),
                        customer: bigBuyer, cancel: false, arriveAt: LocalDateTime.of(2025, 2, 13, 9, 12, 4), role: 'as-of-segment'])
Map priceOrder = plan(PRICE_CHANGE_AT.toLocalDate().minusDays(1), [created: PRICE_CHANGE_AT.minusDays(1).withHour(16).withMinute(20),
                      mustHave: hotProduct, cancel: false, arriveAt: LocalDateTime.of(2025, 2, 20, 8, 47, 31), role: 'as-of-price'])
Map monthEnd1 = plan(LocalDate.of(2025, 1, 31), [created: LocalDateTime.of(2025, 2, 1, 9, 5), role: 'month-boundary'])
Map monthEnd2 = plan(LocalDate.of(2025, 2, 28), [created: LocalDateTime.of(2025, 3, 3, 8, 50), role: 'month-boundary'])
Map errorOrder = plan(DAY_ZERO.plusDays(33), [created: at(DAY_ZERO.plusDays(33), 10, 15), channel: 'Phone', cancel: false, role: 'keyed-in-error'])
Map negativeOrder = plan(DAY_ZERO.plusDays(BAD_DAY), [created: at(DAY_ZERO.plusDays(BAD_DAY), 10, 42), nLines: 3, channel: 'EDI',
                         cancel: true, cancelH: 9, cancelM: 30, role: 'bad-negative-quantity'])
String ghost = null
for (String c : buyers) {                   // a CustomerID one keystroke away from a real one, that nobody has
    String g = c.substring(0, 4) + (Character.isDigit(c.charAt(4)) ? 'X' : String.valueOf(((c.charAt(4) as int) - 65 + 1) % 26 + 65 as char))
    if (!ids.contains(g)) { ghost = g; break }
}
Map ghostOrder = plan(DAY_ZERO.plusDays(BAD_DAY), [created: at(DAY_ZERO.plusDays(BAD_DAY), 13, 5), customerId: ghost, channel: 'EDI', cancel: false,
                      neverShips: true, role: 'bad-unknown-customer'])
// Watermark ties: two orders shipped at 17:59 on the same day; the second one's update is delivered after midnight.
List<Map> tiePairs = TIE_DAYS.collect { int dn ->
    LocalDate shipOn = DAY_ZERO.plusDays(dn)
    [plan(shipOn.minusDays(4), [created: at(shipOn.minusDays(4), 9, 30), cancel: false, late: false, shipDays: 4, shipH: 17, shipM: 59, role: 'tie-first',
                                shipArriveAt: at(shipOn, 17, 59).plusSeconds(9)]),
     plan(shipOn.minusDays(4), [created: at(shipOn.minusDays(4), 11, 10), cancel: false, late: false, shipDays: 4, shipH: 17, shipM: 59, role: 'tie-second',
                                shipArriveAt: at(shipOn.plusDays(1), 0, 4).plusSeconds(17)])]
}
plans.sort { a, b -> (a.created as LocalDateTime) <=> (b.created as LocalDateTime) }
plans = plans.findAll { !(it.created as LocalDateTime).isAfter(at(LAST, 23, 59)) }    // keyed after 31 March: not in this log

// ── 2c. each order's life: keyed, maybe cancelled, shipped, paid ───────────────────────────────
// Which ordinary orders carry the delete, the reinstatement and the hand-typed status: the first that fits, on the day.
def firstFitting = { int dn, Closure ok -> plans.find { it.role == null && dayNo(it.created as LocalDateTime) >= dn && ok(it) } }
def shipDateOf = { Map p -> LocalDate s = (p.orderDate as LocalDate).plusDays(p.late ? p.lateDays as int : p.shipDays as int)
                            LocalDate c = (p.created as LocalDateTime).toLocalDate(); s.isAfter(c) ? s : c.plusDays(1) }
Map lineDeleteOrder = firstFitting(20) { Map p -> !p.cancel && p.nLines >= 3 && ChronoUnit.DAYS.between((p.created as LocalDateTime).toLocalDate(), shipDateOf(p)) >= 3 }
lineDeleteOrder.role = 'line-deleted'
Map reinstateOrder = firstFitting(25) { Map p -> !p.cancel && ChronoUnit.DAYS.between((p.created as LocalDateTime).toLocalDate(), shipDateOf(p)) >= 4 }
reinstateOrder.role = 'reinstated'
Map typedStatusOrder = plans.find { it.role == null && !it.cancel && shipDateOf(it) == DAY_ZERO.plusDays(BAD_DAY) }
typedStatusOrder.role = 'bad-typed-status'
Map dupOfOrder = null                                     // day 45: the order whose OrderID is sent again

LocalDateTime END = at(LAST, 23, 59)
Map<String, Integer> made = [orders: 0, lines: 0, shipped: 0, cancelled: 0, invoices: 0, paid: 0, web: 0]
Map<LocalDate, List> control = [:].withDefault { [0, 0, BigDecimal.ZERO] }     // orders, lines, amount — by the day keyed
plans.each { Map p ->
    LocalDateTime created = p.created
    int orderId = nextOrderId++
    p.orderId = orderId
    String cid = p.customerId ?: p.customer
    Map cu = customers[cid] ? custAt(cid, created) ?: customers[cid] : customers[p.customer]
    if (cu == null) cu = customers[p.customer]
    // Lines: products by 2024 popularity, at the price of the moment the order was keyed.
    Set<Integer> used = new LinkedHashSet<>()
    if (p.mustHave) used << (p.mustHave as int)
    int li = 0
    while (used.size() < (p.nLines as int)) {
        long x = ((p.picks as List)[li % 6][0] as long) * (li + 1) % productWeights.total().longValue()
        int chosen = onSale[productWeights.find(x as double)]
        if (!used.contains(chosen)) used << chosen else used << onSale[((p.picks as List)[li % 6][1] as int) % onSale.size()]
        li++
    }
    List<Map> lines = []
    BigDecimal goods = BigDecimal.ZERO
    used.eachWithIndex { int pid, int i ->
        BigDecimal price = priceAt(pid, created)
        List pk = (p.picks as List)[i % 6]
        int qty = 4 + ((pk[1] as int) % Math.max(8, (int) (QTY_BUDGET / (price as double))))
        if (p.role == 'bad-negative-quantity' && i == 1) qty = -qty
        BigDecimal disc = new BigDecimal(pk[2] as String)
        lines << [OrderID: orderId, ProductID: pid, UnitPrice: jv(price), Quantity: qty, Discount: jv(disc)]
        goods += (price * qty * (BigDecimal.ONE - disc)).setScale(2, RoundingMode.HALF_UP)
    }
    BigDecimal freight = money((goods.abs() as double) * (p.freightRate as double))
    int employee = p.rep ?: (p.repRoll < 0.85 && repOf[cid] ? repOf[cid] : p.otherRep)
    String city = (cu.City as String)
    String note = null
    if ((p.note as int) >= 0) {
        String contact = cu.ContactName
        note = NOTES[p.note as int].replace('{contact}', contact).replace('{first}', contact.split(' ')[0]).replace('{phone}', cu.Phone as String)
    }
    Map order = new LinkedHashMap()
    order.OrderID = orderId; order.CustomerID = cid; order.EmployeeID = employee
    order.OrderDate = tsv((p.orderDate as LocalDate).atStartOfDay()); order.RequiredDate = tsv((p.orderDate as LocalDate).plusDays(14).atStartOfDay())
    order.ShippedDate = null; order.ShipVia = p.shipVia; order.Freight = jv(freight)
    order.ShipName = cu.CompanyName; order.ShipAddress = cu.Address; order.ShipCity = city; order.ShipRegion = regionOf[city.trim()]
    order.ShipPostalCode = cu.PostalCode; order.ShipCountry = cu.Country; order.Status = 'Open'
    order.UpdatedAt = tsv(created); order.CreatedAt = tsv(created); order.Channel = p.channel; order.DeliveryNotes = note

    List<Map> evs = [ev('Orders', 'c', [OrderID: orderId], null, new LinkedHashMap(order))]
    if (created.toLocalDate() == DAY_ZERO.plusDays(BAD_DAY - 1)) p.orderRow = new LinkedHashMap(order)     // day 45 copies one of these
    lines.each { l -> evs << ev('Order Details', 'c', [OrderID: orderId, ProductID: l.ProductID], null, l) }
    int openStatusId = nextStatusId++
    evs << ev('OrderStatusHistory', 'c', [StatusChangeID: openStatusId], null, [StatusChangeID: openStatusId, OrderID: orderId, Status: 'Open', ChangedAt: tsv(created)])
    // The web shop's raw JSON, with the source's defects (~1 in 25 each): quantity as text, price with a comma, no ship_to.
    if (p.channel == 'Web') {
        List<Integer> wr = p.webRolls
        List items = lines.withIndex().collect { Map l, int i ->
            def qty = wr[(2 * i) % 12] == 0 ? "\"${l.Quantity}\"" : "${l.Quantity}"
            String plain = new BigDecimal(l.UnitPrice.toString()).setScale(2).toPlainString()
            def price = wr[(2 * i + 1) % 12] == 0 ? "\"${plain.replace('.', ',')}\"" : plain
            "{\"sku\": \"P${String.format('%04d', l.ProductID)}\", \"qty\": ${qty}, \"unit_price\": ${price}, \"discount\": ${new BigDecimal(l.Discount.toString()).stripTrailingZeros().toPlainString()}}"
        }
        String shipTo = wr[12] == 0 ? '' : ", \"ship_to\": {\"city\": \"${city.trim()}\", \"country\": \"${cu.Country}\"}"
        String payload = "{\"order_ref\": \"WEB-${orderId}\", \"customer\": \"${cid}\", \"placed_at\": \"${created.format(ISO).substring(0, 16)}\"${shipTo}, \"items\": [${items.join(', ')}]}"
        String ip = p.ipRoll < 0.75 ? (officeIp[cid] ?: joinerById[cid]?.ip ?: "198.51.100.${p.ipHost}".toString()) : "192.0.2.${p.ipHost}".toString()
        int webId = nextWebId++
        evs << ev('WebOrders', 'c', [WebOrderID: webId], null, [WebOrderID: webId, OrderID: orderId, ReceivedAt: tsv(created), Payload: payload, ClientIP: ip])
        made.web++
    }
    Map createTx = addTx(created, 'order', evs, [arriveAt: p.arriveAt, orderId: orderId])
    made.orders++; made.lines += lines.size()
    if (p.role != 'keyed-in-error') {
        List c = control[created.toLocalDate()]
        c[0] = (c[0] as int) + 1; c[1] = (c[1] as int) + lines.size(); c[2] = (c[2] as BigDecimal) + goods
    }
    p.createTx = createTx

    // PLANTED — ETL S3 · 15: an order keyed in error, deleted 25 minutes later with its lines and status row.
    if (p.role == 'keyed-in-error') {
        List<Map> del = lines.collect { l -> ev('Order Details', 'd', [OrderID: orderId, ProductID: l.ProductID], l, null) }
        del << ev('OrderStatusHistory', 'd', [StatusChangeID: openStatusId], [StatusChangeID: openStatusId, OrderID: orderId, Status: 'Open', ChangedAt: tsv(created)], null)
        del << ev('Orders', 'd', [OrderID: orderId], new LinkedHashMap(order), null)
        p.deleteTx = addTx(created.plusMinutes(25), 'order-deleted', del)
        return
    }
    // PLANTED — ETL S3 · 15: a line removed the next morning. Order Details has no UpdatedAt and Orders is not touched.
    if (p.role == 'line-deleted') {
        Map gone = lines.remove(lines.size() - 1)
        goods -= (new BigDecimal(gone.UnitPrice.toString()) * (gone.Quantity as int) * (BigDecimal.ONE - new BigDecimal(gone.Discount.toString()))).setScale(2, RoundingMode.HALF_UP)
        p.deleteTx = addTx(at(created.toLocalDate().plusDays(1), 11, 2), 'line-deleted', [ev('Order Details', 'd', [OrderID: orderId, ProductID: gone.ProductID], gone, null)])
    }
    def update = { LocalDateTime t, Map changes, String status, String kind, Map extra = [:] ->
        Map before = new LinkedHashMap(order)
        changes.each { k, v -> order[k] = v }
        order.UpdatedAt = tsv(t)
        List<Map> e = [ev('Orders', 'u', [OrderID: orderId], before, new LinkedHashMap(order))]
        if (status) { int sid = nextStatusId++; e << ev('OrderStatusHistory', 'c', [StatusChangeID: sid], null, [StatusChangeID: sid, OrderID: orderId, Status: status.trim().capitalize(), ChangedAt: tsv(t)]) }
        addTx(t, kind, e, extra)
    }
    if (p.role == 'reinstated') {
        p.cancelTx = update(at(created.toLocalDate().plusDays(1), 10, 0), [Status: 'Cancelled'], 'Cancelled', 'order-cancelled')
        p.reopenTx = update(at(created.toLocalDate().plusDays(2), 11, 0), [Status: 'Open'], 'Open', 'order-reinstated')
    }
    if (p.cancel) {
        LocalDateTime t = at(created.toLocalDate().plusDays(1), p.cancelH as int, p.cancelM as int)
        if (!t.isAfter(END)) { update(t, [Status: 'Cancelled'], 'Cancelled', 'order-cancelled'); made.cancelled++ }
        return
    }
    if (p.neverShips) return
    LocalDate shipOn = shipDateOf(p)
    LocalDateTime shipAt = at(shipOn, p.shipH as int, p.shipM as int)
    if (shipAt.isAfter(END)) return
    String status = p.role == 'bad-typed-status' ? 'shipped ' : 'Shipped'
    Map shipTx = update(shipAt, [ShippedDate: tsv(shipOn.atStartOfDay()), Status: status], status, 'order-shipped', [arriveAt: p.shipArriveAt])
    int invoiceId = nextInvoiceId++
    Map invoice = [InvoiceID: invoiceId, OrderID: orderId, InvoiceDate: shipOn.toString(), Amount: jv(goods), Freight: jv(freight), PaidDate: null]
    shipTx.events << ev('Invoices', 'c', [InvoiceID: invoiceId], null, new LinkedHashMap(invoice))
    if (p.role) p.shipTx = shipTx
    made.shipped++; made.invoices++
    LocalDate paidOn = shipOn.plusDays(p.payDays as int)
    if (!paidOn.isAfter(LAST)) {
        Map before = new LinkedHashMap(invoice)
        invoice.PaidDate = paidOn.toString()
        addTx(at(paidOn, 9 + (orderId % 8), (orderId * 7) % 60), 'invoice-paid', [ev('Invoices', 'u', [InvoiceID: invoiceId], before, invoice)])
        made.paid++
    }
}
// Day 45: the same OrderID sent again, for another customer, with no lines — a replayed import from the EDI bridge.
LocalDateTime dupAt = at(DAY_ZERO.plusDays(BAD_DAY), 11, 20)
dupOfOrder = plans.findAll { it.role == null && (it.created as LocalDateTime).toLocalDate() == DAY_ZERO.plusDays(BAD_DAY - 1) }.last()
Map dupRow = new LinkedHashMap(dupOfOrder.orderRow as Map)
String otherBuyer = buyers.find { it != dupRow.CustomerID }
Map ob = customers[otherBuyer]
dupRow.CustomerID = otherBuyer; dupRow.ShipName = ob.CompanyName; dupRow.ShipAddress = ob.Address; dupRow.ShipCity = ob.City
dupRow.ShipRegion = regionOf[(ob.City as String).trim()]; dupRow.ShipPostalCode = ob.PostalCode; dupRow.ShipCountry = ob.Country
dupRow.Channel = 'EDI'; dupRow.DeliveryNotes = null; dupRow.CreatedAt = tsv(dupAt); dupRow.UpdatedAt = tsv(dupAt)
Map dupTx = addTx(dupAt, 'bad-duplicate-order-id', [ev('Orders', 'c', [OrderID: dupRow.OrderID], null, dupRow)])

// The source's open orders of December ship in January (1.5% are cancelled); its unpaid invoices get paid.
carryOrders.each { Map o ->
    int shipDays = 2 + rS.nextInt(6), h = 9 + rS.nextInt(9), m = rS.nextInt(60), payDays = 10 + rS.nextInt(40)
    double cancelRoll = rS.nextDouble()
    LocalDate od = LocalDate.parse((o.OrderDate as String).substring(0, 10))
    LocalDate shipOn = od.plusDays(shipDays)
    // The holiday backlog clears over the first working week.
    if (shipOn.isBefore(FIRST.plusDays(1))) shipOn = FIRST.plusDays(1 + (o.OrderID as int) % 5)
    LocalDateTime t = at(shipOn, h, m)
    Map before = new LinkedHashMap(o), after = new LinkedHashMap(o)
    after.UpdatedAt = tsv(t)
    if (cancelRoll < 0.015) {
        after.Status = 'Cancelled'
        int sid = nextStatusId++
        addTx(t, 'order-cancelled', [ev('Orders', 'u', [OrderID: o.OrderID], before, after),
                                     ev('OrderStatusHistory', 'c', [StatusChangeID: sid], null, [StatusChangeID: sid, OrderID: o.OrderID, Status: 'Cancelled', ChangedAt: tsv(t)])])
        made.cancelled++
        return
    }
    after.Status = 'Shipped'; after.ShippedDate = tsv(shipOn.atStartOfDay())
    BigDecimal goods = carryLines[o.OrderID as int].sum(BigDecimal.ZERO) { l -> (new BigDecimal(l.UnitPrice.toString()) * (l.Quantity as int) * (BigDecimal.ONE - new BigDecimal(l.Discount.toString()))).setScale(2, RoundingMode.HALF_UP) } as BigDecimal
    int sid = nextStatusId++, invoiceId = nextInvoiceId++
    Map invoice = [InvoiceID: invoiceId, OrderID: o.OrderID, InvoiceDate: shipOn.toString(), Amount: jv(goods), Freight: jv(new BigDecimal(o.Freight.toString()).setScale(2, RoundingMode.HALF_UP)), PaidDate: null]
    addTx(t, 'order-shipped', [ev('Orders', 'u', [OrderID: o.OrderID], before, after),
                               ev('OrderStatusHistory', 'c', [StatusChangeID: sid], null, [StatusChangeID: sid, OrderID: o.OrderID, Status: 'Shipped', ChangedAt: tsv(t)]),
                               ev('Invoices', 'c', [InvoiceID: invoiceId], null, new LinkedHashMap(invoice))])
    made.shipped++; made.invoices++
    LocalDate paidOn = shipOn.plusDays(payDays)
    if (!paidOn.isAfter(LAST)) {
        Map b = new LinkedHashMap(invoice); invoice.PaidDate = paidOn.toString()
        addTx(at(paidOn, 9 + ((o.OrderID as int) % 8), ((o.OrderID as int) * 7) % 60), 'invoice-paid', [ev('Invoices', 'u', [InvoiceID: invoiceId], b, invoice)])
        made.paid++
    }
}
unpaid.each { Map inv ->
    int payDays = 10 + rS.nextInt(40), h = 9 + rS.nextInt(8), m = rS.nextInt(60)
    LocalDate paidOn = LocalDate.parse(inv.InvoiceDate as String).plusDays(payDays)
    if (!paidOn.isAfter(DAY_ZERO)) paidOn = FIRST.plusDays(1 + (inv.InvoiceID as int) % 9)
    if (paidOn.isAfter(LAST)) return
    Map after = new LinkedHashMap(inv); after.PaidDate = paidOn.toString()
    addTx(at(paidOn, h, m), 'invoice-paid', [ev('Invoices', 'u', [InvoiceID: inv.InvoiceID], inv, after)])
    made.paid++
}
// The source's own daily count, at 02:00 the next morning.
(1..DAYS).each { int dn ->
    LocalDate d = DAY_ZERO.plusDays(dn)
    List c = control[d]
    addTx(at(d.plusDays(1), 2, 0), 'control', [ev('_control', 'c', [day: d.toString()], null,
                                                 [day: d.toString(), orders: c[0], order_lines: c[1], amount: jv(c[2] as BigDecimal)])])
}
log.info("  business: {} orders ({} lines, {} web), {} shipped, {} cancelled, {} invoices, {} paid",
         made.orders, made.lines, made.web, made.shipped, made.cancelled, made.invoices, made.paid)

// ── 3. delivery: event ids, arrival times, duplicates, the log's offsets ──────────────────────────
txs.sort { a, b -> (a.time as LocalDateTime) <=> (b.time as LocalDateTime) ?: (a.n as int) <=> (b.n as int) }
long eventNo = 0
int txNo = 0
txs.each { Map tx ->
    tx.tx = ++txNo
    String eventTime = JSON.writeValueAsString((tx.time as LocalDateTime).format(ISO))
    tx.lines = (tx.events as List<Event>).collect { Event e ->
        String line = '{"event_id":' + JSON.writeValueAsString(String.format('E%07d', ++eventNo)) + ',"tx":' + tx.tx +
                      ',"table":' + JSON.writeValueAsString(e.table) + ',"op":' + JSON.writeValueAsString(e.op) + ',"key":' + e.key +
                      ',"event_time":' + eventTime + e.rows
        e.rows = null                                    // in the line now; the event keeps its table, op and key
        line
    }
}
Random rT = rnd('Delivery')
List<List> deliveries = []                                 // [arrival, tx order, copy, lines, kind]
int lateCount = 0, oooCount = 0, twiceCount = 0
List<Map> resendCandidates = []
txs.each { Map tx ->
    int secs = 1 + rT.nextInt(30), oooMin = 1 + rT.nextInt(59), lateDays = 1 + rT.nextInt(5), lateMin = rT.nextInt(1440), dupSecs = 5 + rT.nextInt(116)
    double roll = rT.nextDouble(), dupRoll = rT.nextDouble()
    LocalDateTime t = tx.time
    List<String> lines = tx.lines
    if (tx.drop) return
    boolean plain = tx.kind == 'control' || tx.arriveAt
    LocalDateTime arrival
    if (tx.arriveAt) arrival = tx.arriveAt
    else if (tx.kind == 'control') arrival = t.plusSeconds(5)
    else if (roll < 0.03) { arrival = t.plusDays(lateDays).withHour(0).withMinute(0).plusMinutes(lateMin); if (!arrival.isAfter(t)) arrival = t.plusSeconds(secs); else lateCount++ }
    else if (roll < 0.08) { arrival = t.plusMinutes(oooMin).plusSeconds(secs); oooCount++ }
    else arrival = t.plusSeconds(secs)
    tx.arrival = arrival
    deliveries << [arrival, tx.tx, 0, lines, tx.kind]
    if (!plain && dupRoll < 0.005) { deliveries << [arrival.plusSeconds(dupSecs), tx.tx, 1, lines, tx.kind]; twiceCount++ }
    if (!plain && tx.kind == 'order' && dayNo(t) in 10..70 && !tx.arriveAt) resendCandidates << tx
}
// A few sent again 8–10 days later, outside a 7-day dedup window.
Random rR = rnd('Delivery.Resend')
int N_RESEND = Math.max(3, Math.round(twiceCount / 10.0) as int)
List<Map> resent = []
N_RESEND.times {
    Map tx = resendCandidates.remove(rR.nextInt(resendCandidates.size()))
    LocalDateTime again = (tx.arrival as LocalDateTime).plusDays(8 + rR.nextInt(3)).plusMinutes(rR.nextInt(120))
    deliveries << [again, tx.tx, 2, tx.lines, tx.kind]
    resent << tx
}
// PLANTED — ETL S4 · 10: a truncated line on day 52, just before the intact one.
List poisonOf = deliveries.findAll { dayNo(it[0] as LocalDateTime) == POISON_DAY && it[4] == 'order' && (it[0] as LocalDateTime).hour >= 12 }
                          .min { a, b -> (a[0] as LocalDateTime) <=> (b[0] as LocalDateTime) ?: (a[1] as int) <=> (b[1] as int) }
String poisonLine = (poisonOf[3] as List<String>)[0]
deliveries << [(poisonOf[0] as LocalDateTime).minusSeconds(1), poisonOf[1], -1, [poisonLine.substring(0, (int) (poisonLine.length() * 0.6))], 'poison']
// PLANTED — ETL S4 · 10: 20 minutes of records the apply step cannot use (no key, an unknown op, an unknown table,
// a create with no row), about a third of a normal day's volume.
int eventsPerDay = (int) (txs.sum { (it.events as List).size() } as long) / DAYS
int N_REJECTS = Math.max(12, (int) (eventsPerDay * 0.3))
LocalDateTime rejectFrom = at(DAY_ZERO.plusDays(REJECT_DAY), 14, 0)
List<String> rejectLines = (1..N_REJECTS).collect { int i ->
    Map line = new LinkedHashMap()
    line.event_id = String.format('R%06d', i); line.tx = 0
    int kind = i % 4
    line.table = kind == 2 ? 'Ordrs' : 'Orders'
    line.op = kind == 1 ? 'x' : 'c'
    line.key = kind == 0 ? [OrderID: null] : [OrderID: nextOrderId + i]
    line.event_time = rejectFrom.plusSeconds((long) (i * 1200L).intdiv(N_REJECTS)).format(ISO)
    line.before = null
    line.after = kind == 3 ? null : [OrderID: kind == 0 ? null : nextOrderId + i, Status: 'Open']
    JSON.writeValueAsString(line)
}
rejectLines.eachWithIndex { String l, int i -> deliveries << [rejectFrom.plusSeconds((long) (i * 1200L).intdiv(N_REJECTS) + 2), 0, 3 + i, [l], 'reject'] }
// PLANTED — dbt S3 · 20: one order create delivered twice INSIDE the last arrival day, so both copies land in the
// same daily partition. Duplicate OrderIDs already exist earlier in the quarter — the redelivered stretch (days
// 59/60), the three resends 8–10 days on, the day-45 bad load, the random sent-twice — but every one of them
// either straddles two batches or sits far back in history. Without this, a `unique` test scoped to "yesterday's
// partition" would pass on every single day of this log, and a reader would rightly conclude the cheap windowed
// test is as good as the full-table one. This is the counterexample that makes the trade-off visible: the
// windowed test catches THIS duplicate and still misses the older ones. Deliberately on the LAST day, which is
// also the cheapest place to put it — everything before day 60 keeps its offsets, including the redelivered
// stretch that a lesson quotes by number.
// hour < 23 so that the second copy, seven minutes later, cannot spill into the next arrival day.
List lastDayCreate = deliveries.findAll {
    dayNo(it[0] as LocalDateTime) == DAYS && (it[2] as int) == 0 && (it[0] as LocalDateTime).hour < 23 &&
            (it[3] as List<String>).any { String l -> l.contains('"table":"Orders"') && l.contains('"op":"c"') }
}.max { a, b -> (a[0] as LocalDateTime) <=> (b[0] as LocalDateTime) ?: (a[1] as int) <=> (b[1] as int) }
if (!lastDayCreate) throw new IllegalStateException("no order create arrives before 23:00 on day ${DAYS} to duplicate.")
LocalDateTime dupInBatch = (lastDayCreate[0] as LocalDateTime).plusMinutes(7)
deliveries << [dupInBatch, lastDayCreate[1], 99, lastDayCreate[3], lastDayCreate[4]]

deliveries.sort { a, b -> (a[0] as LocalDateTime) <=> (b[0] as LocalDateTime) ?: (a[1] as int) <=> (b[1] as int) ?: (a[2] as int) <=> (b[2] as int) }
List<List> feed = []                                        // [offset, arrival, line]
long offset = 0
Map<Long, Long> offsetOfTx = [:]
deliveries.each { List d ->
    if ((d[2] as int) == 0) offsetOfTx[(d[1] as Number).longValue()] = offset + 1
    // The web shop's connector stops after day 70: from then on no WebOrders event reaches the log.
    boolean webStopped = dayNo(d[0] as LocalDateTime) > WEB_FEED_LAST_DAY
    (d[3] as List<String>).each { String l -> if (!(webStopped && l.contains('"table":"WebOrders"'))) feed << [++offset, d[0], l] }
}
// PLANTED — ETL S4 · 30: a consumer restart on day 60 at 00:30 receives the last quarter of day 59's records again —
// the same offsets, the same lines.
List<List> day59 = feed.findAll { dayNo(it[1] as LocalDateTime) == REDELIVERY_DAY - 1 }
List<List> again = day59.drop(day59.size() - day59.size().intdiv(4))
LocalDateTime restart = at(DAY_ZERO.plusDays(REDELIVERY_DAY), 0, 30)
feed.addAll(again.collect { [it[0], restart, it[2]] })
feed.sort { a, b -> (a[1] as LocalDateTime) <=> (b[1] as LocalDateTime) ?: (a[0] as long) <=> (b[0] as long) }
int ARRIVAL_DAYS = dayNo(feed.last()[1] as LocalDateTime)
log.info("  delivery: {} records, {} offsets, over {} arrival days; {} transactions late by days, {} by minutes, {} sent twice, {} sent again 8–10 days later, {} redelivered",
         feed.size(), offset, ARRIVAL_DAYS, lateCount, oooCount, twiceCount, N_RESEND, again.size())

// ── 4. where each planted thing is ────────────────────────────────────────────────────────────
List<List> plantRows = []
def plantAt = { String what, String lesson, String detail, LocalDateTime arrival, String ref -> plantRows << [what, lesson, dayNo(arrival), ref, detail] }
plants.each { List p -> Map tx = p[3]; plantAt(p[0], p[1], p[2], tx.arrival, "tx ${tx.tx}".toString()) }
plantAt('as-of-segment-order', 'ETL S4 · 25', "order ${segmentOrder.orderId} keyed ${(segmentOrder.created as LocalDateTime).format(ISO)}, before the segment change".toString(), (segmentOrder.createTx as Map).arrival, "tx ${(segmentOrder.createTx as Map).tx}".toString())
plantAt('as-of-price-order', 'ETL S4 · 25', "order ${priceOrder.orderId} keyed ${(priceOrder.created as LocalDateTime).format(ISO)}, before the price change".toString(), (priceOrder.createTx as Map).arrival, "tx ${(priceOrder.createTx as Map).tx}".toString())
[monthEnd1, monthEnd2].each { Map p -> plantAt('month-boundary', 'dbt S3 · 10', "order ${p.orderId} dated ${p.orderDate}, keyed ${(p.created as LocalDateTime).format(ISO)}".toString(), (p.createTx as Map).arrival, "OrderID ${p.orderId}".toString()) }
plantAt('keyed-in-error', 'ETL S3 · 15', "order ${errorOrder.orderId} created and deleted 25 minutes later, with its lines".toString(), (errorOrder.deleteTx as Map).arrival, "OrderID ${errorOrder.orderId}".toString())
plantAt('line-deleted', 'ETL S3 · 15', "a line of order ${lineDeleteOrder.orderId} deleted; Orders.UpdatedAt does not move".toString(), (lineDeleteOrder.deleteTx as Map).arrival, "OrderID ${lineDeleteOrder.orderId}".toString())
plantAt('reinstated', 'Data Warehousing S1 · 22', "order ${reinstateOrder.orderId}: Open, Cancelled, Open, Shipped".toString(), (reinstateOrder.cancelTx as Map).arrival, "OrderID ${reinstateOrder.orderId}".toString())
plantAt('bad-duplicate-order-id', 'dbt S2 · 50, ETL S2', "a second Orders row for OrderID ${dupRow.OrderID}, customer ${otherBuyer}".toString(), dupTx.arrival, "OrderID ${dupRow.OrderID}".toString())
plantAt('bad-negative-quantity', 'dbt S2 · 50, ETL S2', "order ${negativeOrder.orderId} has a line with a negative Quantity (cancelled the next morning)".toString(), (negativeOrder.createTx as Map).arrival, "OrderID ${negativeOrder.orderId}".toString())
plantAt('bad-unknown-customer', 'dbt S2 · 50, ETL S2', "order ${ghostOrder.orderId} for CustomerID ${ghost}, which does not exist".toString(), (ghostOrder.createTx as Map).arrival, "OrderID ${ghostOrder.orderId}".toString())
plantAt('bad-typed-status', 'dbt S1 · 45', "order ${typedStatusOrder.orderId} shipped with Status 'shipped ' typed by hand".toString(), (typedStatusOrder.shipTx as Map).arrival, "OrderID ${typedStatusOrder.orderId}".toString())
tiePairs.each { List<Map> pair ->
    Map second = pair[1].shipTx as Map
    plantAt('watermark-tie', 'ETL S1 · 35, dbt S2', "orders ${pair[0].orderId} and ${pair[1].orderId} both updated at ${(second.time as LocalDateTime).format(ISO)}; the second arrives after midnight".toString(), second.arrival, "OrderID ${pair[1].orderId}".toString())
}
joiners.findAll { it.factFirst }.each { Map j ->
    Map first = plans.find { it.customer == j.id && it.joinerFirst }
    plantAt('fact-before-dimension', 'ETL S4 · 20, Data Warehousing S1', "customer ${j.id}: order ${first.orderId} keyed ${(first.created as LocalDateTime).format(ISO)}, the customer record at ${(j.created as LocalDateTime).format(ISO)}".toString(), (first.createTx as Map).arrival, "CustomerID ${j.id}".toString())
}
Map neverFirst = plans.find { it.customer == missingCustomer.id && it.joinerFirst }
plantAt('dimension-never-arrives', 'ETL S4 · 20', "customer ${missingCustomer.id} was created in the source but its event never reaches the log; its orders do".toString(), (neverFirst.createTx as Map).arrival, "CustomerID ${missingCustomer.id}".toString())
plantAt('web-feed-stops', 'dbt S1 · 10, ETL S2', "no WebOrders events after day ${WEB_FEED_LAST_DAY}; Orders keep arriving".toString(), at(DAY_ZERO.plusDays(WEB_FEED_LAST_DAY + 1), 0, 0), 'table WebOrders')
plantAt('poison', 'ETL S4 · 10', 'a truncated, unparseable line; the intact event follows it', (poisonOf[0] as LocalDateTime), "offset ${feed.find { it[2] == poisonLine.substring(0, (int) (poisonLine.length() * 0.6)) }[0]}".toString())
plantAt('reject-burst', 'ETL S4 · 10', "${N_REJECTS} records with no usable key, op, table or row, 14:00–14:20".toString(), rejectFrom, 'event_id R*')
plantAt('redelivery', 'ETL S4 · 30', "offsets ${again.first()[0]}–${again.last()[0]} delivered again at 00:30 (a consumer restart)".toString(), restart, "offset ${again.first()[0]}".toString())
resent.each { Map tx -> plantAt('resent-late', 'ETL S4 · 15', "order ${tx.orderId} sent again 8–10 days after it first arrived".toString(), (tx.arrival as LocalDateTime).plusDays(8), "tx ${tx.tx}".toString()) }
plantAt('late-arrivals', 'ETL S4 · 00, S4 · 35', "${lateCount} transactions arrive 1–5 days late, ${oooCount} a few minutes out of order".toString(), at(FIRST, 0, 0), 'arrival_time - event_time')
plantAt('sent-twice', 'ETL S4 · 15, S4 · 30', "${twiceCount} transactions delivered twice: the same event_id, a new offset".toString(), at(FIRST, 0, 0), 'event_id')
plantAt('duplicate-in-last-batch', 'dbt S3 · 20', "an order create delivered twice inside arrival day ${DAYS}: both copies land in the same daily partition, so a test scoped to that one day catches it".toString(), dupInBatch, "tx ${lastDayCreate[1]}".toString())

// ── 5. checks: the log must say what this header says ───────────────────────────────────────
Map<String, Integer> plantCount = plantRows.countBy { it[0] }
List<String> problems = []
['as-of-segment', 'as-of-price', 'as-of-segment-order', 'as-of-price-order', 'keyed-in-error', 'line-deleted', 'reinstated', 'bad-duplicate-order-id',
 'bad-negative-quantity', 'bad-unknown-customer', 'bad-typed-status', 'dimension-never-arrives', 'web-feed-stops', 'poison', 'reject-burst', 'redelivery',
 'duplicate-in-last-batch'].each {
    if (plantCount[it] != 1) problems << "${it}: ${plantCount[it] ?: 0} planted, expected 1".toString()
}
if ((plantCount['watermark-tie'] ?: 0) != TIE_DAYS.size()) problems << 'watermark ties missing'
if ((plantCount['fact-before-dimension'] ?: 0) != N_FACT_FIRST) problems << 'fact-before-dimension joiners missing'
if ((plantCount['month-boundary'] ?: 0) != 2) problems << 'month-boundary orders missing'
if (plantRows.find { it[0] == 'bad-duplicate-order-id' }[2] != BAD_DAY || plantRows.find { it[0] == 'bad-typed-status' }[2] != BAD_DAY ||
    plantRows.find { it[0] == 'bad-unknown-customer' }[2] != BAD_DAY || plantRows.find { it[0] == 'bad-negative-quantity' }[2] != BAD_DAY)
    problems << 'the bad load is not all on day 45'
tiePairs.each { List<Map> pair -> if (dayNo((pair[1].shipTx as Map).arrival as LocalDateTime) != dayNo((pair[0].shipTx as Map).arrival as LocalDateTime) + 1) problems << "watermark tie on ${pair[0].orderId} does not straddle midnight".toString() }
joiners.findAll { it.factFirst }.each { Map j ->
    Map first = plans.find { it.customer == j.id && it.joinerFirst }
    if (!((first.createTx as Map).arrival as LocalDateTime).isBefore((j.tx as Map).arrival as LocalDateTime)) problems << "customer ${j.id} arrives before its first order".toString()
}
if (!((segmentOrder.createTx as Map).arrival as LocalDateTime).isAfter(SEGMENT_CHANGE_AT)) problems << 'the as-of-segment order arrives before the change'
if (!((priceOrder.createTx as Map).arrival as LocalDateTime).isAfter(PRICE_CHANGE_AT)) problems << 'the as-of-price order arrives before the change'
if (!((segmentOrder.createTx as Map).events.any { it.table == 'Order Details' })) problems << 'the as-of-segment order has no lines'
if (!((priceOrder.createTx as Map).events.any { it.table == 'Order Details' && JSON.readValue(it.key as String, Map).ProductID == hotProduct })) problems << 'the as-of-price order does not buy the product'
if (feed.any { dayNo(it[1] as LocalDateTime) > WEB_FEED_LAST_DAY && (it[2] as String).contains('"table":"WebOrders"') }) problems << 'WebOrders events after the feed stopped'
if (!feed.any { dayNo(it[1] as LocalDateTime) > WEB_FEED_LAST_DAY && (it[2] as String).contains('"Channel":"Web"') }) problems << 'no web orders after the feed stopped'
Set<LocalDate> arrivalDays = feed.collect { (it[1] as LocalDateTime).toLocalDate() } as Set
(1..DAYS).each { int dn -> if (!(DAY_ZERO.plusDays(dn) in arrivalDays)) problems << "arrival day ${dn} is empty".toString() }
if (feed.count { (it[2] as String).contains('"table":"_control"') } != DAYS) problems << 'a control total is missing'
int controlOrders = control.values().sum { it[0] } as int
if (controlOrders != made.orders - 1) problems << "control totals count ${controlOrders} orders, ${made.orders - 1} were kept".toString()
if (problems) throw new IllegalStateException("Change log check failed — ${problems.join('; ')}. Nothing is wrong with the source; this script has a bug.")
log.info("  ok: every planted incident is in the log, on its day ({} kinds, {} rows in _plants)", plantCount.size(), plantRows.size())
// From here on only the log itself and the planted rows are needed: what the log was made from goes, so that the
// heap holds one copy of the log while it is written.
txs = null; plans = null; deliveries = null; resendCandidates = null; dimChanges = null
customers = null; custHistory = null; joiners = null; joinerById = null; day59 = null
// ── 6. write ─────────────────────────────────────────────────────────────────────────────────
if (CH) {
    dbSql.execute("DROP DATABASE IF EXISTS ${DST} SYNC".toString())
    dbSql.execute("CREATE DATABASE ${DST}".toString())
} else {
    dbSql.execute("DROP SCHEMA IF EXISTS ${DST} CASCADE".toString())
    dbSql.execute("CREATE SCHEMA ${DST}".toString())
}
String DDL = """
CREATE TABLE ${T('event_log')} (seq INTEGER PRIMARY KEY, "offset" INTEGER NOT NULL, arrival_time TIMESTAMP NOT NULL, line VARCHAR NOT NULL);
CREATE TABLE ${T('_plants')} (plant VARCHAR(40) NOT NULL, lesson VARCHAR(60) NOT NULL, arrival_day INTEGER NOT NULL, ref VARCHAR(60) NOT NULL, detail VARCHAR(200) NOT NULL)
""".toString()
DDL.split(';').collect { it.trim() }.findAll { it }.each { dbSql.execute(CH ? chDdl(it) : it) }
// Rows go to a CSV file and in with one COPY — row-by-row INSERTs take minutes at L. ClickHouse takes batched
// INSERTs, 20,000 rows at a time (each batch is one INSERT, one part on disk).
DateTimeFormatter TS_CSV = DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss')
def csvValue = { v ->
    if (v == null) return '\\N'
    if (v instanceof CharSequence) return '"' + v.toString().replace('"', '""') + '"'
    if (v instanceof LocalDateTime) return ((LocalDateTime) v).format(TS_CSV)
    v.toString()
}
def insert = { String table, List<String> cols, List<List> rows ->
    if (CH) {
        String sql = "INSERT INTO ${T(table)} (${cols.join(', ')}) VALUES (${cols.collect { '?' }.join(', ')})".toString()
        rows.collate(20_000).each { List<List> chunk -> dbSql.withBatch(chunk.size(), sql) { ps -> chunk.each { ps.addBatch(it.collect(chValue)) } } }
        log.info("  {}: {} rows", table, rows.size())
        return
    }
    File f = File.createTempFile("${DST}-", '.csv')
    try {
        f.withWriter('UTF-8') { w -> rows.each { r -> w.write(r.collect(csvValue).join(',')); w.write('\n') } }
        String target = "${T(table)} (${cols.join(', ')})"
        if (vendor == 'DUCKDB') {
            String path = f.absolutePath.replace('\\', '/').replace("'", "''")
            dbSql.execute("COPY ${target} FROM '${path}' (FORMAT csv, HEADER false, DELIMITER ',', QUOTE '\"', ESCAPE '\"', NULL '\\N')".toString())
        } else {
            def copyApi = dbSql.connection.unwrap(Class.forName('org.postgresql.PGConnection')).getCopyAPI()
            f.withReader('UTF-8') { r -> copyApi.copyIn("COPY ${target} FROM STDIN WITH (FORMAT csv, NULL '\\N')".toString(), r) }
        }
    } finally { f.delete() }
    log.info("  {}: {} rows", table, rows.size())
}
// The log's rows as event_log has them, made one at a time as they are written rather than as a second copy of it.
List<List> eventRows = new AbstractList<List>() {
    int size() { feed.size() }
    List get(int i) { List f = feed[i] as List; [i + 1, f[0], f[1], f[2]] }
}
insert('event_log', ['seq', '"offset"', 'arrival_time', 'line'], eventRows)
insert('_plants', ['plant', 'lesson', 'arrival_day', 'ref', 'detail'], plantRows.sort { a, b -> (a[2] as int) <=> (b[2] as int) ?: a[0] <=> b[0] ?: a[3] <=> b[3] })
if (EXPORT_DIR) {
    File dir = new File(EXPORT_DIR, DST)
    dir.mkdirs()
    feed.groupBy { (it[1] as LocalDateTime).toLocalDate() }.each { LocalDate d, List<List> rows ->
        new File(dir, "events-${d}.jsonl").withWriter('UTF-8') { w -> rows.each { w.write(it[2] as String); w.write('\n') } }
    }
    log.info("  exported {} files to {}", ARRIVAL_DAYS, dir)
}

// ── 6b. figures: the counts the curriculum quotes, stated by the script that produces them ────────
// WHY THIS EXISTS. About thirty `reason:` fields across Data Warehousing, ETL and dbt quote this
// log's totals. Hand-copied, they go stale the moment this script runs again — and one already was:
// an entry read "6,138 events ... (4,720 creates, 1,406 updates, 6 deletes)", whose parts are six
// short of its own total. Both halves of that were wrong, and in a way worth writing down, because
// it is the same trap a lesson walks a learner into: the op is counted off the DELIVERED JSON text,
// so op_create also counts the 15 reject lines that carry op "c" and the one truncated poison line
// that still has its op in the surviving 60% — 4,721, not 4,720 — and the remaining 5 reject lines
// carry op "x", which is neither c, u nor d. Nothing here is missing an op. The four buckets below
// therefore partition `events` exactly, and reject_op_create/reject_op_unknown/poison say how much
// of them is planted garbage. Counting what a consumer receives, rather than what was generated
// before delivery, is deliberate: it is the number the lessons talk about.
// CurriculumSpec checks the prose against this file instead of trusting a human to re-type it.
int opCreate = (feed.count { ((String) it[2]).contains('"op":"c"') }) as int
int opUpdate = (feed.count { ((String) it[2]).contains('"op":"u"') }) as int
int opDelete = (feed.count { ((String) it[2]).contains('"op":"d"') }) as int
int rejectC = (feed.count { String l = (String) it[2]; l.contains('"event_id":"R') && l.contains('"op":"c"') }) as int
Map<String, Object> figures = new LinkedHashMap<String, Object>()
figures.events = feed.size()
figures.offsets = offset
figures.arrival_days = ARRIVAL_DAYS
figures.op_create = opCreate
figures.op_update = opUpdate
figures.op_delete = opDelete
figures.op_unknown = feed.size() - (opCreate + opUpdate + opDelete)
figures.late_by_days = lateCount
figures.out_of_order_by_minutes = oooCount
figures.sent_twice = twiceCount
figures.resent_late = N_RESEND
figures.redelivered = again.size()
figures.redelivered_offset_first = again ? again.first()[0] : 0
figures.redelivered_offset_last = again ? again.last()[0] : 0
figures.rejects = N_REJECTS
figures.reject_op_create = rejectC
figures.reject_op_unknown = N_REJECTS - rejectC
figures.poison = 1
figures.control_totals = DAYS
figures.bad_day = BAD_DAY
figures.web_feed_last_day = WEB_FEED_LAST_DAY
figures.redelivery_day = REDELIVERY_DAY
// The manifest is only worth checking prose against if it is self-consistent. Two arithmetic facts
// hold by construction; if a future edit breaks one, fail here rather than publish a wrong figure.
if (opCreate + opUpdate + opDelete + (figures.op_unknown as int) != feed.size())
    throw new IllegalStateException("figures: the op buckets do not add up to ${feed.size()} events.")
if (rejectC + (figures.reject_op_unknown as int) != N_REJECTS)
    throw new IllegalStateException("figures: reject lines do not add up to ${N_REJECTS}.")
File figuresFile = FIGURES_FILE ? new File(FIGURES_FILE) : (EXPORT_DIR ? new File(EXPORT_DIR, "${DST}-figures.yaml".toString()) : null)
if (figuresFile) {
    figuresFile.parentFile?.mkdirs()
    StringBuilder y = new StringBuilder()
    y << "# Emitted by academy-northwind-co-changes.groovy — do not hand-edit, re-run the script.\n"
    y << "# CurriculumSpec checks every figure quoted in a curriculum reason against this file.\n"
    y << "dataset: ${DATASET}\nscale: ${SCALE}\n".toString()
    y << "figures:\n"
    figures.each { String k, Object v -> y << "  ${k}: ${v}\n".toString() }
    y << "plants:\n"
    new TreeMap<String, Integer>(plantRows.countBy { it[0] } as Map<String, Integer>)
            .each { String k, Integer v -> y << "  ${k}: ${v}\n".toString() }
    // LF, always: this file is read on Windows and Linux and committed to the content repo.
    figuresFile.withWriter('UTF-8') { w -> w.write(y.toString().replace('\r\n', '\n')) }
    log.info("  figures: {} counts and {} plant kinds written to {}", figures.size(), plantRows.countBy { it[0] }.size(), figuresFile)
}

// ── 7. _dataset_info: counts and checksums (the canonical form of academy-verify.groovy) ──────────
def q = { String name -> "\"${name}\"".toString() }
def canon = { Object v ->
    if (v == null) return '<NULL>'
    if (v instanceof Boolean) return v ? 'true' : 'false'
    if (v instanceof Number) { String s = new BigDecimal(v.toString()).stripTrailingZeros().toPlainString(); return s == '-0' ? '0' : s }
    if (v instanceof Timestamp) return ((Timestamp) v).toLocalDateTime().toString().replace('T', ' ').padRight(19, ':00').substring(0, 19)
    if (v instanceof LocalDateTime) return v.toString().replace('T', ' ').padRight(19, ':00').substring(0, 19)
    if (v instanceof java.sql.Date) return ((java.sql.Date) v).toLocalDate().toString()
    v.toString()
}
int EVENTS = feed.size()
feed = null                                                // in event_log now; the checksum reads it back from there
BigInteger MOD = BigInteger.ONE.shiftLeft(256)
// Reads a big table in pieces: PostgreSQL otherwise fetches every row into memory before the first one is seen.
def eachRowStreamed = { String sql, Closure c ->
    if (vendor != 'POSTGRES') { dbSql.eachRow(sql, c); return }
    def conn = dbSql.connection
    boolean autoCommit = conn.autoCommit
    conn.autoCommit = false
    try { dbSql.withStatement { it.fetchSize = 10_000 }; dbSql.eachRow(sql, c) }
    finally { dbSql.withStatement { it.fetchSize = 0 }; conn.commit(); conn.autoCommit = autoCommit }
}
String infoDdl = "CREATE TABLE ${T('_dataset_info')} (\"Dataset\" VARCHAR(40), \"Scale\" VARCHAR(2), \"TableName\" VARCHAR(40), \"RowCount\" INTEGER, \"Checksum\" VARCHAR(64))".toString()
dbSql.execute(CH ? chDdl(infoDdl) : infoDdl)
// A table's columns for the checksum, sorted case-insensitively, as SELECT expressions. ClickHouse is asked for its
// times as text (toString), which is the canonical form already.
def checksumColumns = { String table ->
    if (!CH) return dbSql.rows("SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?".toString(), [DST, table])
                         .collect { it.column_name as String }.sort { a, b -> a.compareToIgnoreCase(b) }.collect { q(it) }
    dbSql.rows("SELECT name, type FROM system.columns WHERE database = ? AND table = ?".toString(), [DST, table])
         .sort { a, b -> (a.name as String).compareToIgnoreCase(b.name as String) }
         .collect { (it.type as String) ==~ /^(Nullable\()?(Date|Date32|DateTime|Bool)\b.*/ ? "toString(${q(it.name as String)}) AS ${q(it.name as String)}".toString() : q(it.name as String) }
}
List<List> info = ['event_log', '_plants'].collect { String table ->
    List<String> cols = checksumColumns(table)
    String select = "SELECT ${cols.join(', ')} FROM ${T(table)}".toString()
    int n = 0
    String hex
    if (SCALE == 'S') {
        List<String> rows = []
        dbSql.eachRow(select) { r -> rows << (1..cols.size()).collect { canon(r.getObject(it)) }.join('\t') }
        rows.sort()
        n = rows.size()
        hex = MessageDigest.getInstance('SHA-256').digest(rows.join('\n').getBytes('UTF-8')).collect { String.format('%02x', it) }.join()
    } else {
        MessageDigest sha = MessageDigest.getInstance('SHA-256')
        BigInteger sum = BigInteger.ZERO
        eachRowStreamed(select) { r -> sum = sum.add(new BigInteger(1, sha.digest((1..cols.size()).collect { canon(r.getObject(it)) }.join('\t').getBytes('UTF-8')))); n++ }
        hex = String.format('%064x', sum.mod(MOD))
    }
    [DATASET, SCALE, table, n, hex]
}
dbSql.withBatch(info.size(), "INSERT INTO ${T('_dataset_info')} VALUES (?, ?, ?, ?, ?)".toString()) { ps -> info.each { ps.addBatch(it) } }
log.info("=== {} built from {}: {} records over {} arrival days, {} planted incidents (see {}) ===", DST, SRC, EVENTS, ARRIVAL_DAYS, plantRows.size(), T('_plants'))
