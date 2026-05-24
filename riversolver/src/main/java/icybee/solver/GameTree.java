package icybee.solver;

import static icybee.solver.utils.JsonUtil.MAPPER;

import icybee.solver.exceptions.ActionNotFoundException;
import icybee.solver.exceptions.NodeLengthMismatchException;
import icybee.solver.exceptions.NodeNotFoundException;
import icybee.solver.exceptions.RoundNotFoundException;
import icybee.solver.nodes.ActionNode;
import icybee.solver.nodes.ChanceNode;
import icybee.solver.nodes.GameActions;
import icybee.solver.nodes.GameTreeNode;
import icybee.solver.nodes.ShowdownNode;
import icybee.solver.nodes.TerminalNode;
import icybee.solver.solver.GameTreeBuildingSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * Created by huangxuefeng on 2019/10/7.
 * This file contains code for GameTree construction
 */
public class GameTree {
    @Nullable
    String tree_json_dir;

    GameTreeNode root;
    Deck deck;

    public Deck getDeck() {
        return deck;
    }

    public GameTreeNode getRoot() {
        return root;
    }

    private static String readAllBytes(String filePath) throws IOException {
        String content;
        content = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
        return content;
    }

    GameTreeNode.GameRound intToGameRound(int round) {
        return switch (round) {
            case 1 -> GameTreeNode.GameRound.PREFLOP;
            case 2 -> GameTreeNode.GameRound.FLOP;
            case 3 -> GameTreeNode.GameRound.TURN;
            case 4 -> GameTreeNode.GameRound.RIVER;
            default -> throw new RoundNotFoundException(String.format("round %s not found", round));
        };
    }

    GameTreeNode.GameRound strToGameRound(String round) {
        return switch (round) {
            case "preflop" -> GameTreeNode.GameRound.PREFLOP;
            case "flop" -> GameTreeNode.GameRound.FLOP;
            case "turn" -> GameTreeNode.GameRound.TURN;
            case "river" -> GameTreeNode.GameRound.RIVER;
            default -> throw new RoundNotFoundException(String.format("round %s not found", round));
        };
    }

    Double getValue(Map<String, Object> meta, String key) {
        Object value = Objects.requireNonNull(meta.get(key), "key '" + key + "' not found in meta");
        return switch (value) {
            case Integer i -> i.doubleValue();
            case Double v -> v;
            default -> throw new RuntimeException(String.format("data type %s not underestood", value));
        };
    }

    ActionNode generateActionNode(
            Map meta,
            List<String> childrens_actions,
            List<Map> childrens_nodes,
            String round,
            @Nullable GameTreeNode parent) {
        if (childrens_actions.size() != childrens_nodes.size()) {
            throw new NodeLengthMismatchException(String.format(
                    "mismatch when generate ActionNode, childrens_action length %d children_nodes length %d",
                    childrens_actions.size(), childrens_nodes.size()));
        }

        if (!(round.equals("preflop") || round.equals("flop") || round.equals("turn") || round.equals("river"))) {
            throw new RoundNotFoundException(String.format("round %s not found", round));
        }

        List<GameActions> actions = new ArrayList<>();
        List<GameTreeNode> childrens = new ArrayList<>();

        // 遍历所有children actions 来生成GameAction 的list，用于初始化ActionNode
        for (int i = 0; i < childrens_actions.size(); i++) {
            String one_action = childrens_actions.get(i);
            Map one_children_map = childrens_nodes.get(i);
            if (one_action == null) throw new ActionNotFoundException("action is null");
            if (one_children_map == null) throw new RuntimeException("one_children_map is null");

            GameTreeNode.PokerActions action;
            Double amount = null;
            switch (one_action) {
                case "check" -> {
                    action = GameTreeNode.PokerActions.CHECK;
                }
                case "fold" -> {
                    action = GameTreeNode.PokerActions.FOLD;
                }
                case "call" -> {
                    action = GameTreeNode.PokerActions.CALL;
                }
                default -> {
                    if (one_action.contains("bet")) {
                        List<String> action_sp = List.of(one_action.split("_", -1));
                        if (action_sp.size() != 2)
                            throw new RuntimeException(String.format("action sp length %d", action_sp.size()));
                        String action_str = action_sp.get(0);
                        action = GameTreeNode.PokerActions.BET;
                        if (!action_str.equals("bet"))
                            throw new ActionNotFoundException(String.format("Action %s not found", action_str));
                        amount = Double.valueOf(action_sp.get(1));

                    } else if (one_action.contains("raise")) {
                        List<String> action_sp = List.of(one_action.split("_", -1));
                        if (action_sp.size() != 2)
                            throw new RuntimeException(String.format("action sp length %d", action_sp.size()));
                        String action_str = action_sp.get(0);
                        action = GameTreeNode.PokerActions.RAISE;
                        if (!action_str.equals("raise"))
                            throw new ActionNotFoundException(String.format("Action %s not found", action_str));
                        amount = Double.valueOf(action_sp.get(1));
                    } else {
                        throw new ActionNotFoundException(String.format("%s action not found", one_action));
                    }
                }
            } // end of switch
            GameTreeNode one_children_node = recurrentGenerateTreeNode(one_children_map, parent);
            childrens.add(one_children_node);

            GameActions game_action = new GameActions(action, amount);
            actions.add(game_action);
        }
        Integer player = (Integer) meta.get("player");
        Double pot = this.getValue(meta, "pot");
        if (player == null) throw new RuntimeException("player is null");
        if (childrens.size() != actions.size()) {
            throw new NodeLengthMismatchException(
                    String.format("childrens length %d, actions length %d", childrens.size(), actions.size()));
        }
        GameTreeNode.GameRound game_round = strToGameRound(round);
        ActionNode actionNode = new ActionNode(actions, childrens, player, game_round, pot, parent);
        for (GameTreeNode one_node : childrens) {
            one_node.setParent(actionNode);
        }
        return actionNode;
    }

