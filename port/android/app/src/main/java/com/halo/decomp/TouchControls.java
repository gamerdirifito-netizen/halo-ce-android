package com.halo.decomp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

/** Multitouch controller overlay. Coordinates use a 960 x 540 safe area. */
public final class TouchControls extends View {
    private static final int LEFT = TouchLayout.LEFT, RIGHT = TouchLayout.RIGHT;
    private static final int TOGGLE = -3, EDIT = -4;
    private static final class Button {
        final String label;
        final float radius;
        final int bit, trigger;
        Button(String label, float radius, int bit, int trigger) {
            this.label = label; this.radius = radius;
            this.bit = bit; this.trigger = trigger;
        }
    }
    // Button bits follow SDL_GamepadButton; triggers are SDL axes 4 and 5.
    private final Button[] buttons = {
        new Button("A / Jump", 36, 0, -1),
        new Button("B / Melee", 32, 1, -1),
        new Button("X / Reload", 34, 2, -1),
        new Button("Y / Weapon", 32, 3, -1),
        new Button("Fire", 39, -1, 5),
        new Button("Grenade", 35, -1, 4),
        new Button("Crouch", 32, 7, -1),
        new Button("Zoom", 32, 8, -1),
        new Button("Light", 27, 9, -1),
        new Button("Gren. type", 29, 10, -1),
        new Button("Pause", 28, 6, -1),
        new Button("Back", 28, 4, -1),
        new Button("Up", 25, 11, -1),
        new Button("Down", 25, 12, -1),
        new Button("Left", 25, 13, -1),
        new Button("Right", 25, 14, -1)
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SparseIntArray owners = new SparseIntArray();
    private final int[] axes = new int[6];
    private final TouchLayout layout = new TouchLayout();
    private final SharedPreferences preferences;
    private boolean editing;
    private int dragPointer = -1, dragControl = -1;
    private float dragOffsetX, dragOffsetY;
    private float scale = 1, offsetX, offsetY;
    private int insetLeft, insetRight, insetTop, insetBottom;
    private boolean visible = true;

    private static native void nativeState(int lx, int ly, int rx, int ry,
                                          int lt, int rt, int buttons);

    public TouchControls(Context context) {
        super(context);
        preferences = context.getSharedPreferences("touch-layout-v1", Context.MODE_PRIVATE);
        for (int i = 0; i < layout.size(); i++)
            layout.restore(i, preferences.getFloat("x"+i, layout.x(i)),
                              preferences.getFloat("y"+i, layout.y(i)));
        setFocusable(false);
        setContentDescription("Halo touch controller");
        setOnApplyWindowInsetsListener((view, insets) -> {
            insetLeft = insets.getSystemWindowInsetLeft();
            insetRight = insets.getSystemWindowInsetRight();
            insetTop = insets.getSystemWindowInsetTop();
            insetBottom = insets.getSystemWindowInsetBottom();
            if (android.os.Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                insetLeft = Math.max(insetLeft, insets.getDisplayCutout().getSafeInsetLeft());
                insetRight = Math.max(insetRight, insets.getDisplayCutout().getSafeInsetRight());
                insetTop = Math.max(insetTop, insets.getDisplayCutout().getSafeInsetTop());
                insetBottom = Math.max(insetBottom, insets.getDisplayCutout().getSafeInsetBottom());
            }
            layoutControls();
            return insets;
        });
    }

    private void layoutControls() {
        reset();
        float width = getWidth() - insetLeft - insetRight;
        float height = getHeight() - insetTop - insetBottom;
        scale = Math.max(0.01f, Math.min(width / 960f, height / 540f));
        offsetX = insetLeft + (width - 960 * scale) / 2;
        offsetY = insetTop + (height - 540 * scale) / 2;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        layoutControls();
        requestApplyInsets();
    }

    public void reset() {
        dragPointer = dragControl = -1;
        owners.clear();
        java.util.Arrays.fill(axes, 0);
        publish();
        invalidate();
    }

    private void publish() {
        if (editing) {
            nativeState(0, 0, 0, 0, 0, 0, 0);
            return;
        }
        int bits = 0;
        axes[4] = axes[5] = 0;
        for (int i = 0; i < owners.size(); i++) {
            int control = owners.valueAt(i);
            if (control < 0 || control >= buttons.length) continue;
            Button b = buttons[control];
            if (b.bit >= 0) bits |= 1 << b.bit;
            if (b.trigger >= 0) axes[b.trigger] = 32767;
        }
        nativeState(axes[0], axes[1], axes[2], axes[3], axes[4], axes[5], bits);
    }

    private boolean held(int control) {
        return owners.indexOfValue(control) >= 0;
    }

    private static boolean inside(float x, float y, float cx, float cy, float radius) {
        return (x-cx)*(x-cx) + (y-cy)*(y-cy) <= radius*radius;
    }

    private int hit(float x, float y) {
        if (x >= 650 && x <= 860 && y >= 8 && y <= 64) return EDIT;
        if (inside(x, y, 480, 36, 30)) return TOGGLE;
        if (!visible) return Integer.MIN_VALUE;
        for (int i = 0; i < buttons.length; i++) {
            Button b = buttons[i];
            if (inside(x, y, layout.x(i), layout.y(i), b.radius)) return i;
        }
        if (!held(LEFT) && inside(x, y, layout.x(LEFT), layout.y(LEFT), 82)) return LEFT;
        if (!held(RIGHT) && inside(x, y, layout.x(RIGHT), layout.y(RIGHT), 92)) return RIGHT;
        return Integer.MIN_VALUE;
    }

    private void moveStick(int control, float x, float y) {
        int axis = control == LEFT ? 0 : 2;
        float dx = (x - layout.x(control)) / 64f;
        float dy = (y - layout.y(control)) / 64f;
        float length = (float)Math.sqrt(dx*dx + dy*dy);
        if (length < 0.12f) { dx = 0; dy = 0; }
        else if (length > 1) { dx /= length; dy /= length; }
        axes[axis] = Math.round(dx * 32767);
        axes[axis+1] = Math.round(dy * 32767);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked(), index = event.getActionIndex();
        int id = event.getPointerId(index);
        float x = (event.getX(index)-offsetX)/scale, y = (event.getY(index)-offsetY)/scale;
        if (editing) return editTouch(event, action, index, id, x, y);
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int control = hit(x, y);
            if (control == EDIT) {
                reset(); editing = true; visible = true; performClick();
            } else if (control == TOGGLE) {
                reset(); visible = !visible; performClick();
            } else if (control != Integer.MIN_VALUE) {
                owners.put(id, control);
                if (control == LEFT || control == RIGHT) moveStick(control, x, y);
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int control = owners.get(event.getPointerId(i), Integer.MIN_VALUE);
                if (control == LEFT || control == RIGHT)
                    moveStick(control, (event.getX(i)-offsetX)/scale, (event.getY(i)-offsetY)/scale);
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int control = owners.get(id, Integer.MIN_VALUE);
            if (control == LEFT || control == RIGHT) {
                int axis = control == LEFT ? 0 : 2;
                axes[axis] = axes[axis+1] = 0;
            }
            owners.delete(id);
        } else if (action == MotionEvent.ACTION_CANCEL) {
            reset();
        }
        publish(); invalidate();
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private boolean editTouch(MotionEvent event, int action, int index, int id, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (dragPointer < 0) {
                int control = hit(x, y);
                if (control == EDIT) {
                    SharedPreferences.Editor saved = preferences.edit();
                    for (int i = 0; i < layout.size(); i++)
                        saved.putFloat("x"+i, layout.x(i)).putFloat("y"+i, layout.y(i));
                    if (saved.commit()) {
                        reset(); editing = false;
                        performClick();
                    } else {
                        Toast.makeText(getContext(), "Could not save layout. Try again.", Toast.LENGTH_LONG).show();
                    }
                } else if (control >= 0) {
                    dragPointer = id; dragControl = control;
                    dragOffsetX = x-layout.x(control); dragOffsetY = y-layout.y(control);
                }
            }
        } else if (action == MotionEvent.ACTION_MOVE && dragPointer >= 0) {
            int pointer = event.findPointerIndex(dragPointer);
            if (pointer >= 0)
                layout.move(dragControl, (event.getX(pointer)-offsetX)/scale-dragOffsetX,
                                        (event.getY(pointer)-offsetY)/scale-dragOffsetY);
        } else if (action == MotionEvent.ACTION_CANCEL ||
                   ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && id == dragPointer)) {
            dragPointer = dragControl = -1;
        }
        publish(); invalidate();
        return true;
    }

