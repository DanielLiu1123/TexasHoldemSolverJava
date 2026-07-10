package pokersolver.nodes;

import pokersolver.exceptions.RoundNotFoundException;

/** A betting round. */
public enum GameRound {
    PREFLOP,
    FLOP,
    TURN,
    RIVER;

    /** The 1-based round number used by the tree files and the tree builder. */
    public int number() {
        return switch (this) {
            case PREFLOP -> 1;
            case FLOP -> 2;
            case TURN -> 3;
            case RIVER -> 4;
        };
    }

    /** The lowercase name used by the tree files and the strategy JSON. */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static GameRound fromInt(int round) {
        return switch (round) {
            case 1 -> PREFLOP;
            case 2 -> FLOP;
            case 3 -> TURN;
            case 4 -> RIVER;
            default -> throw new RoundNotFoundException(String.format("round %s not found", round));
        };
    }

    public static GameRound fromString(String round) {
        for (GameRound value : values()) {
            if (value.id().equals(round)) return value;
        }
        throw new RoundNotFoundException(String.format("round %s not found", round));
    }
}
