package pokersolver;

import static pokersolver.utils.JsonUtil.MAPPER;

import pokersolver.nodes.ActionNode;
import pokersolver.nodes.ChanceNode;
import pokersolver.nodes.GameActions;
import pokersolver.nodes.GameTreeNode;
import pokersolver.nodes.ShowdownNode;
import pokersolver.nodes.TerminalNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/** Serializes a GameTree to JSON. */
class GameTreeSerializer {

    static ObjectNode dumps(GameTreeNode root) {
        return Objects.requireNonNull(toJson(root), "root node should not produce null JSON");
    }

    @SuppressWarnings("NullAway")
    private static @Nullable ObjectNode toJson(GameTreeNode node) {
        switch (node) {
            case ActionNode actionNode -> {
                ObjectNode retJson = MAPPER.createObjectNode();

                List<String> actionsStr = new ArrayList<>();
                for (GameActions oneAction : actionNode.getActions()) actionsStr.add(oneAction.toString());

                retJson.set("actions", MAPPER.valueToTree(actionsStr));
                retJson.put("player", actionNode.getPlayer());

                ObjectNode childrenJson = null;
                for (int i = 0; i < actionNode.getActions().size(); i++) {
                    GameActions oneAction = actionNode.getActions().get(i);
                    GameTreeNode oneChild = actionNode.getChildren().get(i);
                    ObjectNode oneJson = toJson(oneChild);
                    if (oneJson != null) {
                        if (childrenJson == null) childrenJson = MAPPER.createObjectNode();
                        childrenJson.set(oneAction.toString(), oneJson);
                    }
                }
                if (childrenJson != null) {
                    retJson.set("children", childrenJson);
                }
                retJson.set(
                        "strategy",
                        Objects.requireNonNull(actionNode.getTrainable(), "trainable not set on action node")
                                .dumps(false));
                retJson.put("node_type", "action_node");
                return retJson;
            }
            case TerminalNode _, ShowdownNode _ -> {
                return null;
            }
            case ChanceNode chanceNode -> {
                List<Card> cards = chanceNode.getCards();
                List<GameTreeNode> chanceChildren = chanceNode.getChildren();
                if (cards.size() != chanceChildren.size()) throw new RuntimeException("length not match");
                ObjectNode retJson = MAPPER.createObjectNode();

                ObjectNode dealCards = MAPPER.createObjectNode();
                for (int i = 0; i < cards.size(); i++) {
                    Card oneCard = cards.get(i);
                    GameTreeNode gameTreeNode = chanceChildren.get(i);
                    dealCards.set(oneCard.toString(), toJson(gameTreeNode));
                }

                retJson.set("deal_cards", dealCards);
                retJson.put("deal_number", dealCards.size());
                retJson.put("node_type", "chance_node");
                return retJson;
            }
            case null, default -> throw new RuntimeException();
        }
    }

    private GameTreeSerializer() {}
}