    void convertObject2Double(List<Object> from, Double[] to) {
        for (int winner_player = 0; winner_player < from.size(); winner_player++) {
            Object tmp_payoff = from.get(winner_player);
            if (tmp_payoff instanceof Integer i) {
                to[winner_player] = i.doubleValue();
            } else if (tmp_payoff instanceof Double d) {
                to[winner_player] = d;
            } else {
                throw new RuntimeException(String.format("payoff data type %s not underestood", tmp_payoff.toString()));
            }
        }
    }

    @SuppressWarnings("NullAway")
    ShowdownNode generateShowdownNode(Map<String, Object> meta, String round, @Nullable GameTreeNode parent) {
        Map<String, Object> meta_payoffs = Objects.requireNonNull((Map) meta.get("payoffs"), "payoffs");
        List<Object> tmp_tie_payoffs = Objects.requireNonNull((List<Object>) meta_payoffs.get("tie"), "tie payoffs");
        Double[] tie_payoffs = new Double[tmp_tie_payoffs.size()];
        convertObject2Double(tmp_tie_payoffs, tie_payoffs);

        // meta_payoffs 的key有 n个玩家+1个平局,代表某个玩家赢了的时候如何分配payoff
        Double[][] player_payoffs = new Double[meta_payoffs.keySet().size() - 1]
                [meta_payoffs.keySet().size() - 1];
        Double pot = this.getValue(meta, "pot");

        for (String one_player : meta_payoffs.keySet()) {
            if (one_player.equals("tie")) {
                continue;
            }
            // 获胜玩家id
            Integer player_id = Integer.valueOf(one_player);
            if (player_id == null) throw new RuntimeException("player id json convert fail");

            // 玩家在当前Showdown节点能获得的收益
            List<Object> tmp_payoffs = (List<Object>) meta_payoffs.get(one_player);
            Double[] player_payoff = new Double[tmp_payoffs.size()];

            convertObject2Double(tmp_payoffs, player_payoff);

            player_payoffs[Integer.valueOf(one_player)] = player_payoff;
        }
        GameTreeNode.GameRound game_round = strToGameRound(round);
        return new ShowdownNode(tie_payoffs, player_payoffs, game_round, pot, parent);
    }

    @SuppressWarnings("NullAway")
    TerminalNode generateTerminalNode(Map meta, String round, @Nullable GameTreeNode parent) {
        List<Object> player_payoff_list = Objects.requireNonNull((List<Object>) meta.get("payoff"), "payoff");
        Double[] player_payoff = new Double[player_payoff_list.size()];
        for (int one_player = 0; one_player < player_payoff_list.size(); one_player++) {

            Object tmp_payoff = player_payoff_list.get(one_player);
            if (tmp_payoff instanceof Integer i) {
                player_payoff[one_player] = i.doubleValue();
            } else if (tmp_payoff instanceof Double d) {
                player_payoff[one_player] = d;
            } else {
                throw new RuntimeException(String.format("payoff data type %s not underestood", tmp_payoff.toString()));
            }
        }

        // 节点上的下注额度
        Double pot = this.getValue(meta, "pot");

        GameTreeNode.GameRound game_round = strToGameRound(round);
        // 多人游戏的时候winner就不等于当前节点的玩家了，这里要注意
        Integer player = (Integer) meta.get("player");
        if (player == null) throw new RuntimeException("player is null");
        return new TerminalNode(player_payoff, player, game_round, pot, parent);
    }

