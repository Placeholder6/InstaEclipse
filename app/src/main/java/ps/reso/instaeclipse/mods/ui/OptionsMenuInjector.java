package ps.reso.instaeclipse.mods.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Toast;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.Xposed.Module;
import ps.reso.instaeclipse.mods.ui.utils.VibrationUtil;

public class OptionsMenuInjector {

    private static boolean hooked = false;

    public static void inject(DexKitBridge bridge, ClassLoader classLoader) {
        if (hooked) return;

        try {
            // 1. Find the method that builds the "Settings and privacy" list
            // We look for a method that uses this specific string literal.
            List<MethodData> methods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().usingStrings("settings_and_privacy")));

            if (methods.isEmpty()) {
                XposedBridge.log("InstaEclipse: ❌ Could not find Options Menu builder.");
                return;
            }

            // Usually the method that builds the list is the one we want.
            for (MethodData methodData : methods) {
                Method method = methodData.getMethodInstance(classLoader);
                
                // Hook it
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // 2. The return value is usually the List of items
                        Object result = param.getResult();
                        if (result instanceof List) {
                            List<Object> menuItems = (List<Object>) result;

                            // Avoid adding duplicate items if called multiple times
                            for (Object item : menuItems) {
                                if (getObjectStringField(item).contains("InstaEclipse")) {
                                    return;
                                }
                            }

                            // 3. Clone the first item (usually "Settings") to get the correct styling
                            if (!menuItems.isEmpty()) {
                                Object originalItem = menuItems.get(0);
                                Object newItem = cloneAndModifyItem(originalItem, param.thisObject);
                                
                                if (newItem != null) {
                                    // 4. Add our item to the TOP of the list
                                    menuItems.add(0, newItem); 
                                }
                            }
                        }
                    }
                });
            }
            hooked = true;
            XposedBridge.log("InstaEclipse: ✅ Options Menu Hooked!");

        } catch (Exception e) {
            XposedBridge.log("InstaEclipse: Failed to hook Options Menu: " + e.getMessage());
        }
    }

    // Helper to Clone the Instagram Menu Item and change its Title/Action
    private static Object cloneAndModifyItem(Object original, Object thisObject) {
        try {
            // Create a shallow copy if possible, or use the same class
            // Since we don't know the class constructor, we try to use Reflection to copy fields
            // But usually, these are simple POJOs. Let's try to clone fields manually if clone() isn't available.
            
            // STRATEGY: Create a new instance of the same class
            Class<?> itemClass = original.getClass();
            Object newItem = null;

            try {
                // Try empty constructor
                newItem = itemClass.newInstance();
            } catch (Exception e) {
                // If no empty constructor, this strategy fails.
                // Fallback: Use the original item as a template? No, that modifies the original.
                return null;
            }

            // Copy fields from original to newItem (Icon, Styling, etc.)
            for (Field field : itemClass.getDeclaredFields()) {
                field.setAccessible(true);
                field.set(newItem, field.get(original));
            }

            // MODIFY: Find the field that holds the Title (String or CharSequence)
            boolean titleSet = false;
            for (Field field : itemClass.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(newItem);
                
                // If we find the string "Settings and privacy", replace it!
                if (value instanceof String && ((String) value).contains("Settings")) {
                    field.set(newItem, "InstaEclipse Settings");
                    titleSet = true;
                    break; // Stop after changing title
                }
                // Sometimes it's a Resource ID (int), which is harder to change without context.
                // But mostly it's a CharSequence/String in the generated list.
            }
            
            if (!titleSet) {
                 // Fallback: Look for any String field and set it (Risky but effective)
                 // Or look for a field named 'label' or 'title' if not obfuscated
                 // For now, if we fail to find the string, we might skip adding to avoid empty row.
            }

            // MODIFY: Find the field that holds the OnClickListener
            for (Field field : itemClass.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == View.OnClickListener.class) {
                    // Replace with our listener
                    field.set(newItem, (View.OnClickListener) v -> {
                        Context context = v.getContext();
                        VibrationUtil.vibrate(context);
                        
                        try {
                            Intent intent = new Intent();
                            intent.setClassName("ps.reso.instaeclipse", "ps.reso.instaeclipse.activities.EclipseSettingsActivity");
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            // Disable animation for native feel
                            if (context instanceof Activity) {
                                ((Activity) context).startActivity(intent);
                                ((Activity) context).overridePendingTransition(0, 0); 
                            } else {
                                context.startActivity(intent);
                            }
                        } catch (Exception e) {
                            Toast.makeText(context, "Failed to open settings", Toast.LENGTH_SHORT).show();
                        }
                    });
                    break;
                }
            }

            return newItem;

        } catch (Exception e) {
            XposedBridge.log("InstaEclipse: Error creating menu item: " + e.getMessage());
            return null;
        }
    }

    // Helper to safely read string fields for verification
    private static String getObjectStringField(Object obj) {
        StringBuilder sb = new StringBuilder();
        try {
            for (Field f : obj.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(obj);
                if (val instanceof String) sb.append(val);
            }
        } catch (Exception ignored) {}
        return sb.toString();
    }
                              }
