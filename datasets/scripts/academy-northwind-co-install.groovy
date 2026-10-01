// @description Academy dataset: installs "Northwind Company" (a simulated company, 2020-2024) into its own schema, at scale S, M or L. Set SCALE below. Idempotent — drops and recreates the schema.
// Bindings provided by GenericSeedExecutor:
//   dbSql  — groovy.sql.Sql connected to the target database
//   vendor — String (uppercase): POSTGRES, DUCKDB, CLICKHOUSE
//   log    — SLF4J Logger
//   params — Map; optional keys: SCALE, OUTAGE_WEEKS (26), OUTAGE_RATE (0.20) — the last two only to compare variants

import groovy.transform.CompileStatic
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ═════════════════════════════════════════════════════════════════════════════════════════════
// SCALE — THE ONE SETTING TO CHANGE. 'S', 'M' or 'L'. Each installs into its own schema (northwind_co_s, _m, _l),
// so all three can sit side by side. params.SCALE, when the Seed Data tab passes one, wins over this line.
// ═════════════════════════════════════════════════════════════════════════════════════════════
String SCALE = (params?.SCALE ?: 'S').toString().toUpperCase()

// THE SIZES OF EACH SCALE. The defaults suit a consumer laptop (8–16 GB RAM, an SSD). Times, disk and Java heap were
// measured on DuckDB on such a laptop (2026-09-19); PostgreSQL has not been measured at M or L yet (expect a slower
// load and several times the disk). The comments say what else is worth trying. Everything not listed here — growth, seasonality, customer skew, couriers, the Speedy Express
// outage and every other planted fact — is a RATE, so it comes out the same shape at every size.
Map<String, Map<String, Object>> SIZES = [

    // S — THE LESSONS' DATASET. Leave it alone: every published figure was measured on it, and _dataset_info pins its
    // checksums (datasets/README.md, "Rules for changing a relational dataset").
    // About 30 s to 2 min (plain INSERTs), ~13 MB on DuckDB, under 300 MB of Java heap.
    S: [ORDERS     : 10_000,
        CUSTOMERS  : 120,          JOINERS    : 25,       // JOINERS: customers who start buying after 2020 (cohorts)
        PRODUCTS   : 80,           SUPPLIERS  : 20,
        TERRITORIES: 30,
        ORG        : [1, 2, 9],                            // head count per level, CEO first; the last level are the reps
        STAFF      : true,                                 // PLANTED: operations and finance, six levels deep beside sales
        REPORTING_LOOP: true,                              // PLANTED: two interim managers who report to each other
        BULK_LOAD  : false],                               // S keeps the plain batched INSERTs it was published with

    // M — BIG ENOUGH TO FEEL A MISSING INDEX, SMALL ENOUGH TO RE-INSTALL OVER COFFEE. ~250,000 order lines.
    // A full scan takes a moment on PostgreSQL, an index makes it instant. About 1 min, ~60 MB on DuckDB, under 400 MB of heap.
    //   Try ORDERS: 250_000, or 500_000 with CUSTOMERS: 6_000 (the first plan's M); time and disk grow about in step
    //   with ORDERS (estimated, not measured).
    M: [ORDERS     : 100_000,
        CUSTOMERS  : 2_000,        JOINERS    : 400,
        PRODUCTS   : 200,          SUPPLIERS  : 50,
        TERRITORIES: 46,                                   // 46 = every city in the word lists; more are capped to it
        ORG        : [1, 3, 8, 28],                        // 40 people, 4 levels
        BULK_LOAD  : true],                                // rows are streamed to a CSV file and loaded with COPY

    // L — THE PERFORMANCE LESSONS' DATASET. ~2.5 million order lines: a bad query plan is seconds, not milliseconds,
    // and a good one is still instant. Nothing here blocks a laptop: 2–3 min, ~450 MB on DuckDB, under 400 MB of heap.
    //   Try ORDERS: 5_000_000 with CUSTOMERS: 100_000 and PRODUCTS: 1_000 (the first plan's L): ~12.5M lines,
    //   roughly 5× the time and disk (estimated, not measured), and PostgreSQL scans that take tens of seconds.
    //   Give PostgreSQL room first (several GB) and expect the install to take a while.
    //   Try ORDERS: 2_000_000 for a middle ground.
    L: [ORDERS     : 1_000_000,
        CUSTOMERS  : 20_000,       JOINERS    : 4_000,
        PRODUCTS   : 400,          SUPPLIERS  : 100,      // PRODUCTS can go up to 1,280 (160 names per category)
        TERRITORIES: 46,
        ORG        : [1, 3, 6, 12, 24, 74],                // 120 people, 6 levels (recursive CTEs have depth to walk)
        REPORTING_LOOP: true,                              // PLANTED: two interim managers who report to each other
        BULK_LOAD  : true],
]
// ═════════════════════════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────────────────────
// WHAT THIS IS
// The DataZeus academy's second relational dataset (the first, Northwind, is FROZEN). One simulated
// company with Northwind's own table names, plus what the data courses need: invoices, stock
// movements, sales targets, dated customer and price changes, courier confirmations, order status
// and an updated-at column. Design: kraft-src-company-biz/.docs/plan-academy-datasets.md.
// Rules every change to this file must follow: the academy's datasets/README.md,
// "Rules for changing a relational dataset" — in short:
//   1. what a published lesson shows never moves: its rows and the results of its queries stay as they are;
//   2. evolve by ADDING tables and columns;
//   3. ONE RANDOM STREAM PER TABLE (rnd('Orders') …), so adding a table never shifts another's rows;
//   4. no faker libraries — every name comes from the word lists in this file;
//   5. money is BigDecimal end to end;
//   6. every install writes per-table row counts and checksums to _dataset_info.
// Planted facts are marked PLANTED, additions for later courses EXTENSION; each names who needs it.
//
// THE THREE SCALES are one company generated three times, not S copied: each scale seeds its own random streams
// (the SCALE is part of every seed), so M is not "S plus more rows" and its figures are its own. Lessons quote
// figures from S only; M and L are for timing, plans and volume. The code below is written so that S draws exactly
// the random numbers it always drew — a change here must leave every value a lesson reads unchanged (install S on a
// scratch DuckDB file and compare it, column by column, with datasets/northwind-co/northwind_co.duckdb). The one
// deliberate break so far (2026-09-19): CustomerChanges.ChangedDate re-sorted and Orders.ShipCity/ShipRegion made
// as-of, both read by no published lesson.
// M and L stream their rows to the database as they are made and keep only what later steps need, so L fits in
// about 1 GB of Java heap. Their checksums are order-independent sums (below), because sorting millions of rows in
// memory would not fit.
//
// WHO NEEDS WHAT — an index, so nothing here is removed as "unused" (search the tag to find the code):
//   PLANTED   Learn SQL S2 · 00–60   the Speedy Express recording outage, invoices only for recorded ship dates,
//                                    courier confirmations (+ noise), phantom sales (cancelled, never updated)
//   PLANTED   Learn SQL S2 · 35      a price tie (products 4 and 12)
//   PLANTED   Learn SQL S2 · 40      messy phones, stray spaces and case variants in cities and contact names
//   PLANTED   Learn SQL S2 · 48      courier confirmations re-sent after a failed hand-over (UNION drops them,
//                                    UNION ALL does not)
//   PLANTED   Learn SQL S2 · 50      "Region" filled only for USA, Canada and Brazil (a real NULL beside ROLLUP's)
//   PLANTED   Learn SQL S2 · 58      a heavy tail in "Freight" (pallets, islands, re-deliveries) and in days to pay
//                                    (a few invoices sit for months), so AVG sits well above the typical bill
//   (natural) Learn SQL S2 · 45      months with no orders for small countries (LAG is the previous ROW)
//   EXTENSION Learn SQL S3 · 05      "WebOrders"."Payload": raw JSON with defects (missing key, number as text,
//                                    decimal comma)
//   EXTENSION Learn SQL S3 · 15      customers who join and customers who stop (cohorts)
//   PLANTED   Learn SQL S3 · 10      "Employees" six levels deep at S (operations and finance beside sales, which is
//                                    three), and at S and L a reporting loop (a recursive CTE that must stop)
//   EXTENSION Data Modeling S3       "StockMovements", "SalesTargets" (January 2025 budgeted, no sales yet),
//                                    "EmployeeTerritories" (a bridge), dated "CustomerChanges"/"PriceChanges" (SCD)
//   EXTENSION Data Warehousing S1    "Orders"."CreatedAt" (~1.5% keyed in late: late-arriving facts), cancelled
//                                    orders, the order number on every line
//   PLANTED   Data Warehousing S1·22 "OrderStatusHistory": one order in 10,000 cancelled and then reinstated late in
//                                    2024 (a reconciliation cause both sides can show)
//   EXTENSION Data Warehousing S3    dated customer and price history (SCD2 as-of joins): each customer's changes
//                                    dated in the order they chain in; "Orders"."ShipCity" as of the order date;
//                                    five years (hot and cold)
//   EXTENSION ETL S1–S2              "Orders"."UpdatedAt" and "CreatedAt" (watermarks, incremental loads);
//                                    "WebOrders" (a JSON source); names, emails, phones (personal data), and the ones
//                                    people miss: "Orders"."DeliveryNotes" (free text, some with a name or phone),
//                                    "WebOrders"."ClientIP"
//   PLANTED   ETL S2 · 20            "Customers"."CreatedAt": a few web-shop customers created 1–3 days after their
//                                    first order (a late dimension, an inferred member)
//   EXTENSION dbt S1–S2              "CreatedAt"/"UpdatedAt" (freshness, incremental models); "Customers" and
//                                    "Products"."UpdatedAt" and the history tables (snapshots); ordered vs invoiced
//                                    revenue (one metric definition)
//   EXTENSION analytics, all         "Orders"."Channel" drifting from sales reps to the web shop (a trend)
// NOT HERE, on purpose: test orders (every published order count would move) and a separate account manager per
// customer ("Orders"."EmployeeID" follows each customer's rep; a second, diverging owner would contradict it).
// NOT in this script, by design — their own scripts: the change feed (academy-northwind-co-changes), the CRM export
// (academy-crm-export-install), the file exports (academy-files-export), the star schema (academy-northwind-co-dw-
// install), the landed all-text copy (academy-northwind-co-raw-install).
// ENGINES: PostgreSQL, DuckDB and ClickHouse. ClickHouse is for the analytics series, beside DuckDB (which series use
// which pair: _datazeus/tests DatasetsSpec, the engine-pair checks). On ClickHouse the schema is a database and every
// table a MergeTree ordered by its primary key; the rows and the checksums are meant to be the same as on the other two.
// KEYS AND INDEXES: a primary key on every table at every scale; at S, an index on each column the lessons join on
// (JOIN INDEXES, below); at M and L, primary keys only, on purpose: the performance lessons (Learn SQL S3 · 18–30) start
// from sequential scans and add every index themselves, on L or on M where L is too large for a laptop, and the same
// indexes sit there commented, each with what it would change. NO FOREIGN KEYS: they would fix a load order and a drop
// order.
// ─────────────────────────────────────────────────────────────────────────────────────────────

if (!SIZES.containsKey(SCALE)) throw new IllegalArgumentException("SCALE must be S, M or L (got ${SCALE}).")
if (!(vendor in ['POSTGRES', 'DUCKDB', 'CLICKHOUSE'])) throw new IllegalArgumentException("Only PostgreSQL, DuckDB and ClickHouse are supported (connection is ${vendor}).")
Map SIZE = SIZES[SCALE]

String DATASET = 'northwind_co'
String SCHEMA  = "${DATASET}_${SCALE.toLowerCase()}".toString()
LocalDate FIRST_DAY = LocalDate.of(2020, 1, 1)
LocalDate LAST_DAY  = LocalDate.of(2024, 12, 31)          // "today" in every lesson

int ORDERS = SIZE.ORDERS as int, CUSTOMERS = SIZE.CUSTOMERS as int, JOINERS = SIZE.JOINERS as int
int PRODUCTS = SIZE.PRODUCTS as int, SUPPLIERS = SIZE.SUPPLIERS as int, TERRITORIES = SIZE.TERRITORIES as int
List<Integer> ORG = SIZE.ORG as List<Integer>
boolean STAFF = SIZE.STAFF as boolean, REPORTING_LOOP = SIZE.REPORTING_LOOP as boolean, BULK_LOAD = SIZE.BULK_LOAD as boolean
int TERRITORIES_PER_REP = 5
int CUSTOMER_ID_LENGTH = SCALE == 'S' ? 5 : 8             // Northwind's 5 letters run out past a few hundred customers
assert PRODUCTS >= 12 && PRODUCTS <= 1280 : 'PRODUCTS must be 12..1,280 (the price tie needs products 4 and 12; 160 names per category)'
assert ORG.size() >= 3 && ORG.size() <= 6 && ORG[0] == 1 : 'ORG: 3 to 6 levels, one CEO at the top'
// 10% growth a year: ORDERS split over 2020..2024 in the ratio 1 : 1.1 : 1.21 : 1.331 : 1.4641 (S: 1638 … 2398).
double growth = (0..4).collect { Math.pow(1.1, it) }.sum() as double
int[] ORDERS_PER_YEAR = (0..4).collect { Math.round(ORDERS * Math.pow(1.1, it) / growth) as int } as int[]
ORDERS_PER_YEAR[4] = ORDERS - (ORDERS_PER_YEAR[0..3].sum() as int)
if (SCALE == 'S') assert ORDERS_PER_YEAR as List == [1638, 1802, 1982, 2180, 2398]
double QTY_BUDGET = 1100                                   // tunes the average order value (target ≈ €1,500)
double PHANTOM_RATE = 0.024                                // ≈ 40 cancelled-but-still-open orders in 2024 at S (S2 · 60)

