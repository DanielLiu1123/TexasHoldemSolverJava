package pokersolver.ranges;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import pokersolver.Card;
import pokersolver.exceptions.BoardNotFoundException;

/**
 * Created by huangxuefeng on 2019/10/17.
 * getting and setting private infos
 */
public class PrivateCardsManager {
    PrivateCards[][] privateCards;
    int playerNumber;
    long initialboard;
    int[][] cardPlayerIndex;

    public PrivateCardsManager(PrivateCards[][] privateCards, int playerNumber, long initialboard) {
        this.privateCards = privateCards;
        this.playerNumber = playerNumber;
        this.cardPlayerIndex = new int[52 * 52][];
        for (int i = 0; i < 52 * 52; i++) {
            this.cardPlayerIndex[i] = new int[this.playerNumber];
            Arrays.fill(this.cardPlayerIndex[i], -1);
        }

        // 用一个二维数组记录每个Private Combo的对应index,方便从一方的手牌找对方的同名卡牌的index
        for (int playerId = 0; playerId < playerNumber; playerId++) {
            PrivateCards[] privateCombos = privateCards[playerId];
            for (int i = 0; i < privateCombos.length; i++) {
                PrivateCards onePrivateCombo = privateCombos[i];
                this.cardPlayerIndex[onePrivateCombo.hashCode()][playerId] = i;
            }
        }

        this.initialboard = initialboard;
        try {
            setRelativeProbs();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    public PrivateCards[] getPreflopCards(int player) {
        return this.privateCards[player];
    }

    public @Nullable Integer indPlayer2Player(int fromPlayer, int toPlayer, int index) {
        if (index < 0 || index >= this.getPreflopCards(fromPlayer).length) throw new RuntimeException();
        PrivateCards playerCombo = this.getPreflopCards(fromPlayer)[index];
        int toPlayerIndex = this.cardPlayerIndex[playerCombo.hashCode()][toPlayer];
        if (toPlayerIndex == -1) {
            return null;
        } else {
            return toPlayerIndex;
        }
    }

    public float[] getInitialReachProb(int player, long initialboard) throws BoardNotFoundException {
        int cardsLen = this.privateCards[player].length;
        float[] probs = new float[cardsLen];
        for (int i = 0; i < cardsLen; i++) {
            PrivateCards pc = this.privateCards[player][i];
            if (Card.boardsHasIntercept(initialboard, Card.boardInts2long(new int[] {pc.card1, pc.card2}))) {
                probs[i] = 0;
            } else {
                probs[i] = this.privateCards[player][i].weight;
            }
        }
        return probs;
    }

    public void setRelativeProbs() throws BoardNotFoundException {
        int players = this.privateCards.length;
        for (int playerId = 0; playerId < players; playerId++) {
            int oppo = 1 - playerId;
            float playerProbSum = 0;

            for (int i = 0; i < this.privateCards[playerId].length; i++) {
                float oppoProbSum = 0;
                PrivateCards playerCard = this.privateCards[playerId][i];
                long playerLong = Card.boardInts2long(new int[] {playerCard.card1, playerCard.card2});

                //
                if (Card.boardsHasIntercept(playerLong, initialboard)) {
                    continue;
                }

                for (int j = 0; j < this.privateCards[oppo].length; j++) {
                    PrivateCards oppoCard = this.privateCards[oppo][j];
                    long oppoLong = Card.boardInts2long(new int[] {oppoCard.card1, oppoCard.card2});
                    if (Card.boardsHasIntercept(oppoLong, this.initialboard)
                            || Card.boardsHasIntercept(oppoLong, playerLong)) {
                        continue;
                    }
                    oppoProbSum += oppoCard.weight;
                }
                playerCard.relativeProb = oppoProbSum * playerCard.weight;
                playerProbSum += playerCard.relativeProb;
            }
            for (int i = 0; i < this.privateCards[playerId].length; i++) {
                PrivateCards playerCard = this.privateCards[playerId][i];
                playerCard.relativeProb = playerCard.relativeProb / playerProbSum;
            }
        }
    }
}
