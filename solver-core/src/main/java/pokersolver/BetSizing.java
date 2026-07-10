package pokersolver;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import pokersolver.solver.GameTreeBuildingSettings;

/**
 * The bet/raise legality rule: given the betting state at an action node and the street's
 * configured sizings, computes the legal amounts a player may put in.
 *
 * <p>Extracted from {@code GameTreeBuilder} so the sizing logic — pot-fraction conversion,
 * blind-relative rounding, the all-in option, and the post-flop legality filters — has a single
 * home that can be tested directly, independent of tree assembly. This is the intended landing
 * spot for proper minimum-raise enforcement (see the note below): the filter belongs here, behind
 * a white-box test, rather than inline in the recursive builder.
 */
final class BetSizing {

    enum BetType {
        BET,
        DONK,
        RAISE
    }

    private BetSizing() {}

    private static double roundNearest(double number, double roundNum) {
        roundNum = 1 / roundNum;
        return Math.round(number * roundNum) / roundNum;
    }

    private static float commit(int player, float ipCommit, float oopCommit) {
        if (player == 0) return ipCommit;
        else if (player == 1) return oopCommit;
        else throw new IllegalArgumentException("unknown player: " + player);
    }

    /**
     * Returns the legal amounts (chips on top of the current commit) a player may put in.
     *
     * @param player the acting player (0 = IP, 1 = OOP)
     * @param nextPlayer {@code 1 - player}
     * @param streetSetting the configured sizings for this street and player
     * @param betType whether this is an opening bet, a donk bet, or a raise
     * @return the legal amounts (chips on top of the current commit) for this action
     */
    static List<Double> possibleBets(
            int player,
            int nextPlayer,
            float ipCommit,
            float oopCommit,
            float smallBlind,
            float bigBlind,
            float stack,
            GameTreeBuildingSettings.StreetSetting streetSetting,
            BetType betType) {
        ArrayList<Double> betsRatios = new ArrayList<>();
        boolean allIn = streetSetting.allin();
        float[] betsFromRule;
        if (betType == BetType.BET) betsFromRule = streetSetting.betSizes();
        else if (betType == BetType.DONK) {
            betsFromRule = streetSetting.donkSizes();
            allIn = false;
        } else if (betType == BetType.RAISE) betsFromRule = streetSetting.raiseSizes();
        else throw new IllegalArgumentException("bet type unknown");
        if (betsFromRule == null) return new ArrayList<>();
        for (float oneBet : betsFromRule) {
            betsRatios.add((double) oneBet / 100);
        }
        float pot = ipCommit + oopCommit;
        float playerCommit = commit(player, ipCommit, oopCommit);
        float nextCommit = commit(nextPlayer, ipCommit, oopCommit);

        List<Double> possibleAmounts = new ArrayList<>();
        for (Double oneBet : betsRatios) {
            Double amount;
            if (oopCommit == smallBlind) {
                amount = oneBet * bigBlind - smallBlind;
                amount = roundNearest(amount, smallBlind);
            } else if (ipCommit == bigBlind && oopCommit == bigBlind) {
                amount = oneBet * bigBlind;
                amount = roundNearest(amount, smallBlind);
            } else {
                amount = oneBet * pot;
                amount = roundNearest(amount, bigBlind);
            }
            if (amount < stack - playerCommit) {
                possibleAmounts.add(amount);
            }
        }
        if (allIn) possibleAmounts.add((double) (stack - playerCommit));

        if (playerCommit != smallBlind) {
            possibleAmounts = possibleAmounts.stream()
                    .filter(e -> e > 0)
                    .map(n -> (double) n.intValue())
                    .collect(Collectors.toList());
        }
        if (playerCommit == smallBlind) {
            possibleAmounts =
                    possibleAmounts.stream().filter(e -> e >= bigBlind).collect(Collectors.toList());
        } else if (playerCommit == bigBlind && nextCommit == bigBlind) {
            possibleAmounts =
                    possibleAmounts.stream().filter(e -> e >= bigBlind).collect(Collectors.toList());
        }
        // A raise must put in more than the amount needed to call.
        possibleAmounts = possibleAmounts.stream()
                .filter(e -> e > nextCommit - playerCommit)
                .collect(Collectors.toList());
        possibleAmounts =
                possibleAmounts.stream().filter(e -> e <= stack - playerCommit).collect(Collectors.toList());

        // Minimum-raise enforcement. When the actor faces a wager (has committed less than the
        // opponent), a legal raise must raise *by* at least the outstanding amount to call — the
        // total put in is >= 2 * (nextCommit - playerCommit). A short all-in is exempt: a player
        // may always move all-in, even for less than a full raise (the tree does not model the
        // "does not reopen betting" consequence). For an opening bet (no outstanding wager) there
        // is nothing to raise over. (This replaces an earlier attempt whose sign was inverted — it
        // asserted commit(player) > commit(nextPlayer), but the raiser is the player who has
        // committed *less* — so it never fired.)
        float gap = nextCommit - playerCommit;
        if (gap > 0) {
            double minRaise = 2.0 * gap;
            double rawAllIn = stack - playerCommit;
            // Match the integer flooring the configured sizes went through above, so the all-in
            // amount can be recognized exactly.
            final double allInAmt = (playerCommit != smallBlind) ? (double) (int) rawAllIn : rawAllIn;
            possibleAmounts = possibleAmounts.stream()
                    .filter(e -> e >= minRaise || e == allInAmt)
                    .collect(Collectors.toList());
        }
        return possibleAmounts;
    }
}