log.info("=== Academy dataset {} scale {} → schema {} on {}: {} orders, {} customers, {} products ===",
         DATASET, SCALE, SCHEMA, vendor, ORDERS, CUSTOMERS, PRODUCTS)

// ── deterministic helpers ────────────────────────────────────────────────────────────────────
// The |1| is a fixed part of every seed: every row, checksum and quoted figure is built from it, so it never changes.
def rnd = { String table -> new Random(("${DATASET}|1|${SCALE}|${table}".toString()).hashCode() * 2654435761L) }
def money = { double v -> new BigDecimal(v).setScale(2, RoundingMode.HALF_UP) }
def pick = { Random r, List xs -> xs[r.nextInt(xs.size())] }
def ts = { LocalDate d -> Timestamp.valueOf(d.atStartOfDay()) }
def weighted = { Random r, List<Double> w ->
    double t = w.sum() as double, x = r.nextDouble() * t, acc = 0
    for (int i = 0; i < w.size(); i++) { acc += w[i]; if (x < acc) return i }
    return w.size() - 1
}
def q = { String name -> "\"${name}\"".toString() }
def T = { String table -> "${SCHEMA}.\"${table}\"".toString() }

// The same pick as `weighted`, for long lists drawn from many times: the running totals are added up once (in the
// same order, so to the same doubles) and searched by halving. It draws the same number and returns the same index.
@CompileStatic
class Cumulative {
    final double[] sums
    Cumulative(List<Double> w) { sums = new double[w.size()]; double acc = 0; for (int i = 0; i < sums.length; i++) { acc += w[i]; sums[i] = acc } }
    int pick(Random r) {
        double x = r.nextDouble() * sums[sums.length - 1]
        int lo = 0, hi = sums.length - 1
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (x < sums[mid]) hi = mid; else lo = mid + 1 }
        lo
    }
}

// A growable array of ints (stock movements at scale L are millions of rows; boxed lists would not fit).
@CompileStatic
class Ints {
    int[] a = new int[1 << 16]; int n = 0
    void add(int v) { if (n == a.length) a = Arrays.copyOf(a, n * 2); a[n++] = v }
    int get(int i) { a[i] }
}

// The M and L checksum: the sum of every row's SHA-256, so row order does not matter and nothing is sorted or held.
// Compiled rather than dynamic because L hashes about 7 million rows. canon() is section 7's canonical form; the
// same class sits in academy-verify.groovy, and the two must stay identical.
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

// ── word lists (no faker) ────────────────────────────────────────────────────────────────────
List<List<String>> COUNTRIES = [
    // country, cities…, postal pattern, phone prefix
    ['Germany', 'Berlin', 'Hamburg', 'Munich', 'Cologne', '#####', '+49'],
    ['France', 'Paris', 'Lyon', 'Marseille', '#####', '+33'],
    ['United Kingdom', 'London', 'Manchester', 'Bristol', 'AA# #AA', '+44'],
    ['Spain', 'Madrid', 'Barcelona', 'Seville', '#####', '+34'],
    ['Italy', 'Milan', 'Rome', 'Turin', '#####', '+39'],
    ['Netherlands', 'Amsterdam', 'Rotterdam', '#### AA', '+31'],
    ['Belgium', 'Brussels', 'Antwerp', '####', '+32'],
    ['Austria', 'Vienna', 'Graz', '####', '+43'],
    ['Switzerland', 'Zurich', 'Geneva', '####', '+41'],
    ['Sweden', 'Stockholm', 'Gothenburg', '### ##', '+46'],
    ['Denmark', 'Copenhagen', 'Aarhus', '####', '+45'],
    ['Norway', 'Oslo', 'Bergen', '####', '+47'],
    ['Finland', 'Helsinki', 'Tampere', '#####', '+358'],
    ['Poland', 'Warsaw', 'Krakow', '##-###', '+48'],
    ['Portugal', 'Lisbon', 'Porto', '####-###', '+351'],
    ['Ireland', 'Dublin', 'Cork', 'A## A#A#', '+353'],
    ['USA', 'Seattle', 'Portland', 'Boston', '#####', '+1'],
    ['Canada', 'Montreal', 'Vancouver', 'A#A #A#', '+1'],
    ['Mexico', 'Mexico City', 'Guadalajara', '#####', '+52'],
    ['Brazil', 'Sao Paulo', 'Rio de Janeiro', '#####-###', '+55'],
    ['Argentina', 'Buenos Aires', 'Cordoba', 'A####AAA', '+54'],
]
List<String> NAME_A = ['Alpine', 'Baltic', 'Coastal', 'Delta', 'Emerald', 'Fjord', 'Golden', 'Harbor', 'Iberian', 'Juniper',
                       'Kestrel', 'Lakeside', 'Meadow', 'Nordic', 'Olive', 'Pampas', 'Quayside', 'Riverside', 'Saffron',
                       'Tundra', 'Upland', 'Valley', 'Willow', 'Yarrow', 'Zenith', 'Amber', 'Bramble', 'Cedar', 'Dune', 'Elm']
List<String> NAME_B = ['Foods', 'Market', 'Delicatessen', 'Grocers', 'Provisions', 'Trading', 'Kitchen', 'Pantry',
                       'Supermarket', 'Traders', 'Wholefoods', 'Fine Foods', 'Food Hall', 'Stores']
List<String> FIRST = ['Anna', 'Bruno', 'Carla', 'David', 'Elena', 'Felix', 'Greta', 'Hugo', 'Ines', 'Jonas', 'Klara', 'Lukas',
                      'Maria', 'Nils', 'Olga', 'Pablo', 'Rosa', 'Sven', 'Tessa', 'Umberto', 'Vera', 'Wim', 'Yara', 'Zoe',
                      'Liam', 'Maya', 'Omar', 'Paula', 'Ravi', 'Sofia']
List<String> LAST = ['Andersen', 'Bauer', 'Costa', 'Dubois', 'Eriksen', 'Fischer', 'Garcia', 'Hansen', 'Ivanova', 'Jansen',
                     'Keller', 'Lindqvist', 'Moreau', 'Novak', 'Olsen', 'Petrov', 'Quinn', 'Rossi', 'Schmidt', 'Torres',
                     'Ueda', 'Vargas', 'Weber', 'Young', 'Zeller', 'Moreno', 'Nakamura', 'Okafor', 'Silva', 'Walsh']
List<String> TITLES = ['Owner', 'Purchasing Manager', 'Sales Representative', 'Accounting Manager', 'Marketing Manager',
                       'Order Administrator', 'Sales Agent']
List<String> STREETS = ['Market Street', 'Harbour Road', 'Station Avenue', 'Mill Lane', 'Church Street', 'Park Road',
                        'King Street', 'Bridge Road', 'High Street', 'Garden Walk']
List<List> CATEGORIES = [
    [1, 'Beverages', 'Soft drinks, coffees, teas, beers, and ales', 6.0, 40.0],
    [2, 'Condiments', 'Sweet and savory sauces, relishes, spreads, and seasonings', 8.0, 45.0],
    [3, 'Confections', 'Desserts, candies, and sweet breads', 7.0, 50.0],
    [4, 'Dairy Products', 'Cheeses', 9.0, 60.0],
    [5, 'Grains/Cereals', 'Breads, crackers, pasta, and cereal', 6.0, 35.0],
    [6, 'Meat/Poultry', 'Prepared meats', 18.0, 95.0],
    [7, 'Produce', 'Dried fruit and bean curd', 8.0, 55.0],
    [8, 'Seafood', 'Seaweed and fish', 10.0, 70.0],
]
List<String> PRODUCT_WORDS = ['Alpine', 'Classic', 'Coastal', 'Golden', 'Harvest', 'Heritage', 'Highland', 'Island', 'Meadow',
                              'Mountain', 'Nordic', 'Old Town', 'Orchard', 'Riverside', 'Royal', 'Rustic', 'Smoked', 'Spiced',
                              'Stone', 'Valley']
Map<Integer, List<String>> PRODUCT_NOUNS = [
    1: ['Coffee', 'Tea', 'Lager', 'Ale', 'Cola', 'Lemonade', 'Cider', 'Tonic'],
    2: ['Mustard', 'Relish', 'Chutney', 'Ketchup', 'Pesto', 'Hot Sauce', 'Syrup', 'Vinegar'],
    3: ['Chocolate', 'Biscuits', 'Fudge', 'Marzipan', 'Toffee', 'Gingerbread', 'Nougat', 'Waffles'],
    4: ['Cheddar', 'Brie', 'Gouda', 'Feta', 'Parmesan', 'Camembert', 'Butter', 'Yoghurt'],
    5: ['Rye Bread', 'Crispbread', 'Pasta', 'Granola', 'Couscous', 'Oat Flakes', 'Crackers', 'Gnocchi'],
    6: ['Sausages', 'Ham', 'Pate', 'Salami', 'Bacon', 'Meatballs', 'Duck Confit', 'Chicken Pie'],
    7: ['Dried Apricots', 'Tofu', 'Figs', 'Raisins', 'Dried Pears', 'Beans', 'Olives', 'Tomatoes'],
    8: ['Herring', 'Salmon', 'Crab Meat', 'Mussels', 'Kelp', 'Anchovies', 'Caviar', 'Shrimp'],
]
List<String> PACKS = ['10 boxes x 20 bags', '24 - 12 oz bottles', '12 - 550 ml bottles', '48 pieces', '20 - 1 kg tins',
                      '12 - 200 ml jars', '36 boxes', '10 - 500 g pkgs.', '24 - 250 g pkgs.', '5 kg pkg.']

String postal(Random r, String pattern) {
    StringBuilder sb = new StringBuilder()
    for (char c : pattern.toCharArray()) {
        if (c == '#' as char) sb.append(r.nextInt(10))
        else if (c == 'A' as char) sb.append((char) (65 + r.nextInt(26)))
        else sb.append(c)
    }
    sb.toString()
}
String phone(Random r, String prefix) { "${prefix} ${100 + r.nextInt(900)} ${1000 + r.nextInt(9000)}".toString() }
// PLANTED — Learn SQL S2 · 50: "Region" is filled only where the country uses one, so a real NULL region sits beside
// the subtotal NULL that ROLLUP makes. Everywhere else it is NULL.
Map<String, String> REGION_OF_CITY = ['Seattle': 'WA', 'Portland': 'OR', 'Boston': 'MA', 'Montreal': 'QC', 'Vancouver': 'BC',
                                      'Sao Paulo': 'SP', 'Rio de Janeiro': 'RJ']
// The city a row carries may be untidy (S2 · 40 below plants that), so Region is looked up on a tidied key.
// Without this, SHOUTED city would silently lose its region and the S2 · 50 trap would stop being about real NULLs.
Map<String, String> REGION_OF_CITY_KEY = REGION_OF_CITY.collectEntries { k, v -> [(k.toLowerCase()): v] }
def regionOfCity = { String city -> city == null ? null : REGION_OF_CITY_KEY[city.trim().toLowerCase()] }
// The cities of a country OTHER than the one a customer is in — the candidates when they relocate. Matched on a
// tidied key for the same reason: a customer sitting in "BRUSSELS" must still be moved to Antwerp, not "corrected"
// to Brussels. A City row in CustomerChanges is a MOVE, and the SCD lessons downstream depend on that.
def otherCities = { String country, String current ->
    List row = COUNTRIES.find { it[0] == country }
    String key = current == null ? '' : current.trim().toLowerCase()
    row == null ? [] : row.subList(1, row.size() - 2).findAll { (it as String).toLowerCase() != key }
}
// PLANTED — Learn SQL S2 · 40: phone numbers arrive in the formats people actually type.
String messyPhone(Random r, String prefix) {
    String a = String.valueOf(100 + r.nextInt(900)), b = String.valueOf(1000 + r.nextInt(9000))
    String bare = prefix.replace('+', '')
    switch (r.nextInt(5)) {
        case 0: return "${prefix} ${a} ${b}".toString()
        case 1: return "(${bare}) ${a}-${b}".toString()
        case 2: return "00${bare}.${a}.${b}".toString()
        case 3: return "${a} ${b}".toString()
        default: return "${prefix}-${a}${b}".toString()
    }
}


