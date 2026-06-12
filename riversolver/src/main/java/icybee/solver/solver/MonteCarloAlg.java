package icybee.solver.solver;

public enum MonteCarloAlg {
    NONE,
    PUBLIC;

    /** Parses the external identifier used by the CLI and API ("none", "public"). */
    public static MonteCarloAlg fromId(String id) {
        return switch (id) {
            case "none" -> NONE;
            case "public" -> PUBLIC;
            default -> throw new IllegalArgumentException(String.format("monte carlo type not found: %s", id));
        };
    }
}
