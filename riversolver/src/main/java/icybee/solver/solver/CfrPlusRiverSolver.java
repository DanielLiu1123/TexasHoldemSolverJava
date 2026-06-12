package icybee.solver.solver;

import static icybee.solver.utils.JsonUtil.MAPPER;

import icybee.solver.Card;
import icybee.solver.exceptions.BoardNotFoundException;
import icybee.solver.nodes.*;
import icybee.solver.ranges.PrivateCards;
import icybee.solver.ranges.RiverCombs;
import icybee.solver.trainable.Trainable;
import icybee.solver.utils.SimdOps;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import tools.jackson.databind.node.ObjectNode;

/** Single-threaded CFR+ solver. */
public class CfrPlusRiverSolver extends AbstractCfrSolver {

    public CfrPlusRiverSolver(SolverConfig config) {
        super(config);
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

    float[] cfr(int player, GameTreeNode node, float[][] reachProbs, int iter, long currentBoard)
            throws BoardNotFoundException {
        return switch (node.getType()) {
            case ACTION -> actionUtility(player, (ActionNode) node, reachProbs, iter, currentBoard);
            case SHOWDOWN -> showdownUtility(player, (ShowdownNode) node, reachProbs, iter, currentBoard);
            case TERMINAL -> terminalUtility(player, (TerminalNode) node, reachProbs, iter, currentBoard);
            case CHANCE -> chanceUtility(player, (ChanceNode) node, reachProbs, iter, currentBoard);
            default -> throw new RuntimeException("node type unknown");
        };
    }

    float[] getCardsWeights(int player, float[] oppoReachProbs, long currentBoard) {
        PrivateCards[] playerPrivateCard = this.ranges[player];
        PrivateCards[] oppoPrivateCard = this.ranges[1 - player];
        float[] cardWeight = new float[playerPrivateCard.length * 52];
        float oppoReachSum = 0;
        float[] oppoCardWeightSum = new float[52];

        if (oppoReachProbs.length != oppoPrivateCard.length) throw new RuntimeException("oppo cards length not match");
        for (int hand = 0; hand < oppoReachProbs.length; hand++) {
            PrivateCards oneOppoCard = oppoPrivateCard[hand];
            oppoReachSum += oneOppoCard.weight;
            oppoCardWeightSum[oneOppoCard.card1] += oneOppoCard.weight;
            oppoCardWeightSum[oneOppoCard.card2] += oneOppoCard.weight;
        }

        for (int hand = 0; hand < playerPrivateCard.length; hand++) {
            PrivateCards onePlayerHand = playerPrivateCard[hand];
            if (Card.boardsHasIntercept(Card.privateHand2long(onePlayerHand), currentBoard)) continue;

            float totalWeight = 0;

            for (Card oneCard : this.deck.getCards()) {
                int card = oneCard.getCardInt();
                long cardLong = Card.boardCard2long(oneCard);
                if (Card.boardsHasIntercept(cardLong, currentBoard)
                        || Card.boardsHasIntercept(Card.privateHand2long(onePlayerHand), cardLong)) continue;

                float weight = oppoReachSum
                        - oppoCardWeightSum[card]
                        - oppoCardWeightSum[playerPrivateCard[hand].card1]
                        - oppoCardWeightSum[playerPrivateCard[hand].card2]
                        + oppoReachProbs[hand];

                cardWeight[hand + card * playerPrivateCard.length] = weight;

                totalWeight += weight;
            }

            for (Card oneCard : this.deck.getCards()) {
                int card = oneCard.getCardInt();
                long cardLong = Card.boardCard2long(oneCard);
                int cardWeightInd = hand + card * playerPrivateCard.length;
                if (!Card.boardsHasIntercept(cardLong, currentBoard)
                        && totalWeight > 0
                        && cardWeight[cardWeightInd] > 0) {
                    cardWeight[cardWeightInd] /= totalWeight;
                }
            }
        }

        return cardWeight;
    }

    float[] chanceUtility(int player, ChanceNode node, float[][] reachProbs, int iter, long currentBoard)
            throws BoardNotFoundException {
        List<Card> cards = this.deck.getCards();
        if (cards.size() != node.getChildren().size()) throw new RuntimeException();

        int possibleDeals = node.getChildren().size() - Card.long2board(currentBoard).length - 2;

        float[] chanceUtility = new float[reachProbs[player].length];
        int randomDeal = 0, cardcount = 0;
        if (this.monteCarloAlg == MonteCarloAlg.PUBLIC) {
            randomDeal = ThreadLocalRandom.current().nextInt(1, possibleDeals + 1 + 2);
        }
        for (int card = 0; card < node.getCards().size(); card++) {
            GameTreeNode oneChild = node.getChildren().get(card);
            Card oneCard = node.getCards().get(card);
            long cardLong = Card.boardCards2long(new Card[] {oneCard});

            if (Card.boardsHasIntercept(cardLong, currentBoard)) continue;
            cardcount += 1;

            if (oneChild == null || oneCard == null) throw new RuntimeException("child is null");

            long newBoardLong = currentBoard | cardLong;
            if (this.monteCarloAlg == MonteCarloAlg.PUBLIC) {
                if (cardcount == randomDeal) {
                    float[][] newReachProbs = new float[2][];

                    newReachProbs[player] = new float[reachProbs[player].length];
                    newReachProbs[1 - player] = new float[reachProbs[1 - player].length];

                    for (int onePlayer = 0; onePlayer < 2; onePlayer++) {
                        int playerHandLen = this.ranges[onePlayer].length;
                        for (int playerHand = 0; playerHand < playerHandLen; playerHand++) {
                            PrivateCards onePrivate = this.ranges[onePlayer][playerHand];
                            long privateBoardLong = onePrivate.toBoardLong();
                            if (Card.boardsHasIntercept(cardLong, privateBoardLong)) continue;
                            newReachProbs[onePlayer][playerHand] = reachProbs[onePlayer][playerHand];
                        }
                    }
                    return this.cfr(player, oneChild, newReachProbs, iter, newBoardLong);
                } else {
                    continue;
                }
            }

            PrivateCards[] playerPrivateCard = this.ranges[player];
            PrivateCards[] oppoPrivateCards = this.ranges[1 - player];

            float[][] newReachProbs = new float[2][];

            newReachProbs[player] = new float[playerPrivateCard.length];
            newReachProbs[1 - player] = new float[oppoPrivateCards.length];

            if (playerPrivateCard.length != reachProbs[player].length) throw new RuntimeException("length not match");
            if (oppoPrivateCards.length != reachProbs[1 - player].length)
                throw new RuntimeException("length not match");

            for (int onePlayer = 0; onePlayer < 2; onePlayer++) {
                int playerHandLen = this.ranges[onePlayer].length;
                for (int playerHand = 0; playerHand < playerHandLen; playerHand++) {
                    PrivateCards onePrivate = this.ranges[onePlayer][playerHand];
                    long privateBoardLong = onePrivate.toBoardLong();
                    if (Card.boardsHasIntercept(cardLong, privateBoardLong)) continue;
                    newReachProbs[onePlayer][playerHand] = reachProbs[onePlayer][playerHand] / possibleDeals;
                }
            }

            if (Card.boardsHasIntercept(currentBoard, cardLong))
                throw new RuntimeException("board has intercept with dealt card");

            float[] childUtility = this.cfr(player, oneChild, newReachProbs, iter, newBoardLong);
            if (childUtility.length != chanceUtility.length) throw new RuntimeException("length not match");
            for (int i = 0; i < childUtility.length; i++) chanceUtility[i] += childUtility[i];
        }

        if (this.monteCarloAlg == MonteCarloAlg.PUBLIC) {
            throw new RuntimeException("not possible");
        }
        return chanceUtility;
    }

    float[] actionUtility(int player, ActionNode node, float[][] reachProbs, int iter, long currentBoard)
            throws BoardNotFoundException {
        PrivateCards[] nodePlayerPrivateCards = this.ranges[node.getPlayer()];
        Trainable trainable = Objects.requireNonNull(node.getTrainable(), "trainable not set");

        float[] payoffs = new float[this.ranges[player].length];
        List<GameTreeNode> children = node.getChildren();
        List<GameActions> actions = node.getActions();

        float[] currentStrategy = trainable.getcurrentStrategy();
        if (this.debug) {
            for (float oneStrategy : currentStrategy) {
                if (Float.isNaN(oneStrategy)) {
                    System.out.println(Arrays.toString(currentStrategy));
                    throw new RuntimeException();
                }
            }
            for (int onePlayer = 0; onePlayer < this.player_number; onePlayer++) {
                float[] oneReachProb = reachProbs[onePlayer];
                for (float oneProb : oneReachProb) {
                    if (Float.isNaN(oneProb)) throw new RuntimeException();
                }
            }
        }
        float[][] newReachProb = new float[this.player_number][];
        if (currentStrategy.length != actions.size() * nodePlayerPrivateCards.length) {
            node.printHistory();
            throw new RuntimeException(String.format(
                    "length not match %s - %s \n action size %s private_card size %s",
                    currentStrategy.length,
                    actions.size() * nodePlayerPrivateCards.length,
                    actions.size(),
                    nodePlayerPrivateCards.length));
        }

        float[] regrets = new float[actions.size() * nodePlayerPrivateCards.length];

        float[][] allActionUtility = new float[actions.size()][];
        int nodePlayer = node.getPlayer();
        newReachProb[1 - nodePlayer] = reachProbs[1 - nodePlayer];
        for (int actionId = 0; actionId < actions.size(); actionId++) {
            float[] playerNewReach = new float[reachProbs[nodePlayer].length];
            SimdOps.mul(
                    currentStrategy,
                    actionId * nodePlayerPrivateCards.length,
                    reachProbs[nodePlayer],
                    playerNewReach,
                    playerNewReach.length);
            newReachProb[nodePlayer] = playerNewReach;
            float[] actionUtilities = this.cfr(player, children.get(actionId), newReachProb, iter, currentBoard);
            allActionUtility[actionId] = actionUtilities;

            if (actionUtilities.length != payoffs.length) {
                System.out.println("errmsg");
                System.out.println(String.format("node player %s ", node.getPlayer()));
                node.printHistory();
                throw new RuntimeException(String.format(
                        "action and payoff length not match %s - %s", actionUtilities.length, payoffs.length));
            }

            if (player == node.getPlayer()) {
                SimdOps.fma(
                        currentStrategy,
                        actionId * nodePlayerPrivateCards.length,
                        actionUtilities,
                        payoffs,
                        actionUtilities.length);
            } else {
                SimdOps.add(actionUtilities, payoffs, actionUtilities.length);
            }
        }

        if (player == node.getPlayer()) {
            for (int actionId = 0; actionId < actions.size(); actionId++) {
                SimdOps.sub(
                        allActionUtility[actionId],
                        payoffs,
                        regrets,
                        actionId * nodePlayerPrivateCards.length,
                        nodePlayerPrivateCards.length);
            }
            trainable.updateRegrets(regrets, iter + 1, reachProbs[player]);
        }

        return payoffs;
    }

    float[] showdownUtility(int player, ShowdownNode node, float[][] reachProbs, int iter, long currentBoard)
            throws BoardNotFoundException {
        int oppo = 1 - player;
        float winPayoff = (float) node.get_payoffs(ShowdownNode.ShowDownResult.NOTTIE, player)[player];
        float losePayoff = (float) node.get_payoffs(ShowdownNode.ShowDownResult.NOTTIE, oppo)[player];
        PrivateCards[] playerPrivateCards = this.ranges[player];
        PrivateCards[] oppoPrivateCards = this.ranges[oppo];

        RiverCombs[] playerCombs = this.rrm.getRiverCombos(player, playerPrivateCards, currentBoard);
        RiverCombs[] oppoCombs = this.rrm.getRiverCombos(oppo, oppoPrivateCards, currentBoard);

        float[] payoffs = new float[playerPrivateCards.length];

        float winsum = 0;
        float[] cardWinsum = new float[52];

        int j = 0;

        if (this.debug) {
            System.out.println("[PRESHOWDOWN]=======================");
            System.out.println(String.format("player0 reach_prob %s", Arrays.toString(reachProbs[0])));
            System.out.println(String.format("player1 reach_prob %s", Arrays.toString(reachProbs[1])));
            System.out.print("preflop combos: ");
            for (RiverCombs oneRiverComb : playerCombs) {
                System.out.print(String.format("%s(%s) ", oneRiverComb.private_cards.toString(), oneRiverComb.rank));
            }
            System.out.println();
        }

        for (int i = 0; i < playerCombs.length; i++) {
            RiverCombs onePlayerComb = playerCombs[i];
            while (j < oppoCombs.length && onePlayerComb.rank < oppoCombs[j].rank) {
                RiverCombs oneOppoComb = oppoCombs[j];
                winsum += reachProbs[oppo][oneOppoComb.reach_prob_index];
                if (this.debug) {
                    if (onePlayerComb.reach_prob_index == 0) {
                        System.out.print(String.format(
                                "[%s]%s:%s-%s(%s) ",
                                j,
                                oneOppoComb.private_cards.toString(),
                                this.ranges[oppo][oneOppoComb.reach_prob_index].weight,
                                winsum,
                                oneOppoComb.rank));
                    }
                }

                cardWinsum[oneOppoComb.private_cards.card1] += reachProbs[oppo][oneOppoComb.reach_prob_index];
                cardWinsum[oneOppoComb.private_cards.card2] += reachProbs[oppo][oneOppoComb.reach_prob_index];
                j++;
            }
            if (this.debug) {
                System.out.println(String.format(
                        "Before Adding %s, win_payoff %s winsum %s, subcard1 %s subcard2 %s",
                        payoffs[onePlayerComb.reach_prob_index],
                        winPayoff,
                        winsum,
                        -cardWinsum[onePlayerComb.private_cards.card1],
                        -cardWinsum[onePlayerComb.private_cards.card2]));
            }
            payoffs[onePlayerComb.reach_prob_index] = (winsum
                            - cardWinsum[onePlayerComb.private_cards.card1]
                            - cardWinsum[onePlayerComb.private_cards.card2])
                    * winPayoff;
            if (this.debug) {
                if (onePlayerComb.reach_prob_index == 0) {
                    System.out.println(String.format("winsum %s", winsum));
                }
            }
        }

        float losssum = 0;
        float[] cardLosssum = new float[52];
        for (int i = 0; i < cardLosssum.length; i++) cardLosssum[i] = 0;

        j = oppoCombs.length - 1;
        for (int i = playerCombs.length - 1; i >= 0; i--) {
            RiverCombs onePlayerComb = playerCombs[i];
            while (j >= 0 && onePlayerComb.rank > oppoCombs[j].rank) {
                RiverCombs oneOppoComb = oppoCombs[j];
                losssum += reachProbs[oppo][oneOppoComb.reach_prob_index];
                if (this.debug) {
                    if (onePlayerComb.reach_prob_index == 0) {
                        System.out.print(String.format(
                                "lose %s:%s ",
                                oneOppoComb.private_cards.toString(),
                                this.ranges[oppo][oneOppoComb.reach_prob_index].weight));
                    }
                }

                cardLosssum[oneOppoComb.private_cards.card1] += reachProbs[oppo][oneOppoComb.reach_prob_index];
                cardLosssum[oneOppoComb.private_cards.card2] += reachProbs[oppo][oneOppoComb.reach_prob_index];
                j--;
            }
            if (this.debug) {
                System.out.println(String.format("Before Substract %s", payoffs[onePlayerComb.reach_prob_index]));
            }
            payoffs[onePlayerComb.reach_prob_index] += (losssum
                            - cardLosssum[onePlayerComb.private_cards.card1]
                            - cardLosssum[onePlayerComb.private_cards.card2])
                    * losePayoff;
            if (this.debug) {
                if (onePlayerComb.reach_prob_index == 0) {
                    System.out.println(String.format("losssum %s", losssum));
                }
            }
        }
        if (this.debug) {
            System.out.println();
            System.out.println("[SHOWDOWN]============");
            node.printHistory();
            System.out.println(String.format("loss payoffs: %s", losePayoff));
            System.out.println(String.format("oppo sum %s, substracted payoff %s", losssum, payoffs[0]));
        }
        return payoffs;
    }

    float[] terminalUtility(int player, TerminalNode node, float[][] reachProb, int iter, long currentBoard)
            throws BoardNotFoundException {

        double playerPayoff = node.get_payoffs()[player];

        int oppo = 1 - player;
        PrivateCards[] playerHand = playerHands(player);
        PrivateCards[] oppoHand = playerHands(oppo);

        float[] payoffs = new float[this.playerHands(player).length];

        float oppoSum = 0;
        float[] oppoCardSum = new float[52];
        Arrays.fill(oppoCardSum, 0);

        for (int i = 0; i < oppoHand.length; i++) {
            oppoCardSum[oppoHand[i].card1] += reachProb[oppo][i];
            oppoCardSum[oppoHand[i].card2] += reachProb[oppo][i];
            oppoSum += reachProb[oppo][i];
        }

        if (this.debug) {
            System.out.println("[PRETERMINAL]============");
        }
        for (int i = 0; i < playerHand.length; i++) {
            PrivateCards onePlayerHand = playerHand[i];
            if (Card.boardsHasIntercept(
                    currentBoard, Card.boardInts2long(new int[] {onePlayerHand.card1, onePlayerHand.card2}))) {
                continue;
            }

            Integer oppoSameCardInd = this.pcm.indPlayer2Player(player, oppo, i);
            float plusReachProb;
            if (oppoSameCardInd == null) {
                plusReachProb = 0;
            } else {
                plusReachProb = reachProb[oppo][oppoSameCardInd];
            }
            payoffs[i] = (float) playerPayoff
                    * (oppoSum - oppoCardSum[onePlayerHand.card1] - oppoCardSum[onePlayerHand.card2] + plusReachProb);
            if (this.debug) {
                System.out.println(String.format("oppo_card_sum1 %s ", oppoCardSum[onePlayerHand.card1]));
                System.out.println(String.format("oppo_card_sum2 %s ", oppoCardSum[onePlayerHand.card2]));
                System.out.println(String.format("reach_prob i %s ", plusReachProb));
            }
        }

        if (this.debug) {
            System.out.println("[TERMINAL]============");
            node.printHistory();
            System.out.println(String.format("PPPayoffs: %s", playerPayoff));
            System.out.println(String.format("reach prob %s", reachProb[oppo][0]));
            System.out.println(String.format("oppo sum %s, substracted sum %s", oppoSum, payoffs[0] / playerPayoff));
            System.out.println(String.format("substracted sum %s", payoffs[0]));
        }
        return payoffs;
    }
}
