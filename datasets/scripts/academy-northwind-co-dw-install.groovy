// @description Academy dataset: builds "Northwind Company Warehouse" (a star schema) from an installed Northwind Company schema of the same scale. Set SCALE below. Idempotent — drops and recreates the schema.
// Bindings provided by GenericSeedExecutor:
//   dbSql  — groovy.sql.Sql connected to the target database
//   vendor — String (uppercase): POSTGRES, DUCKDB, CLICKHOUSE
//   log    — SLF4J Logger
//   params — Map; optional keys: SCALE, LOAD_CUTOFF (2024-12-31 02:00:00)

import groovy.transform.CompileStatic
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.IsoFields

// ═════════════════════════════════════════════════════════════════════════════════════════════
// SCALE — 'S', 'M' or 'L', the same as the installed Northwind Company it is built from: northwind_co_s becomes
// northwind_co_dw_s, and so on. params.SCALE, when the Seed Data tab passes one, wins over this line.
// ═════════════════════════════════════════════════════════════════════════════════════════════
String SCALE = (params?.SCALE ?: 'S').toString().toUpperCase()
// THE NIGHTLY LOAD this warehouse is the result of. The company's day ends on 2024-12-31; the load ran at 02:00 that
// morning, so what was keyed, shipped or changed after it is in the source and not here. That gap is Data
// Warehousing S1 · 22's first reason a warehouse and its source legitimately differ (timing, and late arrivals).
String LOAD_CUTOFF = (params?.LOAD_CUTOFF ?: '2024-12-31 02:00:00').toString()
int VERSION = 1
// ═════════════════════════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────────────────────
// WHAT THIS IS
// The warehouse the Data Warehousing course reads, loads into and reconciles against: derived from Northwind
// Company, never generated on its own, so every figure in it can be traced to a source row. Nothing random: two
// builds from the same source are identical (_dataset_info). Design: kraft-src-company-biz/.docs/plan-academy-
// datasets.md, D5.
//
// THE TABLES
//   fact_order_line          one row per order line; the order number stays on it (a degenerate dimension)
//   fact_order               one row per order; Freight lives here, once per order, never once per line
//   fact_inventory_monthly   one row per product per month end: stock on hand is SEMI-ADDITIVE (sum it across
//                            products, never across months)
//   fact_sales_target        one row per category per month, January 2025 budgeted with no sales yet
//   dim_date                 2020–2025, ISO weeks, a fiscal year starting in July, UK bank holidays and working days
//                            (the company ships from the UK), and two rows for a date that is missing:
//                              0  'Not applicable' — the order has not shipped (Open, Cancelled)
//                             -1  'Unknown'        — it shipped, but nobody recorded the date (the Speedy Express
//                                                    outage): the difference the outage story turns on
//   dim_customer             SCD TYPE 2 on Segment and City (a new row when either changes); TYPE 1 on the contact
//                            name (overwritten everywhere). Cities cleaned (stray spaces, capitals), Region left NULL
//                            where the country has none — a real NULL beside ROLLUP's subtotal NULL
//   dim_product              SCD TYPE 2 on the list price, from PriceChanges: the line price of every order equals
//                            the price on its row (a check below proves it)
//   dim_category, dim_employee, dim_shipper, bridge_employee_territory (each rep's five territories, weight 0.2)
//   fact_order_line_wide     M and L only: the same lines as ONE BIG TABLE, every dimension copied in (Data
//                            Warehousing S2 · 40, star vs one big table)
//   _load_audit              the load's cutoff and what it left behind
//   _dataset_info            row counts and checksums, as in every academy schema (academy-verify.groovy reads it)
//
// AS OF THE CUTOFF: an order keyed after LOAD_CUTOFF is not loaded; an order's status is the one it had at the
// cutoff (from OrderStatusHistory), and its ship date only if it was recorded by then.
//
// WHO NEEDS WHAT
//   Data Warehousing S1 · 10–45   the star, grain, drill paths, ROLLUP's real NULL region, pre-aggregation, the date
//                                 dimension, the answer key a learner's own load is compared with (S1 · 20, · 45)
//   Data Warehousing S1 · 22      the cutoff, cancelled orders on the fact, the reinstated order
//   Data Warehousing S2           M: partitioning, one big table, compression, rows read
//   Data Warehousing S3 · 10      SCD2 as-of joins (customer segment and city, product price)
//   ETL S3 · 05                   the target star someone else designed, loaded and checked row for row
// ENGINES: PostgreSQL, DuckDB and ClickHouse, the same engine the source was installed on (the ClickHouse
// differences are listed where its helpers are, below).
// ─────────────────────────────────────────────────────────────────────────────────────────────

if (!(SCALE in ['S', 'M', 'L'])) throw new IllegalArgumentException("SCALE must be S, M or L (got ${SCALE}).")
if (!(vendor in ['POSTGRES', 'DUCKDB', 'CLICKHOUSE'])) throw new IllegalArgumentException("Only PostgreSQL, DuckDB and ClickHouse are supported (connection is ${vendor}).")
if (!(LOAD_CUTOFF ==~ /\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/)) throw new IllegalArgumentException("LOAD_CUTOFF must look like 2024-12-31 02:00:00 (got ${LOAD_CUTOFF}).")
String DATASET = 'northwind_co_dw'
String SRC = "northwind_co_${SCALE.toLowerCase()}".toString()
String DW = "${DATASET}_${SCALE.toLowerCase()}".toString()
boolean ONE_BIG_TABLE = SCALE != 'S'
String CUTOFF = (vendor == 'CLICKHOUSE' ? "toDateTime('${LOAD_CUTOFF}')" : "TIMESTAMP '${LOAD_CUTOFF}'").toString()

def q = { String name -> "\"${name}\"".toString() }
def S = { String table -> "${SRC}.\"${table}\"".toString() }
def T = { String table -> "${DW}.${table}".toString() }

