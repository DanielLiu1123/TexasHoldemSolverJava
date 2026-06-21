package pokersolver.compairer;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import me.tongfei.progressbar.ProgressBar;
import pokersolver.Card;
import pokersolver.exceptions.CardsNotFoundException;
import pokersolver.utils.LongIntHashMap;

/**
 * Hand evaluator backed by a 5-card rank dictionary: every 5-card combination maps to a rank
 * (smaller rank = stronger hand, 0 = royal flush). 6/7-card hands take the best rank over all
 * 5-card subsets.
 *
 * <p>Hands are encoded as 52-bit masks (bit i = card i), so a 5-card subset is the full mask with
 * two bits cleared and a lookup is a single probe into a primitive-keyed table — no boxing, no
 * allocation. This sits on the showdown hot path of CFR training.
 */
public class Dic5Compairer extends Compairer {

    private static final int MISSING = -1;

    private final LongIntHashMap cardslong2rank;

    public Dic5Compairer(String dicDir, int lines) throws IOException {
        this(dicDir, lines, true);
    }

    public Dic5Compairer(String dicDir, int lines, boolean verbose) throws IOException {
        super(dicDir, lines);
        this.cardslong2rank = load(dicDir, lines, verbose);
    }

    private static LongIntHashMap load(String dicDir, int lines, boolean verbose) throws IOException {
        LongIntHashMap map = new LongIntHashMap(lines);
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(dicDir), StandardCharsets.UTF_8);
                ProgressBar pb = verbose ? new ProgressBar("Dic5Compairer load", lines) : null) {
            String line;
            int ind = 0;
            while ((line = reader.readLine()) != null) {
                String[] linesp = line.trim().split(",", -1);
                String[] cards = linesp[0].split("-");
                assert (cards.length == 5);

                long key = Card.boardCards2long(cards);
                int rank = Integer.parseInt(linesp[1]);
                if (!map.put(key, rank)) {
                    throw new IllegalStateException(
                            String.format("duplicate dictionary entry: %s (key %d)", linesp[0], key));
                }
                ind++;
                if (ind % 100 == 0 && pb != null) pb.stepBy(100);
            }
        }
        return map;
    }

    /**
     * Best (minimal) rank over all 5-card subsets of the hand encoded in {@code handMask}. The
     * mask must have 5 to 7 bits set.
     */
    private int bestRank(long handMask) {
        int n = Long.bitCount(handMask);
        if (n == 5) {
            return requireRank(handMask);
        }
        if (n < 5 || n > 7) {
            throw new CardsNotFoundException(
                    String.format("expected 5 to 7 distinct cards, got %d (mask %d)", n, handMask));
        }
        int best = Integer.MAX_VALUE;
        if (n == 6) {
            long remaining = handMask;
            while (remaining != 0) {
                long dropped = Long.lowestOneBit(remaining);
                best = Math.min(best, requireRank(handMask ^ dropped));
                remaining ^= dropped;
            }
            return best;
        }
        // n == 7: drop every pair of cards.
        long outer = handMask;
        while (outer != 0) {
            long droppedFirst = Long.lowestOneBit(outer);
            outer ^= droppedFirst;
            long inner = outer; // only pairs ordered after droppedFirst, each pair visited once
            while (inner != 0) {
                long droppedSecond = Long.lowestOneBit(inner);
                best = Math.min(best, requireRank(handMask ^ droppedFirst ^ droppedSecond));
                inner ^= droppedSecond;
            }
        }
        return best;
    }

    private int requireRank(long fiveCardMask) {
        int rank = cardslong2rank.get(fiveCardMask, MISSING);
        if (rank == MISSING) {
            throw new CardsNotFoundException(String.format("no rank for 5-card mask %d", fiveCardMask));
        }
        return rank;
    }

    private static long mask(int[] cards) {
        long mask = 0;
        for (int card : cards) {
            if (card < 0 || card >= 52) {
                throw new CardsNotFoundException(String.format("card with id %d not found", card));
            }
            mask |= 1L << card;
        }
        if (Long.bitCount(mask) != cards.length) {
            throw new CardsNotFoundException("duplicate cards in hand");
        }
        return mask;
    }

    private static long mask(List<Card> cards) {
        int[] ints = new int[cards.size()];
        for (int i = 0; i < ints.length; i++) {
            ints[i] = Card.card2int(cards.get(i));
        }
        return mask(ints);
    }

    private static long disjointUnion(long former, long latter) {
        if ((former & latter) != 0) {
            throw new CardsNotFoundException("hand and board share cards");
        }
        return former | latter;
    }

    CompairResult compairRanks(int rankFormer, int rankLatter) {
        if (rankFormer < rankLatter) {
            // rank更小的牌更大，0是同花顺
            return CompairResult.LARGER;
        } else if (rankFormer > rankLatter) {
            return CompairResult.SMALLER;
        } else {
            return CompairResult.EQUAL;
        }
    }

    @Override
    public CompairResult compair(List<Card> privateFormer, List<Card> privateLatter, List<Card> publicBoard)
            throws CardsNotFoundException {
        assert (privateFormer.size() == 2);
        assert (privateLatter.size() == 2);
        assert (publicBoard.size() == 5);
        long board = mask(publicBoard);
        int rankFormer = bestRank(disjointUnion(mask(privateFormer), board));
        int rankLatter = bestRank(disjointUnion(mask(privateLatter), board));
        return compairRanks(rankFormer, rankLatter);
    }

    @Override
    public CompairResult compair(int[] privateFormer, int[] privateLatter, int[] publicBoard)
            throws CardsNotFoundException {
        assert (privateFormer.length == 2);
        assert (privateLatter.length == 2);
        assert (publicBoard.length == 5);
        long board = mask(publicBoard);
        int rankFormer = bestRank(disjointUnion(mask(privateFormer), board));
        int rankLatter = bestRank(disjointUnion(mask(privateLatter), board));
        return compairRanks(rankFormer, rankLatter);
    }

    @Override
    public int getRank(List<Card> privateHand, List<Card> publicBoard) {
        return bestRank(disjointUnion(mask(privateHand), mask(publicBoard)));
    }

    @Override
    public int getRank(int[] privateHand, int[] publicBoard) {
        return bestRank(disjointUnion(mask(privateHand), mask(publicBoard)));
    }

    @Override
    public int getRank(long privateHand, long publicBoard) {
        return bestRank(disjointUnion(privateHand, publicBoard));
    }
}
