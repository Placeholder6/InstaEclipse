package ps.reso.instaeclipse.mods.ui;

import static ps.reso.instaeclipse.mods.ghost.ui.GhostEmojiManager.addGhostEmojiNextToInbox;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
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
import ps.reso.instaeclipse.utils.dialog.DialogUtils;
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

    private static boolean isAnyGhostOptionEnabled() {
        return GhostModeUtils.isGhostModeActive();
    }

    public static void setupHooks(Activity activity) {
        
        // -----------------------------------------------------------------
        // 1. Hook Search Tab -> Launches "EclipseSettingsActivity" (Full UI)
        // -----------------------------------------------------------------
        hookLongPress(activity, "search_tab", v -> {
            VibrationUtil.vibrate(activity);

            try {
                // Launch the native Activity from your module
                Intent intent = new Intent();
                // Ensure the package name matches your AndroidManifest.xml exactly
                intent.setClassName("ps.reso.instaeclipse", "ps.reso.instaeclipse.activities.EclipseSettingsActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                activity.startActivity(intent);

            } catch (Exception e) {
                XposedBridge.log("InstaEclipse: Failed to launch Settings Activity - " + e.getMessage());
                // Fallback: Show the old dialog if the Activity fails to load
                DialogUtils.showEclipseOptionsDialog(activity);
            }
            return true;
        });

        // -----------------------------------------------------------------
        // 2. Hook Inbox Button -> Toggles Ghost Quick Options
        // -----------------------------------------------------------------
        String[] possibleIds = {"action_bar_inbox_button", "direct_tab"};

        for (String id : possibleIds) {
            @SuppressLint("DiscouragedApi") int viewId = activity.getResources().getIdentifier(id, "id", activity.getPackageName());
            View view = activity.findViewById(viewId);
            if (view != null) {
                hookLongPress(activity, id, v -> {
                    GhostModeUtils.toggleSelectedGhostOptions(activity);
                    VibrationUtil.vibrate(activity);
                    return true;
                });
                break;
            }
        }

        addGhostEmojiNextToInbox(activity, GhostModeUtils.isGhostModeActive());

        // -----------------------------------------------------------------
        // 3. Hook Gallery Button (in DM) -> Mark Message as Seen
        // -----------------------------------------------------------------
        hookLongPress(activity, "row_thread_composer_button_gallery", v -> {
            VibrationUtil.vibrate(activity);

            if (!FeatureFlags.isGhostSeen) {
                return true;
            }

            FeatureFlags.isGhostSeen = false;

            activity.getWindow().getDecorView().post(() -> {
                try {
                    // Look for the exact message list view by ID
                    @SuppressLint("DiscouragedApi") int messageListId = activity.getResources().getIdentifier("message_list", "id", activity.getPackageName());
                    View view = activity.findViewById(messageListId);

                    if (view instanceof ViewGroup messageList) {
                        // Try scrolling via translation if standard scroll methods don't exist
                        messageList.scrollBy(0, -100); // scroll up

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            messageList.scrollBy(0, 100); // scroll back down

                            FeatureFlags.isGhostSeen = true;
                            Toast.makeText(activity, "✅ Message was marked as read", Toast.LENGTH_SHORT).show();

                        }, 300);

                    } else {
                        XposedBridge.log("⚠️ message_list not a ViewGroup or not found — fallback to reset flag");
                        new Handler(Looper.getMainLooper()).postDelayed(() -> FeatureFlags.isGhostSeen = true, 300);
                    }
                } catch (Exception e) {
                    XposedBridge.log("❌ Exception in scroll logic: " + Log.getStackTraceString(e));
                }
            });

            return true;
        });
    }

    // Helper: Safely hook long press on a View by name
    private static void hookLongPress(Activity activity, String viewName, View.OnLongClickListener listener) {
        try {
            @SuppressLint("DiscouragedApi") int viewId = activity.getResources().getIdentifier(viewName, "id", activity.getPackageName());
            View view = activity.findViewById(viewId);

            if (view != null) {
                view.setOnLongClickListener(listener);
            }
        } catch (Exception ignored) {
        }
    }

    public void mainActivity(ClassLoader classLoader) {
        // Hook onCreate of Instagram Main
        XposedHelpers.findAndHookMethod("com.instagram.mainactivity.InstagramMainActivity", classLoader, "onCreate", android.os.Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                final Activity activity = (Activity) param.thisObject;
                currentActivity = activity;
                activity.runOnUiThread(() -> {
                    try {
                        setupHooks(activity);
                        addGhostEmojiNextToInbox(activity, isAnyGhostOptionEnabled());
                        if (!FeatureFlags.showFeatureToasts || CustomToast.toastShown) return;
                        CustomToast.toastShown = true;

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            StringBuilder sb = new StringBuilder("InstaEclipse Loaded 🎯\n");
                            for (Map.Entry<String, Boolean> entry : FeatureStatusTracker.getStatus().entrySet()) {
                                sb.append(entry.getValue() ? "✅ " : "❌ ").append(entry.getKey()).append("\n");
                            }
                            CustomToast.showCustomToast(activity.getApplicationContext(), sb.toString().trim());
                        }, 1000);
                    } catch (Exception ignored) {

                    }
                });
            }
        });

        // Hook onResume - Instagram Main
        XposedHelpers.findAndHookMethod("com.instagram.mainactivity.InstagramMainActivity", classLoader, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                final Activity activity = (Activity) param.thisObject;
                currentActivity = activity;
                activity.runOnUiThread(() -> {
                    try {
                        setupHooks(activity);
                        addGhostEmojiNextToInbox(activity, isAnyGhostOptionEnabled());

                        if (FeatureFlags.isImportingConfig) {
                            // De-bounce: flip it off first so it won't re-trigger on next onResume
                            FeatureFlags.isImportingConfig = false;
                            ConfigManager.importConfigFromClipboard(activity);
                        }
                    } catch (Exception ignored) {
                    }
                });
            }
        });


        // Hook getBottomSheetNavigator - Instagram Main
        BottomSheetHookUtil.hookBottomSheetNavigator(Module.dexKitBridge);

        // Hook onResume - Modal Activity
        XposedHelpers.findAndHookMethod("com.instagram.modal.ModalActivity", classLoader, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        try {
                            setupHooks(activity);
                        } catch (Exception ignored) {
                        }
                    });
                }
            }
        });
    }

}