// Only when NAME_A × NAME_B runs out (it holds 420 names; S uses 120): "Alpine Foods Brothers". M and L need them.
List<String> NAME_C = ['and Sons', 'Brothers', 'Group', 'Co-op', 'Direct', 'Express', 'Imports', 'Partners', 'Company',
                       'Collective', 'Europe', 'International', 'Wholesale', 'Outlet', 'Corner', 'Depot', 'Exchange',
                       'Gallery', 'House', 'Hub', 'Larder', 'Mill', 'Place', 'Quarter', 'Square', 'Station', 'Works',
                       'Yard', 'North', 'South', 'East', 'West', 'Central', 'City', 'Village', 'Harbour', 'Bay',
                       'Hill', 'Park', 'Green', 'Bridge', 'Gate', 'Cross', 'Row', 'Lane', 'Court', 'Hall', 'Plaza',
                       'Arcade', 'Terrace', 'Halle', 'Markt', 'Mercado', 'Marche', 'Mercato', 'Torget', 'Tori',
                       'Rynek', 'Mercado Sul', 'Markthal']

// ── ClickHouse ──────────────────────────────────────────────────────────────────────────────
// The tables are written once, in the DDL PostgreSQL and DuckDB share, and translated for ClickHouse: VARCHAR → String,
// INTEGER → Int32, TIMESTAMP → DateTime, a column that may be NULL → Nullable(…), and ENGINE = MergeTree ordered by the
// primary key (ClickHouse keeps no unique keys; ordering is what its primary key does). A DATE before 1970 needs
// Date32 (BirthDate). ClickHouse has no transactions, so nothing here commits or rolls back.
boolean CH = vendor == 'CLICKHOUSE'
Set<String> CH_WIDE_DATES = ['BirthDate'] as Set
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
// Dates and times go to ClickHouse as text in its own format, which it stores as that same wall-clock value on any
// server; a java.sql.Timestamp would pass through the driver's time zone first.
DateTimeFormatter CH_TS = DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss')
def chValue = { v ->
    if (v instanceof Timestamp) return (v as Timestamp).toLocalDateTime().format(CH_TS)
    if (v instanceof LocalDateTime) return (v as LocalDateTime).format(CH_TS)
    if (v instanceof java.sql.Date || v instanceof LocalDate) return v.toString()
    if (v instanceof GString) return v.toString()
    return v
}

// ── 1. schema ────────────────────────────────────────────────────────────────────────────────
if (CH) {
    dbSql.execute("DROP DATABASE IF EXISTS ${SCHEMA} SYNC".toString())
    dbSql.execute("CREATE DATABASE ${SCHEMA}".toString())
} else {
    dbSql.execute("DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE".toString())
    dbSql.execute("CREATE SCHEMA ${SCHEMA}".toString())
}

int ID = CUSTOMER_ID_LENGTH
String DDL = """
CREATE TABLE ${T('Region')} ("RegionID" INTEGER PRIMARY KEY, "RegionDescription" VARCHAR(50) NOT NULL);
CREATE TABLE ${T('Territories')} ("TerritoryID" VARCHAR(20) PRIMARY KEY, "TerritoryDescription" VARCHAR(50) NOT NULL, "RegionID" INTEGER NOT NULL);
CREATE TABLE ${T('Categories')} ("CategoryID" INTEGER PRIMARY KEY, "CategoryName" VARCHAR(15) NOT NULL, "Description" VARCHAR(200));
CREATE TABLE ${T('Suppliers')} ("SupplierID" INTEGER PRIMARY KEY, "CompanyName" VARCHAR(40) NOT NULL, "ContactName" VARCHAR(30), "ContactTitle" VARCHAR(30), "Address" VARCHAR(60), "City" VARCHAR(15), "Region" VARCHAR(15), "PostalCode" VARCHAR(10), "Country" VARCHAR(15), "Phone" VARCHAR(24), "Fax" VARCHAR(24), "HomePage" VARCHAR(100), "Email" VARCHAR(100));
CREATE TABLE ${T('Products')} ("ProductID" INTEGER PRIMARY KEY, "ProductName" VARCHAR(40) NOT NULL, "SupplierID" INTEGER, "CategoryID" INTEGER, "QuantityPerUnit" VARCHAR(20), "UnitPrice" DECIMAL(19,4), "UnitCost" DECIMAL(19,4), "UnitsInStock" SMALLINT, "UnitsOnOrder" SMALLINT, "ReorderLevel" SMALLINT, "Discontinued" BOOLEAN NOT NULL, "UpdatedAt" TIMESTAMP NOT NULL);
CREATE TABLE ${T('PriceChanges')} ("PriceChangeID" INTEGER PRIMARY KEY, "ProductID" INTEGER NOT NULL, "ChangedDate" DATE NOT NULL, "OldPrice" DECIMAL(19,4) NOT NULL, "NewPrice" DECIMAL(19,4) NOT NULL);
CREATE TABLE ${T('Shippers')} ("ShipperID" INTEGER PRIMARY KEY, "CompanyName" VARCHAR(40) NOT NULL, "Phone" VARCHAR(24));
CREATE TABLE ${T('Employees')} ("EmployeeID" INTEGER PRIMARY KEY, "LastName" VARCHAR(20) NOT NULL, "FirstName" VARCHAR(10) NOT NULL, "Title" VARCHAR(30), "TitleOfCourtesy" VARCHAR(25), "BirthDate" DATE, "HireDate" DATE, "Address" VARCHAR(60), "City" VARCHAR(15), "Region" VARCHAR(15), "PostalCode" VARCHAR(10), "Country" VARCHAR(15), "HomePhone" VARCHAR(24), "Extension" VARCHAR(4), "Notes" VARCHAR(400), "ReportsTo" INTEGER, "Email" VARCHAR(100), "CommissionRate" DECIMAL(5,4));
CREATE TABLE ${T('EmployeeTerritories')} ("EmployeeID" INTEGER NOT NULL, "TerritoryID" VARCHAR(20) NOT NULL, PRIMARY KEY ("EmployeeID", "TerritoryID"));
CREATE TABLE ${T('Customers')} ("CustomerID" VARCHAR(${ID}) PRIMARY KEY, "CompanyName" VARCHAR(40) NOT NULL, "ContactName" VARCHAR(30), "ContactTitle" VARCHAR(30), "Address" VARCHAR(60), "City" VARCHAR(15), "Region" VARCHAR(15), "PostalCode" VARCHAR(10), "Country" VARCHAR(15), "Phone" VARCHAR(24), "Fax" VARCHAR(24), "Email" VARCHAR(100), "Segment" VARCHAR(20), "CreatedAt" TIMESTAMP NOT NULL, "UpdatedAt" TIMESTAMP NOT NULL);
CREATE TABLE ${T('CustomerChanges')} ("CustomerChangeID" INTEGER PRIMARY KEY, "CustomerID" VARCHAR(${ID}) NOT NULL, "ChangedDate" DATE NOT NULL, "Attribute" VARCHAR(20) NOT NULL, "OldValue" VARCHAR(40), "NewValue" VARCHAR(40));
CREATE TABLE ${T('Orders')} ("OrderID" INTEGER PRIMARY KEY, "CustomerID" VARCHAR(${ID}) NOT NULL, "EmployeeID" INTEGER NOT NULL, "OrderDate" TIMESTAMP NOT NULL, "RequiredDate" TIMESTAMP, "ShippedDate" TIMESTAMP, "ShipVia" INTEGER NOT NULL, "Freight" DECIMAL(19,4), "ShipName" VARCHAR(40), "ShipAddress" VARCHAR(60), "ShipCity" VARCHAR(15), "ShipRegion" VARCHAR(15), "ShipPostalCode" VARCHAR(10), "ShipCountry" VARCHAR(15), "Status" VARCHAR(10) NOT NULL, "UpdatedAt" TIMESTAMP NOT NULL, "CreatedAt" TIMESTAMP NOT NULL, "Channel" VARCHAR(10) NOT NULL, "DeliveryNotes" VARCHAR(200));
CREATE TABLE ${T('WebOrders')} ("WebOrderID" INTEGER PRIMARY KEY, "OrderID" INTEGER NOT NULL, "ReceivedAt" TIMESTAMP NOT NULL, "Payload" VARCHAR(2000) NOT NULL, "ClientIP" VARCHAR(45) NOT NULL);
CREATE TABLE ${T('OrderStatusHistory')} ("StatusChangeID" INTEGER PRIMARY KEY, "OrderID" INTEGER NOT NULL, "Status" VARCHAR(10) NOT NULL, "ChangedAt" TIMESTAMP NOT NULL);
CREATE TABLE ${T('Order Details')} ("OrderID" INTEGER NOT NULL, "ProductID" INTEGER NOT NULL, "UnitPrice" DECIMAL(19,4) NOT NULL, "Quantity" SMALLINT NOT NULL, "Discount" DECIMAL(8,4) NOT NULL, PRIMARY KEY ("OrderID", "ProductID"));
CREATE TABLE ${T('Invoices')} ("InvoiceID" INTEGER PRIMARY KEY, "OrderID" INTEGER NOT NULL, "InvoiceDate" DATE NOT NULL, "Amount" DECIMAL(19,2) NOT NULL, "Freight" DECIMAL(19,2) NOT NULL, "PaidDate" DATE);
CREATE TABLE ${T('StockMovements')} ("MovementID" INTEGER PRIMARY KEY, "ProductID" INTEGER NOT NULL, "MovementDate" DATE NOT NULL, "MovementType" VARCHAR(12) NOT NULL, "Quantity" INTEGER NOT NULL, "OrderID" INTEGER);
CREATE TABLE ${T('SalesTargets')} ("CategoryID" INTEGER NOT NULL, "TargetMonth" DATE NOT NULL, "TargetAmount" DECIMAL(19,2) NOT NULL, PRIMARY KEY ("CategoryID", "TargetMonth"));
CREATE TABLE ${T('CourierConfirmations')} ("ConfirmationID" INTEGER PRIMARY KEY, "ShipperID" INTEGER NOT NULL, "OrderID" INTEGER NOT NULL, "DeliveredDate" DATE NOT NULL, "ReceivedDate" DATE NOT NULL);
"""
DDL.split(';').collect { it.trim() }.findAll { it }.each { dbSql.execute(CH ? chDdl(it) : it) }

// ── loading rows ────────────────────────────────────────────────────────────────────────────
// A SINK takes a table's rows one at a time, in order, and never holds more than a chunk of them:
//   BULK_LOAD false (S) — batched INSERTs of 2,000 rows, as the dataset was published with;
//   BULK_LOAD true (M, L) — rows go to a temporary CSV file, loaded in one COPY when the sink is closed
//                           (PostgreSQL: COPY … FROM STDIN through the driver; DuckDB: COPY … FROM the file).
// ClickHouse always takes batched INSERTs, 20,000 rows at a time (each batch is one INSERT, one part on disk).
// Either way the rows land in the same order, so a table reads back the same.
boolean CSV_SINK = BULK_LOAD && !CH
int BATCH = CH ? 20_000 : 2000
DateTimeFormatter TS_CSV = DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss')
def csvValue = { v ->
    if (v == null) return '\\N'
    if (v instanceof CharSequence) return '"' + v.toString().replace('"', '""') + '"'
    if (v instanceof BigDecimal) return (v as BigDecimal).toPlainString()
    if (v instanceof Timestamp) return (v as Timestamp).toLocalDateTime().format(TS_CSV)
    return v.toString()                                    // integers, booleans, java.sql.Date (yyyy-MM-dd)
}
def flushBatch = { Map s ->
    if (s.buf) dbSql.withBatch((s.buf as List).size(), s.sql as String) { ps -> (s.buf as List).each { ps.addBatch(CH ? (it as List).collect(chValue) : it) } }
    s.buf = []
}
def openSink = { String table, List<String> cols ->
    Map s = [table: table, cols: cols, n: 0L]
    if (CSV_SINK) {
        s.file = File.createTempFile("${SCHEMA}-", '.csv')
        s.out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(s.file as File), 'UTF-8'), 1 << 20)
    } else {
        s.sql = "INSERT INTO ${T(table)} (${cols.collect { q(it) }.join(', ')}) VALUES (${cols.collect { '?' }.join(', ')})".toString()
        s.buf = []
    }
    s
}
def put = { Map s, List row ->
    s.n = (s.n as long) + 1
    if (CSV_SINK) { Writer w = s.out as Writer; w.write(row.collect(csvValue).join(',')); w.write('\n') }
    else { (s.buf as List) << row; if ((s.buf as List).size() == BATCH) flushBatch(s) }
}
def closeSink = { Map s ->
    if (CSV_SINK) {
        (s.out as Writer).close()
        File f = s.file as File
        String target = "${T(s.table as String)} (${(s.cols as List<String>).collect { q(it) }.join(', ')})"
        try {
            if (vendor == 'DUCKDB') {
                String path = f.absolutePath.replace('\\', '/').replace("'", "''")
                dbSql.execute("COPY ${target} FROM '${path}' (FORMAT csv, HEADER false, DELIMITER ',', QUOTE '\"', ESCAPE '\"', NULL '\\N')".toString())
            } else {
                def copyApi = dbSql.connection.unwrap(Class.forName('org.postgresql.PGConnection')).getCopyAPI()
                f.withReader('UTF-8') { r -> copyApi.copyIn("COPY ${target} FROM STDIN WITH (FORMAT csv, NULL '\\N')".toString(), r) }
            }
        } finally { f.delete() }
    } else flushBatch(s)
    log.info("  {}: {} rows", s.table, s.n)
}
def insert = { String table, List<String> cols, List<List> rows ->
    if (rows.isEmpty()) return
    Map s = openSink(table, cols)
    rows.each { put(s, it) }
    closeSink(s)
}

