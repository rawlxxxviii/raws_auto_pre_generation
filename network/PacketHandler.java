package com.gmail.rawlxxxviii.raws_chunk_generation.network;

import com.gmail.rawlxxxviii.raws_chunk_generation.RawsChunkGenerationMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {

    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static final SimpleChannel INSTANCE = NetworkRegistry.ChannelBuilder.named(
            new ResourceLocation(RawsChunkGenerationMod.MODID, "main"))
            .serverAcceptedVersions((version) -> true)
            .clientAcceptedVersions((version) -> true)
            .networkProtocolVersion(() -> "1")
            .simpleChannel();

    public static void register() {
        INSTANCE.messageBuilder(SyncChunkRadarPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncChunkRadarPacket::decode)
                .encoder(SyncChunkRadarPacket::encode)
                .consumerMainThread(SyncChunkRadarPacket::handle)
                .add();

        INSTANCE.messageBuilder(StopWatchingRadarPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(StopWatchingRadarPacket::decode)
                .encoder(StopWatchingRadarPacket::encode)
                .consumerMainThread(StopWatchingRadarPacket::handle)
                .add();

        INSTANCE.messageBuilder(OpenRadarPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(OpenRadarPacket::decode)
                .encoder(OpenRadarPacket::encode)
                .consumerMainThread(OpenRadarPacket::handle)
                .add();
    }

    public static <T> void sendToServer(T msg) {
        INSTANCE.send(PacketDistributor.SERVER.noArg(), msg);
    }

    public static <T> void sendToPlayer(T msg, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static <T> void sendToClients(T msg) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), msg);
    }


}
