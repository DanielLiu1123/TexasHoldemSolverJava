package pokersolver;

import pokersolver.exceptions.BoardNotFoundException;
import pokersolver.exceptions.CardsNotFoundException;
import pokersolver.ranges.PrivateCards;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by huangxuefeng on 2019/10/6.
 * created to hold or convert a card to int
 */
public class Card {
    public String getCard() {
        return card;
    }

    String card;

    /** The integer id (0..51), parsed once at construction — see {@link #strCard2int}. */
    final int cardInt;

    Card(String card) {
        this.card = card;
        this.cardInt = strCard2int(card);
    }

    public int getCardInt() {
        return this.cardInt;
    }

    public static int card2int(Card card) {
        return card.cardInt;
    }

    public static int strCard2int(String card) {
        char rank = card.charAt(0);
        char suit = card.charAt(1);
        if (card.length() != 2) {
            throw new CardsNotFoundException(String.format("card %s not found", card));
        }
        return (rankToInt(rank) - 2) * 4 + suitToInt(suit);
    }

    public static String intCard2Str(int card) {
        int rank = card / 4 + 2;
        int suit = card - (rank - 2) * 4;
        return rankToString(rank) + suitToString(suit);
    }

    public static long boardCards2long(String[] cards) {
        Card[] cards_objs = new Card[cards.length];
        for (int i = 0; i < cards.length; i++) {
            cards_objs[i] = new Card(cards[i]);
        }
        return boardCards2long(cards_objs);
    }

    public static long boardCards2long(List<String> cards) {
        Card[] cards_objs = new Card[cards.size()];
        for (int i = 0; i < cards.size(); i++) {
            cards_objs[i] = new Card(cards.get(i));
        }
        return boardCards2long(cards_objs);
    }

    public static long boardCard2long(Card card) {
        try {
            return boardCards2long(new Card[] {card});
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    public static long boardCards2long(Card[] cards) {
        int[] board_int = new int[cards.length];
        for (int i = 0; i < cards.length; i++) {
            board_int[i] = Card.card2int(cards[i]);
        }
        return boardInts2long(board_int);
    }

    public static boolean boardsHasIntercept(long board1, long board2) {
        return ((board1 & board2) != 0);
    }

    public static long boardInts2long(List<Integer> board) {
        int[] array = board.stream().mapToInt(i -> i).toArray();
        return boardInts2long(array);
    }

    public static long privateHand2long(PrivateCards one_hand) {
        return boardInts2long(new int[] {one_hand.card1, one_hand.card2});
    }

    public static long boardInts2long(int[] board) {
        if (board.length < 1 || board.length > 7) {
            throw new RuntimeException(Arrays.toString(board));
        }
        long board_long = 0;
        for (int one_card : board) {
            // 这里hard code了一副扑克牌是52张
            if (one_card < 0 || one_card >= 52) {
                throw new RuntimeException(String.format("Card with id %d not found", one_card));
            }
            // long d
            // long 的range 在- 2 ^ 63 - 1 ~ + 2^ 63之间,所以不用太担心溢出问题
            board_long += (Long.valueOf(1) << one_card);
        }
        return board_long;
    }

    public static int[] long2board(long board_long) {
        List<Integer> board = new ArrayList<>();
        for (int i = 0; i < 52; i++) {
            if ((board_long & 1) == 1) {
                board.add(i);
            }
            board_long = board_long >> 1;
        }
        if (board.size() < 1 || board.size() > 7) {
            throw new RuntimeException(String.format(
                    "board length not correct, board length %d, boards %s", board.size(), board.toString()));
        }
        int[] retval = new int[board.size()];
        for (int i = 0; i < board.size(); i++) {
            retval[i] = board.get(i);
        }
        return retval;
    }

    public static Card[] long2boardCards(long board_long) throws BoardNotFoundException {
        int[] board = long2board(board_long);
        List<Card> board_cards = new ArrayList<>();
        for (int one_board : board) {
            board_cards.add(new Card(intCard2Str(one_board)));
        }
        if (board_cards.size() < 1 || board_cards.size() > 7) {
            throw new BoardNotFoundException(String.format(
                    "board length not correct, board length %d, boards %s",
                    board_cards.size(), Arrays.toString(board)));
        }
        Card retval[] = new Card[board_cards.size()];
        for (int i = 0; i < board_cards.size(); i++) {
            retval[i] = board_cards.get(i);
        }
        return retval;
    }

    static String suitToString(int suit) {
        return switch (suit) {
            case 0 -> "c";
            case 1 -> "d";
            case 2 -> "h";
            case 3 -> "s";
            default -> "c";
        };
    }

    static String rankToString(int rank) {
        return switch (rank) {
            case 2 -> "2";
            case 3 -> "3";
            case 4 -> "4";
            case 5 -> "5";
            case 6 -> "6";
            case 7 -> "7";
            case 8 -> "8";
            case 9 -> "9";
            case 10 -> "T";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            case 14 -> "A";
            default -> "2";
        };
    }

    static int rankToInt(char rank) {
        return switch (rank) {
            case '2' -> 2;
            case '3' -> 3;
            case '4' -> 4;
            case '5' -> 5;
            case '6' -> 6;
            case '7' -> 7;
            case '8' -> 8;
            case '9' -> 9;
            case 'T' -> 10;
            case 'J' -> 11;
            case 'Q' -> 12;
            case 'K' -> 13;
            case 'A' -> 14;
            default -> 2;
        };
    }

    static int suitToInt(char suit) {
        return switch (suit) {
            case 'c' -> 0; // 梅花
            case 'd' -> 1; // 方块
            case 'h' -> 2; // 红桃
            case 's' -> 3; // 黑桃
            default -> 0;
        };
    }

    public static String[] getSuits() {
        return new String[] {"c", "d", "h", "s"};
    }

    @Override
    public String toString() {
        return this.card;
    }
}
