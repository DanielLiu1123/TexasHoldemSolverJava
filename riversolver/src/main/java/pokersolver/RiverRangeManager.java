package pokersolver;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import pokersolver.compairer.Compairer;
import pokersolver.exceptions.BoardNotFoundException;
import pokersolver.ranges.PrivateCards;
import pokersolver.ranges.RiverCombs;

public class RiverRangeManager {
    Map<Long, RiverCombs[]> p1RiverRanges = new HashMap<>();
    Map<Long, RiverCombs[]> p2RiverRanges = new HashMap<>();

    Compairer handEvaluator;

    public RiverRangeManager(Compairer compairer) {
        this.handEvaluator = compairer;
    }

    public RiverCombs[] getRiverCombos(int player, RiverCombs[] riverCombos, int[] board)
            throws BoardNotFoundException {
        PrivateCards[] preflopCombos = new PrivateCards[riverCombos.length];
        for (int i = 0; i < riverCombos.length; i++) {
            preflopCombos[i] = riverCombos[i].privateCards;
        }
        return getRiverCombos(player, preflopCombos, board);
    }

    public RiverCombs[] getRiverCombos(int player, PrivateCards[] preflopCombos, int[] board)
            throws BoardNotFoundException {
        long boardLong = Card.boardInts2long(board);
        return this.getRiverCombos(player, preflopCombos, boardLong);
    }

    public RiverCombs[] getRiverCombos(int player, PrivateCards[] preflopCombos, long boardLong) {
        Map<Long, RiverCombs[]> riverRanges;

        if (player == 0) riverRanges = p1RiverRanges;
        else if (player == 1) riverRanges = p2RiverRanges;
        else throw new RuntimeException("error range  player");

        long key = boardLong;

        if (riverRanges.get(key) != null) return riverRanges.get(key);

        int count = 0;

        for (int hand = 0; hand < preflopCombos.length; hand++) {
            PrivateCards oneHand = preflopCombos[hand];
            if (!Card.boardsHasIntercept(oneHand.toBoardLong(), boardLong)) count++;
        }

        int index = 0;
        RiverCombs[] riverCombos = new RiverCombs[count];

        for (int hand = 0; hand < preflopCombos.length; hand++) {
            PrivateCards preflopCombo = preflopCombos[hand];

            if (Card.boardsHasIntercept(preflopCombo.toBoardLong(), boardLong)) {
                continue;
            }

            int rank = this.handEvaluator.getRank(preflopCombo.toBoardLong(), boardLong);
            RiverCombs riverCombo = new RiverCombs(Card.long2board(boardLong), preflopCombo, rank, hand);
            riverCombos[index++] = riverCombo;
        }

        Arrays.sort(riverCombos);

        riverRanges.put(key, riverCombos);

        return riverCombos;
    }
}
