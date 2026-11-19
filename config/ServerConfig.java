package com.gmail.rawlxxxviii.raws_auto_pre_generation.config;

import net.minecraftforge.common.ForgeConfigSpec;


public class ServerConfig {

    public static final ForgeConfigSpec SERVER_SPEC;

    public static ForgeConfigSpec.BooleanValue SKY_DIMENSION_GENERATION_REQUIRES_SKY;

    static {
        ForgeConfigSpec.Builder configBuilder = new ForgeConfigSpec.Builder();
        setupConfig(configBuilder);
        SERVER_SPEC = configBuilder.build();
    }

    private static void setupConfig(ForgeConfigSpec.Builder builder) {

        builder.push("Sky Dimension Generation Settings");
            SKY_DIMENSION_GENERATION_REQUIRES_SKY = builder
                .comment("In dimensions like the overworld, should the player be under open sky for the map to be generated?")
                .comment("Leaves do not block sky view for this setting.")
                .define("sky_dimension_generation_requires_sky", true);
        builder.pop();
    }
}
