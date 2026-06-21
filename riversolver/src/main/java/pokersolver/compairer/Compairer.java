package pokersolver.compairer;

import java.io.FileNotFoundException;
import java.util.List;
import pokersolver.Card;

/**
 * Created by huangxuefeng on 2019/10/6.
 * Abstract class Compairer
 */
public abstract class Compairer {
    String dicDir;
    int lines;

    public enum CompairResult {
        LARGER,
        EQUAL,
        SMALLER
    }

    public Compairer(String dicDir, int lines) throws FileNotFoundException {
        this.dicDir = dicDir;
        this.lines = lines;
    }

    public abstract CompairResult compair(List<Card> privateFormer, List<Card> privateLatter, List<Card> publicBoard)
            throws Exception;

    public abstract CompairResult compair(int[] privateFormer, int[] privateLatter, int[] publicBoard) throws Exception;

    public abstract int getRank(List<Card> privateHand, List<Card> publicBoard);

    public abstract int getRank(int[] privateHand, int[] publicBoard);

    public abstract int getRank(long privateHand, long publicBoard);
}
