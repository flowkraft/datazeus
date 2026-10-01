// @description Academy dataset: installs "the CRM export" — Northwind Company's customers as the sales team keeps them in another system (135 rows: its own keys, 12 duplicates, 3 prospects, the formats a second source brings). Set SCALE below. Idempotent — drops and recreates the schema.
// Bindings provided by GenericSeedExecutor:
//   dbSql  — groovy.sql.Sql connected to the target database
//   vendor — String (uppercase): POSTGRES, DUCKDB (the two supported so far)
//   log    — SLF4J Logger
//   params — Map; optional keys: SCALE, EXPORT_DIR (also write the export as crm_customers.csv)

import groovy.transform.CompileStatic
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
// SCALE — 'S', 'M' or 'L': which Northwind Company the CRM's customers are matched to (northwind_co_s gets
// crm_export_s, and so on). The export is 135 rows at every scale. params.SCALE, when the Seed Data tab passes one,
// wins over this line.
// ═════════════════════════════════════════════════════════════════════════════════════════════
String SCALE = (params?.SCALE ?: 'S').toString().toUpperCase()
// Also write the export as a file, crm_customers.csv, into this folder (UTF-8, header row, comma-delimited; a NULL
// is an empty field, an empty string is ""). Empty: the database table only.
String EXPORT_DIR = (params?.EXPORT_DIR ?: '').toString()
// ═════════════════════════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────────────────────
// WHAT THIS IS
// A second source system. The sales team keeps its accounts in a CRM; this is that CRM's customer export, taken on
// 2024-12-31 (the day Northwind Company stops). Same company, same customers, typed in by other people into another
// system: the CRM has its own keys and its own habits, and nobody ever cleaned it. Everything in it is derived from
// the installed Northwind Company of the same scale — nothing contradicts it — and it lives in its own schema, so
// nothing is added to Company. Nothing random between runs: the same source always gives the same export
// (_dataset_info). Design: kraft-src-company-biz/.docs/plan-academy-datasets.md, D3.
//
// THE TABLES (schema crm_export_<scale>)
//   crm_customers   135 rows, every column text, as a CRM export arrives:
//     crm_id        the CRM's own key, CRM-000001 …, numbered in the order the records were created
//     account_name  the company as the CRM spells it
//     erp_account   the Northwind CustomerID, where somebody linked the record to the ERP (not always)
//     contact_name, email, phone, city, country
//     active        Y/N, yes/no, 1/0 or true/false — whichever the screen that saved the record used.
//                   True = the customer ordered in 2024 (so never a prospect).
//     created_on    when the CRM record was created: dd/mm/yyyy for records migrated from the old CRM (created
//                   before 2020), yyyy-mm-dd after
//     credit_limit  text with thousands separators ("12,500.00", "12,500"), or without ("12500")
//   _plants         everything planted, one row per incident, and one row per mixed format with its counts
//   _dataset_info   row counts and checksums (academy-verify.groovy reads it)
//
// THE ROWS
//   120  the company's customers, one record each. At S that is all of them; at M and L the accounts a sales team
//        tracks: the near-name pair below, 23 lapsed customers (no order in 2024; the biggest over the years) and the
//        95 biggest buyers of 2024 — about the one-in-five lapsed that S has.
//    12  duplicates: a second record for one of the 120, created later by someone who did not find the first
//     3  prospects: companies that are in the CRM and not (yet) customers — no ERP account, no credit limit
//
// WHO NEEDS WHAT — an index, so nothing here is removed as "unused" (search the tag to find the code):
//   PLANTED   ETL S1 · 20    active in four spellings; created_on in two formats, with dd/mm dates whose day is 12
//                            or less (they parse as mm/dd without an error, into the wrong date); credit_limit as
//                            text with thousands separators
//   PLANTED   ETL S2 · 15    the 12 duplicates, each told apart by something different: a legal suffix added,
//                            capitals, stray spaces, a leading "The", the same name with the phone in another
//                            format, one still on the City the customer left (CustomerChanges), one under the
//                            contact the customer had before (CustomerChanges). Four of them carry the ERP account
//                            (an exact-key match finds those), eight do not.
//   PLANTED   ETL S2 · 15    the near-name pair: two DIFFERENT customers, same first word, one "… Foods" and one
//                            "… Food Hall". A loose fuzzy match merges them; it must not.
//   PLANTED   ETL S2 · 15    8 of the 120 records carry no ERP account (created before anyone linked them): the
//                            key join misses them, matching by name and phone finds them
//   PLANTED   ETL S2 · 15    email as '' , as NULL and as 'n/a' — three ways of saying "none"
//   (natural) ETL S2 · 15    phone formats: the ERP's own (already mixed), +CC NNN NNNN, 00CC NNNNNNN, NNN-NNNN —
//                            the last seven digits always match the ERP's
//   (natural) ETL S2 · 15    country as an ISO code (DE) or a name (Germany)
//   FIXTURE   ETL S1 · 50    the CRM source of the nightly load (with EXPORT_DIR, the file it arrives as)
//   FIXTURE   Data Warehousing S3 · 22   the small table to federate: install it on a second connection and join it
//                            to Company M's Orders on erp_account
//   FIXTURE   dbt (sources)  a second source to conform
// NOT HERE: addresses, postal codes and anything no lesson reads.
// ─────────────────────────────────────────────────────────────────────────────────────────────

