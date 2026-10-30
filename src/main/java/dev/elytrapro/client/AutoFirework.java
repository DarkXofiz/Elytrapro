package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import dev.elytrapro.util.ElytraUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public final class AutoFirework {
    private static final int PENDING_TICKS = 40;

    private static long lastUse = Long.MIN_VALUE / 2;
    private static long boostUntil = Long.MIN_VALUE / 2;
    private static int pending;

    private AutoFirework() {}

    /**
     * Called when the right-click key is processed. Returns true only when the
     * click is consumed by the mod. That happens ONLY when the crosshair is on an
     * already placed trigger BLOCK (logs, planks, cobweb by default).
     * Everything else (placing blocks, firework in hand, other blocks) stays vanilla.
     */
    public static boolean tryUse(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ElytraProConfig cfg = ElytraProConfig.get();
        if (!cfg.autoFirework || p == null || mc.world == null || mc.interactionManager == null) return false;
        if (p.isSpectator() || p.isSneaking() || p.hasVehicle()) return false;

        // 1) must be looking at a placed trigger block
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
        if (!cfg.isTriggerBlock(mc.world.getBlockState(hit.getBlockPos()))) return false;

        // 2) holding a block => the player wants to place it, stay vanilla
        if (!cfg.triggerWithBlockInHand && p.getMainHandStack().getItem() instanceof BlockItem) return false;

        if (!ElytraUtil.hasUsableElytra(p)) return false;
        int slot = findFirework(p);
        if (slot == -2) return false;

        long now = mc.world.getTime();
        if (now - lastUse < cfg.cooldownTicks) return true;

        // already gliding: fire at once
        if (p.isFallFlying()) {
            if (cfg.skipIfBoosting && now < boostUntil) return true;
            fire(mc, p, slot, now);
            return true;
        }

        if (p.isTouchingWater() || p.hasStatusEffect(StatusEffects.LEVITATION)) return false;

        // on the ground: jump, then open wings + fire when the server will accept it
        if (p.isOnGround()) {
            if (!cfg.launchFromGround) return false;
            double jump = 0.42;
            StatusEffectInstance boost = p.getStatusEffect(StatusEffects.JUMP_BOOST);
            if (boost != null) jump += 0.1 * (boost.getAmplifier() + 1);
            Vec3d v = p.getVelocity();
            p.setVelocity(v.x, Math.max(v.y, jump), v.z);
            pending = PENDING_TICKS;
            return true;
        }

        // in the air: open wings and fire in the same tick
        if (ElytraUtil.canStartNow(p)) {
            if (ElytraUtil.tryStartGliding(p)) fire(mc, p, slot, now);
            return true;
        }
        pending = PENDING_TICKS;
        return true;
    }

    /** Called every client tick; finishes a queued take-off. */
    public static void tick(MinecraftClient mc) {
        if (pending <= 0) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) { pending = 0; return; }
        pending--;
        if (p.hasVehicle() || p.isTouchingWater() || p.isSpectator()) { pending = 0; return; }

        int slot = findFirework(p);
        if (slot == -2) { pending = 0; return; }
        long now = mc.world.getTime();

        if (p.isFallFlying()) {
            if (now - lastUse >= ElytraProConfig.get().cooldownTicks) fire(mc, p, slot, now);
            pending = 0;
            return;
        }
        if (!ElytraUtil.canStartNow(p)) return;
        if (ElytraUtil.tryStartGliding(p)) fire(mc, p, slot, now);
        pending = 0;
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

    private static void fire(MinecraftClient mc, ClientPlayerEntity p, int slot, long now) {
        ItemStack stack = slot == -1 ? p.getOffHandStack() : p.getInventory().getStack(slot);
        NbtCompound tag = stack.getSubNbt("Fireworks");
        int flight = tag != null && tag.contains("Flight") ? tag.getByte("Flight") : 1;
        lastUse = now;
        boostUntil = now + 10L * Math.max(1, flight) + 6;
        use(mc, p, slot);
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
