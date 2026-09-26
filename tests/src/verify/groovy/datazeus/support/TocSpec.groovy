package datazeus.support

import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Unroll

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  THE TABLE-OF-CONTENTS GATE — every COPY of a curriculum's episode list   ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * curriculum.yaml says it in its own first lines: anything that has to know "what are the
 * episodes, in what order, and where are we" reads THAT file. Two consumers read it live and
 * cannot drift — the course page and the lesson folder names, both guarded by CurriculumSpec.
 * The rest are COPIES, typed by hand, and on 2026-09-21 every one of them was wrong:
 *
 *   1. The ramp table inside Learn SQL's own Series 2 comment block listed SIXTEEN episodes.
 *      Twelve lines below it the same file said "it appears in 8 of 17 episodes". One file,
 *      two counts, and that file is the source of truth for both.
 *   2. The `SERIES2_TOC` roadmap card in all five Series 2 videos was wrong three ways at once:
 *      56 UPSERT & MERGE was missing entirely, 22 still read "VIEWs — name a query, reuse it",
 *      and 55 still carried UPSERT in its title. Every episode of the series would have shown a
 *      learner a sixteen-part roadmap for a seventeen-part series.
 *   3. All TWELVE published Series 1 videos still showed "COUNT, SUM, AVG, MIN, MAX — many rows,
 *      one number" for episode 25. The curriculum renamed that `short` to "Aggregate Functions —
 *      many rows, one number" on 2026-09-02 and nothing propagated it.
 *   4. courses/learnsql/README.md said "Three series" for a course that has four, and put CTEs
 *      and window functions in Series 3 when they are Series 2 · 20, 35 and 45.
 *
 * That is not four typos. It is one failure four times: a list somebody has to remember to
 * update, with nothing checking that they did. CurriculumSpec's 740 tests never looked at any of
 * them — which is precisely why these were the stale ones.
 *
 * WHAT THIS FILE ASSERTS, in a sentence: a copy of the episode list may exist, but it must be
 * identical to curriculum.yaml and to the other copies.
 *
 * THE VIDEOS LIVE OUTSIDE THIS SUBMODULE — flowkraft/www/cli-remotion/, four levels up from
 * tests/. Those features are @Requires'd on that folder being there, so the submodule still runs
 * green when it is checked out on its own; the curriculum-internal and README rules always run.
 */
class TocSpec extends Specification {

    /** cli-remotion sits beside reportburster.com, OUTSIDE the _datazeus submodule. */
    static final File VIDEOS = new File("../../../../cli-remotion/src/videos/rb")

    /** `<track>-series<N>-<nn>-<slug>`, the Remotion composition folder for one episode. */
    static final java.util.regex.Pattern VIDEO_DIR = ~/^([a-z0-9]+)-series(\d+)-(\d+)-(.+)$/

    // ══════════════════════════════════════════════════════════════════════
    //  Reading the copies
    // ══════════════════════════════════════════════════════════════════════

    static List<File> videoDirs() {
        VIDEOS.listFiles()?.findAll {
            it.directory && VIDEO_DIR.matcher(it.name).matches() &&
                    !it.name.contains("-short-") && new File(it, "index.tsx").exists()
        }?.sort { it.name } ?: []
    }

    static List<String> videoTracks() {
        videoDirs().collect { VIDEO_DIR.matcher(it.name)[0][1] as String }.unique().sort()
    }

    static List<File> videosOf(String track) {
        videoDirs().findAll { it.name.startsWith(track + "-series") }
    }

    /**
     * The roadmap card's episode list, however this composition happens to hold it: a shared
     * `const SERIES<N>_TOC = [...]` (Series 2's five videos) or the array written inline in the
     * `toc:` object (Series 1's twelve). Returns null when the composition carries no roadmap
     * card at all — not every video does, and that is not this spec's business.
     */
    static List<String> roadmapOf(File dir) {
        String t = new File(dir, "index.tsx").getText("UTF-8")
        def m = (t =~ /(?s)const SERIES\d+_TOC\s*=\s*\[(.*?)\n\];/)
        String body = m.find() ? m.group(1) : null
        if (body == null) {
            m = (t =~ /(?s)\n[ ]*episodes: \[(.*?)\n[ ]*\],/)
            body = m.find() ? m.group(1) : null
        }
        if (body == null) return null
        body.readLines()*.trim().findAll { it.startsWith('"') }
                .collect { it.substring(1, it.lastIndexOf('"')) }
    }

    static String fieldOf(File dir, String name) {
        def m = (new File(dir, "index.tsx").getText("UTF-8") =~ /\n[ ]*${name}: "(.*?)",/)
        m.find() ? m.group(1) : null
    }

    static Integer currentOf(File dir) {
        def m = (new File(dir, "index.tsx").getText("UTF-8") =~ /\n[ ]*current: (\d+),/)
        m.find() ? m.group(1) as Integer : null
    }

    /** The series a composition folder belongs to, or null if the curriculum has no such series. */
    static Map seriesOf(Map doc, String seriesNo) {
        doc.series.find { (it.slug as String).startsWith("series" + seriesNo + "-") } as Map
    }

