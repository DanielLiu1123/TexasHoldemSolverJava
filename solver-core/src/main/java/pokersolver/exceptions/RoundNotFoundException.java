package pokersolver.exceptions;

/** Signals a betting round outside preflop..river. */
public class RoundNotFoundException extends SolverException {

    public RoundNotFoundException(String message) {
        super(message);
    }
}
