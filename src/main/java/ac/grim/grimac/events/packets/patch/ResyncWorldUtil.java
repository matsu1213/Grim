package ac.grim.grimac.events.packets.patch;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.math.GrimMath;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAcknowledgeBlockChanges;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;

public class ResyncWorldUtil {
    static HashMap<BlockData, Integer> blockDataToId = new HashMap<>();

    public static void resyncPosition(GrimPlayer player, Vector3i pos) {
        player.getResyncHandler().resync(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static void resyncPositions(GrimPlayer player, SimpleCollisionBox box) {
        player.getResyncHandler().resync(GrimMath.floor(box.minX), GrimMath.floor(box.minY), GrimMath.floor(box.minZ),
                GrimMath.ceil(box.maxX), GrimMath.ceil(box.maxY), GrimMath.ceil(box.maxZ));
    }

    public static void resyncPositions(GrimPlayer player, int minBlockX, int mY, int minBlockZ, int maxBlockX, int mxY, int maxBlockZ) {
        // Check the 4 corners of the player world for loaded chunks before calling event
        if (!player.compensatedWorld.isChunkLoaded(minBlockX >> 4, minBlockZ >> 4) || !player.compensatedWorld.isChunkLoaded(minBlockX >> 4, maxBlockZ >> 4)
                || !player.compensatedWorld.isChunkLoaded(maxBlockX >> 4, minBlockZ >> 4) || !player.compensatedWorld.isChunkLoaded(maxBlockX >> 4, maxBlockZ >> 4))
            return;

        if (player.bukkitPlayer == null) return;
        World world = player.bukkitPlayer.getWorld();

        // Takes 0.15ms or so to complete. Not bad IMO. Unsure how I could improve this other than sending packets async.
        // But that's on PacketEvents.
        FoliaScheduler.getRegionScheduler().execute(GrimAPI.INSTANCE.getPlugin(), world,
                minBlockX >> 4, minBlockZ >> 4, () -> {
            boolean flat = PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13);
            // Player hasn't spawned, don't spam packets
            if (!player.getSetbackTeleportUtil().hasAcceptedSpawnTeleport) return;

            // Check the 4 corners of the BB for loaded chunks, don't freeze main thread to load chunks.
            if (!world.isChunkLoaded(minBlockX >> 4, minBlockZ >> 4) || !world.isChunkLoaded(minBlockX >> 4, maxBlockZ >> 4)
                    || !world.isChunkLoaded(maxBlockX >> 4, minBlockZ >> 4) || !world.isChunkLoaded(maxBlockX >> 4, maxBlockZ >> 4))
                return;

            // This is based on Tuinity's code, thanks leaf. Now merged into paper.
            // I have no idea how I could possibly get this more efficient...
            final int minSection = player.compensatedWorld.getMinHeight() >> 4;
            final int minBlock = minSection << 4;
            final int maxBlock = player.compensatedWorld.getMaxHeight() - 1;

            int minBlockY = Math.max(minBlock, mY);
            int maxBlockY = Math.min(maxBlock, mxY);

            int minChunkX = minBlockX >> 4;
            int maxChunkX = maxBlockX >> 4;

            int minChunkY = minBlockY >> 4;
            int maxChunkY = maxBlockY >> 4;

            int minChunkZ = minBlockZ >> 4;
            int maxChunkZ = maxBlockZ >> 4;

            for (int currChunkZ = minChunkZ; currChunkZ <= maxChunkZ; ++currChunkZ) {
                int minZ = currChunkZ == minChunkZ ? minBlockZ & 15 : 0; // coordinate in chunk
                int maxZ = currChunkZ == maxChunkZ ? maxBlockZ & 15 : 15; // coordinate in chunk

                for (int currChunkX = minChunkX; currChunkX <= maxChunkX; ++currChunkX) {
                    int minX = currChunkX == minChunkX ? minBlockX & 15 : 0; // coordinate in chunk
                    int maxX = currChunkX == maxChunkX ? maxBlockX & 15 : 15; // coordinate in chunk

                    Chunk chunk = world.getChunkAt(currChunkX, currChunkZ);

                    for (int currChunkY = minChunkY; currChunkY <= maxChunkY; ++currChunkY) {
                        int minY = currChunkY == minChunkY ? minBlockY & 15 : 0; // coordinate in chunk
                        int maxY = currChunkY == maxChunkY ? maxBlockY & 15 : 15; // coordinate in chunk

                        int totalBlocks = (maxX - minX + 1) * (maxZ - minZ + 1) * (maxY - minY + 1);
                        WrapperPlayServerMultiBlockChange.EncodedBlock[] encodedBlocks = new WrapperPlayServerMultiBlockChange.EncodedBlock[totalBlocks];

                        int blockIndex = 0;
                        // Alright, we are now in a chunk section
                        // This can be used to construct and send a multi block change
                        for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                            for (int currX = minX; currX <= maxX; ++currX) {
                                for (int currY = minY; currY <= maxY; ++currY) {
                                    Block block = chunk.getBlock(currX, currY | (currChunkY << 4), currZ);

                                    int blockId;

                                    if (flat) {
                                        // Cache this because strings are expensive
                                        blockId = blockDataToId.computeIfAbsent(block.getBlockData(), data -> WrappedBlockState.getByString(PacketEvents.getAPI().getServerManager().getVersion().toClientVersion(), data.getAsString(false)).getGlobalId());
                                    } else {
                                        blockId = (block.getType().getId() << 4) | block.getData();
                                    }

                                    encodedBlocks[blockIndex++] = new WrapperPlayServerMultiBlockChange.EncodedBlock(blockId, currX, currY | (currChunkY << 4), currZ);
                                }
                            }
                        }

                        WrapperPlayServerMultiBlockChange packet = new WrapperPlayServerMultiBlockChange(new Vector3i(currChunkX, currChunkY, currChunkZ), true, encodedBlocks);
                        ChannelHelper.runInEventLoop(player.user.getChannel(), () -> player.user.sendPacket(packet));
                    }
                }
            }
        });
    }