// ── ClickHouse ──────────────────────────────────────────────────────────────────────────────
// The tables are written once, in the DDL PostgreSQL and DuckDB share, and translated for ClickHouse (the same
// translation as academy-northwind-co-install's): VARCHAR → String, INTEGER → Int32, a column that may be NULL →
// Nullable(…), ENGINE = MergeTree ordered by the primary key. valid_from and valid_to are Date32, the only ClickHouse
// date that reaches back to 1900-01-01; it ends on 2299-12-31, so there an open row ends on that day, not 9999-12-31
// (the one difference, and why dim_customer's and dim_product's checksums differ from PostgreSQL's and DuckDB's).
// A range condition (valid_from ≤ date < valid_to) stands in WHERE, not in a join's ON: ClickHouse joins on equal
// keys only, and for an inner join the two mean the same on every engine.
boolean CH = vendor == 'CLICKHOUSE'
Set<String> CH_WIDE_DATES = ['valid_from', 'valid_to'] as Set
def chType = { String type, String col ->
    String u = type.toUpperCase().replaceAll('\\s', '')
    if (u.startsWith('VARCHAR') || u == 'TEXT') return 'String'
    if (u == 'INTEGER') return 'Int32'
    if (u == 'SMALLINT') return 'Int16'
    if (u == 'BIGINT') return 'Int64'
    if (u.startsWith('DECIMAL')) return u.replace('DECIMAL', 'Decimal')
    if (u == 'TIMESTAMP') return 'DateTime'
    if (u == 'DATE') return col in CH_WIDE_DATES ? 'Date32' : 'Date'
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
    List<String> cols = parts.findAll { !it.toUpperCase().startsWith('PRIMARY KEY') }.collect { String p ->
        def c = p =~ /(?s)^("[^"]+"|\w+)\s+(\w+(?:\s*\([^)]*\))?)(.*)$/
        if (!c.matches()) throw new IllegalArgumentException("Cannot read column '${p}' in ${name}.")
        String col = c.group(1), rest = c.group(3).toUpperCase()
        if (rest.contains('PRIMARY KEY')) key << col
        String type = chType(c.group(2), col.replace('"', ''))
        "${col} ${rest.contains('NOT NULL') || rest.contains('PRIMARY KEY') || col in key ? type : "Nullable(${type})"}".toString()
    }
    "CREATE TABLE ${name} (${cols.join(', ')}) ENGINE = MergeTree ORDER BY ${key ? "(${key.join(', ')})" : 'tuple()'}".toString()
}
// Dates go to ClickHouse as text (yyyy-MM-dd), which it stores as that same day on any server.
def chValue = { v -> v instanceof java.sql.Date || v instanceof LocalDate || v instanceof GString ? v.toString() : v }
// A DATE column read back as text on ClickHouse, for the same reason.
def dateCol = { String col -> CH ? "toString(${col}) AS ${col}".toString() : col }
// CREATE TABLE … AS SELECT: ClickHouse needs the table's engine.
def ctas = { String table -> CH ? "CREATE TABLE ${T(table)} ENGINE = MergeTree ORDER BY tuple() AS".toString() : "CREATE TABLE ${T(table)} AS".toString() }
// The SCD2 as-of test: the row of alias `a` valid on day or time x.
def validOn = { String x, String a ->
    String d = CH ? "toDate32(${x})" : x
    "${d} >= ${a}.valid_from AND ${d} < ${a}.valid_to".toString()
}

// ── 0. the source must be there, and be what it says it is ─────────────────────────────────────
List srcInfo
try { srcInfo = dbSql.rows("SELECT \"Dataset\", \"Version\", \"Scale\", \"TableName\" FROM ${S('_dataset_info')}".toString()) }
catch (Exception e) { throw new IllegalStateException("Install Northwind Company scale ${SCALE} first (schema ${SRC} not found: ${e.message}).") }
if (srcInfo.isEmpty() || srcInfo[0].Scale != SCALE || srcInfo[0].Dataset != 'northwind_co')
    throw new IllegalStateException("${SRC} is not Northwind Company scale ${SCALE}.")
if (!srcInfo.any { it.TableName == 'OrderStatusHistory' })
    throw new IllegalStateException("${SRC} was installed by an older academy-northwind-co-install (no OrderStatusHistory). Reinstall it.")
int SOURCE_VERSION = srcInfo[0].Version as int
log.info("=== Warehouse {} v{} from {} (v{}) on {}, loaded as of {} ===", DW, VERSION, SRC, SOURCE_VERSION, vendor, LOAD_CUTOFF)

if (CH) {
    dbSql.execute("DROP DATABASE IF EXISTS ${DW} SYNC".toString())
    dbSql.execute("CREATE DATABASE ${DW}".toString())
} else {
    dbSql.execute("DROP SCHEMA IF EXISTS ${DW} CASCADE".toString())
    dbSql.execute("CREATE SCHEMA ${DW}".toString())
}

