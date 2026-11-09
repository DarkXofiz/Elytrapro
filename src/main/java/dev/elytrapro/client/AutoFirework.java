package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import dev.elytrapro.util.ElytraUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class AutoFirework {
    private static final int PENDING_TICKS = 40;

    private static long lastUse = Long.MIN_VALUE / 2;
    private static long boostUntil = Long.MIN_VALUE / 2;
    private static int pending;

    private AutoFirework() {}

    /**
     * Called when the right-click key is processed. Returns true only when the click is consumed
     * by the mod. The rocket decides everything:
     *  - no firework rocket in the hand that the click would use => always vanilla (eating, drinking,
     *    blocking, placing blocks, bows and so on are never touched)
     *  - rocket in the main hand => trigger, unless the off hand holds food, a drink or a bow
     *  - rocket in the off hand => trigger only if the main hand is empty, a sword or a pickaxe
     *    (food, potions, bows, tools with a block use and so on keep working)
     * With a rocket in hand the click is consumed only when you are inside a cobweb, look at a placed
     * trigger block (logs, planks, cobweb, bedrock) or look at another player.
     */
    public static boolean tryUse(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ElytraProConfig cfg = ElytraProConfig.get();
        if (!cfg.autoFirework || p == null || mc.world == null || mc.interactionManager == null) return false;
        if (p.isSpectator() || p.isSneaking() || p.hasVehicle()) return false;
        // never interrupt eating, drinking, blocking, drawing a bow
        if (p.isUsingItem()) return false;

        // 1) the rocket decides: without a usable rocket in hand everything stays vanilla
        Hand hand = findTriggerHand(p);
        if (hand == null) return false;

        // 2) trigger: standing inside a cobweb, looking at a placed trigger block, or looking at a player
        boolean inWeb = cfg.launchInsideCobweb && ElytraUtil.inCobweb(p);
        boolean onTriggerBlock = mc.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK
                && cfg.isTriggerBlock(mc.world.getBlockState(hit.getBlockPos()));
        boolean onPlayer = cfg.triggerOnPlayer && lookingAtPlayer(mc, p, cfg.playerTriggerRange);
        if (!inWeb && !onTriggerBlock && !onPlayer) return false;

        // 3) holding a block => the player wants to place it, stay vanilla
        if (!cfg.triggerWithBlockInHand && p.getMainHandStack().getItem() instanceof BlockItem) return false;

        if (!ElytraUtil.hasUsableElytra(p)) return false;

        long now = mc.world.getTime();
        if (now - lastUse < cfg.cooldownTicks) return true;

        // already gliding: fire at once
        if (p.isFallFlying()) {
            if (cfg.skipIfBoosting && now < boostUntil) return true;
            fire(mc, p, hand, now);
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
            if (ElytraUtil.tryStartGliding(p)) fire(mc, p, hand, now);
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
        if (p.hasVehicle() || p.isTouchingWater() || p.isSpectator() || p.isUsingItem()) { pending = 0; return; }

        Hand hand = findTriggerHand(p);
        if (hand == null) { pending = 0; return; }
        long now = mc.world.getTime();

        if (p.isFallFlying()) {
            if (now - lastUse >= ElytraProConfig.get().cooldownTicks) fire(mc, p, hand, now);
            pending = 0;
            return;
        }
        if (!ElytraUtil.canStartNow(p)) return;
        if (ElytraUtil.tryStartGliding(p)) fire(mc, p, hand, now);
        pending = 0;
    }

    /**
     * The hand whose rocket the click would use, or null. The rocket must be in one hand AND the other
     * hand must not react to a right click on its own, so both hands behave the same:
     *  - rocket in the main hand: off hand must not hold food or a drink (otherwise vanilla eating wins)
     *  - rocket in the off hand: main hand must be empty, a sword or a pickaxe
     */
    private static Hand findTriggerHand(ClientPlayerEntity p) {
        ItemStack main = p.getMainHandStack();
        ItemStack off = p.getOffHandStack();
        if (main.isOf(Items.FIREWORK_ROCKET)) return offHandUsesClick(off) ? null : Hand.MAIN_HAND;
        if (off.isOf(Items.FIREWORK_ROCKET) && mainHandLeavesClickFree(main)) return Hand.OFF_HAND;
        return null;
    }

    /** Empty hand, sword or pickaxe: nothing happens on right click, so the off hand rocket may take it. */
    private static boolean mainHandLeavesClickFree(ItemStack s) {
        if (s.isEmpty()) return true;
        Item item = s.getItem();
        return item instanceof SwordItem || item instanceof PickaxeItem;
    }

    /** Food, drinks, bows and similar in the off hand: the click belongs to them (shield is ignored). */
    private static boolean offHandUsesClick(ItemStack s) {
        if (s.isEmpty()) return false;
        if (s.getItem().isFood()) return true;
        UseAction a = s.getUseAction();
        return a != UseAction.NONE && a != UseAction.BLOCK;
    }

    /**
     * True when another player is under the crosshair. Vanilla only targets entities within about 3
     * blocks, so a second ray looks further (up to {@code range}) and stops at the first block.
     */
    private static boolean lookingAtPlayer(MinecraftClient mc, ClientPlayerEntity p, double range) {
        if (mc.crosshairTarget instanceof EntityHitResult eh
                && eh.getEntity() instanceof PlayerEntity other && other != p) {
            return true;
        }
        if (range <= 3.0 || mc.world == null) return false;

        Vec3d from = p.getCameraPosVec(1.0F);
        Vec3d look = p.getRotationVec(1.0F);
        Vec3d to = from.add(look.multiply(range));

        HitResult blockHit = mc.world.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        double maxSq = range * range;
        if (blockHit.getType() != HitResult.Type.MISS) {
            maxSq = blockHit.getPos().squaredDistanceTo(from);
        }

        Box box = p.getBoundingBox().stretch(look.multiply(range)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(p, from, to, box,
                e -> e instanceof PlayerEntity && e != p && !e.isSpectator() && e.canHit(), maxSq);
        return hit != null;
    }

    private static void fire(MinecraftClient mc, ClientPlayerEntity p, Hand hand, long now) {
        ItemStack stack = p.getStackInHand(hand);
        NbtCompound tag = stack.getSubNbt("Fireworks");
        int flight = tag != null && tag.contains("Flight") ? tag.getByte("Flight") : 1;
        lastUse = now;
        boostUntil = now + 10L * Math.max(1, flight) + 6;
        ActionResult r = mc.interactionManager.interactItem(p, hand);
        if (r.shouldSwingHand()) p.swingHand(hand);
    }
}
