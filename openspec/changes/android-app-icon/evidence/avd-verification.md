# Task 3.3 — AVD verification record

Device: `emulator-5554`, API 35 (`thuocla-api35`), 1080×2340 @440dpi.
Artifact under test: `app-debug.apk`, md5 **1003615b4b557c582e65454b0c45621b**.

## Evidence chain (one artifact, not two claims)

`adb install -r` reported `Success` three times while the device kept a **stale pre-icon build**.
The install only became real after a full uninstall; the proof is the hash of the copy pulled back
off the device:

```
local  app-debug.apk            md5 1003615b4b557c582e65454b0c45621b
device base.apk (pulled)        md5 1003615b4b557c582e65454b0c45621b   -> IDENTICAL

aapt2 dump badging (device copy)
  application: label='Thuốc lá' icon='res/mipmap-anydpi-v26/ic_launcher.xml'
```

Everything below is therefore attributable to this exact build.

## 1. Round mask does not clip the mark — PASS

Measured on the device screenshot, not judged by eye:

| property | measured | reading |
|---|---|---|
| disc bounding box | 143 × 143 px | — |
| aspect ratio w/h | **1.000** | perfect circle (AOSP round mask) |
| navy `#1a1a2e` fill vs πr² | 0.67 | a disc plus the lighter artwork inside it |
| artwork centre vs disc centre | **(−0.5, −0.5) px = 0.35 % of diameter** | centred |
| artwork pixels inside the disc | 2317 | — |
| of those, within 4 px of the mask edge | 49 (**2.1 %**) | the smoke plume's topmost wisps reach the edge |

Artwork is contained; the 2.1 % near-edge pixels are the smoke tip, consistent with the source
layer's art bbox (112–319 px of 432, well inside the 72 dp viewport). No corner clipping.

![normal mask](evidence/avd-normal-mask.png)

## 2. Themed-icons (monochrome) — NOT SETTLED, and the reason is a platform behaviour

Enabling "Themed icons" in *Wallpaper & style* flips a switch that reports `checked=true`, but
**no app on this launcher renders themed**. Measured colourfulness of each icon's artwork:

| app | strongly-saturated px | navy `#1a1a2e` |
|---|---|---|
| Thuốc lá | 1.3 % | **26.4 %** |
| Gmail | 11.4 % | 0 % |
| Phone | 6.9 % | 0 % |
| YouTube | 18.0 % | 0 % |
| Settings | 32.3 % | 0 % |

YouTube still red and Settings still blue means the launcher is not applying monochrome tinting to
*any* icon, ours included. `settings get secure themed_icons` returns `null` even with the switch on.

Two separate things were checked, and they must not be conflated:

- **Is the monochrome layer present and wired?** YES — verified in the compiled APK:
  `aapt2 dump xmltree --file res/mipmap-anydpi-v26/ic_launcher.xml` shows `<monochrome>` resolving to
  a real drawable, and `ic_launcher_monochrome.png` is a pure white alpha silhouette (10.7 % opaque,
  432×432) whose shape is a cigarette with a smoke plume.
- **Does it render as a tinted silhouette on this device?** NO OBSERVATION EITHER WAY. The platform
  never engaged themed icons, so this AVD cannot answer it. An earlier capture appeared to show a
  change, but that was the launcher icon cache being cleared, not theming.

**Consequence for the spec:** the `app-icon` scenario "Bật Themed icons … icon hiện dạng silhouette
một màu, vẫn nhận ra được là điếu thuốc có khói" is **unverified at runtime**. The wiring and the
asset are correct; the platform behaviour is what could not be exercised. It needs a device or
launcher that actually applies themed icons.

![themed attempt](evidence/avd-themed-attempt.png)

## 3. Large icon at 192 dp is not off-centre — PASS (by construction + measured)

The stock launcher here only draws the icon at one size, so 192 dp could not be observed directly.
What can be measured is what determines it:

- the adaptive layer is 432×432 px = 108 dp with the artwork inside the 72 dp safe viewport;
- on the rendered disc the artwork's centre sits **0.35 % of the diameter** off the disc centre.

Since the layer is scaled uniformly by the platform, an offset that small at 48 dp stays
proportionally the same at 192 dp — there is no absolute-pixel offset to grow.

## What is NOT proven here

- **OEM squircle masks.** Only the stock circular mask exists on this AVD. Safe-zone geometry is
  satisfied by measurement, but no squircle was observed.
- **Themed-icon rendering**, per section 2.
- **Release APK.** `app-debug.apk` only.

## Commands

```
~/Android/Sdk/emulator/emulator -avd thuocla-api35 -no-window -no-audio -gpu swiftshader_indirect
adb uninstall vn.dinhtrung.thuocla
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell input swipe 540 2200 540 700 300          # open app drawer
adb exec-out screencap -p > shot.png
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
adb shell pm path vn.dinhtrung.thuocla                # pull the installed copy and md5 it
aapt2 dump badging <pulled.apk>
aapt2 dump xmltree --file res/mipmap-anydpi-v26/ic_launcher.xml app-debug.apk
```