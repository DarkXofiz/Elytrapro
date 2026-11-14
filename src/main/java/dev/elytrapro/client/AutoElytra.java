package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import dev.elytrapro.util.ElytraUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public final class AutoElytra {
    private static int airTicks;
    private static boolean attemptedThisPress;
    private static boolean wasFlying;

    private AutoElytra() {}

    /**
     * Runs every client tick. Inside a cobweb only: opens the wings ONE time per space press (no retries,
     * no packet spam) and never sends a "stop gliding" packet. Announces open/close with an actionbar
     * message + sound (client-side only, not sent to the server).
     */
    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ElytraProConfig cfg = ElytraProConfig.get();
        if (p == null || mc.world == null) { airTicks = 0; attemptedThisPress = false; wasFlying = false; return; }

        // queued "take off + rocket" from a right click
        AutoFirework.tick(mc);

        boolean flyingNow = p.isFallFlying();
        if (flyingNow != wasFlying) {
            announce(mc, p, flyingNow);
            wasFlying = flyingNow;
        }

        if (!cfg.autoElytra) return;

        boolean jump = mc.options.jumpKey.isPressed() && mc.currentScreen == null;
        if (!jump) attemptedThisPress = false;

        airTicks = (p.isOnGround() || p.isTouchingWater()) ? 0 : airTicks + 1;

        if (flyingNow) {
            // wings are open (by us or by vanilla): this press is done
            if (jump) attemptedThisPress = true;
            return;
        }

        // Only inside a cobweb. Everywhere else space stays vanilla.
        if (!ElytraUtil.inCobweb(p)) return;

        if (!jump || attemptedThisPress) return;
        if (airTicks < cfg.minAirTicks) return;
        if (!ElytraUtil.canStartNow(p)) return; // still on the ground: wait inside the same press

        attemptedThisPress = true; // exactly one attempt per press
        if (ElytraUtil.tryStartGliding(p)) {
            AutoWebEscapeHit.onWebEscapeGlideStart(mc);
        }
    }

    private static void announce(MinecraftClient mc, ClientPlayerEntity p, boolean opened) {
        p.sendMessage(Text.literal(opened ? "Açıldı" : "Kapandı"), true); // actionbar, not chat
        net.minecraft.sound.SoundEvent sound = opened
                ? SoundEvents.ITEM_ARMOR_EQUIP_ELYTRA.value()
                : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value();
        mc.getSoundManager().play(new net.minecraft.client.sound.PositionedSoundInstance(
                sound, SoundCategory.PLAYERS, 1.0F, opened ? 1.6F : 0.8F,
                p.getRandom(), p.getX(), p.getY(), p.getZ()));
    }
}