String DDL = """
CREATE TABLE ${T('dim_date')} (date_key INTEGER PRIMARY KEY, full_date DATE, date_label VARCHAR(20) NOT NULL, year SMALLINT, quarter SMALLINT, month SMALLINT, month_name VARCHAR(10), year_month INTEGER, day_of_month SMALLINT, day_of_week SMALLINT, day_name VARCHAR(10), is_weekend BOOLEAN, is_month_end BOOLEAN, iso_year SMALLINT, iso_week SMALLINT, fiscal_year VARCHAR(6), fiscal_quarter SMALLINT, fiscal_month SMALLINT, is_uk_holiday BOOLEAN, holiday_name VARCHAR(40), is_working_day BOOLEAN);
CREATE TABLE ${T('dim_customer')} (customer_key INTEGER PRIMARY KEY, customer_id VARCHAR(8) NOT NULL, company_name VARCHAR(40) NOT NULL, contact_name VARCHAR(30), city VARCHAR(20), region VARCHAR(15), country VARCHAR(15), segment VARCHAR(20), valid_from DATE NOT NULL, valid_to DATE NOT NULL, is_current BOOLEAN NOT NULL);
CREATE TABLE ${T('dim_category')} (category_key INTEGER PRIMARY KEY, category_name VARCHAR(15) NOT NULL);
CREATE TABLE ${T('dim_product')} (product_key INTEGER PRIMARY KEY, product_id INTEGER NOT NULL, product_name VARCHAR(40) NOT NULL, category_key INTEGER NOT NULL, category_name VARCHAR(15) NOT NULL, supplier_name VARCHAR(40), supplier_country VARCHAR(15), list_price DECIMAL(19,4), discontinued BOOLEAN NOT NULL, valid_from DATE NOT NULL, valid_to DATE NOT NULL, is_current BOOLEAN NOT NULL);
CREATE TABLE ${T('dim_employee')} (employee_key INTEGER PRIMARY KEY, employee_id INTEGER NOT NULL, full_name VARCHAR(40) NOT NULL, title VARCHAR(30), reports_to_key INTEGER, manager_name VARCHAR(40));
CREATE TABLE ${T('dim_shipper')} (shipper_key INTEGER PRIMARY KEY, shipper_id INTEGER NOT NULL, company_name VARCHAR(40) NOT NULL);
CREATE TABLE ${T('bridge_employee_territory')} (employee_key INTEGER NOT NULL, territory_id VARCHAR(20) NOT NULL, territory_name VARCHAR(50) NOT NULL, region_name VARCHAR(50) NOT NULL, allocation_weight DECIMAL(6,4) NOT NULL, PRIMARY KEY (employee_key, territory_id));
CREATE TABLE ${T('fact_order_line')} (order_id INTEGER NOT NULL, product_key INTEGER NOT NULL, customer_key INTEGER NOT NULL, employee_key INTEGER NOT NULL, shipper_key INTEGER NOT NULL, order_date_key INTEGER NOT NULL, required_date_key INTEGER NOT NULL, shipped_date_key INTEGER NOT NULL, order_status VARCHAR(10) NOT NULL, channel VARCHAR(10) NOT NULL, quantity INTEGER NOT NULL, unit_price DECIMAL(19,4) NOT NULL, discount DECIMAL(8,4) NOT NULL, gross_amount DECIMAL(19,2) NOT NULL, net_amount DECIMAL(19,2) NOT NULL);
CREATE TABLE ${T('fact_order')} (order_id INTEGER PRIMARY KEY, customer_key INTEGER NOT NULL, employee_key INTEGER NOT NULL, shipper_key INTEGER NOT NULL, order_date_key INTEGER NOT NULL, required_date_key INTEGER NOT NULL, shipped_date_key INTEGER NOT NULL, order_status VARCHAR(10) NOT NULL, channel VARCHAR(10) NOT NULL, line_count INTEGER NOT NULL, net_amount DECIMAL(19,2) NOT NULL, freight DECIMAL(19,2) NOT NULL);
CREATE TABLE ${T('fact_inventory_monthly')} (product_key INTEGER NOT NULL, month_end_date_key INTEGER NOT NULL, units_received INTEGER NOT NULL, units_sold INTEGER NOT NULL, units_returned INTEGER NOT NULL, units_adjusted INTEGER NOT NULL, units_on_hand INTEGER NOT NULL, PRIMARY KEY (product_key, month_end_date_key));
CREATE TABLE ${T('fact_sales_target')} (category_key INTEGER NOT NULL, month_date_key INTEGER NOT NULL, target_amount DECIMAL(19,2) NOT NULL, PRIMARY KEY (category_key, month_date_key));
CREATE TABLE ${T('_load_audit')} (source_schema VARCHAR(40) NOT NULL, source_version INTEGER NOT NULL, load_cutoff TIMESTAMP NOT NULL, orders_loaded INTEGER NOT NULL, orders_keyed_after_cutoff INTEGER NOT NULL, orders_changed_after_cutoff INTEGER NOT NULL);
"""
DDL.split(';').collect { it.trim() }.findAll { it }.each { dbSql.execute(CH ? chDdl(it) : it) }

def insert = { String table, List<String> cols, List<List> rows ->
    String sql = "INSERT INTO ${T(table)} (${cols.join(', ')}) VALUES (${cols.collect { '?' }.join(', ')})".toString()
    rows.collate(2000).each { chunk -> dbSql.withBatch(chunk.size(), sql) { ps -> chunk.each { ps.addBatch(CH ? (it as List).collect(chValue) : it) } } }
    log.info("  {}: {} rows", table, rows.size())
}
def count = { String sql -> (dbSql.firstRow(sql.toString()).values().first() as Number).longValue() }
// yyyymmdd, the date key, from a DATE or TIMESTAMP column — the same on PostgreSQL and DuckDB.
def dk = { String x -> "CAST(EXTRACT(YEAR FROM ${x}) * 10000 + EXTRACT(MONTH FROM ${x}) * 100 + EXTRACT(DAY FROM ${x}) AS INTEGER)".toString() }
LocalDate OPEN_START = LocalDate.of(1900, 1, 1), OPEN_END = CH ? LocalDate.of(2299, 12, 31) : LocalDate.of(9999, 12, 31)
// A DATE column comes back as java.sql.Date from PostgreSQL, as LocalDate from DuckDB and as text from ClickHouse (dateCol).
def day = { Object v -> v instanceof java.sql.Date ? ((java.sql.Date) v).toLocalDate() : v instanceof CharSequence ? LocalDate.parse(v.toString()) : v as LocalDate }

