package pokersolver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pokersolver.solver.Algorithm;
import pokersolver.solver.SequentialCfrSolver;

class TrainingProgressTest {
    @Test
    void reportsCompletedIterationsIncludingTheFinalPartialInterval() throws Exception {
        List<Integer> iterations = new ArrayList<>();
        List<Long> elapsed = new ArrayList<>();
        var config = SolverFixture.builder(
                        SolverFixture.RIVER_TREE,
                        SolverFixture.RIVER_BOARD,
                        SolverFixture.NARROW_RANGE,
                        Algorithm.DISCOUNTED_CFR,
                        23)
                .printInterval(10)
                .progressListener((iteration, exploitability, elapsedMs) -> {
                    iterations.add(iteration);
                    elapsed.add(elapsedMs);
                })
                .build();
        new SequentialCfrSolver(config).train();
        assertThat(iterations).containsExactly(1, 10, 20, 23);
        assertThat(elapsed).isSorted();
    }
}
