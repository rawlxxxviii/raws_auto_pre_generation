package com.gmail.rawlxxxviii.raws_chunk_generation.client.screen;

import com.gmail.rawlxxxviii.raws_chunk_generation.client.ClientKeybinds;
import com.gmail.rawlxxxviii.raws_chunk_generation.config.ServerConfig;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.PacketHandler;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.packet.RequestChunkAreaPacket;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.packet.SyncChunkAreaPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class ChunkVisualiserScreen extends Screen {

    private static final int PIXELS_PER_CHUNK = 2;
    private static final int VIEW_CHUNKS = 256;

    private int panOffsetX = 0;
    private int panOffsetZ = 0;
    private int tickCounter = 0;

    private @Nullable SyncChunkAreaPacket latestAreaDataPacket = null;

    public ChunkVisualiserScreen() {
        super(Component.translatable("screen.raws_chunk_generation.generation_screen_title"));
    }


    @Override
    protected void init() {
        super.init();
        requestAreaUpdate();
    }

    @Override
    public void tick() {
        super.tick();
        if (++tickCounter % 10 == 0) {
            requestAreaUpdate();
        }
    }

    private void requestAreaUpdate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ResourceKey<Level> dim = mc.level.dimension();
        ChunkPos playerChunk = mc.player.chunkPosition();

        int centerChunkX = playerChunk.x + panOffsetX;
        int centerChunkZ = playerChunk.z + panOffsetZ;
        int halfView = VIEW_CHUNKS / 2;

        PacketHandler.sendToServer(new RequestChunkAreaPacket(
            dim,
            centerChunkX - halfView,
            centerChunkZ - halfView,
            VIEW_CHUNKS,
            VIEW_CHUNKS
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int mapSizePx = VIEW_CHUNKS * PIXELS_PER_CHUNK; // 512 x 512 pixels
        int minX = (this.width - mapSizePx) / 2;
        int minY = (this.height - mapSizePx) / 2 - 10;

        graphics.fill(minX - 1, minY - 1, minX + mapSizePx + 1, minY + mapSizePx + 1, 0xFF888888);
        graphics.fill(minX, minY, minX + mapSizePx, minY + mapSizePx, 0xFF000000);

        SyncChunkAreaPacket data = this.latestAreaDataPacket;
        if (data != null) {
            int index = 0;
            for (int dz = 0; dz < data.height; dz++) {
                int py = minY + (dz * PIXELS_PER_CHUNK);
                for (int dx = 0; dx < data.width; dx++) {
                    int byteIndex = index >> 2;
                    if (byteIndex < data.packedStates.length) {
                        int state = (data.packedStates[byteIndex] >> ((index & 3) << 1)) & 3;
                        if (state != 0) {
                            int color = (state == 1) ? 0xFF555555 : 0xFF3388FF;
                            int px = minX + (dx * PIXELS_PER_CHUNK);
                            graphics.fill(px, py, px + PIXELS_PER_CHUNK, py + PIXELS_PER_CHUNK, color);
                        }
                    }
                    index++;
                }
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null && mc.level.dimension().equals(data.dimension)) {
                ChunkPos pPos = mc.player.chunkPosition();
                int playerRelX = pPos.x - data.startChunkX;
                int playerRelZ = pPos.z - data.startChunkZ;

                int playerPxX = minX + (playerRelX * PIXELS_PER_CHUNK) + (PIXELS_PER_CHUNK / 2);
                int playerPxY = minY + (playerRelZ * PIXELS_PER_CHUNK) + (PIXELS_PER_CHUNK / 2);

                if (playerPxX >= minX && playerPxX < minX + mapSizePx && playerPxY >= minY && playerPxY < minY + mapSizePx) {
                    graphics.fill(playerPxX - 1, playerPxY - 1, playerPxX + 2, playerPxY + 2, 0xFFFFFFFF);
                }

                int bufferRadiusChunks = ServerConfig.INSTANCE.GENERATION_BUFFER_RADIUS.get();
                int radiusPx = bufferRadiusChunks * PIXELS_PER_CHUNK;
                drawCircleOutline(graphics, playerPxX, playerPxY, radiusPx, minX, minY, mapSizePx, 0xFF00FF00);
            }

            graphics.drawCenteredString(this.font, data.debugInfo, this.width / 2, minY + mapSizePx + 8, 0xFFCCCCCC);
        } else {
            graphics.drawCenteredString(this.font, "Loading chunk data...", this.width / 2, minY + (mapSizePx / 2), 0xFFAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawCircleOutline(GuiGraphics graphics, int cx, int cy, int radius, int minX, int minY, int size, int color) {
        if (radius <= 0) return;
        int steps = Math.max(64, radius * 4);
        for (int i = 0; i < steps; i += 2) {
            double angle = (2.0 * Math.PI * i) / steps;
            int px = cx + (int) Math.round(Math.cos(angle) * radius);
            int py = cy + (int) Math.round(Math.sin(angle) * radius);
            if (px >= minX && px < minX + size && py >= minY && py < minY + size) {
                graphics.fill(px, py, px + 1, py + 1, color);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientKeybinds.OPEN_GENERATION_SCREEN_KEY.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }

        int panAmount = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 64 : 16;
        boolean panned = false;

        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) {
            panOffsetZ -= panAmount;
            panned = true;
        } else if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) {
            panOffsetZ += panAmount;
            panned = true;
        } else if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) {
            panOffsetX -= panAmount;
            panned = true;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) {
            panOffsetX += panAmount;
            panned = true;
        }

        if (panned) {
            requestAreaUpdate();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            panOffsetX -= (int) Math.round(dragX / PIXELS_PER_CHUNK);
            panOffsetZ -= (int) Math.round(dragY / PIXELS_PER_CHUNK);
            requestAreaUpdate();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public void setLatestAreaDataPacket(SyncChunkAreaPacket packet) {
        this.latestAreaDataPacket = packet;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
