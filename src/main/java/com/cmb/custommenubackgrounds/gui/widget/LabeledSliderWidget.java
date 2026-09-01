package com.cmb.custommenubackgrounds.gui.widget;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * A vanilla {@link SliderWidget} that maps its internal 0..1 double onto an
 * arbitrary [min, max] float range and formats its own label, so every
 * numeric setting in {@link com.cmb.custommenubackgrounds.gui.ModSettingsScreen}
 * doesn't need its own subclass.
 */
public class LabeledSliderWidget extends SliderWidget {

    private final float min;
    private final float max;
    private final BiFunction<String, Float, Text> labelFormatter;
    private final Consumer<Float> onChange;

    public LabeledSliderWidget(int x, int y, int width, int height,
                                float min, float max, float initialValue,
                                BiFunction<String, Float, Text> labelFormatter,
                                Consumer<Float> onChange) {
        super(x, y, width, height, Text.empty(), normalize(initialValue, min, max));
        this.min = min;
        this.max = max;
        this.labelFormatter = labelFormatter;
        this.onChange = onChange;
        updateMessage();
    }

    private static double normalize(float value, float min, float max) {
        return (value - min) / (max - min);
    }

    public float currentValue() {
        return (float) (min + value * (max - min));
    }

    @Override
    protected void updateMessage() {
        setMessage(labelFormatter.apply("", currentValue()));
    }

    @Override
    protected void applyValue() {
        onChange.accept(currentValue());
    }
}
