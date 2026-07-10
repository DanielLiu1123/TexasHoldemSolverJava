package pokersolver.exceptions;

/** Signals a board mask holds the wrong number of cards. */
public class BoardNotFoundException extends SolverException {

    public BoardNotFoundException(String message) {
        super(message);
    }
}
