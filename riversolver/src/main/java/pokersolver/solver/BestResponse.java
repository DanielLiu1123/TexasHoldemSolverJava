package pokersolver.solver;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import pokersolver.Card;
import pokersolver.Deck;
import pokersolver.RiverRangeManager;
import pokersolver.compairer.Compairer;
import pokersolver.exceptions.BoardNotFoundException;
import pokersolver.exceptions.NodeNotFoundException;
import pokersolver.nodes.*;
import pokersolver.ranges.PrivateCards;
import pokersolver.ranges.PrivateCardsManager;
import pokersolver.ranges.RiverCombs;
import pokersolver.utils.Range;

/**
 * Created by huangxuefeng on 2019/10/12.
 * best response calculator
 */
public class BestResponse {

    private final Deck deck;
    // player -> preflop combos
    PrivateCards[][] privateCombos;
    int[] playerHands;
    int playerNumber;
    RiverRangeManager rrm;
    PrivateCardsManager pcm;
    boolean debug;

    public BestResponse(
            PrivateCards[][] privateCombos,
            int playerNumber,
            Compairer compairer,
            PrivateCardsManager pcm,
            RiverRangeManager rrm,
            Deck deck,
            boolean debug) {
        this.privateCombos = privateCombos;
        this.playerNumber = playerNumber;
        this.rrm = rrm;
        this.pcm = pcm;
        this.debug = debug;
        this.deck = deck;

        if (privateCombos.length != playerNumber)
            throw new RuntimeException(
                    String.format("river combo length NE player nunber: %d -- %d", privateCombos.length, playerNumber));
        playerHands = new int[playerNumber];
        for (int i = 0; i < playerNumber; i++) {
            playerHands[i] = privateCombos[i].length;
            /*
            int oppo = (i + 1) % player_number;
            if(river_combos[i].length != river_combos[oppo].length){
                throw new RuntimeException("river combo length not match");
            }
             */
        }
    }

    public float printExploitability(GameTreeNode root, int iterationCount, float initialPot, long initialBoard)
            throws BoardNotFoundException {
        float[][] reachProbs = new float[this.playerNumber][];

        System.out.printf("Iter: %d%n", iterationCount);
        float exploitible = 0;
        // 构造双方初始reach probs(按照手牌weights)
        for (int playerId = 0; playerId < this.playerNumber; playerId++) {
            float[] reachProbPlayer = new float[privateCombos[playerId].length];
            for (int hc = 0; hc < privateCombos[playerId].length; hc++)
                reachProbPlayer[hc] = privateCombos[playerId][hc].weight;
            reachProbs[playerId] = reachProbPlayer;
        }

        for (int playerId = 0; playerId < this.playerNumber; playerId++) {
            float playerExploitability = getBestReponseEv(root, playerId, reachProbs, initialBoard);
            exploitible += playerExploitability;
            System.out.printf("player %d exploitability %f%n", playerId, playerExploitability);
        }
        float totalExploitability = exploitible / this.playerNumber / initialPot * 100;
        System.out.printf("Total exploitability %f precent%n", totalExploitability);
        return totalExploitability;
    }

    public float getBestReponseEv(GameTreeNode node, int player, float[][] reachProbs, long initialBoard)
            throws BoardNotFoundException {
        float ev = 0;
        // 考虑（1）相对的手牌 proability,(2)被场面和对手ban掉的手牌
        float[] privateCardsEvs = bestResponse(node, player, reachProbs, initialBoard);
        PrivateCards[] playerCombo = this.privateCombos[player];
        PrivateCards[] oppoCombo = this.privateCombos[1 - player];

        for (int playerHand = 0; playerHand < playerCombo.length; playerHand++) {
            float onePayoff = privateCardsEvs[playerHand];
            PrivateCards onePlayerHand = playerCombo[playerHand];
            long privateLong = onePlayerHand.toBoardLong();
            if (Card.boardsHasIntercept(privateLong, initialBoard)) {
                continue;
            }
            float oppoSum = 0;

            for (PrivateCards oneOppoHand : oppoCombo) {
                long privateLongOppo = oneOppoHand.toBoardLong();
                if (Card.boardsHasIntercept(privateLong, privateLongOppo)
                        || Card.boardsHasIntercept(privateLongOppo, initialBoard)) {
                    continue;
                }
                oppoSum += oneOppoHand.weight;
            }
            ev += onePayoff * onePlayerHand.relativeProb / oppoSum;
        }

        return ev;
    }

