package pokersolver.exceptions;

/**
 * The root of the solver's exception hierarchy: an input the solver cannot make sense of.
 *
 * <p>All of these are unchecked. They report programming errors and malformed input — a card that
 * does not exist, a board of the wrong length, a game-tree file naming an unknown node type — none
 * of which a caller can meaningfully recover from mid-solve.
 */
public class SolverException extends RuntimeException {

    public SolverException(String message) {
        super(message);
    }
}