    ChanceNode generateChanceNode(Map meta, Map child, String round, @Nullable GameTreeNode parent) {
        // 节点上的下注额度
        Double pot = this.getValue(meta, "pot");
        List<GameTreeNode> childrens = new ArrayList<>();
        for (Card _ : this.deck.getCards()) {
            GameTreeNode one_node = recurrentGenerateTreeNode(child, null);
            childrens.add(one_node);
        }
        GameTreeNode.GameRound game_round = strToGameRound(round);
        ChanceNode chanceNode = new ChanceNode(childrens, game_round, pot, parent, this.deck.getCards());
        for (GameTreeNode gameTreeNode : chanceNode.getChildrens()) {
            gameTreeNode.setParent(chanceNode);
        }
        return chanceNode;
    }

    int recurrentSetDepth(GameTreeNode node, int depth) {
        node.depth = depth;
        switch (node) {
            case ActionNode actionNode -> {
                int subtree_size = 1;
                for (GameTreeNode one_child : actionNode.getChildrens()) {
                    subtree_size += this.recurrentSetDepth(one_child, depth + 1);
                }
                node.subtree_size = subtree_size;
            }
            case ChanceNode chanceNode -> {
                int subtree_size = 1;
                for (GameTreeNode one_child : chanceNode.getChildrens()) {
                    subtree_size += this.recurrentSetDepth(one_child, depth + 1);
                }
                node.subtree_size = subtree_size;
            }
            default -> node.subtree_size = 1;
        }
        return node.subtree_size;
    }

    GameTreeNode recurrentGenerateTreeNode(Map<String, Map> node_json, @Nullable GameTreeNode parent)
            throws NullPointerException {
        Map<String, Object> meta = node_json.get("meta");
        if (meta == null) {
            throw new NullPointerException("node json get meta null pointer");
        }
        String round = (String) meta.get("round");
        if (round == null) {
            throw new NullPointerException("node json get round null pointer");
        }

        if (!(round.equals("preflop") || round.equals("flop") || round.equals("turn") || round.equals("river"))) {
            throw new RoundNotFoundException(String.format("round %s not found", round));
        }

        String node_type = (String) meta.get("node_type");
        if (node_type == null) {
            throw new NullPointerException("node json get round null pointer");
        }
        if (!(node_type.equals("Terminal")
                || node_type.equals("Showdown")
                || node_type.equals("Action")
                || node_type.equals("Chance"))) {
            throw new NodeNotFoundException(String.format("node type %s not found", node_type));
        }

        return switch (node_type) {
            case "Action" -> {
                // 孩子节点的动作，存在list里
                List<String> childrens_actions = (List<String>) node_json.get("children_actions");
                if (childrens_actions == null) {
                    throw new NullPointerException("action node children null pointer");
                }

                // 孩子节点本身，同样存在list里,和上面的children_actions 一一对应,事实上两者的长度一致
                List<Map> childrens = (List<Map>) node_json.get("children");
                if (childrens == null) {
                    throw new NullPointerException("action node children_actions null pointer");
                }
                if (childrens.size() != childrens_actions.size()) {
                    throw new NodeLengthMismatchException("action node child length mismatch");
                }
                yield this.generateActionNode(meta, childrens_actions, childrens, round, parent);
            }
            case "Showdown" -> this.generateShowdownNode(meta, round, parent);
            case "Terminal" -> this.generateTerminalNode(meta, round, parent);
            case "Chance" -> {
                List<Map> childrens = Objects.requireNonNull((List<Map>) node_json.get("children"), "children");
                if (childrens.size() != 1) throw new RuntimeException("Chance node should have only one child");
                yield this.generateChanceNode(meta, childrens.getFirst(), round, parent);
            }
            default -> throw new NodeNotFoundException(String.format("node type %s not found", node_type));
        };
    }

    public GameTree(String tree_json_dir, Deck deck) throws IOException {
        this.tree_json_dir = tree_json_dir;
        String file_content = readAllBytes(tree_json_dir);
        Map<String, Map> json_map = (Map<String, Map>) MAPPER.readValue(file_content, Map.class);
        Map<String, Map> json_root = Objects.requireNonNull((Map<String, Map>) json_map.get("root"), "root");
        this.deck = deck;
        this.root = recurrentGenerateTreeNode(json_root, null);
        this.recurrentSetDepth(this.root, 0);
    }

