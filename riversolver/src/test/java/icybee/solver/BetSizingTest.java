package icybee.solver;

import static org.assertj.core.api.Assertions.assertThat;

import icybee.solver.solver.GameTreeBuildingSettings;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * White-box tests for the bet/raise legality rule. This is the test surface the rule lacked while
 * it was inlined in the tree builder — and the guard against which proper minimum-raise enforcement
 * should be developed.
 */
class BetSizingTest {

    private static GameTreeBuildingSettings.StreetSetting setting(
            float[] bet, float[] raise, float @org.jspecify.annotations.Nullable [] donk, boolean allin) {
        return new GameTreeBuildingSettings.StreetSetting(bet, raise, donk, allin);
    }

    @Test
    void halfPotBetIsRoundedToBigBlindAndLegal() {
        // pot = 10, 50% pot = 5; deep stacks, both already committed 5.
        List<Double> bets = BetSizing.possibleBets(
                0,
                1,
                5f,
                5f,
                0.5f,
                1f,
                100f,
                setting(new float[] {50f}, new float[] {50f}, null, false),
                BetSizing.BetType.BET);
        assertThat(bets).containsExactly(5.0);
    }

    @Test
    void allInIsAppendedWhenEnabled() {
        List<Double> bets = BetSizing.possibleBets(
                0,
                1,
                5f,
                5f,
                0.5f,
                1f,
                100f,
                setting(new float[] {50f}, new float[] {50f}, null, true),
                BetSizing.BetType.BET);
        // all-in = stack - commit = 100 - 5
        assertThat(bets).containsExactly(5.0, 95.0);
    }

    @Test
    void donkWithoutDonkSizesIsEmpty() {
        List<Double> bets = BetSizing.possibleBets(
                1,
                0,
                5f,
                5f,
                0.5f,
                1f,
                100f,
                setting(new float[] {50f}, new float[] {50f}, null, true),
                BetSizing.BetType.DONK);
        assertThat(bets).isEmpty();
    }

    @Test
    void raiseMustExceedTheOutstandingCall() {
        // IP committed 5, OOP has bet to 15: the 10 needed to call is not itself a legal raise.
        float[] half = {50f}; // 50% of pot(20) = 10 == call amount -> filtered out
        assertThat(BetSizing.possibleBets(
                        0, 1, 5f, 15f, 0.5f, 1f, 100f, setting(half, half, null, false), BetSizing.BetType.RAISE))
                .isEmpty();

        float[] big = {150f}; // 150% of pot(20) = 30 > 10 -> legal
        assertThat(BetSizing.possibleBets(
                        0, 1, 5f, 15f, 0.5f, 1f, 100f, setting(big, big, null, false), BetSizing.BetType.RAISE))
                .containsExactly(30.0);
    }

    @Test
    void everyAmountRespectsStackAndCallInvariants() {
        float ip = 5f;
        float oop = 15f;
        float stack = 100f;
        List<Double> raises = BetSizing.possibleBets(
                0,
                1,
                ip,
                oop,
                0.5f,
                1f,
                stack,
                setting(new float[] {100f, 200f, 300f}, new float[] {100f, 200f, 300f}, null, true),
                BetSizing.BetType.RAISE);
        assertThat(raises).isNotEmpty();
        for (double amount : raises) {
            assertThat(amount).isGreaterThan(oop - ip); // exceeds the outstanding call
            assertThat(amount).isLessThanOrEqualTo(stack - ip); // never over the stack
        }
    }
}
