package pokersolver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pokersolver.nodes.Action;
import pokersolver.nodes.ActionNode;
import pokersolver.nodes.ChanceNode;
import pokersolver.nodes.GameRound;
import pokersolver.nodes.GameTreeNode;
import pokersolver.nodes.ShowdownNode;
import pokersolver.nodes.TerminalNode;
import pokersolver.solver.GameTreeBuildingSettings;

/**
 * The tree the betting rules build.
 *
 * <p>{@code GameTreeBuilder}'s internal {@code Rule} record threads eight positional components —
 * two commits, two blinds, a stack, a round, a raise limit — through every node it creates. Six of
 * them are floats. Transposing two would compile silently and produce a tree that still solves, to
 * the wrong game. The payoff and commit assertions below are what make that visible.
 */
class GameTreeBuilderTest {

    private static final float SMALL_BLIND = 0.5f;
    private static final float BIG_BLIND = 1.0f;
    private static final float POT = 20f;
    private static final float STACK = 100f;

    private static GameTreeBuildingSettings settings(boolean allin) {
        GameTreeBuildingSettings.StreetSetting street =
                new GameTreeBuildingSettings.StreetSetting(new float[] {50f}, new float[] {50f}, null, allin);
        return new GameTreeBuildingSettings(street, street, street, street, street, street);
    }

    /** A river tree: both players in for pot/2 each, five raises allowed, no all-in. */
    private static GameTreeNode riverRoot() {
        return SolverEnvironment.gameTreeFromParams(
                        POT / 2, POT / 2, GameRound.RIVER.number(), 5, SMALL_BLIND, BIG_BLIND, STACK, settings(false))
                .getRoot();
    }

