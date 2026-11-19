package com.gmail.rawlxxxviii.raws_auto_pre_generation.server;

import com.gmail.rawlxxxviii.raws_auto_pre_generation.config.DimensionPreGenConfig;
import com.gmail.rawlxxxviii.raws_auto_pre_generation.config.ServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber
public class PreGenerationManager {
    private static final Map<ResourceLocation, DimensionPreGenConfig> DIM_CONFIGS = new HashMap<>();
    private static final Map<ResourceLocation, Set<Long>> KNOWN_EXISTING = new HashMap<>();
    private static final Map<ResourceLocation, List<ChunkTask>> SPIRAL_QUEUES = new HashMap<>();
    private static final Map<ResourceLocation, Integer> TOTAL_ZONE_TARGETS = new HashMap<>();

    // Player trails: dimensie -> (ChunkPos.asLong -> timestamp van aanmaak)
    private static final Map<ResourceLocation, Map<Long, Long>> PLAYER_TRAILS = new HashMap<>();

    // Recent gegenereerde chunks (1 sec zichtbaar als blauw spoor op de radar)
    private static final Map<ChunkPos, Long> RECENT_BLUE_TRAIL = Collections.synchronizedMap(new HashMap<>());

    // Debug / Prestatiestatistieken
    private static final Deque<Double> RECENT_DURATIONS_MS = new ArrayDeque<>();
    private static final Deque<Long> RECENT_COMPLETION_TIMESTAMPS = new ArrayDeque<>();
    private static long currentStartTimeNs = 0L;
    private static String currentStatus = "Initialiseren...";

    private static ResourceLocation activeDimension = null;
    private static ChunkPos currentTarget = null;
    private static CompletableFuture<?> currentFuture = null;
    private static boolean initialized = false;
    private static int trailSampleTicks = 0;

    private record ChunkTask(ChunkPos pos, double prio, double angle) {}

    public record DebugStats(
            String status,
            double avgChunkTimeMs,
            double chunksPerSec,
            int completedInZones,
            int totalInZones
    ) {}

    public static DimensionPreGenConfig getDimensionConfig(ResourceLocation dimId) {
        return DIM_CONFIGS.get(dimId);
    }

    public static Set<Long> getKnownExisting(ResourceLocation dimId) {
        return KNOWN_EXISTING.computeIfAbsent(dimId, k -> Collections.synchronizedSet(new HashSet<>()));
    }

    public static Set<ChunkPos> getVisibleActiveChunks() {
        long now = System.currentTimeMillis();
        Set<ChunkPos> result = new HashSet<>();
        if (currentTarget != null) {
            result.add(currentTarget);
        }
        synchronized (RECENT_BLUE_TRAIL) {
            RECENT_BLUE_TRAIL.entrySet().removeIf(e -> (now - e.getValue()) > 1000L);
            result.addAll(RECENT_BLUE_TRAIL.keySet());
        }
        return result;
    }

