package com.rieman.softplayer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Size;
import android.view.Window;
import android.view.WindowManager;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@CapacitorPlugin(name = "Music", permissions = {
        @Permission(strings = {"android.permission.READ_MEDIA_AUDIO"}, alias = "audio13"),
        @Permission(strings = {"android.permission.READ_EXTERNAL_STORAGE"}, alias = "audioOld"),
        @Permission(strings = {"android.permission.POST_NOTIFICATIONS"}, alias = "notif")
})
public class MusicPlugin extends Plugin {

    static class Song {
        long id;
        String title, artist, album, uri;
    }

    static MusicPlugin inst;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Map<Long, Song> byId = new ConcurrentHashMap<>();
    private final List<Long> queue = new ArrayList<>();
    private final Random rnd = new Random();
    private MediaPlayer mp;
    private boolean prepared = false;
    private boolean ended = false;
    private int index = -1;
    private long curId = -1;
    private boolean shuffle = false;
    private int repeat = 0;
    private float speed = 1f;
    private long sleepAt = 0;

    private final Runnable sleepTask = () -> {
        try { if (mp != null && prepared && mp.isPlaying()) mp.pause(); } catch (Exception e) { }
        sleepAt = 0;
        stopSvc();
    };

    private final AudioManager.OnAudioFocusChangeListener fl = ch -> {
        if ((ch == AudioManager.AUDIOFOCUS_LOSS || ch == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) && mp != null && prepared) {
            try { if (mp.isPlaying()) { mp.pause(); stopSvc(); } } catch (Exception e) { }
        }
    };

    static void stopAll() {
        final MusicPlugin p = inst;
        if (p != null) p.h.post(p::killNow);
    }

    private void killNow() {
        try { if (mp != null) { mp.release(); mp = null; } } catch (Exception e) { }
        prepared = false;
        curId = -1;
    }

    // ---------- permissions + library ----------
    private String audioAlias() { return Build.VERSION.SDK_INT >= 33 ? "audio13" : "audioOld"; }

