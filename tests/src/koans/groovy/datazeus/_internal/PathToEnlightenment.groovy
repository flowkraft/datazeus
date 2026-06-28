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
 *       <relative path to the .groovy koan>:<line>
 *       <line>:   <the actual source line with the ___>
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

    private static final List<String> ZEN = [
            "Real data, real questions. Become the Data Zeus.",
            "Don't go hunting for the answer — write a query and ask for it.",
            "A query you typed is worth a thousand you watched.",
            "You became a legendary Data Zeus!",
            "Small data, fits in your head. Real enough to ask anything.",
            "The rows you keep tell the truth. WHERE is your discipline.",
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
            o << "\n  " + CYAN + zen(done) + RESET + "\n"
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
                if (path && ln) {
                    // A multi-line koan reports its start line; show the line that
                    // actually holds the ___ (the blank you must fill).
                    def blank = blankLine(path, ln)
                    int showNo = (blank ? blank[0] : ln) as int
                    String src = (blank ? blank[1] : sourceLine(path, ln))
                    o << "      " + CYAN + path + ":" + showNo + RESET + "\n"
                    if (src) o << "      " + showNo + ":   " + src + "\n"
                } else if (path) {
                    o << "      " + CYAN + path + RESET + "\n"
                }
            }
            o << "\n      your path thus far  " + bar(done, total) + "  " +
                    BOLD + done + RESET + " of " + BOLD + total + RESET + " koans\n"
            o << "\n  " + CYAN + zen(done) + RESET + "\n"
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

    private static String zen(int seed) {
        return ZEN[Math.abs(seed) % ZEN.size()]
    }

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

    /** "WriteYourFirstQueryKoans" -> "Write Your First Query". */
    private static String prettySpec(String specName) {
        String base = specName.replaceAll(/Koans$/, "")
        return base.replaceAll(/([a-z])([A-Z])/, '$1 $2')
    }

    private static String sourcePath(String specName) {
        String cls = specClassNames[specName]
        if (!cls) return null
        return KOANS_ROOT + "/" + cls.replace('.', '/') + ".groovy"
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