// ── 2. reference data ────────────────────────────────────────────────────────────────────────
insert('Region', ['RegionID', 'RegionDescription'], [[1, 'Northern'], [2, 'Southern'], [3, 'Western'], [4, 'Eastern']])
insert('Categories', ['CategoryID', 'CategoryName', 'Description'], CATEGORIES.collect { [it[0], it[1], it[2]] })

List<List> SHIPPERS = [[1, 'Speedy Express', '+44 20 7946 0101'], [2, 'United Package', '+49 30 901820'],
                       [3, 'Federal Shipping', '+1 206 555 0199'], [4, 'Northern Freight Lines', '+46 8 505 000']]
insert('Shippers', ['ShipperID', 'CompanyName', 'Phone'], SHIPPERS)

// Territories: the first TERRITORIES cities of the word lists (46 at most) across the four regions.
Random rT = rnd('Territories')
List<List> territories = []
int tid = 1
COUNTRIES.each { c ->
    c.subList(1, c.size() - 2).each { city -> if (territories.size() < TERRITORIES) territories << [String.format('T%03d', tid++), city, 1 + rT.nextInt(4)] }
}
insert('Territories', ['TerritoryID', 'TerritoryDescription', 'RegionID'], territories)

// ── 3. employees: a sales tree, ORG people per level (S: CEO → 2 sales managers → 9 reps), then at S the staff ──
// Each level's people are shared out in order among the level above, the first ones taking the larger share
// (S: reps 4–8 report to manager 2, reps 9–12 to manager 3). The last level are the sales reps.
List<String> MIDDLE_TITLES = ['Vice President, Sales', 'Regional Director', 'Sales Director', 'Sales Manager']
Random rE = rnd('Employees')
List<List> employees = []
List<List<Integer>> levelIds = []
int eid = 0
ORG.eachWithIndex { int count, int level ->
    boolean isRep = level == ORG.size() - 1
    String title = level == 0 ? 'Chief Executive Officer' : isRep ? 'Sales Representative' : MIDDLE_TITLES[MIDDLE_TITLES.size() - (ORG.size() - 1 - level)]
    List<Integer> bosses = level == 0 ? [] : levelIds[level - 1]
    int share = level == 0 ? 1 : (int) Math.ceil(count / (double) bosses.size())
    List<Integer> ids = []
    count.times { int k ->
        int id = ++eid
        ids << id
        Integer boss = level == 0 ? null : bosses[Math.min(k.intdiv(share), bosses.size() - 1)]
        if (!isRep)
            employees << [id, pick(rE, LAST), pick(rE, FIRST), title, pick(rE, ['Mr.', 'Ms.', 'Dr.']),
                          LocalDate.of(1965 + rE.nextInt(15), 1 + rE.nextInt(12), 1 + rE.nextInt(28)), LocalDate.of(2012 + rE.nextInt(4), 1 + rE.nextInt(12), 1),
                          "${1 + rE.nextInt(200)} ${pick(rE, STREETS)}".toString(), 'London', null, postal(rE, 'AA# #AA'), 'United Kingdom',
                          phone(rE, '+44'), String.valueOf(100 + rE.nextInt(900)), null, boss, null, id == 1 ? 0.0 : 0.01]
        else
            employees << [id, pick(rE, LAST), pick(rE, FIRST), title, pick(rE, ['Mr.', 'Ms.']),
                          LocalDate.of(1975 + rE.nextInt(20), 1 + rE.nextInt(12), 1 + rE.nextInt(28)), LocalDate.of(2014 + rE.nextInt(5), 1 + rE.nextInt(12), 1),
                          "${1 + rE.nextInt(200)} ${pick(rE, STREETS)}".toString(), 'London', null, postal(rE, 'AA# #AA'), 'United Kingdom',
                          phone(rE, '+44'), String.valueOf(100 + rE.nextInt(900)), null, boss, null, 0.03]
    }
    levelIds << ids
}
List<Integer> reps = levelIds.last()
// PLANTED — Learn SQL S3 · 10 (recursive CTEs), scale S: the people who are not in sales. Sales is three levels, so
// on its own a walk from the CEO ends at the second step; operations goes six deep and finance four, so the depth of
// the chart differs by branch and no fixed chain of self-joins (Series 2 · 30) reaches every row. From a warehouse
// operative the walk to the top is five steps. They take no orders and cover no territory, so "Orders" and
// "EmployeeTerritories" do not move, and all were hired from 2016, as the business grew past a sales office. On their
// own stream, after the sales tree, so employees 1 to 12 are the rows they always were. M and L take their depth from
// ORG instead. Each row: the title, the key of the boss (null = the CEO), the year hired.
if (STAFF) {
    Random rD = rnd('Employees.Staff')
    List<List> STAFF_TREE = [
        ['ops',   'Operations Director',   null,    2016], ['fin',   'Finance Director',      null,    2016],
        ['wh',    'Warehouse Manager',     'ops',   2016], ['tr',    'Transport Manager',     'ops',   2019],
        ['acc',   'Accounts Manager',      'fin',   2017], ['early', 'Shift Supervisor',      'wh',    2017],
        ['late',  'Shift Supervisor',      'wh',    2021], ['drv',   'Driver',                'tr',    2019],
        ['cred',  'Credit Controller',     'acc',   2020], ['asst',  'Accounts Assistant',    'acc',   2022],
        ['pick',  'Team Leader, Picking',  'early', 2018], ['goods', 'Team Leader, Goods In', 'late',  2021],
        ['op1',   'Warehouse Operative',   'pick',  2020], ['op2',   'Warehouse Operative',   'pick',  2022],
        ['op3',   'Warehouse Operative',   'goods', 2023]]
    Map<String, Integer> staffId = [:]
    STAFF_TREE.each { s ->
        int id = ++eid
        staffId[s[0] as String] = id
        employees << [id, pick(rD, LAST), pick(rD, FIRST), s[1], pick(rD, ['Mr.', 'Ms.']),
                      LocalDate.of(1965 + rD.nextInt(30), 1 + rD.nextInt(12), 1 + rD.nextInt(28)), LocalDate.of(s[3] as int, 1 + rD.nextInt(12), 1),
                      "${1 + rD.nextInt(200)} ${pick(rD, STREETS)}".toString(), 'London', null, postal(rD, 'AA# #AA'), 'United Kingdom',
                      phone(rD, '+44'), String.valueOf(100 + rD.nextInt(900)), null, s[2] == null ? 1 : staffId[s[2] as String], null, 0.0]
    }
}
// PLANTED — Learn SQL S3 · 10 (recursive CTEs), scales S and L: two interim managers entered as reporting to each other.
// No sales and outside the tree, so a walk down from the CEO never meets them; a walk UP from either of them never
// ends unless the query stops it. On its own stream, so nothing above moves.
if (REPORTING_LOOP) {
    Random rLoop = rnd('Employees.ReportingLoop')
    [[eid + 1, eid + 2], [eid + 2, eid + 1]].each { pair ->
        employees << [pair[0], pick(rLoop, LAST), pick(rLoop, FIRST), 'Interim Sales Manager', pick(rLoop, ['Mr.', 'Ms.']),
                      LocalDate.of(1970 + rLoop.nextInt(20), 1 + rLoop.nextInt(12), 1 + rLoop.nextInt(28)), LocalDate.of(2024, 6 + rLoop.nextInt(6), 1),
                      "${1 + rLoop.nextInt(200)} ${pick(rLoop, STREETS)}".toString(), 'London', null, postal(rLoop, 'AA# #AA'), 'United Kingdom',
                      phone(rLoop, '+44'), String.valueOf(100 + rLoop.nextInt(900)), 'Interim: reporting line to be confirmed', pair[1], null, 0.01]
    }
}
employees.each { e -> e[16] = "${(e[2] as String).toLowerCase()}.${(e[1] as String).toLowerCase()}@northwind-company.example".toString() }
insert('Employees', ['EmployeeID', 'LastName', 'FirstName', 'Title', 'TitleOfCourtesy', 'BirthDate', 'HireDate', 'Address', 'City', 'Region',
                     'PostalCode', 'Country', 'HomePhone', 'Extension', 'Notes', 'ReportsTo', 'Email', 'CommissionRate'],
       employees.collect { e -> e.withIndex().collect { v, i -> (i in [5, 6]) ? java.sql.Date.valueOf(v as LocalDate) : (i == 17 ? new BigDecimal(v.toString()) : v) } })

Random rET = rnd('EmployeeTerritories')
Set<String> et = new LinkedHashSet<>()
reps.each { emp -> while (et.count { it.startsWith("${emp}|") } < TERRITORIES_PER_REP) { et << "${emp}|${territories[rET.nextInt(territories.size())][0]}".toString() } }
insert('EmployeeTerritories', ['EmployeeID', 'TerritoryID'], et.collect { it.split('\\|') }.collect { [it[0] as int, it[1]] })

// ── 4. suppliers, products, price history ───────────────────────────────────────────────────
Random rS = rnd('Suppliers')
List<List> suppliers = []
Set<String> supplierNames = new HashSet<>()
(1..SUPPLIERS).each { id ->
    String name
    while (true) { name = "${pick(rS, NAME_A)} ${pick(rS, ['Farms', 'Producers', 'Brewery', 'Dairy', 'Fisheries', 'Mills', 'Orchards', 'Kitchens'])}".toString(); if (supplierNames.add(name)) break }
    List<String> c = pick(rS, COUNTRIES)
    suppliers << [id, name, "${pick(rS, FIRST)} ${pick(rS, LAST)}".toString(), pick(rS, TITLES), "${1 + rS.nextInt(200)} ${pick(rS, STREETS)}".toString(),
                  c[1 + rS.nextInt(c.size() - 3)], null, postal(rS, c[c.size() - 2]), c[0], phone(rS, c[c.size() - 1]), null, null,
                  "orders@${name.toLowerCase().replaceAll('[^a-z]', '')}.example".toString()]
}
insert('Suppliers', ['SupplierID', 'CompanyName', 'ContactName', 'ContactTitle', 'Address', 'City', 'Region', 'PostalCode', 'Country', 'Phone',
                     'Fax', 'HomePage', 'Email'], suppliers)

Random rP = rnd('Products')
List<Map> products = []
Set<String> productNames = new HashSet<>()
(1..PRODUCTS).each { id ->
    List cat = CATEGORIES[(id - 1) % 8]
    String name
    while (true) { name = "${pick(rP, PRODUCT_WORDS)} ${pick(rP, PRODUCT_NOUNS[cat[0] as int])}".toString(); if (productNames.add(name)) break }
    double lo = cat[3] as double, hi = cat[4] as double
    // Long tail: most products cheap, a few dear (skew for the lessons on medians and ranks).
    double startPrice = lo + (hi - lo) * Math.pow(rP.nextDouble(), 2.2)
    products << [id: id, name: name, supplier: 1 + rP.nextInt(SUPPLIERS), category: cat[0], pack: pick(rP, PACKS),
                 price: money(startPrice), costRatio: 0.68 + rP.nextDouble() * 0.08, popularity: Math.pow(rP.nextDouble(), 3) + 0.05,
                 discontinuedFrom: (id % 13 == 0) ? LocalDate.of(2022 + rP.nextInt(3), 1 + rP.nextInt(12), 1) : null]
}

// Price history: ~3 changes per product over five years, mostly rises.
Random rPC = rnd('PriceChanges')
List<List> priceChanges = []
Map<Integer, List<List>> priceTimeline = [:]      // productID -> [[fromDate, price] …] in date order
products.each { p ->
    BigDecimal price = p.price as BigDecimal
    List<List> timeline = [[FIRST_DAY, price]]
    int n = 1 + rPC.nextInt(5)                     // 1..5, average 3
    List<LocalDate> dates = (1..n).collect { FIRST_DAY.plusDays(60 + rPC.nextInt(1700)) }.sort()
    dates.each { d ->
        double factor = rPC.nextDouble() < 0.85 ? 1.03 + rPC.nextDouble() * 0.05 : 0.93 + rPC.nextDouble() * 0.04
        BigDecimal next = (price * new BigDecimal(factor)).setScale(2, RoundingMode.HALF_UP)
        priceChanges << [priceChanges.size() + 1, p.id, java.sql.Date.valueOf(d), price, next]
        timeline << [d, next]
        price = next
    }
    p.currentPrice = price
    priceTimeline[p.id as int] = timeline
}
// PLANTED — Learn SQL S2 · 35: two products in the same category end at the same price, so ROW_NUMBER() <= N picks one
// arbitrarily where RANK() keeps both. Done as one more dated price change, so the history stays consistent.
Map tieA = products[3], tieB = products[11]                // products 4 and 12, both category 4
if ((tieA.currentPrice as BigDecimal) != (tieB.currentPrice as BigDecimal)) {
    LocalDate d = LocalDate.of(2024, 11, 4)
    priceChanges << [priceChanges.size() + 1, tieB.id, java.sql.Date.valueOf(d), tieB.currentPrice, tieA.currentPrice]
    priceTimeline[tieB.id as int] << [d, tieA.currentPrice]
    tieB.currentPrice = tieA.currentPrice
}
def priceOn = { int productId, LocalDate d ->
    BigDecimal price = null
    for (List step : priceTimeline[productId]) { if (!(step[0] as LocalDate).isAfter(d)) price = step[1] as BigDecimal }
    price
}

