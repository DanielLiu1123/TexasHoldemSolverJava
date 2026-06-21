package pokersolver.ranges;

/**
 * Created by huangxuefeng on 2019/10/11.
 * river ranges code
 */
public class RiverCombs implements Comparable<RiverCombs> {
    int[] board;
    public int rank;
    public PrivateCards privateCards;
    public int reachProbIndex;
    // public float reachprob;
    public RiverCombs(int[] board, PrivateCards privateCards, int rank, int reachProbIndex) {
        this.board = board;
        this.rank = rank;
        this.privateCards = privateCards;
        this.reachProbIndex = reachProbIndex;
    }

    @Override
    public int compareTo(RiverCombs o) {
        if (this.rank < o.rank) // if a's rank is smaller than b's , a win b lose
        return 1;
        if (this.rank > o.rank) return -1;
        return 0;
    }
}
