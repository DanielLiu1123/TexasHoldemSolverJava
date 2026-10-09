package pokersolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pokersolver.ranges.PrivateCards;
import pokersolver.solver.Algorithm;
import pokersolver.solver.ParallelCfrSolver;
import pokersolver.solver.SequentialCfrSolver;
import pokersolver.solver.SolverConfig;

class SolverInputTest {
    private static SolverConfig.Builder scenario() {
        return SolverFixture.builder(
                SolverFixture.RIVER_TREE,
                SolverFixture.RIVER_BOARD,
                SolverFixture.NARROW_RANGE,
                Algorithm.DISCOUNTED_CFR,
                10);
    }

    private static PrivateCards hand(String first, String second) {
        return new PrivateCards(Card.strCard2int(first), Card.strCard2int(second), 1);
    }

    @Test
    void invalidIterationsBoardAndStoppingThresholdAreRejected() {
        assertThatThrownBy(() -> scenario().iterationNumber(0).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scenario().printInterval(0).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scenario().stopExploitability(Double.NaN).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scenario().stopExploitability(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> scenario().initialBoard(SolverFixture.TURN_BOARD).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scenario()
                        .initialBoard(SolverFixture.board("Kd", "Jd", "Td", "7s", "7s"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyOrMutuallyBlockedRangesAreRejected() {
        assertThatThrownBy(() -> new SequentialCfrSolver(
                        scenario().range1(new PrivateCards[0]).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("compatible");
        assertThatThrownBy(() -> new SequentialCfrSolver(scenario()
                        .range1(new PrivateCards[] {hand("As", "Ah")})
                        .range2(new PrivateCards[] {hand("As", "Ks")})
                        .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("compatible");
    }

    @Test
    void aHandWithNoCompatibleOpponentDoesNotPoisonExploitability() throws Exception {
        List<Float> values = new ArrayList<>();
        new SequentialCfrSolver(scenario()
                        .range1(new PrivateCards[] {hand("As", "Ah"), hand("Ac", "Ad")})
                        .range2(new PrivateCards[] {hand("As", "Ks")})
                        .progressListener((iteration, exploitability, elapsedMs) -> values.add(exploitability))
                        .build())
                .train();
        assertThat(values).isNotEmpty().allSatisfy(value -> assertThat(Float.isFinite(value))
                .isTrue());
    }

    @Test
    void invalidForkSettingsAreRejectedBeforeTraining() {
        assertThatThrownBy(() -> new ParallelCfrSolver(scenario().build(), 2, Double.NaN, 1, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParallelCfrSolver(scenario().build(), 2, 1, 1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParallelCfrSolver(scenario().build(), 2, 1, 1, 1, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
