package pokersolver.api;

import java.util.List;
import pokersolver.Deck;

/**
 * The supported poker variants and the decks they are played with.
 *
 * <p>Each deck is built once and shared: it is immutable, and it carries the hand evaluator, whose
 * lookup tables cost a few hundred kilobytes to derive. This used to be a lazily-populated cache
 * around a 2.6M-entry dictionary file read from disk.
 */
public enum GameType {
    HOLDEM("holdem", List.of("A", "K", "Q", "J", "T", "9", "8", "7", "6", "5", "4", "3", "2")),
    SHORTDECK("shortdeck", List.of("A", "K", "Q", "J", "T", "9", "8", "7", "6"));

    private final String id;
    private final Deck deck;

    GameType(String id, List<String> ranks) {
        this.id = id;
        this.deck = new Deck(ranks, List.of("h", "s", "d", "c"));
    }

    public String id() {
        return id;
    }

    /** This variant's deck, and through it, its hand evaluator. */
    public Deck deck() {
        return deck;
    }

    public static GameType fromId(String id) {
        for (GameType type : values()) {
            if (type.id.equals(id)) return type;
        }
        throw new IllegalArgumentException(String.format("game type not found: %s (expected holdem|shortdeck)", id));
    }
}
