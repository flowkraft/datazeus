package datazeus._internal

/**
 * Counts the most rows the learner's code ever held IN MEMORY AT ONCE.
 *
 * ── THE EPISODE ──────────────────────────────────────────────────────────
 * 2/35 is "Streaming Large Results — Fetch Size, Cursors and Not Melting the Heap". Both the
 * naive version (pull the whole ResultSet into a List, then sum) and the streaming version
 * produce the identical, correct total. Correctness cannot separate them.
 *
 * ── WHY THIS AND NOT -Xmx ────────────────────────────────────────────────
 * The first design forked a JVM with -Xmx32m so the naive version would OutOfMemory. It
 * genuinely works and it is dramatic. It was rejected anyway: it costs ~30 seconds in the
 * learner's inner loop and it proves exactly what this counter proves instantly, and a slow
 * exercise is an exercise people stop running.
 *
 * It was also the wrong SHAPE of test. An OOM tells you that you ran out of memory. This
 * tells you the thing you actually did wrong: you were holding a million rows when one would
 * have done. That is the sentence the learner needs.
 *
 * ── WHY IT CANNOT BE FAKED ───────────────────────────────────────────────
 * The koan hands over a sink and asks for the answer to be produced through it. To keep
 * peakHeld at 1 you have to consume each row and let it go — which is streaming, by
 * definition. Materialising the result first and then replaying it into the sink records the
 * peak at the moment of materialisation, because that is what accept() is counting: rows
 * alive, not rows seen.
 *
 * `seen` exists so a koan can prove the learner did not simply skip most of the data on the
 * way to a small peak — the pincer again: hold few rows, AND still see all of them.
 */
class RowSink {

    /** Rows currently alive — the learner calls release() (or use consume()) when done. */
    int held = 0

    /** The high-water mark. This is the number the koan asserts on. */
    int peakHeld = 0

    /** Every row that ever arrived. Guards against "small peak because you dropped the data". */
    int seen = 0

    /** A row has arrived and is now being held. */
    void accept(Object row) {
        seen++
        held++
        if (held > peakHeld) peakHeld = held
    }

    /** Finished with a row — it can be collected. */
    void release() { if (held > 0) held-- }

    /**
     * The shape most koans want: take a row, do something with it, let it go. Streaming code
     * falls into this naturally; code that built a List first cannot get back to a peak of 1.
     */
    void consume(Object row, Closure work) {
        accept(row)
        try { work.call(row) } finally { release() }
    }

    void reset() { held = 0; peakHeld = 0; seen = 0 }

    @Override
    String toString() { "saw ${seen} rows, held at most ${peakHeld} at once" }
}
