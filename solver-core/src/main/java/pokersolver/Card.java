package pokersolver;

import java.util.Arrays;
import pokersolver.exceptions.BoardNotFoundException;
import pokersolver.exceptions.CardsNotFoundException;

/**
 * One playing card, identified by an integer in {@code [0, 52)} encoded as {@code (rank - 2) * 4 +
 * suit} — ranks ascending from the deuce, suits ordered clubs, diamonds, hearts, spades.
 *
 * <p>Sets of cards travel through the solver as 52-bit masks (bit {@code i} = card {@code i}) rather
 * than as collections: intersection is a single {@code &}, and {@link pokersolver.eval.HandEvaluator}
 * consumes the mask directly. Each card caches its own one-bit mask, because the CFR traversal asks
 * for it once per chance-node edge per iteration.
 */
public final class Card {

    static final int DECK_SIZE = 52;

    private final String card;

    /** The integer id (0..51), parsed once at construction — see {@link #strCard2int}. */
    private final int cardInt;

    /** {@code 1L << cardInt}: this card as a one-bit board mask. */
    private final long mask;

    Card(String card) {
        this.card = card;
        this.cardInt = strCard2int(card);
        this.mask = 1L << cardInt;
    }

    public String getCard() {
        return card;
    }

    public int getCardInt() {
        return cardInt;
    }

    /** This card as a single-bit board mask. */
    public long mask() {
        return mask;
    }

    public static int card2int(Card card) {
        return card.cardInt;
    }

    public static int strCard2int(String card) {
        if (card.length() != 2) {
            throw new CardsNotFoundException(String.format("card %s not found", card));
        }
        return (rankToInt(card.charAt(0)) - 2) * 4 + suitToInt(card.charAt(1));
    }

    public static String intCard2Str(int card) {
        int rank = card / 4 + 2;
        int suit = card - (rank - 2) * 4;
        return rankToString(rank) + suitToString(suit);
    }

    public static long boardCards2long(Card[] cards) {
        long mask = 0;
        for (Card card : cards) mask |= card.mask;
        return mask;
    }

    public static boolean boardsHasIntercept(long board1, long board2) {
        return (board1 & board2) != 0;
    }

    public static long boardInts2long(int[] board) {
        if (board.length < 1 || board.length > 7) {
            throw new BoardNotFoundException(Arrays.toString(board));
        }
        long mask = 0;
        for (int card : board) {
            if (card < 0 || card >= DECK_SIZE) {
                throw new CardsNotFoundException(String.format("Card with id %d not found", card));
            }
            mask |= 1L << card;
        }
        return mask;
    }

    /** How many cards a board mask holds — {@code long2board(mask).length} without the array. */
    public static int cardCount(long boardLong) {
        return Long.bitCount(boardLong);
    }

    public static int[] long2board(long boardLong) {
        int size = Long.bitCount(boardLong);
        if (size < 1 || size > 7) {
            throw new BoardNotFoundException(String.format("board length not correct, board length %d", size));
        }
        int[] board = new int[size];
        for (int i = 0; i < size; i++) {
            board[i] = Long.numberOfTrailingZeros(boardLong);
            boardLong &= boardLong - 1;
        }
        return board;
    }

    public static Card[] long2boardCards(long boardLong) throws BoardNotFoundException {
        int[] board = long2board(boardLong);
        Card[] cards = new Card[board.length];
        for (int i = 0; i < board.length; i++) cards[i] = new Card(intCard2Str(board[i]));
        return cards;
    }

    static String suitToString(int suit) {
        return switch (suit) {
            case 1 -> "d";
            case 2 -> "h";
            case 3 -> "s";
            default -> "c";
        };
    }

    static String rankToString(int rank) {
        return switch (rank) {
            case 10 -> "T";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            case 14 -> "A";
            default -> String.valueOf(rank);
        };
    }

    static int rankToInt(char rank) {
        return switch (rank) {
            case 'T' -> 10;
            case 'J' -> 11;
            case 'Q' -> 12;
            case 'K' -> 13;
            case 'A' -> 14;
            case '2', '3', '4', '5', '6', '7', '8', '9' -> rank - '0';
            default -> throw new CardsNotFoundException(String.format("rank %s not found", rank));
        };
    }

    static int suitToInt(char suit) {
        return switch (suit) {
            case 'c' -> 0;
            case 'd' -> 1;
            case 'h' -> 2;
            case 's' -> 3;
            default -> throw new CardsNotFoundException(String.format("suit %s not found", suit));
        };
    }

    public static String[] getSuits() {
        return new String[] {"c", "d", "h", "s"};
    }

    @Override
    public String toString() {
        return card;
    }
}
