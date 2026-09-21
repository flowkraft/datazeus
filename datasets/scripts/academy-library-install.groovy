// @description Academy dataset: installs "The Riverside Library" lending spreadsheet (2019-2026) into its own schema — the brief's 26 rows and the whole file since 2019. Idempotent — drops and recreates the schema.
// Bindings provided by GenericSeedExecutor:
//   dbSql  — groovy.sql.Sql connected to the target database
//   vendor — String (uppercase): POSTGRES, DUCKDB (the two supported so far)
//   log    — SLF4J Logger
//   params — Map; optional keys: SCALE (S, the only one), VERSION (1)

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

// ═════════════════════════════════════════════════════════════════════════════════════════════
// SCALE — 'S' is the only scale. There is no M: no Series 2 episode asks for more than S (S2 · 40 asks for
// at least 10,000 loans). params.SCALE, when the Seed Data tab passes one, wins over this line.
// ═════════════════════════════════════════════════════════════════════════════════════════════
String SCALE = (params?.SCALE ?: 'S').toString().toUpperCase()

// THE SIZES. S is the lessons' dataset: leave it alone once a lesson is published on it (a different S is a new
// VERSION — datasets/README.md, "Rules for changing a relational dataset"). About 8 s and ~1.3 MB on DuckDB on a laptop
// (2026-09-19); PostgreSQL not measured yet.
Map<String, Map<String, Object>> SIZES = [
    S: [LOANS  : 14_000,          // generated loans, 2019-03-04 .. 2026-07-21; the brief's 26 rows come on top
        MEMBERS: 600,             // everyone who ever joined, the brief's 10 included ("roughly six hundred")
        COPIES : 4_000],          // physical books on the shelves, the brief's 17 included ("about four thousand")
]
int    VERSION = (params?.VERSION ?: 1) as int
// ═════════════════════════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────────────────────
// WHAT THIS IS
// The Riverside Library's lending spreadsheet — the greenfield domain of Data Modeling Series 2
// (courses/datamodeling/brief/README.md). Two tables with the spreadsheet's own sixteen columns, nothing else:
//   lending_log       the brief's lending-log.csv, all 26 rows exactly as the learner sees them in the brief
//   lending_log_full  the whole spreadsheet since 2019: ~14,000 loans, 600 members, 4,000 books, the same 26
//                     rows among them, unchanged
// WHY NO normalised tables (members, titles, copies, loans …): in Series 2 the learner DESIGNS that model, in
// practice.*, and loads this spreadsheet into it. Shipping ours would be the answer key the brief forbids
// ("seeing our answer first costs you the entire exercise"), and LibraryChecks goes through the learner's four
// views, never through tables of ours. Branches, ebooks and reservations are S2 · 60's change requests — the
// learner designs them; the checks bring their own rows.
// The rules every change to this file must follow are Northwind Company's (academy-northwind-co-install.groovy):
// frozen once published (bump VERSION); ONE RANDOM STREAM PER TABLE; no faker libraries, every name comes from
// the word lists here; money is BigDecimal; every install writes row counts and checksums to _dataset_info.
//
// HOW lending_log_full IS MADE, so it reads like the brief's file and loads into any model that took the 26 rows:
//   - one row per loan, the member's details repeated on every row, exactly like the brief;
//   - an email always belongs to the same person, the same joined date and the same member_status;
//   - a book is (title, copy_note): multi-copy titles say "copy 1", "copy 2" … in copy_note, single copies leave it
//     blank; an ISBN always belongs to one title;
//   - a copy is never out on two loans at once, and nobody holds two copies of the same title at once;
//   - blank `returned` means still out on 2026-07-21 ("today", the brief's last row); loans run 21 days, 20p a day
//     after that, and about half the fines are waived (fine_paid blank);
//   - the reference-only book is never lent again — the brief's one row is the only one.
// The generated rows carry no defects of their own: the defects to design around are the brief's, in its rows.
//
// WHO NEEDS WHAT — an index, so nothing here is removed as "unused" (search the tag to find the code):
//   FIXTURE   S2 · 00–30, 42, 50   lending_log: the brief's 26 rows, verbatim (FITS, REFUSES, ANSWERS in 05; the
//                                  author2 group, blank returned, notes, Jonah who left, the lent reference book)
//   PLANTED   S2 · 40               lending_log_full: ~14,000 loans over seven years, so CloudBeaver's EXPLAIN on
//                                  PostgreSQL shows an index paying off for "copies currently out per title",
//                                  "fines owed per member" and "overdue today"
//   PLANTED   S2 · 40               overdue today: loans still out past their 21 days, and a few lost books out
//                                  for years
//   PLANTED   S2 · 35 (and 10)      titles whose copies are of two editions (Dune and The Pragmatic Programmer
//                                  among them): isbn → title while (title, copy) is the key — the library's own
//                                  BCNF-but-not-3NF case, visible wherever a model keeps isbn next to title on a copy
//   (natural) S2 · 30               members who lapsed or left, with years of loans behind them
//   (natural) S2 · 40               skew: a few titles are always out, most sit on the shelf
// NO SECONDARY INDEXES, on purpose: S2 · 40 starts from sequential scans and adds them.
// ─────────────────────────────────────────────────────────────────────────────────────────────

if (!SIZES.containsKey(SCALE)) throw new IllegalArgumentException("SCALE must be S, the library's only scale (got ${SCALE}).")
if (!(vendor in ['POSTGRES', 'DUCKDB'])) throw new IllegalArgumentException("Only PostgreSQL and DuckDB are supported so far (connection is ${vendor}).")
Map SIZE = SIZES[SCALE]

String DATASET = 'library'
String SCHEMA  = "${DATASET}_${SCALE.toLowerCase()}".toString()
LocalDate FIRST_DAY = LocalDate.of(2019, 3, 4)            // the spreadsheet's first day (Sarah Okonkwo joined)
LocalDate TODAY     = LocalDate.of(2026, 7, 21)           // "today" in every lesson: the brief's last row
LocalDate FIXTURE_FROM = LocalDate.of(2025, 11, 1)        // the brief's books and members stop in the generated rows here
int LOAN_DAYS = 21
BigDecimal FINE_PER_DAY = new BigDecimal('0.20')

int LOANS = SIZE.LOANS as int, MEMBERS = SIZE.MEMBERS as int, COPIES = SIZE.COPIES as int

log.info("=== Academy dataset {} v{} scale {} → schema {} on {}: {} loans, {} members, {} copies ===",
         DATASET, VERSION, SCALE, SCHEMA, vendor, LOANS, MEMBERS, COPIES)