if (!(SCALE in ['S', 'M', 'L'])) throw new IllegalArgumentException("SCALE must be S, M or L (got ${SCALE}).")
if (!(vendor in ['POSTGRES', 'DUCKDB'])) throw new IllegalArgumentException("Only PostgreSQL and DuckDB are supported so far (connection is ${vendor}).")
String DATASET = 'crm_export'
String SRC = "northwind_co_${SCALE.toLowerCase()}".toString()
String SCHEMA = "${DATASET}_${SCALE.toLowerCase()}".toString()
LocalDate AS_OF = LocalDate.of(2024, 12, 31)
LocalDate MIGRATED_BEFORE = LocalDate.of(2020, 1, 1)
int MATCHING = 120, DUPLICATES = 12, PROSPECTS = 3, NO_ERP = 8

// The |1| is a fixed part of every seed: every row, checksum and quoted figure is built from it, so it never changes.
def rnd = { String stream -> new Random(("${DATASET}|1|${SCALE}|${stream}".toString()).hashCode() * 2654435761L) }
def q = { String name -> "\"${name}\"".toString() }
def S = { String table -> "${SRC}.${q(table)}".toString() }
def T = { String table -> "${SCHEMA}.${q(table)}".toString() }
def exists = { String schema, String table ->
    dbSql.firstRow('SELECT COUNT(*) AS n FROM information_schema.tables WHERE table_schema = ? AND table_name = ?', [schema, table]).n as int > 0
}
def toDate = { Object v -> v == null ? null : v instanceof Timestamp ? ((Timestamp) v).toLocalDateTime().toLocalDate()
                         : v instanceof LocalDateTime ? ((LocalDateTime) v).toLocalDate()
                         : v instanceof java.sql.Date ? ((java.sql.Date) v).toLocalDate() : v as LocalDate }

if (!exists(SRC, '_dataset_info')) throw new IllegalStateException("Install Northwind Company scale ${SCALE} first (academy-northwind-co-install; schema ${SRC} not found).")
log.info("=== CRM export {} from {} on {}: {} customers, {} duplicates, {} prospects ===",
         SCHEMA, SRC, vendor, MATCHING, DUPLICATES, PROSPECTS)

