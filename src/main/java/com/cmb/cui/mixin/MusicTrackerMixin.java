package com.cmb.cui.mixin;

import com.cmb.cui.client.CUIClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MusicTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla's music system from starting/ticking menu music
 * while our own custom OGG track is playing.  Vanilla's MusicTracker.tick()
 * is called every client tick and will attempt to start menu music whenever
 * the player is on the title screen and nothing is currently playing through
 * the vanilla sound manager.  Without this mixin, vanilla music keeps
 * restarting over our custom track.
 */
@Mixin(MusicTracker.class)
public abstract class MusicTrackerMixin {

    /**
     * Suppress vanilla music whenever CUI custom music is active.
     * Previous version only checked TitleScreen, but user expects custom
     * to play across ALL menu screens (settings, etc.). We now suppress
     * whenever custom is active and we are still in a menu (world == null).
     * The ClientTick handler in {@link CUIClient} keeps
     * custom playing across menus and stops it when entering a world.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cmb$suppressVanillaMusicOnTitleScreen(CallbackInfo ci) {
        if (CUIClient.isCustomMusicActive()) {
            MinecraftClient client = MinecraftClient.getInstance();
            // Only suppress in menus; in-game let vanilla handle music normally
            // (custom is stopped on world join anyway)
            if (client == null || client.world == null) {
                ci.cancel();
            }
        }
    }
}
