package com.cmb.custommenubackgrounds.mixin;

import com.cmb.custommenubackgrounds.CustomMenuBackgroundsClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.sound.MusicSound;
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
     * Intercepts MusicTracker.tick() — if we're on the title screen and
     * custom music is enabled + playing, skip the entire vanilla tick so
     * it never starts a competing track.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cmb$suppressVanillaMusicOnTitleScreen(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof TitleScreen) {
            if (CustomMenuBackgroundsClient.isCustomMusicActive()) {
                ci.cancel();
            }
        }
    }
}
