// ===========================================================================
//  RESYNC LESSON BUNDLES  —  node content/_datazeus/tools/resync-lessons.js
// ===========================================================================
//  Re-derives the parts of every lesson file that come FROM curriculum.yaml:
//    - the front-matter `title`
//    - the "HANDS-ON FOR THIS EPISODE" brief, for tracks that use `hands_on`
//  Everything else — all the prose and the per-episode rationale — is untouched.
//
//  WHY THIS EXISTS. The generators that created these files COPIED title and
//  hands_on into them, which makes each lesson a snapshot, and a snapshot goes
//  stale the moment the curriculum moves. It has happened twice:
//    - Data Modeling: 20 curriculum edits later, 19 of 42 files disagreed.
//    - A retitle pass across Learn SQL and Data Modeling left 34 files behind.
//  Both times the gate caught it, which is the point of the gate — this is the
//  thing that fixes it.
//
//  EVERY TRACK, not one. It was Data-Modeling-only until 2026-08-24, which meant
//  it could not repair the very drift the gate had just found in Learn SQL.
//
//  ...and the "every track" fix was itself only half done — see CONVENTIONS below.
//  It walked every track but the hands-on regex only ever matched Data Modeling's
//  header, so Java & Groovy (41 briefs) and Python (31) were silently skipped for
//  ten days. Same bug class as the one this file exists to fix, one level up.
//  Fixed 2026-08-24: the block format and the koan-file path are now per-track
//  DATA, and an unknown track fails loudly instead of being quietly ignored.
//
//  Run it after ANY curriculum edit. You do not have to remember: CurriculumSpec
//  (tests/src/verify/.../CurriculumSpec.groovy) fails the build if you forget.
//
//  Handles both finished lessons (00-slug.mdx) and briefs (_todo-00-slug.mdx).
//  Needs js-yaml, which the website already depends on:
//      cd flowkraft/www/reportburster.com && node content/_datazeus/tools/resync-lessons.js
// ===========================================================================
const fs = require("fs")
const path = require("path")
const yaml = require("js-yaml")

const COURSES = path.join(__dirname, "..", "courses")
const TRACKS = fs.readdirSync(COURSES)
  .filter((t) => fs.existsSync(path.join(COURSES, t, "curriculum.yaml")))

const pascal = (s) => s.split("-").map((w) => w[0].toUpperCase() + w.slice(1)).join("")
const snake = (s) => s.replace(/-/g, "_")

// ---------------------------------------------------------------------------
//  PER-TRACK CONVENTIONS. Each track that declares `hands_on` writes its brief
//  block with its own header and points at its own koan toolchain. Pretending
//  one shape fits all is what caused the ten-day silent skip described above.
//
//    header  the exact first line of the block, matched literally
//    koan    where THIS track's koan for an episode lives, so the brief names a
//            path its author can actually open
// ---------------------------------------------------------------------------
const CONVENTIONS = {
  datamodeling: {
    header: "    HANDS-ON FOR THIS EPISODE (from curriculum.yaml — re-synced, never hand-edited)",
    koan: (n, ep) => `series${n}/_${ep.n}/${pascal(ep.slug)}Koans.groovy`,
  },
  javagroovy: {
    header: "    ── HANDS-ON (from curriculum.yaml `hands_on`) ──────────────────────────",
    koan: (n, ep) =>
      `tests/src/koans/groovy/datazeus/javagroovy/series${n}/_${ep.n}/${pascal(ep.slug)}Koans.groovy`,
  },
  python: {
    header: "    ── HANDS-ON (from curriculum.yaml) ─────────────────────────────────────",
    koan: (n, ep) => `tests/src/koans/python/series${n}/_${ep.n}/test_${snake(ep.slug)}.py`,
  },
}

// ---------------------------------------------------------------------------
//  THE SIX ROADMAP TRACKS, added 2026-08-24 when they grew `hands_on`.
//
//  WHICH HARNESS. Not a style choice — it follows from what each track's oracle
//  needs to touch:
//    Groovy/Spock  etl, dbt, datawarehousing, dataops, bi. All five assert over
//                  JDBC or over a process (dbt build, a shell pipeline, a
//                  scheduled job), which is exactly what KoanBase and
//                  JvmKoanBase already do. A second harness would be a second
//                  framework for no new capability.
//    pytest        ai. The LLM ecosystem is Python, and `koan:eval` — score
//                  generated output against known-good and assert a threshold —
//                  needs to live where the client libraries are.
// ---------------------------------------------------------------------------
// Learn SQL, which grew `hands_on` on 2026-08-24. Its koans have always lived at
// groovy/datazeus/learnsql/ — this only records the path so the briefs can name it.
CONVENTIONS.learnsql = {
  header: "    ── HANDS-ON (from curriculum.yaml) ─────────────────────────────────────",
  koan: (n, ep) =>
    `tests/src/koans/groovy/datazeus/learnsql/series${n}/_${ep.n}/${pascal(ep.slug)}Koans.groovy`,
}

