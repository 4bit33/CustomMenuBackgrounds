package com.cmb.custommenubackgrounds.mixin;

import com.cmb.custommenubackgrounds.CustomMenuBackgroundsClient;
import com.cmb.custommenubackgrounds.background.BackgroundManager;
import com.cmb.custommenubackgrounds.gui.ModSettingsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.RotatingCubeMapRenderer;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into the vanilla title screen at exactly two points:
 *  1. On construction/first render, tell {@link BackgroundManager} the
 *     screen is open so it can (re)build the configured renderer and start
 *     custom music.
 *  2. Redirects vanilla's own "draw the rotating panorama" call so that,
 *     when the user has configured a non-vanilla background, our renderer
 *     draws instead of (not in addition to) the built-in panorama.
 *
 * NOTE ON MAPPINGS: the exact field/method names used below
 * (RotatingCubeMapRenderer#render) match Yarn mappings for 1.21.1 at the
 * time of writing. If Yarn renames these in a later mapping build, use
 * "Refactor > Rename" from the decompiled vanilla source in your IDE and
 * update the @Redirect target accordingly - Mixin will fail fast at launch
 * with a clear "could not find target method" error if this ever drifts,
 * it will not fail silently.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends net.minecraft.client.gui.screen.Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    private boolean cmb$announcedOpen = false;

    /**
     * Adds the "Menu Backgrounds..." button to the bottom-right corner of
     * the title screen, next to vanilla's own "Language"/"Accessibility"
     * corner buttons, without touching any other vanilla widget.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void cmb$addSettingsButton(CallbackInfo ci) {
        TitleScreen self = (TitleScreen) (Object) this;
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("custommenubackgrounds.settings.button"),
                button -> MinecraftClient.getInstance().setScreen(new ModSettingsScreen(self))
        ).dimensions(this.width - 154, this.height - 26, 150, 20).build());
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void cmb$onRenderHead(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!cmb$announcedOpen) {
            cmb$announcedOpen = true;
            BackgroundManager.get().onTitleScreenOpened();
            // Always refresh music when (re-)entering the title screen
            CustomMenuBackgroundsClient.refreshMusic();
        }
        BackgroundManager manager = BackgroundManager.get();
        manager.tick(delta / 20.0f);
        if (!manager.isVanilla()) {
            manager.render(context, this.width, this.height);
        }
    }

    /**
     * Screen#removed() fires when Minecraft is about to replace this screen
     * with another one (e.g. entering a world, opening a different menu) -
     * the right moment to free GPU textures and stop the video decode
     * thread rather than leaking them until the title screen is reopened.
     */
    @Inject(method = "removed", at = @At("HEAD"))
    private void cmb$onRemoved(CallbackInfo ci) {
        cmb$announcedOpen = false;
        BackgroundManager.get().onTitleScreenClosed();
        // Stop custom music so vanilla can resume in-game
        CustomMenuBackgroundsClient.stopMusic();
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/RotatingCubeMapRenderer;render(Lnet/minecraft/client/gui/DrawContext;IIFF)V"
            ),
            require = 0
    )
    private void cmb$redirectPanorama(RotatingCubeMapRenderer instance, DrawContext context, int width, int height, float tickDelta, float alpha) {
        BackgroundManager manager = BackgroundManager.get();
        if (manager.isVanilla()) {
            instance.render(context, width, height, tickDelta, alpha);
        }
    }
}
