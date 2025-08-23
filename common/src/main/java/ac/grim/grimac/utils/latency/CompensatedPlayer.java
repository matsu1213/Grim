package ac.grim.grimac.utils.latency;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.CompensationVelocityData;
import ac.grim.grimac.utils.data.KnownInput;
import ac.grim.grimac.utils.data.Pair;
import ac.grim.grimac.utils.math.Vector3dm;
import ac.grim.grimac.utils.nmsutil.Collisions;
import ac.grim.grimac.utils.nmsutil.GetBoundingBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

// This class is used to predict and compensate positions for latency.
public class CompensatedPlayer {
    private boolean compensateKnockback = false;

    GrimPlayer player;
    private final ArrayList<Pair<Vector3dm, Boolean>> predictedPositions = new ArrayList<>();

    public boolean async = false;
    private List<CompensationVelocityData> pendingKnockback = new ArrayList<>();
    private Vector3dm syncPos = new Vector3dm(0, 0, 0);
    private Vector3dm asyncPos = new Vector3dm(0, 0, 0);
    private Vector3dm asyncVelocity = new Vector3dm(0, 0, 0);
    private boolean asyncGround = false;

    public CompensatedPlayer(GrimPlayer player) {
        this.player = player;
    }

    public void doMiniPrediction(int maxTicksAhead, int maxTicksSprintAhead, double moveMultiplier, boolean compensateKnockback) {
        predictedPositions.clear();
        this.compensateKnockback = compensateKnockback;

        //if (async && pendingKnockback.isEmpty()) {
        //    //player.sendMessage("resync");
        //    async = false;
        //}

        Vector3dm p = async ? asyncPos : new Vector3dm(player.x, player.y, player.z);
        Vector3dm v = async ? asyncVelocity : new Vector3dm(player.x - player.lastX, player.y - player.lastY, player.z - player.lastZ);
        boolean onGround = async ? asyncGround : player.onGround;
        boolean lastOnGround = player.lastOnGround;
        boolean sprinting = player.isSprinting;
        KnownInput input = player.packetStateData.knownInput;

        for (int i = 0; i < maxTicksAhead + 1; i++) {
            predictedPositions.add(new Pair<>(new Vector3dm(p.getX(), p.getY(), p.getZ()), onGround));

            // we will use predicted position in the next tick for async prediction
            if (i == 1) {
                asyncPos = new Vector3dm(p.getX(), p.getY(), p.getZ());
                asyncVelocity = new Vector3dm(v.getX(), v.getY(), v.getZ());
                asyncGround = onGround;
            }

            if (async) {
                Iterator<CompensationVelocityData> iterator = pendingKnockback.iterator();
                while (iterator.hasNext()) {
                    CompensationVelocityData data = iterator.next();
                    if (data.delayTicks == i + 1) {
                        v.add(data.vector);
                        data.delayTicks--;
                        if (data.delayTicks == 0) {
                            iterator.remove();
                            if (pendingKnockback.isEmpty()) {
                                async = false;
                                player.sendMessage("resync");
                            }
                        }
                    }
                }
            }

            // end of tick
            if (lastOnGround) {
                if (v.getX() != 0) {
                    v.setX(v.getX() * 0.6 * moveMultiplier);
                }
                if (v.getY() != 0) {
                    v.setY((v.getY() - 0.08) * 0.98);
                }
                if (v.getZ() != 0) {
                    v.setZ(v.getZ() * 0.6 * moveMultiplier);
                }
            } else {
                if(v.getX() != 0) {
                    v.setX(v.getX() * moveMultiplier);
                }
                if (v.getY() != 0) {
                    v.setY((v.getY() - 0.08) * 0.98);
                }
                if (v.getZ() != 0) {
                    v.setZ(v.getZ() * moveMultiplier);
                }
            }

            // collision check
            List<SimpleCollisionBox> collisions = new ArrayList<>();
            SimpleCollisionBox box = GetBoundingBox.getBoundingBoxFromPosAndSizeRaw(p.getX(), p.getY(), p.getZ(), 0.6f, 1.8f);
            Collisions.getCollisionBoxes(player, box.copy().expandToCoordinate(v.getX(), v.getY(), v.getZ()), collisions, false);
            Vector3dm vec = Collisions.collideBoundingBoxLegacy(new Vector3dm(v.getX(), v.getY(), v.getZ()), box.copy(), collisions, Arrays.asList(Collisions.Axis.Y, Collisions.Axis.X, Collisions.Axis.Z));

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

    public Pair<Vector3dm, Boolean> getPredictedPosition(int ticksAhead) {
        if (ticksAhead >= predictedPositions.size()) {
            return null;
        }
        return predictedPositions.get(ticksAhead);
    }

    public void addPendingKnockback(int transaction, Vector3dm kb) {
        if (!compensateKnockback) return;

        pendingKnockback.add(new CompensationVelocityData(transaction, kb, player.getTransactionPing() / 100));
        if (!async) {
            player.sendMessage("async");
        }
        async = true;
    }

    public boolean isPendingKnockback() {
        return !pendingKnockback.isEmpty();
    }
}
