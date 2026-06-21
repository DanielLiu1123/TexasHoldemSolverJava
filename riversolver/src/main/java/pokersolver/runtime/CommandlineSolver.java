package pokersolver.runtime;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import pokersolver.*;
import pokersolver.compairer.Compairer;
import pokersolver.ranges.PrivateCards;
import pokersolver.solver.Algorithm;
import pokersolver.solver.CfrPlusRiverSolver;
import pokersolver.solver.MonteCarloAlg;
import pokersolver.solver.ParallelCfrPlusSolver;
import pokersolver.solver.Solver;
import pokersolver.solver.SolverConfig;
import pokersolver.utils.PrivateRangeConverter;

public class CommandlineSolver {

    static Config loadConfig(String confName) {
        File file = new File(confName);

        Config config;
        try {
            config = new Config(file.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException();
        }
        return config;
    }

    public static void main(String[] args) throws Exception {
        ArgumentParser parser = ArgumentParsers.newFor("CommandlineSolver")
                .build()
                .defaultHelp(true)
                .description("use command line to solve poker cfr");
        parser.addArgument("-c", "--config").help("route to the config file");
        parser.addArgument("-p1", "--player1_range")
                .help("player1 range str,like 'KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9' ");
        parser.addArgument("-p2", "--player2_range")
                .help("player2 range str,like 'KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9' ");
        parser.addArgument("-b", "--initial_board").help("the board card when the game start");
        parser.addArgument("-n", "--iteration_number").help("iteration number the cfr algorithm would run");
        parser.addArgument("-i", "--print_interval")
                .help("calculate best respond ev every other print_interval iterations of cfr");
        parser.addArgument("-d", "--debug").setDefault(false).help("open debug mode");
        parser.addArgument("-p", "--parallel").setDefault(true).help("whether to use thread pool");
        parser.addArgument("-o", "--output_strategy_file")
                .setDefault((Object) null)
                .help("where to output strategy json");
        parser.addArgument("-l", "--logfile")
                .setDefault((Object) null)
                .help("calculate best respond ev every other print_interval iterations of cfr");
        parser.addArgument("-a", "--algorithm")
                .choices("discounted_cfr", "cfr", "cfr_plus", "pcfr_plus")
                .setDefault("discounted_cfr")
                .help("cfr algorithm type");
        parser.addArgument("-m", "--monte_carol")
                .choices("none", "public")
                .setDefault("none")
                .help("(experimental)whether to use monte carol algorithm");
        parser.addArgument("-t", "--threads").setDefault(-1).help("multi thread thread number");
        parser.addArgument("-fa", "--fork_at_action")
                .setDefault(1)
                .help("using multi-thread in each action node with this prob");
        parser.addArgument("-fc", "--fork_at_chance")
                .setDefault(1)
                .help("using multi-thread in each chance node with this prob");
        parser.addArgument("-fe", "--fork_every_n_depth")
                .setDefault(1)
                .help("fork in between n layer of trees, default 1");
        parser.addArgument("-fs", "--no_fork_subtree_size").setDefault(0).help("fork minimal subtree size, default 0");

        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        if (ns == null) return;

        String configFile = ns.getString("config");
        if (configFile == null) {
            parser.printHelp();
            System.exit(1);
            return;
        }
        String player1RangeStr = ns.getString("player1_range");
        String player2RangeStr = ns.getString("player2_range");
        String initialBoardStr = ns.getString("initial_board");
        String[] initialBoardArr = initialBoardStr.split(",");
        int[] initialBoard = Arrays.stream(initialBoardArr)
                .map(Card::strCard2int)
                .mapToInt(i -> i)
                .toArray();
        int iterationNumber = Integer.parseInt(ns.getString("iteration_number"));
        int printInterval = Integer.parseInt(ns.getString("print_interval"));
        float forkAtAction = Float.parseFloat(ns.getString("fork_at_action"));
        float forkAtChance = Float.parseFloat(ns.getString("fork_at_chance"));
        boolean debug = Boolean.parseBoolean(ns.getString("debug"));
        boolean parallel = Boolean.parseBoolean(ns.getString("parallel"));
        String outputStrategyFile = ns.getString("output_strategy_file");
        String logfile = ns.getString("logfile");

        Algorithm algorithm = Algorithm.fromId(ns.getString("algorithm"));
        MonteCarloAlg monteCarlo = MonteCarloAlg.fromId(ns.getString("monte_carol"));
        int threads = Integer.parseInt(ns.getString("threads"));
        int forkEveryNDepth = Integer.parseInt(ns.getString("fork_every_n_depth"));
        int noForkSubtreeSize = Integer.parseInt(ns.getString("no_fork_subtree_size"));

        Config config = loadConfig(configFile);
        Deck deck = SolverEnvironment.deckFromConfig(config);
        Compairer compairer = SolverEnvironment.compairerFromConfig(config);
        GameTree gameTree = SolverEnvironment.gameTreeFromConfig(config, deck);

        PrivateCards[] player1Range = PrivateRangeConverter.rangeStr2Cards(player1RangeStr, initialBoard);
        PrivateCards[] player2Range = PrivateRangeConverter.rangeStr2Cards(player2RangeStr, initialBoard);

        SolverConfig solverConfig = SolverConfig.builder()
                .tree(gameTree)
                .range1(player1Range)
                .range2(player2Range)
                .initialBoard(initialBoard)
                .compairer(compairer)
                .deck(deck)
                .iterationNumber(iterationNumber)
                .debug(debug)
                .printInterval(printInterval)
                .logfile(logfile)
                .algorithm(algorithm)
                .monteCarloAlg(monteCarlo)
                .build();
        Solver solver;
        if (parallel) {
            solver = new ParallelCfrPlusSolver(
                    solverConfig, threads, forkAtAction, forkAtChance, forkEveryNDepth, noForkSubtreeSize);
        } else {
            solver = new CfrPlusRiverSolver(solverConfig);
        }
        solver.train();

        String strategyJson = solver.getTree().dumps(false).toString();
        File outputFile = new File(outputStrategyFile);
        FileWriter writer = new FileWriter(outputFile, StandardCharsets.UTF_8);
        writer.write(strategyJson);
        writer.flush();
        writer.close();
    }
}
