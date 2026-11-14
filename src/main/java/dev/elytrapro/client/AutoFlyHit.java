package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import dev.elytrapro.util.ElytraUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Locale;

/**
 * Fly Hit: when a player in open air is almost dead (default 4 hearts or less), take off with the
 * elytra, steer at them with firework boosts, switch to a sword and hit as soon as they are in reach.
 *
 * Needs: elytra worn, a sword in the hotbar, rockets (hand or hotbar) for take-off / speed.
 * Nothing is taken from the inventory, only the selected hotbar slot is changed and restored.
 */
public final class AutoFlyHit {
    private static final int SCAN_INTERVAL = 2;
    private static final int LOS_GRACE_TICKS = 20;

    private static boolean enabled = true;
    private static PlayerEntity target;
    private static int returnSlot = -1;
    private static long lastRocket = Long.MIN_VALUE / 2;
    private static long lastJump = Long.MIN_VALUE / 2;
    private static int ticks;
    private static int lostSight;
    // smoothed target velocity: raw per-tick delta is too noisy (strafing/jumping
    // makes it jump every frame) and caused the stutter. Exponential smoothing fixes it.
    private static Vec3d smoothedTargetVel = Vec3d.ZERO;

    private AutoFlyHit() {}

    public static boolean toggle() {
        enabled = !enabled;
        if (!enabled) release(MinecraftClient.getInstance());
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ElytraProConfig cfg = ElytraProConfig.get();
        if (!cfg.flyHit || !enabled || p == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || p.isSpectator() || p.hasVehicle() || p.isDead()) {
            release(mc);
            return;
        }
        ticks++;

        if (target != null && !valid(p, target, cfg)) target = null;
        if (target == null && ticks % SCAN_INTERVAL == 0) {
            target = pick(mc, p, cfg);
            lostSight = 0;
            smoothedTargetVel = Vec3d.ZERO;
        }
        if (target == null) {
            release(mc);
            return;
        }

        if (!ElytraUtil.hasUsableElytra(p)) {
            release(mc);
            return;
        }
        int swordSlot = findSword(p);
        if (swordSlot < 0) {
            release(mc);
            return;
        }

        PlayerInventory inv = p.getInventory();
        if (returnSlot < 0) returnSlot = inv.selectedSlot;
        // keep the sword selected for the whole hunt, so the attack cooldown is never reset
        if (inv.selectedSlot != swordSlot) inv.selectedSlot = swordSlot;

        long now = mc.world.getTime();
        Vec3d eye = p.getEyePos();
        double dist = distanceToBox(eye, target.getBoundingBox());

        // 1) take off
        if (!p.isFallFlying()) {
            if (p.isTouchingWater() || p.hasStatusEffect(StatusEffects.LEVITATION)) return;
            if (p.isOnGround()) {
                if (!cfg.launchFromGround || dist < 4.0) return;
                if (now - lastJump >= 10) {
                    double jump = 0.42;
                    StatusEffectInstance boost = p.getStatusEffect(StatusEffects.JUMP_BOOST);
                    if (boost != null) jump += 0.1 * (boost.getAmplifier() + 1);
                    Vec3d v = p.getVelocity();
                    p.setVelocity(v.x, Math.max(v.y, jump), v.z);
                    lastJump = now;
                }
                return;
            }
            if (ElytraUtil.canStartNow(p)) ElytraUtil.tryStartGliding(p);
            if (!p.isFallFlying()) return;
        }

        // 2) steer
        steer(p, target, dist, cfg);

        // 3) speed
        if (cfg.flyHitUseRockets && dist > cfg.flyHitRocketMinDistance
                && p.getVelocity().length() < cfg.flyHitMinSpeed
                && now - lastRocket >= cfg.flyHitRocketCooldown) {
            fireRocket(mc, p, inv, swordSlot, now);
        }

        // 4) hit
        if (dist <= cfg.flyHitReach && !p.isUsingItem()
                && p.getAttackCooldownProgress(0.5F) >= cfg.flyHitMinCooldown) {
            if (inv.selectedSlot != swordSlot) inv.selectedSlot = swordSlot;
            mc.interactionManager.attackEntity(p, target);
            p.swingHand(Hand.MAIN_HAND);
        }
    }

    // ---------------------------------------------------------------- target

