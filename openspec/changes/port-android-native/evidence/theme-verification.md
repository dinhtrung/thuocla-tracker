# #19 theme — device verification record

Device: `emulator-5554`, API 35 (Android 15), 1080×2340.
Artifact under test: `app-debug.apk`, md5 **0819900c49887619bb84e25230514bd6**.

Every figure below is produced by `analyse.py` in this directory from the committed screencaps.
Run `python3 analyse.py` to regenerate all of it; `SCRIPT-OUTPUT.txt` is its captured output, exit
code 0. If a figure here cannot be reproduced by that script, this document is wrong — fix the
document, not the script.

## Evidence chain — one artifact, not two claims

`adb install -r` prints `Success` even when the device keeps a stale build, so an install is only
real once the device copy hashes to the same bytes as the artifact:

```
local   android/app/build/outputs/apk/debug/app-debug.apk   md5 0819900c49887619bb84e25230514bd6
device  .../vn.dinhtrung.thuocla-U-tudQ_2BPRdpGT3KdZZ4Q==/base.apk   md5 0819900c49887619bb84e25230514bd6
```

That md5 reproduces from `./gradlew --offline :app:clean :app:assembleDebug` on this branch, and it
is unchanged between the build that captured the frames and the build that ran the final gate. So
the frames and the test run describe the same binary.

`aapt dump badging` on the packaged APK:

```
package: name='vn.dinhtrung.thuocla' versionCode='1' versionName='1.0.0'
sdkVersion:'26'  targetSdkVersion:'35'
uses-permission: name='vn.dinhtrung.thuocla.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

The only `uses-permission` is the app's own auto-added receiver permission: **no INTERNET**.

## Acceptance 1 — "Đổi máy sang dark mode: app tự đổi"

`adb shell cmd uimode night yes`, then a fresh launch. `shipped/today-dark.png` and
`shipped/stats-dark.png` are captured in that state, against the brief's palette as implemented in
`ui/theme/Color.kt`:

| role | expected | observed | share of frame |
|---|---|---|---|
| surface | `#1a1a2e` | `#1a1a2e` | 55.7 % (Today) / 54.7 % (Stats) |
| surfaceVariant | `#232a42` | `#232a42` | 36.0 % / 40.1 % |
| onSurface | `#e6eaf3` | `#e6eaf3` | 0.65 % / 0.31 % |
| onSurfaceVariant | `#a8b3c9` | `#a8b3c9` | 0.95 % / 1.16 % |
| primary | `#ff6b81` | `#ff6b81` | 0.71 % / 0.66 % |

The two large fills are the brief's navy surface and its container shade — what the screens and the
bottom navigation bar are painted with.

## Acceptance 2 — "không viền trắng, không chữ đen trên nền navy"

Two independent measurements, because "no white border" is easy to claim loosely.

**No white band.** A window strip would be a row spanning the full width in near-white. Counting
rows where near-white pixels exceed 95 % of the width: **0 rows in both dark frames**.
`#ffffff` totals 0.19 % (Today) and 0.16 % (Stats) of the frame, and the widest single row of pure
white reaches 10.2 % of the width — glyphs, not a band.

**No dark text on navy.** Ink on the navy surface must be light. Measured per dark frame: `#e6eaf3`
and `#a8b3c9` both present as above; and dark pixels (luminance < 40) that are **not** the navy
surface itself total **none** in either frame. Nothing dark is painted on navy.

The one dark ink that is correct — `onPrimary #3f0013` on the coral fill — is present in Today
(856 px) and absent from Stats (0 px, no coral button there), exactly as the design intends.

## Acceptance 3 — "Theme đang chọn áp dụng trên mọi màn hình"

Four frames, two screens × two themes:

| frame | surface | surfaceVariant | third token checked |
|---|---|---|---|
| `shipped/today-light.png` | `#f2f4fa` 55.7 % | `#e6eaf3` 36.0 % | `onSurfaceVariant #4a5568` present |
| `shipped/today-dark.png` | `#1a1a2e` 55.7 % | `#232a42` 36.0 % | `primary #ff6b81` present |
| `shipped/stats-light.png` | `#f2f4fa` 54.7 % | `#e6eaf3` 40.1 % | `onSurfaceVariant #4a5568` present |
| `shipped/stats-dark.png` | `#1a1a2e` 54.7 % | `#232a42` 40.1 % | `onSurfaceVariant #a8b3c9` present |

