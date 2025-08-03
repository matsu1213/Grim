package ac.grim.grimac.utils.data;

import ac.grim.grimac.utils.math.Vector3dm;

public class CompensationVelocityData {
    public int id;
    public Vector3dm vector;
    public int delayTicks;

    public CompensationVelocityData(int id, Vector3dm vector, int delayTicks) {
        this.id = id;
        this.vector = vector;
        this.delayTicks = delayTicks;
    }
}
