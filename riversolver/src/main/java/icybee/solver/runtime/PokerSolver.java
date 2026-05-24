package icybee.solver.runtime;

import icybee.solver.*;
import icybee.solver.compairer.Compairer;
import icybee.solver.ranges.PrivateCards;
import icybee.solver.solver.CfrPlusRiverSolver;
import icybee.solver.solver.MonteCarloAlg;
import icybee.solver.solver.ParallelCfrPlusSolver;
import icybee.solver.solver.Solver;
import icybee.solver.solver.SolverConfig;
import icybee.solver.trainable.CfrPlusTrainable;
import icybee.solver.trainable.CfrTrainable;
import icybee.solver.trainable.DiscountedCfrTrainable;
import icybee.solver.trainable.TrainableFactory;
import icybee.solver.utils.PrivateRangeConverter;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public class PokerSolver {
    Deck deck;

    @Nullable
    GameTree tree;

    Compairer compairer;

    public PokerSolver(
            String compairer_type, String compairer_dic_dir, int compairer_lines, String[] ranks, String[] suits)
            throws IOException {
        this.deck = new Deck(Arrays.asList(ranks), Arrays.asList(suits));
        this.compairer = SolverEnvironment.compairerFromFile(compairer_type, compairer_dic_dir, compairer_lines);
        this.tree = null;
    }

    public String train(
            String player1_range,
            String player2_range,
            String initial_board,
            int iteration_number,
            int print_interval,
            boolean debug,
            boolean parallel,
            String output_strategy_file,
            String logfile,
            String algorithm,
            String monte_carol,
            int threads,
            float fork_at_action,
            float fork_at_chance,
            int fork_every_n_depth,
            int no_fork_subtree_size)
            throws Exception {
        if (this.tree == null) throw new RuntimeException("tree not initized");
        String[] initial_board_split = initial_board.split(",");
        int[] initial_board_arr = Arrays.stream(initial_board_split)
                .map(Card::strCard2int)
                .mapToInt(i -> i)
                .toArray();
        TrainableFactory algorithm_class =
                switch (algorithm) {
                    case "cfr" -> CfrTrainable::new;
                    case "cfr_plus" -> CfrPlusTrainable::new;
                    case "discounted_cfr" -> DiscountedCfrTrainable::new;
                    default -> throw new RuntimeException(String.format("algorithm not found :%s", algorithm));
                };

        MonteCarloAlg monte_coral_alg =
                switch (monte_carol) {
                    case "none" -> MonteCarloAlg.NONE;
                    case "public" -> MonteCarloAlg.PUBLIC;
                    default -> throw new RuntimeException(String.format("monte coral type not found :%s", monte_carol));
                };

        PrivateCards[] player1Range = PrivateRangeConverter.rangeStr2Cards(player1_range, initial_board_arr);
        PrivateCards[] player2Range = PrivateRangeConverter.rangeStr2Cards(player2_range, initial_board_arr);

        SolverConfig solverConfig = new SolverConfig(
                this.tree,
                player1Range,
                player2Range,
                initial_board_arr,
                compairer,
                deck,
                iteration_number,
                debug,
                print_interval,
                logfile,
                algorithm_class,
                monte_coral_alg);
        Solver solver;
        if (parallel) {
            solver = new ParallelCfrPlusSolver(
                    solverConfig, threads, fork_at_action, fork_at_chance, fork_every_n_depth, no_fork_subtree_size);
        } else {
            solver = new CfrPlusRiverSolver(solverConfig);
        }
        Map train_config = new HashMap();
        solver.train(train_config);

        String strategy_json = solver.getTree().dumps(false).toString();
        if (output_strategy_file == null || output_strategy_file.isEmpty()) {
            return strategy_json;
        } else {
            File output_file = new File(output_strategy_file);
            try (BufferedWriter writer = Files.newBufferedWriter(output_file.toPath(), StandardCharsets.UTF_8)) {
                writer.write(strategy_json);
            }
            return output_strategy_file;
        }
    }
}
