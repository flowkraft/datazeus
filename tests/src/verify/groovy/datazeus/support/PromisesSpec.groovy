package datazeus.support

import org.yaml.snakeyaml.Yaml
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  THE PROMISE GATE — sentences that lean on another series or course       ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * The rule (CONTRIBUTING.md, "Promises between lessons"): inside a series, lessons build on
 * each other and keep what they promise. Across series and courses the link is the data,
 * never a promise.
 *
 * CurriculumSpec catches the promises a regex can prove: an episode NUMBER of another course,
 * a link to a lesson that isn't live. The dangerous ones carry no number — "Series 3 finds out
 * what happened", "your own model from the other course", "a later lesson covers contacts" —
 * and telling those from an innocent sentence takes a person. So this gate is a heuristic
 * that FAILS, and a person clears it, one sentence at a time, in _datazeus/promises.yaml:
 *
 *   not_a_promise   "I read it; it points elsewhere but promises nothing." Needs a `why`.
 *   kept            A hand-over to a later lesson of the same series, and the lesson that
 *                   keeps it: `kept_by: "1 · 45"`, `about: "NULL"` (or a list, every word
 *                   needed). The gate then CHECKS it — the lesson exists, comes later, is in this
 *                   series and mentions `about` (in its lesson file, or in its brief while there
 *                   is none). That check is what would have caught Data Modeling 1 · 05 promising
 *                   contacts that 10, 15 and 20 never cover. A `kept` entry may also record a
 *                   promise no heuristic flags, found by reading ("we'll apply it, step by step,
 *                   to solve it"): leave out `rule:`, and it is checked the same way.
 *
 * Every `kept` promise goes into the ledger, target/promises-ledger-<track>.txt, printed on each
 * run: where it is made, which lesson keeps it, and whether it is CLOSED (that lesson is live)
 * or OPEN (written in a draft or a brief, not live yet). An OPEN promise is listed, not failed:
 * a live lesson may promise the next one before it ships. A promise whose keeper does not say
 * it fails, live or not.
 *   covered         Names what ANOTHER series or course teaches ("query speed is covered in
 *                   Series 3"). Safe only if that series certainly covers it, so the gate checks
 *                   its plan: `series: 3` (+ `course:` if another course) and `about: [EXPLAIN]`,
 *                   words the series' part of curriculum.yaml must contain.
 *   unreviewed      Judged NOT safe on 2026-09-19 and still to be reworded. Passes for now.
 *
 * The only ways to make a red run green are to reword the sentence or to add it to one of
 * those lists. An entry that no longer matches any sentence fails too, so the lists never
 * outlive what they excuse.
 *
 * When it fails it prints each sentence with file and line, and writes ready-to-paste entries
 * to target/promises-uncovered-<track>-<cross|forward>.yaml.
 *
 * WHAT IT READS, per track listed in promises.yaml `tracks:` — the five data courses to start
 * with; add a course when its lessons start being written:
 *   - curriculum.yaml: each series' tagline, each episode's title, short and data.reason
 *     (excludes and prerequisites are CurriculumSpec's)
 *   - every written lesson, drafts included. Code blocks, comments and tags are skipped.
 *   - NOT `_todo-` briefs. They are full of cross-references on purpose ("BUILDS ON: Series 1 ·
 *     40") — that is how an author plans. Nothing in a brief reaches a learner until the brief
 *     becomes a lesson file, and from that moment this gate reads it. (Tried both ways on
 *     2026-09-19: briefs added 164 hits, nearly all planning notes.)
 */
class PromisesSpec extends Specification {

    static final File CONFIG = new File("../promises.yaml")
    static final File DUMP_DIR = new File("target")

    static final Map<String, Integer> NUMBER_WORDS =
            [one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7]

    // ── the heuristics ─────────────────────────────────────────────────────────────────────
    // Each is one rule name, one question. They are deliberately a little eager: a false hit
    // costs one line in promises.yaml; a missed promise costs a lesson rewritten after launch.

    /** "Series 2" in a Series 3 lesson: leaning on another series, backwards or forwards. */
    static final String OTHER_SERIES = "other-series"
    /** "Data Ops", "the DuckDB course": naming another course. */
    static final String OTHER_COURSE = "other-course"
    /** "the next series", "you built it in another course": a cross-series promise with no name. */
    static final String CROSS_PHRASE = "cross-phrase"
    /** A link to a lesson of another series or course. */
    static final String CROSS_LINK = "cross-link"
    /** "a lesson of its own later in this series": a promise the series must keep. */
    static final String FORWARD = "forward"

    static final Pattern SERIES_REF =
            ~/\bSeries\s+(\d+|one|two|three|four|five|six|seven)\b/

    static final Pattern CROSS_PHRASES = Pattern.compile(
            /\b(?:next|previous|last|later|earlier|another|other|following|coming|future|prior) (?:series|course|track)\b/ +
            /|\b(?:you|we) (?:met|built|made|wrote|saw|learned|learnt|loaded|landed|set up|designed|modell?ed) (?:\S+ ){0,3}in (?:another|the other|a previous|an earlier|a different) (?:series|course)\b/ +
            /|\byour own \S+(?: \S+)? from (?:another|the other|a previous|an earlier|the last) (?:series|course)\b/ +
            /|\belsewhere in the academy\b/,
            Pattern.CASE_INSENSITIVE)

    static final Pattern FORWARD_PHRASES = Pattern.compile(
            /\bnext (?:lesson|episode)\b/ +
            /|\b(?:a|the) later (?:lesson|episode)\b|\bin a later (?:lesson|episode)\b/ +
            /|\blater (?:on )?in (?:this|the) series\b/ +
            /|\b(?:a|an) (?:lesson|episode) of its own\b|\bits own (?:lesson|episode)\b/ +
            /|\b(?:we|you)(?:'ll| will) come back to\b|\bcomes back to (?:it|this|that)\b/ +
            /|\bthe (?:lessons|episodes) ahead\b/,
            Pattern.CASE_INSENSITIVE)

    static final Pattern ACADEMY_LINK = ~/\/academy\/([a-z0-9-]+)\/([a-z0-9-]+)/

    // ── the text it reads ──────────────────────────────────────────────────────────────────

    /** One flagged sentence. */
    static class Hit {
        String track, file, where, rule, detail, sentence
        int line
        String getLocation() { line > 0 ? "${file}:${line}" : "${file} (${where})" }
    }

    /** A piece of prose, and where it sits. */
    static class Unit {
        File dir; Map doc; Map series; Map ep
        int seriesNo
        String file, where, kind   // kind: yaml | lesson
        boolean live
        String raw                 // as written
        String prose               // same length; code, comments and tags blanked
        int lineOffset             // lines before `raw` in the file; -1 for yaml values
    }

    static Map config() {
        CONFIG.withReader("UTF-8") { new Yaml().load(it) } as Map
    }

    static String norm(String s) { (s ?: "").replaceAll(/\s+/, " ").trim() }

    /** Replace each match with spaces, keeping newlines, so offsets and line numbers survive. */
    static String blank(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text)
        def sb = new StringBuilder()
        int last = 0
        while (m.find()) {
            sb.append(text, last, m.start()).append(m.group().replaceAll(/[^\n]/, " "))
            last = m.end()
        }
        sb.append(text.substring(last)).toString()
    }

    /** What a learner reads: code, comments (author notes) and tags blanked out. */
    static String prose(String raw) {
        def t = blank(raw, /(?s)```.*?```/)
        t = blank(t, /(?s)\{\/\*.*?\*\/\}/)
        t = blank(t, /(?s)<!--.*?-->/)
        t = blank(t, /<[^>\n]*>/)
        blank(t, /(?<=\])\([^)\n]*\)/)          // a markdown link's URL; its text stays
    }

    static int seriesNo(Map s) { ((s.slug =~ /^series(\d+)/)[0][1] as String).toInteger() }

    static List<Unit> units(File dir) {
        def doc = CurriculumSpec.load(dir)
        def out = []
        doc.series.each { s ->
            int no = seriesNo(s)
            def yamlUnit = { where, value, ep ->
                if (!(value instanceof String) || !value) return
                out << new Unit(dir: dir, doc: doc, series: s, ep: ep, seriesNo: no, kind: "yaml",
                        file: "${dir.name}/curriculum.yaml", where: where,
                        raw: value, prose: value, lineOffset: -1)
            }
            yamlUnit("Series ${no} tagline", s.tagline, null)
            s.episodes.each { ep ->
                yamlUnit("${no} · ${ep.n} title", ep.title, ep)
                yamlUnit("${no} · ${ep.n} short", ep.short, ep)
                yamlUnit("${no} · ${ep.n} data.reason", (ep.data instanceof Map) ? ep.data.reason : null, ep)

                def folder = "${s.slug}/${ep.n}-${ep.slug}"
                def f = new File(dir, "${folder}/${ep.n}-${ep.slug}.mdx")   // written lessons only; see the header
                if (!f.exists()) return
                def text = f.getText("UTF-8").replace("\r\n", "\n")
                def fm = (text =~ /(?m)^---[ \t]*$/)
                int bodyAt = 0
                if (fm.find() && fm.start() == 0 && fm.find()) bodyAt = fm.end()
                def body = text.substring(bodyAt)
                out << new Unit(dir: dir, doc: doc, series: s, ep: ep, seriesNo: no, kind: "lesson",
                        file: "${dir.name}/${folder}/${f.name}", where: f.name,
                        live: CurriculumSpec.isLive(dir, s, ep),
                        raw: body, prose: prose(body), lineOffset: text.substring(0, bodyAt).count("\n"))
            }
        }
        out
    }

    /** Every other course's title, with the pattern that counts as naming it. */
    static Map<String, Pattern> otherCourses(String ownTitle) {
        def all = CurriculumSpec.trackDirs().collect { CurriculumSpec.load(it).title as String }
        all.findAll { it != ownTitle }.sort { -it.length() }.collectEntries { String t ->
            def q = Pattern.quote(t)
            // A course named after a product ("DuckDB", "PostgreSQL") is that product in almost
            // every sentence; it counts as the COURSE only when the sentence says so.
            boolean generic = t =~ /\b(?:Data|Learn|Concepts|Patterns|Teardowns|Delivery|Build Your Own|Formats)\b| SQL$/
            def p = generic ? q : "(?:the |an? )?${q} (?:course|track)|course (?:on|about) ${q}|Learn ${q}"
            [(t): Pattern.compile("(?<![\\w-])(?:${p})(?![\\w-])")]
        }
    }

    // ── scanning ───────────────────────────────────────────────────────────────────────────

    @Shared Map<String, List<Hit>> hitCache = [:]

    List<Hit> hits(String track) {
        hitCache.computeIfAbsent(track) { scan(new File(CurriculumSpec.COURSES, track)) }
    }

    @Shared Map<String, List<Hit>> sentenceCache = [:]

    /** Every sentence of every written lesson, as a Hit with no rule: what a `kept` entry is matched against. */
    List<Hit> sentences(String track) {
        sentenceCache.computeIfAbsent(track) {
            units(new File(CurriculumSpec.COURSES, track)).findAll { it.kind == "lesson" }.collectMany { u ->
                sentencesOf(u).collect { seg ->
                    new Hit(track: track, file: u.file, where: u.where, sentence: seg[0], line: seg[1] as int)
                }
            }
        }
    }

    /** A unit's prose cut into sentences: [sentence, line, start, end] each, empty ones left out. */
    static List<List> sentencesOf(Unit u) {
        def boundary = (u.prose =~ /(?<=[.!?])\s+|\n[ \t]*\n/)
        int start = 0
        def segments = []
        while (boundary.find()) { segments << [start, boundary.start()]; start = boundary.end() }
        segments << [start, u.prose.length()]
        segments.collect { seg ->
            int a = seg[0], b = seg[1]
            int line = u.lineOffset < 0 ? 0 :
                    u.lineOffset + u.raw.substring(0, a).count("\n") + 1 +
                    (u.prose.substring(a, b) =~ /^\s*/)[0].count("\n")
            [norm(u.prose.substring(a, b)), line, a, b]
        }.findAll { it[0] }
    }

    static List<Hit> scan(File dir) {
        def units = units(dir)
        if (!units) return []
        def doc = units[0].doc
        def courses = otherCourses(doc.title as String)
        def byCourse = CurriculumSpec.trackDirs().collectEntries { [(CurriculumSpec.load(it).course): it] }
        def out = []

        units.each { u ->
            sentencesOf(u).each { seg ->
                String s = seg[0]
                int line = seg[1], a = seg[2], b = seg[3]
                String rawSeg = u.raw.substring(a, b)
                def hit = { String rule, String detail ->
                    if (!out.any { it.file == u.file && it.rule == rule && it.sentence == s && it.where == u.where })
                        out << new Hit(track: dir.name, file: u.file, where: u.where, rule: rule,
                                detail: detail, sentence: s, line: line)
                }

                String namedCourse = courses.find { t, p -> p.matcher(s).find() }?.key
                if (namedCourse) hit(OTHER_COURSE, "names ${namedCourse}")

                def sm = SERIES_REF.matcher(s)
                while (sm.find()) {
                    def v = sm.group(1)
                    int n = v.isInteger() ? v.toInteger() : NUMBER_WORDS[v.toLowerCase()]
                    if (n == u.seriesNo) continue
                    def before = s.substring(0, sm.start()).replaceAll(/[,\s]+$/, "")
                    if (courses.keySet().any { before.endsWith(it) }) continue   // another course's series: other-course
                    hit(OTHER_SERIES, n > u.seriesNo
                            ? "promises Series ${n}; this is Series ${u.seriesNo}"
                            : "leans on Series ${n}; this is Series ${u.seriesNo} — restate what it needs instead")
                    break
                }

                def pm = CROSS_PHRASES.matcher(s)
                if (pm.find()) hit(CROSS_PHRASE, "\"${pm.group()}\"")

                if (u.kind == "lesson") {
                    def fm = FORWARD_PHRASES.matcher(s)
                    if (fm.find()) hit(FORWARD, "\"${fm.group()}\" — which lesson keeps it?")
                }

                if (u.kind != "yaml") {
                    def lm = ACADEMY_LINK.matcher(rawSeg)
                    while (lm.find()) {
                        def targetDir = byCourse[lm.group(1)] as File
                        if (!targetDir) continue
                        def target = CurriculumSpec.load(targetDir)
                        if (target.course != doc.course) {
                            hit(CROSS_LINK, "links to ${target.title}"); break
                        }
                        def ts = target.series.find { x -> x.episodes.any { it.slug == lm.group(2) } }
                        if (ts && seriesNo(ts) != u.seriesNo) {
                            hit(CROSS_LINK, "links to Series ${seriesNo(ts)}"); break
                        }
                    }
                }
            }
        }
        out
    }

    // ── matching hits against promises.yaml ────────────────────────────────────────────────

    /** The entry quotes this sentence of this file. */
    static boolean quotes(Map entry, Hit h) {
        entry.file == h.file && entry.quote && h.sentence.contains(norm(entry.quote as String))
    }

    static boolean covers(Map entry, Hit h) {
        quotes(entry, h) && (!entry.rule || entry.rule == h.rule)
    }

    static List<Map> entries(String section) { (config()[section] ?: []) as List<Map> }

    static List<String> abouts(Map entry) {
        ((entry.about instanceof List) ? entry.about : [entry.about]).findAll { it }*.toString()
    }

    /**
     * A `kept` entry checked against the curriculum. [problem: what's wrong] when it doesn't hold;
     * otherwise where it is made and where it is kept, for the ledger.
     */
    static Map keptCheck(Map entry, Hit h) {
        def dir = new File(CurriculumSpec.COURSES, h.track)
        def unit = units(dir).find { it.file == h.file }
        def m = (entry.kept_by as String ?: "") =~ /^\s*(\d+)\s*·\s*(\d+)\s*$/
        if (!m) return [problem: "kept_by must look like \"1 · 45\", got '${entry.kept_by}'"]
        int sn = (m[0][1] as String).toInteger()
        def epN = m[0][2] as String
        if (sn != unit.seriesNo)
            return [problem: "kept_by ${entry.kept_by} is in another series: a promise across series can't be kept, reword it"]
        def ep = unit.series.episodes.find { it.n.toString().toInteger() == epN.toInteger() }
        if (!ep) return [problem: "kept_by ${entry.kept_by}: no such episode in Series ${sn}"]
        if (ep.n.toString().toInteger() <= unit.ep.n.toString().toInteger())
            return [problem: "kept_by ${entry.kept_by} comes before this lesson, not after it"]
        def words = abouts(entry)
        if (!words) return [problem: "needs `about:` — words the keeping lesson must mention"]
        def folder = "${unit.series.slug}/${ep.n}-${ep.slug}"
        def keeper = [new File(dir, "${folder}/${ep.n}-${ep.slug}.mdx"),
                      new File(dir, "${folder}/_todo-${ep.n}-${ep.slug}.mdx")].find { it.exists() }
        if (!keeper) return [problem: "kept_by ${entry.kept_by} has no lesson file yet"]
        def text = keeper.getText("UTF-8").toLowerCase()
        def missing = words.findAll { !text.contains(it.toLowerCase()) }
        if (missing) return [problem: "kept_by ${entry.kept_by} never mentions ${missing.collect { "'${it}'" }.join(', ')} (${keeper.name})"]
        boolean keeperLive = CurriculumSpec.isLive(dir, unit.series, ep)
        [made: "${unit.seriesNo} · ${unit.ep.n}", madeLive: unit.live, keeperLive: keeperLive,
         keeper: keeper.name.startsWith("_todo-") ? "brief" : keeperLive ? "live" : "draft"]
    }

    /** The ledger: every `kept` promise of a track, where it is made and where it is kept. */
    static String ledger(String track, List<Map> rows) {
        def lines = rows.sort { a, b -> a.h.file <=> b.h.file ?: a.h.line <=> b.h.line }.collect { r ->
            def c = r.c
            String status = c.keeperLive ? "CLOSED" : "OPEN"
            String found = r.e.rule ? "" : "  (found by reading)"
            "  ${status.padRight(7)}${c.made}:${r.h.line} (${c.madeLive ? 'live' : 'draft'})  →  ${r.e.kept_by} (${c.keeper})" +
                    "  about ${abouts(r.e).collect { "'${it}'" }.join(', ')}${found}\n" +
                    "         \"${r.h.sentence.take(160)}${r.h.sentence.length() > 160 ? '…' : ''}\""
        }
        int closed = rows.count { it.c.keeperLive } as int
        int waiting = rows.count { !it.c.keeperLive && it.c.madeLive } as int
        def text = "${track}: ${rows.size()} promises to a later lesson — ${closed} closed, ${rows.size() - closed} open" +
                (waiting ? " (${waiting} made in a live lesson: learners are waiting on these)" : "") + "\n" +
                "CLOSED: the keeping lesson is live and says it. OPEN: its draft or brief says it; not live yet.\n\n" +
                lines.join("\n") + "\n"
        DUMP_DIR.mkdirs()
        new File(DUMP_DIR, "promises-ledger-${track}.txt").setText(text, "UTF-8")
        text
    }

    /**
     * A `covered` entry checked against the curriculum: null when it holds, else what's wrong.
     * Naming what another series teaches is safe only when that series' plan really says so.
     */
    static String coveredProblem(Map entry, Hit h) {
        def course = (entry.course ?: h.track) as String
        def dir = new File(CurriculumSpec.COURSES, course)
        if (!new File(dir, "curriculum.yaml").exists()) return "course: no course '${course}'"
        if (!(entry.series?.toString() ==~ /\d+/)) return "series must be a number, got '${entry.series}'"
        def doc = CurriculumSpec.load(dir)
        int sn = entry.series.toString().toInteger()
        def s = doc.series.find { seriesNo(it) == sn }
        if (!s) return "${doc.title} has no Series ${sn}"
        def abouts = ((entry.about instanceof List) ? entry.about : [entry.about]).findAll { it } as List
        if (!abouts) return "needs `about:` — what that series must teach"
        def plan = new Yaml().dump(s).toLowerCase()
        def missing = abouts.findAll { !plan.contains(it.toString().toLowerCase()) }
        if (missing) return "${doc.title}, Series ${sn} never mentions ${missing.collect { "'${it}'" }.join(', ')}: " +
                "nothing in its plan keeps this yet. Add it to that series' plan, or reword"
        null
    }

    static String suggestion(Hit h) {
        def words = h.sentence.split(" ")
        def q = words.take(12).join(" ")
        def yq = "'" + q.replace("'", "''") + "'"
        def base = "  - file: ${h.file}\n    rule: ${h.rule}\n    quote: ${yq}\n"
        h.rule == FORWARD
                ? base + "    kept_by: \"S · EP\"      # the later lesson in this series that keeps it\n    about: [\"\"]             # words that lesson must mention\n"
                : base + "    why: \"\"                 # why this is not a promise\n"
    }

    static String report(String track, String kind, List<Hit> open, String intro) {
        if (!open) return ""
        DUMP_DIR.mkdirs()
        new File(DUMP_DIR, "promises-uncovered-${track}-${kind}.yaml").setText(open.collect { suggestion(it) }.join("\n"), "UTF-8")
        "\n${intro}\n\n" + open.collect { h ->
            "  ${h.location}  [${h.rule}: ${h.detail}]\n      \"${h.sentence.take(220)}${h.sentence.length() > 220 ? '…' : ''}\""
        }.join("\n") +
                "\n\nFor each one: reword it so it stands on its own, or clear it in _datazeus/promises.yaml." +
                "\nReady-to-paste entries: tests/target/promises-uncovered-${track}-${kind}.yaml\n"
    }

    // ── the rules ──────────────────────────────────────────────────────────────────────────

    @Unroll
    def "#track: nothing leans on another series or course unless promises.yaml clears it"() {
        given:
        def problems = []
        def open = hits(track).findAll { it.rule != FORWARD }.findAll { h ->
            def cov = entries("covered").find { covers(it, h) }
            if (cov) {
                def p = coveredProblem(cov, h)
                if (p) problems << "  ${h.location}: ${p}"
                return false
            }
            !(entries("not_a_promise") + entries("unreviewed")).any { covers(it, h) }
        }
        def msg = report(track, "cross", open,
                "These sentences point at another series or course. Across series and courses the link is the data, never a promise.") +
                (problems ? "\ncovered: entries that don't hold:\n" + problems.join("\n") + "\n" : "")

        expect:
        assert open.isEmpty() && problems.isEmpty() : msg

        where:
        track << (config().tracks as List)
    }

    @Unroll
    def "#track: every hand-over to a later lesson names the lesson that keeps it"() {
        given:
        def kept = entries("kept").findAll { (it.file as String)?.startsWith("${track}/") }
        def problems = []
        def rows = []
        kept.each { e ->
            def h = sentences(track).find { quotes(e, it) }
            if (!h) return                                   // the promises.yaml rule reports it
            def c = keptCheck(e, h)
            if (c.problem) problems << "  ${h.location}: ${c.problem}"
            else rows << [e: e, h: h, c: c]
        }
        def open = hits(track).findAll { it.rule == FORWARD }.findAll { h ->
            !kept.any { quotes(it, h) } && !(entries("not_a_promise") + entries("unreviewed")).any { covers(it, h) }
        }
        if (rows) println("\n" + ledger(track, rows))
        def msg = report(track, "forward", open,
                "These sentences promise a later lesson. Say which one keeps it (`kept:` in promises.yaml), and the gate checks it does.") +
                (problems ? "\nkept: entries that don't hold:\n" + problems.join("\n") + "\n" : "")

        expect:
        assert open.isEmpty() && problems.isEmpty() : msg

        where:
        track << (config().tracks as List)
    }

    def "promises.yaml: every entry still matches a sentence, and says why"() {
        given:
        def tracks = config().tracks as List
        def all = tracks.collectMany { hits(it) }
        def said = tracks.collectMany { sentences(it) }
        def bad = []
        ["not_a_promise", "kept", "covered", "unreviewed"].each { section ->
            entries(section).each { e ->
                def label = "${section}: ${e.file} '${e.quote}'"
                if (!e.file || !e.quote) { bad << "${label}: needs file and quote"; return }
                if (!tracks.any { (e.file as String).startsWith("${it}/") }) {
                    bad << "${label}: its course isn't in `tracks:`"; return
                }
                if (e.rule && !(e.rule in [OTHER_SERIES, OTHER_COURSE, CROSS_PHRASE, CROSS_LINK, FORWARD]))
                    bad << "${label}: unknown rule '${e.rule}'"
                if (section == "not_a_promise" && !norm(e.why as String)) bad << "${label}: needs a `why`"
                if (section == "kept" && (!e.kept_by || !e.about)) bad << "${label}: needs `kept_by` and `about`"
                if (section == "covered" && (!e.series || !e.about)) bad << "${label}: needs `series` and `about`"
                boolean matches = section == "kept" ? said.any { quotes(e, it) } : all.any { covers(e, it) }
                if (!matches)
                    bad << "${label}: matches nothing any more — the sentence changed or went; remove the entry"
            }
        }

        expect:
        assert bad.isEmpty() : "\n" + bad.join("\n")
    }
}