The stats bar colours come from the scheme, measured with seven seeded records so both kinds of bar
are drawn (`ChartBar`, line 250: today → `primary`, every other day → `tertiary`):

| frame | today's bar | other days' bars |
|---|---|---|
| `stats-light.png` | `#b3122e` 16643 px | `#8a6100` 19948 px |
| `stats-dark.png` | `#ff6b81` 16643 px | `#f1c40f` 19948 px |

The old hardcoded `#ff6b81` is **absent** from the light frame, so the literal really is gone rather
than merely unused.

An earlier capture pair was taken on an empty database, where `barHeight` is 0 dp for every day and
therefore no bar is drawn at all — the only primary pixels were the week navigator's selected chip.
Those frames could not support this claim, which is why the shipped pair is the seeded one.

## Acceptance 4 — no Material3 default survives

Material3's own baseline scheme paints every role the brief does not name, and the ones that show
up are not only purple: the navigation bar and the selected tab take `surfaceContainer*` and
`secondaryContainer`, and — the largest leak of all — an unbranded **`surfaceVariant #e7e0ec`**
fills whole cards. Setting only the brief's headline roles therefore produced an app whose screens
were coral while its cards and chrome stayed Material3. The scan looks for the baseline defaults
themselves, not just for the expected colours; the banned set is the full baseline light scheme
(21 roles), with `#b3261e` deliberately left out because it is both the baseline *error* and this
app's own light error and so cannot distinguish a leak from a correct token. Share in the shipped
frames:

| Material3 baseline role | hex | share in shipped frames |
|---|---|---|
| surface | `#fef7ff` | 0 % |
| surfaceContainerLow | `#f7f2fa` | 0 % |
| surfaceContainer | `#f3edf7` | 0 % |
| surfaceContainerHigh | `#ece6f0` | 0 % |
| surfaceContainerHighest | `#e6e0e9` | 0 % |
| **surfaceVariant** | **`#e7e0ec`** | **0 %** |
| secondaryContainer | `#e8def8` | 0 % |
| primary (purple) | `#6750a4` | 0 % |
| primaryContainer | `#eaddff` | 0 % |
| inversePrimary | `#d0bcff` | 0 % |
| secondary / tertiary | `#625b71` / `#7d5260` | 0 % |
| tertiaryContainer | `#ffd8e4` | 0 % |
| errorContainer | `#f9dedc` | 0 % |
| onPrimaryContainer | `#21005d` | 0 % |
| onSecondaryContainer | `#1d192b` | 0 % |
| onSurfaceVariant | `#49454f` | 0 % |
| onSurface | `#1d1b20` | 0 % |
| outline / outlineVariant | `#79747e` / `#cac4d0` | 0 % |
| inverseSurface | `#322f35` | 0 % |

The two `prefix/` frames prove this check has teeth, and the script now fails if they stop
tripping it (positive-control floors of 90 % and 15 %):

| frame | what it shows | Material3 default share |
|---|---|---|
| `prefix-01-material-defaults.png` | stale APK, no theme class at all — every surface still baseline | **93.8 %** |
| `prefix-02-headline-roles-only.png` | theme with headline roles only — brand surfaces, baseline card fill and purple nav pill | **18.3 %** |

Both are the Today screen. The second is the exact bug the brief warned about, caught by eye on the
device and then measured. In `prefix-01` the baseline `surface 45.1 %`, **`surfaceVariant 30.3 %`**,
`surfaceContainer 11.1 %`, `surfaceContainerHighest 6.0 %` and `secondaryContainer 1.2 %` add up to
93.8 % — this frame is almost entirely Material3 defaults. The shipped build measures 0 %.

An earlier revision of this document reported **63.5 %** for `prefix-01`: the script's banned list
held only five roles and omitted `#e7e0ec`, so a lavender card fill covering 30.3 % of the frame
was invisible to it. The figure was re-measured from the pixels and the list completed before this
record was published. `#e7e0ec` is now a named, separately-asserted offender in the script, so the
same omission cannot recur silently.

