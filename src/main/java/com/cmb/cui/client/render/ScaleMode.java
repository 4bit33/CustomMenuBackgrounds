package com.cmb.cui.client.render;

/**
 * How a static/animated image or video should be fitted into the window.
 */
public enum ScaleMode {
    FILL,
    FIT,
    STRETCH,
    CENTER;

    public static ScaleMode fromStringSafe(String name) {
        if (name == null) {
            return FILL;
        }
        try {
            return ScaleMode.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FILL;
        }
    }

    /**
     * Computes the destination rectangle (in screen pixels) that the source
     * image of the given dimensions should be drawn into, for a screen of
     * size screenWidth x screenHeight, following this scale mode.
     */
    public Rect compute(int screenWidth, int screenHeight, int sourceWidth, int sourceHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return new Rect(0, 0, screenWidth, screenHeight);
        }

        double screenAspect = (double) screenWidth / screenHeight;
        double sourceAspect = (double) sourceWidth / sourceHeight;

        switch (this) {
            case STRETCH:
                return new Rect(0, 0, screenWidth, screenHeight);
            case CENTER: {
                return new Rect((screenWidth - sourceWidth) / 2, (screenHeight - sourceHeight) / 2, sourceWidth, sourceHeight);
            }
            case FIT: {
                if (sourceAspect > screenAspect) {
                    int h = (int) Math.round(screenWidth / sourceAspect);
                    return new Rect(0, (screenHeight - h) / 2, screenWidth, h);
                } else {
                    int w = (int) Math.round(screenHeight * sourceAspect);
                    return new Rect((screenWidth - w) / 2, 0, w, screenHeight);
                }
            }
            case FILL:
            default: {
                if (sourceAspect > screenAspect) {
                    int w = (int) Math.round(screenHeight * sourceAspect);
                    return new Rect((screenWidth - w) / 2, 0, w, screenHeight);
                } else {
                    int h = (int) Math.round(screenWidth / sourceAspect);
                    return new Rect(0, (screenHeight - h) / 2, screenWidth, h);
                }
            }
        }
    }

    public record Rect(int x, int y, int width, int height) {
    }
}
