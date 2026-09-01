package com.cmb.custommenubackgrounds.background;

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
                int w = sourceWidth;
                int h = sourceHeight;
                int x = (screenWidth - w) / 2;
                int y = (screenHeight - h) / 2;
                return new Rect(x, y, w, h);
            }
            case FIT: {
                if (sourceAspect > screenAspect) {
                    int w = screenWidth;
                    int h = (int) Math.round(screenWidth / sourceAspect);
                    return new Rect(0, (screenHeight - h) / 2, w, h);
                } else {
                    int h = screenHeight;
                    int w = (int) Math.round(screenHeight * sourceAspect);
                    return new Rect((screenWidth - w) / 2, 0, w, h);
                }
            }
            case FILL:
            default: {
                if (sourceAspect > screenAspect) {
                    int h = screenHeight;
                    int w = (int) Math.round(screenHeight * sourceAspect);
                    return new Rect((screenWidth - w) / 2, 0, w, h);
                } else {
                    int w = screenWidth;
                    int h = (int) Math.round(screenWidth / sourceAspect);
                    return new Rect(0, (screenHeight - h) / 2, w, h);
                }
            }
        }
    }

    public record Rect(int x, int y, int width, int height) {
    }
}
