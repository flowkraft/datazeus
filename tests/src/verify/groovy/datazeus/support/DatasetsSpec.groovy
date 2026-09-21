package datazeus.support

import groovy.sql.Sql
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  WHICH NORTHWIND EACH SERIES RUNS ON — decided 2026-09-19, with reasons  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * There are two Northwinds, and they are two different companies:
 *
 *   CLASSIC        datasets/northwind/northwind.duckdb — NorthwindDataGenerator's DataPallas demo
 *                  data: 25 customers, 79 orders, no declared keys. Its file also carries
 *                  NorthwindOlapDataGenerator's random star (fact_sales, dim_*), which the
 *                  curriculum calls `generated`.
 *   COMPANY        datasets/northwind-co/northwind_co.duckdb and its S/M/L installs — the academy's
 *                  own dataset: 120 customers and 10,000 orders at S, declared keys, history,
 *                  invoices, stock, targets.
 *
 * They share table and column names and NO rows. A query ports; its numbers do not.
 *
 * THE RULE: the classic dataset is used where being tiny, keyless or installed on every engine
 * IS the lesson; Northwind Company everywhere a real business at a realistic size is the lesson.
 * Each series uses one of them throughout — CONTRIBUTING.md: never "switch datasets halfway
 * through a series".
 *
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  WHICH ENGINES EACH SERIES RUNS ON — decided 2026-09-19, with reasons    ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Two pairs, and each series runs on one of them, never on all three engines:
 *
 *   EVERYDAY    DuckDB + PostgreSQL   every series not named below.
 *   ANALYTICS   DuckDB + ClickHouse   Data Warehousing Series 2 and 3, dbt Series 3.
 *
 * THE REASONING. DuckDB is to analytics what SQLite is to ordinary databases: in-process, one
 * file, no server. ClickHouse is to analytics what PostgreSQL is to ordinary databases: the
 * server that teams actually run. So DuckDB sits in both pairs (it is what a learner opens with
 * no setup), and the second engine is the server that matches what the series teaches:
 *   - A series about transactions, keys, constraints, UPDATE and MERGE needs PostgreSQL.
 *     ClickHouse has no transactions and no enforced keys, and its UPDATE is a background
 *     mutation, so those lessons would teach its exceptions instead of the rule.
 *   - A series about columnar storage at scale needs ClickHouse. Only a columnar SERVER has
 *     MergeTree parts and merges, ReplacingMergeTree, materialized views that compute at insert
 *     time, sharding, and dbt-clickhouse's insert_overwrite. DuckDB alone teaches the columnar
 *     concepts, but "analytics on a file" is not what a team runs in production. PostgreSQL there
 *     is a row store, and it would teach the wrong costs.
 *   - Never all three in one series: a learner sets up two engines per series, not three, and
 *     the lessons compare like with like.
 * PostgreSQL may still appear in an analytics series, and ClickHouse in an everyday one, for a
 * ROLE rather than as the engine the series runs on. Examples: the row-store baseline in a size
 * comparison, the operational source a warehouse reads from, or the target an ETL pipeline
 * loads. Each such episode is listed in ENGINE_EPISODES, with its role.
 * The seed scripts follow this: the Northwind Company scripts the analytics series need
 * (install, changes, raw-install, dw-install, verify, uninstall) accept CLICKHOUSE, and the ones
 * only everyday series use (library, crm-export) do not. They moved into THIS repo on 2026-09-20
 * (datasets/scripts/), so that claim is now checkable rather than merely asserted — CHECK 7 reads
 * the scripts and holds them to it.
 *
 * THIS FILE HAS THREE PARTS:
 *   1. THE DECISIONS   where the classic dataset stays, which engines each series runs on, and
 *                      why. Every failure message quotes these reasons, so whoever trips a check
 *                      reads why before changing anything.
 *   2. THE CHECKS      five, one per place a decision can silently break: the curriculum, the
 *                      test code and the classic file itself for the dataset; the curriculum and
 *                      the test code for the engines.
 *   3. HELPERS
 *
 * TO CHANGE A DECISION ON PURPOSE: edit part 1, with the new reason, in the same commit as the
 * curriculum. This file is the record; the curriculum follows it.
 *
 * Scope: the five data courses (learnsql, datamodeling, datawarehousing, etlpipelines, dbt).
 */
class DatasetsSpec extends Specification {

    // ════════════════════════════════════════════════════════════════════════════════════════
    //  1. THE DECISIONS
    // ════════════════════════════════════════════════════════════════════════════════════════

    static final List<String> TRACKS = ["learnsql", "datamodeling", "datawarehousing", "etlpipelines", "dbt"]

    /** Series that run on classic Northwind, and nothing else. */
    static final Map<String, String> CLASSIC_SERIES = [
        "learnsql/series1":
            "Learn SQL Series 1 is for beginners, and it is small enough to see every row: 25 customers, " +
            "79 orders. A beginner checks a result by eye, and 10,000 orders would take that away. " +
            "It is also published: its videos, koans and numbers are frozen on this dataset.",
        "learnsql/series4":
            "Learn SQL Series 4 compares database engines on the SAME rows. DataPallas already installs " +
            "classic Northwind identically on every engine it supports (that is what NorthwindDataGenerator " +
            "was built for). Northwind Company installs on PostgreSQL, DuckDB and ClickHouse only.",
        "datamodeling/series1":
            "Data Modeling Series 1 is built on classic Northwind declaring NO keys or constraints and " +
            "having empty tables (Territories, Region, demographics): the learner designs the keys, and " +
            "1 · 57 reverse-engineers a schema that declares nothing. Northwind Company already declares " +
            "its primary keys and checks, so the design work would be done before the learner starts.",
    ]

