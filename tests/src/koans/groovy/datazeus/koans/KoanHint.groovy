package datazeus.koans

/**
 * A koan failure whose message IS the learner-facing hint (a goal-aware "compare"
 * line), not a stacktrace. KoanBase.shouldReturn throws this; PathToEnlightenment
 * prints its message verbatim under the koan.
 */
class KoanHint extends AssertionError {
    KoanHint(String message) { super((Object) message) }
}
