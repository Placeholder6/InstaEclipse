package ps.reso.instaeclipse.mods.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;

import java.io.File;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.mods.devops.config.ConfigManager;
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import de.robv.android.xposed.XposedBridge;

public class EclipseSettingsController {

    private static View settingsView;

    public static void open(Activity activity) {
        if (settingsView != null) {
            return; // Already open
        }

        try {
            // 1. Get Module Context to access YOUR XML resources
            Context moduleContext = activity.createPackageContext("ps.reso.instaeclipse", Context.CONTEXT_IGNORE_SECURITY);
            
            // 2. Inflate the layout using Module Context
            LayoutInflater inflater = LayoutInflater.from(moduleContext);
            settingsView = inflater.inflate(R.layout.activity_eclipse_settings, null);

            // 3. Configure the View
            settingsView.setBackgroundColor(Color.parseColor("#121212")); // Ensure opaque background
            settingsView.setClickable(true); // Catch clicks so they don't pass through
            settingsView.setFocusable(true); // Catch key events (like Back button)
            settingsView.setFocusableInTouchMode(true);

            // 4. Handle Back Button to close OUR view, not the app
            settingsView.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    close(activity);
                    return true;
                }
                return false;
            });

            // 5. Setup UI Logic (Bind Switches)
            setupUI(activity, settingsView);

            // 6. Inject into Root View
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root != null) {
                root.addView(settingsView, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 
                        ViewGroup.LayoutParams.MATCH_PARENT));
                settingsView.requestFocus(); // Essential for catching Back button
            }

        } catch (Exception e) {
            XposedBridge.log("InstaEclipse: Failed to inject settings view - " + e.getMessage());
            Toast.makeText(activity, "Error opening settings", Toast.LENGTH_SHORT).show();
        }
    }

    public static void close(Activity activity) {
        if (settingsView != null) {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root != null) {
                root.removeView(settingsView);
            }
            settingsView = null;
        }
    }

    private static void setupUI(Activity activity, View view) {
        SettingsManager.init(activity);

        ImageView closeBtn = view.findViewById(R.id.btn_close);
        if (closeBtn != null) closeBtn.setOnClickListener(v -> close(activity));

        setupGhostMode(view);
        setupAdBlock(view);
        setupDistraction(view);
        setupMisc(view);
        setupDeveloper(activity, view);
        setupFooter(activity, view);
    }

    private static void setupGhostMode(View view) {
        bindSwitch(view, R.id.switch_ghost_seen, FeatureFlags.isGhostSeen, (v, isChecked) -> FeatureFlags.isGhostSeen = isChecked);
        bindSwitch(view, R.id.switch_ghost_typing, FeatureFlags.isGhostTyping, (v, isChecked) -> FeatureFlags.isGhostTyping = isChecked);
        bindSwitch(view, R.id.switch_ghost_screenshot, FeatureFlags.isGhostScreenshot, (v, isChecked) -> FeatureFlags.isGhostScreenshot = isChecked);
        bindSwitch(view, R.id.switch_ghost_view_once, FeatureFlags.isGhostViewOnce, (v, isChecked) -> FeatureFlags.isGhostViewOnce = isChecked);
        bindSwitch(view, R.id.switch_ghost_story, FeatureFlags.isGhostStory, (v, isChecked) -> FeatureFlags.isGhostStory = isChecked);
        bindSwitch(view, R.id.switch_ghost_live, FeatureFlags.isGhostLive, (v, isChecked) -> FeatureFlags.isGhostLive = isChecked);
    }

    private static void setupAdBlock(View view) {
        bindSwitch(view, R.id.switch_ad_block, FeatureFlags.isAdBlockEnabled, (v, isChecked) -> FeatureFlags.isAdBlockEnabled = isChecked);
        bindSwitch(view, R.id.switch_analytics, FeatureFlags.isAnalyticsBlocked, (v, isChecked) -> FeatureFlags.isAnalyticsBlocked = isChecked);
        bindSwitch(view, R.id.switch_tracking, FeatureFlags.disableTrackingLinks, (v, isChecked) -> FeatureFlags.disableTrackingLinks = isChecked);
    }

    private static void setupDistraction(View view) {
        Switch extremeSwitch = view.findViewById(R.id.switch_extreme_mode);
        if (extremeSwitch != null) {
            extremeSwitch.setChecked(FeatureFlags.isExtremeMode);
            if (FeatureFlags.isExtremeMode) extremeSwitch.setEnabled(false);
            
            extremeSwitch.setOnCheckedChangeListener((v, isChecked) -> {
                if (isChecked && !FeatureFlags.isExtremeMode) {
                    FeatureFlags.isExtremeMode = true;
                    FeatureFlags.isDistractionFree = true;
                    SettingsManager.saveAllFlags();
                    v.setEnabled(false);
                    // Refresh view simply by re-binding logic to force lock check
                    bindSwitch(view, R.id.switch_disable_stories, FeatureFlags.disableStories, null);
                    bindSwitch(view, R.id.switch_disable_feed, FeatureFlags.disableFeed, null);
                    bindSwitch(view, R.id.switch_disable_reels, FeatureFlags.disableReels, null);
                    bindSwitch(view, R.id.switch_disable_explore, FeatureFlags.disableExplore, null);
                }
            });
        }

        bindSwitch(view, R.id.switch_disable_stories, FeatureFlags.disableStories, (v, isChecked) -> FeatureFlags.disableStories = isChecked);
        bindSwitch(view, R.id.switch_disable_feed, FeatureFlags.disableFeed, (v, isChecked) -> FeatureFlags.disableFeed = isChecked);
        bindSwitch(view, R.id.switch_disable_reels, FeatureFlags.disableReels, (v, isChecked) -> FeatureFlags.disableReels = isChecked);
        bindSwitch(view, R.id.switch_disable_explore, FeatureFlags.disableExplore, (v, isChecked) -> FeatureFlags.disableExplore = isChecked);
    }

    private static void setupMisc(View view) {
        bindSwitch(view, R.id.switch_story_flip, FeatureFlags.disableStoryFlipping, (v, isChecked) -> FeatureFlags.disableStoryFlipping = isChecked);
        bindSwitch(view, R.id.switch_video_autoplay, FeatureFlags.disableVideoAutoPlay, (v, isChecked) -> FeatureFlags.disableVideoAutoPlay = isChecked);
        bindSwitch(view, R.id.switch_follower_toast, FeatureFlags.showFollowerToast, (v, isChecked) -> FeatureFlags.showFollowerToast = isChecked);
    }

    private static void setupDeveloper(Activity activity, View view) {
        bindSwitch(view, R.id.switch_dev_mode, FeatureFlags.isDevEnabled, (v, isChecked) -> FeatureFlags.isDevEnabled = isChecked);

        Button btnImport = view.findViewById(R.id.btn_import_config);
        if (btnImport != null) btnImport.setOnClickListener(v -> {
            FeatureFlags.isImportingConfig = true;
            Toast.makeText(activity, "Re-open Instagram to trigger import", Toast.LENGTH_LONG).show();
            close(activity);
        });

        Button btnExport = view.findViewById(R.id.btn_export_config);
        if (btnExport != null) btnExport.setOnClickListener(v -> {
             ConfigManager.exportCurrentDevConfig(activity);
             Toast.makeText(activity, "Config copied to clipboard", Toast.LENGTH_SHORT).show();
        });
    }

    private static void setupFooter(Activity activity, View view) {
        Button btnRestart = view.findViewById(R.id.btn_restart);
        if (btnRestart != null) btnRestart.setOnClickListener(v -> restartApp(activity));
        
        Button btnGithub = view.findViewById(R.id.btn_github);
        if (btnGithub != null) btnGithub.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ReSo7200/InstaEclipse"));
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(browserIntent);
        });
    }

    private static void bindSwitch(View root, int id, boolean currentVal, CompoundButton.OnCheckedChangeListener listener) {
        Switch sw = root.findViewById(id);
        if (sw != null) {
            sw.setChecked(currentVal);
            sw.setOnCheckedChangeListener((v, isChecked) -> {
                if (listener != null) listener.onCheckedChanged(v, isChecked);
                SettingsManager.saveAllFlags();
            });
            if (FeatureFlags.isExtremeMode && isDistractionSwitch(id)) {
                sw.setEnabled(false);
            }
        }
    }

    private static boolean isDistractionSwitch(int id) {
        return id == R.id.switch_disable_stories || id == R.id.switch_disable_feed ||
               id == R.id.switch_disable_reels || id == R.id.switch_disable_explore;
    }

    private static void restartApp(Context context) {
        try {
            File cacheDir = context.getCacheDir();
            if (cacheDir != null && cacheDir.isDirectory()) {
                File[] children = cacheDir.listFiles();
                if (children != null) for (File child : children) child.delete();
            }
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Runtime.getRuntime().exit(0);
            }
        } catch (Exception e) {
            Toast.makeText(context, "Restart failed", Toast.LENGTH_SHORT).show();
        }
    }
}