// ── deterministic helpers ────────────────────────────────────────────────────────────────────
def rnd = { String table -> new Random(("${DATASET}|${VERSION}|${SCALE}|${table}".toString()).hashCode() * 2654435761L) }
def pick = { Random r, List xs -> xs[r.nextInt(xs.size())] }
def q = { String name -> "\"${name}\"".toString() }
def T = { String table -> "${SCHEMA}.\"${table}\"".toString() }
def date = { String s -> s ? java.sql.Date.valueOf(s) : null }
def sqlDate = { LocalDate d -> d == null ? null : java.sql.Date.valueOf(d) }

// Picks an index with probability proportional to its weight: running totals added up once, searched by halving.
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

// ── the brief's spreadsheet, byte for byte (courses/datamodeling/brief/lending-log.csv) ───────
// FIXTURE — S2 · 00–30, 42, 50. Change it only together with the brief's CSV: they are the same 26 rows.
String LENDING_LOG_CSV = '''\
member,email,joined,member_status,title,author,author2,isbn,category,copy_note,notes,borrowed,returned,fine_paid,last_edited,edited_by
Sarah Okonkwo,sarah.ok@example.com,2019-03-04,active,Dune,Frank Herbert,,9780441013593,Science Fiction,copy 1,,2026-06-02,2026-06-20,,2026-06-20,TB
Sarah Okonkwo,sarah.ok@example.com,2019-03-04,active,The Silk Roads,Peter Frankopan,,9781408839997,History,,,2026-07-01,2026-07-19,,2026-07-19,TB
Tom Béres,tberes@example.com,2020-11-12,active,Dune,Frank Herbert,,9780441013593,Science Fiction,copy 2,,2026-06-05,2026-07-02,2.40,2026-07-02,HP
Tom Béres,tberes@example.com,2020-11-12,active,Dune Messiah,Frank Herbert,,9780575074354,Science Fiction,,"2nd in series",2026-07-10,,,2026-07-10,HP
Aisha Rahman,a.rahman@example.com,2021-01-20,active,Good Omens,Terry Pratchett,Neil Gaiman,9780552137034,Fantasy,,,2026-05-14,2026-06-01,,2026-06-01,TB
Aisha Rahman,a.rahman@example.com,2021-01-20,active,Dune,Frank Herbert,,9780441013593,Science Fiction,copy 1,,2026-07-08,,,2026-07-08,TB
Aisha Rahman,a.rahman@example.com,2021-01-20,active,The Silk Roads,Peter Frankopan,,9781408839997,History,,,2026-07-08,,,2026-07-08,TB
Marcus Lindqvist,marcus.l@example.com,2019-09-30,active,Riverside: A Local History,H. Pettifer,,,Local History,donated 1998,"no ISBN - donated; fragile spine",2026-04-11,2026-05-02,,2026-05-02,HP
Marcus Lindqvist,marcus.l@example.com,2019-09-30,active,The Pragmatic Programmer,Andrew Hunt,David Thomas,9780135957059,Computing,2nd edition,"20th anniversary ed.",2026-06-18,2026-07-09,,2026-07-09,TB
Priya Nair,p.nair@example.com,2022-06-15,active,The Pragmatic Programmer,Andrew Hunt,David Thomas,020161622X,Computing,1st edition,"ISBN-10; superseded by the 2nd ed.",2026-06-20,2026-07-14,0.60,2026-07-14,HP
Priya Nair,p.nair@example.com,2022-06-15,active,Where the Wild Things Are,Maurice Sendak,,9780099408390,Picture Books 3-5,,"large print",2026-07-15,,,2026-07-15,TB
Danny O'Shea,d.oshea@example.com,2023-02-02,lapsed,Dune,Frank Herbert,,9780441013593,Science Fiction,copy 3,,2026-05-30,2026-06-21,,2026-06-21,HP
Danny O'Shea,d.oshea@example.com,2023-02-02,lapsed,Good Omens,Terry Pratchett,Neil Gaiman,9780552137034,Fantasy,,,2026-07-02,2026-07-20,,2026-07-20,TB
Elena Fischer,e.fischer@example.com,2020-05-18,active,Riverside: A Local History,H. Pettifer,,,Local History,donated 1998,"no ISBN - donated",2026-06-25,,,2026-06-25,TB
Elena Fischer,e.fischer@example.com,2020-05-18,active,The Silk Roads,Peter Frankopan,,9781408839997,History,,"trans. from the German",2026-03-09,2026-04-04,1.20,2026-04-04,HP
Elena Fischer,e.fischer@example.com,2020-05-18,active,Dune,Frank Herbert,,9780441013593,Science Fiction,copy 4,,2026-07-11,,,2026-07-11,TB
Kwame Mensah,k.mensah@example.com,2024-08-21,active,Where the Wild Things Are,Maurice Sendak,,9780099408390,Picture Books 3-5,,,2026-06-09,2026-06-27,,2026-06-27,HP
Kwame Mensah,k.mensah@example.com,2024-08-21,active,The Very Hungry Caterpillar,Eric Carle,,9780241003008,Picture Books 3-5,,"board book; ages 0-3 really",2026-06-09,2026-06-27,,2026-06-27,HP
Sarah Okonkwo,sarah.ok@example.com,2019-03-04,active,Good Omens,Terry Pratchett,Neil Gaiman,9780552137034,Fantasy,,,2026-07-16,,,2026-07-16,TB
Tom Béres,tberes@example.com,2020-11-12,active,Sapiens,Yuval Noah Harari,,9780099590088,History,,"orig. Hebrew; trans. Harari + Purcell",2026-07-17,,,2026-07-17,TB
Marcus Lindqvist,marcus.l@example.com,2019-09-30,active,Dune,Frank Herbert,,9780441013593,Science Fiction,copy 2,,2026-07-19,,,2026-07-19,HP
Priya Nair,p.nair@example.com,2022-06-15,active,The Illustrated Riverside,H. Pettifer,J. Pettifer,,Local History,fragile - ref only,"no ISBN; reference only - do not lend",2026-05-05,2026-05-12,,2026-05-12,HP
Jonah Whitfield,j.whitfield@example.com,2021-07-07,left,Sapiens,Yuval Noah Harari,,9780099590088,History,,,2026-02-03,2026-02-24,,2026-02-24,HP
Jonah Whitfield,j.whitfield@example.com,2021-07-07,left,Dune,Frank Herbert,,9780441013593,Science Fiction,copy 3,,2026-03-01,2026-03-19,0.80,2026-03-19,TB
Rosa Delgado,r.delgado@example.com,2025-01-14,active,The Very Hungry Caterpillar,Eric Carle,,9780241003008,Picture Books 3-5,,,2026-07-20,,,2026-07-20,TB
Rosa Delgado,r.delgado@example.com,2025-01-14,active,Dune,Frank Herbert,,9780593099322,Science Fiction,copy 5,"2021 film tie-in reprint - different ISBN, same book",2026-07-21,,,2026-07-21,TB
'''
// One CSV line to its fields: commas, double quotes around a field, "" for a quote inside one; blank is NULL.
def csvFields = { String line ->
    List<String> out = []
    StringBuilder f = new StringBuilder()
    boolean quoted = false
    for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i)
        if (quoted) {
            if (c == '"' as char) { if (i + 1 < line.length() && line.charAt(i + 1) == '"' as char) { f.append('"'); i++ } else quoted = false }
            else f.append(c)
        } else if (c == '"' as char) quoted = true
        else if (c == ',' as char) { out << f.toString(); f.setLength(0) }
        else f.append(c)
    }
    out << f.toString()
    out.collect { it.isEmpty() ? null : it }
}
List<String> COLUMNS = csvFields(LENDING_LOG_CSV.readLines()[0]) as List<String>
List<List> fixtureRows = LENDING_LOG_CSV.readLines().drop(1).findAll { it }.collect { line ->
    List<String> f = csvFields(line)
    assert f.size() == 16 : "lending-log line has ${f.size()} fields: ${line}"
    [f[0], f[1], date(f[2]), f[3], f[4], f[5], f[6], f[7], f[8], f[9], f[10], date(f[11]), date(f[12]),
     f[13] ? new BigDecimal(f[13]) : null, date(f[14]), f[15]]
}
assert fixtureRows.size() == 26

