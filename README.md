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

Learn SQL · Python for Data · Java & Groovy for Data · Data Modeling · Data Model Patterns ·
Schema Teardowns · ETL & Data Pipelines · Analytics Engineering with dbt · Data Warehousing ·
Data Ops · BI & Data Visualization · AI for Data

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