// ── 1. dim_date ─────────────────────────────────────────────────────────────────────────────
// England and Wales bank holidays, as published (gov.uk), with the substitute days and the one-off ones.
Map<LocalDate, String> UK_HOLIDAYS = [:]
[['2020-01-01', "New Year's Day"], ['2020-04-10', 'Good Friday'], ['2020-04-13', 'Easter Monday'], ['2020-05-08', 'Early May bank holiday (VE day)'],
 ['2020-05-25', 'Spring bank holiday'], ['2020-08-31', 'Summer bank holiday'], ['2020-12-25', 'Christmas Day'], ['2020-12-28', 'Boxing Day (substitute day)'],
 ['2021-01-01', "New Year's Day"], ['2021-04-02', 'Good Friday'], ['2021-04-05', 'Easter Monday'], ['2021-05-03', 'Early May bank holiday'],
 ['2021-05-31', 'Spring bank holiday'], ['2021-08-30', 'Summer bank holiday'], ['2021-12-27', 'Christmas Day (substitute day)'], ['2021-12-28', 'Boxing Day (substitute day)'],
 ['2022-01-03', "New Year's Day (substitute day)"], ['2022-04-15', 'Good Friday'], ['2022-04-18', 'Easter Monday'], ['2022-05-02', 'Early May bank holiday'],
 ['2022-06-02', 'Spring bank holiday'], ['2022-06-03', 'Platinum Jubilee bank holiday'], ['2022-08-29', 'Summer bank holiday'],
 ['2022-09-19', 'State Funeral of Queen Elizabeth II'], ['2022-12-26', 'Boxing Day'], ['2022-12-27', 'Christmas Day (substitute day)'],
 ['2023-01-02', "New Year's Day (substitute day)"], ['2023-04-07', 'Good Friday'], ['2023-04-10', 'Easter Monday'], ['2023-05-01', 'Early May bank holiday'],
 ['2023-05-08', 'Coronation of King Charles III'], ['2023-05-29', 'Spring bank holiday'], ['2023-08-28', 'Summer bank holiday'], ['2023-12-25', 'Christmas Day'],
 ['2023-12-26', 'Boxing Day'],
 ['2024-01-01', "New Year's Day"], ['2024-03-29', 'Good Friday'], ['2024-04-01', 'Easter Monday'], ['2024-05-06', 'Early May bank holiday'],
 ['2024-05-27', 'Spring bank holiday'], ['2024-08-26', 'Summer bank holiday'], ['2024-12-25', 'Christmas Day'], ['2024-12-26', 'Boxing Day'],
 ['2025-01-01', "New Year's Day"], ['2025-04-18', 'Good Friday'], ['2025-04-21', 'Easter Monday'], ['2025-05-05', 'Early May bank holiday'],
 ['2025-05-26', 'Spring bank holiday'], ['2025-08-25', 'Summer bank holiday'], ['2025-12-25', 'Christmas Day'], ['2025-12-26', 'Boxing Day']
].each { UK_HOLIDAYS[LocalDate.parse(it[0])] = it[1] }
List<String> MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']
List<List> dates = [[-1, null, 'Unknown', null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null],
                    [0, null, 'Not applicable', null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null]]
for (LocalDate d = LocalDate.of(2020, 1, 1); d.isBefore(LocalDate.of(2026, 1, 1)); d = d.plusDays(1)) {
    int fm = (d.monthValue + 5) % 12 + 1                                      // July = fiscal month 1
    boolean weekend = d.dayOfWeek in [DayOfWeek.SATURDAY, DayOfWeek.SUNDAY]
    String holiday = UK_HOLIDAYS[d]
    dates << [d.year * 10000 + d.monthValue * 100 + d.dayOfMonth, java.sql.Date.valueOf(d), d.toString(), d.year, (d.monthValue + 2).intdiv(3), d.monthValue,
              MONTHS[d.monthValue - 1], d.year * 100 + d.monthValue, d.dayOfMonth, d.dayOfWeek.value, d.dayOfWeek.toString().toLowerCase().capitalize(), weekend,
              d.dayOfMonth == d.lengthOfMonth(), d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
              "FY${d.monthValue >= 7 ? d.year + 1 : d.year}".toString(), (fm + 2).intdiv(3), fm, holiday != null, holiday, !weekend && holiday == null]
}
insert('dim_date', ['date_key', 'full_date', 'date_label', 'year', 'quarter', 'month', 'month_name', 'year_month', 'day_of_month', 'day_of_week', 'day_name', 'is_weekend',
                    'is_month_end', 'iso_year', 'iso_week', 'fiscal_year', 'fiscal_quarter', 'fiscal_month', 'is_uk_holiday', 'holiday_name', 'is_working_day'], dates)

// ── 2. dim_customer: SCD2 on Segment and City, type 1 on the contact ────────────────────────
// The canonical spelling of each city: the one not typed in capitals, trimmed. Regions come from the source's own
// ship-to rows (the source fills Region only where the country has one).
Set<String> cleanCities = new TreeSet<>()
Map<String, String> regionOf = [:]
dbSql.eachRow("SELECT DISTINCT \"ShipCity\", \"ShipRegion\" FROM ${S('Orders')}".toString()) { r ->
    String c = (r.ShipCity as String).trim()
    if (c != c.toUpperCase()) cleanCities << c
    if (r.ShipRegion != null) regionOf[c] = r.ShipRegion as String
}
def cleanCity = { String c -> String t = c?.trim(); t == null ? null : (cleanCities.find { it.equalsIgnoreCase(t) } ?: t) }
def cleanName = { String n -> n == null || n != n.toLowerCase() ? n : n.split(' ').collect { it.capitalize() }.join(' ') }

