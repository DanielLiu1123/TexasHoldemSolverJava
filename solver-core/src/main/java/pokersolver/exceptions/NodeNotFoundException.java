package pokersolver.exceptions;

/** Signals a game-tree node that does not exist. */
public class NodeNotFoundException extends SolverException {

    public NodeNotFoundException(String message) {
        super(message);
    }
}
