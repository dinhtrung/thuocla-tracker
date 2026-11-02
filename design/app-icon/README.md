# App icon asset set

Source of truth for the Android launcher icon. Nothing here is hand-drawn for Android: the mark is
the PWA's own published artwork (`smoking-tracker/icon-512.png`, the app's established brand), with the
navy ground (`#1a1a2e` — the same value as the dark-theme `surface` token) chroma-keyed out so the same
art serves as an adaptive foreground.

| file | purpose |
|---|---|
| `ic_launcher_foreground.png` | adaptive foreground, 432×432 (108dp @xxxhdpi), art inside the 66dp safe circle |
| `ic_launcher_background.png` | adaptive background, 432×432 flat `#1a1a2e` |
| `ic_launcher_monochrome.png` | Android 13+ themed icon layer, white alpha silhouette (the platform tints it) |
| `mipmap-{m,h,xh,xxh,xxx}dpi/ic_launcher.png` | legacy square icon, 48…192 px |
| `mipmap-{m,h,xh,xxh,xxx}dpi/ic_launcher_round.png` | legacy round icon, 48…192 px |
| `play-store-512.png` | 512×512 store listing (not needed for sideloading) |
| `preview.png` | proof sheet: 48/96/192 px under circle, squircle and square masks + monochrome |

## Wiring the fleet must do

1. Copy `mipmap-*/` verbatim into `android/app/src/main/res/`.
2. Copy the three adaptive layers into `android/app/src/main/res/drawable-nodpi/`.
3. `res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
</adaptive-icon>
```

4. `AndroidManifest.xml` currently declares **no** `android:icon` at all (the APK reports `icon=''` and
   the launcher falls back to the default robot): add `android:icon="@mipmap/ic_launcher"` and
   `android:roundIcon="@mipmap/ic_launcher_round"` on `<application>`.
5. Keep `android:label="Thuốc lá"` as is; the icon carries no text, so it needs no translation.

## Rules

- Do not recolour, redraw or re-crop the mark. If the brand changes, regenerate this directory from the
  new source art and note the change here — the launcher must never disagree with the PWA icon.
- Do not add text to the icon: it is illegible below 96 px and untranslatable.