    private static class Rule {
        Deck deck;
        float oop_commit;
        float ip_commit;
        int current_round;
        int raise_limit;
        float small_blind;
        float big_blind;
        float stack;
        GameTreeBuildingSettings build_settings;

        Rule(
                Deck deck,
                float oop_commit,
                float ip_commit,
                int current_round,
                int raise_limit,
                float small_blind,
                float big_blind,
                float stack,
                GameTreeBuildingSettings build_settings) {
            this.deck = deck;
            this.oop_commit = oop_commit;
            this.ip_commit = ip_commit;
            this.current_round = current_round;
            this.raise_limit = raise_limit;
            this.small_blind = small_blind;
            this.big_blind = big_blind;
            this.stack = stack;
            this.build_settings = build_settings;
        }

        Rule(Rule rule) {
            this.deck = rule.deck;
            this.oop_commit = rule.oop_commit;
            this.ip_commit = rule.ip_commit;
            this.current_round = rule.current_round;
            this.raise_limit = rule.raise_limit;
            this.small_blind = rule.small_blind;
            this.big_blind = rule.big_blind;
            this.stack = rule.stack;
            this.build_settings = rule.build_settings;
        }

        float get_pot() {
            return this.oop_commit + this.ip_commit;
        }

        float get_commit(int player) {
            if (player == 0) return this.ip_commit;
            else if (player == 1) return this.oop_commit;
            else throw new RuntimeException("unknown player");
        }
    }

    public GameTree(
            Deck deck,
            float oop_commit,
            float ip_commit,
            int current_round,
            int raise_limit,
            float small_blind,
            float big_blind,
            float stack,
            GameTreeBuildingSettings buildingSettings)
            throws IOException {
        this.deck = deck;
        Rule rule = new Rule(
                deck,
                oop_commit,
                ip_commit,
                current_round,
                raise_limit,
                small_blind,
                big_blind,
                stack,
                buildingSettings);
        this.root = this.buildTree(rule);
        this.recurrentSetDepth(this.root, 0);
    }

    GameTreeNode buildTree(Rule rule) {
        int current_player = 1;
        GameTreeNode.GameRound round = intToGameRound(rule.current_round);
        ActionNode node = new ActionNode(List.of(), List.of(), current_player, round, (double) rule.get_pot(), null);
        this.__build(node, rule);
        return node;
    }

    GameTreeNode __build(GameTreeNode node, Rule rule) {
        return this.__build(node, rule, "roundbegin", 0, 0);
    }

    GameTreeNode __build(GameTreeNode node, Rule rule, String last_action, int check_times, int raise_times) {

        switch (node) {
            case ActionNode actionNode -> this.buildAction(actionNode, rule, last_action, check_times, raise_times);
            case ShowdownNode _, TerminalNode _ -> {}
            case ChanceNode chanceNode -> this.buildChance(chanceNode, rule);
            case null, default -> throw new RuntimeException("node type unknown");
        }
        return node;
    }

    void buildChance(ChanceNode root, Rule rule) {
        // 节点上的下注额度
        Rule nextrule = new Rule(rule);
        List<GameTreeNode> childrens = new ArrayList<>();
        assert (rule.current_round <= 4);
        for (Card _ : this.deck.getCards()) {
            GameTreeNode one_node;
            if (rule.oop_commit == rule.ip_commit && rule.oop_commit == rule.stack) {
                if (rule.current_round >= 4) {
                    Double p1_commit = (double) rule.ip_commit;
                    Double p2_commit = (double) rule.oop_commit;
                    Double peace_getback = (p1_commit + p2_commit) / 2;

                    Double[][] payoffs = {
                        {p2_commit, -p2_commit},
                        {-p1_commit, p1_commit}
                    };
                    nextrule = new Rule(rule);
                    one_node = new ShowdownNode(
                            new Double[] {peace_getback - p1_commit, peace_getback - p2_commit},
                            payoffs,
                            this.intToGameRound(rule.current_round),
                            (double) rule.get_pot(),
                            root);
                } else {
                    nextrule = new Rule(rule);
                    nextrule.current_round += 1;
                    assert (nextrule.current_round <= 4);
                    one_node = new ChanceNode(
                            null,
                            this.intToGameRound(rule.current_round + 1),
                            (double) rule.get_pot(),
                            root,
                            rule.deck.getCards());
                }
            } else {
                one_node = new ActionNode(
                        List.of(),
                        List.of(),
                        1,
                        this.intToGameRound(rule.current_round),
                        (double) rule.get_pot(),
                        root);
            }
            childrens.add(one_node);
            assert (nextrule.current_round <= 4);
            this.__build(one_node, nextrule, "begin", 0, 0);
        }
        root.setChildrens(childrens);
    }

