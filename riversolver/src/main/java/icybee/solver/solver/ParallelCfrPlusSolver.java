package icybee.solver.solver;

import static icybee.solver.utils.JsonUtil.MAPPER;

import icybee.solver.Card;
import icybee.solver.Deck;
import icybee.solver.GameTree;
import icybee.solver.compairer.Compairer;
import icybee.solver.nodes.*;
import icybee.solver.ranges.PrivateCards;
import icybee.solver.ranges.RiverCombs;
import icybee.solver.trainable.DiscountedCfrTrainable;
import icybee.solver.trainable.Trainable;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/** Parallel CFR+ solver using ForkJoin work-stealing. */
public class ParallelCfrPlusSolver extends AbstractCfrSolver {

    ForkJoinPool forkJoinPool;
    int nthreads;
    double forkprob_action;
    double forkprob_chance;
    int fork_every_n_depth;
    int no_fork_subtree_size;

    public ParallelCfrPlusSolver(
            GameTree tree,
            PrivateCards[] range1,
            PrivateCards[] range2,
            int[] initialBoard,
            Compairer compairer,
            Deck deck,
            int iterationNumber,
            boolean debug,
            int printInterval,
            @Nullable String logfile,
            Class<?> trainer,
            MonteCarolAlg monteCarolAlg,
            int nthreads,
            double forkprobAction,
            double forkprobChance,
            int forkBetween,
            int noForkSubtreeSize) {
        super(
                tree,
                range1,
                range2,
                initialBoard,
                compairer,
                deck,
                iterationNumber,
                debug,
                printInterval,
                logfile,
                trainer,
                monteCarolAlg);
        if (nthreads >= 1) {
            this.nthreads = nthreads;
        } else if (nthreads == -1) {
            this.nthreads = Runtime.getRuntime().availableProcessors();
        } else {
            throw new RuntimeException("nthread not correct");
        }
        this.forkJoinPool = new ForkJoinPool(this.nthreads);
        if (forkprobAction > 1 || forkprobAction < 0)
            throw new RuntimeException(String.format("forkprob action not between [0,1] : %s", forkprobAction));
        if (forkprobChance > 1 || forkprobChance < 0)
            throw new RuntimeException(String.format("forkprob chance not between [0,1] : %s", forkprobChance));
        this.forkprob_action = forkprobAction;
        this.forkprob_chance = forkprobChance;
        this.fork_every_n_depth = forkBetween;
        this.no_fork_subtree_size = noForkSubtreeSize;
        System.out.println(String.format("Using %s threads", this.nthreads));
    }

    private double getDoubleValue(Map<String, Object> meta, String key) {
        Object value = meta.get(key);
        if (value instanceof Integer i) {
            return i.doubleValue();
        } else if (value instanceof Double d) {
            return d;
        } else {
            return 0.0;
        }
    }

