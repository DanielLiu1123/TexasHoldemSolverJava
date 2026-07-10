package pokersolver.exceptions;

/** Signals a card, rank or suit does not exist. */
public class CardsNotFoundException extends SolverException {

    public CardsNotFoundException(String message) {
        super(message);
    }
}