    public static DebugStats getDebugStats(ResourceLocation dimId) {
        double avgMs = 0.0;
        synchronized (RECENT_DURATIONS_MS) {
            if (!RECENT_DURATIONS_MS.isEmpty()) {
                double sum = 0.0;
                for (double d : RECENT_DURATIONS_MS) sum += d;
                avgMs = sum / RECENT_DURATIONS_MS.size();
            }
        }

        double cps = 0.0;
        long now = System.currentTimeMillis();
        synchronized (RECENT_COMPLETION_TIMESTAMPS) {
            RECENT_COMPLETION_TIMESTAMPS.removeIf(t -> (now - t) > 5000L);
            if (!RECENT_COMPLETION_TIMESTAMPS.isEmpty()) {
                cps = RECENT_COMPLETION_TIMESTAMPS.size() / 5.0;
            }
        }

        int total = TOTAL_ZONE_TARGETS.getOrDefault(dimId, 0);
        int remaining = SPIRAL_QUEUES.containsKey(dimId) ? SPIRAL_QUEUES.get(dimId).size() : 0;
        int completed = Math.max(0, total - remaining);

        return new DebugStats(currentStatus, avgMs, cps, completed, total);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        DIM_CONFIGS.clear();
        KNOWN_EXISTING.clear();
        SPIRAL_QUEUES.clear();
        TOTAL_ZONE_TARGETS.clear();
        PLAYER_TRAILS.clear();
        RECENT_BLUE_TRAIL.clear();
        RECENT_DURATIONS_MS.clear();
        RECENT_COMPLETION_TIMESTAMPS.clear();
        currentTarget = null;
        currentFuture = null;
        initialized = false;
        currentStatus = "Scannen...";

        for (ServerLevel level : server.getAllLevels()) {
            ResourceLocation dimId = level.dimension().location();
            DimensionPreGenConfig cfg = DimensionPreGenConfig.loadOrCreate(server, dimId);
            DIM_CONFIGS.put(dimId, cfg);
        }

        CompletableFuture.runAsync(() -> {
            for (ServerLevel level : server.getAllLevels()) {
                ResourceLocation dimId = level.dimension().location();
                DimensionPreGenConfig cfg = DIM_CONFIGS.get(dimId);
                if (cfg == null || !cfg.enabled || cfg.zones.isEmpty()) continue;

                Set<Long> existingSet = getKnownExisting(dimId);
                scanExistingChunksForZones(server, level, cfg, existingSet);
                rebuildSpiralQueue(dimId, cfg, existingSet);
            }
            initialized = true;
            currentStatus = "Gereed";
        });
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DIM_CONFIGS.clear();
        KNOWN_EXISTING.clear();
        SPIRAL_QUEUES.clear();
        TOTAL_ZONE_TARGETS.clear();
        PLAYER_TRAILS.clear();
        RECENT_BLUE_TRAIL.clear();
        currentTarget = null;
        currentFuture = null;
        initialized = false;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !initialized) return;
        MinecraftServer server = event.getServer();

