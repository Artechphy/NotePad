package dev.notepad.check;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Separate controller process: all app actions use public accessibility/input APIs. */
public class UiBridge extends Instrumentation {
    private UiAutomation ui;
    private boolean running = true;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        try {
            ui = getUiAutomation();
            AccessibilityServiceInfo service = ui.getServiceInfo();
            service.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            ui.setServiceInfo(service);
            try (ServerSocket server = new ServerSocket(18765)) {
                while (running) {
                    try (Socket client = server.accept();
                         BufferedReader input = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                         Writer output = new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8)) {
                        JSONObject response;
                        try { response = handle(new JSONObject(input.readLine())); }
                        catch (Exception error) {
                            response = new JSONObject().put("ok", false).put("error", error.toString());
                        }
                        output.write(response.toString() + "\n");
                        output.flush();
                    }
                }
            }
            finish(0, new Bundle());
        } catch (Exception error) {
            Bundle result = new Bundle(); result.putString("error", error.toString());
            finish(1, result);
        }
    }

    private JSONObject handle(JSONObject request) throws Exception {
        String action = request.getString("action");
        if (action.equals("stop")) { running = false; return new JSONObject().put("ok", true); }
        if (action.equals("key")) {
            int key = request.getInt("code");
            ui.injectInputEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key), true);
            ui.injectInputEvent(new KeyEvent(KeyEvent.ACTION_UP, key), true);
        } else if (!action.equals("dump")) {
            AccessibilityNodeInfo root = ui.getRootInActiveWindow();
            AccessibilityNodeInfo node = find(root, request);
            if (node == null) throw new IllegalStateException("Visible UI node not found: " + request);
            if (action.equals("set")) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, request.getString("value"));
                if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
                    throw new IllegalStateException("UI rejected text input");
            } else if (action.equals("click") || action.equals("long")) {
                Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
                if (bounds.isEmpty()) throw new IllegalStateException("UI node has empty bounds");
                long downTime = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
                        bounds.centerX(), bounds.centerY(), 0);
                down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                ui.injectInputEvent(down, true); down.recycle();
                if (action.equals("long")) SystemClock.sleep(700);
                MotionEvent up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                        bounds.centerX(), bounds.centerY(), 0);
                up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                ui.injectInputEvent(up, true); up.recycle();
            } else throw new IllegalArgumentException("Unknown UI action");
        }
        SystemClock.sleep(160);
        return new JSONObject().put("ok", true).put("nodes", dump());
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, JSONObject request) {
        if (node == null) return null;
        String kind = request.optString("by", "id");
        String expected = request.optString("match");
        CharSequence value = kind.equals("text") ? node.getText()
                : kind.equals("desc") ? node.getContentDescription()
                : kind.equals("class") ? node.getClassName() : node.getViewIdResourceName();
        if (node.isVisibleToUser() && value != null && value.toString().equals(expected)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), request);
            if (found != null) return found;
        }
        return null;
    }

    private JSONArray dump() throws Exception {
        JSONArray nodes = new JSONArray();
        collect(ui.getRootInActiveWindow(), nodes);
        return nodes;
    }

    private void collect(AccessibilityNodeInfo node, JSONArray nodes) throws Exception {
        if (node == null) return;
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        nodes.put(new JSONObject().put("id", node.getViewIdResourceName())
                .put("text", node.getText()).put("desc", node.getContentDescription())
                .put("class", node.getClassName()).put("package", node.getPackageName())
                .put("visible", node.isVisibleToUser()).put("enabled", node.isEnabled())
                .put("selected", node.isSelected()).put("clickable", node.isClickable())
                .put("bounds", bounds.toShortString()));
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), nodes);
    }
}
