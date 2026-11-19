package com.gmail.rawlxxxviii.raws_auto_pre_generation.network;

import com.gmail.rawlxxxviii.raws_auto_pre_generation.client.ClientPacketHandler;
import com.gmail.rawlxxxviii.raws_auto_pre_generation.config.DimensionPreGenConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Supplier;

public class SyncChunkRadarPacket {
    private final int regionRadius;
    private final BitSet existingChunks;
    private final BitSet activeChunks;
    private final List<DimensionPreGenConfig.PriorityZone> zones;
    private final int playerTrailRadiusBlocks;

    private final String status;
    private final double avgChunkTimeMs;
    private final double chunksPerSec;
    private final int completedInZones;
    private final int totalInZones;

    public SyncChunkRadarPacket(int regionRadius, BitSet existingChunks, BitSet activeChunks,
                                List<DimensionPreGenConfig.PriorityZone> zones, int playerTrailRadiusBlocks,
                                String status, double avgChunkTimeMs, double chunksPerSec,
                                int completedInZones, int totalInZones) {
        this.regionRadius = regionRadius;
        this.existingChunks = existingChunks;
        this.activeChunks = activeChunks;
        this.zones = zones;
        this.playerTrailRadiusBlocks = playerTrailRadiusBlocks;
        this.status = status;
        this.avgChunkTimeMs = avgChunkTimeMs;
        this.chunksPerSec = chunksPerSec;
        this.completedInZones = completedInZones;
        this.totalInZones = totalInZones;
    }

    public static void encode(SyncChunkRadarPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.regionRadius);
        buf.writeBitSet(msg.existingChunks);
        buf.writeBitSet(msg.activeChunks);
        buf.writeInt(msg.zones.size());
        for (DimensionPreGenConfig.PriorityZone zone : msg.zones) {
            buf.writeInt(zone.centerX());
            buf.writeInt(zone.centerZ());
            buf.writeInt(zone.radiusBlocks());
            buf.writeDouble(zone.maxPrio());
        }
        buf.writeInt(msg.playerTrailRadiusBlocks);
        buf.writeUtf(msg.status);
        buf.writeDouble(msg.avgChunkTimeMs);
        buf.writeDouble(msg.chunksPerSec);
        buf.writeInt(msg.completedInZones);
        buf.writeInt(msg.totalInZones);
    }

    public static SyncChunkRadarPacket decode(FriendlyByteBuf buf) {
        int radius = buf.readInt();
        BitSet existing = buf.readBitSet();
        BitSet active = buf.readBitSet();
        int zoneCount = buf.readInt();
        List<DimensionPreGenConfig.PriorityZone> zones = new ArrayList<>(zoneCount);
        for (int i = 0; i < zoneCount; i++) {
            int cx = buf.readInt();
            int cz = buf.readInt();
            int r = buf.readInt();
            double prio = buf.readDouble();
            zones.add(new DimensionPreGenConfig.PriorityZone(cx, cz, r, prio));
        }
        int trailRadius = buf.readInt();
        String status = buf.readUtf();
        double avgMs = buf.readDouble();
        double cps = buf.readDouble();
        int completed = buf.readInt();
        int total = buf.readInt();
        return new SyncChunkRadarPacket(radius, existing, active, zones, trailRadius, status, avgMs, cps, completed, total);
    }

    public static void handle(SyncChunkRadarPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleRadarSync(msg));
        });
        ctx.get().setPacketHandled(true);
    }

    public int getRegionRadius() { return regionRadius; }
    public BitSet getExistingChunks() { return existingChunks; }
    public BitSet getActiveChunks() { return activeChunks; }
    public List<DimensionPreGenConfig.PriorityZone> getZones() { return zones; }
    public int getPlayerTrailRadiusBlocks() { return playerTrailRadiusBlocks; }
    public String getStatus() { return status; }
    public double getAvgChunkTimeMs() { return avgChunkTimeMs; }
    public double getChunksPerSec() { return chunksPerSec; }
    public int getCompletedInZones() { return completedInZones; }
    public int getTotalInZones() { return totalInZones; }
}
