package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lets the quick settings panel (QuickSettingsActivity) list the TV's notifications, the job the stock Google TV
 * home's panel did before it was disabled. Android binds it only once the listener is allowed, which the TV's
 * Settings has no screen for, so it is granted over adb:
 *   cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.NotificationsService
 * Without the grant the panel simply shows no notifications section.
 */
public final class NotificationsService extends NotificationListenerService {

    /** How many notifications the panel lists, newest first. */
    static final int MAX_SHOWN = 5;

    private static volatile NotificationsService connected;

    @Override
    public void onListenerConnected() {
        connected = this;
    }

    @Override
    public void onListenerDisconnected() {
        if (connected == this) connected = null;
    }

    @Override
    public void onDestroy() {
        if (connected == this) connected = null;
        super.onDestroy();
    }

    /** True while Android has the listener bound (the adb grant above is in place). */
    static boolean isConnected() {
        return connected != null;
    }

    /** The notifications worth showing, newest first: no ongoing or foreground-service ones, no group summaries,
     *  none of this launcher's own, none without any text. Empty when the listener is not bound. */
    static List<StatusBarNotification> shown(String ownPackage) {
        NotificationsService service = connected;
        if (service == null) return Collections.emptyList();
        StatusBarNotification[] active;
        try {
            active = service.getActiveNotifications();
        } catch (RuntimeException e) {   // SecurityException when the grant was just taken back
            return Collections.emptyList();
        }
        if (active == null) return Collections.emptyList();
        List<StatusBarNotification> list = new ArrayList<>();
        for (StatusBarNotification sbn : active) {
            Notification n = sbn.getNotification();
            if (n == null || sbn.isOngoing()) continue;
            if ((n.flags & (Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_GROUP_SUMMARY)) != 0) continue;
            if (sbn.getPackageName().equals(ownPackage)) continue;
            if (TextUtils.isEmpty(title(n)) && TextUtils.isEmpty(text(n))) continue;
            list.add(sbn);
        }
        Collections.sort(list, (a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
        return list.size() > MAX_SHOWN ? new ArrayList<>(list.subList(0, MAX_SHOWN)) : list;
    }

    static CharSequence title(Notification n) {
        return n.extras == null ? null : n.extras.getCharSequence(Notification.EXTRA_TITLE);
    }

    static CharSequence text(Notification n) {
        if (n.extras == null) return null;
        CharSequence big = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        return !TextUtils.isEmpty(big) ? big : n.extras.getCharSequence(Notification.EXTRA_TEXT);
    }

    /** Opens what the notification points to, and removes it when it asks to be removed on a tap.
     *  False when it has nowhere to go or the app's target is gone. */
    static boolean open(Activity from, StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        PendingIntent target = n.contentIntent;
        if (target == null) return false;
        Bundle options = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14: the panel is in front, but a PendingIntent only starts an activity on the sender's
            // behalf when the sender says so.
            ActivityOptions o = ActivityOptions.makeBasic();
            o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            options = o.toBundle();
        }
        try {
            target.send(from, 0, null, null, null, null, options);
        } catch (PendingIntent.CanceledException e) {
            return false;
        }
        if ((n.flags & Notification.FLAG_AUTO_CANCEL) != 0) dismiss(sbn);
        return true;
    }

    static void dismiss(StatusBarNotification sbn) {
        NotificationsService service = connected;
        if (service == null) return;
        try {
            service.cancelNotification(sbn.getKey());
        } catch (RuntimeException ignored) {
        }
    }

    /** Clears every clearable notification, as the stock panel's Clear all did. */
    static void clearAll() {
        NotificationsService service = connected;
        if (service == null) return;
        try {
            service.cancelAllNotifications();
        } catch (RuntimeException ignored) {
        }
    }
}
