# Working on DataZeus

## `_todo` — how an unwritten lesson is marked

**If you downloaded DataZeus and opened a file full of `TODO`, you found a brief, not a
lesson.** Those files are named so you can tell at a glance:

| | finished | not written yet |
|---|---|---|
| lesson | `courses/<track>/<series>/20-joins/20-joins.mdx` | `courses/<track>/<series>/20-joins/`**`_todo-`**`20-joins.mdx` |
| koan | `tests/src/koans/`**`groovy`**`/datazeus/<track>/…Koans.groovy` | `tests/src/koans/`**`_todo`**`/<track>/…Koans.groovy` |

Nothing under `_todo` is teaching material. Every one of them contains a **brief**: what the
episode must cover, which constructs it teaches, and the decisions already taken about it.
That is genuinely useful — it is just not the lesson.

### Why the mechanism differs between the two

Not for style. Each one uses whatever the surrounding tooling already filters on, so a TODO
file cannot leak into anything:

- **Lessons — a `_todo-` filename prefix.** Contentlayer's `Lesson` type matches
  `_datazeus/courses/**/[0-9]*.mdx`, i.e. filenames starting with a digit. A `_todo-` file
  does not match, so it never enters the site build at all. The folder still shows where the
  finished lesson will live.
- **Koans — a separate `_todo/` tree.** Maven compiles only `src/koans/groovy`, and
  `zeus koans` resolves lesson scopes only under `src/koans/groovy/datazeus`. A draft parked
  outside both is never compiled and never run — and `zeus koans <track> <series> <ep>` gives
  the correct *"not in your copy yet"* message instead of failing with a confusing compile
  error on a spec that contains no tests.

### Promoting one

- **Lesson:** write it, then drop the `_todo-` prefix. **Careful — that publishes it.**
  `published` defaults to `true` in the `Lesson` type, and the course pages filter on it
  (`app/(marketing)/academy/…`), so a renamed file with no `published` line goes live on
  the next build. Add `published: false` to the front-matter while it is still a draft, and
  remove that line when the video is up.
- **Koan:** write the koans, then move the file from `src/koans/_todo/<track>/…` into
  `src/koans/groovy/datazeus/<track>/…` at the same series/episode path. Check the `package`
  line matches its new folder.

### What counts as finished

A koan is real when it has at least one **uncommented** `def "…"()` feature method. Example
sketches inside `//` comments do not count — that is what a brief looks like, and an earlier
classification pass got this wrong and promoted 31 briefs by mistake.

---

## The roadmap gate

`zeus test` runs `mvn test` — **every `*Spec` under `src/verify`**, not just the roadmap one.
That includes each published lesson's own spec, which re-runs its queries against a throwaway
PostgreSQL and the bundled DuckDB, so a lesson whose numbers stopped being true fails here.

The roadmap one is `CurriculumSpec`, which checks every `courses/*/curriculum.yaml` against the
rules its own header states: unique slugs, ascending `n`, every title has a hook, `short` fits
and scans the same, prerequisites resolve, each series' project is on the core path, and
**every lesson file's front-matter title matches the curriculum** — `_todo-` files included,
because a stale title in an unwritten file is a bug waiting for someone to drop the prefix.

It parses YAML with `allowDuplicateKeys = false` on purpose: SnakeYAML tolerates duplicate
keys and js-yaml (which the website uses) throws, and a gate more permissive than production
is worse than no gate.

If it fails on a title mismatch, the usual fix is `node tools/resync-lessons.js`, which
re-derives the parts of each lesson file that come from `curriculum.yaml` and leaves your
prose alone.

---

## Promises between lessons

**A series is one story; a course is a shelf of them.** Inside a series, lessons build on each
other and hand over to each other: that is what makes it a series. Between series, and between
courses, the link is the data, never a promise: the same company, the same table names and IDs
everywhere, kept consistent by the install scripts (their checksums and gates), not by lesson
text. Each series can then be rewritten, renumbered or cut without breaking any other.