    void buildAction(ActionNode root, Rule rule, String last_action, int check_times, int raise_times) {
        // current player
        int player = root.getPlayer();

        String[] possible_actions =
                switch (last_action) {
                    case "roundbegin", "begin" -> new String[] {"check", "bet"};
                    case "bet", "raise" -> new String[] {"call", "raise", "fold"};
                    case "check" -> new String[] {"check", "raise", "bet"};
                    case "fold" -> null;
                    case "call" -> new String[] {"check", "raise"};
                    default -> throw new NodeNotFoundException(String.format("last action %s not found", last_action));
                };
        int nextplayer = 1 - player;

        List<GameActions> actions = new ArrayList<>();
        List<GameTreeNode> childrens = new ArrayList<>();

        if (possible_actions == null) return;

        for (String action : possible_actions) {
            switch (action) {
                case "check" -> {
                    // 当不是第一轮的时候 call后面是不能跟check的
                    GameTreeNode nextnode;
                    Rule nextrule;
                    if ((Objects.equals(last_action, "call")
                                    && root.getParent() != null
                                    && root.getParent().getParent() == null)
                            || check_times >= 1) {

                        // 双方均check的话,进入摊派阶段
                        // check 只有最有一轮（river）的时候才是摊派，否则都是应该进入下一轮的
                        if (rule.current_round == 4) {
                            // 在river check 导致游戏进入showdown
                            Double p1_commit = (double) rule.ip_commit;
                            Double p2_commit = (double) rule.oop_commit;
                            Double peace_getback = (p1_commit + p2_commit) / 2;

                            Double[][] payoffs = {
                                {p2_commit, -p2_commit},
                                {-p1_commit, p1_commit}
                            };
                            nextrule = new Rule(rule);
                            nextnode = new ShowdownNode(
                                    new Double[] {peace_getback - p1_commit, peace_getback - p2_commit},
                                    payoffs,
                                    this.intToGameRound(rule.current_round),
                                    (double) rule.get_pot(),
                                    root);
                        } else {
                            // 在preflop/flop/turn check 导致游戏进入下一轮
                            nextrule = new Rule(rule);
                            nextrule.current_round += 1;
                            nextnode = new ChanceNode(
                                    null,
                                    this.intToGameRound(rule.current_round + 1),
                                    (double) rule.get_pot(),
                                    root,
                                    rule.deck.getCards());
                        }
                    } else {
                        nextrule = new Rule(rule);
                        nextnode = new ActionNode(
                                List.of(),
                                List.of(),
                                nextplayer,
                                this.intToGameRound(rule.current_round),
                                (double) rule.get_pot(),
                                root);
                    }
                    this.__build(nextnode, nextrule, "check", check_times + 1, 0);
                    actions.add(new GameActions(GameTreeNode.PokerActions.CHECK, 0.0));
                    childrens.add(nextnode);
                }
                case "bet" -> {
                    BetType betType = BetType.BET;
                    // if it's a donk bet
                    if (root.getPlayer() == 1
                            && root.getParent() != null
                            && root.getParent() instanceof ChanceNode chanceNodeBeforeThis) {
                        if (chanceNodeBeforeThis.isDonk()) betType = BetType.DONK;
                    }
                    List<Double> bet_sizes = this.get_possible_bets(root, player, nextplayer, rule, betType);
                    for (Double one_betting_size : bet_sizes) {
                        Rule nextrule = new Rule(rule);
                        if (player == 0) nextrule.ip_commit = (float) (nextrule.ip_commit + one_betting_size);
                        else if (player == 1) nextrule.oop_commit = (float) (nextrule.oop_commit + one_betting_size);
                        else throw new RuntimeException("unknown player");
                        GameTreeNode nextnode = new ActionNode(
                                List.of(),
                                List.of(),
                                nextplayer,
                                this.intToGameRound(rule.current_round),
                                (double) rule.get_pot(),
                                root);
                        this.__build(nextnode, nextrule, "bet", 0, 0);
                        actions.add(new GameActions(GameTreeNode.PokerActions.BET, one_betting_size));
                        childrens.add(nextnode);
                    }
                }
                case "call" -> {
                    Rule nextrule = new Rule(rule);
                    if (player == 0) nextrule.ip_commit += (rule.oop_commit - rule.ip_commit);
                    else if (player == 1) nextrule.oop_commit += (rule.ip_commit - rule.oop_commit);
                    else throw new RuntimeException("unknown player");

                    GameTreeNode nextnode;
                    if (root.getParent() == null) {
                        nextnode = new ActionNode(
                                List.of(),
                                List.of(),
                                nextplayer,
                                this.intToGameRound(rule.current_round),
                                (double) rule.get_pot(),
                                root);
                    } else if (rule.current_round == 4) {

                        Double p1_commit = (double) nextrule.ip_commit;
                        Double p2_commit = (double) nextrule.oop_commit;
                        Double peace_getback = (p1_commit + p2_commit) / 2;

                        Double[][] payoffs = {
                            {p2_commit, -p2_commit},
                            {-p1_commit, p1_commit}
                        };
                        Double[] tie_payoffs = new Double[] {peace_getback - p1_commit, peace_getback - p2_commit};
                        nextnode = new ShowdownNode(
                                tie_payoffs,
                                payoffs,
                                this.intToGameRound(rule.current_round),
                                (double) rule.get_pot(),
                                root);
                    } else {
                        nextrule.current_round += 1;
                        boolean donk = player == 1;
                        nextnode = new ChanceNode(
                                null,
                                this.intToGameRound(rule.current_round + 1),
                                (double) rule.get_pot(),
                                root,
                                rule.deck.getCards(),
                                donk);
                    }
                    assert (nextrule.current_round <= 4);
                    this.__build(nextnode, nextrule, "call", 0, 0);
                    actions.add(new GameActions(
                            GameTreeNode.PokerActions.CALL, (double) Math.abs(rule.oop_commit - rule.ip_commit)));
                    childrens.add(nextnode);
                }
                case "raise" -> {
                    if (Objects.equals(last_action, "call")) {
                        if (!(root.getParent() != null && root.getParent().getParent() == null)) continue;
                    } else if (Objects.equals(last_action, "check")) {
                        // 第二轮之后的check后面只能跟 bet
                        if (!(root.getParent() != null
                                && root.getParent().getParent() == null
                                && rule.current_round == 1)) continue;
                    }
                    // 如果raise次数超出限制，则不可以继续raise
                    if (raise_times >= rule.raise_limit) continue;
                    List<Double> bet_sizes = this.get_possible_bets(root, player, nextplayer, rule, BetType.RAISE);
                    for (Double one_betting_size : bet_sizes) {
                        Rule nextrule = new Rule(rule);
                        if (player == 0) nextrule.ip_commit = (float) (nextrule.ip_commit + one_betting_size);
                        else if (player == 1) nextrule.oop_commit = (float) (nextrule.oop_commit + one_betting_size);
                        else throw new RuntimeException("unknown player");
                        GameTreeNode nextnode = new ActionNode(
                                List.of(),
                                List.of(),
                                nextplayer,
                                this.intToGameRound(rule.current_round),
                                (double) rule.get_pot(),
                                root);
                        this.__build(nextnode, nextrule, "raise", 0, raise_times + 1);
                        actions.add(new GameActions(GameTreeNode.PokerActions.RAISE, one_betting_size));
                        childrens.add(nextnode);
                    }
                }
                case "fold" -> {
                    Rule nextrule = new Rule(rule);
                    Double[] payoffs;
                    if (player == 0) {
                        payoffs = new Double[] {(double) -rule.ip_commit, (double) rule.ip_commit};
                    } else if (player == 1) {
                        payoffs = new Double[] {(double) rule.oop_commit, (double) -rule.oop_commit};
                    } else throw new RuntimeException("unknown player");
                    GameTreeNode nextnode = new TerminalNode(
                            payoffs,
                            nextplayer,
                            this.intToGameRound(rule.current_round),
                            (double) rule.get_pot(),
                            root);
                    this.__build(nextnode, nextrule, "fold", 0, 0);
                    actions.add(new GameActions(GameTreeNode.PokerActions.FOLD, 0.0));
                    childrens.add(nextnode);
                }
                case null, default -> {}
            }
        }
        assert !actions.isEmpty();
        root.setActions(actions);
        root.setChildrens(childrens);
    }

