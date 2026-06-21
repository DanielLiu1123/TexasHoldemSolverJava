package pokersolver.api;

import org.jspecify.annotations.Nullable;

/**
 * A solve submission. Only the scenario itself (board, ranges, pot, stack) is required — solver
 * parameters fall back to the defaults documented on each accessor.
 *
 * @param game "holdem" (default) or "shortdeck"
 * @param board comma-separated public cards, e.g. {@code "Qs,Jh,2h"} (flop), 4 cards for turn, 5
 *     for river
 * @param rangeIp in-position player's range, e.g. {@code "AA,KQs:0.5"}
 * @param rangeOop out-of-position player's range
 * @param pot chips already in the pot
 * @param effectiveStack remaining effective stack behind
 */
public record SolveRequest(
        @Nullable String game,
        @Nullable String board,
        @Nullable String rangeIp,
        @Nullable String rangeOop,
        @Nullable Float pot,
        @Nullable Float effectiveStack,
        @Nullable Integer raiseLimit,
        @Nullable StreetSpec flop,
        @Nullable StreetSpec turn,
        @Nullable StreetSpec river,
        @Nullable StreetSpec flopIp,
        @Nullable StreetSpec turnIp,
        @Nullable StreetSpec riverIp,
        @Nullable StreetSpec flopOop,
        @Nullable StreetSpec turnOop,
        @Nullable StreetSpec riverOop,
        @Nullable Integer iterations,
        @Nullable Integer progressInterval,
        @Nullable String algorithm,
        @Nullable String monteCarlo,
        @Nullable Integer threads,
        @Nullable Double stopExploitability) {

    /**
     * Bet sizing for one street. The shared {@code flop}/{@code turn}/{@code river} specs apply to
     * both positions; the {@code *Ip}/{@code *Oop} variants override per position (donk sizes are
     * only meaningful out of position). Sizes are in percent of the pot. Defaults: 50% bets and
     * raises, no donk bets, all-in enabled on turn and river only.
     */
    public record StreetSpec(
            float @Nullable [] betSizes,
            float @Nullable [] raiseSizes,
            float @Nullable [] donkSizes,
            @Nullable Boolean allin) {}
}