    @PluginMethod
    public void getSongs(PluginCall call) {
        inst = this;
        String a = audioAlias();
        if (getPermissionState(a) != PermissionState.GRANTED) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissionForAliases(new String[]{a, "notif"}, call, "permDone");
            else requestPermissionForAlias(a, call, "permDone");
            return;
        }
        load(call);
    }

    @PermissionCallback
    private void permDone(PluginCall call) {
        if (getPermissionState(audioAlias()) == PermissionState.GRANTED) load(call);
        else call.reject("denied");
    }

    private void load(final PluginCall call) {
        new Thread(() -> {
            try {
                Uri src = Build.VERSION.SDK_INT >= 29
                        ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                        : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                String[] cols = {
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DISPLAY_NAME};
                String sel = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " + MediaStore.Audio.Media.DURATION + " >= 15000";
                JSArray arr = new JSArray();
                byId.clear();
                Cursor c = getContext().getContentResolver().query(src, cols, sel, null,
                        MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
                if (c != null) {
                    try {
                        while (c.moveToNext()) {
                            Song s = new Song();
                            s.id = c.getLong(0);
                            s.title = c.getString(1);
                            s.artist = c.getString(2);
                            s.album = c.getString(3);
                            s.uri = android.content.ContentUris.withAppendedId(src, s.id).toString();
                            byId.put(s.id, s);
                            JSObject o = new JSObject();
                            o.put("id", s.id);
                            o.put("title", s.title == null ? "Unknown" : s.title);
                            o.put("artist", s.artist == null ? "" : s.artist);
                            o.put("album", s.album == null ? "" : s.album);
                            o.put("dur", c.getLong(4));
                            o.put("added", c.getLong(5));
o.put("path", c.getString(6) == null ? "" : c.getString(6));
o.put("name", c.getString(7) == null ? "" : c.getString(7));
                            arr.put(o);
                        }
                    } finally {
                        c.close();
                    }
                }
                JSObject r = new JSObject();
                r.put("songs", arr);
                call.resolve(r);
            } catch (Exception e) {
                call.reject(e.getMessage() == null ? "error" : e.getMessage());
            }
        }).start();
    }

    // ---------- album art ----------
    @PluginMethod
    public void getArt(final PluginCall call) {
        final long id = call.getData().optLong("id", -1);
        final int size = call.getData().optInt("size", 160);
        new Thread(() -> {
            JSObject o = new JSObject();
            try {
                java.io.File cf = new java.io.File(getContext().getCacheDir(), "art_" + id + "_" + size + ".jpg");
                if (cf.exists() && cf.length() > 0) {
                    byte[] cb = new byte[(int) cf.length()];
                    java.io.DataInputStream di = new java.io.DataInputStream(new java.io.FileInputStream(cf));
                    di.readFully(cb);
                    di.close();
                    o.put("img", "data:image/jpeg;base64," + Base64.encodeToString(cb, Base64.NO_WRAP));
                    call.resolve(o);
                    return;
                }
                Song s = byId.get(id);
                if (s != null) {
                    Bitmap b = null;
                    Uri u = Uri.parse(s.uri);
                    if (Build.VERSION.SDK_INT >= 29) {
                        try { b = getContext().getContentResolver().loadThumbnail(u, new Size(size, size), null); }
                        catch (Exception e) { }
                    }
                    if (b == null) {
                        MediaMetadataRetriever r = new MediaMetadataRetriever();
                        try {
                            r.setDataSource(getContext(), u);
                            byte[] d = r.getEmbeddedPicture();
                            if (d != null) b = decode(d, size);
                        } catch (Exception e) {
                        } finally {
                            try { r.release(); } catch (Exception e) { }
                        }
                    }
                    if (b != null) {
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        b.compress(Bitmap.CompressFormat.JPEG, 82, bo);
                        try { java.io.FileOutputStream fo = new java.io.FileOutputStream(cf); fo.write(bo.toByteArray()); fo.close(); } catch (Exception e) { }
                        o.put("img", "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP));
                    }
                }
            } catch (Exception e) { }
            call.resolve(o);
        }).start();
    }

    private Bitmap decode(byte[] d, int target) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(d, 0, d.length, o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= target) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(d, 0, d.length, o2);
    }

    // ---------- playback ----------
    private void post(final PluginCall call, final Runnable r) {
        h.post(() -> {
            try { r.run(); } catch (Exception e) { }
            call.resolve(state());
        });
    }

    private JSObject state() {
        JSObject o = new JSObject();
        boolean pl = false;
        int pos = 0, dur = 0;
        try {
            if (mp != null && prepared) {
                pl = mp.isPlaying();
                pos = mp.getCurrentPosition();
                dur = mp.getDuration();
            }
        } catch (Exception e) { }
        o.put("id", curId);
        o.put("playing", pl);
        o.put("pos", pos);
        o.put("dur", dur);
        o.put("shuffle", shuffle);
        o.put("repeat", repeat);
        o.put("ended", ended);
        o.put("speed", (double) speed);
        o.put("vol", volPct());
        o.put("sleep", sleepAt > 0 ? Math.max(0, sleepAt - System.currentTimeMillis()) : 0);
        return o;
    }

    private void focus() {
        try {
            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            am.requestAudioFocus(fl, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        } catch (Exception e) { }
    }

    private void applySpeed() {
        if (Build.VERSION.SDK_INT >= 23 && mp != null && prepared) {
            try {
                boolean was = mp.isPlaying();
                PlaybackParams p = mp.getPlaybackParams();
                p.setSpeed(speed);
                mp.setPlaybackParams(p);
                if (!was && mp.isPlaying()) mp.pause();
            } catch (Exception e) { }
        }
    }

    private void startSvc(final Song s) {
        PlayerService.title = s.title == null ? "Rounder Music" : s.title;
        PlayerService.artist = s.artist == null ? "" : s.artist;
        PlayerService.playing = true;
        if (PlayerService.artId != s.id) {
            PlayerService.art = null;
            PlayerService.artId = s.id;
        }
        pushState();
        if (PlayerService.art == null) {
            new Thread(() -> {
                Bitmap b = notifArt(s);
                if (b != null && curId == s.id) {
                    PlayerService.art = b;
                    h.post(this::pushState);
                }
            }).start();
        }
    }

    private void pushState() {
        try {
            PlayerService.pos = (mp != null && prepared) ? mp.getCurrentPosition() : 0;
            PlayerService.dur = (mp != null && prepared) ? mp.getDuration() : 0;
            PlayerService.speed = speed;
            if (PlayerService.me != null) {
                PlayerService.me.refresh();
            } else if (PlayerService.playing) {
                Intent i = new Intent(getContext(), PlayerService.class);
                if (Build.VERSION.SDK_INT >= 26) getContext().startForegroundService(i);
                else getContext().startService(i);
            }
        } catch (Exception e) { }
    }

    private Bitmap notifArt(Song s) {
        try {
            java.io.File cf = new java.io.File(getContext().getCacheDir(), "art_" + s.id + "_600.jpg");
            if (cf.exists() && cf.length() > 0) return BitmapFactory.decodeFile(cf.getAbsolutePath());
            Bitmap b = null;
            Uri u = Uri.parse(s.uri);
            if (Build.VERSION.SDK_INT >= 29) {
                try { b = getContext().getContentResolver().loadThumbnail(u, new Size(300, 300), null); }
                catch (Exception e) { }
            }
            if (b == null) {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                    r.setDataSource(getContext(), u);
                    byte[] d = r.getEmbeddedPicture();
                    if (d != null) b = decode(d, 300);
                } catch (Exception e) {
                } finally {
                    try { r.release(); } catch (Exception e) { }
                }
            }
            return b;
        } catch (Exception e) { return null; }
    }

    static void cmd(final String c, final int arg) {
        final MusicPlugin p = inst;
        if (p == null) return;
        p.h.post(() -> p.doCmd(c, arg));
    }

    private void doCmd(String c, int arg) {
        try {
            if (c.equals("toggle")) doToggle();
            else if (c.equals("play")) { if (mp != null && prepared && !mp.isPlaying()) doToggle(); }
            else if (c.equals("pause")) { if (mp != null && prepared && mp.isPlaying()) doToggle(); }
            else if (c.equals("next")) doNext();
            else if (c.equals("prev")) doPrev();
            else if (c.equals("seek")) { if (mp != null && prepared) { mp.seekTo(arg); pushState(); } }
        } catch (Exception e) { }
    }

    private void doToggle() {
        if (mp == null || !prepared) return;
        if (mp.isPlaying()) {
            mp.pause();
            stopSvc();
        } else {
            focus();
            int d = mp.getDuration();
            if (d > 0 && mp.getCurrentPosition() >= d - 300) mp.seekTo(0);
            ended = false;
            mp.start();
            applySpeed();
            Song s = byId.get(curId);
            if (s != null) startSvc(s);
        }
    }

    private void doNext() {
        if (queue.isEmpty()) return;
        if (shuffle && queue.size() > 1) playIndex(randomOther(), 0);
        else playIndex((index + 1) % queue.size(), 0);
    }

    private void doPrev() {
        if (queue.isEmpty()) return;
        if (prepared && mp.getCurrentPosition() > 3000) { mp.seekTo(0); pushState(); return; }
        playIndex((index - 1 + queue.size()) % queue.size(), 0);
    }

    private void startSvcOld(Song s) {
        try {
            Intent i = new Intent(getContext(), PlayerService.class);
            i.putExtra("title", s.title);
            i.putExtra("artist", s.artist == null ? "" : s.artist);
            if (Build.VERSION.SDK_INT >= 26) getContext().startForegroundService(i);
            else getContext().startService(i);
        } catch (Exception e) { }
    }

    private void stopSvc() {
        PlayerService.playing = false;
        pushState();
    }

    private void stopSvcOld() {
        try { getContext().stopService(new Intent(getContext(), PlayerService.class)); } catch (Exception e) { }
    }

    private void playIndex(int i, int tries) {
        if (i < 0 || i >= queue.size()) return;
        Song s = byId.get(queue.get(i));
        if (s == null) return;
        try {
            if (mp == null) {
                mp = new MediaPlayer();
                mp.setOnCompletionListener(m -> onDone());
                mp.setOnErrorListener((m, w, e) -> { prepared = false; return true; });
            }
            mp.reset();
            prepared = false;
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            mp.setWakeMode(getContext(), PowerManager.PARTIAL_WAKE_LOCK);
            mp.setDataSource(getContext(), Uri.parse(s.uri));
            mp.prepare();
            prepared = true;
            ended = false;
            index = i;
            curId = s.id;
            focus();
            mp.start();
            applySpeed();
            startSvc(s);
        } catch (Exception e) {
            prepared = false;
            if (tries < 5 && queue.size() > 1) playIndex((i + 1) % queue.size(), tries + 1);
        }
    }

    private int randomOther() {
        if (queue.size() < 2) return 0;
        int n;
        do { n = rnd.nextInt(queue.size()); } while (n == index);
        return n;
    }

    private void onDone() {
        if (repeat == 2) { playIndex(index, 0); return; }
        if (shuffle && queue.size() > 1) { playIndex(randomOther(), 0); return; }
        if (index + 1 < queue.size()) playIndex(index + 1, 0);
        else if (repeat == 1 && !queue.isEmpty()) playIndex(0, 0);
        else { ended = true; stopSvc(); }
    }

    @PluginMethod
    public void setQueue(PluginCall call) {
        final JSArray ids = call.getArray("ids");
        final long start = call.getData().optLong("start", -1);
        post(call, () -> {
            queue.clear();
            try {
                for (int i = 0; i < ids.length(); i++) {
                    long id = ids.getLong(i);
                    if (byId.containsKey(id)) queue.add(id);
                }
            } catch (Exception e) { }
            int i = queue.indexOf(start);
            if (i < 0) i = 0;
            playIndex(i, 0);
        });
    }

    @PluginMethod
    public void playId(PluginCall call) {
        final long id = call.getData().optLong("id", -1);
        post(call, () -> {
            int i = queue.indexOf(id);
            if (i >= 0) playIndex(i, 0);
        });
    }

    private int volPct() {
        try {
            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            int mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            return mx > 0 ? Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / mx) : 0;
        } catch (Exception e) { return 0; }
    }

    @PluginMethod
    public void setVolume(PluginCall call) {
        final int pct = call.getData().optInt("pct", 50);
        post(call, () -> {
            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            int mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(Math.max(0, Math.min(100, pct)) * mx / 100f), 0);
        });
    }

    @PluginMethod
    public void setMeta(PluginCall call) {
        final long id = call.getData().optLong("id", -1);
        final String t = call.getString("title");
        final String a = call.getString("artist");
        final String al = call.getString("album");
        post(call, () -> {
            Song s = byId.get(id);
            if (s == null) return;
            if (t != null && !t.isEmpty()) s.title = t;
            s.artist = a;
            s.album = al;
        });
    }

    @PluginMethod
    public void getInfo(final PluginCall call) {
        final long id = call.getData().optLong("id", -1);
        new Thread(() -> {
            JSObject r = new JSObject();
            try {
                Uri src = Build.VERSION.SDK_INT >= 29
                        ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                        : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                Uri u = android.content.ContentUris.withAppendedId(src, id);
                String[] cols = {MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.DISPLAY_NAME};
                Cursor c = getContext().getContentResolver().query(u, cols, null, null, null);
                if (c != null) {
                    try {
                        if (c.moveToFirst()) {
                            r.put("size", c.getLong(0));
                            r.put("path", c.getString(1) == null ? "" : c.getString(1));
                            r.put("mime", c.getString(2) == null ? "" : c.getString(2));
                            r.put("name", c.getString(3) == null ? "" : c.getString(3));
                        }
                    } finally { c.close(); }
                }
                try {
                    android.media.MediaMetadataRetriever m = new android.media.MediaMetadataRetriever();
                    m.setDataSource(getContext(), u);
                    String br = m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE);
                    if (br != null) r.put("bitrate", Long.parseLong(br));
                    m.release();
                } catch (Exception e) { }
            } catch (Exception e) { }
            call.resolve(r);
        }).start();
    }

    @PluginMethod
    public void getState(PluginCall call) { post(call, () -> { }); }

    @PluginMethod
    public void toggle(PluginCall call) {
        post(call, () -> {
            if (mp == null || !prepared) return;
            if (mp.isPlaying()) {
                mp.pause();
                stopSvc();
            } else {
                focus();
                int d = mp.getDuration();
                if (d > 0 && mp.getCurrentPosition() >= d - 300) mp.seekTo(0);
                mp.start();
                applySpeed();
                Song s = byId.get(curId);
                if (s != null) startSvc(s);
            }
        });
    }

    @PluginMethod
    public void next(PluginCall call) {
        post(call, () -> {
            if (queue.isEmpty()) return;
            if (shuffle && queue.size() > 1) playIndex(randomOther(), 0);
            else playIndex((index + 1) % queue.size(), 0);
        });
    }

    @PluginMethod
    public void prev(PluginCall call) {
        post(call, () -> {
            if (queue.isEmpty()) return;
            if (prepared && mp.getCurrentPosition() > 3000) { mp.seekTo(0); return; }
            playIndex((index - 1 + queue.size()) % queue.size(), 0);
        });
    }

    @PluginMethod
    public void seek(PluginCall call) {
        final int ms = call.getData().optInt("ms", 0);
        post(call, () -> { if (mp != null && prepared) { mp.seekTo(ms); pushState(); } });
    }

    @PluginMethod
    public void setShuffle(PluginCall call) {
        final boolean on = call.getData().optBoolean("on", false);
        post(call, () -> shuffle = on);
    }

    @PluginMethod
    public void setRepeat(PluginCall call) {
        final int m = call.getData().optInt("mode", 0);
        post(call, () -> repeat = m);
    }

    @PluginMethod
    public void setSpeed(PluginCall call) {
        final float sp = (float) call.getData().optDouble("speed", 1.0);
        post(call, () -> { speed = sp; applySpeed(); });
    }

    @PluginMethod
    public void setSleep(PluginCall call) {
        final int m = call.getData().optInt("minutes", 0);
        post(call, () -> {
            h.removeCallbacks(sleepTask);
            if (m > 0) {
                sleepAt = System.currentTimeMillis() + m * 60000L;
                h.postDelayed(sleepTask, m * 60000L);
            } else {
                sleepAt = 0;
            }
        });
    }

    // ---------- write physical tags ----------
