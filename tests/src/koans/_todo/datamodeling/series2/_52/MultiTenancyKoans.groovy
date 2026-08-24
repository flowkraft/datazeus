package datazeus.datamodeling.series2._52

import datazeus._internal.SchemaKoanBase
import spock.lang.Stepwise

/**
 * ╔════════════════════════════════════════════════════════════════════════╗
 * ║  SCHEMA KOANS — Data Modeling · Series 2 · 52 One Database or a Thousand
 * ╚════════════════════════════════════════════════════════════════════════╝
 *
 * BOILERPLATE — no koans written yet. The brief is below; delete it as you write them.
 *
 *     zeus.bat koans datamodeling series2 _52     (Windows)
 *     ./zeus.sh koans datamodeling series2 _52    (macOS/Linux)
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
 * THIS LESSON'S RUNGS: koan:choose
 *
 * ── WHY THIS EPISODE, SPECIFICALLY ───────────────────────────────────────
 * GOAL: model for more than one customer.
 * MODELS: shared schema + tenant_id, schema per tenant, database per tenant.
 *
 * The one modeling decision made once that is nearly impossible to reverse - which is why
 * it sits beside schema-evolution: 50 is the change you CAN make additively, this is the
 * one you cannot.
 *   shared schema + tenant_id   cheapest, one migration for all, and every query is one
 *                               forgotten WHERE away from a data breach
 *   schema per tenant           real isolation, migrations multiply by tenant count
 *   database per tenant         strongest isolation, biggest operational bill
 * ON-BRAND, so teach it from the inside: DataPallas and ReportBurster both live this. The
 * interesting part is NOT the three options - every blog post has those - it is that the
 * choice is driven by who your customers are (one enterprise vs ten thousand self-serve)
 * and what your compliance story must survive, not by what is cheapest to build.
 * Boundary: Data Ops owns running and migrating whichever one you picked. This picks it.
 *
 * ── THE RULE THAT KEEPS THESE HONEST ─────────────────────────────────────
 * Pair every shouldReject with a shouldAccept. A learner who only ever sees rejections
 * learns to bolt constraints onto everything and call it rigour; the pincer — this must
 * bounce, AND this must still get through — is the judgement actually being taught.
 */
@Stepwise // walk the koans in order — once one fails, the rest wait
class MultiTenancyKoans extends SchemaKoanBase {

    // TODO: koans, in the same order as the lesson's beats.
    //
    // Sketches for the rungs this lesson uses (koan:choose):
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