    /*
    public float[] getUnblockedComboCounts(PreflopCombo[] heroCombos, PreflopCombo[] villainCombos, int[] initialBoard)
    {
    }
    */

    public float[] bestResponse(GameTreeNode node, int player, float[][] reachProbs, long board) {
        if (node == null) throw new RuntimeException("Node type not understood: null");
        return switch (node) {
            case ActionNode actionNode -> actionBestResponse(actionNode, player, reachProbs, board);
            case ShowdownNode showdownNode -> showdownBestResponse(showdownNode, player, reachProbs, board);
            case TerminalNode terminalNode -> terminalBestReponse(terminalNode, player, reachProbs, board);
            case ChanceNode chanceNode -> chanceBestReponse(chanceNode, player, reachProbs, board);
            default ->
                throw new RuntimeException(String.format(
                        "Node type not understood %s", node.getClass().getName()));
        };
    }

    private float[] chanceBestReponse(ChanceNode node, int player, float[][] reachProbs, long currentBoard) {
        List<Card> cards = this.deck.getCards();
        if (cards.size() != node.getChildren().size()) throw new RuntimeException();
        // float[] cardWeights = getCardsWeights(player,reach_probs[1 - player],current_board);

        // 可能的发牌情况,2代表每个人的holecard是两张
        int possibleDeals = node.getChildren().size() - Card.long2board(currentBoard).length - 2;
        float[] chanceUtility = new float[reachProbs[player].length];
        // 遍历每一种发牌的可能性
        for (int card = 0; card < node.getCards().size(); card++) {
            GameTreeNode oneChild = node.getChildren().get(card);
            Card oneCard = node.getCards().get(card);
            long cardLong = Card.boardCards2long(new Card[] {oneCard});

            // 不可能发出和board重复的牌，对吧
            if (Card.boardsHasIntercept(cardLong, currentBoard)) continue;

            if (oneChild == null || oneCard == null) throw new RuntimeException("child is null");

            PrivateCards[] playerPrivateCard = this.pcm.getPreflopCards(player); // this.getPlayerPrivateCard(player);
            PrivateCards[] oppoPrivateCards = this.pcm.getPreflopCards(1 - player);

            float[][] newReachProbs = new float[2][];

            if (!(reachProbs[player].length == playerPrivateCard.length)) throw new RuntimeException("length mismatch");

            newReachProbs[player] = new float[playerPrivateCard.length];
            newReachProbs[1 - player] = new float[oppoPrivateCards.length];

            // 检查是否双方 hand和reach prob长度符合要求
            if (playerPrivateCard.length != reachProbs[player].length) throw new RuntimeException("length not match");
            if (oppoPrivateCards.length != reachProbs[1 - player].length)
                throw new RuntimeException("length not match");

            for (int onePlayer = 0; onePlayer < 2; onePlayer++) {
                int playerHandLen = this.pcm.getPreflopCards(onePlayer).length;
                for (int playerHand = 0; playerHand < playerHandLen; playerHand++) {
                    PrivateCards onePrivate = this.pcm.getPreflopCards(onePlayer)[playerHand];
                    long privateBoardLong = onePrivate.toBoardLong();
                    if (Card.boardsHasIntercept(cardLong, privateBoardLong)) continue;
                    newReachProbs[onePlayer][playerHand] = reachProbs[onePlayer][playerHand] / possibleDeals;
                }
            }

            if (Card.boardsHasIntercept(currentBoard, cardLong))
                throw new RuntimeException("board has intercept with dealt card");
            long newBoardLong = currentBoard | cardLong;

            float[] childUtility = this.bestResponse(oneChild, player, newReachProbs, newBoardLong);
            if (childUtility.length != chanceUtility.length) throw new RuntimeException("length not match");
            for (int i = 0; i < childUtility.length; i++) chanceUtility[i] += childUtility[i];
        }

        return chanceUtility;
    }