## Acceptance 5 — "Trong Cài đặt đặt Tối/Sáng/Theo hệ thống"

The three modes are proven on the device in both override directions, not only SYSTEM:

| capture | system setting | stored mode | dominant surface | verdict |
|---|---|---|---|---|
| `override/settings-picker-system.png` | LIGHT | SYSTEM | `#f2f4fa` 95.5 % | light, as the system says |
| `override/settings-saved-dark-on-light-system.png` | LIGHT | **DARK** | `#1a1a2e` 95.4 % | the Cài đặt screen itself goes dark |
| `override/dark-mode-on-light-system.png` | **LIGHT** | **DARK** | `#1a1a2e` 55.7 % | dark despite a light system |
| `override/settings-saved-light-on-dark-system.png` | DARK | **LIGHT** | `#f2f4fa` 95.4 % | the Cài đặt screen itself goes light |
| `override/light-mode-on-dark-system.png` | **DARK** | **LIGHT** | `#f2f4fa` 55.7 % | light despite a dark system |

The stored value was read back from the device database after Save — `select id,themeMode from
config` returned `1|DARK` then `1|LIGHT` — so persistence is confirmed at the storage layer and not
only in the pixels. The device was returned to `themeMode=SYSTEM` and its seeded records deleted
afterwards.

## What the frames cannot settle

- **The picker's own Save semantics** (that choosing a radio does not persist until Save). That is
  test-covered, not pixel-covered: `SettingsViewModelTest.choosingDark_showsItInThePicker_butOnlySavePersistsIt`
  and `SettingsFlowTest.choosingDarkInSettings_persistsIt_andReopeningShowsIt`.
- **The Room 1→2 migration.** Covered by
  `ThemeConfigStoreTest.migratingAVersionOneDatabase_keepsEveryRecordAndAddsTheDefaultMode`, which
  seeds a real version-1 database file and asserts every cigarette survives with mode `SYSTEM`.
- **A phone whose system setting changes while the app is running** without a relaunch. Not captured;
  the frames all come from a fresh launch.
- **Colour accuracy beyond these frames.** Each frame is one instant of one emulator on one API level.
  The scan reports the top colours, a fixed set of baseline default roles, and token presence — not a
  per-pixel audit against every colour Material3 could emit. A leaked default outside the 21 listed
  roles would still pass unnoticed; the positive control only proves the *listed* roles are checked.

## Repro

```
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/home/dinhtrung/Android/Sdk
cd android && ./gradlew --offline :app:clean :app:assembleDebug
md5sum app/build/outputs/apk/debug/app-debug.apk   # expect 0819900c49887619bb84e25230514bd6
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm path vn.dinhtrung.thuocla              # hash base.apk, must match

# seed seven records so the stats bars draw (empty DB draws no bars at all)
adb shell am force-stop vn.dinhtrung.thuocla
adb shell "run-as vn.dinhtrung.thuocla sqlite3 databases/thuocla.db \
  \"INSERT INTO records(dateKey,minuteOfDay,triggerIdx) VALUES \
   ('2026-11-03',540,0),('2026-11-03',600,1),('2026-11-03',720,2), \
   ('2026-11-02',600,0),('2026-11-02',660,3),('2026-11-01',600,1),('2026-10-31',600,0);\""

# SYSTEM-following pair
for m in no yes; do
  adb shell cmd uimode night $m; adb shell am force-stop vn.dinhtrung.thuocla
  adb shell am start -n vn.dinhtrung.thuocla/.MainActivity; sleep 4
  adb exec-out screencap -p > today-$([ $m = no ] && echo light || echo dark).png
  adb shell input tap 401 2208; sleep 2
  adb exec-out screencap -p > stats-$([ $m = no ] && echo light || echo dark).png
done

cd openspec/changes/port-android-native/evidence && python3 analyse.py
```

Do not reuse an APK whose md5 differs from the one above. A stale install is how the icon slice
produced three "Successful" installs of the wrong build, and how the first stats capture pair ended
up on an empty database with no bars to measure.