// ── 5. customers, segments, changes ─────────────────────────────────────────────────────────
Random rC = rnd('Customers')
List<Map> customers = []
Set<String> customerNames = new HashSet<>(), customerIds = new HashSet<>()
Map<String, Integer> nextIdSuffix = [:]           // per 4-letter stem, the first number not yet tried (the same ids, found faster)
(1..CUSTOMERS).each { n ->
    // A name from NAME_A × NAME_B; once those are mostly taken (M, L), with a NAME_C word added; failing that, numbered.
    // S never gets past the first try-loop (its checksums prove it).
    String name = null
    for (int tries = 0; tries < 50 && name == null; tries++) { String t = "${pick(rC, NAME_A)} ${pick(rC, NAME_B)}".toString(); if (customerNames.add(t)) name = t }
    for (int tries = 0; tries < 50 && name == null; tries++) { String t = "${pick(rC, NAME_A)} ${pick(rC, NAME_B)} ${pick(rC, NAME_C)}".toString(); if (t.length() <= 40 && customerNames.add(t)) name = t }
    if (name == null) { name = "${pick(rC, NAME_A)} ${pick(rC, NAME_B)} ${n}".toString(); customerNames.add(name) }
    String base = name.replaceAll('[^A-Za-z]', '').toUpperCase().substring(0, 5), id = base
    String stem = base.substring(0, 4)
    int k = nextIdSuffix[stem] ?: 1
    while (!customerIds.add(id)) { id = stem + (k++) }
    if (id != base) nextIdSuffix[stem] = k
    List<String> c = COUNTRIES[n <= 21 ? n - 1 : rC.nextInt(COUNTRIES.size())]   // every country has at least one customer
    // SKEW: a few large customers bring about half the revenue.
    double weight = Math.pow(rC.nextDouble(), 5) * 40 + 0.3
    // CHURN AND JOINERS: the last JOINERS join after the start (S: 25), one in eight stop before the end (S: 15) — cohorts, Learn SQL S3.
    LocalDate joined = n <= CUSTOMERS - JOINERS ? FIRST_DAY : FIRST_DAY.plusDays(200 + rC.nextInt(1400))
    LocalDate left = (n % 8 == 3) ? FIRST_DAY.plusDays(500 + rC.nextInt(1100)) : null
    customers << [id: id, name: name, contact: "${pick(rC, FIRST)} ${pick(rC, LAST)}".toString(), title: pick(rC, TITLES),
                  address: "${1 + rC.nextInt(300)} ${pick(rC, STREETS)}".toString(), city: c[1 + rC.nextInt(c.size() - 3)], postal: postal(rC, c[c.size() - 2]),
                  country: c[0], phone: messyPhone(rC, c[c.size() - 1]), segment: pick(rC, ['Retail', 'Retail', 'Restaurant', 'Wholesale']),
                  weight: weight, joined: joined, left: left, rep: reps[rC.nextInt(reps.size())]]
}
assert customers.count { it.left != null } == (1..CUSTOMERS).count { it % 8 == 3 }   // S: exactly 15
// PLANTED — Learn SQL S2 · 40: the text nobody cleaned. Roughly one customer in six has a city that needs
// tidying and one in six a contact name that does. FOUR DEFECTS EACH, BUT NOT THE SAME FOUR: City gets a
// leading space, a trailing space, SHOUTING and whispering; ContactName gets a trailing space, a DOUBLED INNER
// space, SHOUTING and whispering. A doubled inner space is a ContactName defect ONLY — a lesson that promises
// one inside a city name will not find it.
// COUNTS, MEASURED 2026-09-21 RATHER THAN ESTIMATED. The switches below plant 19 cities and 21 contact names
// out of 120. A later City or ContactName change (further down) retypes some of those customers cleanly,
// exactly as a real correction would, so THE FINISHED TABLE HOLDS 13 MESSY CITIES (7 outer space, 4 SHOUTED,
// 2 whispered) AND 16 MESSY CONTACT NAMES (3 outer space, 4 doubled inner, 4 SHOUTED, 5 whispered). That is
// what the old wording called "a little lower": six of the nineteen cities and five of the twenty-one contact
// names are cleaned by their own change history. Quote the finished numbers in the lesson, not the planted ones.
// THE FIGURE THE EPISODE IS FOR: SELECT DISTINCT "City" returns 58, and TRIM/LOWER it returns 45. Thirteen
// cities that do not exist, on a table of 120 rows a learner can read end to end — twelve cities split in two
// and Tampere split in three. These are rates on the row number, so every scale shows the same picture and no
// random draw moves. Say so in the lesson; do not "fix" it here.
customers.eachWithIndex { cu, i ->
    switch (i % 25) {
        case 3:  cu.city = "${cu.city} ".toString(); break                            // a trailing space
        case 9:  cu.city = " ${cu.city}".toString(); break                            // a leading space
        case 14: cu.city = (cu.city as String).toUpperCase(); break                   // SHOUTED
        case 21: cu.city = (cu.city as String).toLowerCase(); break                   // whispered
    }
    switch (i % 23) {
        case 2:  cu.contact = (cu.contact as String).toLowerCase(); break
        case 7:  cu.contact = (cu.contact as String).toUpperCase(); break
        case 12: cu.contact = (cu.contact as String).replaceFirst(' ', '  '); break   // a doubled inner space
        case 18: cu.contact = "${cu.contact} ".toString(); break                      // a trailing space
    }
}

Random rCC = rnd('CustomerChanges')
List<List> customerChanges = []
customers.each { cu ->
    double roll = rCC.nextDouble()
    int n = roll < 0.1 ? 0 : (roll < 0.6 ? 1 : 2)          // ≈ 1.3 per customer ≈ 150 in all at S
    List<List> mine = []
    n.times {
        LocalDate d = FIRST_DAY.plusDays(100 + rCC.nextInt(1700))
        String attr = pick(rCC, ['Segment', 'Segment', 'City', 'ContactName'])
        String oldV = attr == 'Segment' ? cu.segment : attr == 'City' ? cu.city : cu.contact
        String newV = attr == 'Segment' ? pick(rCC, ['Retail', 'Restaurant', 'Wholesale'].findAll { it != oldV }) :
                      attr == 'City' ? pick(rCC, otherCities(cu.country as String, oldV) ?: [oldV]) :
                      "${pick(rCC, FIRST)} ${pick(rCC, LAST)}".toString()
        mine << [customerChanges.size() + mine.size() + 1, cu.id, d, attr, oldV, newV]
        if (attr == 'Segment') cu.segment = newV else if (attr == 'City') cu.city = newV else cu.contact = newV
    }
    // FIXED (2026-09-19) — Data Warehousing S3 · 10, dbt S2 (snapshots): a customer's changes happen in the order
    // they chain in (each OldValue is the previous NewValue), so their dates ascend in that order too. The same
    // draws as before; only the dates are handed out sorted. Before this, 28 of the 53 customers with two changes had
    // the second dated before the first.
    List<LocalDate> dates = mine.collect { it[2] as LocalDate }.sort()
    mine.eachWithIndex { c, i -> c[2] = dates[i] }
    // What each customer looked like before any change, and the dated steps since: ship-to cities and notes as of the
    // order date, and UpdatedAt, come from these.
    cu.cityHistory = mine.findAll { it[3] == 'City' }.collect { [it[2], it[5]] }
    cu.contactHistory = mine.findAll { it[3] == 'ContactName' }.collect { [it[2], it[5]] }
    cu.firstCity = mine.find { it[3] == 'City' }?.getAt(4) ?: cu.city
    cu.firstContact = mine.find { it[3] == 'ContactName' }?.getAt(4) ?: cu.contact
    cu.lastChange = mine ? mine.last()[2] : null
    mine.each { customerChanges << [it[0], it[1], java.sql.Date.valueOf(it[2] as LocalDate), it[3], it[4], it[5]] }
}
insert('CustomerChanges', ['CustomerChangeID', 'CustomerID', 'ChangedDate', 'Attribute', 'OldValue', 'NewValue'], customerChanges)
// The value a customer had on a day: the last change on or before it, else the value before any change.
def asOf = { Object first, List<List> history, LocalDate d ->
    Object v = first
    for (List step : history) { if (!(step[0] as LocalDate).isAfter(d)) v = step[1] else break }
    v
}

// ── 6. orders, lines, shipping, invoices, stock ─────────────────────────────────────────────
Random rO = rnd('Orders'), rL = rnd('Order Details'), rI = rnd('Invoices'), rM = rnd('StockMovements'), rCF = rnd('CourierConfirmations')
// EXTENSIONS (2026-09-17) for the courses after Learn SQL S2 — each on its OWN stream, so no earlier row moved:
Random rCreated = rnd('Orders.CreatedAt'), rChannel = rnd('Orders.Channel'), rW = rnd('WebOrders')
// PLANTED — Learn SQL S2 · 58 (freight) and · 58 (days to pay). Both live on streams of their own, so a tail
// added here can never move an order, a line or an invoice that was drawn before it.
Random rFS = rnd('Orders.FreightSurcharge'), rLP = rnd('Invoices.LatePayers')
double[] MONTH_WEIGHT = [1, 1, 1, 1, 1, 1, 1, 0.8, 1, 1, 1.35, 1.35]
// PLANTED — Learn SQL S2. DECIDED AT GATE 2 (owner, 2026-09-17): a PARTIAL outage — 20% of Speedy Express ship dates
// lost for 26 weeks, orders placed 2024-03-04 .. 2024-08-30. The 86% versions (13 and 8 weeks) halved monthly invoicing,
// which any finance team would notice at the first month-end close; 20% stays inside normal monthly variation.
// Figures: kraft-src-company-biz/.docs/plan-academy-figures-northwind-co-s.md. The two settings stay parameters only so a
// variant can be measured side by side; the defaults ARE the dataset.
int OUTAGE_WEEKS = (params?.OUTAGE_WEEKS ?: 26) as int
double OUTAGE_RATE = (params?.OUTAGE_RATE ?: 0.20) as double      // share of Speedy Express ship dates lost in the window
LocalDate OUTAGE_FROM = LocalDate.of(2024, 3, 4)
LocalDate OUTAGE_TO = OUTAGE_FROM.plusWeeks(OUTAGE_WEEKS).minusDays(3)   // ends on a Friday: 2024-08-30 for 26 weeks

// The four big tables go to the database as they are made; only what the later steps need is kept.
Map ordersOut = openSink('Orders', ['OrderID', 'CustomerID', 'EmployeeID', 'OrderDate', 'RequiredDate', 'ShippedDate', 'ShipVia', 'Freight', 'ShipName',
                                    'ShipAddress', 'ShipCity', 'ShipRegion', 'ShipPostalCode', 'ShipCountry', 'Status', 'UpdatedAt', 'CreatedAt', 'Channel',
                                    'DeliveryNotes'])
Map linesOut = openSink('Order Details', ['OrderID', 'ProductID', 'UnitPrice', 'Quantity', 'Discount'])
Map webOut = openSink('WebOrders', ['WebOrderID', 'OrderID', 'ReceivedAt', 'Payload', 'ClientIP'])
Map historyOut = openSink('OrderStatusHistory', ['StatusChangeID', 'OrderID', 'Status', 'ChangedAt'])
long historyCount = 0

// EXTENSIONS (2026-09-19), each on its own stream again:
// DELIVERY NOTES — ETL S1 · 27 (personal data): free text typed by whoever took the order, ~5% of orders. Some carry
// the contact's name or phone number, the personal data a column-by-column masking plan misses.
Random rDN = rnd('Orders.DeliveryNotes')
List<String> NOTES = ['Deliver to the back entrance.', 'Loading bay closes at 15:00.', 'Leave with reception if nobody answers.',
                      'Pallets only, no loose boxes.', 'Cold chain: straight to the chiller, please.', 'Invoice to head office, goods to the shop.',
                      'Please call ahead: {contact}, {phone}.', 'Ask for {first} at goods-in.', 'Phone {phone} on arrival.',
                      'If late, tell {contact} before noon.']
