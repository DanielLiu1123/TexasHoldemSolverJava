package pokersolver.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import pokersolver.Card;

/**
 * Pins {@link HandEvaluator} to the hand-rank dictionaries the solver used to load at startup.
 *
 * <p>The evaluator now derives its tables from the rules of each variant, so nothing checks them
 * against reality unless something does it here. Both dictionaries enumerate every five-card hand
 * with its rank (1 = royal flush), and the generated tables must agree on <em>every single one</em>
 * — not merely induce the same ordering — because the rank numbering is part of the evaluator's
 * published contract.
 *
 * <p>The seven-card path is checked against the five-card path by brute force on sampled boards:
 * the rank of seven cards must equal the best rank over all 21 of their five-card subsets.
 */
class HandEvaluatorGoldenTest {

    private static final Path DICTIONARIES = Path.of("src/test/resources/compairer");

    @Test
    void holdemMatchesTheFiveCardDictionaryExactly() throws IOException {
        assertMatchesDictionary(PokerVariant.HOLDEM, "card5_dic_sorted.txt", 2598960, 7462);
    }

    @Test
    void shortDeckMatchesTheFiveCardDictionaryExactly() throws IOException {
        assertMatchesDictionary(PokerVariant.SHORT_DECK, "card5_dic_sorted_shortdeck.txt", 376992, 1404);
    }

    private static void assertMatchesDictionary(PokerVariant variant, String file, int expectedRows, int distinctRanks)
            throws IOException {
        HandEvaluator evaluator = HandEvaluator.forVariant(variant);
        assertThat(evaluator.distinctRanks()).isEqualTo(distinctRanks);

        int rows = 0;
        try (BufferedReader reader = Files.newBufferedReader(DICTIONARIES.resolve(file), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int comma = line.indexOf(',');
                long cards = 0;
                for (String card : line.substring(0, comma).split("-", -1)) {
                    cards |= 1L << Card.strCard2int(card);
                }
                int expected = Integer.parseInt(line.substring(comma + 1).trim());
                int actual = evaluator.rank(cards);
                if (actual != expected) {
                    throw new AssertionError(
                            "%s: %s ranked %d, dictionary says %d".formatted(variant, line, actual, expected));
                }
                rows++;
            }
        }
        assertThat(rows).as("%s dictionary rows", variant).isEqualTo(expectedRows);
    }

    @Test
    void sevenCardRankIsTheBestOfItsTwentyOneFiveCardSubsets() {
        HandEvaluator evaluator = HandEvaluator.forVariant(PokerVariant.HOLDEM);
        // A deterministic sweep over structurally distinct seven-card hands: every board of five
        // consecutive-by-stride cards, against every hole-card pair the board leaves free.
        for (int stride = 1; stride <= 11; stride += 2) {
            for (int start = 0; start < 52; start++) {
                long board = 0;
                for (int i = 0; i < 5; i++) board |= 1L << ((start + i * stride) % 52);
                if (Long.bitCount(board) != 5) continue;
                for (int a = 0; a < 52; a++) {
                    if ((board & (1L << a)) != 0) continue;
                    for (int b = a + 1; b < 52; b++) {
                        if ((board & (1L << b)) != 0) continue;
                        long hand = board | (1L << a) | (1L << b);
                        assertThat(evaluator.rank(hand))
                                .as("seven-card rank of mask %d", hand)
                                .isEqualTo(bestFiveCardSubsetRank(evaluator, hand));
                    }
                }
            }
        }
    }

    /** The strongest (smallest) rank over all five-card subsets, found by dropping every pair. */
    private static int bestFiveCardSubsetRank(HandEvaluator evaluator, long sevenCards) {
        int best = Integer.MAX_VALUE;
        long outer = sevenCards;
        while (outer != 0) {
            long first = Long.lowestOneBit(outer);
            outer ^= first;
            long inner = outer;
            while (inner != 0) {
                long second = Long.lowestOneBit(inner);
                inner ^= second;
                best = Math.min(best, evaluator.rank(sevenCards ^ first ^ second));
            }
        }
        return best;
    }

    @Test
    void sixCardRankIsTheBestOfItsSixFiveCardSubsets() {
        HandEvaluator evaluator = HandEvaluator.forVariant(PokerVariant.SHORT_DECK);
        int[] deck = shortDeckCards();
        for (int i = 0; i < deck.length; i++) {
            long six = 0;
            for (int j = 0; j < 6; j++) six |= 1L << deck[(i + j * 5) % deck.length];
            if (Long.bitCount(six) != 6) continue;
            int best = Integer.MAX_VALUE;
            long remaining = six;
            while (remaining != 0) {
                long dropped = Long.lowestOneBit(remaining);
                remaining ^= dropped;
                best = Math.min(best, evaluator.rank(six ^ dropped));
            }
            assertThat(evaluator.rank(six)).as("six-card rank of mask %d", six).isEqualTo(best);
        }
    }