    static String quoted(List<String> list, int i) {
        i < list.size() ? '"' + list[i] + '"' : "(nothing)"
    }

    // ══════════════════════════════════════════════════════════════════════
    //  The videos
    // ══════════════════════════════════════════════════════════════════════

    @Unroll
    @Requires({ TocSpec.VIDEOS.directory && !TocSpec.videoDirs().isEmpty() })
    def "#track: every video's roadmap card is the curriculum's episode list, in order"() {
        given:
        def doc = CurriculumSpec.load(new File(CurriculumSpec.COURSES, track))
        def bad = []

        videosOf(track).each { dir ->
            def g = VIDEO_DIR.matcher(dir.name)[0]
            def s = seriesOf(doc, g[2] as String)
            if (s == null) { bad << "${dir.name}: curriculum.yaml has no series${g[2]}"; return }

            def got = roadmapOf(dir)
            if (got == null) return

            def want = s.episodes.collect { it.short as String }
            if (got != want) {
                int i = (0..<Math.max(got.size(), want.size())).find {
                    (it < got.size() ? got[it] : null) != (it < want.size() ? want[it] : null)
                }
                bad << "${dir.name}: the card lists ${got.size()} episodes, the curriculum has " +
                        "${want.size()}; first difference at #${i + 1} — card says ${quoted(got, i)}, " +
                        "curriculum says ${quoted(want, i)}"
            }
        }

        expect:
        bad.isEmpty()

        where:
        track << videoTracks()
    }

    @Unroll
    @Requires({ TocSpec.VIDEOS.directory && !TocSpec.videoDirs().isEmpty() })
    def "#track: every roadmap card agrees on the series title and on which episode you are watching"() {
        // `current` is 1-based and indexes the list above it, so it moves whenever an episode is
        // inserted — 56 going in at position 15 would have shifted 58 and 60 had either been
        // rendered. This is the half of the card that copy-pasting does not keep right.
        given:
        def doc = CurriculumSpec.load(new File(CurriculumSpec.COURSES, track))
        def bad = []

        videosOf(track).each { dir ->
            def g = VIDEO_DIR.matcher(dir.name)[0]
            def s = seriesOf(doc, g[2] as String)
            if (s == null || roadmapOf(dir) == null) return

            int place = s.episodes.findIndexOf { (it.n as String) == (g[3] as String) } + 1
            if (place == 0) { bad << "${dir.name}: series${g[2]} has no episode ${g[3]}"; return }

            def current = currentOf(dir)
            if (current != place)
                bad << "${dir.name}: says current: ${current}, but it is episode #${place} of the series"

            def wantTitle = "Series ${g[2]} — ${s.title}"
            def gotTitle = fieldOf(dir, "seriesTitle")
            if (gotTitle != wantTitle)
                bad << "${dir.name}: seriesTitle is \"${gotTitle}\", the curriculum's is \"${wantTitle}\""
        }

        expect:
        bad.isEmpty()

        where:
        track << videoTracks()
    }

    @Unroll
    @Requires({ TocSpec.VIDEOS.directory && !TocSpec.videoDirs().isEmpty() })
    def "#track: every copy of a series roadmap is identical to the others"() {
        // Belt and braces over the rule above: if the curriculum and every copy move together
        // this can never fire, and if somebody repairs ONE video by hand it fires and names it.
        given:
        def bad = []

        videosOf(track).groupBy { VIDEO_DIR.matcher(it.name)[0][2] as String }.each { seriesNo, dirs ->
            def copies = dirs.collectEntries { [(it.name): roadmapOf(it)] }.findAll { it.value != null }
            def distinct = copies.values().toList().unique()
            if (distinct.size() > 1) {
                bad << "${track} series${seriesNo}: ${distinct.size()} different roadmaps across " +
                        "${copies.size()} videos — " +
                        copies.groupBy { it.value }.collect { list, group ->
                            "${group.keySet().join(', ')} list ${list.size()}"
                        }.join(" | ")
            }
        }

        expect:
        bad.isEmpty()

        where:
        track << videoTracks()
    }

    @Unroll
    @Requires({ TocSpec.VIDEOS.directory && !TocSpec.videoDirs().isEmpty() })
    def "#track: every video folder is an episode the curriculum still has"() {
        // Folder NAME, not slug: the composition folder is also the key in
        // config/videos.manifest.json, which maps it to a published YouTube id, so it is allowed
        // to keep a name the lesson slug has since dropped (series1 · 50 does exactly that, and
        // renaming it would orphan a published video). What is NOT allowed is a video for a
        // series or an episode number that no longer exists.
        given:
        def doc = CurriculumSpec.load(new File(CurriculumSpec.COURSES, track))
        def bad = []

        videosOf(track).each { dir ->
            def g = VIDEO_DIR.matcher(dir.name)[0]
            def s = seriesOf(doc, g[2] as String)
            if (s == null) { bad << "${dir.name}: no series${g[2]} in curriculum.yaml"; return }
            if (!s.episodes.any { (it.n as String) == (g[3] as String) })
                bad << "${dir.name}: series${g[2]} has no episode ${g[3]} (it has ${s.episodes*.n.join(', ')})"
        }

        expect:
        bad.isEmpty()

        where:
        track << videoTracks()
    }