// CLIENT IPs — ETL S1 · 27: the web shop logs the address each web order came from. Each customer mostly orders from
// one office address, sometimes from a phone. Only documentation address blocks (RFC 5737, RFC 3849), never a
// real one.
Random rIP = rnd('WebOrders.ClientIP')
def ipAddress = { Random r ->
    int block = r.nextInt(4), a = r.nextInt(254) + 1, b = r.nextInt(65536), c = r.nextInt(65536)
    block == 0 ? "192.0.2.${a}".toString() : block == 1 ? "198.51.100.${a}".toString() : block == 2 ? "203.0.113.${a}".toString()
               : String.format('2001:db8:%x:%x::%x', b, c, a)
}
customers.each { cu -> cu.officeIp = ipAddress(rIP) }
// PLANTED — Data Warehousing S1 · 22 (reconciliation): orders cancelled and then reinstated, one per 10,000 (S: one),
// near the end of 2024. OrderStatusHistory holds Open → Cancelled → Open → Shipped for it, so a warehouse loaded
// while it was Cancelled disagrees with the source for a reason both sides can show.
Random rSH = rnd('OrderStatusHistory')
List<Integer> reinstate = (1..Math.max(1, ORDERS.intdiv(10_000))).collect { (int) (ORDERS * 0.95) + rSH.nextInt(ORDERS - (int) (ORDERS * 0.95) - 200) }.sort()
Map invoicesOut = openSink('Invoices', ['InvoiceID', 'OrderID', 'InvoiceDate', 'Amount', 'Freight', 'PaidDate'])
// Kept for later steps: the sales stock movements (as int arrays), the outage orders and the Speedy Express orders that
// kept their ship date in the outage window (the courier confirmations), and sales per category and month (the targets).
Ints saleProduct = new Ints(), saleDay = new Ints(), saleQty = new Ints(), saleOrder = new Ints()
List<Map> outageOrders = []
List<List> datedInOutage = []
Map<String, BigDecimal> actual = [:]
long lineCount = 0, invoiceCount = 0, webCount = 0

// Customers weighted by size, among those buying on the day; the list changes only on a join or leave date.
TreeSet<LocalDate> customerEvents = new TreeSet<>(customers.collectMany { [it.joined, it.left].findAll { it != null } as List<LocalDate> })
LocalDate activeOn = null
List<Map> active = null
Cumulative activeWeights = null
Cumulative productPopularity = new Cumulative(products.collect { it.popularity as double })

int orderId = 0
ORDERS_PER_YEAR.eachWithIndex { int count, int yi ->
    int year = 2020 + yi
    List<LocalDate> dates = []
    (1..count).each {
        int m = weighted(rO, MONTH_WEIGHT.toList()) + 1
        LocalDate first = LocalDate.of(year, m, 1)
        dates << first.plusDays(rO.nextInt(first.lengthOfMonth()))
    }
    dates.sort().each { LocalDate od ->
        orderId++
        if (activeOn == null || (od != activeOn && !customerEvents.subSet(activeOn, false, od, true).isEmpty())) {
            active = customers.findAll { !(it.joined as LocalDate).isAfter(od) && (it.left == null || (it.left as LocalDate).isAfter(od)) }
            activeWeights = new Cumulative(active.collect { it.weight as double })
        }
        activeOn = od
        Map cu = active[activeWeights.pick(rO)]
        int shipVia = weighted(rO, [0.75d, 0.12d, 0.08d, 0.05d]) + 1
        int employee = rO.nextDouble() < 0.85 ? (cu.rep as int) : reps[rO.nextInt(reps.size())]
        LocalDate required = od.plusDays(14)
        // Lines: 1–6, average ~2.5; price as of the order date.
        int nLines = weighted(rL, [0.26d, 0.32d, 0.20d, 0.12d, 0.07d, 0.03d]) + 1
        Set<Integer> used = new HashSet<>()
        List<List> orderLines = []
        BigDecimal goods = BigDecimal.ZERO
        nLines.times {
            Map p
            while (true) {
                p = products[productPopularity.pick(rL)]
                if (p.discontinuedFrom == null || od.isBefore(p.discontinuedFrom as LocalDate)) { if (used.add(p.id as int)) break }
            }
            BigDecimal price = priceOn(p.id as int, od)
            int qty = 4 + rL.nextInt(Math.max(8, (int) (QTY_BUDGET / (price as double))))
            BigDecimal disc = new BigDecimal(pick(rL, ['0', '0', '0', '0.05', '0.1', '0.15']))
            orderLines << [orderId, p.id, price, qty, disc]
            goods += (price * qty * (BigDecimal.ONE - disc)).setScale(2, RoundingMode.HALF_UP)
        }
        BigDecimal freight = money((goods as double) * (0.02 + rO.nextDouble() * 0.03))
        // PLANTED — Learn SQL S2 · 58: freight is normally 2-5% of the goods, but about one delivery in twenty needs
        // a pallet, a ferry or an express re-delivery, and the courier bills a flat surcharge for it — 120 to 600,
        // whatever the order was worth. A FLAT fee, because that is how couriers really bill, and because it keeps the
        // biggest bill believable: the tail is long, not absurd. The effect is that the AVERAGE freight bill sits well
        // above the TYPICAL one and only about one order in four is above "average" — which is the whole reason MEDIAN
        // and the percentiles exist. Both rolls are drawn for every order, never inside the `if`, so changing the rate
        // moves only the orders the rate is about.
        // The surcharge is also capped at 60% of the goods: a courier does not bill 600 to move a 96 order, and a
        // freight bill larger than what is in the box would make a learner distrust the data rather than query it.
        double surchargeRoll = rFS.nextDouble(), surcharge = 120 + rFS.nextDouble() * 480
        if (surchargeRoll < 0.05) freight = money((freight as double) + Math.min(surcharge, (goods as double) * 0.6))

        // What really happened to the order.
        // EVERY ROLL IS DRAWN FOR EVERY ORDER, before any decision uses it. A draw inside an `if` would make the
        // stream depend on earlier outcomes, so changing one rule (say the outage window) would shift every later
        // order. Drawn unconditionally, a rule change moves only the orders the rule is about.
        int shipDays = 2 + rO.nextInt(6), lateDays = 15 + rO.nextInt(10)
        double lateRoll = rO.nextDouble(), cancelRoll = rO.nextDouble(), phantomRoll = rO.nextDouble(),
               lapseRoll = rO.nextDouble(), outageRoll = rO.nextDouble()
        String status = 'Shipped'
        LocalDate actualShip = od.plusDays(lateRoll < 0.02 ? lateDays : shipDays)           // ~2% late (noise)
        LocalDate recordedShip = actualShip
        if (!actualShip.isBefore(LAST_DAY.minusDays(1)) || od.isAfter(LAST_DAY.minusDays(6))) { status = 'Open'; actualShip = null; recordedShip = null }
        else if (cancelRoll < 0.015) { status = 'Cancelled'; actualShip = null; recordedShip = null }
        else if (year == 2024 && od.isBefore(LocalDate.of(2024, 10, 1)) && phantomRoll < PHANTOM_RATE) {
            // PLANTED — Learn SQL S2 · 60: cancelled by the customer, never updated: still 'Open', counted as a sale.
            status = 'Open'; actualShip = null; recordedShip = null
        }
        else if (lapseRoll < 0.002) { recordedShip = null }                                   // ~0.2% data-entry lapses (noise)
        // PLANTED — Learn SQL S2: for Speedy Express orders placed from 2024-03-04 for OUTAGE_WEEKS weeks, ship dates
        // stop being recorded (20% of them, the default). Keyed on the ORDER date on purpose: it is the only date a learner can see on
        // such an order, so "it starts the week of 4 March" (S2 · 10) is what the query shows.
        boolean inOutageWindow = shipVia == 1 && !od.isBefore(OUTAGE_FROM) && !od.isAfter(OUTAGE_TO)
        boolean outage = inOutageWindow && actualShip != null && outageRoll < OUTAGE_RATE
        if (outage) recordedShip = null

        LocalDateTime updated = (recordedShip ?: od).atTime(9 + rO.nextInt(9), rO.nextInt(60))
        // WHEN THE ORDER WAS ENTERED, not when it was placed. Most on the day; ~1.5% keyed in 1–10 days late.
        // EXTENSION — Data Warehousing S1 (late-arriving facts, reconciliation timing), ETL S1 (incremental loads on
        // a watermark), dbt S2 (freshness, incremental models).
        LocalDateTime created = od.atTime(8 + rCreated.nextInt(10), rCreated.nextInt(60))
        if (rCreated.nextDouble() < 0.015) created = created.plusDays(1 + rCreated.nextInt(10))
        if (updated.isBefore(created)) updated = created
        // EXTENSION — analytics courses: CHANNEL, drifting from sales reps towards the web shop over five years.
        double webShare = 0.10 + 0.08 * yi
        double roll = rChannel.nextDouble()
        String channel = roll < webShare ? 'Web' : roll < webShare + 0.10 ? 'EDI' : roll < webShare + 0.35 ? 'Phone' : 'Sales rep'
        // FIXED (2026-09-19) — Data Warehousing S3 · 10: an order ships to where the customer was ON THE ORDER DATE,
        // not where it is now, so an order placed before a City change keeps the old city.
        String shipCity = asOf(cu.firstCity, cu.cityHistory as List<List>, od)
        String contactThen = asOf(cu.firstContact, cu.contactHistory as List<List>, od)
        double noteRoll = rDN.nextDouble()
        int noteKind = rDN.nextInt(NOTES.size())
        String note = noteRoll >= 0.05 ? null : NOTES[noteKind].replace('{contact}', contactThen).replace('{first}', contactThen.split(' ')[0])
                                                                 .replace('{phone}', cu.phone as String)
        put(ordersOut, [orderId, cu.id, employee, ts(od), ts(required), recordedShip == null ? null : ts(recordedShip), shipVia, freight,
                        cu.name, cu.address, shipCity, regionOfCity(shipCity), cu.postal, cu.country, status, Timestamp.valueOf(updated),
                        Timestamp.valueOf(created), channel, note])
        if (cu.firstCreated == null) { cu.firstCreated = created; cu.firstChannel = channel }
        // EXTENSION — Data Warehousing S1 · 22, ETL S1 · 35: every status an order has been in, and when. The last
        // row is the order's Status at its UpdatedAt.
        List<List> steps = [['Open', created]]
        if (!reinstate.isEmpty() && orderId >= reinstate[0] && status == 'Shipped' && recordedShip != null && updated.isAfter(created.plusDays(3))) {
            reinstate.remove(0)
            steps << ['Cancelled', created.plusDays(1).withHour(10)] << ['Open', created.plusDays(2).withHour(11)]
        }
        if (status != 'Open') steps << [status, updated]
        steps.each { put(historyOut, [++historyCount, orderId, it[0], Timestamp.valueOf(it[1] as LocalDateTime)]) }
        if (inOutageWindow && recordedShip != null) datedInOutage << [orderId, recordedShip]
        // EXTENSION — THE WEB SHOP'S RAW JSON for web orders, as it arrived. Planted defects, ~1 in 25 each: a missing
        // key, a quantity sent as text, a price with a comma. For Learn SQL S3 (JSON in SQL), ETL (a JSON/API
        // source) and dbt (parsing a raw source). The order tables above are the cleaned truth.
        if (channel == 'Web') {
            List items = orderLines.collect { l ->
                def qty = rW.nextInt(25) == 0 ? "\"${l[3]}\"" : "${l[3]}"
                def price = rW.nextInt(25) == 0 ? "\"${(l[2] as BigDecimal).setScale(2).toPlainString().replace('.', ',')}\"" : (l[2] as BigDecimal).setScale(2).toPlainString()
                "{\"sku\": \"P${String.format('%04d', l[1])}\", \"qty\": ${qty}, \"unit_price\": ${price}, \"discount\": ${(l[4] as BigDecimal).stripTrailingZeros().toPlainString()}}"
            }
            String shipTo = rW.nextInt(25) == 0 ? '' : ", \"ship_to\": {\"city\": \"${shipCity.trim()}\", \"country\": \"${cu.country}\"}"
            String payload = "{\"order_ref\": \"WEB-${orderId}\", \"customer\": \"${cu.id}\", \"placed_at\": \"${created.toString()}\"${shipTo}, \"items\": [${items.join(', ')}]}"
            double ipRoll = rIP.nextDouble()
            String fresh = ipAddress(rIP)
            put(webOut, [++webCount, orderId, Timestamp.valueOf(created), payload, ipRoll < 0.75 ? cu.officeIp : fresh])
        }
        orderLines.each { l ->
            put(linesOut, l)
            String key = "${products[(l[1] as int) - 1].category}|${od.year}-${od.monthValue}"
            actual[key] = (actual[key] ?: BigDecimal.ZERO) + (l[2] as BigDecimal) * (l[3] as int) * (BigDecimal.ONE - (l[4] as BigDecimal))
        }
        lineCount += orderLines.size()

        // ERP rule: an invoice is created when a ship date is recorded.
        if (recordedShip != null) {
            // PLANTED — Learn SQL S2 · 58: most invoices are settled in 10-49 days, but about one in twenty sits for
            // months. AVG(days to pay) is dragged up by those few; MEDIAN says what actually happens and a percentile
            // says what to promise. Some of the slowest run past the last day and stay unpaid (a real NULL, S2 · 25).
            double slowRoll = rLP.nextDouble()
            int slowExtra = 45 + rLP.nextInt(200)
            LocalDate paid = recordedShip.plusDays(10 + rI.nextInt(40) + (slowRoll < 0.08 ? slowExtra : 0))
            put(invoicesOut, [++invoiceCount, orderId, java.sql.Date.valueOf(recordedShip), goods, freight, paid.isAfter(LAST_DAY) ? null : java.sql.Date.valueOf(paid)])
        }
        // Stock leaves the warehouse when goods physically ship — recorded or not (a clue for the modelling courses).
        if (actualShip != null) orderLines.each { l -> saleProduct.add(l[1] as int); saleDay.add((int) actualShip.toEpochDay()); saleQty.add(-(l[3] as int)); saleOrder.add(orderId) }
        if (outage) outageOrders << [orderId: orderId, shipped: actualShip]
        if (orderId % 100_000 == 0) log.info("  … {} of {} orders", orderId, ORDERS)
    }
}
[ordersOut, webOut, linesOut, invoicesOut, historyOut].each { closeSink(it) }