// ── 1. the customers, as Company has them on 2024-12-31 ─────────────────────────────────────
// revenue24: what they bought in 2024 (active, the credit limit, the M/L selection); revenue: what they bought ever
// (which lapsed accounts the M/L selection keeps). Cancelled orders are not bought.
List<Map> customers = dbSql.rows("""
    SELECT c."CustomerID" AS id, c."CompanyName" AS name, c."ContactName" AS contact, c."City" AS city, c."Country" AS country,
           c."Phone" AS phone, c."Email" AS email, c."CreatedAt" AS created,
           COALESCE(r.revenue24, 0) AS revenue24, COALESCE(r.revenue, 0) AS revenue
    FROM ${S('Customers')} c
    LEFT JOIN (SELECT o."CustomerID" AS id, SUM(d."UnitPrice" * d."Quantity" * (1 - d."Discount")) AS revenue,
                      SUM(CASE WHEN o."OrderDate" >= '2024-01-01' THEN d."UnitPrice" * d."Quantity" * (1 - d."Discount") ELSE 0 END) AS revenue24
               FROM ${S('Orders')} o JOIN ${S('Order Details')} d ON d."OrderID" = o."OrderID"
               WHERE o."Status" <> 'Cancelled' AND o."OrderDate" < '2025-01-01'
               GROUP BY o."CustomerID") r ON r.id = c."CustomerID"
    ORDER BY c."CustomerID"
""".toString()).collect { row ->
    [id: row.id, name: row.name, contact: row.contact, city: row.city, country: row.country, phone: row.phone, email: row.email,
     created: toDate(row.created), revenue24: new BigDecimal(row.revenue24.toString()), revenue: new BigDecimal(row.revenue.toString())]
}
// The last change of City and of ContactName per customer (for the two duplicates that kept the old value). The old
// value held from the change before it (since; null = from the start) until this one: a record keeping it was created
// in between.
Map<String, Map> cityChange = [:], contactChange = [:]
dbSql.eachRow("""SELECT "CustomerID", "ChangedDate", "Attribute", "OldValue", "NewValue" FROM ${S('CustomerChanges')}
                 WHERE "Attribute" IN ('City', 'ContactName') ORDER BY "CustomerID", "ChangedDate", "CustomerChangeID\"""".toString()) { r ->
    Map target = r.Attribute == 'City' ? cityChange : contactChange
    LocalDate since = target[r.CustomerID as String]?.date as LocalDate
    target[r.CustomerID as String] = [date: toDate(r.ChangedDate), since: since, old: r.OldValue, new: r.NewValue]
}
if (customers.size() < MATCHING) throw new IllegalStateException("${SRC} has ${customers.size()} customers; the CRM export needs ${MATCHING}.")

// ── 2. which 120 ─────────────────────────────────────────────────────────────────────────────
// PLANTED — ETL S2 · 15: the near-name pair. Company's names are "<word> <kind>", so "Golden Foods" and "Golden Food
// Hall" can both exist; the pair taken is the one whose smaller customer bought the most in 2024.
Map<String, Map> byName = customers.collectEntries { [(it.name as String): it] }
List<List<Map>> pairs = customers.findAll { (it.name as String).endsWith(' Foods') && !(it.name as String).endsWith(' Fine Foods') }
                                 .collect { Map a -> [a, byName[(a.name as String).replaceFirst(/ Foods$/, ' Food Hall')]] }
                                 .findAll { it[1] != null }
if (!pairs) throw new IllegalStateException("${SRC} has no '<word> Foods' / '<word> Food Hall' pair for the near-name plant.")
List<Map> nearPair = pairs.max { p -> [p[0].revenue24, p[1].revenue24].min() }
// The rest: at S every customer. At M and L the accounts a sales team tracks — the biggest buyers of 2024, and one in
// five lapsed (no 2024 order; the biggest of those over the years), as S has about one in five.
List<Map> rest = customers.findAll { !(it in nearPair) }
def biggest = { List<Map> from, String by, int n -> from.sort(false) { a, b -> (b[by] <=> a[by]) ?: (a.id <=> b.id) }.take(n) }
int lapsedWanted = (MATCHING - 2).intdiv(5)
List<Map> lapsed = biggest(rest.findAll { (it.revenue24 as BigDecimal).signum() == 0 }, 'revenue', lapsedWanted)
List<Map> chosen = customers.size() == MATCHING ? customers
                 : nearPair + lapsed + biggest(rest.findAll { (it.revenue24 as BigDecimal).signum() > 0 }, 'revenue24', MATCHING - 2 - lapsed.size())
