# Hide Nav Pill  (LSPosed module)

Hides the gesture-navigation "pill" / home handle on **PixelOS (Android 17)**.
Only the *drawing* of the pill is suppressed. Edge-back, swipe-up-home and
long-press-assistant are all left untouched.

---

## 0. TL;DR

| item          | value |
|---------------|-------|
| Scope package | `com.google.android.apps.nexuslauncher` (Launcher3 / NexusLauncher) |
| Target class  | `com.android.launcher3.taskbar.StashedHandleView` |
| Hooks         | `draw(Canvas)` -> `setResult(null)` ; `setBackgroundColor(int)` -> `0x00000000` |

If your launcher package differs (e.g. AOSP `com.android.launcher3`), edit the
`LAUNCHER` constant in `HideHandleModule.java` and rebuild.

---

## 1. Why this works (逆向依据) — v3, corrected root cause

The v1/v2 assumption ("SystemUI draws the pill") was **wrong on this device**.
Dynamic evidence (`dumpsys window displays`, SurfaceFlinger layer dump):

    Window #4 Window{... u0 Taskbar}
        mOwnerUid = 10196              -> com.google.android.apps.nexuslauncher
        mAttrs    = {(0,0)(fillx71) ... ty=NAVIGATION_BAR fmt=TRANSLUCENT}
        insets    = id=#5d820001 type=navigationBars ... bottom=71
    SF layer   : Taskbar#68 / VRI-Taskbar#73

So the navigation-bar window is **owned and rendered by the Launcher process**,
not by SystemUI. Inside that window the visible bar is an
`android.view.View`, decompiled from the Launcher dex with baksmali:

    com.android.launcher3.taskbar.StashedHandleView
        .super Landroid/view/View;
        // NO onDraw override -- it paints only through its background

    com.android.launcher3.taskbar.StashedHandleViewController
        .implements Lcom/android/quickstep/NavHandle;

`StashedHandleView.updateHandleColor(boolean, boolean)` is the only color code
path, and it goes through `View.setBackgroundColor(int)` plus an
`ObjectAnimator.ofArgb(..., LauncherAnimUtils.VIEW_BACKGROUND_COLOR, ...)`.

=> The pill is a **background-painted View**, so:
- hooking `draw(Canvas)` and returning early stops *all* rasterization of the
  bar while the View keeps its bounds (insets / touch / gestures unaffected);
- forcing `setBackgroundColor` to transparent is a belt-and-suspenders guard.

Why v2 failed: it hooked SystemUI
`...navigationbar.gestural.NavigationHandle` / `QuickswitchOrientedNavHandle`.
Those classes exist but are **never instantiated** on this build (the nav bar is
Launcher-owned), so the hooks never fired on a live object.

---

## 2. What this module hooks

    Package : com.google.android.apps.nexuslauncher
    Class   : com.android.launcher3.taskbar.StashedHandleView
    Hook 1  : draw(Canvas)          -> setResult(null)      (suppress drawing)
    Hook 2  : setBackgroundColor(int) -> force 0x00000000   (safety net)

Nothing else is touched. No resource/RRO overlay is used, so there is no risk
of the bootloop that the RRO "hide pill" approach causes on Android 17.

---

## 3. Build

Requirements: **JDK 17**, **Android SDK platform 35**, `ANDROID_HOME` or a
`local.properties` file. The Gradle wrapper is bundled, no local Gradle install
is needed.

    ./gradlew :app:assembleRelease

Output:

    app/build/outputs/apk/release/app-release.apk

(it is signed with the AGP built-in debug key, installs as a normal app.)

### Cloud build (GitHub Actions)

Push to `main`/`master` or run the **Build HideNavPill APK** workflow manually.
The APK is uploaded as the `app-release` artifact.

---

## 4. Install & enable

1. Install `app-release.apk` on the device.
2. Open **LSPosed Manager -> Modules**, enable **Hide Nav Pill**.
3. **Scope: tick `com.google.android.apps.nexuslauncher` (the launcher).**
   You do *not* need to tick System UI for v3. Tick *System Framework* only if
   your LSPosed build requires it.
4. Reboot, or restart the launcher:

       su -c "am force-stop com.google.android.apps.nexuslauncher"

## 5. Verify

LSPosed's own module log (not logcat) is at:

    /data/adb/lspd/log/modules_*.log

or via `logcat` if `XposedBridge.log` is mirrored:

    su -c "logcat -d | grep HideHandle"

Expected lines:

    [HideHandle] Hooked com.android.launcher3.taskbar.StashedHandleView.draw -> pill hidden
    [HideHandle] Hooked com.android.launcher3.taskbar.StashedHandleView.setBackgroundColor -> transparent
    [HideHandle] HideNavPill module active (launcher scope)

If you instead see `[HideHandle] skip ...`, the class name differs on your
build. Re-check it with (note the required `./` prefix, see below):

    ./gradlew  # or on device:
    baksmali d ./classes.dex -o out_smali -a 35
    grep -rl StashedHandleView out_smali | head

> NOTE: baksmali 2.5.x needs the `./` prefix on a bare `.dex` file, otherwise it
> mistakes it for an APK and fails with "specify the specific entry ...".

## 6. Remove the old NavTweaks leftover first (important)

If `/data/adb/HideNavBar_config.sh` still exists (HD=true / VAR3=HP), delete it
so it cannot conflict / re-apply the RRO that bootloops on Android 17:

    su -c "rm -f /data/adb/HideNavBar_config.sh"

## 7. Uninstall / revert

Just disable the module in LSPosed Manager (and reboot / restart the launcher).
No persistent system change is made.
