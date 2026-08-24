package datazeus._internal

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement

/**
 * A JDBC Connection that counts how many times you actually went to the database.
 *
 * ── WHY A COUNTER AND NOT A STOPWATCH ────────────────────────────────────
 * 2/30 is "Batches and Transactions — 100,000 Inserts That Finish". The naive one-row-at-a-
 * time loop and the batched version both end up with 100,000 correct rows, so an assertion
 * on correctness cannot tell them apart. The obvious next move is to time them — and that is
 * the trap. Timing assertions are machine-dependent, flaky in CI, and get deleted by whoever
 * is on the receiving end of the flake.
 *
 * The round-trip count is deterministic on every machine, and it is not a proxy for the
 * lesson: it IS the lesson. "Your version asked the database 100,000 times. Batching asks
 * 100." Nothing about that sentence needs a clock.
 *
 * ── WHY A DYNAMIC PROXY ──────────────────────────────────────────────────
 * A simple wrapper class would only see calls made on the Connection. Learners write
 * PreparedStatement.addBatch()/executeBatch(), and a Statement obtained from an unproxied
 * Connection would execute invisibly — the koan would go green for the wrong reason, which
 * is worse than going red.
 *
 * So createStatement/prepareStatement/prepareCall return PROXIED statements, and every
 * execute* on them increments the same counter. `batches` is tracked separately because
 * "one executeBatch of 1,000 rows" is one round trip, and a learner should be able to see
 * that distinction rather than infer it.
 *
 * Read-only by design in spirit: this counts, it never rewrites SQL or swallows anything.
 * If a koan ever needs to intercept the SQL text itself, add it here rather than in a
 * lesson's own file, so every koan counts the same way.
 */
class CountingConnection {

    /** Every execute*() that crossed the wire, on the Connection or any Statement from it. */
    int executions = 0

    /** Of those, how many were executeBatch(). One batch is one trip, whatever its size. */
    int batches = 0

    /** Rows handed to addBatch() — so a koan can prove the data really was all there. */
    int rowsBatched = 0

    private final Connection real

    CountingConnection(Connection real) { this.real = real }

    /** The proxied Connection to hand to the learner's code. */
    Connection getConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.classLoader, [Connection] as Class[], new ConnHandler())
    }

    /** Convenience so a koan can write `counting(...).sql` for groovy.sql.Sql-flavoured koans. */
    groovy.sql.Sql getSql() { new groovy.sql.Sql(getConnection()) }

    void reset() { executions = 0; batches = 0; rowsBatched = 0 }

    @Override
    String toString() { "round trips: ${executions} (${batches} batches, ${rowsBatched} rows added)" }

    private class ConnHandler implements InvocationHandler {
        @Override
        Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
            def result = m.invoke(real, args)
            if (m.name in ["createStatement", "prepareStatement", "prepareCall"]) {
                // Wrap whatever came back so its executions are counted too. Both interfaces
                // are advertised: a PreparedStatement IS-A Statement, and callers may hold
                // either reference.
                def ifaces = (result instanceof PreparedStatement
                        ? [PreparedStatement, Statement] : [Statement]) as Class[]
                return Proxy.newProxyInstance(Statement.classLoader, ifaces, new StmtHandler(result))
            }
            if (m.name.startsWith("execute")) executions++
            return result
        }
    }

    private class StmtHandler implements InvocationHandler {
        private final Object realStmt
        StmtHandler(Object realStmt) { this.realStmt = realStmt }

        @Override
        Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
            if (m.name == "addBatch") rowsBatched++
            if (m.name.startsWith("execute")) {
                executions++
                if (m.name == "executeBatch") batches++
            }
            return m.invoke(realStmt, args)
        }
    }
}
