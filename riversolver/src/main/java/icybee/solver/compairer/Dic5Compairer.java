package icybee.solver.compairer;

import icybee.solver.Card;
import icybee.solver.exceptions.CardsNotFoundException;
import icybee.solver.utils.LongIntHashMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import me.tongfei.progressbar.ProgressBar;

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

    public Dic5Compairer(String dic_dir, int lines) throws IOException {
        this(dic_dir, lines, true);
    }

    public Dic5Compairer(String dic_dir, int lines, boolean verbose) throws IOException {
        super(dic_dir, lines);
        this.cardslong2rank = load(dic_dir, lines, verbose);
    }

    private static LongIntHashMap load(String dic_dir, int lines, boolean verbose) throws IOException {
        LongIntHashMap map = new LongIntHashMap(lines);
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(dic_dir), StandardCharsets.UTF_8);
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

    CompairResult compairRanks(int rank_former, int rank_latter) {
        if (rank_former < rank_latter) {
            // rank更小的牌更大，0是同花顺
            return CompairResult.LARGER;
        } else if (rank_former > rank_latter) {
            return CompairResult.SMALLER;
        } else {
            return CompairResult.EQUAL;
        }
    }

    @Override
    public CompairResult compair(List<Card> private_former, List<Card> private_latter, List<Card> public_board)
            throws CardsNotFoundException {
        assert (private_former.size() == 2);
        assert (private_latter.size() == 2);
        assert (public_board.size() == 5);
        long board = mask(public_board);
        int rank_former = bestRank(disjointUnion(mask(private_former), board));
        int rank_latter = bestRank(disjointUnion(mask(private_latter), board));
        return compairRanks(rank_former, rank_latter);
    }

    @Override
    public CompairResult compair(int[] private_former, int[] private_latter, int[] public_board)
            throws CardsNotFoundException {
        assert (private_former.length == 2);
        assert (private_latter.length == 2);
        assert (public_board.length == 5);
        long board = mask(public_board);
        int rank_former = bestRank(disjointUnion(mask(private_former), board));
        int rank_latter = bestRank(disjointUnion(mask(private_latter), board));
        return compairRanks(rank_former, rank_latter);
    }

    @Override
    public int get_rank(List<Card> private_hand, List<Card> public_board) {
        return bestRank(disjointUnion(mask(private_hand), mask(public_board)));
    }

    @Override
    public int get_rank(int[] private_hand, int[] public_board) {
        return bestRank(disjointUnion(mask(private_hand), mask(public_board)));
    }

    @Override
    public int get_rank(long private_hand, long public_board) {
        return bestRank(disjointUnion(private_hand, public_board));
    }
}