const JVM_SIX = (track) => ({
  header: "    ── HANDS-ON (from curriculum.yaml) ─────────────────────────────────────",
  koan: (n, ep) =>
    `tests/src/koans/groovy/datazeus/${track}/series${n}/_${ep.n}/${pascal(ep.slug)}Koans.groovy`,
})
for (const t of ["etlpipelines", "dbt", "datawarehousing", "dataops", "bi"]) {
  CONVENTIONS[t] = JVM_SIX(t)
}
CONVENTIONS.ai = {
  header: "    ── HANDS-ON (from curriculum.yaml) ─────────────────────────────────────",
  koan: (n, ep) => `tests/src/koans/python/ai/series${n}/_${ep.n}/test_${snake(ep.slug)}.py`,
}

let titles = 0
let blocks = 0

for (const track of TRACKS) {
  const C = path.join(COURSES, track)
  const doc = yaml.load(fs.readFileSync(path.join(C, "curriculum.yaml"), "utf8"))
  for (const s of doc.series) {
    const n = s.slug.match(/^series(\d+)/)[1]
    for (const ep of s.episodes) {
      const dir = path.join(C, s.slug, ep.n + "-" + ep.slug)
      let f = path.join(dir, ep.n + "-" + ep.slug + ".mdx")
      if (!fs.existsSync(f)) f = path.join(dir, "_todo-" + ep.n + "-" + ep.slug + ".mdx")
      if (!fs.existsSync(f)) continue

      let t = fs.readFileSync(f, "utf8")

      // 1) front-matter title — always derived from the curriculum
      const want = "title: " + JSON.stringify(ep.title)
      const t2 = t.replace(/^title: .*$/m, want)
      if (t2 !== t) { t = t2; titles++ }

      // 2) the hands-on brief — only for tracks that declare the field, and only
      //    while the episode is still a BRIEF. The block exists to tell an author
      //    which exercise to write; a published lesson has no author left to
      //    mislead, and the real teaching material is the thing that matters there.
      const hands = ep.status === "published" ? [] : ep.hands_on || []
      if (hands.length) {
        const conv = CONVENTIONS[track]
        if (!conv) {
          // Loud, not silent. A track that grew a `hands_on` field without an entry
          // here is exactly the case that went unnoticed for ten days.
          console.error(
            `FATAL: track '${track}' declares hands_on but has no entry in CONVENTIONS. ` +
            `Add one (header + koan path) — do not let it be skipped.`)
          process.exit(1)
        }
        const rungs = hands.filter((h) => h.indexOf("koan:") === 0)
        const tabs = hands.filter((h) => h.indexOf("tab:") === 0)
        const lines = [conv.header]
        hands.forEach((h) => lines.push("      " + h))
        if (ep.dataset) lines.push("      dataset: " + ep.dataset)
        lines.push(rungs.length
          ? "      koan file: " + conv.koan(n, ep)
          : "      NO KOAN — judged by eyes, deliberately. See the note above.")
        if (tabs.length) {
          lines.push('      NAME THE TAB IN THE PROSE — "' + tabs.map((x) => x.slice(4)).join(", ") + '".')
        }
        // Anchor on this track's own header, and stop at the `===...` that closes
        // the comment block. Matching any track's header here would rewrite the
        // block in another track's shape.
        const re = new RegExp(
          conv.header.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "[\\s\\S]*?(?=\\n   ={10})")
        if (re.test(t)) {
          const t3 = t.replace(re, lines.join("\n"))
          if (t3 !== t) { t = t3; blocks++ }
        } else {
          console.error(`WARN: ${path.relative(COURSES, f)} has no hands-on block to resync`)
        }
      }

      fs.writeFileSync(f, t)
    }
  }
}

console.log("resynced across " + TRACKS.length + " tracks: " + titles + " titles, " + blocks + " hands-on blocks")
