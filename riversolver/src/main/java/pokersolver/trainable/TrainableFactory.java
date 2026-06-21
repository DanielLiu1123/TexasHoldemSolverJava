package pokersolver.trainable;

import pokersolver.nodes.ActionNode;
import pokersolver.ranges.PrivateCards;

/** Creates a {@link Trainable} instance for a given action node and private card range. */
@FunctionalInterface
public interface TrainableFactory {
    Trainable create(ActionNode node, PrivateCards[] privateCards);
}