    @Override
    public void train(Map training_config) throws Exception {
        setTrainable(tree.getRoot());

        PrivateCards[][] playerPrivates = new PrivateCards[this.player_number][];
        playerPrivates[0] = pcm.getPreflopCards(0);
        playerPrivates[1] = pcm.getPreflopCards(1);

        BestResponse br = new BestResponse(
                playerPrivates, this.player_number, this.compairer, this.pcm, this.rrm, this.deck, this.debug);

        br.printExploitability(tree.getRoot(), 0, tree.getRoot().getPot().floatValue(), initial_board_long);

        float[][] reachProbs = this.getReachProbs();

        long begintime = System.currentTimeMillis();
        long endtime = System.currentTimeMillis();

        double stopExploitability = this.getDoubleValue(training_config, "stop_exploitibility");
        try (Writer fileWriter = this.logfile != null
                ? Files.newBufferedWriter(Paths.get(this.logfile), StandardCharsets.UTF_8)
                : Writer.nullWriter()) {
            for (int i = 0; i < this.iteration_number; i++) {
                for (int playerId = 0; playerId < this.player_number; playerId++) {
                    if (this.debug) {
                        System.out.println(String.format(
                                "---------------------------------     player %s --------------------------------",
                                playerId));
                    }
                    this.round_deal = new int[] {-1, -1, -1, -1};

                    CfrTask task =
                            new CfrTask(playerId, this.tree.getRoot(), reachProbs, i, this.initial_board_long, this);
                    forkJoinPool.invoke(task);
                }
                if (i % this.print_interval == 0) {
                    endtime = System.currentTimeMillis();
                    long timeMs = endtime - begintime;
                    System.out.println(String.format("time used: %.2fs", (float) timeMs / 1000));
                    System.out.println("-------------------");
                    float exploitability = br.printExploitability(
                            tree.getRoot(), i + 1, tree.getRoot().getPot().floatValue(), initial_board_long);
                    ObjectNode jo = MAPPER.createObjectNode();
                    jo.put("iteration", i);
                    jo.put("exploitability", exploitability);
                    jo.put("time_ms", timeMs);
                    fileWriter.write(String.format("%s\n", jo.toString()));
                    if (stopExploitability > exploitability) break;
                }
            }
        }
        endtime = System.currentTimeMillis();
        long timeMs = endtime - begintime;
        System.out.println("++++++++++++++++");
        System.out.println(String.format("solve finish, total time used: %.2fs", (float) timeMs / 1000));
        forkJoinPool.shutdown();
    }

    class CfrTask extends RecursiveTask<float[]> {
        int player;
        GameTreeNode node;
        float[][] reach_probs;
        int iter;
        long current_board;
        ParallelCfrPlusSolver solver_env;

        public CfrTask(
                int player,
                GameTreeNode node,
                float[][] reach_probs,
                int iter,
                long current_board,
                ParallelCfrPlusSolver solver_env) {
            this.player = player;
            this.node = node;
            this.reach_probs = reach_probs;
            this.iter = iter;
            this.current_board = current_board;
            this.solver_env = solver_env;
        }

        @Override
        protected float[] compute() {
            return this.cfr(this.player, this.node, this.reach_probs, this.iter, this.current_board);
        }

        float[] cfr(int player, GameTreeNode node, float[][] reach_probs, int iter, long current_board) {
            return switch (node.getType()) {
                case ACTION -> actionUtility(player, (ActionNode) node, reach_probs, iter, current_board);
                case SHOWDOWN -> showdownUtility(player, (ShowdownNode) node, reach_probs, iter, current_board);
                case TERMINAL -> terminalUtility(player, (TerminalNode) node, reach_probs, iter, current_board);
                case CHANCE -> chanceUtility(player, (ChanceNode) node, reach_probs, iter, current_board);
                default -> throw new RuntimeException("node type unknown");
            };
        }

