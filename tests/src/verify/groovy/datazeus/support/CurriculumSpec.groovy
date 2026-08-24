package datazeus.support

import org.yaml.snakeyaml.Yaml
import spock.lang.Specification
import spock.lang.Unroll

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  THE ROADMAP GATE — every courses/<track>/curriculum.yaml, checked        ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * WHY THIS EXISTS, WRITTEN DOWN SO NOBODY DELETES IT AS BUREAUCRACY.
 *
 * The curriculum files carry a lot of rules in comments — slugs are unique, `n` ascends,
 * every episode is hands-on, every series leaves an artifact, a title has a hook. Comments
 * do not enforce anything, and the honour system had already failed twice before this file
 * existed:
 *
 *   1. Learn SQL episode 05's title said one thing in curriculum.yaml and another in its
 *      .mdx front-matter. The commit that "fixed" it aligned five places and left the sixth,
 *      and the sixth is the one that wins on the rendered page.
 *   2. Data Modeling's generated lesson files were a SNAPSHOT of `title` and `hands_on`.
 *      Twenty edits to the curriculum later, 19 of 42 lesson files disagreed with it.
 *
 * Both were invisible until somebody went looking. Neither can happen again while this runs.
 *
 * It is deliberately in src/verify (the publish GATE, run by `zeus test` and CI) and not in
 * src/koans: a learner should never see it, and a red one should block publishing.
 *
 * ADDING A RULE: put it here, not in a comment. If a rule is worth writing in the header of
 * a curriculum.yaml, it is worth ten lines in this file.
 */
class CurriculumSpec extends Specification {

    /** courses/ lives beside tests/ — the module runs with tests/ as its basedir. */
    static final File COURSES = new File("../courses")

    /** Vocabulary for Data Modeling's track-local fields. Extend here when the track does. */
    static final List<String> KOAN_RUNGS =
            ["predict", "diagnose", "choose", "complete", "author", "migration", "reconcile"]
    static final List<String> TABS =
            ["er-diagram", "database-schema", "domain-grouped-schema", "ubiquitous-language"]
    static final List<String> DATASETS = ["northwind:read", "northwind:rebuild", "northwind:star",
                                          "library", "library:brief", "library+northwind", "case-study"]

