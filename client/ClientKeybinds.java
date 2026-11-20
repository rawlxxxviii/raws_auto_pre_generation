package com.gmail.rawlxxxviii.raws_chunk_generation.client;

import com.gmail.rawlxxxviii.raws_chunk_generation.RawsChunkGenerationMod;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.OpenRadarPacket;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.PacketHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public class ClientKeybinds {

    public static final KeyMapping OPEN_RADAR_KEY = new KeyMapping(
            "key.raws_chunk_generation.open_radar",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_BACKSPACE,
            "key.categories.raws_chunk_generation"
    );

    @Mod.EventBusSubscriber(modid = RawsChunkGenerationMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_RADAR_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = RawsChunkGenerationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ForgeBusEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            while (OPEN_RADAR_KEY.consumeClick()) {
                if (Minecraft.getInstance().screen == null) {
                    PacketHandler.sendToServer(new OpenRadarPacket());
                }
            }
        }
    }
}