    public float[] actionBestResponse(ActionNode node, int player, float[][] reachProbs, long board) {
        if (player == node.getPlayer()) {
            // 如果是自己在做决定，那么肯定选对自己的最有利的，反之对于对方来说，这个就是我方expliot了对方,
            // 这里可以当成"player"做决定的时候，action prob是0-1分布，因为需要使用最好的策略去expliot对方，最好的策略一定是ont-hot的
            float[] myExploitability = null;
            for (GameTreeNode oneNode : node.getChildren()) {
                float[] nodeEv = this.bestResponse(oneNode, player, reachProbs, board);
                if (myExploitability == null) {
                    myExploitability = nodeEv;
                } else {
                    for (int i : Range.range(nodeEv.length)) {
                        myExploitability[i] = Float.max(myExploitability[i], nodeEv[i]);
                    }
                }
            }
            if (myExploitability == null) throw new RuntimeException("action node has no children");
            if (this.debug) {
                System.out.println("[action]");
                node.printHistory();
                System.out.println(Arrays.toString(myExploitability));
            }
            return myExploitability;
        } else {
            // 如果是别人做决定，那么就按照别人的策略加权算出一个 ev
            float[] totalPayoffs = new float[playerHands[player]];

            float[] nodeStrategy = Objects.requireNonNull(node.getTrainable(), "trainable not set")
                    .getAverageStrategy();
            if (nodeStrategy.length != node.getChildren().size() * reachProbs[node.getPlayer()].length) {
                throw new RuntimeException(String.format(
                        "strategy size not match %d - %d",
                        nodeStrategy.length, node.getChildren().size() * reachProbs[node.getPlayer()].length));
            }

            // 构造reach probs矩阵
            for (int actionInd = 0; actionInd < node.getChildren().size(); actionInd++) {
                float[][] nextReachProbs = new float[this.playerNumber][];
                for (int i = 0; i < this.playerNumber; i++) {
                    if (i == node.getPlayer()) {
                        int privateComboNumbers = reachProbs[i].length;
                        float[] nextReachProbsCurrentPlayer = new float[privateComboNumbers];
                        for (int j = 0; j < privateComboNumbers; j++) {
                            nextReachProbsCurrentPlayer[j] =
                                    reachProbs[node.getPlayer()][j] * nodeStrategy[actionInd * privateComboNumbers + j];
                        }
                        nextReachProbs[i] = nextReachProbsCurrentPlayer;
                    } else {
                        nextReachProbs[i] = reachProbs[i];
                    }
                }

                GameTreeNode oneChild = node.getChildren().get(actionInd);
                if (oneChild == null) throw new NodeNotFoundException("child node not found");
                float[] actionPayoffs = this.bestResponse(oneChild, player, nextReachProbs, board);
                if (actionPayoffs.length != totalPayoffs.length)
                    throw new RuntimeException(String.format(
                            "length not match between action payoffs and total payoffs %d -- %d",
                            actionPayoffs.length, totalPayoffs.length));

                for (int i = 0; i < totalPayoffs.length; i++) {
                    totalPayoffs[i] += actionPayoffs[i]; //  * node_strategy[i] 的动作实际上已经在递归的时候做过了，所以这里不需要乘
                }
            }
            if (this.debug) {
                System.out.println("[action]");
                node.printHistory();
                System.out.println(Arrays.toString(totalPayoffs));
            }
            return totalPayoffs;
        }
    }

    /*
    public float[] chanceBestReponse(ChanceNode node, float[] villainReachProbs, int[] board)
    {
    }
    */

