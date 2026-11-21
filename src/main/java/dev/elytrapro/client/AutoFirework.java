package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import dev.elytrapro.util.ElytraUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
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
     *  - no firework rocket in either hand => always vanilla (nothing is touched)
     *  - rocket in one hand, and the OTHER hand holds food, a drink, a bow or similar => vanilla
     *    (eating and drinking always work)
     *  - otherwise, with a rocket in hand, the click is consumed when you are inside a cobweb, look at
     *    a placed trigger block, look at another player (up to playerTriggerRange blocks), or
     *    launchAnywhere is on.
     * With debug on, the action bar tells why a rocket click did or did not launch.
     */
    public static boolean tryUse(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ElytraProConfig cfg = ElytraProConfig.get();
        if (!cfg.autoFirework || p == null || mc.world == null || mc.interactionManager == null) return false;
        if (p.isSpectator() || p.hasVehicle()) return false;
        // never interrupt eating, drinking, blocking, drawing a bow
        if (p.isUsingItem()) return false;

        // 1) the rocket decides: without a usable rocket in hand everything stays vanilla
        Hand hand = findTriggerHand(p);
        if (hand == null) {
            if (holdsRocket(p)) debug(p, cfg, "other hand has food/drink/bow, vanilla");
            return false;
        }

        // 2) trigger: inside a cobweb, on a placed trigger block, or looking at a player
        boolean inWeb = cfg.launchInsideCobweb && ElytraUtil.inCobweb(p);
        boolean onTriggerBlock = mc.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK
                && cfg.isTriggerBlock(mc.world.getBlockState(hit.getBlockPos()));
        boolean onPlayer = cfg.triggerOnPlayer && lookingAtPlayer(mc, p, cfg.playerTriggerRange);
        if (!inWeb && !onTriggerBlock && !onPlayer && !cfg.launchAnywhere) {
            debug(p, cfg, "no target: look at a player, log, planks, cobweb or bedrock");
            return false;
        }

        // Shift never blocks the rocket inside a cobweb or while gliding.
        // Elsewhere it only blocks when allowWhileSneaking is off.
        if (p.isSneaking() && !cfg.allowWhileSneaking && !inWeb && !p.isFallFlying()) {
            debug(p, cfg, "sneaking, vanilla");
            return false;
        }

        // 3) holding a block => the player wants to place it, stay vanilla
        if (!cfg.triggerWithBlockInHand && p.getMainHandStack().getItem() instanceof BlockItem) {
            debug(p, cfg, "block in main hand, vanilla");
            return false;
        }

        if (!ElytraUtil.hasUsableElytra(p)) { debug(p, cfg, "no usable elytra worn"); return false; }

        long now = mc.world.getTime();
        if (now - lastUse < cfg.cooldownTicks) return true;
        // the next rocket waits until the current one has burned out
        if (cfg.skipIfBoosting && now < boostUntil) return true;

        // already gliding: fire
        if (p.isFallFlying()) {
            debug(p, cfg, "rocket");
            fire(mc, p, hand, now);
            return true;
        }

        if (p.isTouchingWater() || p.hasStatusEffect(StatusEffects.LEVITATION)) {
            debug(p, cfg, "water or levitation, vanilla");
            return false;
        }

        // on the ground: jump, then open wings + fire when the server will accept it
        if (p.isOnGround()) {
            if (!cfg.launchFromGround) { debug(p, cfg, "launchFromGround is off"); return false; }
            ElytraUtil.hop(p);
            pending = PENDING_TICKS;
            debug(p, cfg, "jump, then take off");
            return true;
        }

        // in the air: open wings and fire in the same tick
        if (ElytraUtil.canStartNow(p)) {
            if (ElytraUtil.tryStartGliding(p)) {
                debug(p, cfg, "wings + rocket");
                fire(mc, p, hand, now);
            }
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

        ElytraProConfig cfg = ElytraProConfig.get();
        if (p.isFallFlying()) {
            boolean ready = now - lastUse >= cfg.cooldownTicks && !(cfg.skipIfBoosting && now < boostUntil);
            if (ready) fire(mc, p, hand, now);
            pending = 0;
            return;
        }
        if (!ElytraUtil.canStartNow(p)) return;
        if (ElytraUtil.tryStartGliding(p)) fire(mc, p, hand, now);
        pending = 0;
    }

    /**
     * The hand whose rocket the click would use, or null. Same rule for both hands: the rocket is in
     * one hand and the other hand must not react to a right click (food, drink, bow and similar).
     */
    private static Hand findTriggerHand(ClientPlayerEntity p) {
        ItemStack main = p.getMainHandStack();
        ItemStack off = p.getOffHandStack();
        if (main.isOf(Items.FIREWORK_ROCKET)) return usesClick(off) ? null : Hand.MAIN_HAND;
        if (off.isOf(Items.FIREWORK_ROCKET)) return usesClick(main) ? null : Hand.OFF_HAND;
        return null;
    }

    private static boolean holdsRocket(ClientPlayerEntity p) {
        return p.getMainHandStack().isOf(Items.FIREWORK_ROCKET) || p.getOffHandStack().isOf(Items.FIREWORK_ROCKET);
    }

    /** Food, drinks, bows and similar: the right click belongs to them (shield and swords are ignored). */
    private static boolean usesClick(ItemStack s) {
        if (s.isEmpty() || s.isOf(Items.FIREWORK_ROCKET)) return false;
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

    private static void debug(ClientPlayerEntity p, ElytraProConfig cfg, String msg) {
        if (cfg.debug) p.sendMessage(Text.literal("ElytraPro: " + msg), true);
    }

    private static void fire(MinecraftClient mc, ClientPlayerEntity p, Hand hand, long now) {
        ItemStack stack = p.getStackInHand(hand);
        NbtCompound tag = stack.getSubNbt("Fireworks");
        int flight = tag != null && tag.contains("Flight") ? tag.getByte("Flight") : 0;
        lastUse = now;
        // vanilla rocket lifetime: 10 * (1 + Flight) + up to 11 random ticks
        boostUntil = now + 10L * (1 + Math.max(0, flight)) + 11;
        ActionResult r = mc.interactionManager.interactItem(p, hand);
        if (r.shouldSwingHand()) p.swingHand(hand);
    }
}
