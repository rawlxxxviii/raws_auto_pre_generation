package com.gmail.rawlxxxviii.raws_auto_pre_generation.server;

import com.gmail.rawlxxxviii.raws_auto_pre_generation.config.DimensionPreGenConfig;
import com.gmail.rawlxxxviii.raws_auto_pre_generation.config.ServerConfig;
import com.gmail.rawlxxxviii.raws_auto_pre_generation.network.PacketHandler;
import com.gmail.rawlxxxviii.raws_auto_pre_generation.network.SyncChunkRadarPacket;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber
public class RadarTracker {
    private static final Set<UUID> VIEWING_PLAYERS = new HashSet<>();
    private static final int REGION_RADIUS = 10; // 21x21 regions = 672x672 chunks (~10.750x10.750 blokken)
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("pregenradar")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    startWatching(player);
                    return 1;
                }));
    }

    public static void startWatching(ServerPlayer player) {
        VIEWING_PLAYERS.add(player.getUUID());
        sendRadarUpdateAsync(player);
    }

    public static void stopWatching(ServerPlayer player) {
        VIEWING_PLAYERS.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        VIEWING_PLAYERS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || VIEWING_PLAYERS.isEmpty()) return;

        tickCounter++;
        if (tickCounter >= 10) {
            tickCounter = 0;
            var server = event.getServer();
            for (UUID uuid : new ArrayList<>(VIEWING_PLAYERS)) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    sendRadarUpdateAsync(player);
                } else {
                    VIEWING_PLAYERS.remove(uuid);
                }
            }
        }
    }

    private static void sendRadarUpdateAsync(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ResourceLocation dimId = level.dimension().location();
        Path regionDir = DimensionType.getStorageFolder(level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT)).resolve("region");

        Set<ChunkPos> activeSnapshot = PreGenerationManager.getVisibleActiveChunks();
        Set<Long> knownSnapshot = new HashSet<>(PreGenerationManager.getKnownExisting(dimId));
        DimensionPreGenConfig dimCfg = PreGenerationManager.getDimensionConfig(dimId);
        List<DimensionPreGenConfig.PriorityZone> zonesSnapshot = dimCfg != null ? List.copyOf(dimCfg.zones) : List.of();
        int trailRadius = ServerConfig.INSTANCE.playerTrailingRadiusBlocks.get();
        PreGenerationManager.DebugStats stats = PreGenerationManager.getDebugStats(dimId);

        CompletableFuture.runAsync(() -> {
            int gridRegions = REGION_RADIUS * 2 + 1;
            int gridChunks = gridRegions * 32;
            BitSet existing = new BitSet(gridChunks * gridChunks);
            BitSet active = new BitSet(gridChunks * gridChunks);

            for (int rx = -REGION_RADIUS; rx <= REGION_RADIUS; rx++) {
                for (int rz = -REGION_RADIUS; rz <= REGION_RADIUS; rz++) {
                    Path mcaFile = regionDir.resolve("r." + rx + "." + rz + ".mca");
                    if (!Files.exists(mcaFile)) continue;

                    try (RandomAccessFile raf = new RandomAccessFile(mcaFile.toFile(), "r")) {
                        byte[] header = new byte[4096];
                        if (raf.read(header) == 4096) {
                            for (int localZ = 0; localZ < 32; localZ++) {
                                for (int localX = 0; localX < 32; localX++) {
                                    int headerIdx = (localX + localZ * 32) * 4;
                                    boolean exists = (header[headerIdx] != 0 || header[headerIdx + 1] != 0 || header[headerIdx + 2] != 0);
                                    if (exists) {
                                        int globalX = (rx + REGION_RADIUS) * 32 + localX;
                                        int globalZ = (rz + REGION_RADIUS) * 32 + localZ;
                                        existing.set(globalZ * gridChunks + globalX);
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            for (Long packed : knownSnapshot) {
                int cx = ChunkPos.getX(packed);
                int cz = ChunkPos.getZ(packed);
                int globalX = cx + (REGION_RADIUS * 32);
                int globalZ = cz + (REGION_RADIUS * 32);
                if (globalX >= 0 && globalX < gridChunks && globalZ >= 0 && globalZ < gridChunks) {
                    existing.set(globalZ * gridChunks + globalX);
                }
            }

            for (ChunkPos pos : activeSnapshot) {
                int globalX = pos.x + (REGION_RADIUS * 32);
                int globalZ = pos.z + (REGION_RADIUS * 32);
                if (globalX >= 0 && globalX < gridChunks && globalZ >= 0 && globalZ < gridChunks) {
                    active.set(globalZ * gridChunks + globalX);
                }
            }

            level.getServer().execute(() -> {
                if (VIEWING_PLAYERS.contains(player.getUUID())) {
                    PacketHandler.sendToPlayer(
                            new SyncChunkRadarPacket(
                                    REGION_RADIUS, existing, active, zonesSnapshot, trailRadius,
                                    stats.status(), stats.avgChunkTimeMs(), stats.chunksPerSec(),
                                    stats.completedInZones(), stats.totalInZones()
                            ),
                            player
                    );
                }
            });
        });
    }
}
