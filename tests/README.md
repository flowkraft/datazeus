# DataZeus tests

One Spock/Maven module with **two source roots**, same toolchain. Built on **Spock**
(the Groovy BDD framework), running against an **embedded DuckDB** seeded from
`../datasets/northwind/` (Learn SQL Series 1, Data Modeling) or `../datasets/northwind-co/` (Northwind
Company, from Learn SQL Series 2 on). No Docker, no external database for the koans.

## Two datasets, two bases
| lessons on | koans extend | specs extend | schema |
|---|---|---|---|
| Northwind (frozen) | `KoanBase` | `NorthwindGateSpec` | the file's default |
| Northwind Company S | `NorthwindCoKoanBase` | `NorthwindCoGateSpec` | `northwind_co_s` (`SET search_path` is done for you) |

`NorthwindCoGateSpec` re-checksums BOTH engines against `_dataset_info` before a single figure is
asserted, so a spec can never pass on data that is not the lessons' data. With `PGHOST` set, it
expects Northwind Company installed in that PostgreSQL (DataPallas ▸ Seed Data ▸ Academy Northwind
Co Install); without it, Testcontainers' PostgreSQL gets a type-faithful copy of the DuckDB file.

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
e.g.  datazeus/learnsql/series1/_00/StartHereKoans.groovy
```

Run a scope with the wrapper (it builds a path filter that keeps the `*Koans`
restriction, so the gate never runs during a koans session):

```bash
./zeus.sh koans                            # every koan, every course
./zeus.sh koans learnsql                   # all Learn SQL koans
./zeus.sh koans learnsql series1           # Learn SQL · Series 1
./zeus.sh koans learnsql series1 _00       # ONE lesson  ← the usual path
```
On Windows the same scopes run as `.\zeus.bat koans learnsql series1 _00`.

## The ritual — the path to enlightenment
- Each `*Koans` spec is `@Stepwise`: koans run **in order**, and once one fails the
  rest **wait** (are skipped) — you fix them one at a time.
- A Spock global extension (`datazeus._internal.PathToEnlightenment`, registered only on the
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

Two specs building at the same time (two terminals, two agents) must not share `target/`: give each
its own with `-Ddz.build.dir=target-NN`, e.g.
`PGHOST=localhost ./mvnw -o -q test -Dtest=SubqueriesSpec -Ddz.build.dir=target-00`.

The gate spec reads the lesson's **own** `scripts/*.sql` (relative to this module), so the
SQL is authored once under `courses/learnsql/.../scripts/` and verified here — no drift.

> The `___` trick: it's an `Object`, so `actualInt == ___` is always `false` (red) until
> replaced with the right value. Same idea as Ruby/Python/Kotlin Koans.

> After **moving/renaming** koan packages, run `mvn clean` once — stale `.class` files
> from the old location would otherwise still be picked up.

## Run with only Docker installed (no Java)
`tests/Dockerfile` carries the toolchain this module pins (JDK 17, Maven 3.9.9, Groovy 4.0.22).
From the repository root:

```bash
docker build -t datazeus-tests tests
docker run --rm -v "$PWD":/datazeus -v datazeus-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock --network host \
  datazeus-tests -B test                 # the gate: DuckDB + a throwaway PostgreSQL
```
Mount the whole repository, because the specs read `courses/**/scripts/*.sql`. The Docker socket lets
Testcontainers start the PostgreSQL half, seeded from the DuckDB file. Add `-e PGHOST=localhost` to
check against a Northwind PostgreSQL that is already running instead, or `-Pkoans` to run the koans.
More in the Dockerfile header.