        float[] chanceUtility(int player, ChanceNode node, float[][] reach_probs, int iter, long current_board) {
            List<Card> cards = this.solver_env.deck.getCards();
            if (cards.size() != node.getChildrens().size()) throw new RuntimeException();

            int possible_deals = node.getChildrens().size() - Card.long2board(current_board).length - 2;

            float[] chance_utility = new float[reach_probs[player].length];
            int random_deal = 0, cardcount = 0;
            if (this.solver_env.monteCarolAlg == MonteCarolAlg.PUBLIC) {
                if (this.solver_env.round_deal[GameTreeNode.gameRound2int(node.getRound())] == -1) {
                    random_deal = ThreadLocalRandom.current().nextInt(1, possible_deals + 1 + 2);
                    this.solver_env.round_deal[GameTreeNode.gameRound2int(node.getRound())] = random_deal;
                } else {
                    random_deal = this.solver_env.round_deal[GameTreeNode.gameRound2int(node.getRound())];
                }
            }
            CfrTask[] tasklist = new CfrTask[node.getCards().size()];
            boolean forkAt = false;
            if (this.solver_env.forkprob_chance == 1) {
                forkAt = true;
            } else if (this.solver_env.forkprob_chance == 0) {
                forkAt = false;
            } else if (Math.random() < this.solver_env.forkprob_chance) {
                forkAt = true;
            }

            if (node.depth % this.solver_env.fork_every_n_depth != 0
                    || node.subtree_size <= this.solver_env.no_fork_subtree_size) forkAt = false;

            for (int card = 0; card < node.getCards().size(); card++) {
                GameTreeNode one_child = node.getChildrens().get(card);
                Card one_card = node.getCards().get(card);
                long card_long = Card.boardCards2long(new Card[] {one_card});

                if (Card.boardsHasIntercept(card_long, current_board)) continue;
                cardcount += 1;

                if (one_child == null || one_card == null) throw new RuntimeException("child is null");

                long new_board_long = current_board | card_long;
                if (this.solver_env.monteCarolAlg == MonteCarolAlg.PUBLIC) {
                    if (cardcount == random_deal) {
                        CfrTask task =
                                new CfrTask(this.player, one_child, reach_probs, iter, new_board_long, this.solver_env);
                        return task.compute();
                    } else {
                        continue;
                    }
                }

                PrivateCards[] playerPrivateCard = this.solver_env.ranges[player];
                PrivateCards[] oppoPrivateCards = this.solver_env.ranges[1 - player];

                float[][] new_reach_probs = new float[2][];

                new_reach_probs[player] = new float[playerPrivateCard.length];
                new_reach_probs[1 - player] = new float[oppoPrivateCards.length];

                if (playerPrivateCard.length != reach_probs[player].length)
                    throw new RuntimeException("length not match");
                if (oppoPrivateCards.length != reach_probs[1 - player].length)
                    throw new RuntimeException("length not match");

                for (int one_player = 0; one_player < 2; one_player++) {
                    int player_hand_len = this.solver_env.ranges[one_player].length;
                    for (int player_hand = 0; player_hand < player_hand_len; player_hand++) {
                        PrivateCards one_private = this.solver_env.ranges[one_player][player_hand];
                        long privateBoardLong = one_private.toBoardLong();
                        if (Card.boardsHasIntercept(card_long, privateBoardLong)) continue;
                        new_reach_probs[one_player][player_hand] =
                                reach_probs[one_player][player_hand] / possible_deals;
                    }
                }

                if (Card.boardsHasIntercept(current_board, card_long))
                    throw new RuntimeException("board has intercept with dealt card");

                CfrTask task =
                        new CfrTask(this.player, one_child, new_reach_probs, iter, new_board_long, this.solver_env);
                if (forkAt) task.fork();
                tasklist[card] = task;
            }

            for (int card = 0; card < node.getCards().size(); card++) {
                CfrTask task = tasklist[card];
                if (task == null) continue;
                float[] child_utility;
                if (forkAt) {
                    child_utility = task.join();
                } else {
                    child_utility = task.compute();
                }
                if (child_utility.length != chance_utility.length) throw new RuntimeException("length not match");
                for (int i = 0; i < child_utility.length; i++) chance_utility[i] += child_utility[i];
            }

            if (this.solver_env.monteCarolAlg == MonteCarolAlg.PUBLIC) {
                throw new RuntimeException("not possible");
            }
            return chance_utility;
        }

