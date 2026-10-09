package pokersolver.nodes;

import java.util.List;
import org.jspecify.annotations.Nullable;
import pokersolver.Card;
import pokersolver.Deck;

/**
 * A node where the next community card is dealt: one child per card in the deck, including the cards
 * the running board has already used. The traversal skips those children rather than pruning them, so
 * a chance node's children stay index-aligned with {@link Deck#cards()} — the card dealt down edge
 * {@code i} is {@code Deck.card(i)}.
 *
 * <p>A chance node whose round is {@code RIVER} deals the river card — it sits <em>between</em> the
 * turn and the river, not on it.
 *
 * @see #isDonk()
 */
public final class ChanceNode extends GameTreeNode {

    private final boolean donk;
    private List<GameTreeNode> children;

    public ChanceNode(List<GameTreeNode> children, GameRound round, double pot, @Nullable GameTreeNode parent) {
        this(children, round, pot, parent, false);
    }

    public ChanceNode(
            List<GameTreeNode> children, GameRound round, double pot, @Nullable GameTreeNode parent, boolean donk) {
        super(round, pot, parent);
        this.children = children;
        this.donk = donk;
    }

    /** The deck, whose order this node's children follow. */
    public List<Card> getCards() {
        return Deck.cards();
    }

    public List<GameTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<GameTreeNode> children) {
        this.children = children;
    }

    /**
     * Whether the out-of-position player closed the previous round by calling, which lets them lead
     * ("donk bet") into the next one with their own bet sizes.
     */
    public boolean isDonk() {
        return donk;
    }
}
