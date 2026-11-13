package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import dev.elytrapro.util.ElytraUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;

public final class AutoElytra {
    private static boolean attemptedThisPress;
    private static boolean hoppedThisPress;

    private AutoElytra() {}

    /**
     * Runs every client tick. Inside a cobweb only: pressing the trigger key (Shift by default)
     * opens the wings at once, one time per press. Shift never blocks the rocket, and the mod
     * never sends a "stop gliding" packet.
     */
    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ElytraProConfig cfg = ElytraProConfig.get();
        if (p == null || mc.world == null) { attemptedThisPress = false; hoppedThisPress = false; return; }

        // queued "take off + rocket" from a right click
        AutoFirework.tick(mc);

        if (!cfg.autoElytra) return;

        boolean key = triggerHeld(mc, cfg);
        if (!key) { attemptedThisPress = false; hoppedThisPress = false; }

        if (p.isFallFlying()) {
            // wings are open: this press is done
            if (key) attemptedThisPress = true;
            return;
        }

        // Only inside a cobweb. Everywhere else the keys stay vanilla.
        if (!key || attemptedThisPress) return;
        if (!ElytraUtil.inCobweb(p)) return;
        if (!ElytraUtil.hasUsableElytra(p)) return;
        if (p.hasVehicle() || p.isSpectator() || p.isTouchingWater()
                || p.hasStatusEffect(StatusEffects.LEVITATION)) return;

        if (p.isOnGround()) {
            // vanilla cannot glide from the ground: one small hop, then open in the air
            if (cfg.cobwebHop && !hoppedThisPress) {
                hoppedThisPress = true;
                ElytraUtil.hop(p);
            }
            return;
        }

        if (!ElytraUtil.canStartNow(p)) return;
        attemptedThisPress = true; // exactly one attempt per press
        ElytraUtil.tryStartGliding(p);
    }

    private static boolean triggerHeld(MinecraftClient mc, ElytraProConfig cfg) {
        if (mc.currentScreen != null) return false;
        boolean sneak = mc.options.sneakKey.isPressed();
        boolean jump = mc.options.jumpKey.isPressed();
        switch (cfg.cobwebOpenKey == null ? "SNEAK" : cfg.cobwebOpenKey.toUpperCase()) {
            case "JUMP": return jump;
            case "BOTH": return sneak || jump;
            default: return sneak;
        }
    }
}