### Inside a series: build on it, and keep what you promise

| DO | DON'T |
|---|---|
| Build on the lessons before this one. Open with a one-sentence recap, and keep the same case, tables and practice file. | Re-teach what an earlier lesson of the series already taught, or switch datasets halfway through a series. |
| End by handing over: name the question the next lesson answers, and open that lesson on it. *"What this query cannot say yet is how much money is on those orders. That needs more than one table."* (Learn SQL 2 · 10 → 2 · 15) | End on a hand-over the next lesson doesn't pick up. |
| Before promising a subject, check the curriculum to see which lesson owns it. Write the promise only if that lesson exists and is at least drafted. | Promise what no lesson covers. Data Modeling 1 · 05 says contacts are *"the next lessons' subjects"*, and drafts 10, 15 and 20 never mention them. |
| When you draft a lesson, search the earlier lessons of the series for promises pointing at it (*"next lesson"*, *"a lesson of its own"*, *"comes back to it"*) and keep them. If the plan changed, fix the earlier lesson too. | Leave an earlier lesson promising something the plan has since moved elsewhere. |
| Say *"a later lesson in this series"* in spoken copy. | Put lesson numbers in spoken copy: *"lesson forty-five"*. |
| Make the first lesson of a series install everything the series needs. | Assume the learner still has a database or file from another series. |

### Across series and courses: coherent data, no hard promises

| DO | DON'T |
|---|---|
| Name a course or a subject: *"covered in Data Ops"*, *"Series 3 is about performance"*. | Cite another series' or course's episode: *"Data Ops, Series 1 · 35"*, *"BI, Series 2 · 40"*. Episodes get renumbered and the reference breaks silently. |
| Give every series its own starting point: a dataset, or a reference artifact it installs (the `academy-northwind-co-raw-install` copy dbt starts from). *"If you built your own in Series 1, use it instead"* is an invitation. | Make a series depend on something the learner built elsewhere: *"your own Series 1 model"*, *"the raw copy your ETL project produced"*. Someone arriving from YouTube, or who skipped a project, has nothing to start from. |
| Explain a recurring planted fact in place, in one sentence: *"day 45 of this feed is a bad load"*. | Lean on another course: *"the day 45 you met in ETL"*. |
| End a series by naming the subjects ahead: *"Series 2 joins more tables and builds queries out of queries"*. | Promise a plot, a figure or a dataset detail that another series must deliver: *"Series 3 finds out what happened to those orders"*. |
| State prerequisites as skills, pointing to the course or series that teaches them: *"you can write a join and a CTE — Learn SQL, Series 2"*. | Pin a prerequisite to an episode: *"INSERT from Series 2 · 55"*. |
| Link only to published lessons, or name the lesson without a link until it is published. | Link to a lesson that is still a draft: it returns 404 (Learn SQL 1 · 20 links to 2 · 15). |
| Title an episode with what the data can show today. | Name a table, engine or file in a title before the dataset that has it exists: *"… to fact_sales"*, *"From SQLite"*, *"Northwind PostgreSQL"*. |

Pointing at this course's own episodes from `curriculum.yaml` is fine, e.g. *"In this course,
Series 2 · 50 is the SQL itself"* in an exclude. It lives in the same file, and the gate checks
that it resolves.

### What the gate checks, and what it leaves to you

`CurriculumSpec` (run by `zeus test`, or on its own with `npm run check:curriculum` from the
website root) fails on:

- an exclude or prerequisite that cites another course's episode (*"Data Ops, Series 1 · 35"*),
  or a prerequisite pinned to an episode (*"INSERT from Series 2 · 55"*);
- a written lesson (drafts included, `_todo-` briefs not) citing an episode of another series
  or another course;
- a live lesson linking to an `/academy/…` lesson that is not live yet.

