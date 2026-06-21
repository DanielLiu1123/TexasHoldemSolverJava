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

    static Config loadConfig(String confName) {
        ClassLoader classLoader = ShortDeckSolverTest.class.getClassLoader();
        File file = new File(classLoader.getResource(confName).getFile());
        try {
            return new Config(file.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeAll
    static void loadEnvironments() throws Exception {
        String configName = "yamls/rule_shortdeck_simple.yaml";
        Config config = loadConfig(configName);
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
        List<Card> privateCards = Arrays.asList(new Card("6h"), new Card("7s"));

        int rank = ShortDeckSolverTest.compairer.getRank(privateCards, board);
        System.out.println(rank);
        assertTrue(rank == 687);
    }

    @Test
    public void printTreeTest() {
        String configName = "yamls/rule_shortdeck_simple.yaml";
        Config config = loadConfig(configName);
        GameTree gameTree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);
        System.out.println("The game tree :");
        try {
            gameTree.printTree(-1);
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    @Test
    public void printTreeLimitDepthTest() {
        String configName = "yamls/rule_shortdeck_simple.yaml";
        Config config = loadConfig(configName);
        GameTree gameTree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);
        System.out.println("The depth limit game tree :");
        try {
            gameTree.printTree(2);
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
            int cardInt = Card.card2int(card);

            Card cardRev = new Card(Card.intCard2Str(cardInt));
            int cardIntRev = Card.card2int(cardRev);
            System.out.println(cardInt);
            System.out.println(cardIntRev);
            assert (cardInt == cardIntRev);
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
            long boardInt = Card.boardCards2long(board);
            Card[] boardCards = Card.long2boardCards(boardInt);
            long boardIntRev = Card.boardCards2long(boardCards);

            for (Card i : board) System.out.println(i.getCard());
            System.out.println();
            for (Card i : boardCards) System.out.println(i.getCard());

            System.out.println(boardInt);
            System.out.println(boardIntRev);
            assert (boardInt == boardIntRev);
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
            long boardInt1 = Card.boardCards2long(board1);
            long boardInt2 = Card.boardCards2long(board2);
            assertTrue(boardInt1 != boardInt2);

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    @Test
    public void compaierEquivlentTest() {
        System.out.println("compaierEquivlentTest");
        List<Card> board1Public =
                Arrays.asList(new Card("6c"), new Card("6d"), new Card("7c"), new Card("7d"), new Card("8s"));
        List<Card> board1Private = Arrays.asList(new Card("6h"), new Card("7s"));
        int[] board2Public = {
            (new Card("6c").getCardInt()),
            (new Card("6d").getCardInt()),
            (new Card("7c").getCardInt()),
            (new Card("7d").getCardInt()),
            (new Card("8s").getCardInt()),
        };
        int[] board2Private = {(new Card("6h").getCardInt()), (new Card("7s").getCardInt())};
        try {
            long boardInt1 = compairer.getRank(board1Private, board1Public);
            long boardInt2 = compairer.getRank(board2Private, board2Public);
            System.out.println(boardInt1);
            System.out.println(boardInt2);
            assertTrue(boardInt1 == boardInt2);

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
        System.out.println("end compaierEquivlentTest");
    }

    @Test
    public void cfrSolverTest() throws Exception {
        System.out.println("solverTest");

        String configName = "yamls/rule_shortdeck_simple.yaml";
        Config config = loadConfig(configName);
        GameTree gameTree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);

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

        String logfileName = outputDir.resolve("outputs_log.txt").toString();
        Solver solver = new CfrPlusRiverSolver(SolverConfig.builder()
                .tree(gameTree)
                .range1(player1Range)
                .range2(player2Range)
                .initialBoard(initialBoard)
                .compairer(ShortDeckSolverTest.compairer)
                .deck(ShortDeckSolverTest.deck)
                .iterationNumber(100)
                .debug(false)
                .printInterval(10)
                .logfile(logfileName)
                .trainerFactory(DiscountedCfrTrainable::new)
                .monteCarloAlg(MonteCarloAlg.NONE)
                .build());
        solver.train();
    }

    @Test
    public void cfrTurnSolverTest() throws BoardNotFoundException, Exception {
        System.out.println("solverTest");

        String configName = "yamls/rule_shortdeck_turnsolver.yaml";
        Config config = loadConfig(configName);
        GameTree gameTree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);

        String player1RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";
        String player2RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";

        int[] initialBoard = new int[] {
            Card.strCard2int("Kd"), Card.strCard2int("Jd"), Card.strCard2int("Td"), Card.strCard2int("7s"),
        };

        PrivateCards[] player1Range = PrivateRangeConverter.rangeStr2Cards(player1RangeStr, initialBoard);
        PrivateCards[] player2Range = PrivateRangeConverter.rangeStr2Cards(player2RangeStr, initialBoard);

        String logfileName = outputDir.resolve("outputs_log.txt").toString();
        Solver solver = new CfrPlusRiverSolver(SolverConfig.builder()
                .tree(gameTree)
                .range1(player1Range)
                .range2(player2Range)
                .initialBoard(initialBoard)
                .compairer(ShortDeckSolverTest.compairer)
                .deck(ShortDeckSolverTest.deck)
                .iterationNumber(100)
                .debug(false)
                .printInterval(10)
                .logfile(logfileName)
                .trainerFactory(DiscountedCfrTrainable::new)
                .monteCarloAlg(MonteCarloAlg.NONE)
                .build());
        solver.train();

        String strategyJson = solver.getTree().dumps(false).toString();

        String strategyFname = outputDir.resolve("outputs_strategy.json").toString();

        File outputFile = new File(strategyFname);
        FileWriter writer = new FileWriter(outputFile);
        writer.write(strategyJson);
        writer.flush();
        writer.close();

        System.out.println("end solverTest");
    }

    @Test
    public void parrallelCfrFlopSolverTest() throws BoardNotFoundException, Exception {
        System.out.println("solverTest");

        String configName = "yamls/rule_shortdeck_flopsolver.yaml";
        Config config = loadConfig(configName);
        GameTree gameTree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);

        String player1RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";
        String player2RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";

        int[] initialBoard = new int[] {
            Card.strCard2int("Kd"), Card.strCard2int("Jd"), Card.strCard2int("Td"),
        };

        PrivateCards[] player1Range = PrivateRangeConverter.rangeStr2Cards(player1RangeStr, initialBoard);
        PrivateCards[] player2Range = PrivateRangeConverter.rangeStr2Cards(player2RangeStr, initialBoard);

        String logfileName = outputDir.resolve("outputs_log.txt").toString();
        Solver solver = new ParallelCfrPlusSolver(
                SolverConfig.builder()
                        .tree(gameTree)
                        .range1(player1Range)
                        .range2(player2Range)
                        .initialBoard(initialBoard)
                        .compairer(ShortDeckSolverTest.compairer)
                        .deck(ShortDeckSolverTest.deck)
                        .iterationNumber(31)
                        .debug(false)
                        .printInterval(10)
                        .logfile(logfileName)
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

        String configName = "yamls/rule_shortdeck_flopsolver.yaml";
        Config config = loadConfig(configName);
        GameTree gameTree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);

        String player1RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";
        String player2RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";

        int[] initialBoard = new int[] {
            Card.strCard2int("Kd"), Card.strCard2int("Jd"), Card.strCard2int("Td"),
        };

        PrivateCards[] player1Range = PrivateRangeConverter.rangeStr2Cards(player1RangeStr, initialBoard);
        PrivateCards[] player2Range = PrivateRangeConverter.rangeStr2Cards(player2RangeStr, initialBoard);

        String logfileName = outputDir.resolve("outputs_log.txt").toString();
        Solver solver = new CfrPlusRiverSolver(SolverConfig.builder()
                .tree(gameTree)
                .range1(player1Range)
                .range2(player2Range)
                .initialBoard(initialBoard)
                .compairer(ShortDeckSolverTest.compairer)
                .deck(ShortDeckSolverTest.deck)
                .iterationNumber(31)
                .debug(false)
                .printInterval(10)
                .logfile(logfileName)
                .trainerFactory(DiscountedCfrTrainable::new)
                .monteCarloAlg(MonteCarloAlg.NONE)
                .build());
        solver.train();

        System.out.println("end solverTest");
    }

    @Test
    public void cfrFlopSolverPcsTest() throws BoardNotFoundException, Exception {
        System.out.println("solverTest");

        String configName = "yamls/rule_shortdeck_flopsolver.yaml";
        Config config = loadConfig(configName);
        GameTree gameTree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);

        String player1RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";
        String player2RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";

        int[] initialBoard = new int[] {
            Card.strCard2int("Kd"), Card.strCard2int("Jd"), Card.strCard2int("Td"),
        };

        PrivateCards[] player1Range = PrivateRangeConverter.rangeStr2Cards(player1RangeStr, initialBoard);
        PrivateCards[] player2Range = PrivateRangeConverter.rangeStr2Cards(player2RangeStr, initialBoard);

        String logfileName = outputDir.resolve("outputs_log.txt").toString();
        Solver solver = new CfrPlusRiverSolver(SolverConfig.builder()
                .tree(gameTree)
                .range1(player1Range)
                .range2(player2Range)
                .initialBoard(initialBoard)
                .compairer(ShortDeckSolverTest.compairer)
                .deck(ShortDeckSolverTest.deck)
                .iterationNumber(1000)
                .debug(false)
                .printInterval(100)
                .logfile(logfileName)
                .trainerFactory(DiscountedCfrTrainable::new)
                .monteCarloAlg(MonteCarloAlg.PUBLIC)
                .build());
        solver.train();

        System.out.println("end solverTest");
    }

    @Test
    public void parrallelPcsCfrFlopSolverTest() throws BoardNotFoundException, Exception {
        System.out.println("solverTest");

        String configName = "yamls/rule_shortdeck_flopsolver.yaml";
        Config config = loadConfig(configName);
        GameTree gameTree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);

        String player1RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";
        String player2RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";

        int[] initialBoard = new int[] {
            Card.strCard2int("Kd"), Card.strCard2int("Jd"), Card.strCard2int("Td"),
        };

        PrivateCards[] player1Range = PrivateRangeConverter.rangeStr2Cards(player1RangeStr, initialBoard);
        PrivateCards[] player2Range = PrivateRangeConverter.rangeStr2Cards(player2RangeStr, initialBoard);

        String logfileName = outputDir.resolve("outputs_log.txt").toString();
        Solver solver = new ParallelCfrPlusSolver(
                SolverConfig.builder()
                        .tree(gameTree)
                        .range1(player1Range)
                        .range2(player2Range)
                        .initialBoard(initialBoard)
                        .compairer(ShortDeckSolverTest.compairer)
                        .deck(ShortDeckSolverTest.deck)
                        .iterationNumber(1000)
                        .debug(false)
                        .printInterval(100)
                        .logfile(logfileName)
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

        String configName = "yamls/rule_shortdeck_turnsolver.yaml";
        Config config = loadConfig(configName);
        GameTree gameTree = SolverEnvironment.gameTreeFromConfig(config, ShortDeckSolverTest.deck);

        String player1RangeStr =
                "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,A7,A6,KQ,KJ,KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";
        String player2RangeStr = "KT,K9,K8,K7,K6,QJ,QT,Q9,Q8,Q7,Q6,JT,J9,J8,J7,J6,T9,T8,T7,T6,98,97,96,87,86,76";

        int[] initialBoard = new int[] {
            Card.strCard2int("Kd"), Card.strCard2int("Jd"), Card.strCard2int("Td"),
        };

        PrivateCards[] player1Range = PrivateRangeConverter.rangeStr2Cards(player1RangeStr, initialBoard);
        PrivateCards[] player2Range = PrivateRangeConverter.rangeStr2Cards(player2RangeStr, initialBoard);

        String logfileName = outputDir.resolve("outputs_log.txt").toString();
        Solver solver = new ParallelCfrPlusSolver(
                SolverConfig.builder()
                        .tree(gameTree)
                        .range1(player1Range)
                        .range2(player2Range)
                        .initialBoard(initialBoard)
                        .compairer(ShortDeckSolverTest.compairer)
                        .deck(ShortDeckSolverTest.deck)
                        .iterationNumber(100)
                        .debug(false)
                        .printInterval(10)
                        .logfile(logfileName)
                        .trainerFactory(DiscountedCfrTrainable::new)
                        .monteCarloAlg(MonteCarloAlg.NONE)
                        .build(),
                2,
                1,
                0,
                1,
                0);
        solver.train();

        String strategyJson = solver.getTree().dumps(false).toString();

        String strategyFname = outputDir.resolve("outputs_strategy.json").toString();

        File outputFile = new File(strategyFname);
        FileWriter writer = new FileWriter(outputFile);
        writer.write(strategyJson);
        writer.flush();
        writer.close();

        System.out.println("end solverTest");
    }
}
