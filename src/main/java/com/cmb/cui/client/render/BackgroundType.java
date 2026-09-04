package com.cmb.cui.client.render;

/**
 * The kind of background the title screen should render.
 */
public enum BackgroundType {
    VANILLA,
    STATIC_IMAGE,
    GIF,
    VIDEO,
    PANORAMA;

    public static BackgroundType fromStringSafe(String name) {
        if (name == null) {
            return VANILLA;
        }
        try {
            return BackgroundType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return VANILLA;
        }
    }
}
