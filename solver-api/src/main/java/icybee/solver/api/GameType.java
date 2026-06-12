package icybee.solver.api;

import java.util.List;

/** The supported poker variants and where their compairer dictionaries live. */
public enum GameType {
    HOLDEM(
            "holdem",
            List.of("A", "K", "Q", "J", "T", "9", "8", "7", "6", "5", "4", "3", "2"),
            "compairer/card5_dic_sorted.txt",
            2598961),
    SHORTDECK(
            "shortdeck",
            List.of("A", "K", "Q", "J", "T", "9", "8", "7", "6"),
            "compairer/card5_dic_sorted_shortdeck.txt",
            376993);

    static final List<String> SUITS = List.of("h", "s", "d", "c");

    private final String id;
    private final List<String> ranks;
    private final String dictionaryFile;
    private final int dictionaryLines;

    GameType(String id, List<String> ranks, String dictionaryFile, int dictionaryLines) {
        this.id = id;
        this.ranks = ranks;
        this.dictionaryFile = dictionaryFile;
        this.dictionaryLines = dictionaryLines;
    }

    public String id() {
        return id;
    }

    public List<String> ranks() {
        return ranks;
    }

    public String dictionaryFile() {
        return dictionaryFile;
    }

    public int dictionaryLines() {
        return dictionaryLines;
    }

    public static GameType fromId(String id) {
        for (GameType type : values()) {
            if (type.id.equals(id)) return type;
        }
        throw new IllegalArgumentException(String.format("game type not found: %s (expected holdem|shortdeck)", id));
    }
}