    /**
     * Parse a curriculum, REJECTING duplicate keys the way the website does.
     *
     * SnakeYAML allows duplicate mapping keys by default (last one wins). js-yaml, which
     * lib/curriculum.ts uses, throws. That difference let a genuinely broken file sail
     * through this gate on 2026-08-24 — a script had written `core: true` twice into one
     * episode, the whole suite stayed green, and the site build was the thing that failed.
     *
     * A gate that is more permissive than production is worse than no gate, because it is
     * trusted. So: match the strictest consumer.
     */
    static Map load(File dir) {
        def opts = new org.yaml.snakeyaml.LoaderOptions()
        opts.allowDuplicateKeys = false
        return new Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(opts))
                .load(new File(dir, "curriculum.yaml").text) as Map
    }

    static List<File> trackDirs() {
        COURSES.listFiles()?.findAll { it.directory && new File(it, "curriculum.yaml").exists() }?.sort { it.name } ?: []
    }

    static List episodesOf(Map doc) { doc.series.collectMany { it.episodes } }

    // ══════════════════════════════════════════════════════════════════════
    //  Rules that hold for EVERY track
    // ══════════════════════════════════════════════════════════════════════

    @Unroll
    def "#track: every episode has the fields the site and the file names depend on"() {
        given:
        def doc = load(dir)

        expect:
        doc.course && doc.track && doc.title

        and: "n, slug, title and status are what the URL, the folder and the page are built from"
        episodesOf(doc).every { it.n && it.slug && it.title && it.status }

        and: "status is one of the two the loader understands"
        episodesOf(doc).every { it.status in ["published", "planned"] }

        where:
        dir << trackDirs()
        track = dir.name
    }

    @Unroll
    def "#track: episode slugs are unique across the whole course"() {
        // The route drops the series (/data-academy/<course>/<slug>), so a duplicate slug is
        // two lessons fighting over one URL — and the loser is silently unreachable.
        given:
        def slugs = episodesOf(load(dir))*.slug
        def dupes = slugs.countBy { it }.findAll { it.value > 1 }.keySet()

        expect:
        dupes.isEmpty()

        where:
        dir << trackDirs()
        track = dir.name
    }

    @Unroll
    def "#track: n ascends within each series"() {
        // Gap numbering (00, 05, 10) only buys anything if it stays ordered — an inserted
        // episode that sorts wrong is invisible on the page, which renders by array position.
        given:
        def doc = load(dir)
        def bad = doc.series.collectMany { s ->
            def ns = s.episodes*.n*.toInteger()
            ns == ns.sort(false) ? [] : ["${s.slug}: ${ns}"]
        }

        expect:
        bad.isEmpty()

        where:
        dir << trackDirs()
        track = dir.name
    }

    @Unroll
    def "#track: every title has a hook after the em dash"() {
        // "<topic> — <why you care>". A title without the separator is a lecture title, and
        // the course page renders the two halves differently, so it also looks wrong.
        given:
        def bad = episodesOf(load(dir)).findAll { !it.title.contains(" — ") }*.slug

        expect:
        bad.isEmpty()

        where:
        dir << trackDirs()
        track = dir.name
    }

    @Unroll
    def "#track: short fits the roadmap card and leads with the same keywords as the title"() {
        // The rule's PURPOSE (see the learnsql field docs) is that the roadmap card and the
        // course page are scanned by the same keywords. So the test is not string equality —
        // "Grouping & Aggregating" and "Grouping and Aggregating" scan identically, and the
        // house style abbreviates freely in a label that has to fit on one line.
        //
        // What it does forbid is SUBSTITUTION: `short` may drop words from the topic half, it
        // may not introduce new ones. "Joining in Memory" is a fine compression of "Joining
        // Two Datasets in Memory"; "CLI Tools" for "Command-Line Tools" is not, because
        // someone scanning for "command-line" now finds nothing.
        given:
        def topic = { String s -> s.contains(" — ") ? s.split(" — ")[0] : s }
        def words = { String s ->
            s.toLowerCase().replace("&", " and ").replaceAll(/[^a-z0-9 ]/, " ")
             .split(/\s+/).findAll { it && it != "and" } as Set
        }
        def bad = episodesOf(load(dir)).findAll { it.short }.collect { ep ->
            def introduced = words(topic(ep.short)) - words(topic(ep.title))
            if (ep.short.length() > 55) return "${ep.slug}: short is ${ep.short.length()} chars"
            if (introduced) return "${ep.slug}: short's topic half introduces ${introduced} — not in the title"
            null
        }.findAll()

        expect:
        bad.isEmpty()

        where:
        dir << trackDirs()
        track = dir.name
    }

    @Unroll
    def "#track: a series' PROJECT is core whenever its series is on the core path"() {
        // Renamed 2026-08-24: the tag was "capstone", which is US-academia jargon and the
        // one piece of it in a course whose whole voice is plain. It renders as a #badge on
        // the course page, so it was learner-facing. It is "project" now.
        //
        // The rule, and the reason: a project is where a series' whole argument gets applied.
        // If the shortest-path-to-productive walks a series and skips its project, that path
        // has no proof point — which is exactly what had happened to Data Modeling Series 2.
        // NOT "every project is core": a series with no core episodes rightly has none.
        given:
        def bad = load(dir).series.findAll { s ->
            def proj = s.episodes.find { (it.tags ?: []).contains("project") }
            proj && s.episodes.any { it.core } && !proj.core
        }*.slug

        expect:
        bad.isEmpty()

        where:
        dir << trackDirs()
        track = dir.name
    }

    @Unroll
    def "#track: every 'Series N · MM' cross-reference resolves to a real episode"() {
        // `excludes` and `prerequisites` are the ONLY prose a learner reads that points at
        // another course, and they render verbatim on the course page under "Not here, on
        // purpose" and "Where this leads". They are also pure prose, so nothing stopped them
        // going stale — and three had:
        //
        //   - Java & Groovy asked for "JOINs and GROUP BY from Series 2" of Learn SQL. Both
        //     are in Series 1 (30 and 40). Redundant AND false.
        //   - Learn SQL sent ROLLUP/CUBE to Data Warehousing "where the star schema lives".
        //     The star is DESIGNED in Data Modeling Series 3; Warehousing has it from the
        //     query side. Same error I had already fixed in Data Model Patterns' pointer.
        //   - Data Ops still called Learn SQL's tuning episode "the tuning capstone" after
        //     both the rename and the retitle.
        //
        // A reference with an explicit course name resolves against that course; a bare
        // "Series N · MM" inside a prerequisite resolves against the course it requires, and
        // inside an exclude against the track itself.
        //
        // WHAT THIS RULE DOES NOT CATCH, and a human still has to: it proves the referenced
        // episode EXISTS, not that it is the RIGHT one. Java & Groovy's excludes said
        // "Series 4 · 05 teaches JPA and Series 4 · 10 the Hibernate behaviour" after the
        // Series 4 reorder moved JPA to 00 and Hibernate to 05. Both 05 and 10 still existed,
        // so this passed green while pointing a reader at the wrong two episodes.
        // Renumbering a series means re-reading every reference into it, by eye.
        given:
        def doc = load(dir)
        def byTitle = trackDirs().collectEntries { d -> def c = load(d); [(c.title): c] }
        def bad = []

        // Only a string that IS a known course title counts as one — otherwise the preceding
        // prose gets swallowed as a course name ("…teaches JPA as the vocabulary and Series 4 · 05").
        // Longest first, so "Data Warehousing" wins over "Data Ops" style prefixes.
        def names = (byTitle.keySet() + byTitle.keySet().collect { "Learn " + it })
                .unique().sort { -it.length() }
        def alt = names.collect { java.util.regex.Pattern.quote(it) }.join("|")

        def check = { String text, Map defaultCourse ->
            if (!text) return
            def m = text =~ /(?:(${alt}),?\s+)?Series (\d+) · (\d+)/
            m.each { g ->
                // A BARE reference means "this course" — in an exclude that is the track
                // itself; in a prerequisite it is the course being required.
                def course = g[1] ? (byTitle[g[1]] ?: byTitle[g[1] - "Learn "]) : defaultCourse
                if (!course) { bad << "cannot resolve the course for '${g[0]}'"; return }
                def s = course.series.find { it.slug.startsWith("series" + g[2]) }
                if (!s) { bad << "${course.title} has no Series ${g[2]} (referenced as '${g[0]}')"; return }
                if (!s.episodes.find { it.n == g[3] }) {
                    bad << "${course.title} Series ${g[2]} has no episode ${g[3]} (referenced as '${g[0]}')"
                }
            }
        }

        (doc.excludes ?: []).each { check(it.where, doc) }
        (doc.prerequisites ?: []).each { p ->
            def target = byTitle.values().find { it.course == p.course }
            check(p.needs, target)
            check(p.why, target)
        }

        expect:
        bad.isEmpty()

        where:
        dir << trackDirs()
        track = dir.name
    }

    @Unroll
    def "#track: prerequisites point at courses that exist"() {
        given:
        def known = trackDirs().collect { load(it).course }
        def bad = (load(dir).prerequisites ?: []).findAll { !(it.course in known) }*.course

        expect:
        bad.isEmpty()

        where:
        dir << trackDirs()
        track = dir.name
    }

    // ══════════════════════════════════════════════════════════════════════
    //  The lesson bundle must agree with the roadmap — the drift that bit twice
    // ══════════════════════════════════════════════════════════════════════

    @Unroll
    def "#track: every lesson file's front-matter title matches the curriculum"() {
        // THIS IS THE ONE. It catches both historical failures: a published lesson whose MDX
        // silently overrides the roadmap on the rendered page, and generated boilerplate that
        // was a snapshot of a title which has since been rewritten.
        //
        // Checked for PLANNED episodes too, not just published ones: a stale title in an
        // unpublished file is a bug that is merely waiting for someone to flip a flag.
        given:
        def doc = load(dir)
        def bad = []
        doc.series.each { s ->
            s.episodes.each { ep ->
                // A lesson bundle is either finished (00-slug.mdx) or a TODO brief
                // (_todo-00-slug.mdx). Both are checked: a stale title in an unwritten file is
                // a bug waiting for someone to drop the prefix. The _todo- form is invisible to
                // Contentlayer, whose Lesson pattern is `[0-9]*.mdx`, so it never reaches the site.
                def base = "${s.slug}/${ep.n}-${ep.slug}/${ep.n}-${ep.slug}"
                def mdx = new File(dir, "${base}.mdx")
                if (!mdx.exists()) mdx = new File(dir, "${s.slug}/${ep.n}-${ep.slug}/_todo-${ep.n}-${ep.slug}.mdx")
                if (!mdx.exists()) {
                    if (ep.status == "published") bad << "${ep.slug}: published but no .mdx at ${mdx}"
                    return
                }
                def m = (mdx.text =~ /(?m)^title:\s*"(.*)"$/)
                if (!m) { bad << "${ep.slug}: .mdx has no title in its front-matter"; return }
                if (m[0][1] != ep.title) {
                    bad << "${ep.slug}:\n     yaml: ${ep.title}\n     mdx : ${m[0][1]}\n" +
                           "     FIX: node tools/resync-lessons.js  (or edit the .mdx if the MDX is right)"
                }
            }
        }

        expect:
        bad.isEmpty()

        where:
        dir << trackDirs()
        track = dir.name
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Data Modeling's track-local promises
    // ══════════════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════════════
    //  The hands-on tripwire, for any track that opts in by using the field
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Tracks that declare `hands_on`, and the vocabulary each one is allowed. Two tracks use
     * it today and their values differ because their exercise machinery differs — Data
     * Modeling has DataPallas tabs and a practice schema, Java & Groovy has koans whose
     * assertion target varies. A shared list would be a lie in both directions.
     *
     * Opting in is what makes an episode with NO entry a build failure. That is the whole
     * point: an episode nobody could describe as hands-on has quietly become a lecture.
     */
    /**
     * ASSERTION TARGETS THE SIX ROADMAP TRACKS NEEDED AND THE FIRST FOUR DID NOT.
     * Added 2026-08-24. Each one exists because a whole series had no legal way to be graded,
     * NOT because a new name sounded good — that mistake was made once already, when four
     * named koan mechanisms were invented and had to be cut back to one.
     *
     *   rerun           run the load a second time; assert nothing changed. THE most valuable
     *                   of these: idempotency, watermarks, backfills and dbt incrementals are
     *                   all the same question, and it is invisible to a single run.
     *   concurrent      two sessions. Assert what B sees while A holds an open transaction or
     *                   a lock. Data Ops Series 2 (isolation levels, MVCC, deadlocks) is
     *                   ENTIRELY this shape and cannot be taught any other way — a single
     *                   session literally cannot observe a phantom read.
     *   count:scanned   rows or bytes the engine actually READ. This is the legal replacement
     *                   for the stopwatch that Data Warehousing Series 2 and 3 would otherwise
     *                   demand: partitioning, sort keys, projections and star-vs-OBT are all
     *                   "did it read less?", and both DuckDB and ClickHouse report it. Never
     *                   assert elapsed time — see conftest.py and KoanBase.
     *   equivalent      two queries or models must return the SAME result set. The oracle for
     *                   a refactor (dbt), for a rewrite (BI's number behind the widget), and
     *                   crucially for text-to-SQL: you cannot assert the SQL a model WROTE,
     *                   but you can assert the rows it RETURNS.
     *   eval            score generated output against a known-good set and assert a
     *                   THRESHOLD, not an exact match. AI Series 3 only. The one place in the
     *                   academy where a koan is allowed to be statistical, because the subject
     *                   genuinely is.
     */
    static final List<String> EXTRA_TARGETS =
            ["koan:rerun", "koan:concurrent", "koan:count:scanned", "koan:equivalent", "koan:eval"]

    /**
     * The DataPallas surface each track sends you to, mirroring "CloudBeaver for SQL" and
     * "Jupyter for Python". All of these already exist — none is a new dependency.
     *
     * For all BI and visualization needs we use DataPallas itself — never Metabase, or any
     * other bundled third-party app, when DataPallas can do it. Read
     * content/docs/bi-analytics/dashboards.mdx before touching this.
     *
     *   canvas          the Data Canvas — drop a cube, tick fields, "Visualize as" suggests
     *                   the shape, Finetune when it cannot, Display for the label, Publish.
     *   cube            the cube designer. Shared with Data Warehousing, which BUILDS cubes
     *                   where BI CONSUMES them — the same object from two ends.
     *   report-config   the data script, HTML template and per-component config on disk.
     *                   DataPallas's two dashboard-building paths produce the SAME files, so
     *                   this is not an "advanced mode" — it is the other door into one room.
     *   web-components  rb-value, rb-chart, rb-tabulator, rb-pivot-table, rb-parameters,
     *                   rb-report — the embeddable half.
     *   chat2db         ask in plain English, read the SQL it wrote, see the answer. The
     *                   honest surface for AI for Data Series 1, which is ABOUT that loop.
     */
    static final List<String> SURFACES =
            ["cloudbeaver", "jupyter", "rundeck", "terminal", "dbt", "duckdb", "clickhouse",
             "supabase", "canvas", "cube", "report-config", "web-components", "chat2db"]

    static boolean koan(String h) {
        (h.startsWith("koan:") && h.substring(5) in KOAN_RUNGS) || h in EXTRA_TARGETS ||
        h in ["koan:count:queries", "koan:count:rows-held", "koan:count:roundtrips",
              "koan:capture", "koan:process:tool", "koan:process:jar"]
    }

    static final Map<String, Closure<Boolean>> HANDS_ON_VOCAB = [
        datamodeling: { String h ->
            (h.startsWith("koan:") && h.substring(5) in KOAN_RUNGS) ||
            (h.startsWith("tab:") && h.substring(4) in TABS) ||
            h.startsWith("build:") || h in ["cloudbeaver", "brief"]
        },
        python: { String h ->
            (h.startsWith("koan:") && h.substring(5) in KOAN_RUNGS) ||
            h in ["koan:count:queries", "koan:count:rows-held", "koan:capture",
                  "koan:process:tool", "jupyter", "eyes"]
        },
        javagroovy: { String h ->
            (h.startsWith("koan:") && h.substring(5) in KOAN_RUNGS) ||
            h in ["koan:count:roundtrips", "koan:count:rows-held", "koan:capture",
                  "koan:process:tool", "koan:process:jar", "eyes"]
        },
        // Learn SQL opted in LAST, on 2026-08-24, because the field did not exist when it was
        // written — not because it lacked exercises. It has koans for 34 of its 35 episodes,
        // three of them written and green. The field was the only thing missing, and while it
        // was missing this track alone could not fail the "an episode with no hands_on has
        // quietly become a lecture" tripwire.
        // Two of its rungs are ones invented for the roadmap tracks, which is the strongest
        // evidence those were real and not decoration: Series 3 · 20/25 (EXPLAIN, indexes) is
        // `count:scanned` — the only honest way to grade "make it faster" without a stopwatch —
        // and Series 4 is `equivalent`, because "the same query on five engines" IS that assertion.
        learnsql:        { String h -> koan(h) || h in SURFACES || h == "eyes" },
        // NOT modelpatterns, NOT teardowns — deliberately. Those two are a different kind of
        // track (the agreed patterns, and field evidence of shipped schemas) with their own worked-out
        // conventions in their headers: citation rules, the rebuild rule, trademark handling,
        // and a four-beat shape that ends in a DataPallas build rather than an assertion.
        // Do not fold them into this vocabulary without deciding, deliberately, that they
        // should be graded the same way the other ten are.
        etlpipelines:    { String h -> koan(h) || h in SURFACES || h == "eyes" },
        dbt:             { String h -> koan(h) || h in SURFACES || h == "eyes" },
        datawarehousing: { String h -> koan(h) || h in SURFACES || h == "eyes" },
        dataops:         { String h -> koan(h) || h in SURFACES || h == "eyes" },
        bi:              { String h -> koan(h) || h in SURFACES || h == "eyes" },
        ai:              { String h -> koan(h) || h in SURFACES || h == "eyes" },
    ]

    @Unroll
    def "#track: every episode declares how it is hands-on, in that track's vocabulary"() {
        given:
        def doc = load(new File(COURSES, track))
        def bad = []
        episodesOf(doc).each { ep ->
            if (!ep.hands_on) { bad << "${ep.slug}: NO hands_on — that is a lecture"; return }
            ep.hands_on.findAll { !HANDS_ON_VOCAB[track].call(it) }
                       .each { bad << "${ep.slug}: unknown hands_on '${it}'" }
        }

        expect:
        bad.isEmpty()

        where:
        track << HANDS_ON_VOCAB.keySet()
    }

    @Unroll
    def "#track: `eyes` stays an exception, not a loophole"() {
        // `eyes` means "judged by a person, deliberately". It is honest for something like
        // "where the data code goes", which no assertion can grade. It is also the obvious
        // escape hatch for an episode nobody wanted to build an exercise for — so it is
        // capped at one per series. If a second one is needed, the series has a real problem
        // and the cap is what forces that conversation instead of hiding it.
        given:
        def bad = load(new File(COURSES, track)).series.findAll { s ->
            s.episodes.count { (it.hands_on ?: []).contains("eyes") } > 1
        }*.slug

        expect:
        bad.isEmpty()

        where:
        track << HANDS_ON_VOCAB.keySet()
    }

    def "data modeling: every episode declares how it is hands-on, and on what data"() {
        // The tripwire. An episode with no `hands_on` has quietly become a lecture, and this
        // track's whole claim is that none of them are. `dataset` says WHICH of the three
        // schemas it uses, which used to have to be reconstructed from memory.
        given:
        def doc = load(new File(COURSES, "datamodeling"))
        def noHandsOn = episodesOf(doc).findAll { !it.hands_on }*.slug
        def noDataset = episodesOf(doc).findAll { !it.dataset }*.slug

        expect:
        noHandsOn.isEmpty()
        noDataset.isEmpty()
    }

    def "data modeling: hands_on and dataset use the known vocabulary"() {
        given:
        def doc = load(new File(COURSES, "datamodeling"))
        def bad = []
        episodesOf(doc).each { ep ->
            ep.hands_on.each { h ->
                def ok = (h.startsWith("koan:") && h.substring(5) in KOAN_RUNGS) ||
                         (h.startsWith("tab:") && h.substring(4) in TABS) ||
                         h.startsWith("build:") || h in ["cloudbeaver", "brief"]
                if (!ok) bad << "${ep.slug}: unknown hands_on '${h}'"
            }
            if (!(ep.dataset in DATASETS)) bad << "${ep.slug}: unknown dataset '${ep.dataset}'"
        }

        expect:
        bad.isEmpty()
    }

    def "data modeling: every series leaves an artifact behind"() {
        // Series 3 once declared no `build:` at all — the only series producing nothing the
        // learner keeps was the one whose episodes are named "build the star schema".
        given:
        def doc = load(new File(COURSES, "datamodeling"))
        def bad = doc.series.findAll { s ->
            !s.episodes.any { (it.hands_on ?: []).any { h -> h.startsWith("build:") } }
        }*.slug

        expect:
        bad.isEmpty()
    }

    @Unroll
    def "#track: each lesson file lists the hands_on its episode actually declares"() {
        // The other half of the snapshot problem: the generated brief in each .mdx tells its
        // author which rung and which tab to write for. When the curriculum moved and the
        // files did not, fourteen of them were briefing the wrong exercise.
        //
        // WAS DATA-MODELING-ONLY, and doubly blind, until 2026-08-24:
        //   1. It named one track, so Java & Groovy and Python were never checked.
        //   2. It looked only for `NN-slug.mdx` and returned early otherwise — and every file
        //      in those two tracks is a `_todo-` brief, so it would have been a no-op even
        //      after being pointed at them.
        // Both mattered at once: resync-lessons.js was skipping the same 72 files (its regex
        // only matched Data Modeling's header), so nothing anywhere would have noticed the
        // drift. The tool is fixed; this is what keeps it fixed.
        given:
        def dir = new File(COURSES, track)
        def doc = load(dir)
        def bad = []
        doc.series.each { s ->
            s.episodes.each { ep ->
                // PUBLISHED episodes are exempt, and it is not a loophole. This block is a
                // brief: it tells whoever writes the episode which exercise to build. Once the
                // lesson is written the brief is deleted along with the rest of the TODO
                // scaffolding, and the teaching material is what a reader gets. Requiring a
                // published lesson to still carry author instructions would mean shipping them.
                if (ep.status == "published") return
                def mdx = lessonFile(dir, s, ep)
                if (!mdx) return
                def text = mdx.text
                def absent = ep.hands_on.findAll { !text.contains(it) }
                if (absent) bad << "${ep.slug} (${mdx.name}): .mdx never mentions ${absent}"
            }
        }

        expect:
        bad.isEmpty()

        where:
        track << HANDS_ON_VOCAB.keySet()
    }

    /** The shared bibliography: courses/sources.yaml, keyed by the same string as the tag. */
    static Map sourceRegistry() {
        def opts = new org.yaml.snakeyaml.LoaderOptions()
        opts.allowDuplicateKeys = false
        def doc = new Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(opts))
                .load(new File(COURSES, "sources.yaml").text) as Map
        return doc.sources as Map
    }

    @Unroll
    def "#track: every credit resolves, and the badge matches the bibliography"() {
        // WHY THIS IS A FIELD AND NOT A COMMENT. Attribution lived in a `# SOURCE (kimball):`
        // comment, and a comment enforces nothing — somebody adds a Kimball episode, forgets
        // the line, and the credit is silently missing. That is the same honour system that
        // has already failed three times in this repo.
        //
        // THREE RULES, all of them things nobody should have to remember:
        //   1. every credits[].source names a real entry in courses/sources.yaml, so a typo
        //      cannot produce an episode credited to nobody
        //   2. credits and the author TAG agree in BOTH directions. The tag is what a learner
        //      sees on the course page; the credit is what the article's Sources block is
        //      built from. If they drift, a reader sees "#tufte" on a page that never cites
        //      him, or the bibliography credits somebody the page never mentions
        //   3. `concept` is non-empty — "credited to Kimball" without saying WHAT is useless
        //      to whoever eventually writes the episode
        //
        // NOT a rule: that an episode HAS credits. Most must not have any — 333 of 382 teach
        // textbook material with no owner, and attributing those would be both wrong and would
        // dilute the credits that are real.
        given:
        def registry = sourceRegistry()
        def doc = load(new File(COURSES, track))
        def bad = []

        episodesOf(doc).each { ep ->
            def credits = (ep.credits ?: []) as List
            def tags = (ep.tags ?: []) as List
            def cited = credits.collect { it.source } as Set

            credits.each { c ->
                if (!c.source) { bad << "${ep.slug}: a credit with no source"; return }
                if (!registry.containsKey(c.source)) {
                    bad << "${ep.slug}: credits '${c.source}', which is not in courses/sources.yaml"
                }
                if (!c.concept?.toString()?.trim()) {
                    bad << "${ep.slug}: credits '${c.source}' but does not say WHAT is credited"
                }
            }
            (cited - tags.toSet()).each {
                bad << "${ep.slug}: credits '${it}' but has no \"${it}\" tag — the badge would be missing"
            }
            tags.findAll { registry.containsKey(it) }.each {
                if (!(it in cited)) {
                    bad << "${ep.slug}: tagged '${it}' but has no matching credit — the Sources block would be empty"
                }
            }
        }

        expect:
        bad.isEmpty()

        where:
        track << trackDirs()*.name
    }

    def "every source in the registry is actually used by someone"() {
        // A registry entry nobody cites is either a leftover or an episode that lost its
        // credit in an edit. Both are worth knowing about.
        given:
        def registry = sourceRegistry()
        def used = trackDirs().collectMany { d ->
            episodesOf(load(d)).collectMany { (it.credits ?: []).collect { c -> c.source } }
        } as Set

        expect:
        (registry.keySet() - used).isEmpty()
    }

    @Unroll
    def "#track: a pointer never leaves the reader guessing which course a number belongs to"() {
        // THE RULE: name the course for EVERY reference. Ours is "In this course, Series N ·
        // MM"; anyone else's is "<Course>, Series N · MM". Never let the word "here" carry it.
        //
        // WHY THIS EXISTS. The rule above proves a reference RESOLVES. It cannot prove a
        // person can follow it, and for a while they could not:
        //
        //     "covered in Data Modeling, Series 3 — Series 3 · 05 here LOADS a star …"
        //
        // Two courses' Series 3 in one sentence, disambiguated only by "here". Every one of
        // those resolved correctly and every one was unreadable — and this text is not for a
        // parser, it renders verbatim on the course page under "Not here, on purpose".
        //
        // It also hid a real wrong-pointer bug that the resolve rule passed green. Learn SQL's
        // ROLLUP exclude named "Learn Data Warehousing" and then, after an em-dash, gave two
        // BARE numbers — so they pointed at Learn SQL's own GROUP BY and Dates & Times instead
        // of Data Warehousing's ROLLUP and OLAP Cubes. Both existed, so nothing complained.
        //
        // THE SIGNATURE, and it is exact: a bare "Series N · MM" in a string that also names
        // another course. Either the number belongs to that course and should say so, or it
        // belongs to this one and should say "In this course".
        given:
        def doc = load(new File(COURSES, track))
        def titles = trackDirs().collect { load(it).title }
        def others = (titles + titles.collect { "Learn " + it })
                .findAll { it != doc.title && it != "Learn " + doc.title }
                .unique().sort { -it.length() }
        def bad = []

        (doc.excludes ?: []).each { x ->
            def w = x.where as String
            if (!w) return
            def namesOther = others.any { w.contains(it) }
            if (!namesOther) return
            // strip every "<Course>, Series N · MM" — what is left are the bare ones
            def stripped = w
            others.each { c ->
                stripped = stripped.replaceAll(
                        java.util.regex.Pattern.quote(c) + /,?\s+Series \d+( · \d+)?/, "")
            }
            if (stripped =~ /Series \d+ · \d+/ && !stripped.contains("In this course")) {
                bad << "\"${x.topic}\": bare 'Series N · MM' beside another course's name — " +
                       "say \"In this course, …\" or name the course"
            }
        }

        expect:
        bad.isEmpty()

        where:
        track << trackDirs()*.name
    }

    /**
     * Tracks whose koan briefs carry the episode TITLE on its own line under the box header.
     * Data Modeling is deliberately absent: its briefs put the episode NAME inside the box
     * and never carried the full title, so there is nothing there to drift.
     */
    static final List<String> KOAN_TITLE_TRACKS =
            ["learnsql", "javagroovy", "python",
             "etlpipelines", "dbt", "datawarehousing", "dataops", "bi", "ai"]

    @Unroll
    def "#track: each koan brief carries its episode's CURRENT title"() {
        // THE THIRD PLACE A TITLE IS SNAPSHOT, and the one nothing was watching. The .mdx is
        // repaired by resync-lessons.js and guarded by the test above; the koan brief is
        // repaired by neither and was guarded by nothing.
        //
        // The cost was real and it sat there for months. When Learn SQL's titles were rewritten
        // to drop the rhetorical ones, NINETEEN koan briefs kept the originals — "Who's Your #1
        // Customer?", "Why Your Revenue Just Tripled", "Glue Two Tables Together". They are the
        // exact titles that were removed on purpose, and they survived in the file a koan author
        // opens FIRST. Found on 2026-08-24 by a rename that had nothing to do with titles, which
        // is the definition of a check that should have existed.
        //
        // Deliberately a CONTAINS check, not an equality check: a brief is free to say more, and
        // the failure this catches is a title that is stale, not one that is decorated.
        given:
        def doc = load(new File(COURSES, track))
        def bad = []
        doc.series.each { s ->
            def n = (s.slug =~ /^series(\d+)/)[0][1]
            s.episodes.each { ep ->
                def d = new File(KOAN_TODO, "${track}/series${n}/_${ep.n}")
                if (!d.isDirectory()) return
                d.listFiles()?.findAll { it.isFile() }?.each { f ->
                    if (!f.text.contains(ep.title)) {
                        bad << "${f.name}: does not carry the current title \"${ep.title}\""
                    }
                }
            }
        }

        expect:
        bad.isEmpty()

        where:
        track << KOAN_TITLE_TRACKS
    }

    /** Briefs for koans nobody has written yet. Not compiled, not mounted, not run. */
    static final File KOAN_TODO = new File("src/koans/_todo")

    /** A lesson bundle is either written (`NN-slug.mdx`) or a brief (`_todo-NN-slug.mdx`). */
    static File lessonFile(File dir, s, ep) {
        def base = "${s.slug}/${ep.n}-${ep.slug}"
        [new File(dir, "${base}/${ep.n}-${ep.slug}.mdx"),
         new File(dir, "${base}/_todo-${ep.n}-${ep.slug}.mdx")].find { it.exists() }
    }
}