    /**
     * Single episodes OUTSIDE those series that may open the classic file, the exact sources they
     * may use from it, and why. Anything else outside CLASSIC_SERIES is Northwind Company only.
     */
    static final Map<String, Map> CLASSIC_EPISODES = [
        "datamodeling/series3 · 25": [sources: ["generated"], why:
            "The random star in the classic file is judged here as 'a star somebody else built': its " +
            "dim_time stops at day 28 of every month. The series itself runs on Northwind Company."],
        "datamodeling/series3 · 30": [sources: ["generated"], why:
            "The random star in the classic file is judged here: its fact_sales has no order number, " +
            "which is what degenerate dimensions are about. The series itself runs on Northwind Company."],
        "datawarehousing/series2 · 25": [sources: ["northwind"], why:
            "TEMPORARY. The five sample cubes this episode reads ship with DataPallas on the classic file. " +
            "The plan is academy copies of those cubes on Northwind Company; when they exist, switch the " +
            "episode to them and delete this entry."],
    ]

    /** The curriculum's `data.source` values that mean "the classic file". */
    static final Set<String> CLASSIC_SOURCES = ["northwind", "generated"] as Set

    /**
     * In test code, the base class picks the file. KoanBase and NorthwindGateSpec open the classic
     * one, and SchemaKoanBase works on a copy of it; NorthwindCoKoanBase and NorthwindCoGateSpec
     * open Northwind Company.
     */
    static final List<String> CLASSIC_BASES = ["KoanBase", "NorthwindGateSpec", "SchemaKoanBase"]
    static final List<String> COMPANY_BASES = ["NorthwindCoKoanBase", "NorthwindCoGateSpec"]

