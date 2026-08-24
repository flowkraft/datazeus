package datazeus.datamodeling.series1._25

import datazeus._internal.SchemaKoanBase
import spock.lang.Stepwise

/**
 * ╔════════════════════════════════════════════════════════════════════════╗
 * ║  SCHEMA KOANS — Data Modeling · Series 1 · 25 Normalization: 1NF, 2NF, 3NF
 * ╚════════════════════════════════════════════════════════════════════════╝
 *
 * BOILERPLATE — no koans written yet. The brief is below; delete it as you write them.
 *
 *     zeus.bat koans datamodeling series1 _25     (Windows)
 *     ./zeus.sh koans datamodeling series1 _25    (macOS/Linux)
 *
 * ── WHAT A MODELING KOAN IS, AND IS NOT ──────────────────────────────────
 * A SQL koan blanks a token inside a query and checks the RESULT. A modeling koan is the
 * other way round: the ORACLE is what the schema refuses, and what real data does when you
 * try to load it into it.
 *
 * Do NOT ask the learner to type CREATE TABLE here. If a koan says "write the schema", their
 * effort goes into DDL syntax, which is not the subject. Only the project rung authors a
 * whole model. See SchemaKoanBase for the five rungs and the helper each one uses.
 *
 * THIS LESSON'S RUNGS: koan:diagnose, koan:choose
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ───────────────────────────────────────
 * GOAL: remove the redundancy that makes an UPDATE go half-wrong.
 * MODELS: 1NF/2NF/3NF, functional dependency, update/insert/delete anomalies.
 *
 * Now BEFORE data types, so Series 1 walks the conceptual->logical->physical layering that
 * episode 00 set up instead of zigzagging across it.
 * LET THE KOANS CARRY THE ARGUMENT. A suite where the un-normalized version cannot answer
 * question 4 without lying teaches 3NF better than 1NF/2NF/3NF stated as rules. This is the
 * most academic topic in the track and the easiest place to lose the pragmatic framing:
 * the job is '3NF-ish, then denormalize where it hurts', and S2/35 and S2/40 finish that
 * sentence. Do not finish it here.
 *
 * ── THE RULE THAT KEEPS THESE HONEST ─────────────────────────────────────
 * Pair every shouldReject with a shouldAccept. A learner who only ever sees rejections
 * learns to bolt constraints onto everything and call it rigour; the pincer — this must
 * bounce, AND this must still get through — is the judgement actually being taught.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class Normalization1nf3nfKoans extends SchemaKoanBase {

    // TODO: koans, in the same order as the lesson's beats.
    //
    // Sketches for the rungs this lesson uses (koan:diagnose, koan:choose):
    //
    //   predict   the schema is GIVEN; they decide what the database will do
    //     def "the same product can't be on one order twice"() {
    //         expect: attempting("INSERT INTO order_line VALUES (10248, 42, 5)") == ___
    //     }
    //
    //   diagnose  they name the thing, they don't build it
    //     def "what is one row of Order Details?"() {
    //         expect: grainOf('"Order Details"') == ___          // ['OrderID','ProductID']
    //     }
    //     def "can this model say how many COPIES of Dune we own?"() {
    //         expect: canAnswer("...") == ___                    // false — and that IS the lesson
    //     }
    //
    //   choose    three finished models, given; which one survives?
    //     def "which model still knows what we charged last March?"() {
    //         expect: modelThatSurvives("the price changed on 2026-03-15") == ___   // 'B'
    //     }
    //
    //   complete  boilerplate given, ONE blanked line — the decision itself
    //     def "the same product can't appear twice on one order"() {
    //         expect:
    //         ddlMakesImpossible('''
    //             CREATE TABLE practice.order_line (
    //               "OrderID"   INTEGER NOT NULL,
    //               "ProductID" INTEGER NOT NULL,
    //               "Quantity"  INTEGER NOT NULL CHECK ("Quantity" > 0),
    //               ___                                  -- one line: the grain of a junction
    //             )
    //         ''', 'INSERT INTO practice.order_line VALUES (10248, 42, 5)')
    //     }
    //
    //   author    the whole model — projects only
    //     def "your schema.sql holds the real Northwind rows"() {
    //         expect: loadsRealData('"Order Details"') == ___
    //     }
    //
    //   migration the change is applied and EVERY earlier koan must still pass
    //   reconcile the star's totals must equal the OLTP totals
}
