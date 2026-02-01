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
        
        // 1. Hook HAMBURGER MENU (Profile Settings) -> Long Press
        String[] menuIds = {"menu_settings_row", "action_bar_button_action", "settings_icon"};
        for (String id : menuIds) {
            hookLongPress(activity, id, v -> {
                VibrationUtil.vibrate(activity);
                EclipseSettingsController.open(activity);
                return true;
            });
        }

        // 2. Hook Search Tab (Fallback) -> Long Press
        hookLongPress(activity, "search_tab", v -> {
            VibrationUtil.vibrate(activity);
            EclipseSettingsController.open(activity);
            return true;
        });

        // 3. Hook Inbox -> Ghost Quick Toggle
        String[] inboxIds = {"action_bar_inbox_button", "direct_tab"};
        for (String id : inboxIds) {
            hookLongPress(activity, id, v -> {
                GhostModeUtils.toggleSelectedGhostOptions(activity);
                VibrationUtil.vibrate(activity);
                return true;
            });
        }
        
        addGhostEmojiNextToInbox(activity, GhostModeUtils.isGhostModeActive());
        
        // 4. Hook Gallery -> Mark as Seen
        hookLongPress(activity, "row_thread_composer_button_gallery", v -> {
             VibrationUtil.vibrate(activity);
             if (!FeatureFlags.isGhostSeen) return true;
             FeatureFlags.isGhostSeen = false;
             
             activity.getWindow().getDecorView().post(() -> {
                 try {
                     @SuppressLint("DiscouragedApi") int messageListId = activity.getResources().getIdentifier("message_list", "id", activity.getPackageName());
                     View view = activity.findViewById(messageListId);
                     if (view instanceof ViewGroup) {
                         view.scrollBy(0, -100);
                         new Handler(Looper.getMainLooper()).postDelayed(() -> {
                             view.scrollBy(0, 100);
                             FeatureFlags.isGhostSeen = true;
                             Toast.makeText(activity, "✅ Message was marked as read", Toast.LENGTH_SHORT).show();
                         }, 300);
                     } else {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> FeatureFlags.isGhostSeen = true, 300);
                     }
                 } catch (Exception e) {}
             });
             return true;
        });
    }

    private static void hookLongPress(Activity activity, String viewName, View.OnLongClickListener listener) {
        try {
            @SuppressLint("DiscouragedApi") int viewId = activity.getResources().getIdentifier(viewName, "id", activity.getPackageName());
            View view = activity.findViewById(viewId);
            if (view != null) {
                view.setOnLongClickListener(listener);
            }
        } catch (Exception ignored) {}
    }

    public void mainActivity(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("com.instagram.mainactivity.InstagramMainActivity", classLoader, "onCreate", android.os.Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                final Activity activity = (Activity) param.thisObject;
                currentActivity = activity;
                activity.runOnUiThread(() -> {
                    setupHooks(activity);
                    if (!FeatureFlags.showFeatureToasts || CustomToast.toastShown) return;
                    CustomToast.toastShown = true;
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        StringBuilder sb = new StringBuilder("InstaEclipse Loaded 🎯\n");
                        for (Map.Entry<String, Boolean> entry : FeatureStatusTracker.getStatus().entrySet()) {
                            sb.append(entry.getValue() ? "✅ " : "❌ ").append(entry.getKey()).append("\n");
                        }
                        CustomToast.showCustomToast(activity.getApplicationContext(), sb.toString().trim());
                    }, 1000);
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
                     if (FeatureFlags.isImportingConfig) {
                        FeatureFlags.isImportingConfig = false;
                        ConfigManager.importConfigFromClipboard(activity);
                     }
                 });
            }
        });
        BottomSheetHookUtil.hookBottomSheetNavigator(Module.dexKitBridge);
        
        // Modal Activity Hook
        XposedHelpers.findAndHookMethod("com.instagram.modal.ModalActivity", classLoader, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (activity != null) activity.runOnUiThread(() -> setupHooks(activity));
            }
        });
    }
}
