package pokersolver.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import pokersolver.Card;
import pokersolver.GameTree;
import pokersolver.SolverEnvironment;
import pokersolver.ranges.PrivateCards;
import pokersolver.solver.Algorithm;
import pokersolver.solver.MonteCarloAlg;
import pokersolver.solver.ParallelCfrSolver;
import pokersolver.solver.SequentialCfrSolver;
import pokersolver.solver.Solver;
import pokersolver.solver.SolverConfig;
import pokersolver.utils.PrivateRangeConverter;

/** Solves one scenario from the command line and writes the strategy to a JSON file. */
public final class CommandlineSolver {

    private CommandlineSolver() {}

    public static void main(String[] args) throws Exception {
        ArgumentParser parser = ArgumentParsers.newFor("CommandlineSolver")
                .build()
                .defaultHelp(true)
                .description("solve a post-flop Texas hold'em spot with counterfactual regret minimization");
        parser.addArgument("--tree").required(true).help("path to the game-tree JSON file");
        parser.addArgument("-p1", "--player1-range").required(true).help("in-position range, e.g. 'AA,KK,AKs,KQs:0.5'");
        parser.addArgument("-p2", "--player2-range").required(true).help("out-of-position range");
        parser.addArgument("-b", "--initial-board").required(true).help("community cards, e.g. 'Qs,Jh,2h'");
        parser.addArgument("-n", "--iteration-number").type(Integer.class).setDefault(100);
        parser.addArgument("-i", "--print-interval")
                .type(Integer.class)
                .setDefault(10)
                .help("evaluate exploitability every N iterations");
        parser.addArgument("-o", "--output-strategy-file").required(true).help("where to write the strategy JSON");
        parser.addArgument("-l", "--logfile")
                .setDefault((Object) null)
                .help("where to append a JSON convergence trace");
        parser.addArgument("-a", "--algorithm")
                .choices("discounted_cfr", "pdcfr", "pdcfr_plus", "pcfr_plus", "cfr_plus", "cfr")
                .setDefault("discounted_cfr")
                .help("CFR variant");
        parser.addArgument("-m", "--monte-carlo")
                .choices("none", "public")
                .setDefault("none")
                .help("(experimental) sample one card per chance node instead of dealing all of them");
        parser.addArgument("-e", "--stop-exploitability")
                .type(Double.class)
                .setDefault(0.0)
                .help("stop once exploitability falls below this percentage of the pot; 0 disables");
        parser.addArgument("-p", "--parallel").type(Boolean.class).setDefault(true);
        parser.addArgument("-t", "--threads").type(Integer.class).setDefault(-1).help("-1 for one per core");
        parser.addArgument("-fa", "--fork-at-action").type(Double.class).setDefault(1.0);
        parser.addArgument("-fc", "--fork-at-chance").type(Double.class).setDefault(1.0);
        parser.addArgument("-fe", "--fork-every-n-depth").type(Integer.class).setDefault(1);
        parser.addArgument("-fs", "--no-fork-subtree-size").type(Integer.class).setDefault(0);

        Namespace ns;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
            return;
        }

        int[] initialBoard = Arrays.stream(ns.getString("initial_board").split(","))
                .map(String::trim)
                .mapToInt(Card::strCard2int)
                .toArray();

        GameTree gameTree = SolverEnvironment.gameTreeFromJson(ns.getString("tree"));

        PrivateCards[] range1 = PrivateRangeConverter.rangeStr2Cards(ns.getString("player1_range"), initialBoard);
        PrivateCards[] range2 = PrivateRangeConverter.rangeStr2Cards(ns.getString("player2_range"), initialBoard);

        SolverConfig solverConfig = SolverConfig.builder()
                .tree(gameTree)
                .range1(range1)
                .range2(range2)
                .initialBoard(initialBoard)
                .iterationNumber(ns.getInt("iteration_number"))
                .printInterval(ns.getInt("print_interval"))
                .logfile(ns.getString("logfile"))
                .algorithm(Algorithm.fromId(ns.getString("algorithm")))
                .monteCarloAlg(MonteCarloAlg.fromId(ns.getString("monte_carlo")))
                .stopExploitability(ns.getDouble("stop_exploitability"))
                .build();

        Solver solver = ns.getBoolean("parallel")
                ? new ParallelCfrSolver(
                        solverConfig,
                        ns.getInt("threads"),
                        ns.getDouble("fork_at_action"),
                        ns.getDouble("fork_at_chance"),
                        ns.getInt("fork_every_n_depth"),
                        ns.getInt("no_fork_subtree_size"))
                : new SequentialCfrSolver(solverConfig);
        solver.train();

        writeStrategy(solver, ns.getString("output_strategy_file"));
    }

    private static void writeStrategy(Solver solver, String outputPath) throws IOException {
        Files.writeString(Path.of(outputPath), solver.getTree().dumps().toString(), StandardCharsets.UTF_8);
    }
}
