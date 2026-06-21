package pokersolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pokersolver.compairer.Compairer;
import pokersolver.exceptions.BoardNotFoundException;
import pokersolver.ranges.PrivateCards;
import pokersolver.solver.CfrPlusRiverSolver;
import pokersolver.solver.MonteCarloAlg;
import pokersolver.solver.ParallelCfrPlusSolver;
import pokersolver.solver.Solver;
import pokersolver.solver.SolverConfig;
import pokersolver.trainable.DiscountedCfrTrainable;
import pokersolver.utils.PrivateRangeConverter;

/**
 *
 * Unit test
 */
public class ShortDeckSolverTest {
    static Compairer compairer;
    static Deck deck;

    @TempDir
    static Path outputDir;

    static Config loadConfig(String conf_name) {
        ClassLoader classLoader = ShortDeckSolverTest.class.getClassLoader();
        File file = new File(classLoader.getResource(conf_name).getFile());
        try {
            return new Config(file.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeAll
    static void loadEnvironments() throws Exception {
        String config_name = "yamls/rule_shortdeck_simple.yaml";
        Config config = loadConfig(config_name);
        compairer = SolverEnvironment.compairerFromConfig(config);
        deck = SolverEnvironment.deckFromConfig(config);
    }

    @Test
    public void cardCompairLGTest() {
        try {
            List<Card> board =
                    Arrays.asList(new Card("6c"), new Card("6d"), new Card("7c"), new Card("7d"), new Card("8s"));
            List<Card> private1 = Arrays.asList(new Card("6h"), new Card("6s"));
            List<Card> private2 = Arrays.asList(new Card("9c"), new Card("9s"));

            Compairer.CompairResult cr = ShortDeckSolverTest.compairer.compair(private1, private2, board);
            System.out.println(cr);
            assertTrue(cr == Compairer.CompairResult.LARGER);
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    @Test
    public void cardCompairEQTest() {
        try {
            List<Card> board =
                    Arrays.asList(new Card("6c"), new Card("6d"), new Card("7c"), new Card("7d"), new Card("8s"));
            List<Card> private1 = Arrays.asList(new Card("8h"), new Card("7s"));
            List<Card> private2 = Arrays.asList(new Card("8d"), new Card("7h"));

            Compairer.CompairResult cr = ShortDeckSolverTest.compairer.compair(private1, private2, board);
            System.out.println(cr);
            assertTrue(cr == Compairer.CompairResult.EQUAL);
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    @Test
    public void cardCompairSMTest() {
        try {
            List<Card> board =
                    Arrays.asList(new Card("6c"), new Card("6d"), new Card("7c"), new Card("7d"), new Card("8s"));
            List<Card> private1 = Arrays.asList(new Card("6h"), new Card("7s"));
            List<Card> private2 = Arrays.asList(new Card("8h"), new Card("7h"));

            Compairer.CompairResult cr = ShortDeckSolverTest.compairer.compair(private1, private2, board);
            System.out.println(cr);
            assertTrue(cr == Compairer.CompairResult.SMALLER);
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    @Test
    public void getRankTest() {
        List<Card> board =
                Arrays.asList(new Card("8d"), new Card("9d"), new Card("9s"), new Card("Jd"), new Card("Jh"));
        List<Card> private_cards = Arrays.asList(new Card("6h"), new Card("7s"));

        int rank = ShortDeckSolverTest.compairer.get_rank(private_cards, board);
        System.out.println(rank);
        assertTrue(rank == 687);
    }

    @Test
    public void printTreeTest() {
        String config_name = "yamls/rule_shortdeck_simple.yaml";
        Config config = loadConfig(config_name);
        GameTree game_tree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);
        System.out.println("The game tree :");
        try {
            game_tree.printTree(-1);
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    @Test
    public void printTreeLimitDepthTest() {
        String config_name = "yamls/rule_shortdeck_simple.yaml";
        Config config = loadConfig(config_name);
        GameTree game_tree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);
        System.out.println("The depth limit game tree :");
        try {
            game_tree.printTree(2);
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    @Test
    public void cardConvertTest() {
        System.out.println("cardConvertTest");
        try {
            Card card = new Card("6c");
            int card_int = Card.card2int(card);

            Card card_rev = new Card(Card.intCard2Str(card_int));
            int card_int_rev = Card.card2int(card_rev);
            System.out.println(card_int);
            System.out.println(card_int_rev);
            assert (card_int == card_int_rev);
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
        System.out.println("end of cardConvertTest");
    }

    @Test
    public void cardsIntegerConvertTest() {
        Card[] board = {
            new Card("6c"),
            new Card("6d"),
            new Card("7c"),
            new Card("7d"),
            new Card("8s"),
            new Card("6h"),
            new Card("7s")
        };
        try {
            long board_int = Card.boardCards2long(board);
            Card[] board_cards = Card.long2boardCards(board_int);
            long board_int_rev = Card.boardCards2long(board_cards);

            for (Card i : board) System.out.println(i.getCard());
            System.out.println();
            for (Card i : board_cards) System.out.println(i.getCard());

            System.out.println(board_int);
            System.out.println(board_int_rev);
            assert (board_int == board_int_rev);
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    @Test
    public void cardsIntegerConvertNETest() {
        Card[] board1 = {
            new Card("6c"),
            new Card("6d"),
            new Card("7c"),
            new Card("7d"),
            new Card("8s"),
            new Card("6h"),
            new Card("7s")
        };
        Card[] board2 = {
            new Card("6c"),
            new Card("6d"),
            new Card("7c"),
            new Card("7d"),
            new Card("9s"),
            new Card("6h"),
            new Card("7s")
        };
        try {
            long board_int1 = Card.boardCards2long(board1);
            long board_int2 = Card.boardCards2long(board2);
            assertTrue(board_int1 != board_int2);

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    @Test
    public void compaierEquivlentTest() {
        System.out.println("compaierEquivlentTest");
        List<Card> board1_public =
                Arrays.asList(new Card("6c"), new Card("6d"), new Card("7c"), new Card("7d"), new Card("8s"));
        List<Card> board1_private = Arrays.asList(new Card("6h"), new Card("7s"));
        int[] board2_public = {
            (new Card("6c").getCardInt()),
            (new Card("6d").getCardInt()),
            (new Card("7c").getCardInt()),
            (new Card("7d").getCardInt()),
            (new Card("8s").getCardInt()),
        };
        int[] board2_private = {(new Card("6h").getCardInt()), (new Card("7s").getCardInt())};
        try {
            long board_int1 = compairer.get_rank(board1_private, board1_public);
            long board_int2 = compairer.get_rank(board2_private, board2_public);
            System.out.println(board_int1);
            System.out.println(board_int2);
            assertTrue(board_int1 == board_int2);

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
        System.out.println("end compaierEquivlentTest");
    }

    @Test
    public void cfrSolverTest() throws Exception {
        System.out.println("solverTest");

        String config_name = "yamls/rule_shortdeck_simple.yaml";
        Config config = loadConfig(config_name);
        GameTree game_tree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);

        String player1RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";
        String player2RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";

        int[] initialBoard = new int[] {
            Card.strCard2int("Kd"),
            Card.strCard2int("Jd"),
            Card.strCard2int("Td"),
            Card.strCard2int("7s"),
            Card.strCard2int("8s")
        };

        PrivateCards[] player1Range = PrivateRangeConverter.rangeStr2Cards(player1RangeStr, initialBoard);
        PrivateCards[] player2Range = PrivateRangeConverter.rangeStr2Cards(player2RangeStr, initialBoard);

        String logfile_name = outputDir.resolve("outputs_log.txt").toString();
        Solver solver = new CfrPlusRiverSolver(SolverConfig.builder()
                .tree(game_tree)
                .range1(player1Range)
                .range2(player2Range)
                .initialBoard(initialBoard)
                .compairer(ShortDeckSolverTest.compairer)
                .deck(ShortDeckSolverTest.deck)
                .iterationNumber(100)
                .debug(false)
                .printInterval(10)
                .logfile(logfile_name)
                .trainerFactory(DiscountedCfrTrainable::new)
                .monteCarloAlg(MonteCarloAlg.NONE)
                .build());
        solver.train();
    }

    @Test
    public void cfrTurnSolverTest() throws BoardNotFoundException, Exception {
        System.out.println("solverTest");

        String config_name = "yamls/rule_shortdeck_turnsolver.yaml";
        Config config = loadConfig(config_name);
        GameTree game_tree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);

        String player1RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";
        String player2RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";

        int[] initialBoard = new int[] {
            Card.strCard2int("Kd"), Card.strCard2int("Jd"), Card.strCard2int("Td"), Card.strCard2int("7s"),
        };

        PrivateCards[] player1Range = PrivateRangeConverter.rangeStr2Cards(player1RangeStr, initialBoard);
        PrivateCards[] player2Range = PrivateRangeConverter.rangeStr2Cards(player2RangeStr, initialBoard);

        String logfile_name = outputDir.resolve("outputs_log.txt").toString();
        Solver solver = new CfrPlusRiverSolver(SolverConfig.builder()
                .tree(game_tree)
                .range1(player1Range)
                .range2(player2Range)
                .initialBoard(initialBoard)
                .compairer(ShortDeckSolverTest.compairer)
                .deck(ShortDeckSolverTest.deck)
                .iterationNumber(100)
                .debug(false)
                .printInterval(10)
                .logfile(logfile_name)
                .trainerFactory(DiscountedCfrTrainable::new)
                .monteCarloAlg(MonteCarloAlg.NONE)
                .build());
        solver.train();

        String strategy_json = solver.getTree().dumps(false).toString();

        String strategy_fname = outputDir.resolve("outputs_strategy.json").toString();

        File output_file = new File(strategy_fname);
        FileWriter writer = new FileWriter(output_file);
        writer.write(strategy_json);
        writer.flush();
        writer.close();

        System.out.println("end solverTest");
    }

    @Test
    public void parrallelCfrFlopSolverTest() throws BoardNotFoundException, Exception {
        System.out.println("solverTest");

        String config_name = "yamls/rule_shortdeck_flopsolver.yaml";
        Config config = loadConfig(config_name);
        GameTree game_tree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);

        String player1RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";
        String player2RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";

        int[] initialBoard = new int[] {
            Card.strCard2int("Kd"), Card.strCard2int("Jd"), Card.strCard2int("Td"),
        };

        PrivateCards[] player1Range = PrivateRangeConverter.rangeStr2Cards(player1RangeStr, initialBoard);
        PrivateCards[] player2Range = PrivateRangeConverter.rangeStr2Cards(player2RangeStr, initialBoard);

        String logfile_name = outputDir.resolve("outputs_log.txt").toString();
        Solver solver = new ParallelCfrPlusSolver(
                SolverConfig.builder()
                        .tree(game_tree)
                        .range1(player1Range)
                        .range2(player2Range)
                        .initialBoard(initialBoard)
                        .compairer(ShortDeckSolverTest.compairer)
                        .deck(ShortDeckSolverTest.deck)
                        .iterationNumber(31)
                        .debug(false)
                        .printInterval(10)
                        .logfile(logfile_name)
                        .trainerFactory(DiscountedCfrTrainable::new)
                        .monteCarloAlg(MonteCarloAlg.NONE)
                        .build(),
                -1,
                1,
                0,
                1,
                0);
        solver.train();
    }

    @Test
    public void cfrFlopSolverTest() throws BoardNotFoundException, Exception {
        System.out.println("solverTest");

        String config_name = "yamls/rule_shortdeck_flopsolver.yaml";
        Config config = loadConfig(config_name);
        GameTree game_tree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);

        String player1RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";
        String player2RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";

        int[] initialBoard = new int[] {
            Card.strCard2int("Kd"), Card.strCard2int("Jd"), Card.strCard2int("Td"),
        };

        PrivateCards[] player1Range = PrivateRangeConverter.rangeStr2Cards(player1RangeStr, initialBoard);
        PrivateCards[] player2Range = PrivateRangeConverter.rangeStr2Cards(player2RangeStr, initialBoard);

        String logfile_name = outputDir.resolve("outputs_log.txt").toString();
        Solver solver = new CfrPlusRiverSolver(SolverConfig.builder()
                .tree(game_tree)
                .range1(player1Range)
                .range2(player2Range)
                .initialBoard(initialBoard)
                .compairer(ShortDeckSolverTest.compairer)
                .deck(ShortDeckSolverTest.deck)
                .iterationNumber(31)
                .debug(false)
                .printInterval(10)
                .logfile(logfile_name)
                .trainerFactory(DiscountedCfrTrainable::new)
                .monteCarloAlg(MonteCarloAlg.NONE)
                .build());
        solver.train();

        System.out.println("end solverTest");
    }

    @Test
    public void cfrFlopSolverPcsTest() throws BoardNotFoundException, Exception {
        System.out.println("solverTest");

        String config_name = "yamls/rule_shortdeck_flopsolver.yaml";
        Config config = loadConfig(config_name);
        GameTree game_tree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);

        String player1RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";
        String player2RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";

        int[] initialBoard = new int[] {
            Card.strCard2int("Kd"), Card.strCard2int("Jd"), Card.strCard2int("Td"),
        };

        PrivateCards[] player1Range = PrivateRangeConverter.rangeStr2Cards(player1RangeStr, initialBoard);
        PrivateCards[] player2Range = PrivateRangeConverter.rangeStr2Cards(player2RangeStr, initialBoard);

        String logfile_name = outputDir.resolve("outputs_log.txt").toString();
        Solver solver = new CfrPlusRiverSolver(SolverConfig.builder()
                .tree(game_tree)
                .range1(player1Range)
                .range2(player2Range)
                .initialBoard(initialBoard)
                .compairer(ShortDeckSolverTest.compairer)
                .deck(ShortDeckSolverTest.deck)
                .iterationNumber(1000)
                .debug(false)
                .printInterval(100)
                .logfile(logfile_name)
                .trainerFactory(DiscountedCfrTrainable::new)
                .monteCarloAlg(MonteCarloAlg.PUBLIC)
                .build());
        solver.train();

        System.out.println("end solverTest");
    }

    @Test
    public void parrallelPcsCfrFlopSolverTest() throws BoardNotFoundException, Exception {
        System.out.println("solverTest");

        String config_name = "yamls/rule_shortdeck_flopsolver.yaml";
        Config config = loadConfig(config_name);
        GameTree game_tree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);

        String player1RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";
        String player2RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";

        int[] initialBoard = new int[] {
            Card.strCard2int("Kd"), Card.strCard2int("Jd"), Card.strCard2int("Td"),
        };

        PrivateCards[] player1Range = PrivateRangeConverter.rangeStr2Cards(player1RangeStr, initialBoard);
        PrivateCards[] player2Range = PrivateRangeConverter.rangeStr2Cards(player2RangeStr, initialBoard);

        String logfile_name = outputDir.resolve("outputs_log.txt").toString();
        Solver solver = new ParallelCfrPlusSolver(
                SolverConfig.builder()
                        .tree(game_tree)
                        .range1(player1Range)
                        .range2(player2Range)
                        .initialBoard(initialBoard)
                        .compairer(ShortDeckSolverTest.compairer)
                        .deck(ShortDeckSolverTest.deck)
                        .iterationNumber(1000)
                        .debug(false)
                        .printInterval(100)
                        .logfile(logfile_name)
                        .trainerFactory(DiscountedCfrTrainable::new)
                        .monteCarloAlg(MonteCarloAlg.PUBLIC)
                        .build(),
                -1,
                1,
                0,
                1,
                0);
        solver.train();
    }

    @Test
    public void parrallelCfrTurnSolverTest() throws BoardNotFoundException, Exception {
        System.out.println("solverTest");

        String config_name = "yamls/rule_shortdeck_turnsolver.yaml";
        Config config = loadConfig(config_name);
        GameTree game_tree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);

        String player1RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";
        String player2RangeStr = "KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";

        int[] initialBoard = new int[] {
            Card.strCard2int("Kd"), Card.strCard2int("Jd"), Card.strCard2int("Td"),
        };

        PrivateCards[] player1Range = PrivateRangeConverter.rangeStr2Cards(player1RangeStr, initialBoard);
        PrivateCards[] player2Range = PrivateRangeConverter.rangeStr2Cards(player2RangeStr, initialBoard);

        String logfile_name = outputDir.resolve("outputs_log.txt").toString();
        Solver solver = new ParallelCfrPlusSolver(
                SolverConfig.builder()
                        .tree(game_tree)
                        .range1(player1Range)
                        .range2(player2Range)
                        .initialBoard(initialBoard)
                        .compairer(ShortDeckSolverTest.compairer)
                        .deck(ShortDeckSolverTest.deck)
                        .iterationNumber(100)
                        .debug(false)
                        .printInterval(10)
                        .logfile(logfile_name)
                        .trainerFactory(DiscountedCfrTrainable::new)
                        .monteCarloAlg(MonteCarloAlg.NONE)
                        .build(),
                2,
                1,
                0,
                1,
                0);
        solver.train();

        String strategy_json = solver.getTree().dumps(false).toString();

        String strategy_fname = outputDir.resolve("outputs_strategy.json").toString();

        File output_file = new File(strategy_fname);
        FileWriter writer = new FileWriter(output_file);
        writer.write(strategy_json);
        writer.flush();
        writer.close();

        System.out.println("end solverTest");
    }
}