// ── word lists (no faker) ────────────────────────────────────────────────────────────────────
List<String> FIRST = ['Oliver', 'Amelia', 'Harry', 'Isla', 'George', 'Ava', 'Noah', 'Mia', 'Jack', 'Ivy', 'Leo', 'Freya',
    'Arthur', 'Grace', 'Oscar', 'Lily', 'Charlie', 'Emily', 'Henry', 'Ella', 'Thomas', 'Sophie', 'James', 'Evie', 'William',
    'Poppy', 'Alfie', 'Ruby', 'Joshua', 'Alice', 'Samuel', 'Florence', 'Daniel', 'Chloe', 'Joseph', 'Hannah', 'Adam', 'Zara',
    'Mohammed', 'Fatima', 'Yusuf', 'Maryam', 'Ibrahim', 'Amira', 'Omar', 'Layla', 'Ravi', 'Anjali', 'Arjun', 'Meera',
    'Chen', 'Mei', 'Wei', 'Lin', 'Kofi', 'Ama', 'Chidi', 'Ngozi', 'Tunde', 'Folake', 'Tomasz', 'Kasia', 'Piotr', 'Agnieszka',
    'Luca', 'Giulia', 'Mateo', 'Lucia', 'Pablo', 'Carmen', 'Liam', 'Aoife', 'Cian', 'Niamh', 'Rhys', 'Cerys', 'Dylan', 'Seren',
    'Callum', 'Eilidh', 'Fraser', 'Morag', 'Ewan', 'Kirsty', 'Stuart', 'Helen', 'Martin', 'Julie', 'Paul', 'Karen', 'Andrew',
    'Susan', 'David', 'Margaret', 'Peter', 'Janet', 'Richard', 'Linda', 'Graham', 'Pauline', 'Keith', 'Brenda', 'Nigel',
    'Wendy', 'Trevor', 'Maureen', 'Colin', 'Gillian', 'Derek', 'Sheila', 'Hamza', 'Aaliyah', 'Tariq', 'Nadia', 'Ines', 'Hugo',
    'Astrid', 'Lars', 'Sanne', 'Joost', 'Elif', 'Emre', 'Dina', 'Yosef']
List<String> LAST = ['Smith', 'Jones', 'Taylor', 'Brown', 'Williams', 'Wilson', 'Johnson', 'Davies', 'Patel', 'Robinson',
    'Wright', 'Thompson', 'Evans', 'Walker', 'White', 'Roberts', 'Green', 'Hall', 'Wood', 'Jackson', 'Clarke', 'Khan', 'Lewis',
    'Hughes', 'Edwards', 'Harris', 'Turner', 'Hill', 'Moore', 'Cooper', 'Ward', 'Morris', 'King', 'Baker', 'Harrison', 'Morgan',
    'Allen', 'James', 'Scott', 'Phillips', 'Watson', 'Davis', 'Parker', 'Price', 'Bennett', 'Young', 'Griffiths', 'Mitchell',
    'Kelly', 'Cook', 'Carter', 'Richardson', 'Bailey', 'Collins', 'Bell', 'Shaw', 'Murphy', 'Miller', 'Cox', 'Richards',
    'Hussain', 'Ahmed', 'Ali', 'Begum', 'Sharma', 'Singh', 'Kaur', 'Shah', 'Chowdhury', 'Rahman', 'Mistry', 'Chauhan',
    'Wong', 'Li', 'Zhang', 'Nguyen', 'Tran', 'Kim', 'Adeyemi', 'Okafor', 'Mensah', 'Boateng', 'Owusu', 'Asante', 'Okonjo',
    'Nowak', 'Kowalski', 'Wisniewski', 'Rossi', 'Russo', 'Ferrari', 'Garcia', 'Martinez', 'Lopez', 'Fernandez', "O'Brien",
    "O'Connor", 'Byrne', 'Doyle', 'Walsh', 'McCarthy', 'Fitzgerald', 'MacDonald', 'Campbell', 'Stewart', 'Fraser', 'Ross',
    'Munro', 'Llewellyn', 'Pritchard', 'Vaughan', 'Jenkins', 'Rees', 'Owen', 'Lloyd', 'Hartley', 'Pemberton', 'Ashworth',
    'Fairbairn', 'Thornton', 'Whitaker', 'Holloway', 'Kershaw', 'Dunmore', 'Fenwick', 'Marsh', 'Yilmaz', 'Demir', 'Jansen',
    'de Vries', 'Andersen', 'Larsen', 'Novak', 'Horvat', 'Petrov', 'Ivanova', 'Cohen', 'Levi', 'Haddad', 'Mansour']
List<String> ADJ = ['Silent', 'Last', 'Hidden', 'Broken', 'Golden', 'Winter', 'Distant', 'Lost', 'Burning', 'Quiet',
    'Northern', 'Salt', 'Glass', 'Paper', 'Iron', 'Crooked', 'Long', 'Secret', 'Wild', 'Drowned', 'Pale', 'Bright', 'Hollow',
    'Scarlet', 'Second', 'Little', 'Empty', 'Borrowed', 'Painted', 'Sleeping', 'Endless', 'Stolen', 'Summer', 'Midnight',
    'Western', 'Narrow', 'Bitter', 'Gentle', 'Restless', 'Forgotten', 'Silver', 'Black', 'Green', 'Shattered', 'Frozen']
