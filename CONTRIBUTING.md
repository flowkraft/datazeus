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

- **Lesson:** write it, then drop the `_todo-` prefix. Contentlayer picks it up; set
  `published: true` when the video is live.
- **Koan:** write the koans, then move the file from `src/koans/_todo/<track>/…` into
  `src/koans/groovy/datazeus/<track>/…` at the same series/episode path. Check the `package`
  line matches its new folder.

### What counts as finished

A koan is real when it has at least one **uncommented** `def "…"()` feature method. Example
sketches inside `//` comments do not count — that is what a brief looks like, and an earlier
classification pass got this wrong and promoted 31 briefs by mistake.

---

## The roadmap gate

`zeus test` runs `CurriculumSpec`, which checks every `courses/*/curriculum.yaml` against the
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
