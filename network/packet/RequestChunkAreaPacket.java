package com.gmail.rawlxxxviii.raws_chunk_generation.network.packet;

import com.gmail.rawlxxxviii.raws_chunk_generation.config.ServerConfig;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.PacketHandler;
import com.gmail.rawlxxxviii.raws_chunk_generation.util.ChunkStateTracker;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestChunkAreaPacket {

    private final ResourceKey<Level> dimension;
    private final int startChunkX;
    private final int startChunkZ;
    private final int width;
    private final int height;

    public RequestChunkAreaPacket(ResourceKey<Level> dimension, int startChunkX, int startChunkZ, int width, int height) {
        this.dimension = dimension;
        this.startChunkX = startChunkX;
        this.startChunkZ = startChunkZ;
        this.width = width;
        this.height = height;
    }

    public static void encode(RequestChunkAreaPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceKey(msg.dimension);
        buf.writeInt(msg.startChunkX);
        buf.writeInt(msg.startChunkZ);
        buf.writeInt(msg.width);
        buf.writeInt(msg.height);
    }

    public static RequestChunkAreaPacket decode(FriendlyByteBuf buf) {
        return new RequestChunkAreaPacket(
            buf.readResourceKey(Registries.DIMENSION),
            buf.readInt(),
            buf.readInt(),
            buf.readInt(),
            buf.readInt()
        );
    }

    public static void handle(RequestChunkAreaPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            // 1. Server-side rechten-check (veiligheid tegen aangepaste clients)
            if (ServerConfig.INSTANCE.LIMIT_INFO_SCREEN_TO_OPERATORS.get() && !sender.hasPermissions(2)) {
                return;
            }

            // 2. Veiligheids-cap op grootte (voorkomt dat een client per ongeluk 10.000x10.000 opvraagt)
            int safeWidth = Math.min(Math.max(msg.width, 1), 512);
            int safeHeight = Math.min(Math.max(msg.height, 1), 512);

            ServerLevel targetLevel = sender.getServer().getLevel(msg.dimension);
            if (targetLevel == null) return;

            // 3. Haal de ingepakte 2-bit data op uit onze tracker
            byte[] packedStates = ChunkStateTracker.getAreaStates(
                targetLevel, msg.startChunkX, msg.startChunkZ, safeWidth, safeHeight
            );

            // 4. Bouw alvast een korte debug-status voor onderin het scherm
            int generatedCount = ChunkStateTracker.getGeneratedCount(msg.dimension);
            String debugInfo = String.format(
                "Dim: %s | Generated chunks: %d",
                msg.dimension.location(), generatedCount
            );

            // 5. Stuur het antwoord terug naar deze specifieke speler
            PacketHandler.sendToPlayer(
                new SyncChunkAreaPacket(msg.dimension, msg.startChunkX, msg.startChunkZ, safeWidth, safeHeight, packedStates, debugInfo),
                sender
            );
        });
        ctx.setPacketHandled(true);
    }
}