    // ══════════════════════════════════════════════════════════════════════
    //  The copies inside curriculum.yaml itself
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Every "ramp" table written into a curriculum's own comments: the lines under a `THE RAMP`
     * header that begin with an episode number. SnakeYAML drops comments, so this reads the file
     * as text — which is the point, since a comment is exactly the copy nothing else checks.
     */
    static List<List<String>> rampsIn(File dir) {
        List<String> lines = new File(dir, "curriculum.yaml").getText("UTF-8").readLines()
        List<List<String>> out = []
        lines.eachWithIndex { line, i ->
            if (!line.contains("THE RAMP")) return
            List<String> ns = []
            for (int j = i + 1; j < Math.min(lines.size(), i + 25); j++) {
                def entry = (lines[j] =~ /^\s*#\s{2,}(\d\d\s+\S.*)$/)
                if (!entry.find()) {
                    if (!ns.isEmpty()) break
                    continue
                }
                (entry.group(1) =~ /(\d\d)\s+[A-Z]/).each { all, n -> ns << (n as String) }
            }
            if (!ns.isEmpty()) out << ns
        }
        out
    }

    @Unroll
    def "#track: a ramp table in the comments lists its series' episodes, in order"() {
        // Learn SQL's Series 2 ramp is the one that was wrong: sixteen rows for seventeen
        // episodes, because 56 joined the series and nobody added it here. An episode missing
        // from the ramp is an episode with no stated difficulty and no stated place.
        given:
        def dir = new File(CurriculumSpec.COURSES, track)
        def doc = CurriculumSpec.load(dir)
        def series = doc.series.collect { s -> s.episodes.collect { it.n as String } }
        def bad = []

        rampsIn(dir).each { ramp ->
            if (!series.contains(ramp)) {
                def near = series.min { (it - ramp).size() + (ramp - it).size() }
                bad << "a ramp table lists ${ramp.size()} episodes (${ramp.join(' ')}) and no series " +
                        "matches it; the closest has ${near.size()} (${near.join(' ')}) — " +
                        "missing from the ramp: ${(near - ramp).join(', ') ?: '(none)'}; " +
                        "in the ramp but not in the series: ${(ramp - near).join(', ') ?: '(none)'}"
            }
        }

        expect:
        bad.isEmpty()

        where:
        track << CurriculumSpec.trackDirs()*.name
    }

    @Unroll
    def "#track: an 'N of M episodes' count quotes a series size that is real"() {
        // The sentence that caught the ramp: "it appears in 8 of 17 episodes". It was right and
        // the table twelve lines above it was wrong, which is the only reason anybody noticed.
        given:
        def dir = new File(CurriculumSpec.COURSES, track)
        def doc = CurriculumSpec.load(dir)
        def sizes = doc.series.collect { it.episodes.size() }
        def bad = []

        (new File(dir, "curriculum.yaml").getText("UTF-8") =~ /(\d+) of (\d+) episodes/).each { all, n, m ->
            if (!sizes.contains(m as Integer))
                bad << "\"${all}\" — no series in this course has ${m} episodes (they have ${sizes.join(', ')})"
            else if ((n as Integer) > (m as Integer))
                bad << "\"${all}\" — that is more than all of them"
        }

        expect:
        bad.isEmpty()

        where:
        track << CurriculumSpec.trackDirs()*.name
    }

    // ══════════════════════════════════════════════════════════════════════
    //  The copy in the course README
    // ══════════════════════════════════════════════════════════════════════

    static final Map<Integer, String> COUNT_WORD =
            [1: "One", 2: "Two", 3: "Three", 4: "Four", 5: "Five", 6: "Six", 7: "Seven", 8: "Eight"]

    def "learnsql: README.md tells the same story as curriculum.yaml"() {
        // The README is prose and is meant to stay prose. This checks the handful of facts in it
        // that are NOT prose: how many series, how many episodes in total, and each series' title
        // and size. It said "Three series" from the day Series 4 was added until 2026-09-21.
        //
        // Learn SQL by name because it is the only course README that outlines its series. When a
        // second one does, add it to a list here and this rule covers it too.
        given:
        def dir = new File(CurriculumSpec.COURSES, "learnsql")
        def doc = CurriculumSpec.load(dir)
        def readme = new File(dir, "README.md").getText("UTF-8")
        int total = doc.series.sum { it.episodes.size() } as int
        def bad = []

        def headline = "${COUNT_WORD[doc.series.size()]} series, ${total} episodes"
        if (!readme.contains(headline))
            bad << "README does not say \"${headline}\" — the course has ${doc.series.size()} series " +
                    "and ${total} episodes"

        doc.series.eachWithIndex { s, i ->
            def want = "**Series ${i + 1} — ${s.title}** (${s.episodes.size()} episode"
            if (!readme.contains(want))
                bad << "README has no line starting \"${want}…\""
        }

        expect:
        bad.isEmpty()
    }
}
