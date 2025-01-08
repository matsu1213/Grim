package ac.grim.grimac.utils.nmsutil;

import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import org.bukkit.util.Vector;

import java.util.OptionalInt;

public class JumpPower {
    public static void jumpFromGround(GrimPlayer player, Vector vector) {
        float jumpPower = getJumpPower(player);

        final OptionalInt jumpBoost = player.compensatedEntities.getPotionLevelForPlayer(PotionTypes.JUMP_BOOST);
        if (jumpBoost.isPresent()) {
            jumpPower += 0.1f * (jumpBoost.getAsInt() + 1);
        }

        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_20_5) && jumpPower <= 1.0E-5f) return;

        vector.setY(player.getClientVersion().isOlderThan(ClientVersion.V_1_21_2) ? jumpPower : Math.max(jumpPower, vector.getY()));

        if (player.isSprinting) {
            float radRotation = player.xRot * ((float) Math.PI / 180);
            vector.add(new Vector(-player.trigHandler.sin(radRotation) * 0.2, 0.0, player.trigHandler.cos(radRotation) * 0.2));
        }
    }

    public static float getJumpPower(GrimPlayer player) {
        return (float) player.compensatedEntities.getSelf().getAttributeValue(Attributes.JUMP_STRENGTH) * getPlayerJumpFactor(player);
    }

    public static float getPlayerJumpFactor(GrimPlayer player) {
        return BlockProperties.onHoneyBlock(player, player.mainSupportingBlockData, new Vector3d(player.lastX, player.lastY, player.lastZ)) ? 0.5f : 1f;
    }
}