        // 1. Verzamel elke 20 ticks (1 sec) speler-posities voor de player trails
        trailSampleTicks++;
        if (trailSampleTicks >= 20) {
            trailSampleTicks = 0;
            long now = System.currentTimeMillis();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ResourceLocation dimId = player.serverLevel().dimension().location();
                PLAYER_TRAILS.computeIfAbsent(dimId, k -> new HashMap<>())
                        .put(player.chunkPosition().toLong(), now);
            }
            cleanupExpiredTrails(now);
        }

        // 2. Rond lopende chunk af zodra Minecraft klaar is
        if (currentTarget != null && currentFuture != null) {
            if (currentFuture.isDone()) {
                long now = System.currentTimeMillis();
                double durationMs = (System.nanoTime() - currentStartTimeNs) / 1_000_000.0;
                synchronized (RECENT_DURATIONS_MS) {
                    RECENT_DURATIONS_MS.addLast(durationMs);
                    if (RECENT_DURATIONS_MS.size() > 50) RECENT_DURATIONS_MS.removeFirst();
                }
                synchronized (RECENT_COMPLETION_TIMESTAMPS) {
                    RECENT_COMPLETION_TIMESTAMPS.addLast(now);
                }

                ServerLevel level = getLevelById(server, activeDimension);
                if (level != null) {
                    level.getChunkSource().removeRegionTicket(TicketType.UNKNOWN, currentTarget, 0, currentTarget);
                    getKnownExisting(activeDimension).add(currentTarget.toLong());
                }
                RECENT_BLUE_TRAIL.put(currentTarget, now);
                currentTarget = null;
                currentFuture = null;
                activeDimension = null;
            } else {
                currentStatus = "Actief";
                return;
            }
        }

        // 3. Check of we mogen genereren op basis van online spelers
        if (!canGenerateNow(server)) {
            currentStatus = "Gepauzeerd (speler online)";
            return;
        }

        // 4. Kies eerst een hoge prio chunk uit een actief player trail, anders uit de spiraal-wachtrij
        if (tryStartNextTrailChunk(server)) {
            currentStatus = "Actief (Player Trail)";
            return;
        }
        if (tryStartNextZoneChunk(server)) {
            currentStatus = "Actief (Wereld Zone)";
            return;
        }

        currentStatus = "Klaar (wachtrij leeg)";
    }

    private static boolean canGenerateNow(MinecraftServer server) {
        if (!ServerConfig.INSTANCE.noGenerationWhenPlayersOnline.get()) {
            return true;
        }
        boolean opsCount = ServerConfig.INSTANCE.opsCountTowardsPlayersOnline.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (opsCount || !server.getPlayerList().isOp(player.getGameProfile())) {
                return false;
            }
        }
        return true;
    }

    private static void cleanupExpiredTrails(long now) {
        long maxAgeMs = ServerConfig.INSTANCE.playerTrailingDecayTimeMinutes.get() * 60_000L;
        for (Map<Long, Long> dimTrails : PLAYER_TRAILS.values()) {
            dimTrails.entrySet().removeIf(entry -> (now - entry.getValue()) > maxAgeMs);
        }
    }

    private static boolean tryStartNextTrailChunk(MinecraftServer server) {
        int trailRadiusBlocks = ServerConfig.INSTANCE.playerTrailingRadiusBlocks.get();
        double maxTrailPrio = ServerConfig.INSTANCE.playerTrailingMaxPrio.get();
        if (trailRadiusBlocks <= 0 || maxTrailPrio <= 0.0) return false;

        long now = System.currentTimeMillis();
        long maxAgeMs = ServerConfig.INSTANCE.playerTrailingDecayTimeMinutes.get() * 60_000L;
        int radiusChunks = (trailRadiusBlocks >> 4) + 1;

        ChunkPos bestPos = null;
        ResourceLocation bestDim = null;
        double bestScore = 0.0;

        for (ServerLevel level : server.getAllLevels()) {
            ResourceLocation dimId = level.dimension().location();
            Map<Long, Long> trails = PLAYER_TRAILS.get(dimId);
            if (trails == null || trails.isEmpty()) continue;

            Set<Long> existing = getKnownExisting(dimId);
            for (Map.Entry<Long, Long> entry : trails.entrySet()) {
                double ageFactor = 1.0 - Math.min(1.0, (double) (now - entry.getValue()) / maxAgeMs);
                if (ageFactor <= 0.0) continue;

                ChunkPos origin = new ChunkPos(entry.getKey());
                for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                    for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                        int cx = origin.x + dx;
                        int cz = origin.z + dz;
                        long packed = ChunkPos.asLong(cx, cz);
                        if (existing.contains(packed)) continue;

                        double distBlocks = Math.sqrt((dx * 16.0) * (dx * 16.0) + (dz * 16.0) * (dz * 16.0));
                        if (distBlocks > trailRadiusBlocks) continue;

                        double distFactor = 1.0 - (distBlocks / trailRadiusBlocks);
                        double score = maxTrailPrio * distFactor * ageFactor;
                        if (score > bestScore) {
                            if (level.hasChunk(cx, cz)) {
                                existing.add(packed);
                                continue;
                            }
                            bestScore = score;
                            bestPos = new ChunkPos(cx, cz);
                            bestDim = dimId;
                        }
                    }
                }
            }
        }

        if (bestPos != null && bestDim != null) {
            startChunkGeneration(getLevelById(server, bestDim), bestPos);
            return true;
        }
        return false;
    }

    private static boolean tryStartNextZoneChunk(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            ResourceLocation dimId = level.dimension().location();
            List<ChunkTask> queue = SPIRAL_QUEUES.get(dimId);
            if (queue == null || queue.isEmpty()) continue;

            Set<Long> existing = getKnownExisting(dimId);
            while (!queue.isEmpty()) {
                ChunkTask next = queue.remove(0);
                long packed = next.pos.toLong();
                if (existing.contains(packed)) continue;
                if (level.hasChunk(next.pos.x, next.pos.z)) {
                    existing.add(packed);
                    continue;
                }
                startChunkGeneration(level, next.pos);
                return true;
            }
        }
        return false;
    }

    private static void startChunkGeneration(ServerLevel level, ChunkPos pos) {
        if (level == null) return;
        activeDimension = level.dimension().location();
        currentTarget = pos;
        currentStartTimeNs = System.nanoTime();

        ServerChunkCache chunkCache = level.getChunkSource();
        chunkCache.addRegionTicket(TicketType.UNKNOWN, pos, 0, pos);
        currentFuture = chunkCache.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true);
    }

    private static ServerLevel getLevelById(MinecraftServer server, ResourceLocation dimId) {
        if (dimId == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(dimId)) return level;
        }
        return null;
    }

    private static void scanExistingChunksForZones(MinecraftServer server, ServerLevel level, DimensionPreGenConfig cfg, Set<Long> existingSet) {
        Path regionDir = DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT)).resolve("region");
        if (!Files.exists(regionDir)) return;

        int minRx = Integer.MAX_VALUE, maxRx = Integer.MIN_VALUE;
        int minRz = Integer.MAX_VALUE, maxRz = Integer.MIN_VALUE;

        for (DimensionPreGenConfig.PriorityZone z : cfg.zones) {
            minRx = Math.min(minRx, (z.centerX() - z.radiusBlocks()) >> 9);
            maxRx = Math.max(maxRx, (z.centerX() + z.radiusBlocks()) >> 9);
            minRz = Math.min(minRz, (z.centerZ() - z.radiusBlocks()) >> 9);
            maxRz = Math.max(maxRz, (z.centerZ() + z.radiusBlocks()) >> 9);
        }

        for (int rx = minRx; rx <= maxRx; rx++) {
            for (int rz = minRz; rz <= maxRz; rz++) {
                Path mca = regionDir.resolve("r." + rx + "." + rz + ".mca");
                if (!Files.exists(mca)) continue;
                try (RandomAccessFile raf = new RandomAccessFile(mca.toFile(), "r")) {
                    byte[] header = new byte[4096];
                    if (raf.read(header) == 4096) {
                        for (int lz = 0; lz < 32; lz++) {
                            for (int lx = 0; lx < 32; lx++) {
                                int idx = (lx + lz * 32) * 4;
                                if (header[idx] != 0 || header[idx + 1] != 0 || header[idx + 2] != 0) {
                                    existingSet.add(ChunkPos.asLong((rx << 5) + lx, (rz << 5) + lz));
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private static void rebuildSpiralQueue(ResourceLocation dimId, DimensionPreGenConfig cfg, Set<Long> existingSet) {
        Map<Long, ChunkTask> uniqueTasks = new HashMap<>();
        Set<Long> allZoneChunks = new HashSet<>();

        for (DimensionPreGenConfig.PriorityZone zone : cfg.zones) {
            if (zone.radiusBlocks() <= 0 || zone.maxPrio() <= 0.0) continue;
            int minCx = (zone.centerX() - zone.radiusBlocks()) >> 4;
            int maxCx = (zone.centerX() + zone.radiusBlocks()) >> 4;
            int minCz = (zone.centerZ() - zone.radiusBlocks()) >> 4;
            int maxCz = (zone.centerZ() + zone.radiusBlocks()) >> 4;

            for (int cz = minCz; cz <= maxCz; cz++) {
                int blockZ = (cz << 4) + 8;
                for (int cx = minCx; cx <= maxCx; cx++) {
                    int blockX = (cx << 4) + 8;
                    double dx = blockX - zone.centerX();
                    double dz = blockZ - zone.centerZ();
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist <= zone.radiusBlocks()) {
                        long packed = ChunkPos.asLong(cx, cz);
                        allZoneChunks.add(packed);
                        if (existingSet.contains(packed)) continue;

                        double prio = zone.maxPrio() * (1.0 - (dist / zone.radiusBlocks()));
                        double angle = Math.atan2(dz, dx);

                        ChunkTask prev = uniqueTasks.get(packed);
                        if (prev == null || prio > prev.prio) {
                            uniqueTasks.put(packed, new ChunkTask(new ChunkPos(cx, cz), prio, angle));
                        }
                    }
                }
            }
        }

        TOTAL_ZONE_TARGETS.put(dimId, allZoneChunks.size());

        List<ChunkTask> sorted = new ArrayList<>(uniqueTasks.values());
        sorted.sort((a, b) -> {
            int bandA = (int) Math.round(a.prio * 100.0);
            int bandB = (int) Math.round(b.prio * 100.0);
            if (bandA != bandB) {
                return Integer.compare(bandB, bandA);
            }
            return Double.compare(a.angle, b.angle);
        });

        SPIRAL_QUEUES.put(dimId, sorted);
    }
}
