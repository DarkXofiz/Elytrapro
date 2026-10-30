package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import dev.elytrapro.util.ElytraUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public final class AutoElytra {
    private static final int REOPEN_WINDOW = 60;   // ticks after the last glide tick in which wings are re-opened
    private static final int MAX_REOPENS = 12;     // safety: stop if the server keeps closing the wings

    private static int airTicks;
    private static int sinceAttempt = 1000;
    private static int recentGlide;
    private static int glideTicks;
    private static int reopens;

    private AutoElytra() {}

    /** Runs every client tick. Never sends a "stop gliding" packet. */
    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ElytraProConfig cfg = ElytraProConfig.get();
        if (p == null || mc.world == null) { reset(); return; }

        // pending "rocket + take off" request from a right click
        AutoFirework.tick(mc);

        if (!cfg.autoElytra) return;

        if (p.isOnGround() || p.isTouchingWater()) {
            airTicks = 0;
            recentGlide = 0;
            reopens = 0;
        } else {
            airTicks++;
        }
        sinceAttempt++;

        if (p.isFallFlying()) {
            recentGlide = REOPEN_WINDOW;
            if (++glideTicks >= 10) reopens = 0; // stable flight -> allow future re-opens
            return;
        }
        glideTicks = 0;
        if (recentGlide > 0) recentGlide--;

        if (mc.currentScreen != null) return;

        boolean jumpHeld = mc.options.jumpKey.isPressed();
        boolean keepOpen = cfg.keepOpen && recentGlide > 0 && reopens < MAX_REOPENS;
        if (!jumpHeld && !keepOpen) return;
        if (airTicks < cfg.minAirTicks || sinceAttempt < cfg.retryDelayTicks) return;

        sinceAttempt = 0;
        if (ElytraUtil.tryStartGliding(p) && !jumpHeld) reopens++;
    }

    private static void reset() {
        airTicks = 0;
        recentGlide = 0;
        glideTicks = 0;
        reopens = 0;
    }
}
