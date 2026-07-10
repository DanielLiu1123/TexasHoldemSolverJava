package pokersolver.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import pokersolver.Card;
import pokersolver.ranges.PrivateCards;

/**
 * Parses range notation into the concrete two-card combos it stands for.
 *
 * <p>The grammar is the usual one: {@code "AA"} means every pair of aces, {@code "AKs"} the four
 * suited combos, {@code "AKo"} the twelve offsuit ones, {@code "AK"} all sixteen. Any entry may
 * carry a weight — {@code "KQs:0.5"} — and entries are comma-separated. Combos the board already
 * blocks are dropped.
 */
public final class PrivateRangeConverter {

    private PrivateRangeConverter() {}

    public static PrivateCards[] rangeStr2Cards(String rangeStr, int[] initialBoard) {
        long board = initialBoard.length == 0 ? 0 : Card.boardInts2long(initialBoard);
        List<PrivateCards> combos = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();

        for (String entry : rangeStr.split(",", -1)) {
            String[] parts = entry.split(":", -1);
            if (parts.length > 2) throw new IllegalArgumentException("more than one ':' in range entry: " + entry);

            float weight = parts.length == 2 ? Float.parseFloat(parts[1]) : 1;
            if (weight == 0) continue;

            for (PrivateCards combo : expand(parts[0], board)) {
                if (!seen.add(combo.hashCode())) {
                    throw new IllegalArgumentException("duplicate combo in range: " + combo);
                }
                combos.add(new PrivateCards(combo.card1, combo.card2, weight));
            }
        }
        return combos.toArray(new PrivateCards[0]);
    }

    /** The combos one range entry stands for, minus those the board blocks. */
    private static List<PrivateCards> expand(String entry, long board) {
        if (entry.length() != 2 && entry.length() != 3) {
            throw new IllegalArgumentException("not a valid range entry: " + entry);
        }
        char rank1 = entry.charAt(0);
        char rank2 = entry.charAt(1);
        Character suitedness = entry.length() == 3 ? entry.charAt(2) : null;

        if (suitedness != null && suitedness != 's' && suitedness != 'o') {
            throw new IllegalArgumentException("not a valid range entry: " + entry);
        }
        if (suitedness != null && suitedness == 's' && rank1 == rank2) {
            throw new IllegalArgumentException("%c%cs is not a valid combo".formatted(rank1, rank2));
        }

        String[] suits = Card.getSuits();
        List<PrivateCards> combos = new ArrayList<>();

        if (suitedness != null && suitedness == 's') {
            for (String suit : suits) add(combos, rank1 + suit, rank2 + suit, board);
            return combos;
        }

        // A pair's two cards are interchangeable, so the second suit starts at the first to avoid
        // emitting each combo twice — and it can never match the first, which would be one card.
        // "AKo" excludes matching suits by definition; bare "AK" is every combo, suited included.
        boolean pair = rank1 == rank2;
        boolean excludeSameSuit = pair || suitedness != null;
        for (int i = 0; i < suits.length; i++) {
            for (int j = pair ? i : 0; j < suits.length; j++) {
                if (excludeSameSuit && i == j) continue;
                add(combos, rank1 + suits[i], rank2 + suits[j], board);
            }
        }
        return combos;
    }

    private static void add(List<PrivateCards> combos, String card1, String card2, long board) {
        PrivateCards combo = new PrivateCards(Card.strCard2int(card1), Card.strCard2int(card2), 1);
        if (!Card.boardsHasIntercept(combo.mask(), board)) combos.add(combo);
    }
}
