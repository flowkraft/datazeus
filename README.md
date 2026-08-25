# DataZeus

**Master everything data — by *doing* it.** → Become a Data Zeus.

Free, hands-on data courses where you write the queries yourself. Every lesson is a written
article, a video, and a set of **koans** — runnable exercises with a blank to fill in, that go
green when you get it right.

## The best hands on way to learn SQL and many other data topics

<a href="https://github.com/flowkraft/datapallas#learn-data" target="_blank">Start Learning Data →</a>

The lessons run on **DataPallas** — it bundles the Northwind sample database on PostgreSQL and
CloudBeaver to query it with, so there is nothing else to install and nothing to sign up for.
Everything runs on your own machine.

## How a lesson works

**1. Watch it, or read it — whichever you prefer.** Every episode ships as both a video and a
written article covering the same ground. Some people learn better watching, some by reading.
Take either. Take both.

**2. Then do it yourself — this is the part that counts.** Every episode comes with **koans**:
runnable exercises with a blank to fill in. Most also ask you to type and run the queries
yourself in CloudBeaver, against real tables with the kind of schema you actually meet at work
— customers, orders, products, employees, foreign keys and all — not three rows invented to
make the example tidy.

**3. Nothing to configure.** The database, the sample data and the koan runner all arrive with
the install, already wired together. No connection strings to debug, no data to import, no
account to make. You open the file, fill the blank, run it.

## What a koan looks like

Open the file, replace the `___`, run it. That is the whole loop.

```groovy
// 2) Same shape, a different table. FROM decides WHICH table you read — fill in the
//    one holding the customers. Keep the double quotes; the lesson explains why.
def "the FROM decides which table you count"() {
    expect:
    shouldReturn 25, '''
        SELECT count(*) FROM ___
    '''
}
```

```
$ ./zeus.sh koans learnsql series1 _00

  Forging 'series1 _00 Start Here'

      You mastered 'count every order in the table' — +1 awareness.
      'the FROM decides which table you count' has damaged your karma.

  You have not yet reached enlightenment ...
      it should return 25

  Please meditate on the following code:
      src/koans/groovy/datazeus/learnsql/series1/_00/StartHereKoans.groovy:44
      44:   SELECT count(*) FROM ___

      your path thus far  [#....]  1 of 5 koans
```

Fix it, run again, and the next one turns red with its own hint. Keep going until:

```
  You have reached enlightenment.
  Every koan is green - 5 of 5. Well done.
```

You are never guessing a number. The koan runs **your** query against the real database and
tells you what it returned versus what it should have — so you debug SQL, not the exercise.

## What is in this repo

| | |
|---|---|
| `courses/` | course roadmaps — the episode list, in order, with what each one owes you |
| `tests/src/koans/` | the koans, by course and episode |
| `tests/src/verify/` | the specs that prove every answer, on **DuckDB and PostgreSQL both** |
| `datasets/` | Northwind — a production-like schema with the data to match, shared by every course |
| `zeus.sh` / `zeus.bat` | the runner — `zeus koans <course> <series> <episode>`, and `zeus update` |

## The courses

| Course | What you learn |
|---|---|
| **[Learn SQL](https://datapallas.com/data-academy/learn-sql)** | Query one table, then join two — `SELECT` through window functions, on five different databases |
| **[Python for Data](https://datapallas.com/data-academy/learn-python)** | The half of the job SQL cannot do — read a file nobody cleaned, call an API, reshape it |
| **[Java & Groovy for Data](https://datapallas.com/data-academy/learn-java-groovy)** | The data half of the job, in the language your company already runs |
| **[Data Modeling](https://datapallas.com/data-academy/learn-data-modeling)** | Decide what the tables should be, before anyone writes a query against them |
| **[Data Model Patterns](https://datapallas.com/data-academy/learn-data-model-patterns)** | The patterns under almost every business schema — Party and roles, effective dating, order to cash |
| **[Schema Teardowns](https://datapallas.com/data-academy/learn-schema-teardowns)** | Read the schemas real teams actually shipped, and judge them against the patterns |
| **[ETL & Data Pipelines](https://datapallas.com/data-academy/learn-etl-pipelines)** | Get data from where it is to where it is useful — repeatedly, without losing or duplicating a row |
| **[Analytics Engineering with dbt](https://datapallas.com/data-academy/learn-dbt)** | Turn a folder of ad-hoc SELECTs into a version-controlled, tested, documented project |
| **[Data Warehousing](https://datapallas.com/data-academy/learn-data-warehousing)** | Build the place the questions get answered fast — star schemas, columnar engines, cubes |
| **[Data Ops](https://datapallas.com/data-academy/learn-data-ops)** | Keep it running — the terminal, PostgreSQL in production, and the job that dies at 3 a.m. |
| **[BI & Data Visualization](https://datapallas.com/data-academy/learn-bi)** | Pick the right chart, agree what a metric means, and build a dashboard people act on |
| **[AI for Data](https://datapallas.com/data-academy/learn-ai)** | Use AI on data you are responsible for — and be able to prove the answer is right |

**Hundreds of episodes planned across all courses.** Being straight with you about where
that stands: **Learn SQL Series 1 is publishing now** — the rest is written as roadmaps and is
being turned into lessons one at a time. Star the repo and you will see them as they land.

⭐ **Star this repo** so you don't miss new lessons.

## Get started

**[Install DataPallas, start CloudBeaver and run your first koans →](https://datapallas.com/learn-data)**

A few minutes, once. Then you have a real database on your own machine and every lesson is
ready to run.

## Links

- **The courses, written out** — https://datapallas.com/data-academy
- **Setup guide** — https://datapallas.com/learn-data
- **DataPallas** — everything you need to run the lessons, in one download:
  https://github.com/flowkraft/datapallas

Contributions welcome — see [CONTRIBUTING.md](CONTRIBUTING.md). Licensed under
[LICENSE](LICENSE).
