package pokersolver.compairer;

import pokersolver.Card;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Created by huangxuefeng on 2019/10/6.
 * Abstract class Compairer
 */
public abstract class Compairer {
    String dic_dir;
    int lines;

    public enum CompairResult {
        LARGER,
        EQUAL,
        SMALLER
    }

    public Compairer(String dic_dir, int lines) throws FileNotFoundException {
        this.dic_dir = dic_dir;
        this.lines = lines;
    }

    public abstract CompairResult compair(List<Card> private_former, List<Card> private_latter, List<Card> public_board)
            throws Exception;

    public abstract CompairResult compair(int[] private_former, int[] private_latter, int[] public_board)
            throws Exception;

    public abstract int get_rank(List<Card> private_hand, List<Card> public_board);

    public abstract int get_rank(int[] private_hand, int[] public_board);

    public abstract int get_rank(long private_hand, long public_board);
}
