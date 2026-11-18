<div dir="rtl">

# سكينة TV — نسخة شارب جوجل تي في

نسخة من [سكينة TV](https://github.com/shariefabdelmonem-netizen/sakina-tv) ومشغّل [CleanYourTV](https://github.com/Gathin23/cleanyourtv)، متعدّلة ومجرّبة على تلفزيون **Sharp 4K Google TV** (أندرويد 14، معالج MediaTek).

## الإضافات

- **سكينة هي الشاشة الرئيسية دايماً**: عند التشغيل ومع زر Home، من غير ما شاشة جوجل تي في ترجع تظهر.
- **لوحة إعدادات سريعة جديدة على زر الترس ⚙️**: لوحة جانبية فيها المدخلات، الصورة، الصوت، مؤقت النوم، الشبكة، التطبيقات، الإعدادات، والواجهة.
- **الإشعارات داخل سكينة**: الإشعارات بتظهر في اللوحة، وتقدر تفتح أي إشعار أو تمسحهم كلهم.
- **أزرار الريموت شغّالة زي الأصل**: زر التطبيقات بيفتح مكتبة التطبيقات، وزر TV بيفتح البث المباشر، والضغطة الطويلة عليه بتفتح قايمة المدخلات فوق الشاشة الحالية. وضغطة مطوّلة على الصوت بتعلّيه أو توطّيه على طول.
- **إذاعة القرآن الكريم شغّالة**: لو رابط البث ما ردّش خلال 12 ثانية بتنتقل للرابط التاني.
- **خلفيات طبيعة ومساجد**: 12 صورة طبيعة و5 صور لمساجد القاهرة، تختارها من الواجهة ← الخلفية.

</div>

---

# Sakina TV for Sharp Google TV

A copy of [Sakina TV](https://github.com/shariefabdelmonem-netizen/sakina-tv) with a modified
[CleanYourTV](https://github.com/Gathin23/cleanyourtv) launcher, set up and tested on a **Sharp 4K Google TV**
(Google TV 14, MediaTek, 2 GB RAM).

## Layout

| Folder | What it is |
|---|---|
| `app/` | Sakina TV: the Quran/adhkar cards app, the install skill and the launcher patch (unchanged apart from placeholder coordinates) |
| `cleanyourtv/` | The CleanYourTV launcher with the Sakina patch applied and the changes below |
| `theme-sharp/` | The theme used on the TV: `config.json`, the default wallpaper and a `wallpapers/` collection (see `CREDITS.md`) |

## What changed and why

**Home was inconsistent.** On this Sharp, the stock Google TV launcher (`launcherx`) also hosts the quick panel
that the remote's gear button opens. Disabling it killed the gear button. Re-enabling it made Google TV the real
Home (it has a higher Home priority), and Sakina only came back through an accessibility redirect, so Home
alternated between the two. The fix keeps `launcherx` disabled and moves its job into Sakina:

- `QuickSettingsActivity`: rebuilt as a right-hand side sheet with a tile grid (Inputs, Picture, Sound, Sleep
  timer, Network, Apps, Settings, Launcher settings; tiles this TV lacks are left out) and a notifications section.
- `NotificationsService` (new): a `NotificationListenerService` that feeds the sheet. Notifications can be opened
  (with the Android 14 background-start opt-in), dismissed, or cleared.
- Arabic strings for the sheet, and new icons (`ic_volume`, `ic_timer`, `ic_notifications`, `ic_clear_all`).

**Remote buttons on this Sharp** (theme keys, handled by `HomeKeyService`):

| Button | Sends | Theme setting |
|---|---|---|
| Gear | HID `0x0c009f` → `KEYCODE_NOTIFICATION` (83), scancode 240 | `settings_key_keycodes: [83]`, `settings_key_panel: true` |
| Apps | `KEYCODE_ALL_APPS` (284) | `"284:library"`, `app_library: true` |
| TV | scancode 362 | `"scan:362:com.mediatek.wwtv.tvcenter"` (Live TV); `"hold:scan:362:com.google.android.tv.inputplayer/.switcher.SelectInputActivity"` (input popup) |
| Volume hold | (never reaches the helper) | `volume_pauses_filter: true`; `consume_volume_keys` did not work here |

**Quran Radio stuck on "connecting".** The TV's MediaPlayer never answered the radiojar stream (it redirects to
http): no prepared, no error. `RadioActivity` now moves to the next link after 12 s, and the working
`radio.xecod.com` stream is tried first.

## Install / update

Set your city first (`app/launcher-patch/apply.py`, see `app/skills/sakina-tv/SKILL.md`). Then, with ADB
enabled on the TV:

```bash
cd cleanyourtv/launcher && ./gradlew assembleRelease && cd ..
TV_HOST=<tv ip> SKIP_BUILD=1 HOME_KEY_SERVICE=1 RESTORE_KEY_SERVICES=1 bash launcher/build-and-install.sh
TV_HOST=<tv ip> tools/theme.sh ../theme-sharp          # an install wipes the theme, so push it again
# let the quick panel read notifications (Android TV has no Settings screen for this):
adb -s <tv ip>:5555 shell cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.NotificationsService
```

Test buttons that go through the accessibility helper with the real remote: keys sent over ADB never reach it.

## Licences

Sakina TV and CleanYourTV are MIT (see each folder's `LICENSE`). The wallpapers are Wikimedia Commons photos under
CC BY / CC BY-SA: credits in `theme-sharp/CREDITS.md`, `cleanyourtv/themes/nature-calm/CREDITS.md` and
`app/themes/CREDITS.md`. The adhan recording is not included; `app/tools/fetch-adhan.sh` fetches one.
