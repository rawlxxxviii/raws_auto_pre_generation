package com.gmail.rawlxxxviii.raws_chunk_generation.client.screen;

import com.gmail.rawlxxxviii.raws_chunk_generation.client.ClientKeybinds;
import com.gmail.rawlxxxviii.raws_chunk_generation.config.ServerConfig;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.PacketHandler;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.packet.RequestChunkAreaPacket;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.packet.SyncChunkAreaPacket;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class ChunkVisualiserScreen extends Screen {

    // 512x512 chunks = 8.192 x 8.192 blokken, getekend op 1 pixel per chunk (of schaalt mee met je schermhoogte)
    private static final int VIEW_CHUNKS = 512;
    private static final int POLL_INTERVAL_TICKS = 5;

    private int panOffsetX = 0;
    private int panOffsetZ = 0;
    private int tickCounter = 0;

    private @Nullable SyncChunkAreaPacket latestAreaDataPacket = null;
    private @Nullable DynamicTexture mapTexture = null;
    private @Nullable ResourceLocation mapTextureLocation = null;
    private int texWidth = 0;
    private int texHeight = 0;

    public ChunkVisualiserScreen() {
        super(Component.translatable("screen.raws_chunk_generation.visualiser"));
    }

    @Override
    protected void init() {
        super.init();
        requestAreaUpdate();
    }

    @Override
    public void tick() {
        super.tick();
        if (++tickCounter % POLL_INTERVAL_TICKS == 0) {
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

    public void setLatestAreaDataPacket(SyncChunkAreaPacket packet) {
        this.latestAreaDataPacket = packet;

        if (mapTexture == null || texWidth != packet.width || texHeight != packet.height) {
            if (mapTexture != null) {
                mapTexture.close();
            }
            texWidth = packet.width;
            texHeight = packet.height;
            mapTexture = new DynamicTexture(texWidth, texHeight, false);
            mapTextureLocation = Minecraft.getInstance().getTextureManager()
                .register("raws_chunk_map", mapTexture);
        }

        NativeImage pixels = mapTexture.getPixels();
        if (pixels != null) {
            int index = 0;
            for (int dz = 0; dz < packet.height; dz++) {
                for (int dx = 0; dx < packet.width; dx++) {
                    int byteIndex = index >> 2;
                    int state = 0;
                    if (byteIndex < packet.packedStates.length) {
                        state = (packet.packedStates[byteIndex] >> ((index & 3) << 1)) & 3;
                    }

                    int abgrColor = switch (state) {
                        case 1 -> 0xFF555555; // Grijs (generated)
                        case 2 -> 0xFFFF8833; // Blauw (actief aan het genereren)
                        default -> 0xFF000000; // Zwart (onbekend)
                    };
                    pixels.setPixelRGBA(dx, dz, abgrColor);
                    index++;
                }
            }
            mapTexture.upload();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // Past de kaartgrootte automatisch aan zodat hij altijd mooi op elk scherm past (max 512px)
        int mapSizePx = Math.min(VIEW_CHUNKS, Math.min(this.width - 40, this.height - 60));
        float scale = (float) mapSizePx / VIEW_CHUNKS;

        int minX = (this.width - mapSizePx) / 2;
        int minY = (this.height - mapSizePx) / 2 - 8;

        // 1. Kader en zwarte achtergrond
        graphics.fill(minX - 1, minY - 1, minX + mapSizePx + 1, minY + mapSizePx + 1, 0xFF888888);
        graphics.fill(minX, minY, minX + mapSizePx, minY + mapSizePx, 0xFF000000);

        // 2. Teken de kaart in één enkele GPU blit call
        SyncChunkAreaPacket data = this.latestAreaDataPacket;
        if (data != null && mapTextureLocation != null) {
            graphics.blit(mapTextureLocation, minX, minY, mapSizePx, mapSizePx, 0, 0, data.width, data.height, data.width, data.height);

            // 3. Speler-stip (wit) en Online Buffer cirkel (groen)
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null && mc.level.dimension().equals(data.dimension)) {
                ChunkPos pPos = mc.player.chunkPosition();
                float playerRelX = pPos.x - data.startChunkX + 0.5f;
                float playerRelZ = pPos.z - data.startChunkZ + 0.5f;

                int playerPxX = minX + Math.round(playerRelX * scale);
                int playerPxY = minY + Math.round(playerRelZ * scale);

                if (playerPxX >= minX && playerPxX < minX + mapSizePx && playerPxY >= minY && playerPxY < minY + mapSizePx) {
                    graphics.fill(playerPxX - 1, playerPxY - 1, playerPxX + 2, playerPxY + 2, 0xFFFFFFFF);
                }

                int bufferRadiusChunks = ServerConfig.INSTANCE.GENERATION_BUFFER_RADIUS.get();
                int radiusPx = Math.round(bufferRadiusChunks * scale);
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
        } else if (keyCode == GLFW.GLFW_KEY_R) {
            panOffsetX = 0;
            panOffsetZ = 0;
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
            int mapSizePx = Math.min(VIEW_CHUNKS, Math.min(this.width - 40, this.height - 60));
            float scale = (float) mapSizePx / VIEW_CHUNKS;

            panOffsetX -= (int) Math.round(dragX / scale);
            panOffsetZ -= (int) Math.round(dragY / scale);
            requestAreaUpdate();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void onClose() {
        if (mapTexture != null) {
            mapTexture.close();
            mapTexture = null;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
