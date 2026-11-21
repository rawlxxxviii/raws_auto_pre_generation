package com.gmail.rawlxxxviii.raws_chunk_generation.client.network;

import com.gmail.rawlxxxviii.raws_chunk_generation.client.screen.ChunkVisualiserScreen;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.PacketHandler;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.packet.SyncChunkAreaPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public class ClientPacketHandler {

    public static boolean isModPresentOnServer() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && PacketHandler.INSTANCE.isRemotePresent(connection.getConnection());
    }

    public static void handleSyncChunkArea(SyncChunkAreaPacket msg) {
         if (Minecraft.getInstance().screen instanceof ChunkVisualiserScreen chunkVisualiserScreen) {
             chunkVisualiserScreen.setLatestAreaDataPacket(msg);
         }
    }
}
