package pokersolver.eval;

/**
 * The nine categories a five-card poker hand can fall into.
 *
 * <p>Declaration order carries no meaning: the categories' relative strength is variant-specific
 * (short-deck ranks a flush above a full house, and three of a kind above a straight). Each {@link
 * PokerVariant} declares its own ordering.
 */
public enum HandCategory {
    HIGH_CARD,
    ONE_PAIR,
    TWO_PAIR,
    THREE_OF_A_KIND,
    STRAIGHT,
    FLUSH,
    FULL_HOUSE,
    FOUR_OF_A_KIND,
    STRAIGHT_FLUSH
}