        float[] actionUtility(int player, ActionNode node, float[][] reach_probs, int iter, long current_board) {
            PrivateCards[] node_player_private_cards = this.solver_env.ranges[node.getPlayer()];
            Trainable trainable = Objects.requireNonNull(node.getTrainable(), "trainable not set");

            float[] payoffs = new float[this.solver_env.ranges[player].length];
            List<GameTreeNode> children = node.getChildrens();
            List<GameActions> actions = node.getActions();

            boolean forkAt = false;
            if (this.solver_env.forkprob_action == 1) {
                forkAt = true;
            } else if (this.solver_env.forkprob_action == 0) {
                forkAt = false;
            } else if (Math.random() < this.solver_env.forkprob_action) {
                forkAt = true;
            }

            if (node.depth % this.solver_env.fork_every_n_depth != 0
                    || node.subtree_size <= this.solver_env.no_fork_subtree_size) forkAt = false;

            float[] current_strategy = trainable.getcurrentStrategy();
            if (this.solver_env.debug) {
                for (float one_strategy : current_strategy) {
                    if (Float.isNaN(one_strategy)) {
                        System.out.println(Arrays.toString(current_strategy));
                        throw new RuntimeException();
                    }
                }
                for (int one_player = 0; one_player < this.solver_env.player_number; one_player++) {
                    float[] one_reach_prob = reach_probs[one_player];
                    for (float one_prob : one_reach_prob) {
                        if (Float.isNaN(one_prob)) throw new RuntimeException();
                    }
                }
            }
            if (current_strategy.length != actions.size() * node_player_private_cards.length) {
                node.printHistory();
                throw new RuntimeException(String.format(
                        "length not match %s - %s \n action size %s private_card size %s",
                        current_strategy.length,
                        actions.size() * node_player_private_cards.length,
                        actions.size(),
                        node_player_private_cards.length));
            }

            float[] regrets = new float[actions.size() * node_player_private_cards.length];

            float[][] all_action_utility = new float[actions.size()][];
            int node_player = node.getPlayer();

            CfrTask[] tasklist = new CfrTask[actions.size()];

            for (int action_id = 0; action_id < actions.size(); action_id++) {
                float[][] new_reach_prob = new float[this.solver_env.player_number][];
                new_reach_prob[1 - node_player] = reach_probs[1 - node_player];
                float[] player_new_reach = new float[reach_probs[node_player].length];
                for (int hand_id = 0; hand_id < player_new_reach.length; hand_id++) {
                    float strategy_prob = current_strategy[hand_id + action_id * node_player_private_cards.length];
                    player_new_reach[hand_id] = reach_probs[node_player][hand_id] * strategy_prob;
                }
                new_reach_prob[node_player] = player_new_reach;

                CfrTask task = new CfrTask(
                        this.player, children.get(action_id), new_reach_prob, iter, current_board, this.solver_env);

                if (forkAt) {
                    task.fork();
                }
                tasklist[action_id] = task;
            }

            for (int action_id = 0; action_id < actions.size(); action_id++) {
                CfrTask task = tasklist[action_id];
                if (task == null) continue;

                float[] action_utilities;
                if (forkAt) {
                    try {
                        action_utilities = task.join();
                    } catch (Exception e) {
                        throw new RuntimeException("future get error");
                    }
                } else {
                    action_utilities = task.compute();
                }
                all_action_utility[action_id] = action_utilities;

                if (action_utilities.length != payoffs.length) {
                    System.out.println("errmsg");
                    System.out.println(String.format("node player %s ", node.getPlayer()));
                    node.printHistory();
                    throw new RuntimeException(String.format(
                            "action and payoff length not match %s - %s", action_utilities.length, payoffs.length));
                }

                for (int hand_id = 0; hand_id < action_utilities.length; hand_id++) {
                    if (player == node.getPlayer()) {
                        float strategy_prob = current_strategy[hand_id + action_id * node_player_private_cards.length];
                        payoffs[hand_id] += strategy_prob * action_utilities[hand_id];
                    } else {
                        payoffs[hand_id] += action_utilities[hand_id];
                    }
                }
            }

            if (player == node.getPlayer()) {
                for (int i = 0; i < node_player_private_cards.length; i++) {
                    for (int action_id = 0; action_id < actions.size(); action_id++) {
                        regrets[action_id * node_player_private_cards.length + i] =
                                all_action_utility[action_id][i] - payoffs[i];
                    }
                }
                trainable.updateRegrets(regrets, iter + 1, reach_probs[player]);
                if (trainable instanceof DiscountedCfrTrainable dct) {
                    dct.setEvs(payoffs);
                    dct.setReach_probs(reach_probs);
                }
            }

            return payoffs;
        }

