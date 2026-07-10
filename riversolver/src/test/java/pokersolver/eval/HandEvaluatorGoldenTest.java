package pokersolver.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import pokersolver.Card;

/**
 * Pins {@link HandEvaluator} to the hand-rank dictionary the solver used to load at startup.
 *
 * <p>The evaluator now derives its tables from the rules of the game, so nothing checks them against
 * reality unless something does it here. The dictionary enumerates every five-card hand with its rank
 * (1 = royal flush), and the generated tables must agree on <em>every single one</em> — not merely
 * induce the same ordering — because the rank numbering is part of the evaluator's published
 * contract.
 *
 * <p>The six- and seven-card paths are checked against the five-card path by brute force: the rank of
 * N cards must equal the best rank over all of their five-card subsets.
 */
class HandEvaluatorGoldenTest {

    private static final Path DICTIONARY = Path.of("src/test/resources/compairer/card5_dic_sorted.txt");

    @Test
    void everyFiveCardHandMatchesTheDictionaryExactly() throws IOException {
        assertThat(HandEvaluator.DISTINCT_RANKS).isEqualTo(7462);

        int rows = 0;
        try (BufferedReader reader = Files.newBufferedReader(DICTIONARY, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int comma = line.indexOf(',');
                long cards = 0;
                for (String card : line.substring(0, comma).split("-", -1)) {
                    cards |= 1L << Card.strCard2int(card);
                }
                int expected = Integer.parseInt(line.substring(comma + 1).trim());
                int actual = HandEvaluator.rank(cards);
                if (actual != expected) {
                    throw new AssertionError("%s ranked %d, dictionary says %d".formatted(line, actual, expected));
                }
                rows++;
            }
        }
        assertThat(rows).as("dictionary rows").isEqualTo(2598960);
    }

    @Test
    void sevenCardRankIsTheBestOfItsTwentyOneFiveCardSubsets() {
        // A deterministic sweep over structurally distinct seven-card hands: every board of five
        // stride-separated cards, against every hole-card pair the board leaves free.
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
                        assertThat(HandEvaluator.rank(hand))
                                .as("seven-card rank of mask %d", hand)
                                .isEqualTo(bestSubsetRank(hand, 2));
                    }
                }
            }
        }
    }

    @Test
    void sixCardRankIsTheBestOfItsSixFiveCardSubsets() {
        for (int start = 0; start < 52; start++) {
            for (int stride = 1; stride <= 9; stride += 2) {
                long six = 0;
                for (int i = 0; i < 6; i++) six |= 1L << ((start + i * stride) % 52);
                if (Long.bitCount(six) != 6) continue;
                assertThat(HandEvaluator.rank(six))
                        .as("six-card rank of mask %d", six)
                        .isEqualTo(bestSubsetRank(six, 1));
            }
        }
    }

    /** The strongest (smallest) rank over all subsets that drop exactly {@code drop} cards. */
    private static int bestSubsetRank(long cards, int drop) {
        if (drop == 0) return HandEvaluator.rank(cards);
        int best = Integer.MAX_VALUE;
        long remaining = cards;
        while (remaining != 0) {
            long dropped = Long.lowestOneBit(remaining);
            remaining ^= dropped;
            best = Math.min(best, bestSubsetRank(cards ^ dropped, drop - 1));
        }
        return best;
    }

    @Test
    void categoriesAreOrderedTheWayHoldemPlaysThem() {
        int[] weakestFirst = {
            HandEvaluator.rank(cards("Ac", "Kd", "Qh", "Js", "9c")), // high card
            HandEvaluator.rank(cards("Ac", "Ad", "Kc", "Qd", "7d")), // one pair
            HandEvaluator.rank(cards("Ac", "Ad", "Kc", "Kd", "7d")), // two pair
            HandEvaluator.rank(cards("Ac", "Ad", "Ah", "Kc", "7d")), // trips
            HandEvaluator.rank(cards("9c", "Td", "Jh", "Qs", "Kc")), // straight
            HandEvaluator.rank(cards("2c", "5c", "8c", "Jc", "Kc")), // flush
            HandEvaluator.rank(cards("Ac", "Ad", "Ah", "Kc", "Kd")), // full house
            HandEvaluator.rank(cards("9c", "9d", "9h", "9s", "Kd")), // quads
            HandEvaluator.rank(cards("9d", "Td", "Jd", "Qd", "Kd")), // straight flush
        };
        for (int i = 1; i < weakestFirst.length; i++) {
            assertThat(weakestFirst[i])
                    .as("category %d outranks category %d", i, i - 1)
                    .isLessThan(weakestFirst[i - 1]);
        }
    }

    @Test
    void theWheelIsTheWeakestStraight() {
        assertThat(HandEvaluator.rank(cards("Ac", "2d", "3h", "4s", "5c")))
                .as("A-2-3-4-5 is weaker than 2-3-4-5-6")
                .isGreaterThan(HandEvaluator.rank(cards("2c", "3d", "4h", "5s", "6c")));
        assertThat(HandEvaluator.rank(cards("Ac", "2c", "3c", "4c", "5c")))
                .as("the steel wheel is the weakest straight flush")
                .isGreaterThan(HandEvaluator.rank(cards("2c", "3c", "4c", "5c", "6c")));
    }

    @Test
    void royalFlushIsRankOneAndTiesAcrossSuits() {
        assertThat(HandEvaluator.rank(cards("As", "Ks", "Qs", "Js", "Ts"))).isEqualTo(1);
        assertThat(HandEvaluator.rank(cards("Ah", "Kh", "Qh", "Jh", "Th"))).isEqualTo(1);
        assertThat(HandEvaluator.compare(cards("2c", "3d"), cards("4c", "5d"), cards("As", "Ks", "Qs", "Js", "Ts")))
                .as("a royal flush on the board: both players play it and tie")
                .isZero();
    }

    @Test
    void theWeakestHandIsSevenHigh() {
        assertThat(HandEvaluator.rank(cards("7c", "5d", "4h", "3s", "2c"))).isEqualTo(7462);
    }

    @Test
    void compareOrdersHandsByStrengthNotByRank() {
        long board = cards("Qs", "Jh", "2h", "2d", "6c");
        assertThat(HandEvaluator.compare(cards("Ac", "Ad"), cards("Kc", "Kd"), board))
                .isPositive();
        assertThat(HandEvaluator.compare(cards("Kc", "Kd"), cards("Ac", "Ad"), board))
                .isNegative();
        assertThat(HandEvaluator.compare(cards("Ac", "Ad"), cards("Ah", "As"), board))
                .isZero();
    }

    private static long cards(String... names) {
        long mask = 0;
        for (String name : names) mask |= 1L << Card.strCard2int(name);
        return mask;
    }
}