    private static PlayerEntity pick(MinecraftClient mc, ClientPlayerEntity p, ElytraProConfig cfg) {
        PlayerEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (PlayerEntity t : mc.world.getPlayers()) {
            if (t == p || !valid(p, t, cfg)) continue;
            double d = p.squaredDistanceTo(t);
            if (d < bestDist && canSee(mc, p, t)) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }

    private static boolean valid(ClientPlayerEntity p, PlayerEntity t, ElytraProConfig cfg) {
        if (t == p || t.isRemoved() || !t.isAlive() || t.isSpectator() || t.isCreative()) return false;
        if (p.isTeammate(t)) return false;
        if (p.squaredDistanceTo(t) > cfg.flyHitRange * cfg.flyHitRange) return false;
        float hp = t.getHealth() + t.getAbsorptionAmount();
        if (hp <= 0.0F || hp > cfg.flyHitHealth) return false;
        if (cfg.flyHitIgnoreNames != null) {
            String name = t.getGameProfile().getName().toLowerCase(Locale.ROOT);
            for (String n : cfg.flyHitIgnoreNames) {
                if (n != null && n.toLowerCase(Locale.ROOT).equals(name)) return false;
            }
        }
        if (t == target && !canSeeCached(t)) return lostSight++ < LOS_GRACE_TICKS;
        return true;
    }

    private static boolean canSeeCached(PlayerEntity t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.world != null && canSee(mc, mc.player, t);
    }

    /** Open area check: no block between your eyes and the target's body. */
    private static boolean canSee(MinecraftClient mc, ClientPlayerEntity p, PlayerEntity t) {
        if (!ElytraProConfig.get().flyHitNeedLineOfSight) return true;
        Vec3d from = p.getEyePos();
        Vec3d to = t.getPos().add(0.0, t.getHeight() * 0.6, 0.0);
        HitResult hit = mc.world.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        return hit.getType() == HitResult.Type.MISS;
    }

    // ---------------------------------------------------------------- flight

    private static void steer(ClientPlayerEntity p, PlayerEntity t, double dist, ElytraProConfig cfg) {
        Vec3d eye = p.getEyePos();

        // smooth the target's velocity (raw per-tick delta is jittery) before using it to lead
        Vec3d rawVel = new Vec3d(t.getX() - t.prevX, t.getY() - t.prevY, t.getZ() - t.prevZ);
        smoothedTargetVel = smoothedTargetVel.multiply(0.75).add(rawVel.multiply(0.25));

        double speed = Math.max(0.6, p.getVelocity().length());
        double lead = MathHelper.clamp(dist / speed, 0.0, 6.0);
        Vec3d aim = t.getPos().add(0.0, t.getHeight() * 0.55, 0.0).add(smoothedTargetVel.multiply(lead));

        double dx = aim.x - eye.x;
        double dy = aim.y - eye.y;
        double dz = aim.z - eye.z;
        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        // turn speed scales with how far off-target we are: fast when the angle is big
        // (closing in), gentle near zero error (no overshoot/oscillation close up).
        float yawErr = MathHelper.wrapDegrees(wantYaw - p.getYaw());
        float pitchErr = wantPitch - p.getPitch();
        float yawStep = MathHelper.clamp(Math.abs(yawErr) * 0.35F, 1.0F, cfg.flyHitTurnSpeed);
        float pitchStep = MathHelper.clamp(Math.abs(pitchErr) * 0.35F, 1.0F, cfg.flyHitTurnSpeed);

        float yaw = p.getYaw() + MathHelper.clamp(yawErr, -yawStep, yawStep);
        float pitch = MathHelper.clamp(p.getPitch() + MathHelper.clamp(pitchErr, -pitchStep, pitchStep), -90.0F, 90.0F);
        p.setYaw(yaw);
        p.setPitch(pitch);
    }

    private static void fireRocket(MinecraftClient mc, ClientPlayerEntity p, PlayerInventory inv, int swordSlot, long now) {
        Hand hand = null;
        int slot = -1;
        if (p.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            hand = Hand.OFF_HAND;
        } else {
            for (int i = 0; i < 9; i++) {
                if (inv.getStack(i).isOf(Items.FIREWORK_ROCKET)) { slot = i; break; }
            }
            if (slot < 0) return;
            hand = Hand.MAIN_HAND;
        }
        // swap to the rocket, use it and swap back inside the same tick (sword cooldown stays intact)
        if (slot >= 0) inv.selectedSlot = slot;
        mc.interactionManager.interactItem(p, hand);
        p.swingHand(hand);
        if (slot >= 0) inv.selectedSlot = swordSlot;
        lastRocket = now;
    }

    // ---------------------------------------------------------------- helpers

    private static int findSword(ClientPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        int best = -1;
        float bestDamage = -1.0F;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (s.getItem() instanceof SwordItem sword && sword.getAttackDamage() > bestDamage) {
                bestDamage = sword.getAttackDamage();
                best = i;
            }
        }
        return best;
    }

    private static double distanceToBox(Vec3d point, Box box) {
        double x = MathHelper.clamp(point.x, box.minX, box.maxX);
        double y = MathHelper.clamp(point.y, box.minY, box.maxY);
        double z = MathHelper.clamp(point.z, box.minZ, box.maxZ);
        return point.distanceTo(new Vec3d(x, y, z));
    }

    /** Ends the hunt and puts the hotbar slot back. */
    private static void release(MinecraftClient mc) {
        target = null;
        lostSight = 0;
        smoothedTargetVel = Vec3d.ZERO;
        if (returnSlot >= 0 && mc != null && mc.player != null) {
            mc.player.getInventory().selectedSlot = returnSlot;
        }
        returnSlot = -1;
    }
}
