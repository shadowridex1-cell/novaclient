package dev.nova;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.lwjgl.glfw.GLFW;

public class NovaClient implements ClientModInitializer {
    static final KeyMapping.Category CAT =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nova", "main"));

    static KeyMapping oreKey, baseKey, stashKey, fbKey, sprintKey, reportKey;
    static boolean fullbright, autoSprint;

    static KeyMapping key(String id, int code) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nova." + id, InputConstants.Type.KEYSYM, code, CAT));
    }

    @Override
    public void onInitializeClient() {
        oreKey    = key("ore",        GLFW.GLFW_KEY_KP_1);
        baseKey   = key("base",       GLFW.GLFW_KEY_KP_2);
        stashKey  = key("stash",      GLFW.GLFW_KEY_KP_3);
        fbKey     = key("fullbright", GLFW.GLFW_KEY_KP_4);
        sprintKey = key("sprint",     GLFW.GLFW_KEY_KP_5);
        reportKey = key("report",     GLFW.GLFW_KEY_KP_0);

        ClientTickEvents.END_CLIENT_TICK.register(NovaClient::tick);
    }

    static void tick(Minecraft mc) {
        if (mc.player == null) return;

        while (oreKey.consumeClick())   { Finder.ore = !Finder.ore;     msg(mc, "Ore Finder", Finder.ore); }
        while (baseKey.consumeClick())  { Finder.base = !Finder.base;   msg(mc, "Base Finder", Finder.base); }
        while (stashKey.consumeClick()) { Finder.stash = !Finder.stash; msg(mc, "Stash Finder", Finder.stash); }
        while (sprintKey.consumeClick()){ autoSprint = !autoSprint;     msg(mc, "Auto Sprint", autoSprint); }
        while (reportKey.consumeClick()) Finder.report(mc);
        while (fbKey.consumeClick()) {
            fullbright = !fullbright;
            msg(mc, "Fullbright", fullbright);
            if (!fullbright) mc.player.removeEffect(MobEffects.NIGHT_VISION);
        }

        // Fullbright (night vision doar pe client)
        if (fullbright && (mc.player.tickCount % 100 == 0 || !mc.player.hasEffect(MobEffects.NIGHT_VISION)))
            mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1000, 0, false, false));

        // Auto Sprint
        if (autoSprint && mc.options.keyUp.isDown() && !mc.player.horizontalCollision && !mc.player.isShiftKeyDown())
            mc.player.setSprinting(true);

        Finder.tick(mc);
    }

    static void msg(Minecraft mc, String name, boolean on) {
        mc.player.displayClientMessage(Component.literal("§a[Nova] §f" + name + ": " + (on ? "§aON" : "§cOFF")), true);
    }
}
