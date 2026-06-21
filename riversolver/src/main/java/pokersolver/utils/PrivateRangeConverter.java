package pokersolver.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import pokersolver.Card;
import pokersolver.ranges.PrivateCards;

public class PrivateRangeConverter {
    public static PrivateCards[] rangeStr2Cards(String rangeStr, int[] initialBoards) {
        String[] rangeList = rangeStr.split(",", -1);
        List<PrivateCards> privateCards = new ArrayList<PrivateCards>();

        for (String oneRange : rangeList) {
            PrivateCards thisCard;
            List<String> cardstrArr = Arrays.asList(oneRange.split(":"));
            if (cardstrArr.size() > 2 || cardstrArr.isEmpty()) {
                throw new RuntimeException("':' number exceeded 2");
            }
            float weight = 1;

            oneRange = cardstrArr.get(0);
            if (cardstrArr.size() == 2) {
                weight = Float.parseFloat(cardstrArr.get(1));
            }
            if (weight == 0) continue;

            int rangeLen = oneRange.length();
            if (rangeLen == 3) {
                if (oneRange.charAt(2) == 's') {
                    char rank1 = oneRange.charAt(0);
                    char rank2 = oneRange.charAt(1);
                    if (rank1 == rank2)
                        throw new RuntimeException(String.format("%s%ss is not a valid card desc", rank1, rank2));
                    for (String oneSuit : Card.getSuits()) {
                        int card1 = Card.strCard2int(rank1 + oneSuit);
                        int card2 = Card.strCard2int(rank2 + oneSuit);
                        thisCard = new PrivateCards(card1, card2, weight);
                        privateCards.add(thisCard);
                    }

                } else if (oneRange.charAt(2) == 'o') {
                    char rank1 = oneRange.charAt(0);
                    char rank2 = oneRange.charAt(1);

                    String[] suits = Card.getSuits();
                    for (int i = 0; i < suits.length; i++) {
                        String oneSuit = suits[i];
                        int beginIndex = rank1 == rank2 ? i : 0;
                        for (int j = beginIndex; j < suits.length; j++) {
                            String anotherSuit = suits[j];
                            if (Objects.equals(oneSuit, anotherSuit)) {
                                continue;
                            }
                            int card1 = Card.strCard2int(rank1 + oneSuit);
                            int card2 = Card.strCard2int(rank2 + anotherSuit);
                            if (Card.boardsHasIntercept(
                                    Card.boardInts2long(new int[] {card1, card2}),
                                    Card.boardInts2long(initialBoards))) {
                                continue;
                            }
                            thisCard = new PrivateCards(card1, card2, weight);
                            privateCards.add(thisCard);
                        }
                    }
                } else {
                    throw new RuntimeException("format not recognize");
                }
            } else if (rangeLen == 2) {
                char rank1 = oneRange.charAt(0);
                char rank2 = oneRange.charAt(1);
                String[] suits = Card.getSuits();
                for (int i = 0; i < suits.length; i++) {
                    String oneSuit = suits[i];
                    int beginIndex = rank1 == rank2 ? i : 0;
                    for (int j = beginIndex; j < suits.length; j++) {
                        String anotherSuit = suits[j];
                        if (Objects.equals(oneSuit, anotherSuit) && rank1 == rank2) {
                            continue;
                        }
                        int card1 = Card.strCard2int(rank1 + oneSuit);
                        int card2 = Card.strCard2int(rank2 + anotherSuit);
                        if (Card.boardsHasIntercept(
                                Card.boardInts2long(new int[] {card1, card2}), Card.boardInts2long(initialBoards))) {
                            continue;
                        }
                        thisCard = new PrivateCards(card1, card2, weight);
                        privateCards.add(thisCard);
                    }
                }

            } else throw new RuntimeException(String.format(" range str %s len not valid ", oneRange));
        }

        // 排除初试range中重复的情况
        for (int i = 0; i < privateCards.size(); i++) {
            for (int j = i + 1; j < privateCards.size(); j++) {
                PrivateCards oneCards = privateCards.get(i);
                PrivateCards anotherCards = privateCards.get(j);
                if (oneCards.card1 == anotherCards.card1 && oneCards.card2 == anotherCards.card2) {
                    throw new RuntimeException(String.format(
                            "card %s %s duplicate",
                            Card.intCard2Str(oneCards.card1), Card.intCard2Str(oneCards.card2)));
                }
                if (oneCards.card1 == anotherCards.card2 && oneCards.card2 == anotherCards.card1) {
                    throw new RuntimeException(String.format(
                            "card %s %s duplicate",
                            Card.intCard2Str(oneCards.card1), Card.intCard2Str(oneCards.card2)));
                }
            }
        }

        PrivateCards[] privateCardsList = new PrivateCards[privateCards.size()];
        for (int i = 0; i < privateCards.size(); i++) {
            privateCardsList[i] = privateCards.get(i);
            // System.out.print(String.format("[%s-%s]",Card.intCard2Str(private_cards_list[i].card1),Card.intCard2Str(private_cards_list[i].card2)));
        }
        /*
            output all private combos

        System.out.println("private range number:");
        System.out.println(private_cards.size());
        System.out.println();
         */
        return privateCardsList;
    }
}
