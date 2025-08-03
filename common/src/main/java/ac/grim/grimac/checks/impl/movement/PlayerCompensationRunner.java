package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.Pair;
import ac.grim.grimac.utils.data.TrackerData;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.math.Vector3dm;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;


public class PlayerCompensationRunner extends Check implements PacketCheck {
    private boolean enabled = false;
    private int maxPredictTicks = 4;
    private int maxPredictSprintTicks = 1;
    private double moveMultiplier = 0.91;
    private boolean enableKnockbackCompensation = true;
    private boolean enableBlinkCompensation = false;
    private boolean increaseTickRate = false;

    final AtomicBoolean processing = new AtomicBoolean(false);

    public PlayerCompensationRunner(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (!enabled) return;

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())
                && !player.packetStateData.lastPacketWasOnePointSeventeenDuplicate
                && player.getSetbackTeleportUtil().hasAcceptedSpawnTeleport) {
            player.compensatedPlayer.doMiniPrediction(maxPredictTicks, maxPredictSprintTicks, moveMultiplier, enableKnockbackCompensation);
        }
    }

    @Override
    public void onPacketSend(final PacketSendEvent event) {
        if (!enabled) return;

        try {
            if (event.getPacketType() == PacketType.Play.Server.ENTITY_MOVEMENT) {
                WrapperPlayServerEntityMovement packet = new WrapperPlayServerEntityMovement(event);
                TrackerData data = player.compensatedEntities.getTrackedEntity(packet.getEntityId());
                if (data == null) return;
                sendPositionUpdate(event, packet.getEntityId(), data.getXRot(), data.getYRot());
            } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
                WrapperPlayServerEntityRelativeMove packet = new WrapperPlayServerEntityRelativeMove(event);
                TrackerData data = player.compensatedEntities.getTrackedEntity(packet.getEntityId());
                if (data == null) return;
                sendPositionUpdate(event, packet.getEntityId(), data.getXRot(), data.getYRot());
            } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
                WrapperPlayServerEntityRelativeMoveAndRotation packet = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
                sendPositionUpdate(event, packet.getEntityId(), packet.getYaw(), packet.getPitch());
            } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_TELEPORT) {
                WrapperPlayServerEntityTeleport packet = new WrapperPlayServerEntityTeleport(event);
                sendPositionUpdate(event, packet.getEntityId(), packet.getYaw(), packet.getPitch());
            } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_POSITION_SYNC) {
                WrapperPlayServerEntityPositionSync packet = new WrapperPlayServerEntityPositionSync(event);
                sendPositionUpdate(event, packet.getId(), packet.getValues().getYaw(), packet.getValues().getPitch());
            } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_ROTATION) {
                WrapperPlayServerEntityRotation packet = new WrapperPlayServerEntityRotation(event);
                sendPositionUpdate(event, packet.getEntityId(), packet.getYaw(), packet.getPitch());
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void tick() {
        if (!enabled) return;
        if (!increaseTickRate) return;

        player.compensatedEntities.entityMap.forEach((id, entity) -> {
            if (entity.type.equals(EntityTypes.PLAYER)) {
                sendPositionUpdate(null, id, null, null);
            }
        });
    }

    public void sendPositionUpdate(@Nullable PacketSendEvent event, int targetEntityId, @Nullable Float yaw, @Nullable Float pitch) {
        if (!enabled) return;
        if (event != null && event.isCancelled()) return;
        if (processing.get()) {
            processing.set(false);
            return;
        }

        PacketEntity entity = player.compensatedEntities.getEntity(targetEntityId);
        if (entity == null) return;
        if (!entity.type.equals(EntityTypes.PLAYER)) return;

        TrackerData lastPos = player.compensatedEntities.getTrackedEntity(targetEntityId);
        if (lastPos == null) return;

        GrimPlayer targetPlayer = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(targetEntityId);
        if (targetPlayer == null) return;

        int targetPing = targetPlayer.getTransactionPing();
        int playerPing = player.getTransactionPing();
        int ticks = Math.min((targetPing + playerPing) / 100, maxPredictTicks);

        Pair<Vector3dm, Boolean> predicted = targetPlayer.compensatedPlayer.getPredictedPosition(ticks);
        if (predicted == null) return;

        // if the player is exempted, we should not cancel the packet
        if (increaseTickRate && event != null) {
            event.setCancelled(true);
            return;
        }

        Vector3dm predictedPos = predicted.first();
        boolean predictedGround = predicted.second();

        Vector3d delta = new Vector3d(predictedPos.getX() - lastPos.getX(), predictedPos.getY() - lastPos.getY(), predictedPos.getZ() - lastPos.getZ());
        Vector3d lastDelta = lastPos.getDelta();
        Vector3d acceleration = new Vector3d(delta.getX() - lastDelta.getX(), delta.getY() - lastDelta.getY(), delta.getZ() - lastDelta.getZ());

        if (ticks > 0) {
            delta.multiply(getVirtualSpringConstant(predictedGround, ticks));
            acceleration.multiply(getVirtualDamperConstant(predictedGround, ticks));
            delta.subtract(acceleration);
        }
        Vector3d result = new Vector3d(lastPos.getX() + delta.getX(), lastPos.getY() + delta.getY(), lastPos.getZ() + delta.getZ());

        boolean near = Math.abs(delta.getX()) < 8 && Math.abs(delta.getY()) < 8 && Math.abs(delta.getZ()) < 8;
        boolean relative = near && predictedGround == lastPos.isOnGround();

        if (event != null) {
            event.setCancelled(true);
        }

        if (yaw == null || pitch == null) {
            yaw = targetPlayer.xRot;
            pitch = targetPlayer.yRot;
        }

        processing.set(true);

        if (!relative) {
            if (!near) {
                result = new Vector3d(targetPlayer.x, targetPlayer.y, targetPlayer.z);
            }
            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_21_2)) {
                player.user.sendPacket(new WrapperPlayServerEntityPositionSync(targetEntityId, new EntityPositionData(result, delta, yaw, pitch), predictedGround));
            } else {
                player.user.sendPacket(new WrapperPlayServerEntityTeleport(targetEntityId, new Vector3d(result.getX(), result.getY(), result.getZ()), yaw, pitch, predictedGround));
            }
        } else if (delta.getX() == 0 && delta.getY() == 0 && delta.getZ() == 0) {
            player.user.sendPacket(new WrapperPlayServerEntityRotation(targetEntityId, yaw, pitch, predictedGround));
        } else if (yaw == lastPos.getXRot() && pitch == lastPos.getYRot()) {
            player.user.sendPacket(new WrapperPlayServerEntityRelativeMove(targetEntityId, delta.getX(), delta.getY(), delta.getZ(), predictedGround));
        } else {
            player.user.sendPacket(new WrapperPlayServerEntityRelativeMoveAndRotation(targetEntityId, delta.getX(), delta.getY(), delta.getZ(), yaw, pitch, predictedGround));
        }
    }

    private Vector3d getVirtualSpringConstant(boolean onGround, int delayTicks) {
        double x = 1.0 + delayTicks * 0.04;
        double y = (onGround ? 1 : 1.05);
        double z = 1.0 + delayTicks * 0.04;

        return new Vector3d(x, y, z);
    }

    private Vector3d getVirtualDamperConstant(boolean onGround, int delayTicks) {
        double x = delayTicks * 0.1;
        double y = onGround ? 0 : delayTicks * 0.05;
        double z = delayTicks * 0.1;

        return new Vector3d(x, y, z);
    }

    @Override
    public void onReload(ConfigManager config) {
        this.enabled = config.getBooleanElse("lag-mitigation.enable", false);
        this.maxPredictTicks = config.getIntElse("lag-mitigation.max-predict-ticks", 4);
        this.maxPredictSprintTicks = config.getIntElse("lag-mitigation.max-predict-sprint-ticks", 1);
        this.moveMultiplier = config.getDoubleElse("lag-mitigation.move-multiplier", 0.91);
        this.enableKnockbackCompensation = config.getBooleanElse("lag-mitigation.enable-knockback-compensation", false);
        this.enableBlinkCompensation = config.getBooleanElse("lag-mitigation.enable-blink-compensation", false);
        this.increaseTickRate = config.getBooleanElse("lag-mitigation.increase-tick-rate", false);
    }
}
