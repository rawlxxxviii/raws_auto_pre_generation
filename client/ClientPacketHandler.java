package com.gmail.rawlxxxviii.raws_chunk_generation.client;

import com.gmail.rawlxxxviii.raws_chunk_generation.network.SyncChunkRadarPacket;
import net.minecraft.client.Minecraft;

public class ClientPacketHandler {
    public static void handleRadarSync(SyncChunkRadarPacket msg) {
        if (Minecraft.getInstance().screen instanceof RadarScreen screen) {
            screen.updateRadarData(
                msg.getRegionRadius(),
                msg.getExistingChunks(),
                msg.getActiveChunks(),
                msg.getZones(),
                msg.getPlayerTrailRadiusBlocks(),
                msg.getStatus(),
                msg.getAvgChunkTimeMs(),
                msg.getChunksPerSec(),
                msg.getCompletedInZones(),
                msg.getTotalInZones()
            );
        } else {
            Minecraft.getInstance().setScreen(new RadarScreen(
                msg.getRegionRadius(),
                msg.getExistingChunks(),
                msg.getActiveChunks(),
                msg.getZones(),
                msg.getPlayerTrailRadiusBlocks(),
                msg.getStatus(),
                msg.getAvgChunkTimeMs(),
                msg.getChunksPerSec(),
                msg.getCompletedInZones(),
                msg.getTotalInZones()
            ));
        }
    }
}
