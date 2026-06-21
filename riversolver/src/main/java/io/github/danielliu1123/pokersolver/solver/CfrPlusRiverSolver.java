package pokersolver.solver;

import static pokersolver.utils.JsonUtil.MAPPER;

import pokersolver.nodes.GameTreeNode;
import pokersolver.ranges.PrivateCards;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import tools.jackson.databind.node.ObjectNode;

/**
 * Single-threaded CFR+ solver. Deterministic with {@link MonteCarloAlg#NONE}, which makes it the
 * reference used by the strategy regression suite. The tree traversal lives in {@link
 * AbstractCfrSolver}; this class only walks each node's children in order.
 */
public class CfrPlusRiverSolver extends AbstractCfrSolver {

    public CfrPlusRiverSolver(SolverConfig config) {
        super(config);
    }

    @Override
    protected float[][] evaluateChildren(
            int player,
            int iter,
            GameTreeNode parent,
            GameTreeNode[] children,
            float[][][] childReachProbs,
            long[] childBoards) {
        float[][] utilities = new float[children.length][];
        for (int k = 0; k < children.length; k++) {
            if (children[k] == null) continue;
            utilities[k] = cfr(player, children[k], childReachProbs[k], iter, childBoards[k]);
        }
        return utilities;
    }

    @Override
    public void train() throws Exception {
        setTrainable(tree.getRoot());

        PrivateCards[][] playerPrivates = new PrivateCards[this.player_number][];
        playerPrivates[0] = pcm.getPreflopCards(0);
        playerPrivates[1] = pcm.getPreflopCards(1);

        BestResponse br = new BestResponse(
                playerPrivates, this.player_number, this.compairer, this.pcm, this.rrm, this.deck, this.debug);

        br.printExploitability(tree.getRoot(), 0, (float) tree.getRoot().getPot(), initial_board_long);

        float[][] reachProbs = this.getReachProbs();

        long begintime = System.currentTimeMillis();
        long endtime = System.currentTimeMillis();
        try (Writer fileWriter = this.logfile != null
                ? Files.newBufferedWriter(Paths.get(this.logfile), StandardCharsets.UTF_8)
                : Writer.nullWriter()) {
            for (int i = 0; i < this.iteration_number && !this.stopRequested; i++) {
                for (int playerId = 0; playerId < this.player_number; playerId++) {
                    if (this.debug) {
                        System.out.println(String.format(
                                "---------------------------------     player %s --------------------------------",
                                playerId));
                    }
                    this.round_deal = new int[] {-1, -1, -1, -1};
                    cfr(playerId, this.tree.getRoot(), reachProbs, i, this.initial_board_long);
                }
                if (i % this.print_interval == 0) {
                    System.out.println("-------------------");
                    endtime = System.currentTimeMillis();
                    float exploitability = br.printExploitability(
                            tree.getRoot(), i + 1, (float) tree.getRoot().getPot(), initial_board_long);
                    long time_ms = endtime - begintime;
                    ObjectNode jo = MAPPER.createObjectNode();
                    jo.put("iteration", i);
                    jo.put("exploitability", exploitability);
                    jo.put("time_ms", time_ms);
                    fileWriter.write(String.format("%s\n", jo.toString()));
                    begintime = System.currentTimeMillis();
                    this.progressListener.onProgress(i, exploitability, time_ms);
                    if (this.stop_exploitability > 0 && exploitability < this.stop_exploitability) break;
                }
            }
        }
    }
}
