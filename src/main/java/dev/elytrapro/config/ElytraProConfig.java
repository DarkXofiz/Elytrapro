package dev.elytrapro.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.elytrapro.client.ElytraProClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ElytraProConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ElytraProConfig instance;

    public boolean autoFirework = true;
    public boolean autoElytra = true;
    /** Minimum ticks between two automatic firework uses. */
    public int cooldownTicks = 10;
    /** Do not use another rocket while one launched by the player is still active. */
    public boolean skipIfBoosting = true;
    /** Ticks in the air (jump held) before Elytra opens. */
    public int minAirTicks = 2;
    /** Ticks between Elytra open attempts (prevents packet spam if server refuses). */
    public int retryDelayTicks = 5;
    public List<String> triggerTags = List.of("minecraft:logs", "minecraft:planks");
    public List<String> triggerItems = List.of("minecraft:cobweb");

    private transient Set<Item> resolvedItems;
    private transient List<TagKey<Item>> resolvedTags;

    public static ElytraProConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static ElytraProConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("elytrapro.json");
        ElytraProConfig cfg = null;
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                cfg = GSON.fromJson(r, ElytraProConfig.class);
            } catch (Exception e) {
                ElytraProClient.LOGGER.error("Could not read elytrapro.json, using defaults", e);
            }
        }
        if (cfg == null) {
            cfg = new ElytraProConfig();
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(cfg, w);
            } catch (IOException e) {
                ElytraProClient.LOGGER.error("Could not write elytrapro.json", e);
            }
        }
        cfg.cooldownTicks = Math.max(0, cfg.cooldownTicks);
        cfg.minAirTicks = Math.max(0, cfg.minAirTicks);
        cfg.retryDelayTicks = Math.max(1, cfg.retryDelayTicks);
        if (cfg.triggerTags == null) cfg.triggerTags = List.of();
        if (cfg.triggerItems == null) cfg.triggerItems = List.of();
        return cfg;
    }

    private void resolve() {
        resolvedItems = new HashSet<>();
        resolvedTags = new ArrayList<>();
        for (String s : triggerItems) {
            Identifier id = Identifier.tryParse(s);
            if (id != null && Registries.ITEM.containsId(id)) resolvedItems.add(Registries.ITEM.get(id));
        }
        for (String s : triggerTags) {
            Identifier id = Identifier.tryParse(s);
            if (id != null) resolvedTags.add(TagKey.of(RegistryKeys.ITEM, id));
        }
    }

    public boolean isTrigger(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (resolvedItems == null) resolve();
        if (resolvedItems.contains(stack.getItem())) return true;
        for (TagKey<Item> tag : resolvedTags) {
            if (stack.isIn(tag)) return true;
        }
        return false;
    }
}
