package pokersolver.solver;

import org.jspecify.annotations.Nullable;
import pokersolver.exceptions.RoundNotFoundException;
import pokersolver.nodes.GameRound;

/** The bet sizes offered to each player on each post-flop street, as percentages of the pot. */
public record GameTreeBuildingSettings(
        StreetSetting flopIp,
        StreetSetting turnIp,
        StreetSetting riverIp,
        StreetSetting flopOop,
        StreetSetting turnOop,
        StreetSetting riverOop) {

    public record StreetSetting(float[] betSizes, float[] raiseSizes, float @Nullable [] donkSizes, boolean allin) {}

    /** The sizes offered to {@code player} (0 = in position, 1 = out of position) on {@code round}. */
    public StreetSetting getSettings(GameRound round, int player) {
        if (player != 0 && player != 1) throw new IllegalArgumentException("unknown player: " + player);
        boolean inPosition = player == 0;
        return switch (round) {
            case FLOP -> inPosition ? flopIp : flopOop;
            case TURN -> inPosition ? turnIp : turnOop;
            case RIVER -> inPosition ? riverIp : riverOop;
            case PREFLOP -> throw new RoundNotFoundException("this is a post-flop solver: no preflop sizings");
        };
    }
}
