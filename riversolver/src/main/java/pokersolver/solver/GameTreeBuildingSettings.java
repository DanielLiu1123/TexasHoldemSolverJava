package pokersolver.solver;

import org.jspecify.annotations.Nullable;
import pokersolver.nodes.GameTreeNode;

public class GameTreeBuildingSettings {
    public static class StreetSetting {
        public float[] betSizes;
        public float[] raiseSizes;
        public float @Nullable [] donkSizes;
        public boolean allin;

        public StreetSetting(float[] betSizes, float[] raiseSizes, float @Nullable [] donkSizes, boolean allin) {
            this.betSizes = betSizes;
            this.raiseSizes = raiseSizes;
            this.donkSizes = donkSizes;
            this.allin = allin;
        }
    }

    public StreetSetting flopIp;
    public StreetSetting turnIp;
    public StreetSetting riverIp;

    public StreetSetting flopOop;
    public StreetSetting turnOop;
    public StreetSetting riverOop;

    public GameTreeBuildingSettings(
            StreetSetting flopIp,
            StreetSetting turnIp,
            StreetSetting riverIp,
            StreetSetting flopOop,
            StreetSetting turnOop,
            StreetSetting riverOop) {
        this.flopIp = flopIp;
        this.turnIp = turnIp;
        this.riverIp = riverIp;
        this.flopOop = flopOop;
        this.turnOop = turnOop;
        this.riverOop = riverOop;
    }

    public StreetSetting getSettings(GameTreeNode.GameRound round, int player) {
        if (!(player == 0 || player == 1)) throw new RuntimeException(String.format("player %s not known", player));
        if (round == GameTreeNode.GameRound.RIVER && player == 0) return this.riverIp;
        else if (round == GameTreeNode.GameRound.TURN && player == 0) return this.turnIp;
        else if (round == GameTreeNode.GameRound.FLOP && player == 0) return this.flopIp;
        else if (round == GameTreeNode.GameRound.RIVER && player == 1) return this.riverOop;
        else if (round == GameTreeNode.GameRound.TURN && player == 1) return this.turnOop;
        else if (round == GameTreeNode.GameRound.FLOP && player == 1) return this.flopOop;
        else throw new RuntimeException(String.format("player %s and round not known", player));
    }
}
