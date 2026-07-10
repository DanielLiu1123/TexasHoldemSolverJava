package pokersolver.exceptions;

/** Signals a tree node whose action and child counts disagree. */
public class NodeLengthMismatchException extends SolverException {

    public NodeLengthMismatchException(String message) {
        super(message);
    }
}
