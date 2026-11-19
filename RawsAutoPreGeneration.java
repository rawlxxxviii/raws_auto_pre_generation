package com.gmail.rawlxxxviii.raws_auto_pre_generation;

import com.gmail.rawlxxxviii.raws_auto_pre_generation.config.ServerConfig;
import com.gmail.rawlxxxviii.raws_auto_pre_generation.network.PacketHandler;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(RawsAutoPreGeneration.MODID)
public class RawsAutoPreGeneration {
    public static final String MODID = "raws_auto_pre_generation";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RawsAutoPreGeneration() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SERVER_SPEC, MODID + "-server.toml");

        MinecraftForge.EVENT_BUS.register(this);
    }

    public void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(PacketHandler::register);
    }

    public void clientSetup(final FMLClientSetupEvent event) {
    }
}
