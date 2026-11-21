package com.gmail.rawlxxxviii.raws_chunk_generation.client.screen;

import com.gmail.rawlxxxviii.raws_chunk_generation.client.ClientKeybinds;
import com.gmail.rawlxxxviii.raws_chunk_generation.client.render.ChunkMapRenderer;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.PacketHandler;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.packet.RequestChunkAreaPacket;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.packet.SyncChunkAreaPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import org.lwjgl.glfw.GLFW;

public class ChunkVisualiserScreen extends Screen {

    private static final int VIEW_CHUNKS = 512;
    private static final int POLL_INTERVAL_TICKS = 5;

    private int panOffsetX = 0;
    private int panOffsetZ = 0;
    private int tickCounter = 0;

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
        if (++tickCounter % POLL_INTERVAL_TICKS == 0) {
            requestAreaUpdate();
        }
    }

    private void requestAreaUpdate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ChunkPos playerChunk = mc.player.chunkPosition();
        int halfView = VIEW_CHUNKS / 2;

        PacketHandler.sendToServer(new RequestChunkAreaPacket(
            mc.level.dimension(),
            playerChunk.x + panOffsetX - halfView,
            playerChunk.z + panOffsetZ - halfView,
            VIEW_CHUNKS,
            VIEW_CHUNKS
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int mapSizePx = Math.min(VIEW_CHUNKS, Math.min(this.width - 40, this.height - 60));
        int minX = (this.width - mapSizePx) / 2;
        int minY = (this.height - mapSizePx) / 2 - 8;

        ChunkMapRenderer.renderMap(graphics, minX, minY, mapSizePx);

        SyncChunkAreaPacket data = ChunkMapRenderer.getLatestPacket();
        if (data != null) {
            graphics.drawCenteredString(this.font, data.debugInfo, this.width / 2, minY + mapSizePx + 8, 0xFFCCCCCC);
        } else {
            graphics.drawCenteredString(this.font, "Loading chunk data...", this.width / 2, minY + (mapSizePx / 2), 0xFFAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientKeybinds.OPEN_GENERATION_SCREEN_KEY.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }

        int panAmount = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 64 : 16;
        boolean panned = false;

        if (keyCode == GLFW.GLFW_KEY_UP) {
            panOffsetZ -= panAmount;
            panned = true;
        } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
            panOffsetZ += panAmount;
            panned = true;
        } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
            panOffsetX -= panAmount;
            panned = true;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
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
    public boolean isPauseScreen() {
        return false;
    }
}