// Customers go in after the orders: when each account was created depends on its first order.
// EXTENSION — ETL S2 · 20 (late dimensions), Data Warehousing S1 (inferred members), dbt S2 (snapshots):
//   CreatedAt — the customers of 2020 were created in 2015–2019; a joiner up to a month before its first order.
//   PLANTED   — one joiner in eight whose first order came through the web shop was created 1–3 days AFTER that
//               order (credit control approves web accounts later; S: 3), so a load that looks the customer up when
//               the order arrives finds nothing and needs an inferred member.
//   UpdatedAt — the last CustomerChanges date, else CreatedAt (the timestamp a snapshot or a watermark reads).
Random rCA = rnd('Customers.CreatedAt'), rLate = rnd('Customers.CreatedAt.Late')
customers.each { cu ->
    int days = rCA.nextInt(1826), lead = rCA.nextInt(31), h = 8 + rCA.nextInt(10), m = rCA.nextInt(60), uh = 8 + rCA.nextInt(10), um = rCA.nextInt(60)
    LocalDateTime created = (cu.joined as LocalDate) == FIRST_DAY ? LocalDate.of(2015, 1, 1).plusDays(days).atTime(h, m)
                                                                  : (cu.joined as LocalDate).minusDays(lead).atTime(h, m)
    if (cu.firstCreated != null && created.isAfter(cu.firstCreated as LocalDateTime)) created = (cu.firstCreated as LocalDateTime).minusHours(1)
    cu.created = created
    cu.uTime = [uh, um]
}
List<Map> webFirst = customers.findAll { (it.joined as LocalDate) != FIRST_DAY && it.firstChannel == 'Web' }
Collections.shuffle(webFirst, rLate)
webFirst.take(Math.max(1, JOINERS.intdiv(8))).each { cu -> cu.created = (cu.firstCreated as LocalDateTime).plusDays(1 + rLate.nextInt(3)).withHour(9 + rLate.nextInt(8)) }
insert('Customers', ['CustomerID', 'CompanyName', 'ContactName', 'ContactTitle', 'Address', 'City', 'Region', 'PostalCode', 'Country', 'Phone', 'Fax', 'Email', 'Segment',
                     'CreatedAt', 'UpdatedAt'],
       customers.collect { cu ->
           LocalDateTime created = cu.created as LocalDateTime
           LocalDateTime updated = cu.lastChange == null ? created : (cu.lastChange as LocalDate).atTime(cu.uTime[0] as int, cu.uTime[1] as int)
           if (updated.isBefore(created)) updated = created
           [cu.id, cu.name, cu.contact, cu.title, cu.address, cu.city, regionOfCity(cu.city as String), cu.postal, cu.country, cu.phone, null,
            "buying@${(cu.name as String).toLowerCase().replaceAll('[^a-z]', '')}.example".toString(), cu.segment, Timestamp.valueOf(created), Timestamp.valueOf(updated)] })

// PLANTED — Learn SQL S2 · 48: Speedy Express confirms ~95% of the un-dated outage orders; the rest (~5%) were lost.
List<List> confirmations = []
outageOrders.each { o ->
    if (rCF.nextDouble() < 0.95) confirmations << [0, 1, o.orderId, java.sql.Date.valueOf((o.shipped as LocalDate).plusDays(1 + rCF.nextInt(3))), java.sql.Date.valueOf(LocalDate.of(2024, 12, 16))]
}
// Noise: a few confirmations for orders that already have a ship date (S: 8), and one sent twice.
Math.max(8, ORDERS.intdiv(1250)).times { List o = datedInOutage[rCF.nextInt(datedInOutage.size())]; confirmations << [0, 1, o[0], java.sql.Date.valueOf((o[1] as LocalDate).plusDays(2)), java.sql.Date.valueOf(LocalDate.of(2024, 12, 16))] }
// PLANTED — Learn SQL S2 · 48: the courier's hand-over failed once and its system re-sent a batch, so some orders
// were confirmed twice and a few three times (S: about 15 orders, 18 extra rows, two days later). Counting the
// confirmations with UNION quietly removes them; UNION ALL does not — and the two numbers differ by enough to notice.
// A stream of its own, so the confirmations above never move.
Random rCFD = rnd('CourierConfirmations.Retries')
int resentOrders = Math.max(15, confirmations.size().intdiv(12))
List<List> resent = []
if (confirmations) resentOrders.times {
    List src = confirmations[rCFD.nextInt(confirmations.size())]
    int again = rCFD.nextDouble() < 0.2 ? 2 : 1
    again.times { resent << [0, src[1], src[2], src[3], java.sql.Date.valueOf(LocalDate.of(2024, 12, 18))] }
}
confirmations.addAll(resent)
confirmations.eachWithIndex { c, i -> c[0] = i + 1 }
insert('CourierConfirmations', ['ConfirmationID', 'ShipperID', 'OrderID', 'DeliveredDate', 'ReceivedDate'], confirmations)

// Stock receipts, returns and adjustments around the sales, appended after the sales in this order: receipts, returns,
// adjustments. Then every movement is sorted by date and product (ties keep that order) and numbered.
// The stock figures on Products, drawn here (the same draws, in the same order, as when they were drawn at the insert)
// because the receipts below are sized to end on UnitsInStock.
products.each { p -> p.inStock = (short) rP.nextInt(120); p.onOrder = (short) (rP.nextInt(4) * 10); p.reorder = (short) (5 + rP.nextInt(4) * 5) }
Ints mvProduct = saleProduct, mvDay = saleDay, mvQty = saleQty, mvOrder = saleOrder, mvType = new Ints()
int salesMovements = saleProduct.n
salesMovements.times { mvType.add(0) }
List<String> MOVEMENT_TYPES = ['Sale', 'Receipt', 'Return', 'Adjustment']
def addMovement = { int type, int product, LocalDate day, int qty, int order ->     // order 0 = none
    mvType.add(type); mvProduct.add(product); mvDay.add((int) day.toEpochDay()); mvQty.add(qty); mvOrder.add(order)
}
products.each { p ->
    (0..59).each { mi ->
        LocalDate month = FIRST_DAY.plusMonths(mi)
        if (p.discontinuedFrom != null && !month.isBefore(p.discontinuedFrom as LocalDate)) return
        if (rM.nextDouble() < 0.9) addMovement(1, p.id as int, month.plusDays(rM.nextInt(10)), 40 + rM.nextInt(400), 0)
    }
}
salesMovements.intdiv(80).times {
    int s = rM.nextInt(salesMovements)
    LocalDate back = LocalDate.ofEpochDay(mvDay.get(s)).plusDays(5 + rM.nextInt(20))
    // A return after the last day would be a row from the future; it is logged on the last day instead (3 at S).
    addMovement(2, mvProduct.get(s), back.isAfter(LAST_DAY) ? LAST_DAY : back, 1 + rM.nextInt(3), mvOrder.get(s))
}
((PRODUCTS * 5).intdiv(2)).times { addMovement(3, products[rM.nextInt(PRODUCTS)].id as int, FIRST_DAY.plusDays(rM.nextInt(1826)), rM.nextInt(21) - 10, 0) }   // S: 200
int movementCount = mvType.n
long dayBase = FIRST_DAY.toEpochDay()
long[] order = new long[movementCount]                     // date | product | position: sorting these is the stable sort
def sortMovements = {
    for (int i = 0; i < movementCount; i++) order[i] = ((mvDay.get(i) - dayBase) << 44) | ((long) mvProduct.get(i) << 28) | i
    Arrays.sort(order)
}
sortMovements()
// THE LEDGER BALANCES (Data Warehousing's stock snapshot, Data Modeling S3 · 20): the stock starts at 0 on 2020-01-01,
// never goes negative, and ends on Products.UnitsInStock. Only the receipts are touched, so every row count, date,
// sale, return and adjustment stays as it was. Each receipt covers the stock the product will need until the next
// one, plus its reorder level, plus 0–30% more (from the quantity drawn above), in cases of 6; the last one is sized
// to land on UnitsInStock, and the one before it held back so that the last is at least a case. A product whose first movement is not a receipt gets its first receipt on 2020-01-01,
// the opening stock-in.
Map<Integer, Ints> ledgerOf = [:]
for (int k = 0; k < movementCount; k++) {
    int i = (int) (order[k] & ((1L << 28) - 1))
    ledgerOf.computeIfAbsent(mvProduct.get(i)) { new Ints() }.add(i)
}
List<List> stockTakes = []                                 // [product, correction]
products.each { p ->
    Ints led = ledgerOf[p.id as int]
    if (led == null) return
    List<Integer> seq = (0..<led.n).collect { led.get(it) }
    int first = seq.findIndexOf { mvType.get(it) == 1 }
    if (first < 0) throw new IllegalStateException("Product ${p.id} has stock movements but no receipt.")
    if (first > 0) { int r = seq.remove(first); seq.add(0, r); mvDay.a[r] = (int) dayBase }
    List<Integer> receipts = (0..<seq.size()).findAll { mvType.get(seq[it]) == 1 }
    // What the last receipt must bring: the stock it leaves has to end on UnitsInStock, so the stock it arrives to
    // may be at most UnitsInStock - (what happens after it) - a case of 6. The receipt before it is held to that.
    long afterLast = 0
    for (int k = receipts.last() + 1; k < seq.size(); k++) afterLast += mvQty.get(seq[k])
    long arriveAtMost = (p.inStock as int) - afterLast - 6
    long stock = 0
    receipts.eachWithIndex { int at, int ri ->
        int until = ri + 1 < receipts.size() ? receipts[ri + 1] : seq.size()
        long run = 0, low = 0, net = 0
        for (int k = at + 1; k < until; k++) { run += mvQty.get(seq[k]); low = Math.min(low, run) }
        net = run
        long need = Math.max(1L, -low - stock)                   // the least that keeps the stock at 0 or above
        int r = seq[at]
        long qty
        if (ri + 1 == receipts.size()) {
            qty = (p.inStock as int) - stock - net
            if (qty < need) { qty = need; stockTakes << [p.id as int, (p.inStock as int) - (stock + need + net)] }
        } else {
            double more = (mvQty.get(r) - 40) / 399.0 * 0.3
            qty = Math.max(6L, (long) Math.ceil(((-low) * (1 + more) + (p.reorder as int) - stock) / 6.0) * 6)
            if (ri + 2 == receipts.size()) qty = Math.max(need, Math.min(qty, arriveAtMost - stock - net))
        }
        mvQty.a[r] = (int) qty
        stock += qty + net
    }
}
// The year-end stock take: where what came back after the last receipt was more than UnitsInStock (none at S or M; a
// few at L), no receipt can end the ledger on it, and the count on 2024-12-31 writes the difference off.
stockTakes.each { List t -> addMovement(3, t[0] as int, LAST_DAY, t[1] as int, 0) }
if (stockTakes) {
    log.info("  the year-end stock take corrects {} products (what came back after their last receipt was more than their stock)", stockTakes.size())
    movementCount = mvType.n
    order = new long[movementCount]
}
sortMovements()
Map movementsOut = openSink('StockMovements', ['MovementID', 'ProductID', 'MovementDate', 'MovementType', 'Quantity', 'OrderID'])
for (int k = 0; k < movementCount; k++) {
    int i = (int) (order[k] & ((1L << 28) - 1))
    put(movementsOut, [k + 1, mvProduct.get(i), java.sql.Date.valueOf(LocalDate.ofEpochDay(mvDay.get(i))), MOVEMENT_TYPES[mvType.get(i)], mvQty.get(i),
                       mvOrder.get(i) == 0 ? null : mvOrder.get(i)])
}
closeSink(movementsOut)
order = null

