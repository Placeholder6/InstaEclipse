package ps.reso.instaeclipse.mods.ui;

import static ps.reso.instaeclipse.mods.ghost.ui.GhostEmojiManager.addGhostEmojiNextToInbox;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import ps.reso.instaeclipse.Xposed.Module;
import ps.reso.instaeclipse.mods.devops.config.ConfigManager;
import ps.reso.instaeclipse.mods.ui.utils.BottomSheetHookUtil;
import ps.reso.instaeclipse.mods.ui.utils.VibrationUtil;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.ghost.GhostModeUtils;
import ps.reso.instaeclipse.utils.toast.CustomToast;

public class UIHookManager {

    @SuppressLint("StaticFieldLeak")
    private static Activity currentActivity;

    public static Activity getCurrentActivity() {
        return currentActivity;
    }

    public static void setupHooks(Activity activity) {
        
        // -----------------------------------------------------------
        // 1. Hook HAMBURGER MENU (Profile Settings) -> Long Press
        // -----------------------------------------------------------
        // Typical IDs for the profile menu button
        String[] menuIds = {"menu_settings_row", "action_bar_button_action", "settings_icon"};
        for (String id : menuIds) {
            hookLongPress(activity, id, v -> {
                VibrationUtil.vibrate(activity);
                // INJECT THE VIEW
                EclipseSettingsController.open(activity);
                return true;
            });
        }

        // -----------------------------------------------------------
        // 2. Hook Search Tab (Fallback) -> Long Press
        // -----------------------------------------------------------
        hookLongPress(activity, "search_tab", v -> {
            VibrationUtil.vibrate(activity);
            EclipseSettingsController.open(activity);
            return true;
        });

        // -----------------------------------------------------------
        // 3. Hook Inbox -> Ghost Quick Toggle
        // -----------------------------------------------------------
        String[] inboxIds = {"action_bar_inbox_button", "direct_tab"};
        for (String id : inboxIds) {
            hookLongPress(activity, id, v -> {
                GhostModeUtils.toggleSelectedGhostOptions(activity);
                VibrationUtil.vibrate(activity);
                return true;
            });
        }
        
        addGhostEmojiNextToInbox(activity, GhostModeUtils.isGhostModeActive());
        
        // ... (Keep your existing Gallery hook code here) ...
        hookLongPress(activity, "row_thread_composer_button_gallery", v -> {
             // ... [Paste your existing gallery hook logic here] ...
             VibrationUtil.vibrate(activity);
             if (!FeatureFlags.isGhostSeen) return true;
             FeatureFlags.isGhostSeen = false;
             // ... scroll logic ...
             new Handler(Looper.getMainLooper()).postDelayed(() -> FeatureFlags.isGhostSeen = true, 500);
             return true;
        });
    }

    // Helper
    private static void hookLongPress(Activity activity, String viewName, View.OnLongClickListener listener) {
        try {
            @SuppressLint("DiscouragedApi") int viewId = activity.getResources().getIdentifier(viewName, "id", activity.getPackageName());
            View view = activity.findViewById(viewId);
            if (view != null) {
                view.setOnLongClickListener(listener);
            }
        } catch (Exception ignored) {}
    }

    // ... [Keep the rest of your mainActivity/onResume hooks exactly as they were] ...
    public void mainActivity(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("com.instagram.mainactivity.InstagramMainActivity", classLoader, "onCreate", android.os.Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                final Activity activity = (Activity) param.thisObject;
                currentActivity = activity;
                activity.runOnUiThread(() -> {
                    setupHooks(activity);
                    // ... toast logic ...
                });
            }
        });
        
        XposedHelpers.findAndHookMethod("com.instagram.mainactivity.InstagramMainActivity", classLoader, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                 final Activity activity = (Activity) param.thisObject;
                 currentActivity = activity;
                 activity.runOnUiThread(() -> {
                     setupHooks(activity);
                     // ... config import logic ...
                 });
            }
        });
        
        // ... BottomSheet and Modal hooks ...
        BottomSheetHookUtil.hookBottomSheetNavigator(Module.dexKitBridge);
    }
}
