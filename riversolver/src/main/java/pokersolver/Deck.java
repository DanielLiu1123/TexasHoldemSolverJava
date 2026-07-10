package pokersolver;

import java.util.ArrayList;
import java.util.List;
import pokersolver.eval.HandEvaluator;
import pokersolver.eval.PokerVariant;

/**
 * The deck a game is played with — thirteen ranks for hold'em, nine for short-deck — together with
 * the {@link HandEvaluator} that ranks hands under that variant's rules.
 *
 * <p>Immutable, and safe to share across the solver's worker threads.
 */
public final class Deck {

    private final List<String> ranks;
    private final List<String> suits;
    private final List<Card> cards;
    private final HandEvaluator evaluator;

    public Deck(List<String> ranks, List<String> suits) {
        this.ranks = List.copyOf(ranks);
        this.suits = List.copyOf(suits);
        List<Card> cards = new ArrayList<>(ranks.size() * suits.size());
        for (String rank : this.ranks) {
            for (String suit : this.suits) cards.add(new Card(rank + suit));
        }
        this.cards = List.copyOf(cards);
        this.evaluator = HandEvaluator.forVariant(PokerVariant.forRankCount(this.ranks.size()));
    }

    public List<Card> getCards() {
        return cards;
    }

    public List<String> getRanks() {
        return ranks;
    }

    public List<String> getSuits() {
        return suits;
    }

    /** Ranks hands under this deck's variant. */
    public HandEvaluator evaluator() {
        return evaluator;
    }
}
