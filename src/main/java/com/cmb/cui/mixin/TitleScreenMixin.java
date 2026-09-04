package com.cmb.cui.mixin;

import com.cmb.cui.client.CUIClient;
import com.cmb.cui.client.render.BackgroundManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Title-screen lifecycle for CUI: on first render ensure the background
 * renderer exists and (re)start custom music. Background drawing itself is
 * handled for ALL menu screens by {@code ScreenPanoramaMixin} via
 * {@code Screen#renderPanoramaBackground} — TitleScreen no longer calls
 * {@code RotatingCubeMapRenderer} directly in 1.21.11, so no redirect here.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends net.minecraft.client.gui.screen.Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Unique
    private boolean cmb$announcedOpen = false;

    // NOTE per spec §10: NO extra button on title screen — entry is ModMenu → CUI → Configure only.
    // Previous cmb$addSettingsButton injection has been removed deliberately.

    @Inject(method = "render", at = @At("HEAD"))
    private void cmb$onRenderHead(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!cmb$announcedOpen) {
            cmb$announcedOpen = true;
            BackgroundManager.get().onMenuOpened();
            // Always refresh music when (re-)entering the title screen
            CUIClient.refreshMusic();
        }
        // Background itself (tick + draw, on ALL menu screens) is handled by
        // ScreenPanoramaMixin via renderPanoramaBackground — not here, to avoid
        // double-drawing on the title.
    }

    /**
     * Screen#removed() fires when Minecraft is about to replace this screen
     * with another one (e.g. entering a world, opening a different menu).
     * We intentionally do NOT free background resources here: the custom
     * background stays alive across menu-to-menu transitions (Title → Settings
     * etc.) so every menu shows it. GPU/decoder resources are freed on world
     * join (see {@link CUIClient} tick handler). Music is likewise kept across
     * menus and stopped only on world join or when disabled.
     */
    @Inject(method = "removed", at = @At("HEAD"))
    private void cmb$onRemoved(CallbackInfo ci) {
        cmb$announcedOpen = false;
    }
}
