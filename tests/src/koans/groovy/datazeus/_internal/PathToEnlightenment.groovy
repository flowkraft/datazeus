package datazeus._internal

import org.spockframework.runtime.AbstractRunListener
import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.ErrorInfo
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo

/**
 * The path to enlightenment — a faithful Ruby/Python/PowerShell-Koans run summary
 * for DataZeus koans.
 *
 * Registered as a Spock GLOBAL extension via
 *   src/koans/resources/META-INF/services/org.spockframework.runtime.extension.IGlobalExtension
 * That resources dir is only on the classpath under `mvn test -Pkoans`, so this
 * summary appears for the koans, never for the verified gate (`mvn test`).
 *
 * The output deliberately mimics the classic koans experience (no stacktrace):
 *   Thinking about <Lesson>
 *       <koan> has expanded your awareness.        (green — solved)
 *       <koan> has damaged your karma.             (red   — the one you're on)
 *
 *   You have not yet reached enlightenment ...
 *       The query returned 4, but the koan still says ___ .
 *
 *   Please meditate on the following code:
 *       Your koans file is  <the bare file name, e.g. StartHereKoans.groovy>
 *       Its location is     <the ABSOLUTE path to that file>
 *
 *       Fix line <n>:
 *       <line>:   <the actual source line with the ___>
 *
 * The name and the absolute path are given separately and on their own lines because this
 * is the one thing the learner has to act on: they are about to leave the terminal, find
 * that file in an editor and change it. A path relative to the Maven module ("src/koans/...")
 * cannot be pasted anywhere useful - it does not even resolve from the datazeus folder the
 * command was run in, which is one level up from it.
 *
 *       your path thus far  [####......]  N of M koans
 *
 *   <a data-themed zen line>
 *
 * Spock @Stepwise on each *Koans spec makes koans run in order and, once one fails,
 * the rest WAIT (are skipped) — so you walk the path one koan at a time.
 *
 * The whole report is also written to target/path-to-enlightenment.txt; the
 * koans.sh/koans.bat wrappers print THAT instead of Maven's noisy build log.
 */
class PathToEnlightenment implements IGlobalExtension {

    private static final String ESC = ""
    private static final String GREEN = ESC + "[32m"
    private static final String RED = ESC + "[31m"
    private static final String YELLOW = ESC + "[93m"
    private static final String DIM = ESC + "[90m"
    private static final String BOLD = ESC + "[1m"
    private static final String MAGENTA = ESC + "[95m"
    private static final String CYAN = ESC + "[36m"
    private static final String RESET = ESC + "[0m"

    // The source root koans are compiled from (relative to the module dir = CWD at test time).
    private static final String KOANS_ROOT = "src/koans/groovy"

    private static final Map<String, List<String>> specFeatureOrder = [:]
    private static final Map<String, String> specPackages = [:]
    private static final Map<String, String> specClassNames = [:]
    private static final Set<String> passed = new LinkedHashSet<>()
    private static final Set<String> failed = new LinkedHashSet<>()
    private static final Map<String, String> hints = [:]
    private static final Map<String, Integer> lineNos = [:]

    /* TWO LISTS, NOT ONE — and the split fixes a bug this shipped with.
     *
     * The closing line used to be picked as ZEN[done % ZEN.size()] from a single list holding
     * both "keep going" lines and "you have finished" lines. Six entries, seeded by the number
     * of koans passed, so at 9 of 10 the index landed on 3:
     *
     *     "You became a legendary Data Zeus!"
     *
     * printed directly under a screen that had just said "You have not yet reached
     * enlightenment" and named the koan that failed. The learner is congratulated for
     * finishing something they have not finished, which is worse than saying nothing — it
     * tells them the runner is not paying attention. It went the other way too: completing
     * all ten could close on "Don't go hunting for the answer", advice for somebody who is
     * still working.
     *
     * Splitting the list makes the wrong line IMPOSSIBLE rather than unlikely. Anything
     * congratulatory can only be reached from the branch where done == total.
     *
     * Both lists stay carrot, never stick: the walking ones are what to do next, not what
     * went wrong. A red koan is a step on the path here, not a failure. */
    private static final List<String> ZEN_WALKING = [
            "Real data, real questions. Become the Data Zeus.",
            "Don't go hunting for the answer — write a query and ask for it.",
            "A query you typed is worth a thousand you watched.",
            "The rows you keep tell the truth. WHERE is your discipline.",
    ]

    /* Only ever printed when every koan is green. "Northwind" is NAMED here: the old line read
     * "Small data, fits in your head. Real enough to ask anything." — true, and about a
     * database the sentence never mentioned, so it read as a fortune cookie rather than an
     * invitation to go and query the thing they just spent an hour learning on. */
    private static final List<String> ZEN_ARRIVED = [
            "You became a legendary Data Zeus!",
            "Northwind is small enough to fit in your head, and real enough to ask anything.",
            "Every koan green. Now go and ask this database something nobody has asked it yet.",
    ]

    void start() {}

