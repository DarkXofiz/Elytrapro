package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import dev.elytrapro.util.ElytraUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public final class AutoElytra {
    private static final int REOPEN_WINDOW = 60;
    private static final int MAX_REOPENS = 12;

    private static int airTicks;
    private static int sinceAttempt = 1000;
    private static int recentGlide;
    private static int glideTicks;
    private static int reopens;
    private static boolean attemptedThisPress;

    private AutoElytra() {}

    /** Runs every client tick. Never sends a "stop gliding" packet. */
    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ElytraProConfig cfg = ElytraProConfig.get();
        if (p == null || mc.world == null) { reset(); return; }

        // queued "take off + rocket" from a right click on a trigger block
        AutoFirework.tick(mc);

        if (!cfg.autoElytra) return;

        boolean jump = mc.options.jumpKey.isPressed() && mc.currentScreen == null;
        if (!jump) attemptedThisPress = false;

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
            if (++glideTicks >= 10) reopens = 0;
            return;
        }
        glideTicks = 0;
        if (recentGlide > 0) recentGlide--;

        // Inside a cobweb: open ONCE per space press, never retry, never auto re-open.
        // (Retrying every few ticks is what made the wings open/close/open/close.)
        if (ElytraUtil.inCobweb(p)) {
            if (!jump || attemptedThisPress) return;
            if (!ElytraUtil.canStartNow(p)) return; // wait (same press) until airborne
            attemptedThisPress = true;
            ElytraUtil.tryStartGliding(p);
            return;
        }

        boolean keepOpen = cfg.keepOpen && recentGlide > 0 && reopens < MAX_REOPENS;
        if (!jump && !keepOpen) return;
        if (airTicks < cfg.minAirTicks || sinceAttempt < cfg.retryDelayTicks) return;

        sinceAttempt = 0;
        if (ElytraUtil.tryStartGliding(p) && !jump) reopens++;
    }

    private static void reset() {
        airTicks = 0;
        recentGlide = 0;
        glideTicks = 0;
        reopens = 0;
        attemptedThisPress = false;
    }
}
