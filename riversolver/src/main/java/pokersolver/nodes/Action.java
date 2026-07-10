package pokersolver.nodes;

/**
 * An edge out of an {@link ActionNode}: what a player may do, and for how much.
 *
 * <p>Only {@link Bet} and {@link Raise} carry an amount, so the type system says so — the amount
 * used to be a nullable {@code Double} guarded by an {@code assert}.
 *
 * <p>{@link #label()} is the wire format: it names the edge in the strategy JSON, in the API's
 * {@code ?path=} navigation, and in the game-tree files. Keep it stable.
 */
public sealed interface Action {

    /** The action's kind, as the tree files and strategy JSON spell it. */
    ActionType type();

    /** The chips this action puts in, or {@code 0} for the actions that put in nothing. */
    double amount();

    /** The stable external name of this edge, e.g. {@code "CHECK"} or {@code "BET 10.0"}. */
    String label();

    enum ActionType {
        BEGIN,
        ROUNDBEGIN,
        BET,
        RAISE,
        CHECK,
        FOLD,
        CALL
    }

    record Check() implements Action {
        @Override
        public ActionType type() {
            return ActionType.CHECK;
        }

        @Override
        public double amount() {
            return 0;
        }

        @Override
        public String label() {
            return "CHECK";
        }

        @Override
        public String toString() {
            return label();
        }
    }

    record Fold() implements Action {
        @Override
        public ActionType type() {
            return ActionType.FOLD;
        }

        @Override
        public double amount() {
            return 0;
        }

        @Override
        public String label() {
            return "FOLD";
        }

        @Override
        public String toString() {
            return label();
        }
    }

    record Call() implements Action {
        @Override
        public ActionType type() {
            return ActionType.CALL;
        }

        @Override
        public double amount() {
            return 0;
        }

        @Override
        public String label() {
            return "CALL";
        }

        @Override
        public String toString() {
            return label();
        }
    }

    record Bet(double amount) implements Action {
        @Override
        public ActionType type() {
            return ActionType.BET;
        }

        @Override
        public String label() {
            return "BET " + amount;
        }

        @Override
        public String toString() {
            return label();
        }
    }

    record Raise(double amount) implements Action {
        @Override
        public ActionType type() {
            return ActionType.RAISE;
        }

        @Override
        public String label() {
            return "RAISE " + amount;
        }

        @Override
        public String toString() {
            return label();
        }
    }

    Action CHECK = new Check();
    Action FOLD = new Fold();
    Action CALL = new Call();
}
