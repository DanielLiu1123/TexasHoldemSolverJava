package pokersolver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The deck's invariant, which the chance node depends on without being able to state it: the card at
 * index {@code i} is the card with id {@code i}.
 *
 * <p>A chance node's children are index-aligned with {@link Deck#cards()}, so the card dealt down
 * edge {@code i} is {@code Deck.card(i)}. Reordering this list would silently deal the wrong card
 * down every edge — the strategy would still converge, to the wrong game.
 */
class DeckTest {

    @Test
    void theDeckHasFiftyTwoCards() {
        assertThat(Deck.SIZE).isEqualTo(52);
        assertThat(Deck.cards()).hasSize(52);
    }

    @Test
    void indexEqualsCardId() {
        for (int id = 0; id < Deck.SIZE; id++) {
            assertThat(Deck.card(id).getCardInt()).as("deck index %d", id).isEqualTo(id);
        }
    }

    @Test
    void everyCardAppearsExactlyOnce() {
        long seen = 0;
        for (Card card : Deck.cards()) {
            assertThat(seen & card.mask()).as("%s appears twice", card).isZero();
            seen |= card.mask();
        }
        assertThat(Long.bitCount(seen)).isEqualTo(52);
    }

    @Test
    void theDeckIsOrderedByRankThenSuit() {
        assertThat(Deck.card(0)).hasToString("2c");
        assertThat(Deck.card(1)).hasToString("2d");
        assertThat(Deck.card(2)).hasToString("2h");
        assertThat(Deck.card(3)).hasToString("2s");
        assertThat(Deck.card(4)).hasToString("3c");
        assertThat(Deck.card(51)).hasToString("As");
    }

    @Test
    void theCardListIsImmutable() {
        assertThat(Deck.cards()).isSameAs(Deck.cards());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Deck.cards().add(Deck.card(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
