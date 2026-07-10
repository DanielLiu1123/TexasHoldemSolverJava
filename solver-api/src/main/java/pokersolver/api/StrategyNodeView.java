package pokersolver.api;

import static pokersolver.utils.JsonUtil.MAPPER;

import io.javalin.http.NotFoundResponse;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import pokersolver.Card;
import pokersolver.GameTree;
import pokersolver.nodes.Action;
import pokersolver.nodes.ActionNode;
import pokersolver.nodes.ChanceNode;
import pokersolver.nodes.GameTreeNode;
import pokersolver.nodes.ShowdownNode;
import pokersolver.nodes.TerminalNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Serializes a <em>single</em> strategy-tree node plus its child edge labels, addressed by a path of
 * edge labels from the root. The full post-flop tree's strategy is enormous — a flop solve dumps
 * close to a gigabyte of JSON — so the client walks it lazily, one node per request, instead of
 * downloading the whole thing.
 */
final class StrategyNodeView {

    private StrategyNodeView() {}

    /**
     * @param path comma-separated edge labels from the root — action labels like {@code "BET 10.0"}
     *     or dealt-card labels like {@code "Ah"}. Null or blank addresses the root.
     */
    static ObjectNode atPath(GameTree tree, @Nullable String path) {
        GameTreeNode node = tree.getRoot();
        if (path != null && !path.isBlank()) {
            for (String segment : path.split(",", -1)) {
                GameTreeNode next = step(node, segment);
                if (next == null) throw new NotFoundResponse("no strategy node at path segment: " + segment);
                node = next;
            }
        }
        return dump(node);
    }

    private static @Nullable GameTreeNode step(GameTreeNode node, String label) {
        return switch (node) {
            case ActionNode action -> {
                List<Action> actions = action.getActions();
                for (int i = 0; i < actions.size(); i++) {
                    if (actions.get(i).label().equals(label))
                        yield action.getChildren().get(i);
                }
                yield null;
            }
            case ChanceNode chance -> {
                List<Card> cards = chance.getCards();
                for (int i = 0; i < cards.size(); i++) {
                    if (cards.get(i).toString().equals(label))
                        yield chance.getChildren().get(i);
                }
                yield null;
            }
            case ShowdownNode ignored -> null;
            case TerminalNode ignored -> null;
        };
    }

    private static ObjectNode dump(GameTreeNode node) {
        ObjectNode json = MAPPER.createObjectNode();
        switch (node) {
            case ActionNode action -> {
                json.put("nodeType", "action_node");
                json.put("player", action.getPlayer());
                List<Action> actions = action.getActions();
                ArrayNode actionLabels = json.putArray("actions");
                ArrayNode childActions = MAPPER.createArrayNode();
                for (int i = 0; i < actions.size(); i++) {
                    String label = actions.get(i).label();
                    actionLabels.add(label);
                    // Only action and chance children are navigable; showdown and folds end the line.
                    GameTreeNode child = action.getChildren().get(i);
                    if (child instanceof ActionNode || child instanceof ChanceNode) childActions.add(label);
                }
                json.set("childActions", childActions);
                json.set(
                        "strategy",
                        Objects.requireNonNull(action.getTrainable(), "trainable not set")
                                .dumps());
            }
            case ChanceNode chance -> {
                json.put("nodeType", "chance_node");
                ArrayNode cards = json.putArray("cards");
                for (Card card : chance.getCards()) cards.add(card.toString());
            }
            case ShowdownNode ignored -> json.put("nodeType", "terminal");
            case TerminalNode ignored -> json.put("nodeType", "terminal");
        }
        return json;
    }
}
