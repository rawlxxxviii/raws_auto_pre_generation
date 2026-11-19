package com.gmail.rawlxxxviii.raws_auto_pre_generation.network;

import com.gmail.rawlxxxviii.raws_auto_pre_generation.client.RadarScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.BitSet;
import java.util.function.Supplier;

public class SyncChunkRadarPacket {
    private final int regionRadius;
    private final BitSet existingChunks;
    private final BitSet activeChunks;

    public SyncChunkRadarPacket(int regionRadius, BitSet existingChunks, BitSet activeChunks) {
        this.regionRadius = regionRadius;
        this.existingChunks = existingChunks;
        this.activeChunks = activeChunks;
    }

    public static void encode(SyncChunkRadarPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.regionRadius);
        buf.writeBitSet(msg.existingChunks);
        buf.writeBitSet(msg.activeChunks);
    }

    public static SyncChunkRadarPacket decode(FriendlyByteBuf buf) {
        int radius = buf.readInt();
        BitSet existing = buf.readBitSet();
        BitSet active = buf.readBitSet();
        return new SyncChunkRadarPacket(radius, existing, active);
    }

    public static void handle(SyncChunkRadarPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (Minecraft.getInstance().screen instanceof RadarScreen screen) {
                    screen.updateRadarData(msg.regionRadius, msg.existingChunks, msg.activeChunks);
                } else {
                    Minecraft.getInstance().setScreen(new RadarScreen(msg.regionRadius, msg.existingChunks, msg.activeChunks));
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
