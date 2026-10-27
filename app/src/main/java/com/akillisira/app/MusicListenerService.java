package com.akillisira.app;

import android.content.ComponentName;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;

import java.util.List;

/**
 * Bildirim erişimi verildiğinde Android bu servisi kendisi başlatır.
 * Buradan YT Music'in medya oturumunu izleriz: hangi şarkı çalıyor, kaçıncı saniyede.
 */
public class MusicListenerService extends NotificationListenerService {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaSessionManager sessions;
    private MediaController controller;
    private Engine engine;
    private boolean polling;
    private AudioManager audio;
    private final java.util.Set<Integer> carDevices = new java.util.HashSet<>();
    private final java.util.Set<Integer> hpDevices = new java.util.HashSet<>();
    private long devicesRegisteredAt;

    /** Bluetooth ses cihazı bağlanınca adına bakıp arabaysa "Arabada" moduna geçer. */
    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            // Kayıt anında mevcut cihazlar da bildirilir: onları "yeni takıldı" sayma
            boolean initial = android.os.SystemClock.elapsedRealtime() - devicesRegisteredAt < 4000;
            for (AudioDeviceInfo d : added) {
                if (!d.isSink()) continue;
                String name = String.valueOf(d.getProductName());
                int type = d.getType();
                boolean bt = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
                if (bt && Car.looksLikeCar(name)) {
                    if (carDevices.add(d.getId()) && engine != null) engine.onCarConnected(name);
                    continue;
                }
                boolean hp = bt || type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        || type == AudioDeviceInfo.TYPE_USB_HEADSET || type == 26 /* TYPE_BLE_HEADSET */;
                if (hp && hpDevices.add(d.getId()) && !initial && engine != null) engine.onHeadphonesConnected(name);
            }
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            for (AudioDeviceInfo d : removed) {
                hpDevices.remove(d.getId());
                if (carDevices.remove(d.getId()) && carDevices.isEmpty() && engine != null) {
                    engine.onCarDisconnected();
                }
            }
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            this::attach;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            readMetadata(metadata);
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            startPollingIfNeeded();
        }

        @Override
        public void onSessionDestroyed() {
            detach();
        }
    };

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            polling = false;
            if (controller == null) return;
            PlaybackState st = controller.getPlaybackState();
            boolean isPlaying = st != null && st.getState() == PlaybackState.STATE_PLAYING;
            long pos = 0;
            if (st != null) {
                pos = st.getPosition();
                if (isPlaying && st.getLastPositionUpdateTime() > 0) {
                    pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime())
                            * st.getPlaybackSpeed());
                }
            }
            long dur = 0;
            MediaMetadata md = controller.getMetadata();
            if (md != null) dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            engine.onPoll(Math.max(0, pos), dur, isPlaying);
            if (isPlaying) {
                polling = true;
                handler.postDelayed(this, 500);
            }
        }
    };

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        engine = Engine.get(this);
        current = this;
        engine.setServiceConnected(true);
        sessions = getSystemService(MediaSessionManager.class);
        ComponentName me = new ComponentName(this, MusicListenerService.class);
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsListener, me, handler);
            attach(sessions.getActiveSessions(me));
        } catch (SecurityException e) {
            engine.log("Bildirim erişimi yok: " + e.getMessage());
        }
        audio = getSystemService(AudioManager.class);
        devicesRegisteredAt = android.os.SystemClock.elapsedRealtime();
        if (audio != null) audio.registerAudioDeviceCallback(deviceCallback, handler);
    }

    @Override
    public void onListenerDisconnected() {
        if (current == this) current = null;
        if (sessions != null) {
            try { sessions.removeOnActiveSessionsChangedListener(sessionsListener); } catch (Exception ignored) {}
        }
        detach();
        if (audio != null) {
            try { audio.unregisterAudioDeviceCallback(deviceCallback); } catch (Exception ignored) {}
        }
        if (engine != null) engine.setServiceConnected(false);
        super.onListenerDisconnected();
    }

    /** Ayarlardan müzik uygulaması değişince yeniden bağlan. */
    static volatile MusicListenerService current;

    static void reattachNow() {
        MusicListenerService s = current;
        if (s == null || s.sessions == null) return;
        s.handler.post(() -> {
            try {
                s.attach(s.sessions.getActiveSessions(new ComponentName(s, MusicListenerService.class)));
            } catch (Exception ignored) {}
        });
    }

    private static boolean isPlaying(MediaController c) {
        PlaybackState st = c.getPlaybackState();
        return st != null && st.getState() == PlaybackState.STATE_PLAYING;
    }

    private static boolean accepts(String pref, String pk) {
        if (Engine.PLAYER_YTM.equals(pref)) return Engine.YTM.equals(pk);
        if (Engine.PLAYER_SPOTIFY.equals(pref)) return Engine.SPOTIFY.equals(pk);
        return Engine.YTM.equals(pk) || Engine.SPOTIFY.equals(pk);
    }

    private void attach(List<MediaController> list) {
        if (list == null) return;
        // Desteklenen uygulamalar: ayara göre sadece biri ya da (otomatik) hangisi çalıyorsa
        String pref = engine != null ? engine.getPlayerPref() : Engine.PLAYER_AUTO;
        MediaController ytm = null, playingOne = null, firstOne = null;
        for (MediaController c : list) {
            if (!accepts(pref, c.getPackageName())) continue;
            if (firstOne == null) firstOne = c;
            if (playingOne == null && isPlaying(c)) playingOne = c;
        }
        ytm = playingOne != null ? playingOne : firstOne;
        // Otomatik modda çalmayan bir uygulamaya, çalan bağlıyken geçme
        if (playingOne == null && controller != null && isPlaying(controller)
                && accepts(pref, controller.getPackageName())) return;
        if (ytm == null) return; // desteklenen uygulama kapalı; açılınca tekrar haber gelir
        if (controller != null && controller.getSessionToken().equals(ytm.getSessionToken())) return;

        detach();
        controller = ytm;
        controller.registerCallback(callback, handler);
        engine.setController(controller);
        readMetadata(controller.getMetadata());
        startPollingIfNeeded();
    }

    private void detach() {
        if (controller != null) {
            try { controller.unregisterCallback(callback); } catch (Exception ignored) {}
            controller = null;
            if (engine != null) engine.setController(null);
        }
        handler.removeCallbacks(poll);
        polling = false;
    }

    private void readMetadata(MediaMetadata md) {
        if (md == null || engine == null) return;
        String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null || artist.isEmpty()) artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        engine.onSongChanged(title, artist, dur);
        startPollingIfNeeded();
    }

    private void startPollingIfNeeded() {
        if (!polling && controller != null) {
            polling = true;
            handler.post(poll);
        }
    }
}
