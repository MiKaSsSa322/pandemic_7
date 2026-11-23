package com.pandemic7;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PandemicMod.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END) ClientEffects.tick(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent e) {
        ClientEffects.onInput(e.getInput());
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        double off = ClientEffects.fovOffset();
        if (off != 0.0) e.setFOV(e.getFOV() + off);
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        float roll = ClientEffects.roll();
        if (roll != 0f) e.setRoll(e.getRoll() + roll);
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        if (!ClientState.has(Disease.NUMBNESS)) return;
        if (e.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()
                || e.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()
                || e.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type()) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientState.clear();
        ClientEffects.reset();
    }
}