// EXTENSION — dbt S2 (snapshots, timestamp strategy), ETL (a dimension watermark): UpdatedAt is the last price change
// or the day the product was discontinued, whichever is later; a product untouched since 2020 keeps its 2015–2019 date.
Random rPU = rnd('Products.UpdatedAt')
products.each { p ->
    int days = rPU.nextInt(1826), h = 8 + rPU.nextInt(10), m = rPU.nextInt(60)
    LocalDate last = (priceTimeline[p.id as int].last()[0] as LocalDate)
    if (p.discontinuedFrom != null && (p.discontinuedFrom as LocalDate).isAfter(last)) last = p.discontinuedFrom as LocalDate
    p.updated = last == FIRST_DAY ? LocalDate.of(2015, 1, 1).plusDays(days).atTime(h, m) : last.atTime(h, m)
}
insert('Products', ['ProductID', 'ProductName', 'SupplierID', 'CategoryID', 'QuantityPerUnit', 'UnitPrice', 'UnitCost', 'UnitsInStock', 'UnitsOnOrder', 'ReorderLevel', 'Discontinued',
                    'UpdatedAt'],
       products.collect { p -> [p.id, p.name, p.supplier, p.category, p.pack, p.currentPrice, ((p.currentPrice as BigDecimal) * new BigDecimal(p.costRatio as double)).setScale(2, RoundingMode.HALF_UP),
                                p.inStock, p.onOrder, p.reorder, p.discontinuedFrom != null,
                                Timestamp.valueOf(p.updated as LocalDateTime)] })
insert('PriceChanges', ['PriceChangeID', 'ProductID', 'ChangedDate', 'OldPrice', 'NewPrice'], priceChanges)

// Sales targets: one per category per month, from the previous year's actuals plus growth.
Random rST = rnd('SalesTargets')
List<List> targets = []
CATEGORIES.each { cat ->
    (0..59).each { mi ->
        LocalDate month = FIRST_DAY.plusMonths(mi)
        BigDecimal base = actual["${cat[0]}|${month.year - 1}-${month.monthValue}"] ?: actual["${cat[0]}|${month.year}-${month.monthValue}"] ?: new BigDecimal(8000)
        targets << [cat[0], java.sql.Date.valueOf(month), (base * new BigDecimal(1.08 + rST.nextDouble() * 0.06)).setScale(-2, RoundingMode.HALF_UP).setScale(2)]
    }
}
// PLANTED — Data Modeling S3 (drill-across): January 2025 is budgeted already and has no sales yet, so a full join of
// targets and sales has rows with a target and no sales.
CATEGORIES.each { cat ->
    BigDecimal base = actual["${cat[0]}|2024-1"] ?: new BigDecimal(8000)
    targets << [cat[0], java.sql.Date.valueOf(LocalDate.of(2025, 1, 1)), (base * new BigDecimal(1.10)).setScale(-2, RoundingMode.HALF_UP).setScale(2)]
}
insert('SalesTargets', ['CategoryID', 'TargetMonth', 'TargetAmount'], targets)

List<String> TABLES = ['Region', 'Territories', 'Categories', 'Suppliers', 'Products', 'PriceChanges', 'Shippers', 'Employees', 'EmployeeTerritories',
                       'Customers', 'CustomerChanges', 'Orders', 'Order Details', 'Invoices', 'StockMovements', 'SalesTargets', 'CourierConfirmations', 'WebOrders',
                       'OrderStatusHistory']

// ── JOIN INDEXES ──────────────────────────────────────────────────────────────────────────────
// Every column a lesson joins on that its table's primary key does not already start with, on the tables that grow
// with the orders, and on the two change histories an as-of join looks a date up in. Not indexed: the small reference
// tables (Products, Employees, Territories and the rest are read in a page or two), and ShipVia and ShipperID (a
// handful of shippers, so an index would never be chosen). No foreign keys: they would fix a load order and a drop
// order. Built after the rows are in, which is quicker than keeping them up to date row by row. ClickHouse has no
// secondary indexes (its tables are ordered by their primary keys), so there joinIndex does nothing.
int joinIndexes = 0
def joinIndex = { String table, List<String> cols ->
    if (CH) return
    String name = "ix_${table.toLowerCase().replace(' ', '_')}_${cols.collect { it.toLowerCase() }.join('_')}".toString()
    dbSql.execute("CREATE INDEX ${name} ON ${T(table)} (${cols.collect { q(it) }.join(', ')})".toString())
    joinIndexes++
}
if (SCALE == 'S') {
    joinIndex('Orders', ['CustomerID'])                          // Customers ⋈ Orders, in nearly every series
    joinIndex('Orders', ['EmployeeID'])                          // Employees ⋈ Orders
    joinIndex('Order Details', ['ProductID'])                    // Products ⋈ order lines (OrderID already leads the key)
    joinIndex('Invoices', ['OrderID'])                           // orders with and without an invoice
    joinIndex('OrderStatusHistory', ['OrderID', 'ChangedAt'])    // the join, and an order's status at a moment
    joinIndex('CourierConfirmations', ['OrderID'])               // not unique: re-sent confirmations (Learn SQL S2 · 48)
    joinIndex('WebOrders', ['OrderID'])                          // the web shop's payload behind an order
    joinIndex('StockMovements', ['ProductID'])                   // a product's stock ledger
    joinIndex('StockMovements', ['OrderID'])                     // the movements an order caused
    joinIndex('CustomerChanges', ['CustomerID', 'ChangedDate'])  // a customer's version as of a date
    joinIndex('PriceChanges', ['ProductID', 'ChangedDate'])      // a product's price as of a date
} else {
    // M AND L — PRIMARY KEYS ONLY, ON PURPOSE. L is the dataset of the performance lessons, Learn SQL S3 · 18–30, and
    // the curriculum has them run on primary keys only: the sequential scans they read are real, and every index they
    // add (S3 · 25, S3 · 30) is the learner's own, measured from a clean start. M is where those lessons run when L is
    // too large for a laptop, so M starts the same way. The indexes S gets are below, commented, each with what L does
    // without it (Off) and what uncommenting it changes (On). Uncommenting one builds it at M and L on PostgreSQL and
    // DuckDB, and takes the "before" away from the lesson that adds it.
    // Measured at L on PostgreSQL 16, warm, the best of five runs from a client; On is the plan PostgreSQL chose once the
    // index was there. A join or an aggregate over every row (revenue by rep, by product) hashes both tables with or
    // without an index; what an index speeds up is a filter or a join that picks out a few rows. On DuckDB (1.4.4) none
    // of them changed a plan: every lookup below stayed a sequential scan that zone maps prune, 0–12 ms with or without
    // its index, so there an index only adds its size to the file. All eleven: PostgreSQL +174 MB and 3 s to build;
    // DuckDB +272 MB (the file roughly doubles) and 4 s.
    //
    // joinIndex('Orders', ['CustomerID'])
    //     Off: one customer's orders (53 on average, 380 at most) is a parallel scan of all 1,000,000 (34 ms), and so is
    //     Customers ⋈ Orders for a few customers (the 11 of one city: 40 ms). On: a bitmap index scan (4 ms; that join
    //     2 ms). PostgreSQL 7 MB, DuckDB +24 MB.
    // joinIndex('Orders', ['EmployeeID'])
    //     Off: a rep's orders (13,500 on average, 74 reps) is a parallel scan of all 1,000,000 (32 ms). On: a bitmap scan
    //     (9 ms) that still reads 10,457 of the table's 23,500 pages, a rep's orders being spread all through it.
    //     PostgreSQL 7 MB, DuckDB +13 MB.
    // joinIndex('Order Details', ['ProductID'])
    //     Off: a product's lines (6,277 on average, 23,724 at most) is a parallel scan of all 2,510,619 (39 ms). On: a
    //     bitmap scan (4 ms). PostgreSQL 17 MB, DuckDB +28 MB.
    // joinIndex('Invoices', ['OrderID'])
    //     Off: an order's invoice, or whether it has one (EXISTS for a few orders), is a parallel scan of all 957,511
    //     (23 ms). On: a one-row index scan (2 ms). PostgreSQL 21 MB, DuckDB +8 MB.
    // joinIndex('OrderStatusHistory', ['OrderID', 'ChangedAt'])
    //     Off: an order's status at a moment is a parallel scan of all 1,990,135 changes (34 ms). On: a backward index
    //     scan that reads the latest change first and stops (1 ms). The largest: PostgreSQL 60 MB, DuckDB +115 MB.
    // joinIndex('CourierConfirmations', ['OrderID'])
    //     17,350 confirmations in 112 pages: 2 ms Off and On. Nothing to gain at this size.
    // joinIndex('WebOrders', ['OrderID'])
    //     Off: one order's payload is a parallel scan of all 274,983 payloads, 102 MB (21 ms). On: a one-row index scan
    //     (2 ms). PostgreSQL 6 MB, DuckDB +7 MB.
    // joinIndex('StockMovements', ['ProductID'])
    //     Off: a product's stock ledger (6,252 movements on average, 23,465 at most) is a parallel scan of all 2,500,849
    //     (40 ms). On: a bitmap scan (2 ms). PostgreSQL 17 MB, DuckDB +29 MB.
    // joinIndex('StockMovements', ['OrderID'])
    //     Off: the 2 or 3 movements an order caused is a parallel scan of all 2,500,849 (45 ms). On: an index scan
    //     (1 ms). PostgreSQL 38 MB, DuckDB +46 MB.
    // joinIndex('CustomerChanges', ['CustomerID', 'ChangedDate'])
    //     25,957 changes in 244 pages: 2 ms Off, 1 ms On. Little to gain. PostgreSQL 0.8 MB, DuckDB +2 MB.
    // joinIndex('PriceChanges', ['ProductID', 'ChangedDate'])
    //     1,235 changes in 16 pages: 1 ms Off and On. Nothing to gain at this size.
}
if (joinIndexes) log.info("  {} join indexes", joinIndexes)

// PostgreSQL plans from table statistics; give M and L theirs now, so the first EXPLAIN a learner runs is the real one.
if (vendor == 'POSTGRES' && SCALE != 'S') TABLES.each { dbSql.execute("ANALYZE ${T(it)}".toString()) }

// ── 7. _dataset_info: counts and checksums ──────────────────────────────────────────────────
// Canonical form (the same as Northwind's freeze test): every column, names sorted case-insensitively;
// NULL as <NULL>; numbers with trailing zeros stripped; timestamps yyyy-MM-dd HH:mm:ss; dates yyyy-MM-dd.
// ClickHouse is asked for its dates, times and booleans as text (toString), which is that form already.
//   S:    rows tab-joined and sorted; SHA-256 of the rows joined by newlines.
//   M, L: the SUM of every row's SHA-256 (as a 256-bit number, modulo 2^256), in hex — the same for any row order,
//         so the rows are read once as they come and never sorted or held (academy-verify.groovy does the same).
def canon = { v ->
    if (v == null) return '<NULL>'
    if (v instanceof Boolean) return v ? 'true' : 'false'
    if (v instanceof Number) { String s = new BigDecimal(v.toString()).stripTrailingZeros().toPlainString(); return s == '-0' ? '0' : s }
    if (v instanceof Timestamp) return (v as Timestamp).toLocalDateTime().toString().replace('T', ' ').padRight(19, ':00').substring(0, 19)
    if (v instanceof LocalDateTime) return v.toString().replace('T', ' ').padRight(19, ':00').substring(0, 19)
    if (v instanceof java.sql.Date) return (v as java.sql.Date).toLocalDate().toString()
    if (v instanceof LocalDate) return v.toString()
    return v.toString()
}
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
// A table's columns for the checksum, sorted case-insensitively, as SELECT expressions.
def checksumColumns = { String table ->
    if (!CH) return dbSql.rows("SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?".toString(), [SCHEMA, table])
                         .collect { it.column_name as String }.sort { a, b -> a.compareToIgnoreCase(b) }.collect { q(it) }
    dbSql.rows("SELECT name, type FROM system.columns WHERE database = ? AND table = ?".toString(), [SCHEMA, table])
         .sort { a, b -> (a.name as String).compareToIgnoreCase(b.name as String) }
         .collect { (it.type as String) ==~ /^(Nullable\()?(Date|Date32|DateTime|Bool)\b.*/ ? "toString(${q(it.name as String)}) AS ${q(it.name as String)}".toString() : q(it.name as String) }
}
List<List> info = []
TABLES.each { table ->
    List<String> cols = checksumColumns(table)
    String select = "SELECT ${cols.join(', ')} FROM ${T(table)}".toString()
    String hex
    int count = 0
    if (SCALE == 'S') {
        List<String> rows = []
        dbSql.eachRow(select) { r -> rows << (1..cols.size()).collect { canon(r.getObject(it)) }.join('\t') }
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
    info << [DATASET, SCALE, table, count, hex]
}
insert('_dataset_info', ['Dataset', 'Scale', 'TableName', 'RowCount', 'Checksum'], info)

log.info("=== {} scale {} installed in schema {}: {} orders, {} order lines, {} invoices ===", DATASET, SCALE, SCHEMA, orderId, lineCount, invoiceCount)
