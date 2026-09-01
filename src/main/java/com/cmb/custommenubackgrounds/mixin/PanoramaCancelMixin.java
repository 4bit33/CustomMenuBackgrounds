package com.cmb.custommenubackgrounds.mixin;

import com.cmb.custommenubackgrounds.background.BackgroundManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla draws its own translucent "fade to dark" gradient over the
 * panorama before drawing buttons. When a custom background is active we
 * already apply the user's own overlayOpacity in
 * {@link BackgroundManager#render}, so this redirects vanilla's extra
 * gradient fill to a no-op in that case to avoid double-darkening the
 * screen.
 *
 * As with TitleScreenMixin, the exact target descriptor here
 * (DrawContext#fillGradient) should be double-checked against your local
 * Yarn mappings build - it is the standard vanilla call used to draw the
 * dark gradient behind the title screen buttons.
 */
@Mixin(TitleScreen.class)
public abstract class PanoramaCancelMixin {

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;fillGradient(IIIIII)V"
            ),
            require = 0
    )
    private void cmb$maybeSkipVanillaGradient(DrawContext context, int x1, int y1, int x2, int y2, int colorA, int colorB) {
        if (BackgroundManager.get().isVanilla()) {
            context.fillGradient(x1, y1, x2, y2, colorA, colorB);
        }
        // else: skip vanilla's own darkening, ours was already applied in
        // BackgroundManager#render.
    }
}
