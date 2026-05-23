package icybee.solver.trainable;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Created by huangxuefeng on 2019/10/12.
 * include code for a abstract class trainable,which describes a class that can be trained by cfr
 */
public abstract class Trainable {
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    public abstract float[] getAverageStrategy();
    public abstract float[] getcurrentStrategy();
    public abstract void updateRegrets(float[] regrets,int iteration_number,float[] reach_probs);
    public abstract ObjectNode dumps(boolean with_state);
}