    Double round_nearest(Double number, Double round_num) {
        round_num = 1 / round_num;
        return Math.round((number * round_num)) / round_num;
    }

    enum BetType {
        BET,
        DONK,
        RAISE
    }

    List<Double> get_possible_bets(GameTreeNode root, int player, int next_player, Rule rule, BetType betType) {
        assert (player == 1 - next_player);
        GameTreeBuildingSettings.StreetSetting streetSetting = rule.build_settings.getSettings(root.getRound(), player);
        ArrayList<Double> bets_ratios = new ArrayList<>();
        boolean all_in = streetSetting.allin;
        float[] bets_from_rule;
        if (betType == BetType.BET) bets_from_rule = streetSetting.bet_sizes;
        else if (betType == BetType.DONK) {
            bets_from_rule = streetSetting.donk_sizes;
            all_in = false;
        } else if (betType == BetType.RAISE) bets_from_rule = streetSetting.raise_sizes;
        else throw new RuntimeException("bet type unknown");
        if (bets_from_rule == null) return new ArrayList<>();
        for (float one_bet : bets_from_rule) {
            bets_ratios.add((double) one_bet / 100);
        }
        float pot = rule.ip_commit + rule.oop_commit;
        List<Double> possible_amounts = new ArrayList<Double>();
        for (Double one_bet : bets_ratios) {
            Double amount;
            if (rule.oop_commit == rule.small_blind) {
                // 当德州扑克开始时，在第一个玩家动作时（sb位置玩家）,视作对手先下注一个bb,这个时候下注要扣除自己的sb
                amount = one_bet * rule.big_blind - rule.small_blind;
                amount = round_nearest(amount, (double) rule.small_blind);
            } else if (rule.ip_commit == rule.big_blind && rule.oop_commit == rule.big_blind) {
                // 当德州扑克开始时，在第一个玩家call 的时候第二个玩家要 raise的时候,需要特殊处理
                amount = one_bet * rule.big_blind;
                amount = round_nearest(amount, (double) rule.small_blind);
            } else {
                amount = one_bet * pot;
                amount = round_nearest(amount, (double) rule.big_blind);
            }
            if (amount < rule.stack - rule.get_commit(player)) {
                possible_amounts.add(amount);
            }
        }
        if (all_in) possible_amounts.add((double) (rule.stack - rule.get_commit(player)));

        if (rule.get_commit(player) != rule.small_blind) {
            // 一开始的possible bet amount不能简单取整
            possible_amounts = possible_amounts.stream()
                    .filter(e -> e > 0)
                    .map(n -> Double.valueOf(n.intValue()))
                    .collect(Collectors.toList());
        }
        if (rule.get_commit(player) == rule.small_blind) {
            possible_amounts =
                    possible_amounts.stream().filter(e -> e >= rule.big_blind).collect(Collectors.toList());
        } else if (rule.get_commit(player) == rule.big_blind && rule.get_commit(next_player) == rule.big_blind) {
            possible_amounts =
                    possible_amounts.stream().filter(e -> e >= rule.big_blind).collect(Collectors.toList());
        } else {
            float gap = rule.get_commit(player) - rule.get_commit(next_player);
            assert (gap > 0);
            possible_amounts =
                    possible_amounts.stream().filter(e -> e >= gap * 2).collect(Collectors.toList());
        }
        possible_amounts = possible_amounts.stream()
                .filter(e -> e > rule.get_commit(next_player) - rule.get_commit(player))
                .collect(Collectors.toList());
        possible_amounts = possible_amounts.stream()
                .filter(e -> e <= rule.stack - rule.get_commit(player))
                .collect(Collectors.toList());
        return possible_amounts;
    }

