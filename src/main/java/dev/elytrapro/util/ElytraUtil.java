package dev.elytrapro.util;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.BlockPos;

public final class ElytraUtil {
    private ElytraUtil() {}

    public static boolean hasUsableElytra(ClientPlayerEntity p) {
        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        return chest.isOf(Items.ELYTRA) && ElytraItem.isUsable(chest);
    }

    /** True if the player's feet/body are inside a cobweb. */
    public static boolean inCobweb(ClientPlayerEntity p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        BlockPos pos = p.getBlockPos();
        return mc.world.getBlockState(pos).isOf(Blocks.COBWEB)
                || mc.world.getBlockState(pos.up()).isOf(Blocks.COBWEB);
    }

    /**
     * The server only accepts "start gliding" when the player is airborne AND
     * moving downwards (velocity.y < 0). If we send it while still rising, the
     * server answers with stopFallFlying() and the wings snap shut. That was the
     * reason the wings "did not open / closed again". So we only send it when
     * the server will accept it.
     * Inside a cobweb vanilla zeroes the velocity every tick, so the check is skipped there.
     */
    public static boolean canStartNow(ClientPlayerEntity p) {
        if (p.isFallFlying() || p.isOnGround() || p.hasVehicle() || p.isClimbing()
                || p.getAbilities().flying || p.isSpectator() || p.isTouchingWater()
                || p.hasStatusEffect(StatusEffects.LEVITATION) || !hasUsableElytra(p)) {
            return false;
        }
        return p.getVelocity().y < 0.0 || inCobweb(p);
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