List<String> NOUN = ['River', 'House', 'Garden', 'Harbour', 'Orchard', 'Mirror', 'Lantern', 'Island', 'Road', 'Tide', 'Map',
    'Bridge', 'Letter', 'Crown', 'Forest', 'Station', 'Shore', 'Tower', 'Door', 'Field', 'Window', 'Storm', 'Clock', 'Hour',
    'Kingdom', 'Sea', 'Valley', 'Machine', 'Voyage', 'Signal', 'Country', 'City', 'Mountain', 'Well', 'Harvest', 'Fire',
    'Promise', 'Silence', 'Inheritance', 'Keeper', 'Daughter', 'Stranger', 'Witness', 'Orphan', 'Captain', 'Gardener',
    'Mapmaker', 'Watchman', 'Pilgrim', 'Ferryman', 'Sister', 'Island', 'Chapel', 'Lighthouse', 'Marsh', 'Winter', 'Summer']
List<String> PLACE = ['Hartwell', 'Easterby', 'Coldharbour', 'Fenmouth', 'Ashby Cross', 'Kingsmere', 'Wexcombe', 'Lowgate',
    'Saltley', 'Marbury', 'Thornfield', 'Blackwater', 'Holloway End', 'St Agnes', 'Penrith', 'Whitby', 'Carlisle', 'Lindisfarne',
    'Venice', 'Lisbon', 'Kyoto', 'Istanbul', 'Havana', 'Tangier', 'Riga', 'Trieste', 'Oaxaca', 'Zanzibar']
List<String> ANIMAL = ['Bear', 'Fox', 'Owl', 'Hedgehog', 'Duck', 'Rabbit', 'Mouse', 'Badger', 'Tiger', 'Elephant', 'Penguin',
    'Frog', 'Hen', 'Dragon', 'Giraffe', 'Crocodile', 'Otter', 'Squirrel', 'Lion', 'Whale', 'Cat', 'Dog', 'Monkey', 'Zebra']
List<String> KID = ['Tilly', 'Max', 'Lulu', 'Ned', 'Pip', 'Rosie', 'Sam', 'Bea', 'Finn', 'Kit', 'Maisie', 'Olly', 'Nell',
    'Arlo', 'Tess', 'Zak', 'Hattie', 'Rex', 'Winnie', 'Jojo']
List<String> SUBJECT = ['Memory', 'Sleep', 'Time', 'Light', 'Trees', 'Bees', 'Salt', 'Rivers', 'Numbers', 'Clouds', 'Maps',
    'Bread', 'Stone', 'Birds', 'Fungi', 'Colour', 'Chance', 'Sound', 'Ice', 'Soil', 'Stars', 'Weather', 'Language', 'Money',
    'Silk', 'Coal', 'Tea', 'Glass', 'Iron', 'Wool', 'Sugar', 'Spice', 'Oil', 'Paper', 'Cotton', 'Plague', 'Horses', 'Ships']
List<String> ERA = ['the Tudors', 'the Romans', 'the Vikings', 'the Normans', 'the Victorians', 'the Georgians', 'the Stuarts',
    'the Blitz', 'the Great War', 'the Cold War', 'the Mughals', 'the Ottomans', 'the Incas', 'the Plantagenets', 'the Crusades',
    'the Industrial Revolution', 'the Reformation', 'the Hanseatic League', 'the Byzantines', 'the Silk Road']
List<String> TECH = ['Python', 'Java', 'SQL', 'JavaScript', 'Linux', 'Excel', 'Kotlin', 'Rust', 'Go', 'Docker', 'Git',
    'Spreadsheets', 'Algorithms', 'Networking', 'Raspberry Pi', 'Web Design', 'Data Analysis', 'Machine Learning', 'C#', 'PHP']
List<String> DISH = ['Soups', 'Suppers', 'Bakes', 'Curries', 'Salads', 'Stews', 'Puddings', 'Breads', 'Pies', 'Noodles',
    'Tagines', 'Roasts', 'Traybakes', 'Preserves', 'Pasta', 'Dumplings', 'Pickles', 'Cakes']
List<String> CUISINE = ['Italian', 'Indian', 'Persian', 'Nordic', 'Moroccan', 'Japanese', 'Mexican', 'Greek', 'Korean',
    'Lebanese', 'Georgian', 'Sicilian', 'Cornish', 'Welsh', 'Scottish', 'Vegetarian', 'Vegan', 'Weeknight', 'Sunday', 'Budget']
List<String> COUNTRY = ['Portugal', 'Norway', 'Japan', 'Patagonia', 'Iceland', 'Albania', 'Scotland', 'Wales', 'Ireland',
    'the Balkans', 'Andalucia', 'the Alps', 'Sicily', 'Vietnam', 'Morocco', 'Chile', 'Georgia', 'the Hebrides', 'Brittany', 'Crete']
List<String> PLANT = ['Roses', 'Tomatoes', 'Herbs', 'Dahlias', 'Fruit Trees', 'Salad Leaves', 'Bulbs', 'Ferns', 'Grasses',
    'Clematis', 'Vegetables', 'Succulents', 'Hedges', 'Wildflowers', 'Climbers', 'Apples', 'Sweet Peas', 'Potatoes']
List<String> LANG = ['French', 'German', 'Italian', 'Spanish', 'Japanese', 'Swedish', 'Norwegian', 'Polish', 'Russian',
    'Portuguese', 'Korean', 'Arabic', 'Dutch', 'Turkish', 'Czech', 'Hungarian']
List<String> ORD = ['1st', '2nd', '3rd', '4th', '5th', '6th']
List<String> STAFF = ['TB', 'HP']                          // the two staff: their initials, as edited_by holds them