    void recurrentPrintTree(GameTreeNode node, int depth, int depth_limit) throws ClassCastException {
        if (depth_limit != -1 && depth >= depth_limit) {
            return;
        }

        switch (node) {
            case ActionNode actionNode -> {
                List<GameTreeNode> childrens = actionNode.getChildrens();
                List<GameActions> actions = actionNode.getActions();

                for (int i = 0; i < childrens.size(); i++) {
                    GameTreeNode one_child = childrens.get(i);
                    GameActions one_action = actions.get(i);

                    StringBuilder prefix = new StringBuilder();
                    prefix.repeat("\t", Math.max(0, depth));
                    System.out.printf("%sp%s: %s%n", prefix, actionNode.getPlayer(), one_action.toString());
                    recurrentPrintTree(one_child, depth + 1, depth_limit);
                }
            }
            case ChanceNode chanceNode -> {
                StringBuilder prefix = new StringBuilder();
                prefix.repeat("\t", Math.max(0, depth));
                System.out.printf("%sCHANCE%n", prefix);
                recurrentPrintTree(chanceNode.getChildrens().getFirst(), depth + 1, depth_limit);
            }
            case ShowdownNode showdown_node -> {
                StringBuilder prefix = new StringBuilder();
                prefix.repeat("\t", Math.max(0, depth));
                System.out.printf("%s SHOWDOWN pot %f %n", prefix.toString(), showdown_node.getPot());

                prefix.append("\t");
                for (int i = 0; i < showdown_node.get_payoffs(ShowdownNode.ShowDownResult.TIE, null).length; i++) {
                    System.out.printf("%sif player %d wins, payoff :", prefix.toString(), i);
                    Double[] payoffs = showdown_node.get_payoffs(ShowdownNode.ShowDownResult.NOTTIE, i);

                    for (int player_id = 0; player_id < payoffs.length; player_id++) {
                        System.out.printf(" p%d %f ", player_id, payoffs[player_id]);
                    }
                    System.out.println();
                }
                System.out.printf("%sif Tie, payoff :", prefix.toString());
                Double[] payoffs = showdown_node.get_payoffs(ShowdownNode.ShowDownResult.TIE, null);

                for (int player_id = 0; player_id < payoffs.length; player_id++) {
                    System.out.printf(" p%d %f ", player_id, payoffs[player_id]);
                }
                System.out.println();
            }
            case TerminalNode terminal_node -> {
                StringBuilder prefix = new StringBuilder();
                prefix.repeat("\t", Math.max(0, depth));
                System.out.printf("%s TERMINAL pot %f %n", prefix, terminal_node.getPot());

                prefix.append("\t");
                System.out.printf("%sTerminal payoff :", prefix);
                Double[] payoffs = terminal_node.get_payoffs();

                for (int player_id = 0; player_id < payoffs.length; player_id++) {
                    System.out.printf(" p%d %f ", player_id, payoffs[player_id]);
                }
                System.out.println();
            }
            case null, default -> {}
        }
    }

