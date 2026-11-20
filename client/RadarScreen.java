package com.gmail.rawlxxxviii.raws_chunk_generation.client;

import com.gmail.rawlxxxviii.raws_chunk_generation.network.PacketHandler;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.StopWatchingRadarPacket;
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
    private BitSet existingChunks;
    private BitSet activeChunks;
    private int playerTrailRadiusBlocks;

    private String status;
    private double avgChunkTimeMs;
    private double chunksPerSec;
    private int completedInZones;
    private int totalInZones;

    private int panOffsetX = 0;
    private int panOffsetZ = 0;

    private DynamicTexture dynamicTexture;
    private ResourceLocation textureLocation;
    private int lastTexSize = -1;
    private boolean dirty = true;

    public RadarScreen(int regionRadius, BitSet existingChunks, BitSet activeChunks,
                       List<DimensionPreGenConfig.PriorityZone> priorityZones, int playerTrailRadiusBlocks,
                       String status, double avgChunkTimeMs, double chunksPerSec,
                       int completedInZones, int totalInZones) {
        super(Component.literal("Chunk Pre-Generation Radar"));
        this.regionRadius = regionRadius;
        this.existingChunks = existingChunks;
        this.activeChunks = activeChunks;
        this.priorityZones = priorityZones != null ? priorityZones : List.of();
        this.playerTrailRadiusBlocks = playerTrailRadiusBlocks;
        this.status = status;
        this.avgChunkTimeMs = avgChunkTimeMs;
        this.chunksPerSec = chunksPerSec;
        this.completedInZones = completedInZones;
        this.totalInZones = totalInZones;
    }

    public void updateRadarData(int regionRadius, BitSet existingChunks, BitSet activeChunks,
                                List<DimensionPreGenConfig.PriorityZone> priorityZones, int playerTrailRadiusBlocks,
                                String status, double avgChunkTimeMs, double chunksPerSec,
                                int completedInZones, int totalInZones) {
        this.regionRadius = regionRadius;
        this.existingChunks = existingChunks;
        this.activeChunks = activeChunks;
        this.priorityZones = priorityZones != null ? priorityZones : List.of();
        this.playerTrailRadiusBlocks = playerTrailRadiusBlocks;
        this.status = status;
        this.avgChunkTimeMs = avgChunkTimeMs;
        this.chunksPerSec = chunksPerSec;
        this.completedInZones = completedInZones;
        this.totalInZones = totalInZones;
        this.dirty = true;
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

        if (dirty || dynamicTexture == null || lastTexSize != viewSize) {
            rebuildTexture(viewSize, gridChunks, baseChunkOffset, zeroChunkIndex);
            dirty = false;
        }

        graphics.fill(startX - 2, startY - 2, startX + viewSize + 2, startY + viewSize + 2, 0xFF333333);
        if (textureLocation != null) {
            graphics.blit(textureLocation, startX, startY, 0, 0, viewSize, viewSize, viewSize, viewSize);
        }

        // Speler-positie & trail cirkel
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ChunkPos playerChunk = mc.player.chunkPosition();
            int playerVx = (playerChunk.x + zeroChunkIndex) - (baseChunkOffset + panOffsetX);
            int playerVy = (playerChunk.z + zeroChunkIndex) - (baseChunkOffset + panOffsetZ);

            int trailRadiusChunks = playerTrailRadiusBlocks / 16;
            drawCircleOutline(graphics, startX + playerVx, startY + playerVy, trailRadiusChunks, startX, startY, viewSize, 0xAA22C55E);

            if (playerVx >= 0 && playerVx < viewSize && playerVy >= 0 && playerVy < viewSize) {
                graphics.fill(startX + playerVx, startY + playerVy, startX + playerVx + 1, startY + playerVy + 1, 0xFF22C55E);
            }
        }

        // Rood kruisje op (0,0)
        int zeroVx = zeroChunkIndex - (baseChunkOffset + panOffsetX);
        int zeroVy = zeroChunkIndex - (baseChunkOffset + panOffsetZ);
        if (zeroVx >= 1 && zeroVx < viewSize - 1 && zeroVy >= 1 && zeroVy < viewSize - 1) {
            graphics.fill(startX + zeroVx - 1, startY + zeroVy, startX + zeroVx + 2, startY + zeroVy + 1, 0xFFFF4444);
            graphics.fill(startX + zeroVx, startY + zeroVy - 1, startX + zeroVx + 1, startY + zeroVy + 2, 0xFFFF4444);
        }

        graphics.drawCenteredString(this.font, this.title, this.width / 2, startY - 14, 0xFFFFFF);

        // Links paneel: Live Debug Statistieken
        int debugX = 10;
        int debugY = startY + 4;
        if (startX > 135) {
            graphics.drawString(this.font, "§lGenerator Debug", debugX, debugY, 0xFFFFFF);
            graphics.drawString(this.font, "Status: §b" + status, debugX, debugY + 14, 0xCCCCCC);
            graphics.drawString(this.font, String.format("Tijd/chunk: §e%.1f ms", avgChunkTimeMs), debugX, debugY + 28, 0xCCCCCC);
            graphics.drawString(this.font, String.format("Snelheid: §a%.1f ch/s", chunksPerSec), debugX, debugY + 42, 0xCCCCCC);

            if (totalInZones > 0) {
                double pct = (completedInZones * 100.0) / totalInZones;
                graphics.drawString(this.font, String.format("Zones: §f%d / %d", completedInZones, totalInZones), debugX, debugY + 58, 0xCCCCCC);
                graphics.drawString(this.font, String.format("Voortgang: §6%.1f%%", pct), debugX, debugY + 72, 0xCCCCCC);

                int remaining = Math.max(0, totalInZones - completedInZones);
                if (remaining == 0) {
                    graphics.drawString(this.font, "ETA: §aVoltooid!", debugX, debugY + 86, 0xCCCCCC);
                } else if (chunksPerSec > 0.05) {
                    long etaSec = (long) (remaining / chunksPerSec);
                    graphics.drawString(this.font, "ETA: §f" + formatDuration(etaSec), debugX, debugY + 86, 0xCCCCCC);
                } else {
                    graphics.drawString(this.font, "ETA: §7--", debugX, debugY + 86, 0xCCCCCC);
                }
            }
        }

        // Rechts paneel: Legenda
        int legendX = startX + viewSize + 10;
        int legendY = startY + 4;
        if (legendX + 90 < this.width) {
            drawLegendItem(graphics, legendX, legendY, 0xFF777777, "Op schijf");
            drawLegendItem(graphics, legendX, legendY + 14, 0xFF00D8FF, "Actief");
            drawLegendItem(graphics, legendX, legendY + 28, 0x99FF8C00, "Prio zone");
            drawLegendItem(graphics, legendX, legendY + 42, 0xFF22C55E, "Jij");
        }

        // Onderbalk: Hover-coördinaten of bediening
        if (mouseX >= startX && mouseX < startX + viewSize && mouseY >= startY && mouseY < startY + viewSize) {
            int vx = mouseX - startX;
            int vy = mouseY - startY;
            int chunkX = (baseChunkOffset + panOffsetX + vx) - zeroChunkIndex;
            int chunkZ = (baseChunkOffset + panOffsetZ + vy) - zeroChunkIndex;
            int minBlockX = chunkX << 4;
            int minBlockZ = chunkZ << 4;

            double hoveredPrio = getHighestZonePriority(minBlockX + 8, minBlockZ + 8);
            String prioText = hoveredPrio > 0 ? String.format("  |  Prio: %.0f%%", hoveredPrio * 100.0) : "";

            String hoverText = String.format("Chunk: [%d, %d]  |  Blokken: [%d, %d] t/m [%d, %d]%s",
                    chunkX, chunkZ, minBlockX, minBlockZ, minBlockX + 15, minBlockZ + 15, prioText);
            graphics.drawCenteredString(this.font, hoverText, this.width / 2, startY + viewSize + 8, 0xE2E8F0);
        } else {
            graphics.drawCenteredString(this.font, "Pijltjestoetsen: pannen (Shift = snel)  |  R: reset", this.width / 2, startY + viewSize + 8, 0x94A3B8);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%du %02dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %02ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private void rebuildTexture(int viewSize, int gridChunks, int baseChunkOffset, int zeroChunkIndex) {
        if (dynamicTexture == null || lastTexSize != viewSize) {
            if (dynamicTexture != null) {
                dynamicTexture.close();
            }
            dynamicTexture = new DynamicTexture(viewSize, viewSize, false);
            textureLocation = Minecraft.getInstance().getTextureManager().register("raws_pregen_radar", dynamicTexture);
            lastTexSize = viewSize;
        }

        NativeImage pixels = dynamicTexture.getPixels();
        if (pixels == null) return;

        double highestZoneMax = 0.0;
        for (DimensionPreGenConfig.PriorityZone z : priorityZones) {
            if (z.maxPrio() > highestZoneMax) highestZoneMax = z.maxPrio();
        }
        if (highestZoneMax <= 0.0) highestZoneMax = 1.0;

        final int colBlack = 0xFF000000;
        final int colGray  = 0xFF777777;
        final int colBlue  = 0xFFFFD800;

        for (int vy = 0; vy < viewSize; vy++) {
            int gz = baseChunkOffset + panOffsetZ + vy;
            int worldChunkZ = gz - zeroChunkIndex;
            int blockCenterZ = (worldChunkZ << 4) + 8;

            for (int vx = 0; vx < viewSize; vx++) {
                int gx = baseChunkOffset + panOffsetX + vx;
                if (gx < 0 || gx >= gridChunks || gz < 0 || gz >= gridChunks) {
                    pixels.setPixelRGBA(vx, vy, colBlack);
                    continue;
                }

                int idx = gz * gridChunks + gx;
                if (activeChunks.get(idx)) {
                    pixels.setPixelRGBA(vx, vy, colBlue);
                } else if (existingChunks.get(idx)) {
                    pixels.setPixelRGBA(vx, vy, colGray);
                } else {
                    int worldChunkX = gx - zeroChunkIndex;
                    int blockCenterX = (worldChunkX << 4) + 8;
                    double prio = getHighestZonePriority(blockCenterX, blockCenterZ);
                    if (prio > 0.0) {
                        double relative = Math.min(1.0, prio / highestZoneMax);
                        double intensity = 0.20 + (relative * 0.80);
                        int r = (int) Math.round(255 * intensity);
                        int g = (int) Math.round(140 * intensity);
                        int b = 0;
                        int abgr = (0xFF << 24) | (b << 16) | (g << 8) | r;
                        pixels.setPixelRGBA(vx, vy, abgr);
                    } else {
                        pixels.setPixelRGBA(vx, vy, colBlack);
                    }
                }
            }
        }

        dynamicTexture.upload();
    }

    private double getHighestZonePriority(int blockX, int blockZ) {
        double best = 0.0;
        for (DimensionPreGenConfig.PriorityZone zone : priorityZones) {
            if (zone.radiusBlocks() <= 0 || zone.maxPrio() <= 0.0) continue;
            double dx = blockX - zone.centerX();
            double dz = blockZ - zone.centerZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist <= zone.radiusBlocks()) {
                double factor = 1.0 - (dist / zone.radiusBlocks());
                double prio = zone.maxPrio() * factor;
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
        PacketHandler.sendToServer(new StopWatchingRadarPacket());
        if (dynamicTexture != null) {
            dynamicTexture.close();
            dynamicTexture = null;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
