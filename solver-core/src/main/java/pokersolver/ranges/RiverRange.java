package pokersolver.ranges;

/**
 * One player's range projected onto a complete five-card board: the hands the board does not block,
 * each with its hand rank, sorted weakest first.
 *
 * <p>Stored column-wise rather than as an array of per-hand objects. The showdown kernel sweeps
 * these arrays twice per evaluation and is the hottest loop in the solver; a struct-of-arrays layout
 * keeps each sweep on contiguous memory instead of chasing one pointer per hand.
 *
 * @param ranks hand ranks, descending — so index 0 is the weakest hand and a smaller rank is
 *     stronger (see {@link pokersolver.eval.HandEvaluator})
 * @param card1 first hole card of each hand, parallel to {@code ranks}
 * @param card2 second hole card of each hand, parallel to {@code ranks}
 * @param reachIndex each hand's index in the player's unfiltered range, for indexing reach
 *     probabilities and payoffs
 */
public record RiverRange(int[] ranks, int[] card1, int[] card2, int[] reachIndex) {

    public int size() {
        return ranks.length;
    }
}