        float[] showdownUtility(int player, ShowdownNode node, float[][] reach_probs, int iter, long current_board) {
            int oppo = 1 - player;
            float win_payoff = node.get_payoffs(ShowdownNode.ShowDownResult.NOTTIE, player)[player].floatValue();
            float lose_payoff = node.get_payoffs(ShowdownNode.ShowDownResult.NOTTIE, oppo)[player].floatValue();
            PrivateCards[] player_private_cards = this.solver_env.ranges[player];
            PrivateCards[] oppo_private_cards = this.solver_env.ranges[oppo];

            RiverCombs[] player_combs = this.solver_env.rrm.getRiverCombos(player, player_private_cards, current_board);
            RiverCombs[] oppo_combs = this.solver_env.rrm.getRiverCombos(oppo, oppo_private_cards, current_board);

            float[] payoffs = new float[player_private_cards.length];

            float winsum = 0;
            float[] card_winsum = new float[52];

            int j = 0;

            if (this.solver_env.debug) {
                System.out.println("[PRESHOWDOWN]=======================");
                System.out.println(String.format("player0 reach_prob %s", Arrays.toString(reach_probs[0])));
                System.out.println(String.format("player1 reach_prob %s", Arrays.toString(reach_probs[1])));
                System.out.print("preflop combos: ");
                for (RiverCombs one_river_comb : player_combs) {
                    System.out.print(
                            String.format("%s(%s) ", one_river_comb.private_cards.toString(), one_river_comb.rank));
                }
                System.out.println();
            }

            for (int i = 0; i < player_combs.length; i++) {
                RiverCombs one_player_comb = player_combs[i];
                while (j < oppo_combs.length && one_player_comb.rank < oppo_combs[j].rank) {
                    RiverCombs one_oppo_comb = oppo_combs[j];
                    winsum += reach_probs[oppo][one_oppo_comb.reach_prob_index];
                    if (this.solver_env.debug) {
                        if (one_player_comb.reach_prob_index == 0) {
                            System.out.print(String.format(
                                    "[%s]%s:%s-%s(%s) ",
                                    j,
                                    one_oppo_comb.private_cards.toString(),
                                    this.solver_env.ranges[oppo][one_oppo_comb.reach_prob_index].weight,
                                    winsum,
                                    one_oppo_comb.rank));
                        }
                    }

                    card_winsum[one_oppo_comb.private_cards.card1] += reach_probs[oppo][one_oppo_comb.reach_prob_index];
                    card_winsum[one_oppo_comb.private_cards.card2] += reach_probs[oppo][one_oppo_comb.reach_prob_index];
                    j++;
                }
                if (this.solver_env.debug) {
                    System.out.println(String.format(
                            "Before Adding %s, win_payoff %s winsum %s, subcard1 %s subcard2 %s",
                            payoffs[one_player_comb.reach_prob_index],
                            win_payoff,
                            winsum,
                            -card_winsum[one_player_comb.private_cards.card1],
                            -card_winsum[one_player_comb.private_cards.card2]));
                }
                payoffs[one_player_comb.reach_prob_index] = (winsum
                                - card_winsum[one_player_comb.private_cards.card1]
                                - card_winsum[one_player_comb.private_cards.card2])
                        * win_payoff;
                if (this.solver_env.debug) {
                    if (one_player_comb.reach_prob_index == 0) {
                        System.out.println(String.format("winsum %s", winsum));
                    }
                }
            }

            float losssum = 0;
            float[] card_losssum = new float[52];

            j = oppo_combs.length - 1;
            for (int i = player_combs.length - 1; i >= 0; i--) {
                RiverCombs one_player_comb = player_combs[i];
                while (j >= 0 && one_player_comb.rank > oppo_combs[j].rank) {
                    RiverCombs one_oppo_comb = oppo_combs[j];
                    losssum += reach_probs[oppo][one_oppo_comb.reach_prob_index];
                    if (this.solver_env.debug) {
                        if (one_player_comb.reach_prob_index == 0) {
                            System.out.print(String.format(
                                    "lose %s:%s ",
                                    one_oppo_comb.private_cards.toString(),
                                    this.solver_env.ranges[oppo][one_oppo_comb.reach_prob_index].weight));
                        }
                    }

                    card_losssum[one_oppo_comb.private_cards.card1] +=
                            reach_probs[oppo][one_oppo_comb.reach_prob_index];
                    card_losssum[one_oppo_comb.private_cards.card2] +=
                            reach_probs[oppo][one_oppo_comb.reach_prob_index];
                    j--;
                }
                if (this.solver_env.debug) {
                    System.out.println(String.format("Before Substract %s", payoffs[one_player_comb.reach_prob_index]));
                }
                payoffs[one_player_comb.reach_prob_index] += (losssum
                                - card_losssum[one_player_comb.private_cards.card1]
                                - card_losssum[one_player_comb.private_cards.card2])
                        * lose_payoff;
                if (this.solver_env.debug) {
                    if (one_player_comb.reach_prob_index == 0) {
                        System.out.println(String.format("losssum %s", losssum));
                    }
                }
            }
            if (this.solver_env.debug) {
                System.out.println();
                System.out.println("[SHOWDOWN]============");
                node.printHistory();
                System.out.println(String.format("loss payoffs: %s", lose_payoff));
                System.out.println(String.format("oppo sum %s, substracted payoff %s", losssum, payoffs[0]));
            }
            return payoffs;
        }

