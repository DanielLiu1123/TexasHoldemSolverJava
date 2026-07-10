package pokersolver;

import static pokersolver.utils.JsonUtil.MAPPER;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pokersolver.exceptions.ActionNotFoundException;
import pokersolver.exceptions.NodeLengthMismatchException;
import pokersolver.exceptions.NodeNotFoundException;
import pokersolver.nodes.Action;
import pokersolver.nodes.ActionNode;
import pokersolver.nodes.ChanceNode;
import pokersolver.nodes.GameRound;
import pokersolver.nodes.GameTreeNode;
import pokersolver.nodes.ShowdownNode;
import pokersolver.nodes.TerminalNode;
import tools.jackson.databind.JsonNode;

/**
 * Loads a game tree from the JSON format the upstream Python tree builder emits.
 *
 * <p>Edge labels there are lowercase and underscore-separated ({@code "check"}, {@code "bet_4"}),
 * unlike the labels this solver writes back out. A chance node in the file has one child, the
 * template every dealable card expands into.
 */
final class GameTreeJsonLoader {

    private GameTreeJsonLoader() {}

    static GameTreeNode load(String path, Deck deck) throws IOException {
        JsonNode root = MAPPER.readTree(Files.readString(Path.of(path), StandardCharsets.UTF_8));
        return buildNode(require(root, "root"), deck, null);
    }

    private static JsonNode require(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) throw new NodeNotFoundException("missing '%s'".formatted(field));
        return value;
    }

    private static GameTreeNode buildNode(JsonNode json, Deck deck, @Nullable GameTreeNode parent) {
        JsonNode meta = require(json, "meta");
        GameRound round = GameRound.fromString(require(meta, "round").asString());
        double pot = require(meta, "pot").asDouble();
        String nodeType = require(meta, "node_type").asString();

        return switch (nodeType) {
            case "Action" -> buildActionNode(json, meta, round, pot, parent, deck);
            case "Showdown" -> buildShowdownNode(meta, round, pot, parent);
            case "Terminal" -> buildTerminalNode(meta, round, pot, parent);
            case "Chance" -> buildChanceNode(json, round, pot, parent, deck);
            default -> throw new NodeNotFoundException("node type %s not found".formatted(nodeType));
        };
    }

    private static ActionNode buildActionNode(
            JsonNode json, JsonNode meta, GameRound round, double pot, @Nullable GameTreeNode parent, Deck deck) {
        JsonNode labels = require(json, "children_actions");
        JsonNode childJson = require(json, "children");
        if (labels.size() != childJson.size()) {
            throw new NodeLengthMismatchException(
                    "%d actions but %d children".formatted(labels.size(), childJson.size()));
        }

        List<Action> actions = new ArrayList<>(labels.size());
        List<GameTreeNode> children = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            actions.add(parseAction(labels.get(i).asString()));
            children.add(buildNode(childJson.get(i), deck, null));
        }

        ActionNode node =
                new ActionNode(actions, children, require(meta, "player").asInt(), round, pot, parent);
        for (GameTreeNode child : children) child.setParent(node);
        return node;
    }

    /** Parses {@code "check"}, {@code "fold"}, {@code "call"}, {@code "bet_4"}, {@code "raise_8"}. */
    private static Action parseAction(String label) {
        return switch (label) {
            case "check" -> Action.CHECK;
            case "fold" -> Action.FOLD;
            case "call" -> Action.CALL;
            default -> {
                String[] parts = label.split("_", -1);
                if (parts.length != 2) throw new ActionNotFoundException("action %s not found".formatted(label));
                double amount = Double.parseDouble(parts[1]);
                yield switch (parts[0]) {
                    case "bet" -> new Action.Bet(amount);
                    case "raise" -> new Action.Raise(amount);
                    default -> throw new ActionNotFoundException("action %s not found".formatted(label));
                };
            }
        };
    }

    private static ShowdownNode buildShowdownNode(
            JsonNode meta, GameRound round, double pot, @Nullable GameTreeNode parent) {
        JsonNode payoffs = require(meta, "payoffs");
        double[] tiePayoffs = doubles(require(payoffs, "tie"));

        int playerCount = payoffs.size() - 1; // every key but "tie" is a player id
        double[][] winnerPayoffs = new double[playerCount][];
        for (String key : payoffs.propertyNames()) {
            if (key.equals("tie")) continue;
            winnerPayoffs[Integer.parseInt(key)] = doubles(payoffs.get(key));
        }
        return new ShowdownNode(tiePayoffs, winnerPayoffs, round, pot, parent);
    }

    private static TerminalNode buildTerminalNode(
            JsonNode meta, GameRound round, double pot, @Nullable GameTreeNode parent) {
        return new TerminalNode(
                doubles(require(meta, "payoff")), require(meta, "player").asInt(), round, pot, parent);
    }

    /** A chance node's single child in the file is the template each dealable card expands into. */
    private static ChanceNode buildChanceNode(
            JsonNode json, GameRound round, double pot, @Nullable GameTreeNode parent, Deck deck) {
        JsonNode childJson = require(json, "children");
        if (childJson.size() != 1) {
            throw new NodeLengthMismatchException("a chance node has one child template, found " + childJson.size());
        }

        List<GameTreeNode> children = new ArrayList<>(deck.getCards().size());
        for (int i = 0; i < deck.getCards().size(); i++) {
            children.add(buildNode(childJson.get(0), deck, null));
        }
        ChanceNode node = new ChanceNode(children, round, pot, parent, deck.getCards());
        for (GameTreeNode child : children) child.setParent(node);
        return node;
    }

    private static double[] doubles(JsonNode array) {
        double[] values = new double[array.size()];
        for (int i = 0; i < values.length; i++) values[i] = array.get(i).asDouble();
        return values;
    }
}