    @Test
    void shortDeckRanksFlushesAboveFullHousesAndTripsAboveStraights() {
        HandEvaluator shortDeck = HandEvaluator.forVariant(PokerVariant.SHORT_DECK);
        HandEvaluator holdem = HandEvaluator.forVariant(PokerVariant.HOLDEM);

        long flush = cards("6c", "7c", "8c", "9c", "Jc");
        long fullHouse = cards("Ac", "Ad", "Ah", "Kc", "Kd");
        long trips = cards("Ac", "Ad", "Ah", "Kc", "6d");
        long straight = cards("6c", "7d", "8h", "9s", "Tc");

        assertThat(shortDeck.rank(flush))
                .as("short-deck: weakest flush beats the best full house")
                .isLessThan(shortDeck.rank(fullHouse));
        assertThat(shortDeck.rank(trips))
                .as("short-deck: trip aces beat a straight")
                .isLessThan(shortDeck.rank(straight));

        assertThat(holdem.rank(flush)).as("hold'em: the full house wins").isGreaterThan(holdem.rank(fullHouse));
        assertThat(holdem.rank(trips)).as("hold'em: the straight wins").isGreaterThan(holdem.rank(straight));
    }

    @Test
    void theWheelIsTheWeakestStraightInBothVariants() {
        HandEvaluator holdem = HandEvaluator.forVariant(PokerVariant.HOLDEM);
        assertThat(holdem.rank(cards("Ac", "2d", "3h", "4s", "5c")))
                .as("hold'em wheel A-2-3-4-5 is weaker than 2-3-4-5-6")
                .isGreaterThan(holdem.rank(cards("2c", "3d", "4h", "5s", "6c")));

        HandEvaluator shortDeck = HandEvaluator.forVariant(PokerVariant.SHORT_DECK);
        assertThat(shortDeck.rank(cards("Ac", "6d", "7h", "8s", "9c")))
                .as("short-deck wheel A-6-7-8-9 is weaker than 6-7-8-9-T")
                .isGreaterThan(shortDeck.rank(cards("6c", "7d", "8h", "9s", "Tc")));
    }

    @Test
    void royalFlushIsRankOneAndTiesAcrossSuits() {
        HandEvaluator holdem = HandEvaluator.forVariant(PokerVariant.HOLDEM);
        assertThat(holdem.rank(cards("As", "Ks", "Qs", "Js", "Ts"))).isEqualTo(1);
        assertThat(holdem.rank(cards("Ah", "Kh", "Qh", "Jh", "Th"))).isEqualTo(1);
        assertThat(holdem.compare(cards("2c", "3d"), cards("4c", "5d"), cards("As", "Ks", "Qs", "Js", "Ts")))
                .as("a royal flush on the board: both players play it and tie")
                .isZero();
    }

    @Test
    void compareOrdersHandsByStrengthNotByRank() {
        HandEvaluator holdem = HandEvaluator.forVariant(PokerVariant.HOLDEM);
        long board = cards("Qs", "Jh", "2h", "2d", "6c");
        assertThat(holdem.compare(cards("Ac", "Ad"), cards("Kc", "Kd"), board)).isPositive();
        assertThat(holdem.compare(cards("Kc", "Kd"), cards("Ac", "Ad"), board)).isNegative();
        assertThat(holdem.compare(cards("Ac", "Ad"), cards("Ah", "As"), board)).isZero();
    }

    @Test
    void tablesAreSharedPerVariant() {
        assertThat(HandEvaluator.forVariant(PokerVariant.HOLDEM))
                .isSameAs(HandEvaluator.forVariant(PokerVariant.HOLDEM));
        assertThat(HandEvaluator.forVariant(PokerVariant.HOLDEM))
                .isNotSameAs(HandEvaluator.forVariant(PokerVariant.SHORT_DECK));
    }

    private static long cards(String... names) {
        long mask = 0;
        for (String name : names) mask |= 1L << Card.strCard2int(name);
        return mask;
    }

    private static int[] shortDeckCards() {
        int[] deck = new int[36];
        int size = 0;
        for (int rank = 6; rank <= 14; rank++) {
            for (String suit : Card.getSuits()) {
                deck[size++] = Card.strCard2int(Objects.requireNonNull(rankLabel(rank)) + suit);
            }
        }
        return deck;
    }

    private static String rankLabel(int rank) {
        return switch (rank) {
            case 10 -> "T";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            case 14 -> "A";
            default -> String.valueOf(rank);
        };
    }
}