Map<String, List<Map>> changesOf = [:].withDefault { [] }
dbSql.eachRow("SELECT \"CustomerID\", ${dateCol('"ChangedDate"')}, \"Attribute\", \"OldValue\", \"NewValue\" FROM ${S('CustomerChanges')} ORDER BY \"CustomerID\", \"ChangedDate\", \"CustomerChangeID\"".toString()) { r ->
    changesOf[r.CustomerID as String] << [date: day(r.ChangedDate), attr: r.Attribute as String, old: r.OldValue as String, new: r.NewValue as String]
}
List<List> customerRows = [[-1, 'UNKNOWN', 'Unknown customer', null, null, null, null, null, java.sql.Date.valueOf(OPEN_START), java.sql.Date.valueOf(OPEN_END), true]]
int customerKey = 0
dbSql.eachRow("SELECT \"CustomerID\", \"CompanyName\", \"ContactName\", \"City\", \"Country\", \"Segment\" FROM ${S('Customers')} ORDER BY \"CustomerID\"".toString()) { r ->
    List<Map> ch = changesOf[r.CustomerID as String]
    // Walk back to the first state, then forward through the changes that open a new row.
    Map state = [Segment: ch.find { it.attr == 'Segment' }?.old ?: r.Segment, City: ch.find { it.attr == 'City' }?.old ?: r.City]
    List<List> versions = [[OPEN_START, state.Segment, state.City]]
    ch.findAll { it.attr in ['Segment', 'City'] }.each { c ->
        state[c.attr] = c.new
        if (versions.last()[0] == c.date) versions[-1] = [c.date, state.Segment, state.City] else versions << [c.date, state.Segment, state.City]
    }
    versions.eachWithIndex { v, i ->
        LocalDate to = i + 1 < versions.size() ? versions[i + 1][0] as LocalDate : OPEN_END
        String city = cleanCity(v[2] as String)
        customerRows << [++customerKey, r.CustomerID, r.CompanyName, cleanName(r.ContactName as String), city, regionOf[city], r.Country, v[1],
                         java.sql.Date.valueOf(v[0] as LocalDate), java.sql.Date.valueOf(to), to == OPEN_END]
    }
}
insert('dim_customer', ['customer_key', 'customer_id', 'company_name', 'contact_name', 'city', 'region', 'country', 'segment', 'valid_from', 'valid_to', 'is_current'], customerRows)
customerRows = null

// ── 3. dim_category, dim_product: SCD2 on the list price ──────────────────────────────────────
insert('dim_category', ['category_key', 'category_name'],
       dbSql.rows("SELECT \"CategoryID\", \"CategoryName\" FROM ${S('Categories')} ORDER BY 1".toString()).collect { [it.CategoryID, it.CategoryName] })
Map<Integer, List<Map>> pricesOf = [:].withDefault { [] }
dbSql.eachRow("SELECT \"ProductID\", ${dateCol('"ChangedDate"')}, \"OldPrice\", \"NewPrice\" FROM ${S('PriceChanges')} ORDER BY \"ProductID\", \"ChangedDate\", \"PriceChangeID\"".toString()) { r ->
    pricesOf[r.ProductID as int] << [date: day(r.ChangedDate), old: r.OldPrice as BigDecimal, new: r.NewPrice as BigDecimal]
}
List<List> productRows = [[-1, -1, 'Unknown product', 0, 'Unknown', null, null, null, false, java.sql.Date.valueOf(OPEN_START), java.sql.Date.valueOf(OPEN_END), true]]
int productKey = 0
dbSql.eachRow("""SELECT p."ProductID", p."ProductName", p."CategoryID", c."CategoryName", s."CompanyName" AS supplier, s."Country" AS supplier_country, p."UnitPrice", p."Discontinued"
                 FROM ${S('Products')} p JOIN ${S('Categories')} c ON c."CategoryID" = p."CategoryID" LEFT JOIN ${S('Suppliers')} s ON s."SupplierID" = p."SupplierID"
                 ORDER BY p."ProductID"${CH ? ' SETTINGS join_use_nulls = 1' : ''}""".toString()) { r ->
    List<Map> ch = pricesOf[r.ProductID as int]
    List<List> versions = [[OPEN_START, ch ? ch[0].old : r.UnitPrice]]
    ch.each { c -> if (versions.last()[0] == c.date) versions[-1] = [c.date, c.new] else versions << [c.date, c.new] }
    versions.eachWithIndex { v, i ->
        LocalDate to = i + 1 < versions.size() ? versions[i + 1][0] as LocalDate : OPEN_END
        productRows << [++productKey, r.ProductID, r.ProductName, r.CategoryID, r.CategoryName, r.supplier, r.supplier_country, v[1], r.Discontinued,
                        java.sql.Date.valueOf(v[0] as LocalDate), java.sql.Date.valueOf(to), to == OPEN_END]
    }
}
insert('dim_product', ['product_key', 'product_id', 'product_name', 'category_key', 'category_name', 'supplier_name', 'supplier_country', 'list_price', 'discontinued',
                       'valid_from', 'valid_to', 'is_current'], productRows)

// ── 4. dim_employee, dim_shipper, the bridge ─────────────────────────────────────────────────
// Surrogate key = source key here: employees and shippers carry no history in the source, so nothing is gained by
// renumbering them, and a learner can check a join by eye.
List employees = dbSql.rows("SELECT \"EmployeeID\", \"FirstName\", \"LastName\", \"Title\", \"ReportsTo\" FROM ${S('Employees')} ORDER BY 1".toString())
Map<Integer, String> nameOf = employees.collectEntries { [(it.EmployeeID as int): "${it.FirstName} ${it.LastName}".toString()] }
insert('dim_employee', ['employee_key', 'employee_id', 'full_name', 'title', 'reports_to_key', 'manager_name'],
       employees.collect { [it.EmployeeID, it.EmployeeID, nameOf[it.EmployeeID as int], it.Title, it.ReportsTo, it.ReportsTo == null ? null : nameOf[it.ReportsTo as int]] })
