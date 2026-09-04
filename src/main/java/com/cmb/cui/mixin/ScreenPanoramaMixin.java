package com.cmb.cui.mixin;

import com.cmb.cui.client.render.BackgroundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11: vanilla draws its rotating panorama via
 * {@code Screen#renderPanoramaBackground}, which every menu screen
 * (title, settings, mod list, ...) calls from {@code renderBackground}
 * while {@code world == null}. TitleScreen calls it directly.
 *
 * <p>When CUI has a non-vanilla background configured, this replaces the
 * vanilla panorama with our own renderer on <b>all</b> menu screens (not
 * just the title) and cancels the vanilla cubemap draw. Vanilla blur +
 * darkening in {@code renderBackground} still run on top on submenus,
 * matching the vanilla submenu look. In-game screens ({@code world != null})
 * are untouched.</p>
 */
@Mixin(Screen.class)
public abstract class ScreenPanoramaMixin {

    @Shadow public int width;
    @Shadow public int height;

    @Unique
    private static long cmb$lastTime = -1;

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void cmb$replacePanorama(DrawContext context, float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world != null) {
            return; // in-game: leave vanilla alone
        }
        BackgroundManager manager = BackgroundManager.get();
        if (manager.isVanilla()) {
            // Lazily (re)build, e.g. after world-join freed resources
            manager.onMenuOpened();
            if (manager.isVanilla()) {
                return; // config is vanilla -> vanilla panorama draws
            }
            cmb$lastTime = System.nanoTime(); // reset timer on fresh open
        }

        long now = System.nanoTime();
        if (cmb$lastTime == -1) cmb$lastTime = now;
        float deltaSeconds = (now - cmb$lastTime) / 1_000_000_000.0f;
        cmb$lastTime = now;
        if (deltaSeconds > 0.2f) deltaSeconds = 0.016f; // cap jumps (e.g. after window focus)

        manager.tick(deltaSeconds);
        manager.render(context, this.width, this.height);
        ci.cancel();
    }
}