    private void editorButton(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(editing ? 0xbb2b694b : 0x88304050);
        canvas.drawRoundRect(650, 8, 860, 64, 12, 12, paint);
        paint.setColor(Color.WHITE); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(18);
        canvas.drawText(editing ? "Save and exit" : "Edit", 755, 42, paint);
        if (editing) {
            paint.setTextSize(14);
            canvas.drawText("Drag buttons and sticks, then Save and exit", 480, 94, paint);
        }
    }

    private void circle(Canvas canvas, float x, float y, float radius, String label, boolean active) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? 0x9983d9ff : 0x55304050);
        canvas.drawCircle(x, y, radius, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2);
        paint.setColor(active ? 0xffaee7ff : 0x99ffffff);
        canvas.drawCircle(x, y, radius, paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(11);
        canvas.drawText(label, x, y + 4, paint);
    }

    private void stick(Canvas canvas, int axis, float x, float y, String label) {
        circle(canvas, x, y, 64, label, false);
        circle(canvas, x + axes[axis]/32767f*64, y + axes[axis+1]/32767f*64,
               24, "", held(axis == 0 ? LEFT : RIGHT));
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.save(); canvas.translate(offsetX, offsetY); canvas.scale(scale, scale);
        if (!editing) circle(canvas, 480, 36, 28, visible ? "Hide" : "Touch", false);
        if (visible) {
            stick(canvas, 0, layout.x(LEFT), layout.y(LEFT), "Move");
            stick(canvas, 2, layout.x(RIGHT), layout.y(RIGHT), "Look");
            for (int i = 0; i < buttons.length; i++) {
                Button b = buttons[i];
                circle(canvas, layout.x(i), layout.y(i), b.radius, b.label, held(i) || dragControl == i);
            }
        }
        editorButton(canvas);
        canvas.restore();
    }
}