// Categories as the spreadsheet writes them: adult subjects, one sub-subject the brief names (Local History, under
// History), and the children's age bands. No sub-subject the brief does not name — the tree is the learner's (S2 · 20).
// [category, weight in the stock, kind] — kind picks the title patterns.
List<List> CATEGORIES = [
    ['Fiction', 20, 'fiction'], ['Crime', 11, 'crime'], ['Science Fiction', 5, 'sf'], ['Fantasy', 5, 'fantasy'],
    ['History', 8, 'history'], ['Local History', 2, 'local'], ['Biography', 6, 'biography'], ['Science', 4, 'science'],
    ['Computing', 2, 'computing'], ['Cookery', 4, 'cookery'], ['Travel', 4, 'travel'], ['Gardening', 3, 'gardening'],
    ['Poetry', 2, 'poetry'], ['Picture Books 0-3', 3, 'picture'], ['Picture Books 3-5', 6, 'picture'],
    ['Early Readers 5-7', 5, 'early'], ['Junior Fiction 8-11', 8, 'junior'], ['Teen 12+', 4, 'teen'],
]
Map<String, Closure<String>> TITLE = [
    fiction  : { Random r -> pick(r, ["The ${pick(r, ADJ)} ${pick(r, NOUN)}", "The ${pick(r, NOUN)}'s ${pick(r, NOUN)}",
                                      "A ${pick(r, NOUN)} in ${pick(r, PLACE)}", "The ${pick(r, NOUN)} at ${pick(r, PLACE)}",
                                      "${pick(r, ADJ)} ${pick(r, NOUN)}s", "The ${pick(r, NOUN)} of ${pick(r, PLACE)}"]) },
    crime    : { Random r -> pick(r, ["Death at ${pick(r, PLACE)}", "The ${pick(r, PLACE)} Murders", "A ${pick(r, ADJ)} Killing",
                                      "Murder at the ${pick(r, NOUN)}", "The ${pick(r, ADJ)} Witness", "Blood on the ${pick(r, NOUN)}"]) },
    sf       : { Random r -> pick(r, ["The ${pick(r, NOUN)} Protocol", "${pick(r, ADJ)} Stars", "Beyond the ${pick(r, ADJ)} ${pick(r, NOUN)}",
                                      "The ${pick(r, NOUN)} Engine", "Children of the ${pick(r, NOUN)}", "The ${pick(r, ADJ)} Signal"]) },
    fantasy  : { Random r -> pick(r, ["The ${pick(r, NOUN)} of ${pick(r, ['Aldmere', 'Vessarin', 'Korth', 'Elvenwade', 'Thule', 'Ombra', 'Caradoc', 'Ys', 'Morrow', 'Isenholt'])}",
                                      "Crown of ${pick(r, NOUN)}s", "The ${pick(r, ADJ)} Throne", "A Song for the ${pick(r, NOUN)}", "The ${pick(r, NOUN)}'s Apprentice"]) },
    history  : { Random r -> pick(r, ["A History of ${pick(r, SUBJECT)}", "The World of ${pick(r, ERA)}", "Empire of ${pick(r, SUBJECT)}",
                                      "${pick(r, SUBJECT)}: A Global History", "The Last Days of ${pick(r, ERA)}", "Living with ${pick(r, ERA)}"]) },
    local    : { Random r -> pick(r, ["Riverside in Old Photographs", "The Riverside Floods of ${1890 + r.nextInt(80)}", "Mills of the ${pick(r, ['Upper', 'Lower', 'Old'])} Wey",
                                      "${pick(r, ['Chapel', 'Mill', 'Ferry', 'Station', 'Church', 'Market'])} Street, ${1850 + r.nextInt(120)}-${1900 + r.nextInt(100)}",
                                      "Riverside at War, ${pick(r, ['1914-1918', '1939-1945'])}", "The ${pick(r, ['Parish', 'School', 'Workhouse', 'Brewery', 'Ferry'])} Records of Riverside ${pick(r, ['Vol. 1', 'Vol. 2', 'Vol. 3', ''])}".trim()]) },
    biography: { Random r -> pick(r, ["${pick(r, FIRST)} ${pick(r, LAST)}: A Life", "The Life of ${pick(r, FIRST)} ${pick(r, LAST)}",
                                      "${pick(r, ADJ)} Years: A Memoir", "Letters to ${pick(r, FIRST)}", "My ${pick(r, NOUN)}: A Memoir"]) },
    science  : { Random r -> pick(r, ["The ${pick(r, SUBJECT)} Question", "How ${pick(r, SUBJECT)} Works", "The Hidden Life of ${pick(r, SUBJECT)}",
                                      "A Short History of ${pick(r, SUBJECT)}", "The Science of ${pick(r, SUBJECT)}"]) },
    computing: { Random r -> pick(r, ["${pick(r, TECH)} in Practice", "Learning ${pick(r, TECH)}", "${pick(r, TECH)} for Beginners",
                                      "The ${pick(r, TECH)} Handbook", "${pick(r, TECH)} the Hard Way", "Head First ${pick(r, TECH)}"]) },
    cookery  : { Random r -> pick(r, ["${pick(r, CUISINE)} ${pick(r, DISH)}", "One Pot ${pick(r, DISH)}", "The ${pick(r, CUISINE)} Kitchen",
                                      "${pick(r, ['Simple', 'Quick', 'Slow', 'Easy', 'Winter', 'Summer'])} ${pick(r, DISH)}"]) },
    travel   : { Random r -> pick(r, ["Walking in ${pick(r, COUNTRY)}", "${pick(r, COUNTRY)} by Rail", "A ${pick(r, ['Summer', 'Winter', 'Year', 'Month'])} in ${pick(r, COUNTRY)}",
                                      "The Rough Guide to ${pick(r, COUNTRY)}", "Slow Roads through ${pick(r, COUNTRY)}"]) },
    gardening: { Random r -> pick(r, ["Growing ${pick(r, PLANT)}", "The ${pick(r, ['Small', 'Shady', 'Dry', 'Wild', 'Cottage', 'Kitchen'])} Garden",
                                      "${pick(r, PLANT)} for Every Garden", "The ${pick(r, ['Allotment', 'Balcony', 'Winter', 'No-Dig'])} Year"]) },
    poetry   : { Random r -> pick(r, ["${pick(r, NOUN)}: Poems", "The ${pick(r, ADJ)} ${pick(r, NOUN)}: Poems", "${pick(r, ADJ)} Weather: New Poems"]) },
    picture  : { Random r -> pick(r, ["${pick(r, ANIMAL)} Goes to ${pick(r, ['the Seaside', 'School', 'the Moon', 'Bed', 'the Park', 'Market'])}",
                                      "Where Is ${pick(r, ANIMAL)}?", "The ${pick(r, ['Very', 'Little', 'Sleepy', 'Grumpy', 'Hungry', 'Busy'])} ${pick(r, ANIMAL)}",
                                      "Goodnight, ${pick(r, ANIMAL)}", "${pick(r, KID)} and the ${pick(r, ANIMAL)}"]) },
    early    : { Random r -> pick(r, ["${pick(r, KID)} Saves the Day", "${pick(r, KID)} and the ${pick(r, ADJ)} ${pick(r, NOUN)}",
                                      "The Day the ${pick(r, ANIMAL)} Came to Tea", "${pick(r, KID)}'s ${pick(r, ['Big', 'Bad', 'Best', 'Busy', 'Brave'])} Day"]) },
    junior   : { Random r -> pick(r, ["${pick(r, KID)} and the ${pick(r, ADJ)} ${pick(r, NOUN)}", "The ${pick(r, NOUN)} Club",
                                      "The ${pick(r, ADJ)} ${pick(r, ANIMAL)}", "Secrets of the ${pick(r, NOUN)}", "The ${pick(r, NOUN)} Detectives"]) },
    teen     : { Random r -> pick(r, ["The ${pick(r, ADJ)} Year", "${pick(r, NOUN)} of Ash", "All the ${pick(r, ADJ)} ${pick(r, NOUN)}s",
                                      "We Were ${pick(r, ADJ)}", "The ${pick(r, NOUN)} Between Us"]) },
]

// ── 1. schema ────────────────────────────────────────────────────────────────────────────────
dbSql.execute("DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE".toString())
dbSql.execute("CREATE SCHEMA ${SCHEMA}".toString())