    void visitSpec(SpecInfo spec) {
        if (!spec.name.endsWith("Koans")) return
        specFeatureOrder[spec.name] = spec.allFeatures
                .sort { it.declarationOrder }
                .collect { it.name }
        String className = ""
        try {
            specPackages[spec.name] = spec.reflection?.getPackage()?.getName() ?: ""
            className = spec.reflection?.name ?: ""
        } catch (ignored) {
            specPackages[spec.name] = ""
        }
        specClassNames[spec.name] = className

        spec.addListener(new AbstractRunListener() {
            @Override
            void error(ErrorInfo error) {
                FeatureInfo f = error.method?.feature
                if (!f) return
                String key = "${f.spec.name}::${f.name}"
                failed.add(key)
                String hint = extractHint(error.exception)
                if (hint != null) hints[key] = hint
                Integer ln = extractLine(error.exception, className)
                if (ln != null) lineNos[key] = ln
            }

            @Override
            void afterFeature(FeatureInfo feature) {
                String key = "${feature.spec.name}::${feature.name}"
                if (!failed.contains(key)) passed.add(key)
            }
        })
    }

    /**
     * The value the koan's left-hand side actually evaluated to, pulled out of Spock's
     * recorded condition so we can hint with it instead of dumping a stacktrace.
     * For `actual == ___`, the actual value is the last "interesting" recorded value
     * (not a Boolean result, not the bare Object ___ sentinel).
     */
    private static String extractHint(Throwable t) {
        try {
            // A query koan threw a ready-made, goal-aware hint — use it verbatim.
            // (Spock may wrap a condition's exception, so walk the cause chain.)
            for (Throwable cur = t; cur != null; cur = cur.cause) {
                if (cur.getClass().name == "datazeus._internal.KoanHint") return cur.message
                if (cur.cause == cur) break
            }
            // SpockComparisonFailure / ConditionNotSatisfiedError both carry a `condition`
            // with the recorded values; detect by the property, not an exact class name.
            if (t != null && t.hasProperty('condition') && t.condition != null) {
                def values = t.condition.values
                def interesting = values?.findAll { v ->
                    v != null &&
                    !(v instanceof Boolean) &&
                    !(v.getClass() == Object && v.toString().startsWith("java.lang.Object@"))
                }
                if (interesting) return String.valueOf(interesting[-1])
            }
        } catch (ignored) {}
        return null
    }

    /** Line number of the koan in its own source file (the frame in the spec class). */
    private static Integer extractLine(Throwable t, String className) {
        try {
            def frame = t?.stackTrace?.find { it.className == className && it.lineNumber > 0 }
            return frame?.lineNumber
        } catch (ignored) { return null }
    }

    void stop() {
        // Ordered roster, de-duplicated (visitSpec can fire more than once).
        List<String> specOrder = specFeatureOrder.keySet().sort { [specPackages[it] ?: "", it] }
        List<List<String>> entries = []   // [key, featureName, specName]
        specOrder.each { specName ->
            specFeatureOrder[specName].each { fname ->
                entries << ["${specName}::${fname}".toString(), fname, specName]
            }
        }
        int total = entries.size()
        if (total == 0) return
        int done = entries.count { passed.contains(it[0]) }
        String currentKey = entries.find { failed.contains(it[0]) && !passed.contains(it[0]) }?.getAt(0)

        StringBuilder o = new StringBuilder()
        o << "\n"

        // Per-spec, classic "Thinking about ..." groups with awareness / karma lines.
        specOrder.each { specName ->
            o << "  " + CYAN + BOLD + "Forging '" + tag(specName) + prettySpec(specName) + "'" + RESET + "\n"
            o << "\n"
            for (String fname : specFeatureOrder[specName]) {
                String key = "${specName}::${fname}"
                if (passed.contains(key)) {
                    o << "      " + GREEN + "You mastered '" + fname + "' which expanded +1 your awareness." + RESET + "\n"
                } else {
                    o << "      " + RED + "'" + fname + "' has damaged your karma." + RESET + "\n"
                    break   // @Stepwise: the rest of this spec waits — don't list them
                }
            }
            o << "\n"
        }

        if (done == total) {
            o << "  " + GREEN + BOLD + "You have reached enlightenment." + RESET + "\n"
            o << "  " + GREEN + "Every koan is green - " + total + " of " + total + ". Well done.\n" + RESET
            o << "\n  " + CYAN + zenArrived(done) + RESET + "\n"
        } else {
            o << "  " + BOLD + "You have not yet reached enlightenment ..." + RESET + "\n"
            if (currentKey) {
                String name = entries.find { it[0] == currentKey }?.getAt(1)
                String hint = hints[currentKey]
                if (hint != null && hint.contains("\n")) {
                    // A query koan's ready-made compare ("your query returned X, but …").
                    hint.split("\n").each { ln -> o << "      " + ln + "\n" }
                } else if (hint != null) {
                    // A predict-the-value koan (`actual == ___`): show what it evaluated to.
                    o << "      The query returned " + GREEN + BOLD + hint + RESET +
                            ", but the koan still says " + RED + "___" + RESET + " .\n"
                } else {
                    o << "      The koan \"" + name + "\" is not yet true.\n"
                }
                o << "\n"
                o << "  " + BOLD + "Please meditate on the following code:" + RESET + "\n"
                String specName = entries.find { it[0] == currentKey }?.getAt(2)
                String path = sourcePath(specName)
                Integer ln = lineNos[currentKey]
                if (path) {
                    // Name first, then where it lives - the learner is about to go and open
                    // this file, and the absolute path is the part they can actually use.
                    o << "      Your koans file is  " + BOLD + CYAN + fileName(path) + RESET + "\n"
                    o << "      Its location is     " + CYAN + fullPath(path) + RESET + "\n"
                    if (ln) {
                        // A multi-line koan reports its start line; show the line that
                        // actually holds the ___ (the blank you must fill).
                        def blank = blankLine(path, ln)
                        int showNo = (blank ? blank[0] : ln) as int
                        String src = (blank ? blank[1] : sourceLine(path, ln))
                        o << "\n      Fix line " + BOLD + showNo + RESET + ":\n"
                        if (src) o << "      " + showNo + ":   " + src + "\n"
                    }
                }
            }
            o << "\n      your path thus far  " + bar(done, total) + "  " +
                    BOLD + done + RESET + " of " + BOLD + total + RESET + " koans\n"
            o << "\n  " + CYAN + zenWalking(done) + RESET + "\n"
        }

        String report = o.toString()
        println report
        try {
            File f = new File("target/path-to-enlightenment.txt")
            f.parentFile?.mkdirs()
            f.text = report
        } catch (ignored) {}
    }

