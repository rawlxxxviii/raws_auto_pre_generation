package com.gmail.rawlxxxviii.raws_auto_pre_generation.network;

import com.gmail.rawlxxxviii.raws_auto_pre_generation.server.RadarTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenRadarPacket {

    public OpenRadarPacket() {
    }

    public static void encode(OpenRadarPacket msg, FriendlyByteBuf buf) {
    }

    public static OpenRadarPacket decode(FriendlyByteBuf buf) {
        return new OpenRadarPacket();
    }

    public static void handle(OpenRadarPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                RadarTracker.startWatching(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
