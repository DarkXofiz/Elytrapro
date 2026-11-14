package dev.elytrapro.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Armed with LEFT ALT. Next time the elytra opens while escaping a cobweb: fires one firework,
 * swaps to whichever hotbar slot has the HIGHEST Sweeping Edge level on it (any item type, not
 * filtered to sword or axe), lands exactly one hit on whatever is under the crosshair, then swaps
 * back to the slot that was selected before the swap.
 * One shot per Alt press: it disarms itself the instant it fires or the opportunity passes.
 * If nothing in the hotbar has Sweeping Edge, this does nothing at all.
 */
public final class AutoWebEscapeHit {
    private static boolean armed;
    private static int returnSlot = -1;
    private static int waitTicks;

    private AutoWebEscapeHit() {}

    public static void arm() {
        armed = true;
        waitTicks = 0;
    }

    /** Called from AutoElytra right after the wings are opened while escaping a cobweb. */
    public static void onWebEscapeGlideStart(MinecraftClient mc) {
        if (!armed) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) { armed = false; return; }

        int sweepSlot = findSweepingItem(p);
        if (sweepSlot < 0) { armed = false; return; } // no Sweeping Edge item in hotbar: do nothing

        PlayerInventory inv = p.getInventory();
        returnSlot = inv.selectedSlot;

        fireRocket(mc, p, inv, sweepSlot);

        inv.selectedSlot = sweepSlot;
        Entity target = (mc.crosshairTarget instanceof EntityHitResult eh
                && eh.getType() == HitResult.Type.ENTITY) ? eh.getEntity() : null;
        if (target != null) {
            mc.interactionManager.attackEntity(p, target);
            p.swingHand(Hand.MAIN_HAND);
        } else {
            // no target yet: give the crosshair a few ticks to land on one before giving up
            waitTicks = 10;
            return;
        }
        finish(p);
    }

    /** Runs every client tick: resolves the "no target yet" wait window above. */
    public static void tick(MinecraftClient mc) {
        if (waitTicks <= 0) return;
        waitTicks--;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) { armed = false; waitTicks = 0; return; }
        if (mc.crosshairTarget instanceof EntityHitResult eh && eh.getType() == HitResult.Type.ENTITY) {
            mc.interactionManager.attackEntity(p, eh.getEntity());
            p.swingHand(Hand.MAIN_HAND);
            finish(p);
            return;
        }
        if (waitTicks == 0) finish(p); // window closed, no target found: just swap back
    }

    private static void finish(ClientPlayerEntity p) {
        if (returnSlot >= 0) p.getInventory().selectedSlot = returnSlot;
        returnSlot = -1;
        waitTicks = 0;
        armed = false;
    }

    /** Highest Sweeping Edge level in the hotbar, any item type (sword, axe, anything). */
    private static int findSweepingItem(ClientPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        int best = -1;
        int bestLevel = 0; // level must be >= 1 to count
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            int level = EnchantmentHelper.getLevel(Enchantments.SWEEPING, stack);
            if (level > bestLevel) { bestLevel = level; best = i; }
        }
        return best;
    }

    private static void fireRocket(MinecraftClient mc, ClientPlayerEntity p, PlayerInventory inv, int sweepSlot) {
        if (p.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            mc.interactionManager.interactItem(p, Hand.OFF_HAND);
            p.swingHand(Hand.OFF_HAND);
            return;
        }
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(Items.FIREWORK_ROCKET)) {
                inv.selectedSlot = i;
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.swingHand(Hand.MAIN_HAND);
                inv.selectedSlot = sweepSlot; // sweeping item stays selected for the hit right after
                return;
            }
        }
        // no rocket: still allow the hit, elytra just won't get a speed boost
    }
}