    public void printTree(int depth) {
        if (depth < -1 || depth == 0) {
            throw new RuntimeException("depth can only be -1 or positive");
        }
        recurrentPrintTree(this.root, 0, depth);
    }

    private @Nullable ObjectNode reConvertJson(GameTreeNode node) {
        switch (node) {
            case ActionNode one_node -> {
                ObjectNode retjson = MAPPER.createObjectNode();

                List<String> actions_str = new ArrayList<>();
                for (GameActions one_action : one_node.getActions()) actions_str.add(one_action.toString());

                retjson.set("actions", MAPPER.valueToTree(actions_str));
                retjson.put("player", one_node.getPlayer());

                ObjectNode childrens = null;

                for (int i = 0; i < one_node.getActions().size(); i++) {
                    GameActions one_action = one_node.getActions().get(i);
                    GameTreeNode one_child = one_node.getChildrens().get(i);

                    ObjectNode one_json = this.reConvertJson(one_child);
                    if (one_json != null) {
                        if (childrens == null) childrens = MAPPER.createObjectNode();
                        childrens.set(one_action.toString(), one_json);
                    }
                }
                if (childrens != null) {
                    retjson.set("childrens", childrens);
                }
                retjson.set(
                        "strategy",
                        Objects.requireNonNull(one_node.getTrainable(), "trainable not set on action node")
                                .dumps(false));
                retjson.put("node_type", "action_node");
                return retjson;
            }
            case TerminalNode _, ShowdownNode _ -> {
                return null;
            }
            case ChanceNode chanceNode -> {
                List<Card> cards = chanceNode.getCards();
                List<GameTreeNode> childerns = chanceNode.getChildrens();
                if (cards.size() != childerns.size()) throw new RuntimeException("length not match");
                ObjectNode retjson = MAPPER.createObjectNode();

                ObjectNode dealcards = MAPPER.createObjectNode();
                for (int i = 0; i < cards.size(); i++) {
                    Card one_card = cards.get(i);
                    GameTreeNode gameTreeNode = childerns.get(i);
                    dealcards.set(one_card.toString(), this.reConvertJson(gameTreeNode));
                }

                retjson.set("deal_cards", dealcards);
                retjson.put("deal_number", dealcards.size());
                retjson.put("node_type", "chance_node");
                return retjson;
            }
            case null, default -> throw new RuntimeException();
        }
    }

    public ObjectNode dumps(boolean with_status) {
        if (with_status) throw new RuntimeException();
        return Objects.requireNonNull(this.reConvertJson(this.root), "root node should not produce null JSON");
    }
}