insert('dim_shipper', ['shipper_key', 'shipper_id', 'company_name'],
       dbSql.rows("SELECT \"ShipperID\", \"CompanyName\" FROM ${S('Shippers')} ORDER BY 1".toString()).collect { [it.ShipperID, it.ShipperID, it.CompanyName] })
dbSql.execute("""INSERT INTO ${T('bridge_employee_territory')}
                 SELECT et."EmployeeID", et."TerritoryID", t."TerritoryDescription", r."RegionDescription",
                        CAST(1.0 / COUNT(*) OVER (PARTITION BY et."EmployeeID") AS DECIMAL(6,4))
                 FROM ${S('EmployeeTerritories')} et JOIN ${S('Territories')} t ON t."TerritoryID" = et."TerritoryID"
                 JOIN ${S('Region')} r ON r."RegionID" = t."RegionID\"""".toString())

// ── 5. the order facts, as of the cutoff ─────────────────────────────────────────────────────
// Staged once: each order keyed by the cutoff, with its status at the cutoff and its ship date if recorded by then.
dbSql.execute("""${ctas('_stage_orders')}
    WITH st AS (SELECT "OrderID", "Status", ROW_NUMBER() OVER (PARTITION BY "OrderID" ORDER BY "ChangedAt" DESC, "StatusChangeID" DESC) AS rn
                FROM ${S('OrderStatusHistory')} WHERE "ChangedAt" <= ${CUTOFF})
    SELECT o."OrderID" AS order_id, o."CustomerID" AS customer_id, o."EmployeeID" AS employee_id, o."ShipVia" AS shipper_id,
           o."OrderDate" AS order_date, o."RequiredDate" AS required_date, o."Channel" AS channel, o."Freight" AS freight,
           st."Status" AS order_status, CASE WHEN o."UpdatedAt" <= ${CUTOFF} THEN o."ShippedDate" END AS shipped_date
    FROM ${S('Orders')} o JOIN st ON st."OrderID" = o."OrderID"
    WHERE st.rn = 1 AND o."CreatedAt" <= ${CUTOFF}""".toString())
String shippedKey = "CASE WHEN so.shipped_date IS NOT NULL THEN ${dk('so.shipped_date')} WHEN so.order_status = 'Shipped' THEN -1 ELSE 0 END"
String orderJoins = """
    JOIN ${T('dim_customer')} c ON c.customer_id = so.customer_id"""
dbSql.execute("""INSERT INTO ${T('fact_order_line')}
    SELECT so.order_id, p.product_key, c.customer_key, so.employee_id, so.shipper_id, ${dk('so.order_date')}, ${dk('so.required_date')}, ${shippedKey},
           so.order_status, so.channel, d."Quantity", d."UnitPrice", d."Discount",
           CAST(ROUND(d."UnitPrice" * d."Quantity", 2) AS DECIMAL(19,2)), CAST(ROUND(d."UnitPrice" * d."Quantity" * (1 - d."Discount"), 2) AS DECIMAL(19,2))
    FROM ${T('_stage_orders')} so JOIN ${S('Order Details')} d ON d."OrderID" = so.order_id ${orderJoins}
    JOIN ${T('dim_product')} p ON p.product_id = d."ProductID"
    WHERE ${validOn('so.order_date', 'c')} AND ${validOn('so.order_date', 'p')}""".toString())
log.info("  fact_order_line: {} rows", count("SELECT COUNT(*) FROM ${T('fact_order_line')}"))
dbSql.execute("""INSERT INTO ${T('fact_order')}
    SELECT so.order_id, c.customer_key, so.employee_id, so.shipper_id, ${dk('so.order_date')}, ${dk('so.required_date')}, ${shippedKey},
           so.order_status, so.channel, l.line_count, l.net_amount, CAST(so.freight AS DECIMAL(19,2))
    FROM ${T('_stage_orders')} so ${orderJoins}
    JOIN (SELECT order_id, COUNT(*) AS line_count, SUM(net_amount) AS net_amount FROM ${T('fact_order_line')} GROUP BY order_id) l ON l.order_id = so.order_id
    WHERE ${validOn('so.order_date', 'c')}""".toString())
log.info("  fact_order: {} rows", count("SELECT COUNT(*) FROM ${T('fact_order')}"))

// ── 6. inventory month ends and sales targets ────────────────────────────────────────────────
// Stock on hand from the movement ledger, which starts at zero on 2020-01-01 and ends on Products.UnitsInStock (a
// check below proves the last month end does). The product row is the one valid at the month end.
dbSql.execute("""INSERT INTO ${T('fact_inventory_monthly')}
    WITH months AS (SELECT date_key, full_date, year_month FROM ${T('dim_date')} WHERE is_month_end AND full_date <= ${CH ? "toDate('2024-12-31')" : "DATE '2024-12-31'"}),
    mv AS (SELECT m."ProductID" AS product_id, dd.year_month AS year_month,
                  SUM(CASE WHEN m."MovementType" = 'Receipt' THEN m."Quantity" ELSE 0 END) AS received,
                  SUM(CASE WHEN m."MovementType" = 'Sale' THEN -m."Quantity" ELSE 0 END) AS sold,
                  SUM(CASE WHEN m."MovementType" = 'Return' THEN m."Quantity" ELSE 0 END) AS returned,
                  SUM(CASE WHEN m."MovementType" = 'Adjustment' THEN m."Quantity" ELSE 0 END) AS adjusted
           FROM ${S('StockMovements')} m JOIN ${T('dim_date')} dd ON dd.full_date = m."MovementDate" GROUP BY m."ProductID", dd.year_month),
    grid AS (SELECT p."ProductID" AS product_id, months.date_key AS date_key, months.full_date AS full_date, months.year_month AS year_month
             FROM ${S('Products')} p CROSS JOIN months)
    SELECT dp.product_key, g.date_key, COALESCE(mv.received, 0), COALESCE(mv.sold, 0), COALESCE(mv.returned, 0), COALESCE(mv.adjusted, 0),
           CAST(SUM(COALESCE(mv.received, 0) - COALESCE(mv.sold, 0) + COALESCE(mv.returned, 0) + COALESCE(mv.adjusted, 0))
                OVER (PARTITION BY g.product_id ORDER BY g.year_month ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS INTEGER)
    FROM grid g LEFT JOIN mv ON mv.product_id = g.product_id AND mv.year_month = g.year_month
    JOIN ${T('dim_product')} dp ON dp.product_id = g.product_id
    WHERE ${validOn('g.full_date', 'dp')}""".toString())
