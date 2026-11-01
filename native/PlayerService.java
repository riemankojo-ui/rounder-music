package com.rieman.softplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

public class PlayerService extends Service {
    static final String CH = "rounder_player_v2";
    static PlayerService me;
    static String title = "Rounder Music", artist = "";
    static Bitmap art;
    static long artId = -1;
    static boolean playing;
    static long pos, dur;
    static float speed = 1f;

    private MediaSession session;
    private boolean fg = false;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        me = this;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CH, "Playback", NotificationManager.IMPORTANCE_LOW);
            c.setShowBadge(false);
            c.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            getSystemService(NotificationManager.class).deleteNotificationChannel("rounder_player");
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
        session = new MediaSession(this, "RounderMusic");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { MusicPlugin.cmd("play", 0); }
            @Override public void onPause() { MusicPlugin.cmd("pause", 0); }
            @Override public void onSkipToNext() { MusicPlugin.cmd("next", 0); }
            @Override public void onSkipToPrevious() { MusicPlugin.cmd("prev", 0); }
            @Override public void onSeekTo(long p) { MusicPlugin.cmd("seek", (int) p); }
        });
        session.setActive(true);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        String a = i == null ? null : i.getAction();
        if (a != null && MusicPlugin.inst == null) {
            try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(1); } catch (Exception e) { }
            stopSelf();
            return START_NOT_STICKY;
        }
        if ("sp.prev".equals(a)) MusicPlugin.cmd("prev", 0);
        else if ("sp.pp".equals(a)) MusicPlugin.cmd("toggle", 0);
        else if ("sp.next".equals(a)) MusicPlugin.cmd("next", 0);
        refresh();
        return START_NOT_STICKY;
    }

    void refresh() {
        try {
            sync();
            Notification n = build();
            if (playing) {
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } else {
                    startForeground(1, n);
                }
                fg = true;
            } else {
                if (fg) {
                    if (Build.VERSION.SDK_INT >= 24) stopForeground(Service.STOP_FOREGROUND_DETACH);
                    else stopForeground(false);
                    fg = false;
                }
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, n);
            }
        } catch (Exception e) { }
    }

    private void sync() {
        MediaMetadata.Builder mb = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, dur);
        // cover is not sent to the system, so the card keeps one fixed colour
        session.setMetadata(mb.build());
        long acts = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_SEEK_TO;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(acts)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        pos, playing ? speed : 0f)
                .build());
    }

    private Notification build() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.setSmallIcon(getResources().getIdentifier("ic_stat_s", "drawable", getPackageName()) != 0 ? getResources().getIdentifier("ic_stat_s", "drawable", getPackageName()) : android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(pi)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        if (art != null) b.setLargeIcon(art);
        b.setColor(0xFFB9A7FF);
        if (Build.VERSION.SDK_INT >= 26) b.setColorized(false);
        b.addAction(act(ico("ic_n_prev", android.R.drawable.ic_media_previous), "Previous", "sp.prev", 1));
        b.addAction(act(playing ? ico("ic_n_pause", android.R.drawable.ic_media_pause) : ico("ic_n_play", android.R.drawable.ic_media_play),
                playing ? "Pause" : "Play", "sp.pp", 2));
        b.addAction(act(ico("ic_n_next", android.R.drawable.ic_media_next), "Next", "sp.next", 3));
        b.setStyle(new Notification.MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }

    private int ico(String name, int fallback) {
        int id = getResources().getIdentifier(name, "drawable", getPackageName());
        return id != 0 ? id : fallback;
    }

    private Notification.Action act(int icon, String text, String action, int code) {
        Intent i = new Intent(this, PlayerService.class).setAction(action);
        PendingIntent p = PendingIntent.getService(this, code, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(icon, text, p).build();
    }

    @Override
    public void onTaskRemoved(Intent root) {
        MusicPlugin.stopAll();
        stopSelf();
        super.onTaskRemoved(root);
    }

    @Override
    public void onDestroy() {
        try { stopForeground(true); } catch (Exception e) { }
        try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(1); } catch (Exception e) { }
        try { session.setActive(false); session.release(); } catch (Exception e) { }
        me = null;
        super.onDestroy();
    }
}