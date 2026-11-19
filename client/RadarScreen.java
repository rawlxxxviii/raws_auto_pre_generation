package com.gmail.rawlxxxviii.raws_auto_pre_generation.client;

import com.gmail.rawlxxxviii.raws_auto_pre_generation.network.PacketHandler;
import com.gmail.rawlxxxviii.raws_auto_pre_generation.network.StopWatchingRadarPacket;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.lwjgl.glfw.GLFW;

import java.util.BitSet;
import java.util.List;

public class RadarScreen extends Screen {
    public record PriorityZone(int centerX, int centerZ, int radiusBlocks, double maxPrio) {}

    private int regionRadius;
    private BitSet existingChunks;
    private BitSet activeChunks;

    private int panOffsetX = 0;
    private int panOffsetZ = 0;

    private DynamicTexture dynamicTexture;
    private ResourceLocation textureLocation;
    private int currentTextureSize = -1;
    private boolean dirty = true;

    private final List<PriorityZone> priorityZones = List.of(
            new PriorityZone(0, 0, 1500, 0.50)
    );

    private static final int PLAYER_TRAIL_RADIUS_BLOCKS = 1000;

    public RadarScreen(int regionRadius, BitSet existingChunks, BitSet activeChunks) {
        super(Component.literal("Chunk Pre-Generation Radar"));
        this.regionRadius = regionRadius;
        this.existingChunks = existingChunks;
        this.activeChunks = activeChunks;
    }

    public void updateRadarData(int regionRadius, BitSet existingChunks, BitSet activeChunks) {
        this.regionRadius = regionRadius;
        this.existingChunks = existingChunks;
        this.activeChunks = activeChunks;
        this.dirty = true;
    }