log.info("  fact_inventory_monthly: {} rows", count("SELECT COUNT(*) FROM ${T('fact_inventory_monthly')}"))
dbSql.execute("""INSERT INTO ${T('fact_sales_target')}
    SELECT "CategoryID", ${dk('"TargetMonth"')}, "TargetAmount" FROM ${S('SalesTargets')}""".toString())
log.info("  fact_sales_target: {} rows", count("SELECT COUNT(*) FROM ${T('fact_sales_target')}"))

// ── 7. one big table (M, L) ──────────────────────────────────────────────────────────────────
// The same lines with every dimension attribute copied onto each row, as they were at the order date. Its measures
// equal fact_order_line's line for line; what it cannot do is follow a dimension that changes later.
List<String> WIDE = ['fact_order_line_wide']
if (ONE_BIG_TABLE) {
    // Every column named with AS, so that ClickHouse names it as PostgreSQL and DuckDB do.
    dbSql.execute("""${ctas('fact_order_line_wide')}
        SELECT f.order_id AS order_id, od.full_date AS order_date, od.year AS order_year, od.month AS order_month, f.order_status AS order_status,
               f.channel AS channel, c.customer_id AS customer_id, c.company_name AS company_name, c.city AS customer_city, c.region AS customer_region,
               c.country AS customer_country, c.segment AS segment, p.product_id AS product_id, p.product_name AS product_name,
               p.category_name AS category_name, p.supplier_name AS supplier_name, e.full_name AS employee_name, s.company_name AS shipper_name,
               f.quantity AS quantity, f.unit_price AS unit_price, f.discount AS discount, f.gross_amount AS gross_amount, f.net_amount AS net_amount
        FROM ${T('fact_order_line')} f JOIN ${T('dim_date')} od ON od.date_key = f.order_date_key
        JOIN ${T('dim_customer')} c ON c.customer_key = f.customer_key JOIN ${T('dim_product')} p ON p.product_key = f.product_key
        JOIN ${T('dim_employee')} e ON e.employee_key = f.employee_key JOIN ${T('dim_shipper')} s ON s.shipper_key = f.shipper_key""".toString())
    log.info("  fact_order_line_wide: {} rows", count("SELECT COUNT(*) FROM ${T('fact_order_line_wide')}"))
}

// ── 8. the load audit ────────────────────────────────────────────────────────────────────────
dbSql.execute("""INSERT INTO ${T('_load_audit')}
    SELECT '${SRC}', ${SOURCE_VERSION}, ${CUTOFF},
           (SELECT COUNT(*) FROM ${T('_stage_orders')}),
           (SELECT COUNT(*) FROM ${S('Orders')} WHERE "CreatedAt" > ${CUTOFF}),
           (SELECT COUNT(*) FROM ${S('Orders')} WHERE "CreatedAt" <= ${CUTOFF} AND "UpdatedAt" > ${CUTOFF})""".toString())

// ── 9. reconciliation gates: the install fails rather than leave a warehouse that does not agree ─
Map<String, String> GATES = [
    'every line of every loaded order is on the fact (no dimension lookup dropped one)':
        """SELECT (SELECT COUNT(*) FROM ${T('_stage_orders')} so JOIN ${S('Order Details')} d ON d."OrderID" = so.order_id) - (SELECT COUNT(*) FROM ${T('fact_order_line')})""",
    'every loaded order is on fact_order':
        """SELECT (SELECT COUNT(*) FROM ${T('_stage_orders')}) - (SELECT COUNT(*) FROM ${T('fact_order')})""",
    'each line price equals the list price on its dim_product row (the SCD2 as-of join is right)':
        """SELECT COUNT(*) FROM ${T('fact_order_line')} f JOIN ${T('dim_product')} p ON p.product_key = f.product_key WHERE f.unit_price <> p.list_price""",
    'revenue of invoiced orders equals the invoices, to the cent':
        """SELECT ABS((SELECT SUM(i."Amount") FROM ${S('Invoices')} i JOIN ${T('_stage_orders')} so ON so.order_id = i."OrderID" WHERE so.shipped_date IS NOT NULL)
                   - (SELECT SUM(f.net_amount) FROM ${T('fact_order')} f JOIN ${T('_stage_orders')} so ON so.order_id = f.order_id WHERE so.shipped_date IS NOT NULL)) * 100""",
    'freight is counted once per order':
        """SELECT ABS((SELECT SUM(freight) FROM ${T('_stage_orders')}) - (SELECT SUM(freight) FROM ${T('fact_order')})) * 100""",
    'customer and product rows never overlap in time':
        """SELECT (SELECT COUNT(*) FROM ${T('dim_customer')} a JOIN ${T('dim_customer')} b ON a.customer_id = b.customer_id
                   WHERE a.customer_key < b.customer_key AND a.valid_to > b.valid_from AND b.valid_to > a.valid_from)
                + (SELECT COUNT(*) FROM ${T('dim_product')} a JOIN ${T('dim_product')} b ON a.product_id = b.product_id
                   WHERE a.product_key < b.product_key AND a.valid_to > b.valid_from AND b.valid_to > a.valid_from)""",
    'January 2025 is budgeted for every category (targets with no sales yet)':
        """SELECT (SELECT COUNT(*) FROM ${T('dim_category')}) - (SELECT COUNT(*) FROM ${T('fact_sales_target')} WHERE month_date_key = 20250101)""",
    'stock on hand at 2024-12-31 equals Products.UnitsInStock, and never goes below zero':
        """SELECT (SELECT COUNT(*) FROM ${T('fact_inventory_monthly')} f JOIN ${T('dim_product')} p ON p.product_key = f.product_key
                   JOIN ${S('Products')} sp ON sp."ProductID" = p.product_id WHERE f.month_end_date_key = 20241231 AND f.units_on_hand <> sp."UnitsInStock")
                + (SELECT COUNT(*) FROM ${T('fact_inventory_monthly')} WHERE units_on_hand < 0)""",
    'each rep\'s territory weights add up to 1':
        """SELECT COUNT(*) FROM (SELECT employee_key FROM ${T('bridge_employee_territory')} GROUP BY employee_key HAVING SUM(allocation_weight) <> 1) x""",
]
if (ONE_BIG_TABLE) GATES['the one big table holds the same lines and revenue'] =
    """SELECT (SELECT COUNT(*) FROM ${T('fact_order_line')}) - (SELECT COUNT(*) FROM ${T('fact_order_line_wide')})
            + ABS((SELECT SUM(net_amount) FROM ${T('fact_order_line')}) - (SELECT SUM(net_amount) FROM ${T('fact_order_line_wide')})) * 100"""