// The spreadsheet's sixteen columns, with the types a CSV import would give them. No keys and no NOT NULL: it is a
// spreadsheet, and finding the keys is the learner's job.
String SHEET = '''("member" VARCHAR(60), "email" VARCHAR(80), "joined" DATE, "member_status" VARCHAR(10), "title" VARCHAR(120),
 "author" VARCHAR(60), "author2" VARCHAR(60), "isbn" VARCHAR(13), "category" VARCHAR(40), "copy_note" VARCHAR(40), "notes" VARCHAR(200),
 "borrowed" DATE, "returned" DATE, "fine_paid" DECIMAL(6,2), "last_edited" DATE, "edited_by" VARCHAR(4))'''
String DDL = """
CREATE TABLE ${T('lending_log')} ${SHEET};
CREATE TABLE ${T('lending_log_full')} ${SHEET}
"""
DDL.split(';').collect { it.trim() }.findAll { it }.each { dbSql.execute(it) }

// Multi-row INSERTs of 200 rows (DuckDB's JDBC batches go one row at a time, which is minutes, not seconds).
def insert = { String table, List<String> cols, List<List> rows ->
    String head = "INSERT INTO ${T(table)} (${cols.collect { q(it) }.join(', ')}) VALUES ".toString()
    String one = "(${cols.collect { '?' }.join(', ')})".toString()
    rows.collate(200).each { chunk -> dbSql.execute(head + ([one] * chunk.size()).join(', '), chunk.flatten() as List) }
    log.info("  {}: {} rows", table, rows.size())
}

// ── 2. lending_log: the brief's 26 rows ──────────────────────────────────────────────────────
insert('lending_log', COLUMNS, fixtureRows)

// ── 3. members ───────────────────────────────────────────────────────────────────────────────
// The brief's ten, as the brief has them, then generated members up to MEMBERS. Everyone joins on or after the
// spreadsheet's first day; 'lapsed' and 'left' members stop borrowing on their last day (Danny, lapsed, still
// borrows in the brief's rows — that is the brief's, left as it is).
Random rM = rnd('lending_log_full/members')
List<Map> members = []
Set<String> names = [] as Set, emails = [] as Set
fixtureRows.each { f ->
    if (emails.add(f[1] as String)) {
        members << [idx: members.size(), name: f[0], email: f[1], joined: (f[2] as java.sql.Date).toLocalDate(), status: f[3], fixture: true,
                    last: FIXTURE_FROM.minusDays(1), weight: 1.5d]
        names << (f[0] as String)
    }
}
assert members.size() == 10
def ascii = { String s -> java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll(/[^\p{ASCII}]/, '').replaceAll(/[^A-Za-z]/, '').toLowerCase() }
long spanDays = TODAY.toEpochDay() - FIRST_DAY.toEpochDay()
while (members.size() < MEMBERS) {
    String first = pick(rM, FIRST), last = pick(rM, LAST), name = "${first} ${last}".toString()
    if (!names.add(name)) continue
    String f = ascii(first), l = ascii(last)
    String local = [ "${f}.${l}", "${f[0]}.${l}", "${f}${l[0]}", "${f[0]}${l}", "${f}.${l.take(2)}" ][rM.nextInt(5)]
    String email = "${local}@example.com".toString()
    for (int n = 2; !emails.add(email); n++) email = "${local}${n}@example.com".toString()
    // a third were members already when the spreadsheet started (keyed in over its first months), the rest join later
    LocalDate joined = rM.nextDouble() < 0.33 ? FIRST_DAY.plusDays(rM.nextInt(120)) : FIRST_DAY.plusDays(120 + rM.nextInt((int) spanDays - 150))
    double s = rM.nextDouble()
    String status = 'active'
    LocalDate lastDay = TODAY
    long tenure = TODAY.toEpochDay() - joined.toEpochDay()
    if (tenure > 500 && s < 0.28) {                        // lapsed (didn't renew) or left (told us), a year or more in
        status = s < 0.18 ? 'lapsed' : 'left'
        lastDay = joined.plusDays(365 + rM.nextInt((int) tenure - 400))
    }
    members << [idx: members.size(), name: name, email: email, joined: joined, status: status, fixture: false, last: lastDay,
                weight: Math.exp(rM.nextGaussian() * 0.9)]  // a few heavy readers, many occasional ones
}

// ── 4. books: works, their editions (ISBNs) and copies ──────────────────────────────────────
// A copy is what copy_note names: "copy N" when its title has several, blank when it has one. The brief's books
// come first, exactly as its rows describe them; generated works fill the stock up to COPIES.
Random rB = rnd('lending_log_full/books')
Set<String> isbns = fixtureRows.collect { it[7] }.findAll { it } as Set
def isbn13 = {
    while (true) {
        String body = '978' + (rB.nextDouble() < 0.7 ? '0' : '1') + String.format('%08d', rB.nextInt(100_000_000))
        int sum = 0
        body.eachWithIndex { String d, int i -> sum += Integer.parseInt(d) * (i % 2 == 0 ? 1 : 3) }
        String isbn = body + ((10 - sum % 10) % 10)
        if (isbns.add(isbn)) return isbn
    }
}
def isbn10 = {
    while (true) {
        String body = '0' + String.format('%08d', rB.nextInt(100_000_000))
        int sum = 0
        body.eachWithIndex { String d, int i -> sum += Integer.parseInt(d) * (10 - i) }
        int c = (11 - sum % 11) % 11
        String isbn = body + (c == 10 ? 'X' : c.toString())
        if (isbns.add(isbn)) return isbn
    }
}
LocalDate OLD_STOCK = LocalDate.of(2000, 1, 1)             // acquired before the spreadsheet: lendable from day one
List<Map> works = []
List<Map> copies = []
def addWork = { Map w, List<Map> cs ->
    w.id = works.size(); w.copies = []
    works << w
    cs.each { c -> c.id = copies.size(); c.work = w.id; c.busyUntil = Long.MIN_VALUE; copies << c; w.copies << c.id }
}
// The brief's books. Where its rows give a copy_note or a note for a copy, the generated rows give the same.
// Two of them (The Silk Roads, Good Omens) are out twice at once in the brief's rows, so the library owns two copies;
// the brief leaves their copy_note blank, the generated rows number them.
def fx = { String title, String author, String author2, String category, double pop, List<List> cs ->
    addWork([title: title, author: author, author2: author2, category: category, weight: pop, fixture: true],
            cs.collect { c -> [isbn: c[0], copyNote: c[1], note: c[2], acquired: c[3] ? LocalDate.parse(c[3] as String) : OLD_STOCK] })
}
fx('Dune', 'Frank Herbert', null, 'Science Fiction', 6.0, [
    ['9780441013593', 'copy 1', null, null], ['9780441013593', 'copy 2', null, null], ['9780441013593', 'copy 3', null, null],
    ['9780441013593', 'copy 4', null, null],
    ['9780593099322', 'copy 5', '2021 film tie-in reprint - different ISBN, same book', '2021-10-04']])
