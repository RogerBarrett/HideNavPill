package com.operit.handlehide;

import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hide the gesture-navigation "pill" / home handle on Android 17 (PixelOS).
 *
 * HISTORY:
 *   v3 hooked StashedHandleView.draw -> NoSuchMethodError (class has no draw()).
 *   v4 hooked updateHandleColor + View.setBackgroundColor. Both hooks fired
 *      successfully (confirmed via LSPosed logs), but the pill only turned
 *      BLACK instead of disappearing -> clearing the background colour is NOT
 *      enough; the bar is still painted by another layer/mechanism.
 *
 * STRATEGY (v5, decisive):
 *   Force the StashedHandleView itself to be invisible. A pure drawing View has
 *   no touch handling, so hiding it cannot affect swipe-up / back / assistant.
 *   1. Hook every StashedHandleView constructor -> right after creation call
 *      setVisibility(INVISIBLE) + setAlpha(0f) + setBackgroundColor(TRANSPARENT).
 *   2. Hook updateHandleColor(boolean, boolean) -> no-op (belt & braces).
 *   3. Guard hook View.setBackgroundColor(int) for StashedHandleView instances.
 *
 * SCOPE: com.google.android.apps.nexuslauncher (Launcher3).
 */
public class HideHandleModule implements IXposedHookLoadPackage {

    private static final String TAG = "HideHandle";
    private static final String LAUNCHER = "com.google.android.apps.nexuslauncher";
    private static final String HANDLE_VIEW =
            "com.android.launcher3.taskbar.StashedHandleView";

    private void log(String s) {
        XposedBridge.log("[" + TAG + "] " + s);
    }

    private void hide(View v) {
        try {
            v.setVisibility(View.INVISIBLE);
            v.setAlpha(0f);
            v.setBackgroundColor(0x00000000);
        } catch (Throwable t) {
            log("hide() failed: " + t);
        }
    }

    private void hookView(ClassLoader cl) {
        Class<?> handleView;
        try {
            handleView = XposedHelpers.findClass(HANDLE_VIEW, cl);
        } catch (Throwable t) {
            log("class not found " + HANDLE_VIEW + ": " + t);
            return;
        }

        // 1) Hide the view as soon as any constructor returns.
        try {
            XposedBridge.hookAllConstructors(handleView, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View) {
                        hide((View) param.thisObject);
                    }
                }
            });
            log("Hooked StashedHandleView constructors -> hidden");
        } catch (Throwable t) {
            log("FAILED constructor hook: " + t);
        }

        // 2) Neutralise the colour entry point.
        try {
            XposedHelpers.findAndHookMethod(handleView, "updateHandleColor",
                    boolean.class, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                            if (param.thisObject instanceof View) {
                                hide((View) param.thisObject);
                            }
                        }
                    });
            log("Hooked updateHandleColor -> no-op + hidden");
        } catch (Throwable t) {
            log("FAILED updateHandleColor hook: " + t);
        }

        // 3) Guard any background colour write on the handle.
        try {
            XposedHelpers.findAndHookMethod(View.class, "setBackgroundColor",
                    int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (handleView.isInstance(param.thisObject)) {
                                param.args[0] = 0x00000000;
                                if (param.thisObject instanceof View) {
                                    ((View) param.thisObject)
                                            .setVisibility(View.INVISIBLE);
                                }
                            }
                        }
                    });
            log("Hooked View.setBackgroundColor (guard for StashedHandleView)");
        } catch (Throwable t) {
            log("FAILED setBackgroundColor guard: " + t);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!LAUNCHER.equals(lpparam.packageName)) return;
        hookView(lpparam.classLoader);
        log("HideNavPill module active (launcher scope) v5");
    }
}
