package pokersolver.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.javalin.http.NotFoundResponse;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import pokersolver.Card;
import pokersolver.GameTree;
import pokersolver.solver.GameTreeBuildingSettings;
import pokersolver.solver.SequentialCfrSolver;
import pokersolver.solver.SolverConfig;
import pokersolver.utils.PrivateRangeConverter;

class StrategyNodeViewTest {
    @Test
    void chanceNavigationExcludesExistingBoardCardsAndBlockedHands() throws Exception {
        int[] board =
                Stream.of("2c", "3d", "7h", "9s").mapToInt(Card::strCard2int).toArray();
        long mask = Card.boardInts2long(board);
        var street = new GameTreeBuildingSettings.StreetSetting(new float[0], new float[0], null, false);
        var tree = new GameTree(
                10,
                10,
                3,
                0,
                0.5f,
                1,
                20,
                new GameTreeBuildingSettings(street, street, street, street, street, street));
        new SequentialCfrSolver(SolverConfig.builder()
                        .tree(tree)
                        .initialBoard(board)
                        .range1(PrivateRangeConverter.rangeStr2Cards("AA,KK", board))
                        .range2(PrivateRangeConverter.rangeStr2Cards("AA,KK", board))
                        .iterationNumber(1)
                        .build())
                .train();
        var chance = StrategyNodeView.atPath(tree, "CHECK,CHECK", mask);
        assertThat(chance.get("cards").size()).isEqualTo(48);
        assertThat(chance.get("cards").toString()).doesNotContain("2c", "3d", "7h", "9s");
        assertThatThrownBy(() -> StrategyNodeView.atPath(tree, "CHECK,CHECK,2c", mask))
                .isInstanceOf(NotFoundResponse.class);
        var river = StrategyNodeView.atPath(tree, "CHECK,CHECK,Ah", mask);
        assertThat(river.get("strategy").get("strategy").toString()).doesNotContain("Ah");
        assertThat(river.get("strategy").get("strategy").size()).isEqualTo(9);
    }
}
