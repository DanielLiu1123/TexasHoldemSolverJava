package pokersolver.api;

import static pokersolver.utils.JsonUtil.MAPPER;

import io.javalin.http.NotFoundResponse;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import pokersolver.Card;
import pokersolver.GameTree;
import pokersolver.nodes.ActionNode;
import pokersolver.nodes.ChanceNode;
import pokersolver.nodes.GameActions;
import pokersolver.nodes.GameTreeNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Serializes a <em>single</em> strategy-tree node plus its child edge labels, addressed by a path
 * of edge labels from the root. The full post-flop tree's strategy is enormous (a flop solve dumps
 * close to a gigabyte of JSON), so the client walks it lazily — one node per request — instead of
 * downloading the whole thing.
 */
final class StrategyNodeView {

    private StrategyNodeView() {}

    /**
     * @param path comma-separated edge labels from the root (action labels like {@code "BET 10.0"}
     *     or dealt-card labels like {@code "Ah"}); null or blank addresses the root.
     */
    static ObjectNode atPath(GameTree tree, @Nullable String path) {
        GameTreeNode node = tree.getRoot();
        if (path != null && !path.isBlank()) {
            for (String segment : path.split(",")) {
                GameTreeNode next = step(node, segment);
                if (next == null) throw new NotFoundResponse("no strategy node at path segment: " + segment);
                node = next;
            }
        }
        return dump(node);
    }

    private static @Nullable GameTreeNode step(GameTreeNode node, String label) {
        if (node instanceof ActionNode action) {
            List<GameActions> actions = action.getActions();
            for (int i = 0; i < actions.size(); i++) {
                if (actions.get(i).toString().equals(label))
                    return action.getChildren().get(i);
            }
        } else if (node instanceof ChanceNode chance) {
            List<Card> cards = chance.getCards();
            for (int i = 0; i < cards.size(); i++) {
                if (cards.get(i).toString().equals(label))
                    return chance.getChildren().get(i);
            }
        }
        return null;
    }

    private static ObjectNode dump(GameTreeNode node) {
        ObjectNode json = MAPPER.createObjectNode();
        if (node instanceof ActionNode action) {
            json.put("node_type", "action_node");
            json.put("player", action.getPlayer());
            List<GameActions> actions = action.getActions();
            ArrayNode actionLabels = json.putArray("actions");
            ArrayNode childActions = MAPPER.createArrayNode();
            for (int i = 0; i < actions.size(); i++) {
                String label = actions.get(i).toString();
                actionLabels.add(label);
                GameTreeNode child = action.getChildren().get(i);
                // Only action/chance children are navigable; showdown/terminal end the line.
                if (child instanceof ActionNode || child instanceof ChanceNode) childActions.add(label);
            }
            json.set("childActions", childActions);
            json.set(
                    "strategy",
                    Objects.requireNonNull(action.getTrainable(), "trainable not set")
                            .dumps(false));
        } else if (node instanceof ChanceNode chance) {
            json.put("node_type", "chance_node");
            ArrayNode cards = json.putArray("cards");
            for (Card card : chance.getCards()) cards.add(card.toString());
        } else {
            json.put("node_type", "terminal");
        }
        return json;
    }
}
