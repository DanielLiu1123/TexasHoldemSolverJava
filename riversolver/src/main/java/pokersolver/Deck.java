package pokersolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by huangxuefeng on 2019/10/6.
 */
public class Deck {
    List<String> ranks;
    List<String> suits;
    List<String> cardsStr = new ArrayList<String>();

    public List<Card> getCards() {
        return cards;
    }

    List<Card> cards = new ArrayList<Card>();

    public Deck(List<String> ranks, List<String> suits) {
        this.ranks = ranks;
        this.suits = suits;
        for (String oneRank : ranks) {
            for (String oneSuit : suits) {
                String oneCard = oneRank + oneSuit;
                cardsStr.add(oneCard);
                cards.add(new Card(oneCard));
            }
        }
    }
}