if (chosen.size() != MATCHING) throw new IllegalStateException("${SRC} gave ${chosen.size()} customers for the CRM, not ${MATCHING}.")
chosen = chosen.sort { it.id }

// ── 3. the CRM's habits ──────────────────────────────────────────────────────────────────────
Map<String, List<String>> COUNTRY = [                       // ISO code, calling code (Company's phones use these)
    'Germany': ['DE', '49'], 'France': ['FR', '33'], 'United Kingdom': ['GB', '44'], 'Spain': ['ES', '34'], 'Italy': ['IT', '39'],
    'Netherlands': ['NL', '31'], 'Belgium': ['BE', '32'], 'Austria': ['AT', '43'], 'Switzerland': ['CH', '41'], 'Sweden': ['SE', '46'],
    'Denmark': ['DK', '45'], 'Norway': ['NO', '47'], 'Finland': ['FI', '358'], 'Poland': ['PL', '48'], 'Portugal': ['PT', '351'],
    'Ireland': ['IE', '353'], 'USA': ['US', '1'], 'Canada': ['CA', '1'], 'Mexico': ['MX', '52'], 'Brazil': ['BR', '55'],
    'Argentina': ['AR', '54']]
Map<String, String> LEGAL_SUFFIX = [
    'Germany': 'GmbH', 'Austria': 'GmbH', 'Switzerland': 'AG', 'France': 'SARL', 'United Kingdom': 'Ltd', 'Ireland': 'Ltd',
    'Spain': 'S.L.', 'Italy': 'S.p.A.', 'Netherlands': 'B.V.', 'Belgium': 'NV', 'Sweden': 'AB', 'Denmark': 'ApS', 'Norway': 'AS',
    'Finland': 'Oy', 'Poland': 'Sp. z o.o.', 'Portugal': 'Lda', 'USA': 'Inc.', 'Canada': 'Inc.', 'Mexico': 'S.A. de C.V.',
    'Brazil': 'Ltda', 'Argentina': 'S.A.']
List<List<String>> ACTIVE_SPELLINGS = [['Y', 'N'], ['yes', 'no'], ['1', '0'], ['true', 'false']]
List<Integer> ACTIVE_WEIGHTS = [50, 25, 15, 10]
DateTimeFormatter DMY = DateTimeFormatter.ofPattern('dd/MM/yyyy')
Map<String, Integer> formatCounts = [:].withDefault { 0 }

