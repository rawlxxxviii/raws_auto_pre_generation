package com.gmail.rawlxxxviii.raws_auto_pre_generation.network;

import com.gmail.rawlxxxviii.raws_auto_pre_generation.server.RadarTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class StopWatchingRadarPacket {

    public StopWatchingRadarPacket() {
    }

    public static void encode(StopWatchingRadarPacket msg, FriendlyByteBuf buf) {
    }

    public static StopWatchingRadarPacket decode(FriendlyByteBuf buf) {
        return new StopWatchingRadarPacket();
    }

    public static void handle(StopWatchingRadarPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                RadarTracker.stopWatching(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
