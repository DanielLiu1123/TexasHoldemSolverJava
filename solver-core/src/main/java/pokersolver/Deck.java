package pokersolver;

import java.util.List;

/**
 * The standard 52-card deck, in card-id order: {@code cards().get(i).getCardInt() == i}.
 *
 * <p>A chance node's children are index-aligned with this list, so the card dealt down edge {@code i}
 * is {@code card(i)}. The deck used to be constructed from a YAML rank/suit list so short-deck could
 * supply a 36-card one; there is one deck now, and it is a constant.
 */
public final class Deck {

    public static final int SIZE = Card.DECK_SIZE;

    private static final List<Card> CARDS = buildCards();

    private Deck() {}

    private static List<Card> buildCards() {
        Card[] cards = new Card[SIZE];
        for (int id = 0; id < SIZE; id++) cards[id] = new Card(Card.intCard2Str(id));
        return List.of(cards);
    }

    /** All 52 cards, ordered by card id. */
    public static List<Card> cards() {
        return CARDS;
    }

    /** The card with id {@code cardInt}, in {@code [0, 52)}. */
    public static Card card(int cardInt) {
        return CARDS.get(cardInt);
    }
}