@PluginMethod
public void writeTags(PluginCall call) {
    final long id = call.getData().optLong("id", -1);
    final String title = call.getString("title");
    final String artist = call.getString("artist");
    final String album = call.getString("album");
    final String coverBase64 = call.getString("cover");

    new Thread(() -> {
        try {
            Song s = byId.get(id);
            if (s == null) { call.reject("Song not found"); return; }
            
            Uri src = Build.VERSION.SDK_INT >= 29 ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            Uri u = android.content.ContentUris.withAppendedId(src, id);
            Cursor c = getContext().getContentResolver().query(u, new String[]{MediaStore.Audio.Media.DATA}, null, null, null);
            String path = null;
            if (c != null) { if (c.moveToFirst()) path = c.getString(0); c.close(); }
            
            if (path == null || path.isEmpty()) { call.reject("File path not found"); return; }

            java.io.File file = new java.io.File(path);
            if (!file.exists()) { call.reject("File does not exist"); return; }
            if (!file.canWrite()) { call.reject("No write permission. Please grant 'All Files Access' in settings."); return; }

            // Use jaudiotagger to write to the file
            org.jaudiotagger.audio.AudioFile f = org.jaudiotagger.audio.AudioFileIO.read(file);
            org.jaudiotagger.tag.Tag tag = f.getTag();
            if (tag == null) { tag = f.createDefaultTag(); f.setTag(tag); }

            if (title != null) tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, title);
            if (artist != null) tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, artist);
            if (album != null) tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, album);

            if (coverBase64 != null && !coverBase64.isEmpty()) {
                byte[] imgData = Base64.decode(coverBase64, Base64.DEFAULT);
                tag.deleteArtworkField();
                org.jaudiotagger.tag.images.Artwork artwork = org.jaudiotagger.tag.images.ArtworkFactory.getNew();
                artwork.setBinaryData(imgData);
                artwork.setMimeType("image/jpeg");
                artwork.setDescription("Cover");
                tag.setField(artwork);
            }

            org.jaudiotagger.audio.AudioFileIO.write(f);
            
            // Clear cache so the app loads the new image immediately
            java.io.File cf = new java.io.File(getContext().getCacheDir(), "art_" + id + "_600.jpg");
            if (cf.exists()) cf.delete();

            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage() == null ? "Write error" : e.getMessage());
        }
    }).start();
}

// ---------- status bar + navigation bar colors ----------
    @PluginMethod
    public void setBars(final PluginCall call) {
        final String top = call.getString("top");
        final String bot = call.getString("bottom");
        final Activity a = getActivity();
        if (a == null || top == null || bot == null) { call.resolve(); return; }
        a.runOnUiThread(() -> {
            try {
                int t = Color.parseColor(top);
                int b = Color.parseColor(bot);
                Window w = a.getWindow();
                w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                w.setStatusBarColor(t);
                w.setNavigationBarColor(b);
                w.setBackgroundDrawable(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{t, b}));
                if (Build.VERSION.SDK_INT >= 28) w.setNavigationBarDividerColor(Color.TRANSPARENT);
                if (Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);
                getContext().getSharedPreferences("sp", 0).edit().putInt("top", t).putInt("bot", b).apply();
            } catch (Exception e) { }
        });
        call.resolve();
    }
}