package dev.elytrapro.util;

import dev.elytrapro.config.ElytraProConfig;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class ElytraUtil {
    private ElytraUtil() {}

    public static boolean hasUsableElytra(ClientPlayerEntity p) {
        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        return chest.isOf(Items.ELYTRA) && ElytraItem.isUsable(chest);
    }

    /** True if any part of the player's body overlaps a cobweb block. */
    public static boolean inCobweb(ClientPlayerEntity p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        Box box = p.getBoundingBox().contract(0.001);
        int x0 = (int) Math.floor(box.minX), x1 = (int) Math.floor(box.maxX);
        int y0 = (int) Math.floor(box.minY), y1 = (int) Math.floor(box.maxY);
        int z0 = (int) Math.floor(box.minZ), z1 = (int) Math.floor(box.maxZ);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    pos.set(x, y, z);
                    if (mc.world.getBlockState(pos).isOf(Blocks.COBWEB)) return true;
                }
            }
        }
        return false;
    }

    /** Same conditions vanilla uses before it sends "start gliding". */
    public static boolean canStartNow(ClientPlayerEntity p) {
        if (p.isFallFlying() || p.isOnGround() || p.hasVehicle() || p.isClimbing()
                || p.getAbilities().flying || p.isSpectator() || p.isTouchingWater()
                || p.hasStatusEffect(StatusEffects.LEVITATION) || !hasUsableElytra(p)) {
            return false;
        }
        if (ElytraProConfig.get().onlyWhenFalling && !inCobweb(p)) {
            // Right after a jump the player is still rising for a few ticks; only
            // require "falling" once the ascent is basically over.
            return p.getVelocity().y < 0.05;
        }
        return true;
    }

    /** Small hop so the player leaves the ground and the wings can open. */
    public static void hop(ClientPlayerEntity p) {
        double jump = 0.42;
        StatusEffectInstance boost = p.getStatusEffect(StatusEffects.JUMP_BOOST);
        if (boost != null) jump += 0.1 * (boost.getAmplifier() + 1);
        Vec3d v = p.getVelocity();
        p.setVelocity(v.x, Math.max(v.y, jump), v.z);
    }

    /** Starts gliding exactly like vanilla does (local state + server packet). */
    public static boolean tryStartGliding(ClientPlayerEntity p) {
        if (!canStartNow(p)) return false;
        if (p.checkFallFlying()) {
            p.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return true;
        }
        return false;
    }
}