    private void rebuildTextureIfNeeded(int viewSize, int gridChunks, int baseChunkOffset, int zeroChunkIndex) {
        if (dynamicTexture == null || currentTextureSize != viewSize) {
            if (dynamicTexture != null) {
                dynamicTexture.close();
            }
            dynamicTexture = new DynamicTexture(viewSize, viewSize, false);
            textureLocation = Minecraft.getInstance().getTextureManager().register("raws_pregen_radar", dynamicTexture);
            currentTextureSize = viewSize;
            dirty = true;
        }

        if (!dirty) return;

        NativeImage pixels = dynamicTexture.getPixels();
        if (pixels == null) return;

        for (int vy = 0; vy < viewSize; vy++) {
            int gz = baseChunkOffset + panOffsetZ + vy;
            int worldChunkZ = gz - zeroChunkIndex;
            int blockCenterZ = (worldChunkZ << 4) + 8;

            for (int vx = 0; vx < viewSize; vx++) {
                int gx = baseChunkOffset + panOffsetX + vx;
                int abgr = 0xFF000000; // Zwart (ABGR formaat voor NativeImage!)

                if (gx >= 0 && gx < gridChunks && gz >= 0 && gz < gridChunks) {
                    int idx = gz * gridChunks + gx;
                    int worldChunkX = gx - zeroChunkIndex;
                    int blockCenterX = (worldChunkX << 4) + 8;

                    if (activeChunks.get(idx)) {
                        abgr = 0xFFFFD800; // Lichtblauw (#00D8FF in ABGR)
                    } else if (existingChunks.get(idx)) {
                        abgr = 0xFF777777; // Grijs (#777777)
                    } else {
                        double prio = getHighestZonePriority(blockCenterX, blockCenterZ);
                        if (prio > 0.0) {
                            double normalizedAlpha = 0.20 + (prio * 0.65);
                            int alpha = Math.max(51, Math.min(230, (int) Math.round(normalizedAlpha * 255.0)));
                            // Oranje #FF8C00 -> R=0xFF, G=0x8C, B=0x00 -> ABGR = (alpha << 24) | 0x008CFF
                            abgr = (alpha << 24) | 0x008CFF;
                        }
                    }
                }
                pixels.setPixelRGBA(vx, vy, abgr);
            }
        }

        dynamicTexture.upload();
        dirty = false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int gridChunks = (regionRadius * 2 + 1) * 32;
        int viewSize = Math.min(gridChunks, Math.min(this.width - 40, this.height - 50));
        int startX = (this.width - viewSize) / 2;
        int startY = (this.height - viewSize) / 2;

        int maxPan = Math.max(0, (gridChunks - viewSize) / 2);
        panOffsetX = Math.max(-maxPan, Math.min(maxPan, panOffsetX));
        panOffsetZ = Math.max(-maxPan, Math.min(maxPan, panOffsetZ));

        int baseChunkOffset = (gridChunks - viewSize) / 2;
        int zeroChunkIndex = regionRadius * 32;

        rebuildTextureIfNeeded(viewSize, gridChunks, baseChunkOffset, zeroChunkIndex);

        graphics.fill(startX - 2, startY - 2, startX + viewSize + 2, startY + viewSize + 2, 0xFF333333);
        if (textureLocation != null) {
            graphics.blit(textureLocation, startX, startY, 0, 0, viewSize, viewSize, viewSize, viewSize);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ChunkPos playerChunk = mc.player.chunkPosition();
            int playerVx = (playerChunk.x + zeroChunkIndex) - (baseChunkOffset + panOffsetX);
            int playerVy = (playerChunk.z + zeroChunkIndex) - (baseChunkOffset + panOffsetZ);

            int trailRadiusChunks = PLAYER_TRAIL_RADIUS_BLOCKS / 16;
            drawCircleOutline(graphics, startX + playerVx, startY + playerVy, trailRadiusChunks, startX, startY, viewSize, 0xAA22C55E);

            if (playerVx >= 0 && playerVx < viewSize && playerVy >= 0 && playerVy < viewSize) {
                graphics.fill(startX + playerVx, startY + playerVy, startX + playerVx + 1, startY + playerVy + 1, 0xFF22C55E);
            }
        }

        int zeroVx = zeroChunkIndex - (baseChunkOffset + panOffsetX);
        int zeroVy = zeroChunkIndex - (baseChunkOffset + panOffsetZ);
        if (zeroVx >= 1 && zeroVx < viewSize - 1 && zeroVy >= 1 && zeroVy < viewSize - 1) {
            graphics.fill(startX + zeroVx - 1, startY + zeroVy, startX + zeroVx + 2, startY + zeroVy + 1, 0xFFFF4444);
            graphics.fill(startX + zeroVx, startY + zeroVy - 1, startX + zeroVx + 1, startY + zeroVy + 2, 0xFFFF4444);
        }

        graphics.drawCenteredString(this.font, this.title, this.width / 2, startY - 14, 0xFFFFFF);

        int legendX = startX + viewSize + 10;
        int legendY = startY + 4;
        if (legendX + 100 < this.width) {
            drawLegendItem(graphics, legendX, legendY, 0xFF777777, "Op schijf");
            drawLegendItem(graphics, legendX, legendY + 14, 0xFF00D8FF, "Actief");
            drawLegendItem(graphics, legendX, legendY + 28, 0x99FF8C00, "Prio zone");
            drawLegendItem(graphics, legendX, legendY + 42, 0xFF22C55E, "Jij");
        }

        if (mouseX >= startX && mouseX < startX + viewSize && mouseY >= startY && mouseY < startY + viewSize) {
            int vx = mouseX - startX;
            int vy = mouseY - startY;
            int chunkX = (baseChunkOffset + panOffsetX + vx) - zeroChunkIndex;
            int chunkZ = (baseChunkOffset + panOffsetZ + vy) - zeroChunkIndex;
            int minBlockX = chunkX << 4;
            int minBlockZ = chunkZ << 4;
            int maxBlockX = minBlockX + 15;
            int maxBlockZ = minBlockZ + 15;

            double hoveredPrio = getHighestZonePriority(minBlockX + 8, minBlockZ + 8);
            String prioText = hoveredPrio > 0 ? String.format("  |  Prio: %.0f%%", hoveredPrio * 100.0) : "";

            String hoverText = String.format("Chunk: [%d, %d]  |  Blokken: [%d, %d] t/m [%d, %d]%s",
                    chunkX, chunkZ, minBlockX, minBlockZ, maxBlockX, maxBlockZ, prioText);
            graphics.drawCenteredString(this.font, hoverText, this.width / 2, startY + viewSize + 8, 0xE2E8F0);
        } else {
            graphics.drawCenteredString(this.font, "Pijltjestoetsen: pannen (Shift = snel)  |  R: reset", this.width / 2, startY + viewSize + 8, 0x94A3B8);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private double getHighestZonePriority(int blockX, int blockZ) {
        double best = 0.0;
        for (PriorityZone zone : priorityZones) {
            if (zone.radiusBlocks <= 0 || zone.maxPrio <= 0.0) continue;
            double dx = blockX - zone.centerX;
            double dz = blockZ - zone.centerZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist <= zone.radiusBlocks) {
                double factor = 1.0 - (dist / zone.radiusBlocks);
                double prio = zone.maxPrio * factor;
                if (prio > best) {
                    best = prio;
                }
            }
        }
        return best;
    }

    private void drawLegendItem(GuiGraphics graphics, int x, int y, int color, String label) {
        graphics.fill(x, y, x + 8, y + 8, color);
        graphics.drawString(this.font, label, x + 12, y, 0xCCCCCC);
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
        if (ClientKeybinds.OPEN_RADAR_KEY.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }

        int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 128 : 32;
        if (keyCode == GLFW.GLFW_KEY_UP) {
            panOffsetZ -= step;
            dirty = true;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
            panOffsetZ += step;
            dirty = true;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
            panOffsetX -= step;
            dirty = true;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            panOffsetX += step;
            dirty = true;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_R) {
            panOffsetX = 0;
            panOffsetZ = 0;
            dirty = true;
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (dynamicTexture != null) {
            dynamicTexture.close();
            dynamicTexture = null;
        }
        PacketHandler.sendToServer(new StopWatchingRadarPacket());
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
