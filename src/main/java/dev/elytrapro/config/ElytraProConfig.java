package dev.elytrapro.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.elytrapro.client.ElytraProClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
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

    private static final int CONFIG_VERSION = 9;

    /** Old config files are reset once so the new defaults take effect. */
    public int configVersion; // 0 when missing in an old file
    public boolean autoFirework = true;
    public boolean autoElytra = true;
    /** Right click (rocket in hand) on the ground: jump, open wings and fire. */
    public boolean launchFromGround = true;
    /** Minimum ticks between two automatic firework uses. */
    public int cooldownTicks = 10;
    /** Do not use another rocket while one launched by the player is still active. */
    public boolean skipIfBoosting = true;
    /** Ticks in the air (jump held) before Elytra opens. */
    public int minAirTicks = 0;
    /** Only try to open wings when moving downwards (older server rule). Off (default) = open instantly. On = only when falling (use if a server rejects instant open). */
    public boolean onlyWhenFalling = false;
    /** Right click while standing inside a cobweb launches you (any crosshair target). */
    public boolean launchInsideCobweb = true;
    /** If false, right click with a block in hand always places the block (vanilla). */
    public boolean triggerWithBlockInHand = false;
    /** PLACED blocks that launch you when you right-click them. */
    public List<String> triggerBlockTags = List.of("minecraft:logs", "minecraft:planks");
    public List<String> triggerBlocks = List.of("minecraft:cobweb", "minecraft:bedrock");

    /** Fly Hit: auto fly to a nearly dead player, switch to a sword and hit. */
    public boolean flyHit = true;
    /** Target must have at most this much health (hit points incl. absorption). 8 = 4 hearts, 4 = 2 hearts. */
    public float flyHitHealth = 8.0F;
    /** Max distance to look for targets. */
    public double flyHitRange = 48.0;
    /** Attack distance (to the target's hitbox). */
    public double flyHitReach = 3.0;
    /** Only attack when the sword cooldown is at least this full (0-1). */
    public float flyHitMinCooldown = 0.85F;
    /** Max degrees the view turns per tick while flying at the target. */
    public float flyHitTurnSpeed = 28.0F;
    /** Use firework rockets (offhand or hotbar) to take off and keep speed. */
    public boolean flyHitUseRockets = true;
    public int flyHitRocketCooldown = 12;
    public double flyHitMinSpeed = 1.15;
    public double flyHitRocketMinDistance = 8.0;
    /** Only hunt targets with a free line (open area): no blocks between you and them. */
    public boolean flyHitNeedLineOfSight = true;
    /** Player names (any case) that are never attacked. */
    public List<String> flyHitIgnoreNames = new ArrayList<>();

    private transient Set<Block> resolvedBlocks;
    private transient List<TagKey<Block>> resolvedTags;

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
        boolean write = false;
        if (cfg == null || cfg.configVersion < CONFIG_VERSION) {
            cfg = new ElytraProConfig();
            cfg.configVersion = CONFIG_VERSION;
            write = true;
        }
        if (write) {
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(cfg, w);
            } catch (IOException e) {
                ElytraProClient.LOGGER.error("Could not write elytrapro.json", e);
            }
        }
        cfg.cooldownTicks = Math.max(0, cfg.cooldownTicks);
        cfg.minAirTicks = Math.max(0, cfg.minAirTicks);
        if (cfg.triggerBlockTags == null) cfg.triggerBlockTags = List.of();
        if (cfg.triggerBlocks == null) cfg.triggerBlocks = List.of();
        if (cfg.flyHitIgnoreNames == null) cfg.flyHitIgnoreNames = new ArrayList<>();
        cfg.flyHitHealth = Math.max(1.0F, cfg.flyHitHealth);
        cfg.flyHitMinCooldown = Math.max(0.0F, Math.min(1.0F, cfg.flyHitMinCooldown));
        return cfg;
    }

    private void resolve() {
        resolvedBlocks = new HashSet<>();
        resolvedTags = new ArrayList<>();
        for (String s : triggerBlocks) {
            Identifier id = Identifier.tryParse(s);
            if (id != null && Registries.BLOCK.containsId(id)) resolvedBlocks.add(Registries.BLOCK.get(id));
        }
        for (String s : triggerBlockTags) {
            Identifier id = Identifier.tryParse(s);
            if (id != null) resolvedTags.add(TagKey.of(RegistryKeys.BLOCK, id));
        }
    }

    public boolean isTriggerBlock(BlockState state) {
        if (resolvedBlocks == null) resolve();
        if (resolvedBlocks.contains(state.getBlock())) return true;
        for (TagKey<Block> tag : resolvedTags) {
            if (state.isIn(tag)) return true;
        }
        return false;
    }
}
