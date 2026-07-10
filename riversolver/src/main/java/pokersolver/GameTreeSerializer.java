package pokersolver;

import static pokersolver.utils.JsonUtil.MAPPER;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import pokersolver.nodes.Action;
import pokersolver.nodes.ActionNode;
import pokersolver.nodes.ChanceNode;
import pokersolver.nodes.GameTreeNode;
import pokersolver.nodes.ShowdownNode;
import pokersolver.nodes.TerminalNode;
import tools.jackson.databind.node.ObjectNode;

/** Serializes a solved game tree's strategy to JSON. Terminal lines carry no strategy and are omitted. */
final class GameTreeSerializer {

    private GameTreeSerializer() {}

    static ObjectNode dumps(GameTreeNode root) {
        return Objects.requireNonNull(toJson(root), "the root node has no strategy");
    }

    private static @Nullable ObjectNode toJson(GameTreeNode node) {
        return switch (node) {
            case ActionNode action -> actionToJson(action);
            case ChanceNode chance -> chanceToJson(chance);
            case ShowdownNode ignored -> null;
            case TerminalNode ignored -> null;
        };
    }

    private static ObjectNode actionToJson(ActionNode node) {
        List<Action> actions = node.getActions();
        List<String> labels = new ArrayList<>(actions.size());
        for (Action action : actions) labels.add(action.label());

        ObjectNode json = MAPPER.createObjectNode();
        json.set("actions", MAPPER.valueToTree(labels));
        json.put("player", node.getPlayer());

        ObjectNode children = null;
        for (int i = 0; i < actions.size(); i++) {
            ObjectNode childJson = toJson(node.getChildren().get(i));
            if (childJson == null) continue;
            if (children == null) children = MAPPER.createObjectNode();
            children.set(actions.get(i).label(), childJson);
        }
        if (children != null) json.set("children", children);

        json.set(
                "strategy",
                Objects.requireNonNull(node.getTrainable(), "trainable not set").dumps());
        json.put("nodeType", "action_node");
        return json;
    }

    private static ObjectNode chanceToJson(ChanceNode node) {
        List<Card> cards = node.getCards();
        List<GameTreeNode> children = node.getChildren();
        if (cards.size() != children.size()) {
            throw new IllegalStateException("%d cards but %d children".formatted(cards.size(), children.size()));
        }

        ObjectNode dealCards = MAPPER.createObjectNode();
        for (int i = 0; i < cards.size(); i++) {
            dealCards.set(cards.get(i).toString(), toJson(children.get(i)));
        }

        ObjectNode json = MAPPER.createObjectNode();
        json.set("dealCards", dealCards);
        json.put("dealNumber", dealCards.size());
        json.put("nodeType", "chance_node");
        return json;
    }
}
