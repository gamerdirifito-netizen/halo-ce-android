package com.halo.decomp;

/** Standalone geometry/persistence regression checks; no phone required. */
public final class TouchLayoutTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        TouchLayout layout = new TouchLayout();
        check(layout.size() == 18, "All 16 buttons and both sticks must be editable");
        check(layout.x(TouchLayout.LEFT) == 115 && layout.y(TouchLayout.RIGHT) == 414,
              "Existing layouts must start with the original controls");
        layout.move(TouchLayout.LEFT, -500, -500);
        check(layout.x(TouchLayout.LEFT) >= 64 && layout.y(TouchLayout.LEFT) >= 140,
              "Dragging must keep the full stick inside the screen and below the toolbar");
        layout.move(TouchLayout.RIGHT, 10000, 10000);
        check(layout.x(TouchLayout.RIGHT)+64 <= 960 && layout.y(TouchLayout.RIGHT)+64 <= 540,
              "Dragging beyond the bottom/right edge must keep the stick reachable");
        layout.move(0, 230, 320);
        layout.move(5, 440, 390);
        TouchLayout reopened = new TouchLayout();
        for (int i = 0; i < layout.size(); i++) reopened.restore(i, layout.x(i), layout.y(i));
        for (int i = 0; i < layout.size(); i++) {
            check(layout.x(i) == reopened.x(i) && layout.y(i) == reopened.y(i),
                  "Saved coordinates must round-trip for control "+i);
        }
        float before = reopened.x(0);
        reopened.restore(0, Float.NaN, 200);
        reopened.restore(0, Float.POSITIVE_INFINITY, 200);
        reopened.restore(0, -20, 200);
        check(reopened.x(0) == before, "Invalid saved coordinates must not corrupt the layout");
        reopened.move(0, Float.NaN, 200);
        check(reopened.x(0) == before, "Invalid drag coordinates must not corrupt the layout");
        // A saved top-row Pause button is valid even before it has been moved.
        check(reopened.y(10) == 36, "Reopening must preserve unmoved top-row controls");
        System.out.println("Touch layout checks passed");
    }
}