    public static void resyncPosition(GrimPlayer player, Vector3i pos, int sequence) {
        if (player.bukkitPlayer == null) return;

        final int chunkX = pos.x >> 4;
        final int chunkZ = pos.z >> 4;
        final World world = player.bukkitPlayer.getWorld();

        FoliaScheduler.getRegionScheduler().execute(GrimAPI.INSTANCE.getPlugin(), world, chunkX, chunkZ, () -> {
            if (!player.bukkitPlayer.isOnline() || !player.getSetbackTeleportUtil().hasAcceptedSpawnTeleport) return;

            if (!player.compensatedWorld.isChunkLoaded(chunkX, chunkZ)) return;
            if (player.bukkitPlayer.getLocation().distance(new Location(world, pos.x, pos.y, pos.z)) >= 64) return;
            if (!world.isChunkLoaded(chunkX, chunkZ)) return; // Don't load chunks sync

            final Block block = world.getChunkAt(chunkX, chunkZ).getBlock(pos.x & 15, pos.y, pos.z & 15);

            final int blockId;
            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)) {
                // Cache this because strings are expensive
                blockId = blockDataToId.computeIfAbsent(block.getBlockData(), data -> WrappedBlockState.getByString(PacketEvents.getAPI().getServerManager().getVersion().toClientVersion(), data.getAsString(false)).getGlobalId());
            } else {
                blockId = (block.getType().getId() << 4) | block.getData();
            }

            player.user.sendPacket(new WrapperPlayServerBlockChange(pos, blockId));
            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_19)) { // Via will handle this for us pre-1.19
                player.user.sendPacket(new WrapperPlayServerAcknowledgeBlockChanges(sequence)); // Make 1.19 clients apply the changes
            }
        });
    }
}
