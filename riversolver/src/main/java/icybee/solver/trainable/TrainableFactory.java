package icybee.solver.trainable;

import icybee.solver.nodes.ActionNode;
import icybee.solver.ranges.PrivateCards;

/** Creates a {@link Trainable} instance for a given action node and private card range. */
@FunctionalInterface
public interface TrainableFactory {
    Trainable create(ActionNode node, PrivateCards[] privateCards);
}