// Company's phones end in seven digits (NNN NNNN) after the calling code, whatever the format; the CRM keeps those
// seven and writes the rest its own way.
def crmPhone = { Random r, String erpPhone, String country, int style ->
    String digits = erpPhone.replaceAll(/\D/, '')
    String national = digits.substring(digits.length() - 7)
    String cc = COUNTRY[country]?.get(1)
    if (cc == null) style = 0
    switch (style) {
        case 1: return "+${cc} ${national.substring(0, 3)} ${national.substring(3)}".toString()
        case 2: return "00${cc} ${national}".toString()
        case 3: return "${national.substring(0, 3)}-${national.substring(3)}".toString()
        default: return erpPhone
    }
}
def phoneStyle = { Random r -> int x = r.nextInt(100); x < 45 ? 0 : x < 65 ? 1 : x < 85 ? 2 : 3 }
def crmCountry = { Random r, String country ->
    String iso = COUNTRY[country]?.get(0)
    boolean code = iso != null && r.nextInt(100) < 55
    formatCounts["country as ${code ? 'an ISO code' : 'a name'}".toString()]++
    code ? iso : country
}
def spellActive = { Random r, boolean active ->
    int x = r.nextInt(100), i = 0, acc = ACTIVE_WEIGHTS[0]
    while (x >= acc) { i++; acc += ACTIVE_WEIGHTS[i] }
    String s = ACTIVE_SPELLINGS[i][active ? 0 : 1]
    formatCounts["active as ${ACTIVE_SPELLINGS[i].join('/')}".toString()]++
    s
}
// The limit: a quarter of 2024's revenue, rounded up to 2,500 (at least 2,500, at most 250,000).
def creditLimit = { BigDecimal revenue24 ->
    BigDecimal step = new BigDecimal(2500)
    BigDecimal v = revenue24.multiply(new BigDecimal('0.25')).divide(step, 0, RoundingMode.CEILING).multiply(step)
    v.max(step).min(new BigDecimal(250_000))
}
def spellLimit = { Random r, BigDecimal v ->
    int x = r.nextInt(100)
    String s = x < 50 ? String.format(Locale.ROOT, '%,.2f', v) : x < 85 ? String.format(Locale.ROOT, '%,d', v.longValue()) : v.toBigInteger().toString()
    formatCounts["credit_limit as ${x < 50 ? '12,500.00' : x < 85 ? '12,500' : '12500'}".toString()]++
    s
}
// City and contact as the CRM holds them: its own record, so without Company's stray spaces and capitals.
def tidy = { String s ->
    if (s == null) return null
    String t = s.trim().replaceAll(/\s+/, ' ')
    (t == t.toUpperCase() || t == t.toLowerCase()) && t.length() > 3 ? t.toLowerCase().split(' ').collect { it.capitalize() }.join(' ') : t
}

// ── 4. the 120 records ───────────────────────────────────────────────────────────────────────
Random rC = rnd('crm_customers')
List<Map> records = []
chosen.each { Map c ->
    boolean active = (c.revenue24 as BigDecimal).signum() > 0
    records << [kind: 'customer', src: c, account_name: c.name, erp_account: c.id, contact_name: tidy(c.contact as String),
                email: c.email, phone: crmPhone(rC, c.phone as String, c.country as String, phoneStyle(rC)), city: tidy(c.city as String),
                country: crmCountry(rC, c.country as String), active: spellActive(rC, active),
                created: (c.created as LocalDate).minusDays(rC.nextInt(46)),     // a lead in the CRM before the first order
                credit_limit: spellLimit(rC, creditLimit(c.revenue24 as BigDecimal)), notes: []]
}

