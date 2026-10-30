package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import dev.elytrapro.util.ElytraUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public final class AutoElytra {
    private static int airTicks;
    private static int sinceAttempt = 1000;

    private AutoElytra() {}

    /** Runs every client tick. Never sends a "stop gliding" packet. */
    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ElytraProConfig cfg = ElytraProConfig.get();
        if (p == null || !cfg.autoElytra) { airTicks = 0; return; }

        airTicks = p.isOnGround() ? 0 : airTicks + 1;
        sinceAttempt++;

        if (p.isFallFlying() || mc.currentScreen != null) return;
        if (!mc.options.jumpKey.isPressed() || airTicks < cfg.minAirTicks) return;
        if (sinceAttempt < cfg.retryDelayTicks) return;

        sinceAttempt = 0;
        ElytraUtil.tryStartGliding(p);
    }
}
