package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import dev.elytrapro.util.ElytraUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public final class AutoFirework {
    private static long lastUse = Long.MIN_VALUE / 2;

    private AutoFirework() {}

    /**
     * Called when the right-click key is processed. Returns true if the click
     * was consumed by Auto Firework (vanilla handling is then cancelled).
     * Works regardless of what is under the crosshair (block, entity, player, air).
     */
    public static boolean tryUse(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ElytraProConfig cfg = ElytraProConfig.get();
        if (!cfg.autoFirework || p == null || mc.world == null || mc.interactionManager == null) return false;
        if (p.isSpectator() || p.isSneaking() || p.hasVehicle()) return false;
        if (!cfg.isTrigger(p.getMainHandStack())) return false;
        if (!ElytraUtil.hasUsableElytra(p)) return false;
        // Rockets only help in the air; never waste them on the ground.
        if (!p.isFallFlying() && p.isOnGround()) return false;

        int slot = findFirework(p);
        if (slot == -2) return false;

        long now = mc.world.getTime();
        if (now - lastUse < cfg.cooldownTicks) return true;
        if (cfg.skipIfBoosting && p.isFallFlying() && hasActiveRocket(mc, p)) return true;

        // Open the wings first so the rocket takes effect in the same tick.
        if (!p.isFallFlying() && !ElytraUtil.tryStartGliding(p)) return false;

        lastUse = now;
        use(mc, p, slot);
        return true;
    }

    /** -1 = offhand, 0..8 = hotbar slot, -2 = none. */
    private static int findFirework(ClientPlayerEntity p) {
        if (p.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.FIREWORK_ROCKET)) return i;
        }
        return -2;
    }

    private static boolean hasActiveRocket(MinecraftClient mc, ClientPlayerEntity p) {
        return !mc.world.getEntitiesByClass(FireworkRocketEntity.class,
                p.getBoundingBox().expand(3.0), e -> e.isAlive()).isEmpty();
    }

    private static void use(MinecraftClient mc, ClientPlayerEntity p, int slot) {
        Hand hand;
        int previous = p.getInventory().selectedSlot;
        if (slot == -1) {
            hand = Hand.OFF_HAND;
        } else {
            hand = Hand.MAIN_HAND;
            p.getInventory().selectedSlot = slot;
        }
        try {
            ActionResult r = mc.interactionManager.interactItem(p, hand);
            if (r.shouldSwingHand()) p.swingHand(hand);
        } finally {
            p.getInventory().selectedSlot = previous;
        }
    }
}