// ── 5. the duplicates ────────────────────────────────────────────────────────────────────────
// PLANTED — ETL S2 · 15. Twelve customers get a second record, each different from its first in one documented way.
// Eligible: not the near-name pair (it must stay two clean records).
Random rD = rnd('crm_customers/duplicates')
List<Map> eligible = records.findAll { !(it.src in nearPair) }
Collections.shuffle(eligible, rD)
// one whose City changed after they joined, and one whose contact did, so a record from before the change exists
// (at least half a year between the first record and the change, and since the change before it)
def oldValueWindow = { Map change, Map record ->
    if (change == null) return false
    LocalDate start = [(record.created as LocalDate), (change.since as LocalDate) ?: LocalDate.MIN].max()
    (change.date as LocalDate).isAfter(start.plusDays(180))
}
Map staleCity = eligible.find { oldValueWindow(cityChange[it.erp_account], it) }
Map oldContact = eligible.find { it != staleCity && oldValueWindow(contactChange[it.erp_account], it) }
if (staleCity == null || oldContact == null) throw new IllegalStateException("${SRC} has no customer whose City or contact changed long after they joined.")
List<Map> originals = [staleCity, oldContact] + eligible.findAll { !(it in [staleCity, oldContact]) }.take(DUPLICATES - 2)
// What sets each duplicate apart, and whether it carries the ERP account (4 of 12 do).
List<List> VARIANTS = [
    ['still on the old City',                   true ],
    ['the contact before the change, capitals', true ],
    ['legal suffix added, capitals',            false],
    ['legal suffix added',                      false],
    ['legal suffix added',                      true ],
    ['capitals',                                false],
    ['stray spaces',                            true ],
    ['stray spaces, capitals',                  false],
    ['same name, phone in another format',      false],
    ['same name, phone in another format, no email', false],
    ['a leading "The"',                         false],
    ['legal suffix added, stray spaces',        false],
]
List<Map> duplicates = []
originals.eachWithIndex { Map o, int i ->
    String variant = VARIANTS[i][0]
    Map c = o.src as Map
    String name = c.name as String
    String contact = o.contact_name as String
    String city = o.city as String
    LocalDate from = (o.created as LocalDate).plusDays(30), until = AS_OF.minusDays(30)
    if (variant.contains('legal suffix')) name = "${name} ${LEGAL_SUFFIX[c.country] ?: 'Ltd'}".toString()
    if (variant.contains('stray spaces')) name = " ${name.replaceFirst(' ', '  ')} ".toString()
    if (variant.contains('"The"')) name = "The ${name}".toString()
    if (variant.contains('capitals')) name = name.toUpperCase()
    Map change = variant == 'still on the old City' ? cityChange[c.id] : variant.startsWith('the contact before') ? contactChange[c.id] : null
    if (change) {
        if (change.since) from = [from, (change.since as LocalDate).plusDays(1)].max()
        until = (change.date as LocalDate).minusDays(1)
        if (change.is(cityChange[c.id])) city = tidy(change.old as String) else contact = tidy(change.old as String)
    }
    int style = phoneStyle(rD)
    String phone = crmPhone(rD, c.phone as String, c.country as String, style)
    if (variant.contains('phone in another format')) while (phone == o.phone) phone = crmPhone(rD, c.phone as String, c.country as String, 1 + rD.nextInt(3))
    long span = Math.max(1, until.toEpochDay() - from.toEpochDay())
    duplicates << [kind: 'duplicate', src: c, of: o, variant: variant, account_name: name, erp_account: VARIANTS[i][1] ? c.id : null,
                   contact_name: contact, email: variant.endsWith('no email') ? null : c.email, phone: phone, city: city,
                   country: crmCountry(rD, c.country as String), active: spellActive(rD, (c.revenue24 as BigDecimal).signum() > 0),
                   created: from.plusDays(rD.nextInt((int) span)), credit_limit: spellLimit(rD, creditLimit(c.revenue24 as BigDecimal)), notes: []]
}

// ── 6. the prospects ─────────────────────────────────────────────────────────────────────────
// In the CRM, not (yet) customers. Their first words are not Company's, so no name can collide with a customer.
Random rP = rnd('crm_customers/prospects')
List<String> firsts = customers.collect { (it.contact as String).trim().split(/\s+/)[0].toLowerCase().capitalize() }.unique().sort()
List<String> lasts = customers.collect { (it.contact as String).trim().split(/\s+/)[-1].toLowerCase().capitalize() }.unique().sort()
List<List<String>> PROSPECT_NAMES = [['Heather', 'Provisions'], ['Linden', 'Grocers'], ['Marble', 'Delicatessen']]
List<Map> placePool = customers.collect { [city: tidy(it.city as String), country: it.country, phone: it.phone] }.unique { [it.city, it.country] }
                               .sort { a, b -> (a.country <=> b.country) ?: (a.city <=> b.city) }
List<Map> prospects = PROSPECT_NAMES.collect { List<String> n ->
    Map place = placePool[rP.nextInt(placePool.size())]
    String name = n.join(' ')
    String contact = "${firsts[rP.nextInt(firsts.size())]} ${lasts[rP.nextInt(lasts.size())]}".toString()
    String digits = String.valueOf(100 + rP.nextInt(900)) + String.valueOf(1000 + rP.nextInt(9000))
    [kind: 'prospect', src: null, account_name: name, erp_account: null, contact_name: contact,
     email: "hello@${name.toLowerCase().replace(' ', '')}.example".toString(),
     phone: crmPhone(rP, digits, place.country as String, 1 + rP.nextInt(3)), city: place.city,
     country: crmCountry(rP, place.country as String), active: spellActive(rP, false),
     created: AS_OF.minusDays(10 + rP.nextInt(80)), credit_limit: '', notes: []]
}

