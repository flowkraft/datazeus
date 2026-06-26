# DataZeus tests

One Spock/Maven module with **two source roots**, same toolchain. Built on **Spock**
(the Groovy BDD framework), running against an **embedded DuckDB** seeded from
`../datasets/northwind/`. No Docker, no external database.

## Two source roots, two jobs
- **`src/verify/` — `*Spec.groovy` — the publish GATE.** Real answers baked in; tests
  EXACTLY the queries the lesson/video show (read from each lesson's own `scripts/*.sql`).
  CI runs these and a red one blocks publishing. They're also the source of the
  "expected N" numbers shown in the videos.
- **`src/koans/` — `*Koans.groovy` — the EXERCISE.** A *separate*, richer set of
  fill-in-the-`___` drills on the same topic — practice, **not** a blanked copy of the
  gate. You replace each `___`, run, and watch it go green.

## Package convention — scope to one course / series / lesson
Koans for the whole platform live in this single module, organised by package so a
learner can run **just the slice they're on**:

```
src/<root>/groovy/datazeus/<course>/series<N>/ep<NN>/<Lesson>{Spec|Koans}.groovy
e.g.  datazeus/learnsql/series1/_00/WriteYourFirstQueryKoans.groovy
```

Run a scope with the wrapper (it builds a path filter that keeps the `*Koans`
restriction, so the gate never runs during a koans session):

```bash
./koans.sh                 # every koan, every course
./koans.sh sql             # all Master SQL koans
./koans.sh sql 1           # Master SQL · Series 1
./koans.sh sql S1 _00        # ONE lesson (Series 1 · lesson _00)  ← the usual path
```
Course aliases: `sql modeling etl warehousing dbt viz bi`. Episode is two digits.

## The ritual — the path to enlightenment
- Each `*Koans` spec is `@Stepwise`: koans run **in order**, and once one fails the
  rest **wait** (are skipped) — you fix them one at a time.
- A Spock global extension (`datazeus.koans.PathToEnlightenment`, registered only on the
  koans classpath) prints a progress bar + the next koan to "sit with", or the
  enlightenment line when every koan in scope is green. It scopes automatically to
  whatever you ran.

## Run (raw maven)
```bash
mvn test            # the gate (src/verify, must be green)
mvn test -Pkoans    # all koans (src/koans, red until filled)
mvn test -Pkoans -Dtest.includes="**/learnsql/series1/_00/**/*Koans.java"   # one lesson
```
The `-Pkoans` profile flips which source root is compiled/run — one toolchain.

The gate spec reads the lesson's **own** `scripts/*.sql` (relative to this module), so the
SQL is authored once under `courses/learnsql/.../scripts/` and verified here — no drift.

> The `___` trick: it's an `Object`, so `actualInt == ___` is always `false` (red) until
> replaced with the right value. Same idea as Ruby/Python/Kotlin Koans.

> After **moving/renaming** koan packages, run `mvn clean` once — stale `.class` files
> from the old location would otherwise still be picked up.
