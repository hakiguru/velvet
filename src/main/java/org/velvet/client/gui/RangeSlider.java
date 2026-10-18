package org.velvet.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/** A slider over a number range in fixed steps, reading and writing the value through callbacks. */
public class RangeSlider extends AbstractSliderButton {

    private final double min, max, step;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final DoubleFunction<Component> label;

    public RangeSlider(int x, int y, int width, int height, double min, double max, double step,
                       DoubleSupplier getter, DoubleConsumer setter, DoubleFunction<Component> label) {
        super(x, y, width, height, Component.empty(), 0);
        this.min = min;
        this.max = max;
        this.step = step;
        this.getter = getter;
        this.setter = setter;
        this.label = label;
        sync();
    }

    /** Moves the handle to the current value (after it changed elsewhere). */
    public void sync() {
        this.value = Mth.clamp((getter.getAsDouble() - min) / (max - min), 0, 1);
        updateMessage();
    }

    private double current() {
        double v = min + value * (max - min);
        return Mth.clamp(Math.round(v / step) * step, min, max);
    }

    @Override
    protected void updateMessage() {
        setMessage(label.apply(current()));
    }

    @Override
    protected void applyValue() {
        setter.accept(current());
    }
}