// ── 7. keys, dates, and the "none" emails ────────────────────────────────────────────────────
// crm_id follows creation order, as a CRM's sequence does, so a duplicate always has the later key.
List<Map> all = (records + duplicates + prospects).sort { a, b -> ((a.created as LocalDate) <=> (b.created as LocalDate)) ?: (a.account_name.trim() <=> b.account_name.trim()) }
all.eachWithIndex { Map r, int i -> r.crm_id = String.format('CRM-%06d', i + 1) }
Random rF = rnd('crm_customers/formats')
all.each { Map r ->
    LocalDate d = r.created as LocalDate
    boolean migrated = d.isBefore(MIGRATED_BEFORE)
    r.created_on = migrated ? d.format(DMY) : d.toString()
    formatCounts["created_on as ${migrated ? 'dd/mm/yyyy' : 'yyyy-mm-dd'}".toString()]++
    if (migrated && d.dayOfMonth <= 12 && d.dayOfMonth != d.monthValue) formatCounts['created_on dd/mm with day <= 12 (reads as a valid mm/dd, the wrong date)']++
}
// PLANTED — ETL S2 · 15: three ways of saying "no email", on the 120 (a duplicate's missing email is its own plant)
List<Map> withEmail = all.findAll { it.kind == 'customer' && !(it.src in nearPair) }
Collections.shuffle(withEmail, rF)
withEmail.take(6).each { it.email = ''; it.notes << "email is '' (an empty string)" }
withEmail.drop(6).take(4).each { it.email = null; it.notes << 'email is NULL' }
withEmail.drop(10).take(2).each { it.email = 'n/a'; it.notes << "email is 'n/a'" }
// PLANTED — ETL S2 · 15: eight of the 120 were never linked to the ERP. Not a duplicate's original: those stay
// findable by key, so each duplicate teaches one thing.
List<Map> linkable = all.findAll { it.kind == 'customer' && !(it.src in nearPair) && !(it in originals) }
Collections.shuffle(linkable, rF)
linkable.take(NO_ERP).each { it.erp_account = null }

// ── 8. write ─────────────────────────────────────────────────────────────────────────────────
List<String> COLUMNS = ['crm_id', 'account_name', 'erp_account', 'contact_name', 'email', 'phone', 'city', 'country', 'active', 'created_on', 'credit_limit']
dbSql.execute("DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE".toString())
dbSql.execute("CREATE SCHEMA ${SCHEMA}".toString())
dbSql.execute("CREATE TABLE ${T('crm_customers')} (${COLUMNS.collect { "${q(it)} VARCHAR(80)" }.join(', ')})".toString())
dbSql.execute("CREATE TABLE ${T('_plants')} (\"plant\" VARCHAR(40), \"lesson\" VARCHAR(40), \"crm_id\" VARCHAR(12), \"ref\" VARCHAR(40), \"detail\" VARCHAR(200))".toString())
def insert = { String table, List<String> cols, List<List> rows ->
    String head = "INSERT INTO ${T(table)} (${cols.collect { q(it) }.join(', ')}) VALUES ".toString()
    String one = "(${cols.collect { '?' }.join(', ')})".toString()
    rows.collate(200).each { chunk -> dbSql.execute(head + ([one] * chunk.size()).join(', '), chunk.flatten() as List) }
    log.info("  {}: {} rows", table, rows.size())
}
List<List> rows = all.collect { Map r -> COLUMNS.collect { r[it] } }
insert('crm_customers', COLUMNS, rows)

