package com.halo.decomp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.libsdl.app.SDLActivity;

/**
 * The game: SDL3's activity, running libmain.so (port/android/host), which
 * loads the game image from the APK's assets.
 */
public class HaloActivity extends SDLActivity {
    /** lets system link's broadcasts in over Wi-Fi while the game runs */
    private WifiManager.MulticastLock multicastLock;
    private TouchControls touchControls;
    private static final int EXPORT_LAYOUT = 401, IMPORT_LAYOUT = 402;
    private String pendingLayoutExport;

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL3", "main" };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null)
            pendingLayoutExport = savedInstanceState.getString("pending-layout-export");
        if (mLayout != null) {
            touchControls = new TouchControls(this);
            mLayout.addView(touchControls, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        preferHighestRefreshRate();
        acquireMulticastLock();
        // a new version looked for while the game starts
        Updater.start(this);
    }

    // ---------- physical pads that SDL does not list
    //
    // A generic Bluetooth pad can reach Android as a gamepad (buttons as
    // KEYCODE_BUTTON_* keys, sticks as joystick motion) while SDL does not
    // list it as a joystick, so the game would see only a few keys. This
    // passes such a pad to the game through the touch controls' state (it is
    // merged there). Pads of Sony, Microsoft and Nintendo are left to SDL.
    // key_log.txt (next to gamepad_log.txt) records the devices and events.

    private FileWriter keyLog;
    private int keyLogLines;
    private long lastMotionLog;
    private boolean devicesListed;

    private void padLog(String line) {
        try {
            if (keyLog == null) {
                File dir = getExternalFilesDir(null);
                if (dir == null) return;
                keyLog = new FileWriter(new File(dir, "key_log.txt"), false);
                keyLog.write("key bridge v1\n");
            }
            if (keyLogLines >= 800) return;
            keyLog.write(line + "\n");
            keyLog.flush();
            keyLogLines++;
        } catch (IOException e) {
            // the log is optional
        }
    }

    private void listInputDevices(String when) {
        padLog("--- android input devices (" + when + ") ---");
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) continue;
            padLog("device " + id + " \"" + device.getName() + "\" sources=0x"
                + Integer.toHexString(device.getSources()) + " vendor=0x" + Integer.toHexString(device.getVendorId())
                + " product=0x" + Integer.toHexString(device.getProductId()));
        }
    }

    /** a pad that SDL handles by itself (Sony, Microsoft, Nintendo) */
    private static boolean sdlPad(InputDevice device) {
        int vendor = device.getVendorId();
        return vendor == 0x054c || vendor == 0x045e || vendor == 0x057e;
    }

    /** the SDL gamepad button number of an Android key, or -1 */
    private static int padBit(int keyCode, boolean hasPadSource) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return 0;
            case KeyEvent.KEYCODE_BUTTON_B: return 1;
            case KeyEvent.KEYCODE_BUTTON_X: return 2;
            case KeyEvent.KEYCODE_BUTTON_Y: return 3;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return 4;
            case KeyEvent.KEYCODE_BUTTON_START: return 6;
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return 7;
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return 8;
            case KeyEvent.KEYCODE_BUTTON_L1: return 9;
            case KeyEvent.KEYCODE_BUTTON_R1: return 10;
            default: break;
        }
        if (hasPadSource) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP: return 11;
                case KeyEvent.KEYCODE_DPAD_DOWN: return 12;
                case KeyEvent.KEYCODE_DPAD_LEFT: return 13;
                case KeyEvent.KEYCODE_DPAD_RIGHT: return 14;
                default: break;
            }
        }
        return -1;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        InputDevice device = event.getDevice();
        if (event.getRepeatCount() == 0) {
            padLog("key " + (event.getAction() == KeyEvent.ACTION_DOWN ? "DOWN " : "UP ")
                + KeyEvent.keyCodeToString(event.getKeyCode()) + " from \""
                + (device == null ? "?" : device.getName()) + "\" sources=0x"
                + Integer.toHexString(event.getSource()));
        }
        if (touchControls != null && device != null && !sdlPad(device)) {
            int sources = device.getSources();
            boolean hasPadSource = (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
            boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
            int code = event.getKeyCode();
            int bit = padBit(code, hasPadSource);
            if (bit >= 0) {
                touchControls.setPadButton(bit, down);
                return true;
            }
            if (code == KeyEvent.KEYCODE_BUTTON_L2) {
                touchControls.setPadTrigger(0, down);
                return true;
            }
            if (code == KeyEvent.KEYCODE_BUTTON_R2) {
                touchControls.setPadTrigger(1, down);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static int axisValue(float value) {
        // a small dead zone: a generic pad's stick rarely rests at exactly 0
        if (Math.abs(value) < 0.1f) return 0;
        return Math.round(Math.max(-1f, Math.min(1f, value)) * 32767f);
    }

    private static float stronger(float a, float b) {
        return Math.abs(b) > Math.abs(a) ? b : a;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        InputDevice device = event.getDevice();
        if (device != null && (event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && event.getAction() == MotionEvent.ACTION_MOVE) {
            long now = System.currentTimeMillis();
            float lx = event.getAxisValue(MotionEvent.AXIS_X), ly = event.getAxisValue(MotionEvent.AXIS_Y);
            float rx = stronger(event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RX));
            float ry = stronger(event.getAxisValue(MotionEvent.AXIS_RZ), event.getAxisValue(MotionEvent.AXIS_RY));
            float lt = stronger(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE));
            float rt = stronger(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS));
            float hx = event.getAxisValue(MotionEvent.AXIS_HAT_X), hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
            if (now - lastMotionLog >= 250
                    && (Math.abs(lx) > 0.5f || Math.abs(ly) > 0.5f || Math.abs(rx) > 0.5f || Math.abs(ry) > 0.5f
                        || Math.abs(lt) > 0.5f || Math.abs(rt) > 0.5f || hx != 0 || hy != 0)) {
                lastMotionLog = now;
                padLog(String.format(java.util.Locale.US,
                    "motion from \"%s\" X=%.2f Y=%.2f Z=%.2f RZ=%.2f RX=%.2f RY=%.2f LT=%.2f RT=%.2f BRAKE=%.2f GAS=%.2f HAT=%.0f,%.0f",
                    device.getName(), lx, ly, event.getAxisValue(MotionEvent.AXIS_Z),
                    event.getAxisValue(MotionEvent.AXIS_RZ), event.getAxisValue(MotionEvent.AXIS_RX),
                    event.getAxisValue(MotionEvent.AXIS_RY), event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                    event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE),
                    event.getAxisValue(MotionEvent.AXIS_GAS), hx, hy));
            }
            if (touchControls != null && !sdlPad(device)) {
                touchControls.setPadAxes(axisValue(lx), axisValue(ly), axisValue(rx), axisValue(ry),
                    axisValue(lt), axisValue(rt));
                touchControls.setPadDpad(hy < -0.5f, hy > 0.5f, hx < -0.5f, hx > 0.5f);
                return true;
            }
        }
        return super.dispatchGenericMotionEvent(event);
    }

    /** SAF lets the player choose a folder and filename without storage permissions. */
    public void chooseLayoutFile(boolean export, String configuration) {
        new AlertDialog.Builder(this).setTitle(export ? "Export layout" : "Import layout")
            .setMessage(export ? "Choose the folder and filename for your touch layout."
                : "Choose an exported Halo touch layout. It will replace your current buttons, sensitivity and General settings.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton(export ? "Choose location" : "Choose file", (dialog, which) -> {
                Intent intent = new Intent(export ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(export ? "text/plain" : "*/*");
                if (export) {
                    pendingLayoutExport = configuration;
                    intent.putExtra(Intent.EXTRA_TITLE, "halo-touch-layout.halolayout");
                }
                try { startActivityForResult(intent, export ? EXPORT_LAYOUT : IMPORT_LAYOUT); }
                catch (android.content.ActivityNotFoundException e) {
                    pendingLayoutExport = null;
                    Toast.makeText(this, "No document picker is available.", Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("pending-layout-export", pendingLayoutExport);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        if (request != EXPORT_LAYOUT && request != IMPORT_LAYOUT) {
            super.onActivityResult(request, result, data); return;
        }
        String exported = pendingLayoutExport; pendingLayoutExport = null;
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri document = data.getData();
        new Thread(() -> {
            try {
                if (request == EXPORT_LAYOUT) {
                    if (exported == null) throw new java.io.IOException("Layout snapshot is unavailable");
                    try (OutputStream output = getContentResolver().openOutputStream(document, "wt")) {
                        if (output == null) throw new java.io.IOException("Cannot open destination");
                        output.write(exported.getBytes(StandardCharsets.UTF_8));
                    }
                    runOnUiThread(() -> Toast.makeText(this, "Layout exported.", Toast.LENGTH_SHORT).show());
                } else {
                    ByteArrayOutputStream contents = new ByteArrayOutputStream();
                    try (InputStream input = getContentResolver().openInputStream(document)) {
                        if (input == null) throw new java.io.IOException("Cannot open layout file");
                        byte[] buffer = new byte[4096]; int size;
                        while ((size = input.read(buffer)) != -1) {
                            if (contents.size()+size > 65536) throw new java.io.IOException("Layout file is too large");
                            contents.write(buffer, 0, size);
                        }
                    }
                    String configuration = new String(contents.toByteArray(), StandardCharsets.UTF_8);
                    // Validate away from the UI thread; apply atomically to the active view.
                    TouchLayout.importConfiguration(configuration);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || touchControls == null) return;
                        try {
                            touchControls.importLayout(configuration);
                            Toast.makeText(this, "Layout imported and saved.", Toast.LENGTH_SHORT).show();
                        } catch (IllegalArgumentException e) { layoutFileError(e); }
                    });
                }
            } catch (Exception e) { runOnUiThread(() -> layoutFileError(e)); }
        }, "halo-touch-layout-file").start();
    }

    private void layoutFileError(Exception error) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this).setTitle("Layout file")
            .setMessage("Could not complete the operation: "+error.getMessage())
            .setPositiveButton("OK", null).show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (touchControls != null && getWindow().getDecorView().hasWindowFocus())
            touchControls.startDeviceInput();
    }

    @Override
    protected void onPause() {
        if (touchControls != null) touchControls.stopDeviceInput();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && !devicesListed) {
            devicesListed = true;
            listInputDevices("start");
            new Handler(Looper.getMainLooper()).postDelayed(() -> listInputDevices("after 15 s"), 15000);
        }
        if (touchControls != null) {
            if (hasFocus) touchControls.startDeviceInput();
            else touchControls.stopDeviceInput();
        }
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    protected void onDestroy() {
        if (touchControls != null) touchControls.stopDeviceInput();
        if (multicastLock != null && multicastLock.isHeld())
            multicastLock.release();
        multicastLock = null;
        super.onDestroy();
    }

    /**
     * Many phones drop the Wi-Fi's broadcast and multicast datagrams to
     * save power unless an app holds this: without it they would not see
     * system link games on the local network, nor be seen hosting one.
     */
    private void acquireMulticastLock() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null)
                return;
            multicastLock = wifi.createMulticastLock("halo-system-link");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (RuntimeException e) {
            // (no Wi-Fi, or not allowed: the local network may miss games)
            multicastLock = null;
        }
    }

    /**
     * The game draws a frame at every display refresh, between its 30 Hz
     * ticks (port/shared/game/render_interpolation.c); Android otherwise
     * often keeps an app at 60 Hz on a faster display.
     */
    private void preferHighestRefreshRate() {
        Display display = getWindowManager().getDefaultDisplay();
        Display.Mode current = display.getMode();
        Display.Mode best = current;

        for (Display.Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() == current.getPhysicalWidth() &&
                mode.getPhysicalHeight() == current.getPhysicalHeight() &&
                mode.getRefreshRate() > best.getRefreshRate()) {
                best = mode;
            }
        }
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.preferredDisplayModeId = best.getModeId();
        getWindow().setAttributes(attributes);
    }
}
