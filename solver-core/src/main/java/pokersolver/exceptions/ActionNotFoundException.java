package pokersolver.exceptions;

/** Signals an action label the tree format does not define. */
public class ActionNotFoundException extends SolverException {

    public ActionNotFoundException(String message) {
        super(message);
    }
}