    private static GameTreeNode child(ActionNode node, String label) {
        List<Action> actions = node.getActions();
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i).label().equals(label)) return node.getChildren().get(i);
        }
        throw new AssertionError("no %s among %s".formatted(label, actions));
    }

    @Test
    void theRootIsTheOutOfPositionPlayersCheckOrBet() {
        GameTreeNode root = riverRoot();
        assertThat(root).isInstanceOf(ActionNode.class);
        ActionNode action = (ActionNode) root;
        assertThat(action.getPlayer())
                .as("out of position acts first post-flop")
                .isEqualTo(1);
        assertThat(action.getRound()).isEqualTo(GameRound.RIVER);
        assertThat(action.getPot()).isEqualTo(POT);
        assertThat(action.getActions().stream().map(Action::label)).containsExactly("CHECK", "BET 10.0");
    }

    @Test
    void aHalfPotBetIsHalfThePot() {
        // pot = 20, so 50% = 10. If the stack or a blind had landed in the pot's slot this moves.
        ActionNode root = (ActionNode) riverRoot();
        assertThat(root.getActions().get(1)).isEqualTo(new Action.Bet(10.0));
    }

    @Test
    void checkingThroughOnTheRiverShowsDown() {
        ActionNode root = (ActionNode) riverRoot();
        GameTreeNode afterCheck = child(root, "CHECK");
        assertThat(afterCheck).isInstanceOf(ActionNode.class);
        assertThat(((ActionNode) afterCheck).getPlayer()).isEqualTo(0);

        GameTreeNode showdown = child((ActionNode) afterCheck, "CHECK");
        assertThat(showdown).isInstanceOf(ShowdownNode.class);
        // Each player put in half the pot; the winner takes the other half.
        assertThat(((ShowdownNode) showdown).payoffsIfWins(0)).containsExactly(POT / 2, -POT / 2);
        assertThat(((ShowdownNode) showdown).payoffsIfWins(1)).containsExactly(-POT / 2, POT / 2);
        assertThat(((ShowdownNode) showdown).tiePayoffs()).containsExactly(0.0, 0.0);
    }

    @Test
    void foldingForfeitsExactlyWhatTheFolderCommitted() {
        ActionNode root = (ActionNode) riverRoot();
        // OOP bets 10 (now in for 20), IP folds having committed only 10.
        ActionNode facingBet = (ActionNode) child(root, "BET 10.0");
        assertThat(facingBet.getPlayer()).isEqualTo(0);

        GameTreeNode terminal = child(facingBet, "FOLD");
        assertThat(terminal).isInstanceOf(TerminalNode.class);
        assertThat(((TerminalNode) terminal).getPayoffs())
                .as("player 0 folds having committed pot/2, and loses it")
                .containsExactly(-POT / 2, POT / 2);
        assertThat(((TerminalNode) terminal).winner()).isEqualTo(1);
    }

    @Test
    void callingABetOnTheRiverShowsDownForTheRaisedAmount() {
        ActionNode root = (ActionNode) riverRoot();
        ActionNode facingBet = (ActionNode) child(root, "BET 10.0");
        GameTreeNode showdown = child(facingBet, "CALL");

        assertThat(showdown).isInstanceOf(ShowdownNode.class);
        // Both are now in for 20: the winner takes the loser's 20.
        assertThat(((ShowdownNode) showdown).payoffsIfWins(0)).containsExactly(20.0, -20.0);
    }

    @Test
    void aTurnTreeDealsEveryCardAtItsChanceNode() {
        GameTreeNode root = SolverEnvironment.gameTreeFromParams(
                        POT / 2, POT / 2, GameRound.TURN.number(), 2, SMALL_BLIND, BIG_BLIND, STACK, settings(false))
                .getRoot();

        // check, check → the turn is dealt.
        GameTreeNode afterCheck = child((ActionNode) root, "CHECK");
        GameTreeNode chance = child((ActionNode) afterCheck, "CHECK");
        assertThat(chance).isInstanceOf(ChanceNode.class);

        ChanceNode deal = (ChanceNode) chance;
        assertThat(deal.getChildren()).as("one child per card in the deck").hasSize(52);
        assertThat(deal.getCards()).hasSize(52);
        assertThat(deal.getRound()).isEqualTo(GameRound.RIVER);
        // Children are index-aligned with the deck: edge i deals card i.
        for (int i = 0; i < 52; i++) {
            assertThat(deal.getCards().get(i).getCardInt()).isEqualTo(i);
        }
    }

    /** A raise must at least double the outstanding amount to call, so a half-pot raise is illegal here. */
    @Test
    void aRaiseBelowTheMinimumIsNotOffered() {
        // OOP bets 10 into 20, so IP faces a 10 call. 50% of the 30 pot is 15 — under the 20 minimum.
        ActionNode facingBet = (ActionNode) child((ActionNode) riverRoot(), "BET 10.0");
        assertThat(facingBet.getActions().stream().map(Action::label))
                .as("a 15 raise would not raise *by* the 10 it costs to call")
                .containsExactly("CALL", "FOLD");
    }

    @Test
    void theRaiseLimitBoundsTheBettingLine() {
        // Pot-sized raises clear the minimum, so the line can actually re-raise.
        GameTreeBuildingSettings.StreetSetting potRaises =
                new GameTreeBuildingSettings.StreetSetting(new float[] {50f}, new float[] {100f}, null, false);
        GameTreeBuildingSettings potSettings =
                new GameTreeBuildingSettings(potRaises, potRaises, potRaises, potRaises, potRaises, potRaises);

        ActionNode root = (ActionNode) SolverEnvironment.gameTreeFromParams(
                        POT / 2, POT / 2, GameRound.RIVER.number(), 1, SMALL_BLIND, BIG_BLIND, STACK, potSettings)
                .getRoot();

        // OOP bets 10 (pot 30), IP raises the full pot to 30. With raiseLimit = 1, OOP cannot re-raise.
        ActionNode facingBet = (ActionNode) child(root, "BET 10.0");
        assertThat(facingBet.getActions().stream().map(Action::label)).contains("RAISE 30.0");

        ActionNode facingRaise = (ActionNode) child(facingBet, "RAISE 30.0");
        assertThat(facingRaise.getActions().stream().map(Action::label))
                .as("the raise cap is reached")
                .containsExactly("CALL", "FOLD");
    }

    @Test
    void allInIsOfferedWhenEnabledAndSizedToTheStack() {
        ActionNode root = (ActionNode) SolverEnvironment.gameTreeFromParams(
                        POT / 2, POT / 2, GameRound.RIVER.number(), 5, SMALL_BLIND, BIG_BLIND, STACK, settings(true))
                .getRoot();
        // stack 100, already committed 10 → shove is 90 on top.
        assertThat(root.getActions().stream().map(Action::label)).contains("BET 90.0");
    }

    @Test
    void everyActionNodeHasAsManyChildrenAsActions() {
        assertBalanced(riverRoot());
    }

    private static void assertBalanced(GameTreeNode node) {
        switch (node) {
            case ActionNode action -> {
                assertThat(action.getChildren())
                        .as("actions and children agree at %s", action.history())
                        .hasSameSizeAs(action.getActions());
                action.getChildren().forEach(GameTreeBuilderTest::assertBalanced);
            }
            case ChanceNode chance -> chance.getChildren().forEach(GameTreeBuilderTest::assertBalanced);
            case ShowdownNode ignored -> {}
            case TerminalNode ignored -> {}
        }
    }
}
