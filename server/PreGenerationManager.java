package com.gmail.rawlxxxviii.raws_auto_pre_generation.server;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber
public class PreGenerationManager {
    private static final int TEST_CENTER_X = 0;
    private static final int TEST_CENTER_Z = 0;
    private static final int TEST_RADIUS_BLOCKS = 1500;
    private static final double TEST_MAX_PRIO = 0.50;

    private static final boolean NO_GEN_WHEN_PLAYERS_ONLINE = true;
    private static final boolean OPS_COUNT_TOWARDS_PLAYERS = false;

    public static final Set<ChunkPos> KNOWN_EXISTING = Collections.synchronizedSet(new HashSet<>());
    private static final List<ChunkPos> SORTED_QUEUE = new ArrayList<>();
    private static int queueCursor = 0;
    private static boolean initialized = false;

    private static ChunkPos currentTarget = null;
    private static CompletableFuture<?> currentFuture = null;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        resetState();
        ServerLevel overworld = event.getServer().overworld();
        if (overworld != null) {
            initializeDimensionAsync(overworld);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        resetState();
    }

    private static void resetState() {
        KNOWN_EXISTING.clear();
        SORTED_QUEUE.clear();
        queueCursor = 0;
        initialized = false;
        currentTarget = null;
        currentFuture = null;
        RadarTracker.ACTIVE_CHUNKS.clear();
    }

    private static void initializeDimensionAsync(ServerLevel level) {
        Path regionDir = level.getServer().getWorldPath(LevelResource.ROOT).resolve("region");
        int radiusChunks = (int) Math.ceil(TEST_RADIUS_BLOCKS / 16.0);
        int centerChunkX = TEST_CENTER_X >> 4;
        int centerChunkZ = TEST_CENTER_Z >> 4;

        CompletableFuture.runAsync(() -> {
            int minRegionX = (centerChunkX - radiusChunks) >> 5;
            int maxRegionX = (centerChunkX + radiusChunks) >> 5;
            int minRegionZ = (centerChunkZ - radiusChunks) >> 5;
            int maxRegionZ = (centerChunkZ + radiusChunks) >> 5;

            for (int rx = minRegionX; rx <= maxRegionX; rx++) {
                for (int rz = minRegionZ; rz <= maxRegionZ; rz++) {
                    Path mcaFile = regionDir.resolve("r." + rx + "." + rz + ".mca");
                    if (!Files.exists(mcaFile)) continue;

                    try (RandomAccessFile raf = new RandomAccessFile(mcaFile.toFile(), "r")) {
                        byte[] header = new byte[4096];
                        if (raf.read(header) == 4096) {
                            for (int localZ = 0; localZ < 32; localZ++) {
                                for (int localX = 0; localX < 32; localX++) {
                                    int idx = (localX + localZ * 32) * 4;
                                    if (header[idx] != 0 || header[idx + 1] != 0 || header[idx + 2] != 0) {
                                        KNOWN_EXISTING.add(new ChunkPos((rx << 5) + localX, (rz << 5) + localZ));
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            List<Map.Entry<ChunkPos, Double>> candidates = new ArrayList<>();
            for (int cz = centerChunkZ - radiusChunks; cz <= centerChunkZ + radiusChunks; cz++) {
                int blockZ = (cz << 4) + 8;
                for (int cx = centerChunkX - radiusChunks; cx <= centerChunkX + radiusChunks; cx++) {
                    int blockX = (cx << 4) + 8;
                    double dx = blockX - TEST_CENTER_X;
                    double dz = blockZ - TEST_CENTER_Z;
                    double dist = Math.sqrt(dx * dx + dz * dz);

                    if (dist <= TEST_RADIUS_BLOCKS) {
                        ChunkPos pos = new ChunkPos(cx, cz);
                        if (!KNOWN_EXISTING.contains(pos)) {
                            double prio = TEST_MAX_PRIO * (1.0 - (dist / TEST_RADIUS_BLOCKS));
                            candidates.add(Map.entry(pos, prio));
                        }
                    }
                }
            }

            candidates.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            level.getServer().execute(() -> {
                SORTED_QUEUE.clear();
                for (var entry : candidates) {
                    SORTED_QUEUE.add(entry.getKey());
                }
                queueCursor = 0;
                initialized = true;
            });
        });
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !initialized) return;

        ServerLevel level = event.getServer().overworld();
        if (level == null) return;

        ServerChunkCache chunkCache = level.getChunkSource();

        if (currentTarget != null && currentFuture != null) {
            if (currentFuture.isDone()) {
                chunkCache.removeRegionTicket(TicketType.UNKNOWN, currentTarget, 0, currentTarget);
                KNOWN_EXISTING.add(currentTarget);
                RadarTracker.ACTIVE_CHUNKS.remove(currentTarget);
                currentTarget = null;
                currentFuture = null;
            } else {
                return;
            }
        }

        if (NO_GEN_WHEN_PLAYERS_ONLINE) {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                if (OPS_COUNT_TOWARDS_PLAYERS || !event.getServer().getPlayerList().isOp(player.getGameProfile())) {
                    return;
                }
            }
        }

        while (queueCursor < SORTED_QUEUE.size()) {
            ChunkPos next = SORTED_QUEUE.get(queueCursor++);
            if (KNOWN_EXISTING.contains(next)) {
                continue;
            }
            if (level.hasChunk(next.x, next.z)) {
                KNOWN_EXISTING.add(next);
                continue;
            }

            currentTarget = next;
            RadarTracker.ACTIVE_CHUNKS.add(next);
            chunkCache.addRegionTicket(TicketType.UNKNOWN, next, 0, next);
            currentFuture = chunkCache.getChunkFuture(next.x, next.z, ChunkStatus.FULL, true);
            break;
        }
    }
}