A number is proof. Most promises carry none (*"Series 3 finds out what happened"*, *"a lesson
of its own later in this series"*), so a second gate, `PromisesSpec`, flags them by heuristics:
another series or course named, a link to either, *"the next course"*, *"you built it in
another series"*, and hand-overs like *"next lesson"* or *"comes back to it"*. It reads each
series' tagline and each episode's title, `short` and `data.reason` in `curriculum.yaml`, and
every written lesson (not `_todo-` briefs: planning notes are cross-references on purpose,
and a brief reaches no learner until it becomes a lesson file).

A flagged sentence fails the build. There are two ways to make it pass:

- **Reword it** so it stands on its own.
- **List it in `promises.yaml`** (next to this file):
  - `not_a_promise` with a `why`, when it points elsewhere but promises nothing;
  - `kept` for a hand-over inside the series, naming the lesson that keeps it:
    `kept_by: "1 · 45"`, `about: "NULL"`. The gate then checks that the lesson comes later, is
    in the same series, mentions `about`, and is live whenever the promising lesson is;
  - `covered` when it names what another series or course teaches (*"query speed is covered
    in Series 3"*). That is safe only when the series certainly covers it, so the entry says
    which (`series: 3`, plus `course:` for another course) and `about: [EXPLAIN, Indexes]`, and
    the gate checks that those words are in that series' plan in `curriculum.yaml`.

An entry that no longer matches any sentence fails too, so the list never outlives what it
excuses. When the gate fails, it writes ready-to-paste entries to
`tests/target/promises-uncovered-<course>-<cross|forward>.yaml`. `unreviewed` holds what the
first run found (2026-09-19). Work through it, and never add to it.

`npm run check:curriculum` runs both gates, plus `DatasetsSpec` (next section). What neither can judge is whether the lesson that
keeps a promise keeps it *well*. That is the "PROMISES KEPT" step of the episode checklist.

**The test for any sentence that points beyond its own series:** if the lesson it points at were
rewritten tomorrow, would this sentence still be true? If not, make it softer or remove it.

---

## Which Northwind a series runs on

There are two, and they are two different companies with the same table and column names:

- **Classic Northwind** (`datasets/northwind/northwind.duckdb`, `data.source: northwind`): DataPallas's
  demo data, with 25 customers, 79 orders and no declared keys. Its file also holds a random pivot-table star
  (`fact_sales`, `dim_*`), which the curriculum calls `generated`.
- **Northwind Company** (`northwind_co_s` / `_m` / `_l` and the datasets built from it): the academy's
  own dataset, a realistic business with declared keys, history, invoices and stock.

The classic one stays where being tiny, keyless or installed on every engine *is* the lesson:

| Where | Why |
|---|---|
| Learn SQL S1 | A beginner can see every row and check a result by eye. Published and frozen. |
| Learn SQL S4 | It compares engines on the same rows, and DataPallas installs the classic dataset on every engine. |
| Data Modeling S1 | The learner designs the keys that the classic dataset leaves undeclared; 1 · 57 reverse-engineers a schema that declares nothing. |
| Data Modeling S3 · 25, · 30 | The random star is judged as "a star somebody else built": its 28-day calendar, its missing order number. |
| Data Warehousing S2 · 25 | Temporary: DataPallas's sample cubes are built on it. |

Everywhere else is Northwind Company, and no series switches between the two halfway.

`DatasetsSpec` enforces this in both directions (the classic dataset is used where it must be, and
nowhere else), and its failure messages quote the reasons above. It checks each
episode's `data.source`, the base class of every lesson spec and koan followed up its whole chain
(`KoanBase` and `NorthwindGateSpec` open the classic file, `NorthwindCoKoanBase` and
`NorthwindCoGateSpec` open Northwind Company), any direct use of the classic file's path or helpers,
and that the classic file still has what these lessons are built on: no declared
keys, the empty tables, 25 / 79 / 193 / 20 rows, the 28-day calendar and the missing order number.
To change a decision on purpose, edit `CLASSIC_SERIES` or `CLASSIC_EPISODES` in that spec, with the
new reason, in the same commit as the curriculum.