        float[] terminalUtility(int player, TerminalNode node, float[][] reach_prob, int iter, long current_board) {

            Double player_payoff = node.get_payoffs()[player];
            if (player_payoff == null)
                throw new RuntimeException(String.format("player %d 's payoff is not found", player));

            int oppo = 1 - player;
            PrivateCards[] player_hand = playerHands(player);
            PrivateCards[] oppo_hand = playerHands(oppo);

            float[] payoffs = new float[this.solver_env.playerHands(player).length];

            float oppo_sum = 0;
            float[] oppo_card_sum = new float[52];
            Arrays.fill(oppo_card_sum, 0);

            for (int i = 0; i < oppo_hand.length; i++) {
                oppo_card_sum[oppo_hand[i].card1] += reach_prob[oppo][i];
                oppo_card_sum[oppo_hand[i].card2] += reach_prob[oppo][i];
                oppo_sum += reach_prob[oppo][i];
            }

            if (this.solver_env.debug) {
                System.out.println("[PRETERMINAL]============");
            }
            for (int i = 0; i < player_hand.length; i++) {
                PrivateCards one_player_hand = player_hand[i];
                if (Card.boardsHasIntercept(
                        current_board, Card.boardInts2long(new int[] {one_player_hand.card1, one_player_hand.card2}))) {
                    continue;
                }
                Integer oppo_same_card_ind = this.solver_env.pcm.indPlayer2Player(player, oppo, i);
                float plus_reach_prob;
                if (oppo_same_card_ind == null) {
                    plus_reach_prob = 0;
                } else {
                    plus_reach_prob = reach_prob[oppo][oppo_same_card_ind];
                }
                payoffs[i] = player_payoff.floatValue()
                        * (oppo_sum
                                - oppo_card_sum[one_player_hand.card1]
                                - oppo_card_sum[one_player_hand.card2]
                                + plus_reach_prob);
                if (this.solver_env.debug) {
                    System.out.println(String.format("oppo_card_sum1 %s ", oppo_card_sum[one_player_hand.card1]));
                    System.out.println(String.format("oppo_card_sum2 %s ", oppo_card_sum[one_player_hand.card2]));
                    System.out.println(String.format("reach_prob i %s ", plus_reach_prob));
                }
            }

            if (this.solver_env.debug) {
                System.out.println("[TERMINAL]============");
                node.printHistory();
                System.out.println(String.format("PPPayoffs: %s", player_payoff));
                System.out.println(String.format("reach prob %s", reach_prob[oppo][0]));
                System.out.println(
                        String.format("oppo sum %s, substracted sum %s", oppo_sum, payoffs[0] / player_payoff));
                System.out.println(String.format("substracted sum %s", payoffs[0]));
            }
            return payoffs;
        }
    }
}
