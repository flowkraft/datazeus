// ===========================================================================
//  RESYNC README NEWS  —  node content/_datazeus/tools/resync-readme-news.js
// ===========================================================================
//  Rewrites the "Latest lessons" list in README.md from the lessons themselves:
//  every `published: true` .mdx, newest `date` first, capped at MAX.
//
//  WHY THIS IS GENERATED AND NOT WRITTEN BY HAND. The README asks people to star
//  the repo "so you don't miss new lessons". That ask is only worth making if a
//  reader can see lessons actually landing — and a hand-kept list stops being
//  evidence the first time somebody ships an episode and forgets this file. A
//  stale News section is WORSE than none: it dates the repo publicly and reads
//  as abandonment. Generated, it either matches the lessons or it is a one-line
//  command away from matching them.
//
//  Same shape as resync-lessons.js, and for the same reason: anything a file
//  COPIES from another file is a snapshot, and a snapshot goes stale.
//
//  It rewrites ONLY what sits between the two markers, so the prose around it is
//  yours. If the markers are missing it says so and changes nothing.
//
//      cd flowkraft/www/reportburster.com
//      node content/_datazeus/tools/resync-readme-news.js
// ===========================================================================
const fs = require("fs")
const path = require("path")
const yaml = require("js-yaml")

const ROOT = path.join(__dirname, "..")
const COURSES = path.join(ROOT, "courses")
const README = path.join(ROOT, "README.md")
const SITE = "https://datapallas.com/data-academy"

const BEGIN = "<!-- news:begin -->"
const END = "<!-- news:end -->"

/** How many to show. Five is enough to read as a cadence and short enough to stay scannable. */
const MAX = 5

// --- the lesson's own front-matter is the source: date, title, published ---
const frontMatter = (file) => {
  const m = fs.readFileSync(file, "utf8").match(/^---\r?\n([\s\S]*?)\r?\n---/)
  return m ? yaml.load(m[1]) : null
}

// --- the SERIES title and the lesson's URL slug both live in curriculum.yaml ---
const courses = {}
for (const track of fs.readdirSync(COURSES)) {
  const file = path.join(COURSES, track, "curriculum.yaml")
  if (!fs.existsSync(file)) continue
  courses[track] = yaml.load(fs.readFileSync(file, "utf8"))
}

const lessons = []
for (const [track, doc] of Object.entries(courses)) {
  for (const series of doc.series) {
    for (const ep of series.episodes) {
      const dir = path.join(COURSES, track, series.slug, `${ep.n}-${ep.slug}`)
      const file = path.join(dir, `${ep.n}-${ep.slug}.mdx`)
      if (!fs.existsSync(file)) continue // still a _todo- brief
      const fm = frontMatter(file)
      // `published` defaults to TRUE in the Lesson type, so only an explicit false hides one.
      if (!fm || fm.published === false) continue
      if (!fm.date) {
        console.warn(`WARN: ${ep.n}-${ep.slug} is published but has no \`date\` — skipped`)
        continue
      }
      lessons.push({
        date: String(fm.date).slice(0, 10),
        title: ep.title,
        series: series.title,
        url: `${SITE}/${doc.course}/${ep.slug}`,
      })
    }
  }
}

lessons.sort((a, b) => b.date.localeCompare(a.date))
const rows = lessons.slice(0, MAX).map(
  (l) => `- **${l.date}** — [${l.title}](${l.url})  ·  *${l.series}*`,
)

const readme = fs.readFileSync(README, "utf8")
const from = readme.indexOf(BEGIN)
const to = readme.indexOf(END)
if (from === -1 || to === -1) {
  console.error(`FATAL: README.md has no ${BEGIN} / ${END} markers — nothing was written.`)
  process.exit(1)
}

const next =
  readme.slice(0, from + BEGIN.length) + "\n" + rows.join("\n") + "\n" + readme.slice(to)

if (next === readme) {
  console.log(`README news already up to date (${rows.length} of ${lessons.length} published).`)
} else {
  fs.writeFileSync(README, next)
  console.log(`README news rewritten — ${rows.length} shown, ${lessons.length} published.`)
}
