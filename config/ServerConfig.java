package com.gmail.rawlxxxviii.raws_auto_pre_generation.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ServerConfig INSTANCE;

    public final ForgeConfigSpec.BooleanValue noGenerationWhenPlayersOnline;
    public final ForgeConfigSpec.BooleanValue opsCountTowardsPlayersOnline;
    public final ForgeConfigSpec.IntValue playerTrailingDecayTimeMinutes;
    public final ForgeConfigSpec.IntValue playerTrailingRadiusBlocks;
    public final ForgeConfigSpec.DoubleValue playerTrailingMaxPrio;

    static {
        Pair<ServerConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SPEC = specPair.getRight();
        INSTANCE = specPair.getLeft();
    }

    private ServerConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Algemene instellingen voor Raw's Auto Pre Generation").push("general");
        noGenerationWhenPlayersOnline = builder.define("no_generation_when_players_online", true);
        opsCountTowardsPlayersOnline = builder.define("ops_count_towards_players_online", false);
        playerTrailingDecayTimeMinutes = builder.defineInRange("player_trailing_decay_time_minutes", 60, 1, 10080);
        playerTrailingRadiusBlocks = builder.defineInRange("player_trailing_radius_blocks", 1000, 0, 10000);
        playerTrailingMaxPrio = builder.defineInRange("player_trailing_max_prio", 0.80, 0.0, 1.0);
        builder.pop();
    }
}
