package com.halo.decomp;

/** Positions in the overlay's resolution-independent 960 x 540 safe area. */
final class TouchLayout {
    static final int LEFT = 16, RIGHT = 17;
    private static final float[][] DEFAULTS = {
        {856,447}, {913,377}, {794,377}, {850,312}, {915,239}, {802,239},
        {236,449}, {691,449}, {360,490}, {438,490}, {570,36}, {390,36},
        {100,237}, {100,335}, {51,286}, {149,286}, {115,440}, {570,414}
    };
    private static final float[] RADII = {
        36,32,34,32,39,35,32,32,27,29,28,28,25,25,25,25,64,64
    };
    private final float[][] positions = new float[DEFAULTS.length][2];

    TouchLayout() {
        for (int i = 0; i < positions.length; i++) {
            positions[i][0] = DEFAULTS[i][0];
            positions[i][1] = DEFAULTS[i][1];
        }
    }
    int size() { return positions.length; }
    float x(int control) { return positions[control][0]; }
    float y(int control) { return positions[control][1]; }
    float radius(int control) { return RADII[control]; }

    void move(int control, float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) return;
        float radius = radius(control);
        positions[control][0] = Math.max(radius, Math.min(960-radius, x));
        // Keep moved controls below the fixed editor toolbar.
        positions[control][1] = Math.max(76+radius, Math.min(540-radius, y));
    }

    void restore(int control, float x, float y) {
        if (Float.isFinite(x) && Float.isFinite(y) && x >= radius(control)
                && x <= 960-radius(control) && y >= radius(control) && y <= 540-radius(control)) {
            positions[control][0] = x;
            positions[control][1] = y;
        }
    }
}
