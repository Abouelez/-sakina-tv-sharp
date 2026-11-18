package com.gathin.tvhome;

import android.app.Activity;
import android.app.Notification;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The panel the remote's Settings button opens when {@code settings_key_panel} is on (HomeKeyService): a side
 * sheet on the right, over Home or whatever app is playing. Translucent and in a task of its own, so the app
 * behind keeps running and Back returns to it. Pressing Settings again closes it.
 *
 * It stands in for the stock Google TV panel once that launcher is disabled: a grid of tiles (Inputs, Picture,
 * Sound, Sleep timer, Network, Apps, the TV's Settings, Launcher settings; a tile whose screen this TV does not
 * have is left out), then the TV's notifications through NotificationsService, when its listener is allowed.
 */
public final class QuickSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    private static final int COLUMNS = 4;
    private static final ComponentName SOUND =
            new ComponentName("com.android.tv.settings", "com.android.tv.settings.device.sound.SoundActivity");
    /** MediaTek partner settings (Sharp, and other boards with the partnercustomizer overlay). */
    private static final ComponentName SLEEP_TIMER =
            new ComponentName("com.android.tv.settings", "com.android.tv.settings.partnercustomizer.timer.TimerActivity");
    private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

    private LinearLayout tiles;
    private LinearLayout notifications;

    private static final class Tile {
        final int icon;
        final CharSequence title;
        final Intent target;

        Tile(int icon, CharSequence title, Intent target) {
            this.icon = icon;
            this.title = title;
            this.target = target;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setWindowAnimations(0);   // the sheet slides in by itself, from the right
        setContentView(R.layout.activity_quick_settings);
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        tiles = findViewById(R.id.tiles);
        notifications = findViewById(R.id.notifications);
        buildTiles();
        buildNotifications();
        View first = ((LinearLayout) tiles.getChildAt(0)).getChildAt(0);
        first.requestFocus();
        View panel = findViewById(R.id.panel);
        panel.setTranslationX(dp(340));
        panel.setAlpha(0f);
        panel.animate().translationX(0).alpha(1f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    private void buildTiles() {
        PackageManager pm = getPackageManager();
        List<Tile> list = new ArrayList<>();
        Intent inputs = new Intent(ACTION_VIEW_INPUTS);
        if (pm.resolveActivity(inputs, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            list.add(new Tile(R.drawable.ic_input, getString(R.string.inputs), inputs));
        }
        list.add(new Tile(R.drawable.ic_picture, getString(R.string.quick_picture), SystemSettings.displayIntent(this)));
        list.add(new Tile(R.drawable.ic_volume, getString(R.string.quick_sound), explicitOr(SOUND, Settings.ACTION_SOUND_SETTINGS)));
        Intent timer = explicit(SLEEP_TIMER);
        if (timer != null) list.add(new Tile(R.drawable.ic_timer, getString(R.string.quick_sleep_timer), timer));
        list.add(new Tile(R.drawable.ic_wifi, getString(R.string.quick_network), SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS)));
        list.add(new Tile(R.drawable.ic_apps, getString(R.string.quick_apps),
                SystemSettings.intent(this, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
        list.add(new Tile(R.drawable.ic_settings, getString(R.string.quick_settings_short),
                SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        list.add(new Tile(R.drawable.ic_home_settings, getString(R.string.quick_launcher_short),
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS)));

        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) lp.topMargin = dp(8);
                tiles.addView(row, lp);
            }
            Tile tile = list.get(i);
            View view = getLayoutInflater().inflate(R.layout.item_quick_tile, row, false);
            ((ImageView) view.findViewById(R.id.icon)).setImageResource(tile.icon);
            ((TextView) view.findViewById(R.id.title)).setText(tile.title);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) view.getLayoutParams();
            if (i % COLUMNS != 0) lp.setMarginStart(dp(8));
            view.setOnClickListener(v -> open(tile.target));
            row.addView(view);
        }
        // Keep the last row's tiles the same size as the others.
        if (row != null) {
            for (int i = row.getChildCount(); i < COLUMNS; i++) {
                View spacer = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
                lp.setMarginStart(dp(8));
                row.addView(spacer, lp);
            }
        }
    }

    /** The notifications section, left out entirely while the listener is not allowed. */
    private void buildNotifications() {
        notifications.removeAllViews();
        View header = findViewById(R.id.notifications_header);
        if (!NotificationsService.isConnected()) {
            header.setVisibility(View.GONE);
            return;
        }
        header.setVisibility(View.VISIBLE);
        List<StatusBarNotification> list = NotificationsService.shown(getPackageName());
        boolean clearable = false;
        for (StatusBarNotification sbn : list) {
            addNotification(sbn);
            clearable |= sbn.isClearable();
        }
        if (list.isEmpty()) getLayoutInflater().inflate(R.layout.item_quick_empty, notifications, true);
        View clear = findViewById(R.id.clear_all);
        clear.setVisibility(clearable ? View.VISIBLE : View.GONE);
        clear.setOnClickListener(v -> {
            NotificationsService.clearAll();
            refreshNotificationsSoon();
        });
    }

    /** The listener answers asynchronously; rebuild once the system has had a moment, keeping focus in the sheet. */
    private void refreshNotificationsSoon() {
        notifications.postDelayed(() -> {
            buildNotifications();
            View focused = getCurrentFocus();
            if (focused == null || !focused.isShown()) {
                ((LinearLayout) tiles.getChildAt(0)).getChildAt(0).requestFocus();
            }
        }, 300);
    }

    private void addNotification(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        View card = getLayoutInflater().inflate(R.layout.item_notification, notifications, false);
        CharSequence title = NotificationsService.title(n);
        CharSequence text = NotificationsService.text(n);
        TextView textView = card.findViewById(R.id.text);
        ((TextView) card.findViewById(R.id.title)).setText(!TextUtils.isEmpty(title) ? title : text);
        if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(text)) textView.setText(text);
        else textView.setVisibility(View.GONE);
        ((TextView) card.findViewById(R.id.app)).setText(appLabel(sbn.getPackageName()));
        ImageView icon = card.findViewById(R.id.icon);
        Drawable drawable = null;
        Icon small = n.getSmallIcon();
        if (small != null) {
            try {
                drawable = small.loadDrawable(this);
            } catch (RuntimeException ignored) {
            }
        }
        if (drawable != null) icon.setImageDrawable(drawable);
        else icon.setImageResource(R.drawable.ic_notifications);
        card.setOnClickListener(v -> {
            if (NotificationsService.open(this, sbn)) {
                finish();
            } else if (sbn.isClearable()) {
                // Nowhere to go: a press dismisses it, as on the stock panel.
                NotificationsService.dismiss(sbn);
                refreshNotificationsSoon();
            }
        });
        notifications.addView(card);
    }

    private CharSequence appLabel(String pkg) {
        if ("android".equals(pkg)) return getString(R.string.tv_settings);
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
            return getPackageManager().getApplicationLabel(info);
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private void open(Intent target) {
        try {
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** That exact screen, or null where this TV does not have it. */
    private Intent explicit(ComponentName component) {
        Intent intent = new Intent().setComponent(component);
        return getPackageManager().resolveActivity(intent, 0) != null ? intent : null;
    }

    /** That exact screen, else the standard action (which can bring up a chooser when two screens answer it). */
    private Intent explicitOr(ComponentName component, String action) {
        Intent intent = explicit(component);
        return intent != null ? intent : SystemSettings.intent(this, action);
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    @SuppressWarnings("deprecation")
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }
}