GATES.each { String what, String sql ->
    long off = count(sql)
    if (off != 0) throw new IllegalStateException("Warehouse check failed — ${what} (off by ${off}). Nothing is wrong with the source; this script has a bug.")
    log.info("  ok: {}", what)
}
dbSql.execute("DROP TABLE ${T('_stage_orders')}".toString())

// ── 10. _dataset_info: counts and checksums (the same canonical form and checksums as the source's) ──
// The M and L checksum. The same class sits in academy-northwind-co-install.groovy and academy-verify.groovy.
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
def eachRowStreamed = { String sql, Closure c ->
    if (vendor != 'POSTGRES') { dbSql.eachRow(sql, c); return }
    def conn = dbSql.connection
    boolean autoCommit = conn.autoCommit
    conn.autoCommit = false
    try { dbSql.withStatement { it.fetchSize = 10_000 }; dbSql.eachRow(sql, c) }
    finally { dbSql.withStatement { it.fetchSize = 0 }; conn.commit(); conn.autoCommit = autoCommit }
}
List<String> TABLES = ['dim_date', 'dim_customer', 'dim_category', 'dim_product', 'dim_employee', 'dim_shipper', 'bridge_employee_territory',
                       'fact_order_line', 'fact_order', 'fact_inventory_monthly', 'fact_sales_target', '_load_audit'] + (ONE_BIG_TABLE ? WIDE : [])
if (vendor == 'POSTGRES' && SCALE != 'S') TABLES.each { dbSql.execute("ANALYZE ${T(it)}".toString()) }
String infoDdl = "CREATE TABLE ${T('_dataset_info')} (\"Dataset\" VARCHAR(40), \"Version\" INTEGER, \"Scale\" VARCHAR(2), \"TableName\" VARCHAR(40), \"RowCount\" INTEGER, \"Checksum\" VARCHAR(64))".toString()
dbSql.execute(CH ? chDdl(infoDdl) : infoDdl)
// A table's columns for the checksum, sorted case-insensitively, as SELECT expressions. ClickHouse is asked for its
// dates, times and booleans as text (toString), which is the canonical form already.
def checksumColumns = { String table ->
    if (!CH) return dbSql.rows("SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?".toString(), [DW, table])
                         .collect { it.column_name as String }.sort { a, b -> a.compareToIgnoreCase(b) }.collect { q(it) }
    dbSql.rows("SELECT name, type FROM system.columns WHERE database = ? AND table = ?".toString(), [DW, table])
         .sort { a, b -> (a.name as String).compareToIgnoreCase(b.name as String) }
         .collect { (it.type as String) ==~ /^(Nullable\()?(Date|Date32|DateTime|Bool)\b.*/ ? "toString(${q(it.name as String)}) AS ${q(it.name as String)}".toString() : q(it.name as String) }
}
List<List> info = []
TABLES.each { table ->
    List<String> cols = checksumColumns(table)
    String select = "SELECT ${cols.join(', ')} FROM ${T(table)}".toString()
    String hex
    int n = 0
    if (SCALE == 'S') {
        List<String> rows = []
        dbSql.eachRow(select) { r -> rows << (1..cols.size()).collect { RowSum.canon(r.getObject(it)) }.join('\t') }
        rows.sort()
        n = rows.size()
        hex = MessageDigest.getInstance('SHA-256').digest(rows.join('\n').getBytes('UTF-8')).collect { String.format('%02x', it) }.join()
    } else {
        RowSum rs = new RowSum()
        eachRowStreamed(select) { r -> rs.add(r, cols.size()) }
        n = rs.count
        hex = rs.hex()
    }
    info << [DATASET, VERSION, SCALE, table, n, hex]
}
dbSql.withBatch(info.size(), "INSERT INTO ${T('_dataset_info')} VALUES (?, ?, ?, ?, ?, ?)".toString()) { ps -> info.each { ps.addBatch(it) } }

Map audit = dbSql.firstRow("SELECT * FROM ${T('_load_audit')}".toString())
log.info("=== {} v{} built from {}: {} orders loaded as of {}; {} keyed after the cutoff and {} changed after it are in the source only ===",
         DW, VERSION, SRC, audit.orders_loaded, LOAD_CUTOFF, audit.orders_keyed_after_cutoff, audit.orders_changed_after_cutoff)