    /**
     * Code that opens the classic file without extending a classic base: its helpers, and its
     * path. (Word boundaries matter: NorthwindCoEngines is not NorthwindEngines.)
     */
    static final List CLASSIC_NAMES = [~/\bNorthwindEngines\b/, ~/\bNorthwindSeed\b/,
                                       ~/datasets\/northwind\//, ~/\bnorthwind\.duckdb\b/]

    // ── Engines (the second decision in the header above) ──────────────────────────────────────

    static final Map<String, List<String>> PAIRS = [
        everyday : ["duckdb", "postgres"],
        analytics: ["duckdb", "clickhouse"],
    ]

    static final String EVERYDAY_WHY =
        "Everyday series run on DuckDB + PostgreSQL. What they teach (keys, constraints, transactions, " +
        "UPDATE, MERGE, isolation) is how a row store behaves. ClickHouse has no transactions, does not " +
        "enforce keys, and runs UPDATE as a background mutation, so the lesson would teach its exceptions " +
        "instead of the rule. ClickHouse belongs to Data Warehousing Series 2–3 and dbt Series 3."

    /** Series that run on DuckDB + ClickHouse. Every series not listed here or in ANY_ENGINE_SERIES is everyday. */
    static final Map<String, String> ANALYTICS_SERIES = [
        "datawarehousing/series2":
            "Data Warehousing Series 2 is columnar engines and cubes. DuckDB shows the columnar ideas with " +
            "no server (column projection, row groups, Parquet). ClickHouse is the columnar SERVER a team " +
            "runs: MergeTree parts, merges, ORDER BY as the index, and materialized views. PostgreSQL is a " +
            "row store and would teach the wrong costs.",
        "datawarehousing/series3":
            "Data Warehousing Series 3 is warehousing at scale: aggregating materialized views, projections, " +
            "partitions, sharding, and the DuckDB-vs-ClickHouse benchmark. These are the ClickHouse server's " +
            "features, measured against DuckDB, the engine that needs no server.",
        "dbt/series3":
            "dbt Series 3 takes the Series 1–2 project (built on DuckDB) and points it at ClickHouse: " +
            "materializations on MergeTree, insert_overwrite instead of UPDATE, and ReplacingMergeTree " +
            "instead of snapshots. 'The same project on a server-side columnar warehouse' is the point. " +
            "dbt on PostgreSQL would be another row-store target, and it belongs in Series 1–2 if anywhere.",
    ]

    /** Series whose subject IS comparing engines: any engine goes. */
    static final Map<String, String> ANY_ENGINE_SERIES = [
        "learnsql/series4":
            "Learn SQL Series 4 compares database engines on the same rows, so every engine DataPallas " +
            "runs is in scope, ClickHouse included.",
    ]

    /**
     * Episodes where the engine outside their series' pair appears on purpose, in a ROLE. It is not
     * the engine the series runs on. An entry no episode needs any more fails, so the list only
     * holds live exceptions.
     */
    static final Map<String, Map> ENGINE_EPISODES = [
        "datawarehousing/series2 · 45": [engine: "postgres", why:
            "PostgreSQL is the ROW-STORE BASELINE in the size and compression comparison: the same rows on a " +
            "row store, DuckDB and ClickHouse. It is measured, not taught on."],
        "datawarehousing/series3 · 22": [engine: "postgres", why:
            "PostgreSQL is the OPERATIONAL SOURCE that the warehouse federates or copies from ('federate the " +
            "small, copy the large'). The warehouse side is DuckDB."],
        "etlpipelines/series2 · 40": [engine: "clickhouse", why:
            "ClickHouse is the pipeline's TARGET: Northwind Company goes from PostgreSQL to ClickHouse, and " +
            "the type seams (Nullable, Decimal, a table name with a space) are the lesson."],
        "etlpipelines/series3 · 05": [engine: "clickhouse", why:
            "ClickHouse is one of the two TARGETS the star schema is loaded into (DuckDB and ClickHouse), and " +
            "the load is the lesson."],
        "etlpipelines/series3 · 30": [engine: "clickhouse", why:
            "ClickHouse is a CONTRAST in 'the vendor differences that break it': its ReplacingMergeTree dedups " +
            "only eventually, beside PostgreSQL's MERGE error. The MERGE itself runs on DuckDB + PostgreSQL."],
    ]

    /** How an episode's title, hands_on and data.reason name an engine. DuckDB is in both pairs, so it is never policed. */
    static final Map<String, java.util.regex.Pattern> ENGINE_WORDS = [
        postgres  : ~/(?i)\bpostgres(?:ql)?\b/,
        clickhouse: ~/(?i)clickhouse/,
    ]

    /**
     * How lesson code reaches an engine: a JDBC URL, a Testcontainers class, `sqlFor("…")`, or a
     * gate base whose ENGINES include it (NorthwindGateSpec and NorthwindCoGateSpec run on
     * PostgreSQL).
     */
    static final Map<String, List> ENGINE_IN_CODE = [
        postgres  : [~/jdbc:postgresql/, ~/PostgreSQLContainer/, ~/\bNorthwind(?:Co)?Engines\.pg\(\)/,
                     ~/sqlFor\(\s*["']postgres["']\s*\)/],
        clickhouse: [~/jdbc:(?:clickhouse|ch):/, ~/ClickHouseContainer/, ~/sqlFor\(\s*["']clickhouse["']\s*\)/],
    ]
    static final List<String> POSTGRES_BASES = ["NorthwindGateSpec", "NorthwindCoGateSpec"]

    // ════════════════════════════════════════════════════════════════════════════════════════
    //  2. THE CHECKS — one per place the decision can silently break
    // ════════════════════════════════════════════════════════════════════════════════════════

    /**
     * CHECK 1 · THE CURRICULUM. Each episode's `data.source` names the Northwind its series is
     * assigned. This is where a dataset choice is written down first, so it is where a wrong
     * one is cheapest to catch. An exception in CLASSIC_EPISODES that no episode needs any more
     * fails too, so the list never outlives what it excused.
     */
    @Unroll
    def "#track · curriculum: every episode lists the Northwind its series is assigned"() {
        given:
        def doc = CurriculumSpec.load(new File(CurriculumSpec.COURSES, track))
        def problems = []
        def excusesUsed = [] as Set

        doc.series.each { s ->
            def key = seriesKey(track, s)
            // A classic series lists `northwind` on every episode, or on none (Learn SQL Series 1,
            // which has no data blocks; check 2 guards it through its code). Half is a leak.
            boolean listsData = s.episodes.any { sourcesOf(it) }
            s.episodes.each { ep ->
                def sources = sourcesOf(ep)
                if (!sources && CLASSIC_SERIES.containsKey(key) && listsData) problems <<
                    "${where(track, s, ep)} has no data block, but the rest of this series lists `northwind`. " +
                    "Every episode of it says which dataset it runs on, so a change cannot slip through unlisted.\n" +
                    "  WHY: ${CLASSIC_SERIES[key]}\n" +
                    "  USE: data: { source: northwind, … } as in the other episodes of the series."
                if (!sources) return
                def epKey = "${key} · ${ep.n}".toString()
                def classicHere = sources.findAll { it in CLASSIC_SOURCES }

                if (CLASSIC_SERIES.containsKey(key)) {
                    if (sources != ["northwind"]) problems <<
                        "${where(track, s, ep)} lists ${sources}, but this whole series runs on classic " +
                        "Northwind (`northwind`) and nothing else.\n" +
                        "  WHY: ${CLASSIC_SERIES[key]}\n" +
                        "  TO CHANGE IT ON PURPOSE: edit CLASSIC_SERIES in DatasetsSpec, with the new reason."
                } else if (CLASSIC_EPISODES.containsKey(epKey)) {
                    excusesUsed << epKey
                    def allowed = CLASSIC_EPISODES[epKey].sources as List
                    if (classicHere.sort() != allowed.sort()) problems <<
                        "${where(track, s, ep)} lists ${sources}. From the classic file it may use exactly " +
                        "${allowed}, and the rest of it is Northwind Company.\n" +
                        "  WHY: ${CLASSIC_EPISODES[epKey].why}\n" +
                        "  TO CHANGE IT ON PURPOSE: edit CLASSIC_EPISODES in DatasetsSpec."
                } else if (classicHere) problems <<
                    "${where(track, s, ep)} lists ${classicHere}, the classic Northwind file. This series runs " +
                    "on Northwind Company, and a series never switches dataset halfway (CONTRIBUTING.md).\n" +
                    "  WHY: classic Northwind stays only where being tiny, keyless or installed on every engine " +
                    "is the lesson: ${CLASSIC_SERIES.keySet().join(', ')}, plus ${CLASSIC_EPISODES.keySet().join(', ')}.\n" +
                    "  USE: northwind_co_s (or _m / _l, and the datasets built from it).\n" +
                    "  IF THIS EPISODE TRULY NEEDS THE CLASSIC FILE: add it to CLASSIC_EPISODES in DatasetsSpec, with why."
            }
        }
        CLASSIC_EPISODES.keySet().findAll { it.startsWith("${track}/") && !(it in excusesUsed) }.each {
            problems << "CLASSIC_EPISODES excuses ${it}, but that episode no longer lists the classic file " +
                    "(or no longer exists). Good: delete the entry from DatasetsSpec, so the exception list " +
                    "never outlives what it excused."
        }

        when:
        failWithReasons(problems)

        then: "each problem above says what broke and why it must stay"
        true

        where:
        track << TRACKS
    }

    /**
     * CHECK 2 · THE TEST CODE. Each lesson spec and koan opens the Northwind its series is
     * assigned, in both directions:
     *   - in a classic series, every class must reach a classic base, and nothing may name
     *     Northwind Company;
     *   - in every other series, nothing may reach a classic base or name the classic file,
     *     except inside the episode folders CLASSIC_EPISODES excuses (`_25` and so on).
     * The curriculum only says which dataset an episode SHOULD use; the base class is what it
     * actually runs on. This is also the only check that covers Learn SQL Series 1, which has no
     * data blocks. Only real code is read (src/verify, src/koans/groovy): the `_todo` drafts all
     * say KoanBase and mean nothing yet.
     */
    @Unroll
    def "#track · code: every lesson spec and koan opens the Northwind its series is assigned"() {
        given:
        def doc = CurriculumSpec.load(new File(CurriculumSpec.COURSES, track))
        def parents = classParents()
        def problems = []

        doc.series.each { s ->
            def key = seriesKey(track, s)
            def folder = key.substring(track.length() + 1)
            boolean classic = CLASSIC_SERIES.containsKey(key)
            def excusedFolders = CLASSIC_EPISODES.keySet().findAll { it.startsWith("${key} · ") }
                    .collect { "_" + it.substring(key.length() + 3).padLeft(2, "0") }

            CODE_ROOTS.each { root ->
                def dir = new File("${root}/${track}/${folder}")
                if (!dir.exists()) return
                dir.eachFileRecurse { f ->
                    if (!f.name.endsWith(".groovy")) return
                    def text = f.text
                    def own = (text =~ CLASS_DECL).with { it.find() ? it.group(1) : null }
                    def opens = own ? northwindOf(own, parents) : null
                    def rel = "${root}/${track}/${folder}/${dir.toPath().relativize(f.toPath())}".replace('\\', '/')
                    def chain = own ? chainOf(own, parents).join(' → ') : ''

                    if (classic) {
                        if (opens == "company" || text.contains("northwind_co")) problems <<
                            "${rel} opens Northwind Company (${chain ?: 'northwind_co'}), but ${key} runs on " +
                            "classic Northwind.\n  WHY: ${CLASSIC_SERIES[key]}\n" +
                            "  USE: KoanBase (or SchemaKoanBase) for koans, NorthwindGateSpec for lesson specs."
                        else if (own && opens != "classic") problems <<
                            "${rel} (${chain}) does not open classic Northwind, and ${key} runs on it.\n" +
                            "  WHY: ${CLASSIC_SERIES[key]}\n" +
                            "  USE: KoanBase (or SchemaKoanBase) for koans, NorthwindGateSpec for lesson specs."
                    } else if (!excusedFolders.any { rel.contains("/${it}/") }) {
                        def named = CLASSIC_NAMES.find { text =~ it }
                        if (opens == "classic" || named) problems <<
                            "${rel} opens the classic Northwind file (${opens == 'classic' ? chain : named}), but " +
                            "${key} is not one of the places it stays.\n" +
                            "  WHY: classic Northwind stays only where being tiny, keyless or installed on every " +
                            "engine is the lesson: ${CLASSIC_SERIES.keySet().join(', ')}, plus " +
                            "${CLASSIC_EPISODES.keySet().join(', ')}.\n" +
                            "  USE: NorthwindCoKoanBase for koans, NorthwindCoGateSpec for lesson specs (or a base " +
                            "for the dataset this series names).\n" +
                            "  IF THIS EPISODE TRULY NEEDS THE CLASSIC FILE: add it to CLASSIC_EPISODES, with why."
                    }
                }
            }
        }

        when:
        failWithReasons(problems)

        then: "each problem above says what broke and why it must stay"
        true

        where:
        track << TRACKS
    }

    /**
     * CHECK 3 · THE CLASSIC FILE ITSELF. It still has the rows and the flaws its lessons teach
     * from. The file is DataPallas's demo data, regenerated by DataPallas's own code; if that code
     * changes, these lessons lose what they were built on, and nothing else would notice, because
     * DataPallas's demos do not care. Checked on a throwaway copy, never on the file itself.
     */
    def "classic file: it still has the rows and flaws its lessons teach from"() {
        given:
        def copy = File.createTempFile("datasets-classic-", ".duckdb")
        Files.copy(new File("../datasets/northwind/northwind.duckdb").toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING)
        def db = Sql.newInstance("jdbc:duckdb:" + copy.absolutePath, "org.duckdb.DuckDBDriver")
        def count = { String sql -> db.firstRow(sql)[0] as long }
        def problems = []

        // Data Modeling Series 1: nothing declared, and the empty tables stay empty.
        def constraints = count("SELECT count(*) FROM information_schema.table_constraints WHERE table_schema = 'main'")
        if (constraints) problems <<
            "main.* now declares ${constraints} constraint(s). ${CLASSIC_SERIES['datamodeling/series1']}"
        ["Territories", "EmployeeTerritories", "Region", "CustomerDemographics", "CustomerCustomerDemo"].each { t ->
            def rows = count("SELECT count(*) FROM main.\"${t}\"")
            if (rows) problems << "main.\"${t}\" now has ${rows} rows; Data Modeling Series 1 is built on it being " +
                    "empty. ${CLASSIC_SERIES['datamodeling/series1']}"
        }

        // Learn SQL Series 1: the published numbers.
        [Customers: 25, Orders: 79, "Order Details": 193, Products: 20].each { t, want ->
            def rows = count("SELECT count(*) FROM main.\"${t}\"")
            if (rows != want) problems << "main.\"${t}\" has ${rows} rows, not ${want}. ${CLASSIC_SERIES['learnsql/series1']}"
        }

        // Data Modeling Series 3 · 25 and · 30: the two flaws of the star they judge.
        def maxDay = count("SELECT max(day(date_key)) FROM main.dim_time")
        if (maxDay != 28) problems << "main.dim_time now reaches day ${maxDay}, not 28. " +
                "${CLASSIC_EPISODES['datamodeling/series3 · 25'].why}"
        def orderCols = count("SELECT count(*) FROM information_schema.columns WHERE table_name = 'fact_sales' " +
                "AND lower(column_name) LIKE '%order%'")
        if (orderCols) problems << "main.fact_sales now has an order-number column. " +
                "${CLASSIC_EPISODES['datamodeling/series3 · 30'].why}"

        when:
        failWithReasons(problems.collect { "The classic Northwind file changed: " + it } + (problems ? [
                "It is regenerated by DataPallas (bkend/common/.../db/northwind). Either restore what changed there, " +
                "or move the lessons named above off it and update DatasetsSpec."] : []))

        then: "each problem above says what broke and why it must stay"
        true

        cleanup:
        db?.close()
        copy?.delete()
    }

    /**
     * CHECK 4 · THE ENGINES IN THE CURRICULUM. The engines an episode names (in its title, its
     * hands_on and its data.reason; YAML comments are notes and do not count) belong to its series'
     * pair, unless ENGINE_EPISODES lists the episode with the role the other engine plays there.
     */
    @Unroll
    def "#track · curriculum: every episode names only the engines its series runs on"() {
        given:
        def doc = CurriculumSpec.load(new File(CurriculumSpec.COURSES, track))
        def problems = []
        def excusesUsed = [] as Set

        doc.series.each { s ->
            def key = seriesKey(track, s)
            def pair = pairOf(key)
            if (pair == null) return
            s.episodes.each { ep ->
                def epKey = "${key} · ${ep.n}".toString()
                def text = ([ep.title, ep.data?.reason] + (ep.hands_on ?: [])).findAll { it }.join("\n")
                def foreign = ENGINE_WORDS.findAll { e, re -> !(e in PAIRS[pair]) && text =~ re }.keySet()
                foreign.each { e ->
                    if (ENGINE_EPISODES[epKey]?.engine == e) { excusesUsed << epKey; return }
                    problems << "${where(track, s, ep)} names ${e}, but ${key} runs on ${PAIRS[pair].join(' + ')}.\n" +
                        "  WHY: ${whyOf(key, pair)}\n" +
                        "  IF ${e} PLAYS A ROLE HERE (a baseline, a source, a target) AND IS NOT WHAT THE LESSON RUNS ON: " +
                        "add the episode to ENGINE_EPISODES in DatasetsSpec, with that role.\n" +
                        "  TO MOVE THE SERIES TO THE OTHER PAIR ON PURPOSE: edit ANALYTICS_SERIES, with the new reason."
                }
            }
        }
        ENGINE_EPISODES.keySet().findAll { it.startsWith("${track}/") && !(it in excusesUsed) }.each {
            problems << "ENGINE_EPISODES excuses ${it}, but that episode no longer names ${ENGINE_EPISODES[it].engine} " +
                    "(or no longer exists, or its series now runs on it). Good: delete the entry from DatasetsSpec."
        }

        when:
        failWithReasons(problems)

        then: "each problem above says what broke and why it must stay"
        true

        where:
        track << TRACKS
    }

    /**
     * CHECK 5 · THE ENGINES IN THE TEST CODE. The curriculum says which engines an episode SHOULD
     * use, and the code is what it actually opens. A spec or koan in an analytics series must not
     * reach PostgreSQL (and so must not extend a gate whose ENGINES include it). One in an everyday
     * series must not reach ClickHouse. The folders of episodes in ENGINE_EPISODES are excused for
     * their one engine.
     */
    @Unroll
    def "#track · code: every lesson spec and koan opens only the engines its series runs on"() {
        given:
        def doc = CurriculumSpec.load(new File(CurriculumSpec.COURSES, track))
        def parents = classParents()
        def problems = []

        doc.series.each { s ->
            def key = seriesKey(track, s)
            def pair = pairOf(key)
            if (pair == null) return
            def folder = key.substring(track.length() + 1)
            def excused = ENGINE_EPISODES.findAll { k, v -> k.startsWith("${key} · ") }
                    .collectEntries { k, v -> ["_" + k.substring(key.length() + 3).padLeft(2, "0"), v.engine] }

            CODE_ROOTS.each { root ->
                def dir = new File("${root}/${track}/${folder}")
                if (!dir.exists()) return
                dir.eachFileRecurse { f ->
                    if (!f.name.endsWith(".groovy")) return
                    def text = f.text
                    def rel = "${root}/${track}/${folder}/${dir.toPath().relativize(f.toPath())}".replace('\\', '/')
                    def own = (text =~ CLASS_DECL).with { it.find() ? it.group(1) : null }
                    def chain = own ? chainOf(own, parents) : []
                    ENGINE_IN_CODE.each { e, patterns ->
                        if (e in PAIRS[pair]) return
                        if (excused.any { folderName, engine -> engine == e && rel.contains("/${folderName}/") }) return
                        def hit = patterns.find { text =~ it }?.toString() ?:
                                (e == "postgres" ? chain.find { it in POSTGRES_BASES } : null)
                        if (hit) problems << "${rel} reaches ${e} (${hit}), but ${key} runs on ${PAIRS[pair].join(' + ')}.\n" +
                            "  WHY: ${whyOf(key, pair)}\n" +
                            "  IF ${e} PLAYS A ROLE IN THIS EPISODE (a baseline, a source, a target): add it to ENGINE_EPISODES in DatasetsSpec, with that role."
                    }
                }
            }
        }

        when:
        failWithReasons(problems)

        then: "each problem above says what broke and why it must stay"
        true

        where:
        track << TRACKS
    }

    /**
     * CHECK 6 · THE FIGURES THE CURRICULUM QUOTES ABOUT THE CHANGE LOG. Thirty-odd `reason:` fields
     * across Data Warehousing, ETL and dbt quote totals from D2 (the change log,
     * academy-northwind-co-changes.groovy): how many events, how many arrive late, which offsets get
     * redelivered. Every one of them was typed by hand from a log line, and they go stale the moment
     * the generator is touched — silently, because a `reason:` is prose and prose does not fail a
     * build.
     *
     * They already had gone stale. One entry read "6,138 events … (4,720 creates, 1,406 updates,
     * 6 deletes)", whose parts are six short of its own total; it had been wrong since it was
     * written and nothing noticed. So the generator now STATES its figures — it writes
     * FIGURES (below) on every run — and this check reads the prose back against that file.
     *
     * WHAT IT CHECKS AND WHAT IT CANNOT. FIGURE_PHRASES pairs each figure with the wordings the
     * curriculum actually uses; where a wording matches, the number it captures must equal the
     * manifest's. It is exact, not a heuristic — but it only sees phrasings it knows. Rewording an
     * entry past every pattern would slip by, which is why REQUIRE_A_FIGURE makes the episodes that
     * lean hardest on the log quote at least one checkable number. When you word a figure a new way,
     * add the pattern here; that is the ten lines CurriculumSpec's header asks for.
     *
     * RE-EMITTING IT: run academy-northwind-co-changes.groovy with FIGURES_FILE set to this path
     * (scale S — the scale the prose quotes), then run this check and fix what it names. Never
     * hand-edit the manifest: it is the generator's word, and the whole point is that no human
     * re-types these numbers.
     */
    def "change log: every figure the curriculum quotes is the figure the generator emits"() {
        given:
        def manifest = new File(FIGURES)
        def problems = []

        expect: "the generator has stated its figures at all"
        assert manifest.isFile(),
                "${FIGURES} is missing. Run academy-northwind-co-changes.groovy (SCALE=S) with " +
                "FIGURES_FILE set to it, then re-run this check."

        when:
        def figures = readFigures(manifest)
        // `plants:` counts the planted incidents by name; the prose quotes their total.
        figures["planted_incidents"] = figures.findAll { it.key.startsWith("plant.") }*.value.sum() as Integer

        TRACKS.each { track ->
            def doc = CurriculumSpec.load(new File(CurriculumSpec.COURSES, track))
            doc.series.each { s ->
                s.episodes.each { ep ->
                    String reason = ep.data?.reason
                    if (!reason || !quotesChangeLog(ep, reason)) return
                    def matched = [] as Set

                    FIGURE_PHRASES.each { String figure, List<java.util.regex.Pattern> patterns ->
                        Integer want = figures[figure] as Integer
                        if (want == null) return
                        patterns.each { p ->
                            def m = reason =~ p
                            while (m.find()) {
                                matched << figure
                                Integer got = m.group(1).replace(",", "") as Integer
                                if (got != want) problems <<
                                    "${where(track, s, ep)} says \"${m.group(0).trim()}\", but the change log's " +
                                    "${figure} is ${want}.\n" +
                                    "  WHY: the generator wrote ${figure}: ${want} into ${FIGURES} on its last run. " +
                                    "The prose is a hand-typed copy and this one has drifted.\n" +
                                    "  FIX: put ${want} in the reason. If the number should have stayed what the " +
                                    "prose says, the generator changed by accident — fix the generator instead."
                            }
                        }
                    }

                    if (!matched && "${track} · ${ep.slug}".toString() in REQUIRE_A_FIGURE) problems <<
                        "${where(track, s, ep)} is built on the change log but quotes no figure this check " +
                        "recognises.\n" +
                        "  WHY: REQUIRE_A_FIGURE lists the episodes whose case rests on the log's size or shape, " +
                        "so their reason must carry at least one number that can be checked against " +
                        "${FIGURES}.\n" +
                        "  FIX: quote a figure in a wording FIGURE_PHRASES knows — or, if you have worded one a " +
                        "new way, add that wording to FIGURE_PHRASES in DatasetsSpec."
                }
            }
        }

        then:
        failWithReasons(problems)
        true
    }

    /**
     * CHECK 7 · THE SCRIPTS SUPPORT THE ENGINES THEIR SERIES RUN ON. The scripts moved into this repo
     * on 2026-09-20 (datasets/scripts/, out of the product's db/scripts so they stop filling
     * DataPallas's Seed-Data dropdown — DataPallas is a BI platform, not a learning one). Before that
     * the header above could only ASSERT which of them speak ClickHouse; now the files are here, so
     * this reads them.
     *
     * It matters because CHECK 4 lets an analytics series name ClickHouse in its prose. If the script
     * that installs that series' dataset cannot target ClickHouse, the curriculum promises an engine
     * no learner can reach — which is exactly the state `the-northwind-cubes` was in.
     *
     * A deliberately crude test: does the vendor name appear at all? These scripts branch on `vendor`,
     * so a script with no CLICKHOUSE branch cannot produce ClickHouse DDL. It cannot prove the branch
     * is CORRECT — only a run on ClickHouse does that, and none has happened yet. It catches the case
     * that has actually bitten: a script quietly supporting two engines while the curriculum sells three.
     */
    def "the seed scripts speak the engines their series are sold on"() {
        given:
        def problems = []

        when:
        SCRIPT_ENGINES.each { String name, boolean mustSpeakClickHouse ->
            File f = new File(SCRIPTS, "${name}.groovy")
            if (!f.isFile()) {
                problems << "datasets/scripts/${name}.groovy is missing.\n" +
                        "  WHY: SCRIPT_ENGINES in DatasetsSpec lists it as a script this academy installs from.\n" +
                        "  FIX: restore it, or drop it from SCRIPT_ENGINES if the dataset is genuinely gone."
                return
            }
            boolean speaks = f.text.contains("CLICKHOUSE")
            if (mustSpeakClickHouse && !speaks) problems <<
                    "datasets/scripts/${name}.groovy never mentions CLICKHOUSE, but an analytics series " +
                    "installs from it.\n" +
                    "  WHY: Data Warehousing Series 2-3, dbt and the ETL analytics series run on ClickHouse. " +
                    "A script that cannot target it cannot install the data those lessons need.\n" +
                    "  FIX: add the CLICKHOUSE branch — or, if that dataset is genuinely DuckDB/PostgreSQL only, " +
                    "move it to the false list here AND say so in the reason of every episode that reads it, " +
                    "the way the-northwind-cubes does."
            if (!mustSpeakClickHouse && speaks) problems <<
                    "datasets/scripts/${name}.groovy mentions CLICKHOUSE, but it is listed as a script only " +
                    "everyday series use.\n" +
                    "  WHY: everyday series run on PostgreSQL and DuckDB. ClickHouse support here is either " +
                    "dead code or a sign the dataset has been pulled into an analytics series.\n" +
                    "  FIX: if an analytics series now reads it, move it to the true list. Otherwise delete " +
                    "the branch, so nobody maintains an engine nothing runs."
        }

        then:
        failWithReasons(problems)
        true
    }

    // ════════════════════════════════════════════════════════════════════════════════════════
    //  3. HELPERS
    // ════════════════════════════════════════════════════════════════════════════════════════

    /** The generators, beside the datasets they produce, since 2026-09-20. */
    static final File SCRIPTS = new File("../datasets/scripts")

    /**
     * Which scripts must speak ClickHouse. True for everything an analytics series installs from;
     * false for the datasets only the everyday series (PostgreSQL + DuckDB) read.
     *
     * academy-cubes is false and is the interesting one: Data Warehousing Series 2 RUNS on DuckDB +
     * ClickHouse, yet the cube writer targets DuckDB and PostgreSQL only. That is a real gap, not an
     * oversight in this list — `the-northwind-cubes` is `fit: partial` and its reason says so. When
     * the script learns ClickHouse, flip this to true and the check will hold it there.
     * academy-files-export is false because it writes CSV/XLSX/Parquet out of the source database;
     * the engine that later READS those files is not its concern.
     */
    static final Map<String, Boolean> SCRIPT_ENGINES = [
        "academy-northwind-co-install"    : true,
        "academy-northwind-co-changes"    : true,
        "academy-northwind-co-raw-install": true,
        "academy-northwind-co-dw-install" : true,
        "academy-verify"                  : true,
        "academy-uninstall"               : true,
        "academy-library-install"         : false,
        "academy-crm-export-install"      : false,
        "academy-cubes"                   : false,
        "academy-files-export"            : false,
    ]

    /** What academy-northwind-co-changes.groovy writes on every run. Never hand-edited. */
    static final String FIGURES = "../datasets/northwind-co/northwind_co_changes_s-figures.yaml"

    /**
     * The manifest is two flat blocks of `key: integer`, so it is read directly rather than through
     * a YAML library: `figures:` keys as they are, `plants:` keys prefixed `plant.`.
     */
    static Map<String, Integer> readFigures(File f) {
        def out = [:]
        String block = null
        f.eachLine("UTF-8") { String line ->
            if (line.startsWith("#") || !line.trim()) return
            if (!line.startsWith(" ")) { block = line.trim().replace(":", ""); return }
            def m = line.trim() =~ /^([\w-]+):\s*(\d+)$/
            if (m.matches() && block in ["figures", "plants"])
                out[(block == "plants" ? "plant." : "") + m.group(1)] = m.group(2) as Integer
        }
        out
    }

    /** An episode draws on the change log when it reads one of D2's schemas, or names it as D2. */
    static boolean quotesChangeLog(Map ep, String reason) {
        sourcesOf(ep).any { it.startsWith("northwind_co_changes") || it.startsWith("northwind_co_raw") } ||
                reason =~ /\bD2\b/
    }

    /**
     * The wordings the curriculum uses for each figure, with the number as group 1.
     *
     * Each pattern is anchored on the phrase that means THE TOTAL, not merely on the unit word,
     * because the curriculum also quotes legitimate subsets and rates in the same words and those
     * must not be dragged in: "30 events touching one month" (one day of the log appended to an
     * Iceberg table), "~12 updates a day" (a rate), and "Every D2 event carries both times" — where
     * a pattern reading digits before "event" would helpfully find the 2 in D2. All three were
     * flagged by an earlier, looser version of this list; they are correct prose, so the patterns
     * were tightened rather than the entries.
     */
    static final Map<String, List<java.util.regex.Pattern>> FIGURE_PHRASES = [
        events                  : [~/(\d[\d,]*) events over/, ~/(\d[\d,]*)-event log/,
                                   ~/(\d[\d,]*) events at S/, ~/each of its (\d[\d,]*) events/],
        arrival_days            : [~/(\d[\d,]*) arrival days/],
        op_create               : [~/\((\d[\d,]*) creates/],
        op_update               : [~/creates, (\d[\d,]*) updates/],
        op_delete               : [~/updates, (\d[\d,]*) deletes/],
        op_unknown              : [~/deletes, and (\d[\d,]*) whose op/],
        late_by_days            : [~/(\d[\d,]*) transactions? arriv\w+ 1[-–— ]?(?:to )?5 days late/, ~/(\d[\d,]*) late\b/],
        out_of_order_by_minutes : [~/(\d[\d,]*) a few minutes out of order/, ~/(\d[\d,]*) out of order/],
        sent_twice              : [~/(\d[\d,]*) (?:transactions? )?delivered twice/, ~/(\d[\d,]*) sent twice/],
        redelivered_offset_first: [~/offsets \(?(\d[\d,]*)(?: to |[-–—])/],
        redelivered_offset_last : [~/offsets \(?\d[\d,]*(?: to |[-–—])(\d[\d,]*)/],
        rejects                 : [~/(\d[\d,]*)[- ]record reject burst/],
        control_totals          : [~/(\d[\d,]*) daily control totals/],
        planted_incidents       : [~/_plants names all (\d[\d,]*) incidents/],
    ]

    /**
     * Episodes whose case rests on the log's size or shape: these must quote a checkable figure, so
     * that rewording one cannot quietly drop it out of this check's sight.
     */
    static final Set<String> REQUIRE_A_FIGURE = [
        "etlpipelines · change-data-capture",
        "etlpipelines · event-time-and-watermarks",
        "etlpipelines · staging-in-a-stream",
        "etlpipelines · replay-and-reprocessing",
        "etlpipelines · project-streaming-pipeline",
    ] as Set


    /**
     * Fails with the problems alone, one paragraph each, so the reason is the first thing read.
     * (A plain Spock condition would print the whole list three times over.)
     */
    static void failWithReasons(List problems) {
        if (problems) throw new AssertionError("\n\n" + problems.join("\n\n") + "\n")
    }

    static final List<String> CODE_ROOTS = ["src/verify/groovy/datazeus", "src/koans/groovy/datazeus"]

    /**
     * A class declaration at the start of a line (so the examples inside javadoc don't count).
     * `extends a.b.KoanBase` counts as KoanBase.
     */
    static final def CLASS_DECL = ~/(?m)^\s*(?:abstract\s+)?class\s+(\w+)(?:\s+extends\s+(?:[\w.]+\.)?(\w+))?/

    /** Every class under CODE_ROOTS, mapped to the class it extends. */
    static Map<String, String> classParents() {
        def parents = [:]
        CODE_ROOTS.each { root ->
            new File(root).eachFileRecurse { f ->
                if (!f.name.endsWith(".groovy")) return
                def m = f.text =~ CLASS_DECL
                while (m.find()) if (m.group(2)) parents[m.group(1)] = m.group(2)
            }
        }
        parents
    }

    /** MyKoans → NorthwindModelChecks → SchemaKoanBase → … up to the first Northwind base. */
    static List<String> chainOf(String cls, Map<String, String> parents) {
        def chain = [cls]
        while (!(chain[-1] in CLASSIC_BASES + COMPANY_BASES) && parents[chain[-1]] && chain.size() < 20)
            chain << parents[chain[-1]]
        chain
    }

    /**
     * "classic", "company" or null. The FIRST Northwind base on the way up decides, because
     * NorthwindCoKoanBase itself extends KoanBase and must still count as Company.
     */
    static String northwindOf(String cls, Map<String, String> parents) {
        def top = chainOf(cls, parents)[-1]
        top in CLASSIC_BASES ? "classic" : top in COMPANY_BASES ? "company" : null
    }

    /** `data.source` as a list: it is a single value, or a list when an episode joins two. */
    static List<String> sourcesOf(Map ep) {
        def s = ep.data?.source
        s == null ? [] : (s instanceof List ? s : [s]).collect { it as String }
    }

    /** "analytics", "everyday", or null for a series where any engine goes. */
    static String pairOf(String seriesKey) {
        seriesKey in ANY_ENGINE_SERIES ? null : seriesKey in ANALYTICS_SERIES ? "analytics" : "everyday"
    }

    static String whyOf(String seriesKey, String pair) { pair == "analytics" ? ANALYTICS_SERIES[seriesKey] : EVERYDAY_WHY }

    /** "learnsql/series4" — the key CLASSIC_SERIES uses. */
    static String seriesKey(String track, Map s) { "${track}/${(s.slug =~ /^(series\d+)/)[0][1]}".toString() }

    /** "learnsql, series4-cross-vendor · 05 (portable-paging)" — how a failure names an episode. */
    static String where(String track, Map s, Map ep) { "${track}, ${s.slug} · ${ep.n} (${ep.slug})" }
}