    public float[] terminalBestReponse(TerminalNode node, int player, float[][] reachProbs, long board) {
        int oppo = 1 - player;
        RiverCombs[] playerCombs =
                this.rrm.getRiverCombos(player, this.pcm.getPreflopCards(player), board); // this.river_combos[player];
        RiverCombs[] oppoCombs = this.rrm.getRiverCombos(
                1 - player, this.pcm.getPreflopCards(1 - player), board); // this.river_combos[player];

        double playerPayoff = node.getPayoffs()[player];
        float[] payoffs = new float[playerHands[player]];

        if (this.playerNumber != 2) throw new RuntimeException("player NE 2 not supported");
        // 对手的手牌可能需要和其reach prob一样长
        // 这里用了hard code，因为一副牌，不管是长牌还是短牌，最多扑克牌的数量都是52张
        float[] oppoCardSum = new float[52];

        // 用于记录对手总共的手牌绝对prob之和
        float oppoProbSum = 0;

        float[] oppoReachProb = reachProbs[1 - player];
        for (RiverCombs oneHc : oppoCombs) {
            long oneHcLong = Card.boardInts2long(new int[] {oneHc.privateCards.card1, oneHc.privateCards.card2});

            // 如果对手手牌和public card有重叠，那么这组牌不可能存在
            if (Card.boardsHasIntercept(oneHcLong, board)) {
                continue;
            }

            oppoProbSum += oppoReachProb[oneHc.reachProbIndex];
            oppoCardSum[oneHc.privateCards.card1] += oppoReachProb[oneHc.reachProbIndex];
            oppoCardSum[oneHc.privateCards.card2] += oppoReachProb[oneHc.reachProbIndex];
        }

        for (int playerHand = 0; playerHand < playerCombs.length; playerHand++) {
            RiverCombs playerHc = playerCombs[playerHand];
            long playerHcLong =
                    Card.boardInts2long(new int[] {playerHc.privateCards.card1, playerHc.privateCards.card2});
            if (Card.boardsHasIntercept(playerHcLong, board)) {
                payoffs[playerHand] = 0;
            } else {
                Integer oppoHand = this.pcm.indPlayer2Player(player, oppo, playerHc.reachProbIndex);
                float addReachProb;
                if (oppoHand == null) {
                    addReachProb = 0;
                } else {
                    addReachProb = oppoReachProb[oppoHand];
                }
                payoffs[playerHc.reachProbIndex] = (oppoProbSum
                                - oppoCardSum[playerHc.privateCards.card1]
                                - oppoCardSum[playerHc.privateCards.card2]
                                + addReachProb)
                        * (float) playerPayoff;
            }
        }

        if (this.debug) {
            System.out.println("[terminal]");
            node.printHistory();
            System.out.println(Arrays.toString(payoffs));
        }
        return payoffs;
    }

    /*
    //assumes that both players got allin on the turn
    float[] allinBestResponse(TerminalNode node, float[] villainReachProbs, int[] board)
    {
    }
    */

    float[] showdownBestResponse(ShowdownNode node, int player, float[][] reachProbs, long board) {
        if (this.playerNumber != 2) throw new RuntimeException("player number is not 2");

        int oppo = 1 - player;
        RiverCombs[] playerCombs =
                this.rrm.getRiverCombos(player, this.pcm.getPreflopCards(player), board); // this.river_combos[player];
        RiverCombs[] oppoCombs = this.rrm.getRiverCombos(
                1 - player, this.pcm.getPreflopCards(1 - player), board); // this.river_combos[player];

        float winPayoff = (float) node.getPayoffs(ShowdownNode.ShowDownResult.NOTTIE, player)[player];
        // hard code, 假设了player只有两个
        float losePayoff = (float) node.getPayoffs(ShowdownNode.ShowDownResult.NOTTIE, 1 - player)[player];

        float[] payoffs = ShowdownPayoffs.compute(
                playerCombs, oppoCombs, reachProbs[oppo], winPayoff, losePayoff, playerHands[player]);

        if (this.debug) {
            System.out.println("[showdown]");
            node.printHistory();
            System.out.println(Arrays.toString(payoffs));
        }
        return payoffs;
    }
}
