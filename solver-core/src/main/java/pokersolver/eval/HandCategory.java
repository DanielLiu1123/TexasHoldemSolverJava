package pokersolver.eval;

/** The nine categories a five-card poker hand can fall into, weakest first. */
public enum HandCategory {
    HIGH_CARD(0),
    ONE_PAIR(1),
    TWO_PAIR(2),
    THREE_OF_A_KIND(3),
    STRAIGHT(4),
    FLUSH(5),
    FULL_HOUSE(6),
    FOUR_OF_A_KIND(7),
    STRAIGHT_FLUSH(8);

    private final int strength;

    HandCategory(int strength) {
        this.strength = strength;
    }

    /** How many categories this one outranks. Higher is stronger. */
    public int strength() {
        return strength;
    }
}
