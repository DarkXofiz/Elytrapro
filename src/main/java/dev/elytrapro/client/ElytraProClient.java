package dev.elytrapro.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ElytraProClient implements ClientModInitializer {
    public static final String MOD_ID = "elytrapro";
    /** Used for real errors only. */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding flyHitKey;
    private static KeyBinding webEscapeHitKey;

    @Override
    public void onInitializeClient() {
        flyHitKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.elytrapro.flyhit", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.elytrapro"));
        webEscapeHitKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.elytrapro.webescapehit", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_1, "key.categories.elytrapro"));

        ClientTickEvents.END_CLIENT_TICK.register(AutoElytra::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (flyHitKey.wasPressed()) {
                boolean on = AutoFlyHit.toggle();
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Fly Hit: " + (on ? "ON" : "OFF")), true);
                }
            }
            try {
                AutoFlyHit.tick(client);
            } catch (Throwable t) {
                LOGGER.error("Fly Hit error", t);
            }
            // Left Alt arms a one-shot: next web-escape glide-start fires a rocket + one sweeping-sword hit.
            if (webEscapeHitKey.wasPressed()) {
                AutoWebEscapeHit.arm();
            }
            try {
                AutoWebEscapeHit.tick(client);
            } catch (Throwable t) {
                LOGGER.error("Web Escape Hit error", t);
            }
        });
    }
}
