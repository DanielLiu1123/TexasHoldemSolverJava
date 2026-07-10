package pokersolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pokersolver.ranges.PrivateCards;
import pokersolver.utils.PrivateRangeConverter;

/**
 * Range notation, which had no test at all: {@code "AA"} is six combos, {@code "AKs"} four, {@code
 * "AKo"} twelve, bare {@code "AK"} all sixteen.
 */
class PrivateRangeConverterTest {

    private static final int[] NO_BOARD = new int[0];

    private static PrivateCards[] parse(String range, int... board) {
        return PrivateRangeConverter.rangeStr2Cards(range, board);
    }

    @ParameterizedTest(name = "\"{0}\" expands to {1} combos")
    @CsvSource({
        "AA, 6",
        "AKs, 4",
        "AKo, 12",
        "AK, 16",
        "AAo, 6",
        "'AA,KK', 12",
        "'AA,AKs', 10",
    })
    void rangeEntriesExpandToTheirCombos(String range, int expected) {
        assertThat(parse(range, NO_BOARD)).hasSize(expected);
    }

    @Test
    void bareRankPairIncludesSuitedAndOffsuitCombos() {
        PrivateCards[] combos = parse("AK", NO_BOARD);
        long suited = Arrays.stream(combos)
                .filter(c -> (c.card1 % 4) == (c.card2 % 4))
                .count();
        assertThat(suited)
                .as("\"AK\" contains the four suited combos as well as the twelve offsuit")
                .isEqualTo(4);
        assertThat(combos).hasSize(16);
    }

    @Test
    void suitedEntriesShareASuitAndOffsuitEntriesDoNot() {
        assertThat(parse("AKs", NO_BOARD))
                .allSatisfy(c -> assertThat(c.card1 % 4).isEqualTo(c.card2 % 4));
        assertThat(parse("AKo", NO_BOARD))
                .allSatisfy(c -> assertThat(c.card1 % 4).isNotEqualTo(c.card2 % 4));
    }

    @Test
    void weightsAttachToEveryComboOfTheirEntry() {
        PrivateCards[] combos = parse("AKs:0.5", NO_BOARD);
        assertThat(combos).hasSize(4).allSatisfy(c -> assertThat(c.weight).isEqualTo(0.5f));
    }

    @Test
    void zeroWeightedEntriesAreDropped() {
        assertThat(parse("AA,KK:0", NO_BOARD)).hasSize(6);
    }

    @Test
    void theBoardBlocksCombosThatShareACardWithIt() {
        int[] board = {Card.strCard2int("As"), Card.strCard2int("Kd"), Card.strCard2int("2h")};

        // Two of the six ace pairs use the ace of spades.
        assertThat(parse("AA", board)).hasSize(3);
        // Suited combos are filtered too — AsKs and AdKd are both blocked.
        assertThat(parse("AKs", board)).hasSize(2);
    }

    @Test
    void everyComboIsDistinct() {
        PrivateCards[] combos = parse("AA,KK,QQ,AK,AQ,KQ", NO_BOARD);
        assertThat(Stream.of(combos).map(PrivateCards::hashCode).distinct().count())
                .isEqualTo(combos.length);
    }

    @Test
    void combosCarryTheirOwnBoardMask() {
        for (PrivateCards combo : parse("AKs", NO_BOARD)) {
            assertThat(combo.mask()).isEqualTo((1L << combo.card1) | (1L << combo.card2));
            assertThat(Long.bitCount(combo.mask())).isEqualTo(2);
        }
    }

    @Test
    void handLabelsAreCanonical() {
        assertThat(Stream.of(parse("AKs", NO_BOARD)).map(PrivateCards::summary).distinct())
                .containsExactly("AKs");
        assertThat(Stream.of(parse("AKo", NO_BOARD)).map(PrivateCards::summary).distinct())
                .containsExactly("AKo");
        assertThat(Stream.of(parse("AA", NO_BOARD)).map(PrivateCards::summary).distinct())
                .containsExactly("AA");
    }

    @Test
    void malformedRangesAreRejected() {
        assertThatThrownBy(() -> parse("AAs", NO_BOARD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid combo");
        assertThatThrownBy(() -> parse("AKx", NO_BOARD)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parse("AKQJ", NO_BOARD)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parse("AA:0.5:0.5", NO_BOARD)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parse("AA,AA", NO_BOARD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }
}
