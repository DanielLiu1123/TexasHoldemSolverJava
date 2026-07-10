package pokersolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pokersolver.exceptions.BoardNotFoundException;
import pokersolver.exceptions.CardsNotFoundException;

/** The card encoding: {@code (rank - 2) * 4 + suit}, and the 52-bit board masks built from it. */
class CardTest {

    @Test
    void everyCardIdRoundTripsThroughItsName() {
        for (int id = 0; id < 52; id++) {
            assertThat(Card.strCard2int(Card.intCard2Str(id))).isEqualTo(id);
        }
    }

    @Test
    void theEncodingOrdersCardsByRankThenSuit() {
        assertThat(Card.strCard2int("2c")).isZero();
        assertThat(Card.strCard2int("2s")).isEqualTo(3);
        assertThat(Card.strCard2int("3c")).isEqualTo(4);
        assertThat(Card.strCard2int("As")).isEqualTo(51);
    }

    @Test
    void aCardMaskHasExactlyItsOwnBitSet() {
        for (int id = 0; id < 52; id++) {
            assertThat(Card.boardCards2long(new Card[] {new Card(Card.intCard2Str(id))}))
                    .isEqualTo(1L << id);
        }
    }

    @Test
    void aBoardMaskRoundTripsThroughItsCards() {
        Card[] board = {
            new Card("6c"),
            new Card("6d"),
            new Card("7c"),
            new Card("7d"),
            new Card("8s"),
            new Card("6h"),
            new Card("7s")
        };
        long mask = Card.boardCards2long(board);
        assertThat(Card.cardCount(mask)).isEqualTo(7);
        assertThat(Card.boardCards2long(Card.long2boardCards(mask))).isEqualTo(mask);
        assertThat(Card.long2board(mask)).containsExactly(Card.long2board(mask));
    }

    @Test
    void boardsIntersectExactlyWhenTheyShareACard() {
        long spadeAce = 1L << Card.strCard2int("As");
        long heartAce = 1L << Card.strCard2int("Ah");
        assertThat(Card.boardsHasIntercept(spadeAce, spadeAce)).isTrue();
        assertThat(Card.boardsHasIntercept(spadeAce, heartAce)).isFalse();
        assertThat(Card.boardsHasIntercept(spadeAce | heartAce, heartAce)).isTrue();
    }

    @Test
    void aRepeatedCardDoesNotInflateItsBoardMask() {
        // The mask is a set; the previous implementation added bits and silently carried.
        assertThat(Card.boardInts2long(new int[] {5, 5, 5})).isEqualTo(1L << 5);
    }

    @Test
    void malformedCardsAreRejected() {
        assertThatThrownBy(() -> Card.strCard2int("Xs")).isInstanceOf(CardsNotFoundException.class);
        assertThatThrownBy(() -> Card.strCard2int("Ax")).isInstanceOf(CardsNotFoundException.class);
        assertThatThrownBy(() -> Card.strCard2int("A")).isInstanceOf(CardsNotFoundException.class);
        assertThatThrownBy(() -> Card.boardInts2long(new int[] {52})).isInstanceOf(CardsNotFoundException.class);
        assertThatThrownBy(() -> Card.boardInts2long(new int[0])).isInstanceOf(BoardNotFoundException.class);
    }

    @Test
    void longToBoardListsCardsInAscendingId() {
        long mask = (1L << 51) | (1L << 0) | (1L << 25);
        assertThat(Card.long2board(mask)).containsExactly(0, 25, 51);
    }
}
