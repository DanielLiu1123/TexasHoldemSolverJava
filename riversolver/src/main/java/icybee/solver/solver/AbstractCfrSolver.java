package icybee.solver.solver;

import icybee.solver.Card;
import icybee.solver.Deck;
import icybee.solver.RiverRangeManager;
import icybee.solver.compairer.Compairer;
import icybee.solver.nodes.ActionNode;
import icybee.solver.nodes.ChanceNode;
import icybee.solver.nodes.GameTreeNode;
import icybee.solver.ranges.PrivateCards;
import icybee.solver.ranges.PrivateCardsManager;
import icybee.solver.trainable.TrainableFactory;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Common state and helpers shared by all CFR-family solvers. */
abstract class AbstractCfrSolver extends Solver {

    PrivateCards[][] ranges;
    PrivateCards[] range1;
    PrivateCards[] range2;
    int[] initial_board;
    long initial_board_long;
    Compairer compairer;

    Deck deck;
    RiverRangeManager rrm;
    final int player_number = 2;
    int iteration_number;
    PrivateCardsManager pcm;
    boolean debug;
    int print_interval;

    @Nullable
    String logfile;

    TrainableFactory trainerFactory;
    int[] round_deal;
    MonteCarolAlg monteCarolAlg;

    protected AbstractCfrSolver(SolverConfig config) {
        super(config.tree());
        this.initial_board = config.initialBoard();
        this.initial_board_long = Card.boardInts2long(config.initialBoard());
        this.logfile = config.logfile();
        this.trainerFactory = config.trainerFactory();

        PrivateCards[] range1 = this.noDuplicateRange(config.range1(), initial_board_long);
        PrivateCards[] range2 = this.noDuplicateRange(config.range2(), initial_board_long);

        this.range1 = range1;
        this.range2 = range2;
        this.ranges = new PrivateCards[this.player_number][];
        this.ranges[0] = range1;
        this.ranges[1] = range2;
        this.compairer = config.compairer();
        this.deck = config.deck();
        this.rrm = new RiverRangeManager(config.compairer());
        this.iteration_number = config.iterationNumber();

        PrivateCards[][] privateCombos = new PrivateCards[this.player_number][];
        privateCombos[0] = range1;
        privateCombos[1] = range2;
        this.pcm = new PrivateCardsManager(privateCombos, this.player_number, Card.boardInts2long(this.initial_board));
        this.debug = config.debug();
        this.print_interval = config.printInterval();
        this.monteCarolAlg = config.monteCarolAlg();
        this.round_deal = new int[0];
    }

    PrivateCards[] playerHands(int player) {
        if (player == 0) {
            return range1;
        } else if (player == 1) {
            return range2;
        } else {
            throw new RuntimeException("player not found");
        }
    }

    float[][] getReachProbs() {
        float[][] retval = new float[this.player_number][];
        for (int player = 0; player < this.player_number; player++) {
            PrivateCards[] playerCards = this.playerHands(player);
            float[] reachProb = new float[playerCards.length];
            for (int i = 0; i < playerCards.length; i++) {
                reachProb[i] = playerCards[i].weight;
            }
            retval[player] = reachProb;
        }
        return retval;
    }

    public PrivateCards[] noDuplicateRange(PrivateCards[] privateRange, long boardLong) {
        java.util.List<PrivateCards> rangeArray = new java.util.ArrayList<>();
        java.util.Map<Integer, Boolean> rangeKv = new java.util.HashMap<>();
        for (PrivateCards oneRange : privateRange) {
            if (oneRange == null) throw new RuntimeException();
            if (rangeKv.get(oneRange.hashCode()) != null)
                throw new RuntimeException(String.format("duplicated key %s", oneRange.toString()));
            rangeKv.put(oneRange.hashCode(), Boolean.TRUE);
            long handLong = Card.boardInts2long(new int[] {oneRange.card1, oneRange.card2});
            if (!Card.boardsHasIntercept(handLong, boardLong)) {
                rangeArray.add(oneRange);
            }
        }
        PrivateCards[] ret = new PrivateCards[rangeArray.size()];
        rangeArray.toArray(ret);
        return ret;
    }

    void setTrainable(GameTreeNode root) {
        if (root instanceof ActionNode actionNode) {
            int player = actionNode.getPlayer();
            PrivateCards[] playerPrivates = this.ranges[player];
            actionNode.setTrainable(this.trainerFactory.create(actionNode, playerPrivates));
            List<GameTreeNode> children = actionNode.getChildrens();
            for (GameTreeNode oneChild : children) setTrainable(oneChild);
        } else if (root instanceof ChanceNode chanceNode) {
            List<GameTreeNode> children = chanceNode.getChildrens();
            for (GameTreeNode oneChild : children) setTrainable(oneChild);
        }
    }
}