fx('Dune Messiah', 'Frank Herbert', null, 'Science Fiction', 2.0, [['9780575074354', null, '2nd in series', null]])
fx('The Silk Roads', 'Peter Frankopan', null, 'History', 2.5, [['9781408839997', 'copy 1', null, null], ['9781408839997', 'copy 2', null, null]])
fx('Good Omens', 'Terry Pratchett', 'Neil Gaiman', 'Fantasy', 3.0, [['9780552137034', 'copy 1', null, null], ['9780552137034', 'copy 2', null, null]])
fx('Riverside: A Local History', 'H. Pettifer', null, 'Local History', 1.0, [[null, 'donated 1998', 'no ISBN - donated', null]])
fx('The Pragmatic Programmer', 'Andrew Hunt', 'David Thomas', 'Computing', 1.5, [
    ['020161622X', '1st edition', 'ISBN-10; superseded by the 2nd ed.', null],
    ['9780135957059', '2nd edition', '20th anniversary ed.', '2019-10-14']])
fx('Where the Wild Things Are', 'Maurice Sendak', null, 'Picture Books 3-5', 2.5, [['9780099408390', null, null, null]])
fx('The Very Hungry Caterpillar', 'Eric Carle', null, 'Picture Books 3-5', 3.0, [['9780241003008', null, 'board book; ages 0-3 really', null]])
fx('Sapiens', 'Yuval Noah Harari', null, 'History', 2.5, [['9780099590088', null, 'orig. Hebrew; trans. Harari + Purcell', null]])
// "The Illustrated Riverside" is reference only: on the shelf, never lent — the brief's one row is the only loan.
fx('The Illustrated Riverside', 'H. Pettifer', 'J. Pettifer', 'Local History', 0.0, [[null, 'fragile - ref only', 'no ISBN; reference only - do not lend', null]])
assert copies.size() == 17

// Generated works. Authors: a pool with a few prolific ones; about 1 in 16 works has a second author (author2).
List<String> authors = []
Set<String> authorSet = [] as Set
while (authors.size() < 1800) {
    String a = rB.nextDouble() < 0.15 ? "${pick(rB, FIRST)[0]}. ${pick(rB, LAST)}".toString() : "${pick(rB, FIRST)} ${pick(rB, LAST)}".toString()
    if (authorSet.add(a)) authors << a
}
Cumulative authorPick = new Cumulative((0..<authors.size()).collect { 1.0d / Math.pow(it + 8, 0.7) })
Cumulative categoryPick = new Cumulative(CATEGORIES.collect { (it[1] as Number).doubleValue() })
Set<String> titles = works.collect { it.title as String } as Set
// Notes, as the spreadsheet has them: free text, different for almost every row that has one (S2 · 42).
def editionNote = { Random r, String kind ->
    List<String> notes = ["trans. from the ${pick(r, LANG)}", "orig. ${pick(r, LANG)}; trans. ${pick(r, FIRST)[0]}. ${pick(r, LAST)}", 'large print',
                          'abridged', 'illustrated edition', 'revised edition']
    if (kind in ['fiction', 'crime', 'sf', 'fantasy', 'junior', 'teen']) notes += ["${pick(r, ORD)} in series".toString(), "${pick(r, ORD)} in series".toString()]
    if (kind == 'picture') notes += ['board book', 'board book', 'lift-the-flap']
    pick(r, notes) as String
}
def copyNote = { Random r -> pick(r, ['spine repaired', 'signed by the author', 'water damage pp. 40-52', 'inscribed - gift from the Friends of Riverside',
                                       'missing dust jacket', "donated ${1985 + r.nextInt(38)}", 'pages loose', 'map missing from back cover']) as String }
int multiEditionWorks = 0
while (copies.size() < COPIES) {
    List cat = CATEGORIES[categoryPick.pick(rB)]
    String kind = cat[2]
    String title = null
    for (int tries = 0; tries < 50 && title == null; tries++) { String t = TITLE[kind](rB).toString(); if (!titles.contains(t)) title = t }
    if (title == null) continue
    titles << title
    String author = authors[authorPick.pick(rB)]
    String author2 = rB.nextDouble() < (kind in ['computing', 'science', 'cookery', 'travel'] ? 0.15 : 0.04) ? pick(rB, authors) : null
    if (author2 == author) author2 = null
    double pd = rB.nextDouble()
    int n = pd < 0.84 ? 1 : pd < 0.94 ? 2 : pd < 0.98 ? 3 : 4 + rB.nextInt(2)
    n = Math.min(n, COPIES - copies.size())
    double pop = Math.exp(rB.nextGaussian() * 0.5) * (n > 1 ? 1.2 * n : 1.0)   // several copies because it is popular
    LocalDate acquired = rB.nextDouble() < 0.7 ? OLD_STOCK : FIRST_DAY.plusDays(rB.nextInt((int) spanDays - 30))
    // the edition(s): mostly ISBN-13; older stock ISBN-10; a few with no ISBN at all (old or donated)
    double e = rB.nextDouble()
    String isbnA = acquired == OLD_STOCK && e < 0.04 ? null : acquired == OLD_STOCK && e < 0.14 ? isbn10() : isbn13()
    String noteA = rB.nextDouble() < 0.06 ? editionNote(rB, kind) : null
    if (isbnA == null) noteA = rB.nextDouble() < 0.5 ? 'no ISBN' : noteA
    // PLANTED — S2 · 35 (and 10): about 1 in 4 multi-copy works has later copies of a second edition (a reprint or a new
    // edition, its own ISBN): copy numbers run on across editions, as Dune's copy 5 does. ISBN → title, (title, copy) → ISBN.
    boolean twoEditions = n > 1 && rB.nextDouble() < 0.25
    String isbnB = twoEditions ? isbn13() : null
    String noteB = twoEditions ? pick(rB, ['new edition', 'reprint - new cover', 'revised and updated', "${2019 + rB.nextInt(7)} reprint", null, null]) : null
    int splitAt = twoEditions ? 1 + rB.nextInt(n - 1) : n
    if (twoEditions) multiEditionWorks++
    LocalDate acquiredB = twoEditions ? [acquired, FIRST_DAY].max().plusDays(rB.nextInt(900)) : null
    List<Map> cs = (1..n).collect { k ->
        boolean b = k > splitAt
        String note = b ? noteB : noteA
        if (note == null && rB.nextDouble() < 0.03) note = copyNote(rB)
        [isbn: b ? isbnB : isbnA, copyNote: n > 1 ? "copy ${k}".toString() : null, note: note, acquired: b ? [acquiredB, TODAY.minusDays(30)].min() : acquired]
    }
    addWork([title: title, author: author, author2: author2, category: cat[0], weight: pop, fixture: false], cs)
}