    /** Proportional bar: filled (green) up to done, dim dots for the rest. Caps width at 50. */
    private static String bar(int done, int total) {
        int width = Math.min(total, 50)
        int fill = total == 0 ? 0 : (int) Math.round((done / (double) total) * width)
        if (done > 0 && fill == 0) fill = 1
        if (done == total) fill = width
        return "[" + GREEN + ("#" * fill) + RESET + DIM + ("." * (width - fill)) + RESET + "]"
    }

    // Seeded by progress so the line varies as you advance — but only ever drawn from the
    // list that matches where you actually are.
    private static String zenWalking(int seed) { ZEN_WALKING[Math.abs(seed) % ZEN_WALKING.size()] }
    private static String zenArrived(int seed) { ZEN_ARRIVED[Math.abs(seed) % ZEN_ARRIVED.size()] }

    /** "datazeus.learnsql.series1._00" -> "series1 _00 " (matches the koans.bat command). */
    private static String tag(String specName) {
        def segs = (specPackages[specName] ?: "").split(/\./)
        String s = segs.find { it.startsWith("series") }
        String e = segs.find { it.startsWith("_") }   // the lesson ID slot, e.g. _00 / _05
        def parts = []
        if (s) parts << s              // full "series1" — same token the command uses
        if (e) parts << e
        return parts ? parts.join(" ") + " " : ""
    }

    /** "StartHereKoans" -> "Start Here". */
    private static String prettySpec(String specName) {
        String base = specName.replaceAll(/Koans$/, "")
        return base.replaceAll(/([a-z])([A-Z])/, '$1 $2')
    }

    private static String sourcePath(String specName) {
        String cls = specClassNames[specName]
        if (!cls) return null
        return KOANS_ROOT + "/" + cls.replace('.', '/') + ".groovy"
    }

    /** Just the file name - the thing to search for in an editor's file picker. */
    private static String fileName(String path) {
        try {
            return new File(path).name
        } catch (ignored) { return path }
    }

    /**
     * The koan's ABSOLUTE path, so it can be pasted straight into an editor.
     *
     * KOANS_ROOT is relative to the Maven module dir, which is the CWD the koans run in -
     * exactly the assumption sourceLine() and blankLine() already make when they read the
     * file. So whenever the file really is there, the absolute form is right by construction.
     * When it is not, fall back to the relative path rather than print a confidently wrong
     * absolute one.
     */
    private static String fullPath(String path) {
        try {
            File f = new File(path)
            if (f.exists()) return f.canonicalPath
        } catch (ignored) {}
        return path
    }

    private static String sourceLine(String path, int line) {
        try {
            List<String> lines = new File(path).readLines()
            if (line >= 1 && line <= lines.size()) return clip(lines[line - 1].trim())
        } catch (ignored) {}
        return null
    }

    /**
     * From the koan's start line, find the line that holds the ___ blank — but ONLY
     * within this koan (stop at the end of its SQL block or the next method), so a
     * filled-but-wrong koan doesn't point at a later koan's blank.
     */
    private static List blankLine(String path, int startLine) {
        try {
            List<String> lines = new File(path).readLines()
            int from = Math.max(1, startLine)
            for (int i = from; i <= Math.min(lines.size(), from + 20); i++) {
                String ln = lines[i - 1]
                if (ln.contains("___")) return [i, clip(ln.trim())]
                // boundary: end of this koan's SQL block / statement / next method
                if (i > from && (ln.contains("''')") || ln.trim().startsWith("def ") || ln.trim() == "}")) break
            }
        } catch (ignored) {}
        return null
    }

    private static String clip(String s) {
        return s != null && s.length() > 100 ? s.substring(0, 100) + " ..." : s
    }
}
