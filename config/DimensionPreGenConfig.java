package com.gmail.rawlxxxviii.raws_auto_pre_generation.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DimensionPreGenConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record PriorityZone(int centerX, int centerZ, int radiusBlocks, double maxPrio) {}

    public boolean enabled = true;
    public List<PriorityZone> zones = new ArrayList<>();

    public static DimensionPreGenConfig loadOrCreate(MinecraftServer server, ResourceLocation dimensionId) {
        Path configDir = server.getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig")
                .resolve("raws_auto_pre_generation")
                .resolve("dimensions")
                .resolve(dimensionId.getNamespace());

        Path configFile = configDir.resolve(dimensionId.getPath() + ".json");

        try {
            if (Files.exists(configFile)) {
                try (Reader reader = Files.newBufferedReader(configFile)) {
                    DimensionPreGenConfig loaded = GSON.fromJson(reader, DimensionPreGenConfig.class);
                    if (loaded != null) {
                        if (loaded.zones == null) loaded.zones = new ArrayList<>();
                        return loaded;
                    }
                }
            }

            DimensionPreGenConfig def = createDefault(dimensionId);
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(def, writer);
            }
            return def;
        } catch (Exception e) {
            return createDefault(dimensionId);
        }
    }

    private static DimensionPreGenConfig createDefault(ResourceLocation dimensionId) {
        DimensionPreGenConfig cfg = new DimensionPreGenConfig();
        String path = dimensionId.getPath();
        if (path.equals("overworld")) {
            cfg.zones.add(new PriorityZone(0, 0, 3000, 0.50));
            cfg.zones.add(new PriorityZone(0, 0, 15000, 0.20));
        } else if (path.equals("the_nether")) {
            cfg.zones.add(new PriorityZone(0, 0, 1500, 0.50));
        } else if (path.equals("the_end")) {
            cfg.zones.add(new PriorityZone(0, 0, 3000, 0.40));
        } else {
            cfg.zones.add(new PriorityZone(0, 0, 2000, 0.40));
        }
        return cfg;
    }
}
