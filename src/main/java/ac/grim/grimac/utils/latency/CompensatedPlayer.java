package ac.grim.grimac.utils.latency;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.KnownInput;
import ac.grim.grimac.utils.data.LastInstance;
import ac.grim.grimac.utils.data.Pair;
import ac.grim.grimac.utils.nmsutil.Collisions;
import ac.grim.grimac.utils.nmsutil.GetBoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// This class is used to predict and compensate positions for latency.
public class CompensatedPlayer {
    GrimPlayer player;
    private final ArrayList<Pair<Vector, Boolean>> predictedPositions = new ArrayList<>();

    public CompensatedPlayer(GrimPlayer player) {
        this.player = player;
    }

    public void doMiniPrediction(int maxTicksAhead, int maxTicksSprintAhead, boolean velocityCompensate) {
        predictedPositions.clear();

        Vector p = new Vector(player.x, player.y, player.z);
        Vector v = new Vector(player.x - player.lastX, player.y - player.lastY, player.z - player.lastZ);
        boolean onGround = player.onGround;
        boolean lastOnGround = player.lastOnGround;
        boolean sprinting = player.isSprinting;
        KnownInput input = player.packetStateData.knownInput;

        for (int i = 0; i < maxTicksAhead + 1; i++) {
            predictedPositions.add(new Pair<>(p.clone(), onGround));

            // end of tick
            if (lastOnGround) {
                if (v.getX() != 0) {
                    //v.setX(v.getX() * 0.6 * 0.91);
                }
                if (v.getY() != 0) {
                    v.setY((v.getY() - 0.08) * 0.98);
                }
                if (v.getZ() != 0) {
                    //v.setZ(v.getZ() * 0.6 * 0.91);
                }
            } else {
                if(v.getX() != 0) {
                    //v.setX(v.getX() * 0.91);
                }
                if (v.getY() != 0) {
                    v.setY((v.getY() - 0.08) * 0.98);
                }
                if (v.getZ() != 0) {
                    //v.setZ(v.getZ() * 0.91);
                }
            }

            // collision check
            List<SimpleCollisionBox> collisions = new ArrayList<>();
            SimpleCollisionBox box = GetBoundingBox.getBoundingBoxFromPosAndSizeRaw(p.getX(), p.getY(), p.getZ(), 0.6f, 1.8f);
            Collisions.getCollisionBoxes(player, box.copy().expandToCoordinate(v.getX(), v.getY(), v.getZ()), collisions, false);
            Vector vec = Collisions.collideBoundingBoxLegacy(new Vector(v.getX(), v.getY(), v.getZ()), box.copy(), collisions, Arrays.asList(Collisions.Axis.Y, Collisions.Axis.X, Collisions.Axis.Z));

            // apply velocity
            p.add(vec);

            if (v.getX() != vec.getX()) {
                v.setX(0);
            }
            if (v.getY() <= 0) {
                if (v.getY() != vec.getY()) {
                    lastOnGround = onGround;
                    onGround = true;
                    v.setY(0);
                }
            } else {
                lastOnGround = onGround;
                onGround = false;
            }
            if (v.getZ() != vec.getZ()) {
                v.setZ(0);
            }
        }
    }

    public Pair<Vector, Boolean> getPredictedPosition(int ticksAhead) {
        if (ticksAhead >= predictedPositions.size()) {
            return null;
        }
        return predictedPositions.get(ticksAhead);
    }
}