List<List> plants = []
duplicates.each { Map d ->
    plants << ['duplicate', 'ETL S2 · 15', d.crm_id, "${d.of.crm_id} (${d.src.id})".toString(),
               "${d.variant}${d.erp_account ? '; carries the ERP account' : ''}".toString()]
}
nearPair.each { Map c ->
    Map mine = records.find { it.src == c }, other = records.find { it.src != c && it.src in nearPair }
    plants << ['near name', 'ETL S2 · 15', mine.crm_id, other.crm_id,
               "${c.name} (${c.id}) and ${other.src.name} (${other.src.id}) are different customers".toString()]
}
prospects.each { Map p -> plants << ['prospect', 'ETL S2 · 15', p.crm_id, null, "${p.account_name}: in the CRM only".toString()] }
linkable.take(NO_ERP).each { Map r -> plants << ['no erp_account', 'ETL S2 · 15', r.crm_id, r.src.id, 'the record was never linked to the ERP'] }
all.findAll { it.notes }.sort { it.crm_id }.each { Map r -> r.notes.each { n -> plants << ['email', 'ETL S2 · 15', r.crm_id, r.src?.id, n] } }
formatCounts.keySet().sort().each { String k ->
    plants << ['format', k.startsWith('country') ? 'ETL S2 · 15' : 'ETL S1 · 20', null, k.split(' ')[0], "${k}: ${formatCounts[k]} rows".toString()]
}
insert('_plants', ['plant', 'lesson', 'crm_id', 'ref', 'detail'], plants)

// ── 9. _dataset_info: counts and checksums ──────────────────────────────────────────────────
// Canonical form (Northwind Company's, so academy-verify.groovy checks this schema unchanged with SCHEMA=crm_export_s):
// every column, names sorted case-insensitively; NULL as <NULL>. Scale S: rows tab-joined and sorted, SHA-256 of the
// rows joined by newlines; M and L: the sum of every row's SHA-256 modulo 2^256 (RowSum).
dbSql.execute("CREATE TABLE ${T('_dataset_info')} (\"Dataset\" VARCHAR(40), \"Scale\" VARCHAR(2), \"TableName\" VARCHAR(40), \"RowCount\" INTEGER, \"Checksum\" VARCHAR(64))".toString())
List<List> info = ['crm_customers', '_plants'].collect { String table ->
    List<String> cols = dbSql.rows('SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?', [SCHEMA, table])
                             .collect { it.column_name as String }.sort { a, b -> a.compareToIgnoreCase(b) }
    String select = "SELECT ${cols.collect { q(it) }.join(', ')} FROM ${T(table)}".toString()
    if (SCALE == 'S') {
        List<String> lines = []
        dbSql.eachRow(select) { r -> lines << (1..cols.size()).collect { RowSum.canon(r.getObject(it)) }.join('\t') }
        lines.sort()
        [DATASET, SCALE, table, lines.size(), MessageDigest.getInstance('SHA-256').digest(lines.join('\n').getBytes('UTF-8')).collect { String.format('%02x', it) }.join()]
    } else {
        RowSum rs = new RowSum()
        dbSql.eachRow(select) { r -> rs.add(r, cols.size()) }
        [DATASET, SCALE, table, rs.count, rs.hex()]
    }
}
insert('_dataset_info', ['Dataset', 'Scale', 'TableName', 'RowCount', 'Checksum'], info)

// ── 10. the file, when asked for ────────────────────────────────────────────────────────────
if (EXPORT_DIR) {
    File dir = new File(EXPORT_DIR)
    dir.mkdirs()
    File out = new File(dir, 'crm_customers.csv')
    def field = { Object v ->
        if (v == null) return ''
        String s = v.toString()
        s.isEmpty() || s.contains(',') || s.contains('"') || s.contains('\n') ? '"' + s.replace('"', '""') + '"' : s
    }
    out.withWriter(StandardCharsets.UTF_8.name()) { w ->
        w << COLUMNS.join(',') << '\n'
        rows.each { List r -> w << r.collect { field(it) }.join(',') << '\n' }
    }
    log.info("  wrote {} ({} rows)", out.absolutePath, rows.size())
}

log.info("=== {} installed from {}: {} rows — {} customers ({} without an ERP account), {} duplicates, {} prospects ===",
         SCHEMA, SRC, all.size(), records.size(), NO_ERP, duplicates.size(), prospects.size())
