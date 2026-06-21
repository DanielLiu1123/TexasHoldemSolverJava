package pokersolver;

import static pokersolver.utils.JsonUtil.MAPPER;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import pokersolver.exceptions.ActionNotFoundException;
import pokersolver.exceptions.NodeLengthMismatchException;
import pokersolver.exceptions.NodeNotFoundException;
import pokersolver.nodes.ActionNode;
import pokersolver.nodes.ChanceNode;
import pokersolver.nodes.GameActions;
import pokersolver.nodes.GameTreeNode;
import pokersolver.nodes.ShowdownNode;
import pokersolver.nodes.TerminalNode;

/** Loads a GameTree from a JSON file. */
class GameTreeJsonLoader {

    static GameTreeNode load(String filePath, Deck deck) throws IOException {
        String content = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Map<String, Object>>> jsonMap =
                (Map<String, Map<String, Map<String, Object>>>) MAPPER.readValue(content, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> jsonRoot =
                Objects.requireNonNull((Map<String, Map<String, Object>>) jsonMap.get("root"), "root");
        return buildNode(jsonRoot, deck, null);
    }

    private static double extractDouble(Map<String, Object> meta, String key) {
        Object value = Objects.requireNonNull(meta.get(key), "key '" + key + "' not found in meta");
        return switch (value) {
            case Integer i -> i.doubleValue();
            case Double v -> v;
            default -> throw new RuntimeException(String.format("data type %s not understood", value));
        };
    }

    private static void fillDoubles(List<Object> from, double[] to) {
        for (int i = 0; i < from.size(); i++) {
            Object tmp = from.get(i);
            if (tmp instanceof Integer it) {
                to[i] = it.doubleValue();
            } else if (tmp instanceof Double d) {
                to[i] = d;
            } else {
                throw new RuntimeException(String.format("payoff data type %s not understood", tmp.toString()));
            }
        }
    }

    @SuppressWarnings({"unchecked", "NullAway"})
    private static GameTreeNode buildNode(
            Map<String, Map<String, Object>> nodeJson, Deck deck, @Nullable GameTreeNode parent) {
        Map<String, Object> meta = nodeJson.get("meta");
        if (meta == null) throw new NullPointerException("node json get meta null pointer");
        String round = (String) meta.get("round");
        if (round == null) throw new NullPointerException("node json get round null pointer");

        String nodeType = (String) meta.get("node_type");
        if (nodeType == null) throw new NullPointerException("node json get node_type null pointer");

        return switch (nodeType) {
            case "Action" -> {
                List<String> childActions = (List<String>) nodeJson.get("children_actions");
                if (childActions == null) throw new NullPointerException("action node children_actions null pointer");
                List<Map<String, Map<String, Object>>> children =
                        (List<Map<String, Map<String, Object>>>) nodeJson.get("children");
                if (children == null) throw new NullPointerException("action node children null pointer");
                if (children.size() != childActions.size())
                    throw new NodeLengthMismatchException("action node child length mismatch");
                yield buildActionNode(meta, childActions, children, round, parent, deck);
            }
            case "Showdown" -> buildShowdownNode(meta, round, parent);
            case "Terminal" -> buildTerminalNode(meta, round, parent);
            case "Chance" -> {
                List<Map<String, Map<String, Object>>> children = Objects.requireNonNull(
                        (List<Map<String, Map<String, Object>>>) nodeJson.get("children"), "children");
                if (children.size() != 1) throw new RuntimeException("Chance node should have only one child");
                yield buildChanceNode(meta, children.getFirst(), round, parent, deck);
            }
            default -> throw new NodeNotFoundException(String.format("node type %s not found", nodeType));
        };
    }

    @SuppressWarnings("unchecked")
    private static ActionNode buildActionNode(
            Map<String, Object> meta,
            List<String> childActions,
            List<Map<String, Map<String, Object>>> childMaps,
            String round,
            @Nullable GameTreeNode parent,
            Deck deck) {
        List<GameActions> actions = new ArrayList<>();
        List<GameTreeNode> children = new ArrayList<>();

        for (int i = 0; i < childActions.size(); i++) {
            String oneAction = childActions.get(i);
            Map<String, Map<String, Object>> oneChildMap = childMaps.get(i);
            if (oneAction == null) throw new ActionNotFoundException("action is null");

            GameTreeNode.PokerActions action;
            Double amount = null;
            switch (oneAction) {
                case "check" -> action = GameTreeNode.PokerActions.CHECK;
                case "fold" -> action = GameTreeNode.PokerActions.FOLD;
                case "call" -> action = GameTreeNode.PokerActions.CALL;
                default -> {
                    if (oneAction.contains("bet")) {
                        List<String> parts = List.of(oneAction.split("_", -1));
                        if (parts.size() != 2)
                            throw new RuntimeException(String.format("action sp length %d", parts.size()));
                        if (!parts.get(0).equals("bet"))
                            throw new ActionNotFoundException(String.format("Action %s not found", parts.get(0)));
                        action = GameTreeNode.PokerActions.BET;
                        amount = Double.parseDouble(parts.get(1));
                    } else if (oneAction.contains("raise")) {
                        List<String> parts = List.of(oneAction.split("_", -1));
                        if (parts.size() != 2)
                            throw new RuntimeException(String.format("action sp length %d", parts.size()));
                        if (!parts.get(0).equals("raise"))
                            throw new ActionNotFoundException(String.format("Action %s not found", parts.get(0)));
                        action = GameTreeNode.PokerActions.RAISE;
                        amount = Double.parseDouble(parts.get(1));
                    } else {
                        throw new ActionNotFoundException(String.format("%s action not found", oneAction));
                    }
                }
            }
            GameTreeNode childNode = buildNode(oneChildMap, deck, parent);
            children.add(childNode);
            actions.add(new GameActions(action, amount));
        }

        Integer player = (Integer) meta.get("player");
        double pot = extractDouble(meta, "pot");
        if (player == null) throw new RuntimeException("player is null");

        GameTreeNode.GameRound gameRound = GameTreeNode.GameRound.fromString(round);
        ActionNode actionNode = new ActionNode(actions, children, player, gameRound, pot, parent);
        for (GameTreeNode child : children) child.setParent(actionNode);
        return actionNode;
    }

    @SuppressWarnings({"unchecked", "NullAway"})
    private static ShowdownNode buildShowdownNode(
            Map<String, Object> meta, String round, @Nullable GameTreeNode parent) {
        Map<String, Object> metaPayoffs = Objects.requireNonNull((Map<String, Object>) meta.get("payoffs"), "payoffs");
        List<Object> tmpTiePayoffs = Objects.requireNonNull((List<Object>) metaPayoffs.get("tie"), "tie payoffs");
        double[] tiePayoffs = new double[tmpTiePayoffs.size()];
        fillDoubles(tmpTiePayoffs, tiePayoffs);

        double[][] playerPayoffs =
                new double[metaPayoffs.keySet().size() - 1][metaPayoffs.keySet().size() - 1];
        double pot = extractDouble(meta, "pot");

        for (String onePlayer : metaPayoffs.keySet()) {
            if (onePlayer.equals("tie")) continue;
            int playerId = Integer.parseInt(onePlayer);
            List<Object> tmpPayoffs = (List<Object>) metaPayoffs.get(onePlayer);
            double[] playerPayoff = new double[tmpPayoffs.size()];
            fillDoubles(tmpPayoffs, playerPayoff);
            playerPayoffs[playerId] = playerPayoff;
        }
        return new ShowdownNode(tiePayoffs, playerPayoffs, GameTreeNode.GameRound.fromString(round), pot, parent);
    }

    @SuppressWarnings({"unchecked", "NullAway"})
    private static TerminalNode buildTerminalNode(
            Map<String, Object> meta, String round, @Nullable GameTreeNode parent) {
        List<Object> payoffList = Objects.requireNonNull((List<Object>) meta.get("payoff"), "payoff");
        double[] payoffs = new double[payoffList.size()];
        fillDoubles(payoffList, payoffs);

        double pot = extractDouble(meta, "pot");
        Integer player = (Integer) meta.get("player");
        if (player == null) throw new RuntimeException("player is null");
        return new TerminalNode(payoffs, player, GameTreeNode.GameRound.fromString(round), pot, parent);
    }

    private static ChanceNode buildChanceNode(
            Map<String, Object> meta,
            Map<String, Map<String, Object>> childTemplate,
            String round,
            @Nullable GameTreeNode parent,
            Deck deck) {
        double pot = extractDouble(meta, "pot");
        List<GameTreeNode> children = new ArrayList<>();
        for (Card ignored : deck.getCards()) {
            GameTreeNode oneNode = buildNode(childTemplate, deck, null);
            children.add(oneNode);
        }
        GameTreeNode.GameRound gameRound = GameTreeNode.GameRound.fromString(round);
        ChanceNode chanceNode = new ChanceNode(children, gameRound, pot, parent, deck.getCards());
        for (GameTreeNode child : chanceNode.getChildren()) child.setParent(chanceNode);
        return chanceNode;
    }

    private GameTreeJsonLoader() {}
}