// ── 5. loans ─────────────────────────────────────────────────────────────────────────────────
// LOANS days drawn over the spreadsheet's life (closed Sundays, 25-26 December and 1 January; Saturdays busier;
// ~4% more a year), then walked in date order: a member who is a member that day takes a title that has a copy on
// the shelf and that they do not already hold.
Random rL = rnd('lending_log_full')
List<LocalDate> openDays = []
List<Double> dayWeight = []
for (LocalDate d = FIRST_DAY; !d.isAfter(TODAY); d = d.plusDays(1)) {
    if (d.dayOfWeek == DayOfWeek.SUNDAY) continue
    if ((d.monthValue == 12 && d.dayOfMonth in [25, 26]) || (d.monthValue == 1 && d.dayOfMonth == 1)) continue
    openDays << d
    dayWeight << (d.dayOfWeek == DayOfWeek.SATURDAY ? 1.4d : 1.0d) * Math.pow(1.04, (d.toEpochDay() - FIRST_DAY.toEpochDay()) / 365.0d)
}
Cumulative dayPick = new Cumulative(dayWeight)
List<Integer> loanDays = (1..LOANS).collect { dayPick.pick(rL) }.sort()
Cumulative memberPick = new Cumulative(members.collect { it.weight as double })
Cumulative workPick = new Cumulative(works.collect { it.weight as double })
Map<Integer, Map<Integer, Long>> holding = [:].withDefault { [:] }   // member → work → out until (epoch day)
List<List> loanRows = []
int skipped = 0, lost = 0
long todayE = TODAY.toEpochDay(), fixtureFromE = FIXTURE_FROM.toEpochDay()
loanDays.each { int di ->
    LocalDate d = openDays[di]
    long de = d.toEpochDay()
    Map m = null
    for (int t = 0; t < 60 && m == null; t++) {
        Map c = members[memberPick.pick(rL)]
        if (!(c.joined as LocalDate).isAfter(d) && !(c.last as LocalDate).isBefore(d)) m = c
    }
    if (m == null) { skipped++; return }
    int mi = m.idx as int
    Map copy = null
    for (int t = 0; t < 60 && copy == null; t++) {
        Map w = works[workPick.pick(rL)]
        if (w.fixture && de >= fixtureFromE) continue       // the brief's books: its own rows cover them from here on
        Long held = holding[mi][w.id as Integer]
        if (held != null && held >= de) continue            // they already have a copy of this title
        List<Map> free = (w.copies as List<Integer>).collect { copies[it] }.findAll { c -> c.busyUntil < de && !(c.acquired as LocalDate).isAfter(d) }
        if (free) copy = free[rL.nextInt(free.size())]
    }
    if (copy == null) { skipped++; return }
    Map w = works[copy.work as int]
    // how long it is out: most within the 21 days, a fifth late, a few very late, and a few never come back (lost)
    double x = rL.nextDouble()
    boolean isLost = x < 0.003 && !w.fixture
    int days = x < 0.75 ? 4 + rL.nextInt(18) : x < 0.955 ? 22 + rL.nextInt(14) : 36 + rL.nextInt(40)
    LocalDate back = d.plusDays(days)
    if (back.dayOfWeek == DayOfWeek.SUNDAY) back = back.plusDays(1)
    LocalDate returned = isLost || back.isAfter(TODAY) ? null : back
    if (isLost) lost++
    long until = returned == null ? Long.MAX_VALUE : returned.toEpochDay()
    copy.busyUntil = until
    holding[mi][w.id as Integer] = until
    BigDecimal fine = null
    long late = returned == null ? 0 : returned.toEpochDay() - de - LOAN_DAYS
    if (late > 0 && rL.nextDouble() < 0.5) fine = FINE_PER_DAY.multiply(new BigDecimal(late)).setScale(2, RoundingMode.HALF_UP)   // else waived
    loanRows << [m.name, m.email, sqlDate(m.joined as LocalDate), m.status, w.title, w.author, w.author2, copy.isbn, w.category,
                 copy.copyNote, copy.note, sqlDate(d), sqlDate(returned), fine, sqlDate(returned ?: d), pick(rL, STAFF)]
}
// The brief's 26 rows join the generated ones, unchanged; the whole file is kept in borrowed-date order.
List<List> fullRows = (loanRows + fixtureRows).withIndex().sort { a, b -> (a[0][11] <=> b[0][11]) ?: (a[1] <=> b[1]) }.collect { it[0] }
insert('lending_log_full', COLUMNS, fullRows)
log.info("  generated {} loans ({} days found no free member or copy and were skipped), {} lost books, {} works on two editions",
         loanRows.size(), skipped, lost, multiEditionWorks)

List<String> TABLES = ['lending_log', 'lending_log_full']
if (vendor == 'POSTGRES') TABLES.each { dbSql.execute("ANALYZE ${T(it)}".toString()) }

// ── 6. _dataset_info: counts and checksums ──────────────────────────────────────────────────
// Canonical form (Northwind Company's, so academy-verify.groovy checks this schema unchanged with SCHEMA=library_s):
// every column, names sorted case-insensitively; NULL as <NULL>; numbers with trailing zeros stripped; timestamps
// yyyy-MM-dd HH:mm:ss; dates yyyy-MM-dd. Scale S: rows tab-joined and sorted; SHA-256 of the rows joined by newlines.
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
dbSql.execute("CREATE TABLE ${T('_dataset_info')} (\"Dataset\" VARCHAR(40), \"Version\" INTEGER, \"Scale\" VARCHAR(2), \"TableName\" VARCHAR(40), \"RowCount\" INTEGER, \"Checksum\" VARCHAR(64))".toString())
List<List> info = []
TABLES.each { table ->
    List<String> cols = dbSql.rows("SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?".toString(), [SCHEMA, table])
                             .collect { it.column_name as String }.sort { a, b -> a.compareToIgnoreCase(b) }
    String select = "SELECT ${cols.collect { q(it) }.join(', ')} FROM ${T(table)}".toString()
    List<String> rows = []
    dbSql.eachRow(select) { r -> rows << (1..cols.size()).collect { canon(r.getObject(it)) }.join('\t') }
    rows.sort()
    String hex = MessageDigest.getInstance('SHA-256').digest(rows.join('\n').getBytes('UTF-8')).collect { String.format('%02x', it) }.join()
    info << [DATASET, VERSION, SCALE, table, rows.size(), hex]
}
insert('_dataset_info', ['Dataset', 'Version', 'Scale', 'TableName', 'RowCount', 'Checksum'], info)

log.info("=== {} v{} scale {} installed in schema {}: {} rows in lending_log_full, {} members, {} copies ===",
         DATASET, VERSION, SCALE, SCHEMA, fullRows.size(), members.size(), copies.size())
