package dev.elytrapro.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

public final class ElytraUtil {
    private ElytraUtil() {}

    public static boolean hasUsableElytra(ClientPlayerEntity p) {
        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        return chest.isOf(Items.ELYTRA) && ElytraItem.isUsable(chest);
    }

    /** Starts gliding exactly like vanilla does (local state + server packet). */
    public static boolean tryStartGliding(ClientPlayerEntity p) {
        if (p.isFallFlying() || p.isOnGround() || p.hasVehicle() || p.isClimbing()
                || p.getAbilities().flying || p.isSpectator() || !hasUsableElytra(p)) {
            return false;
        }
        if (p.checkFallFlying()) {
            p.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return true;
        }
        return false;
    }
}
