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

    /** Bluetooth ses cihazı bağlanınca adına bakıp arabaysa "Arabada" moduna geçer. */
    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            for (AudioDeviceInfo d : added) {
                if (!d.isSink() || d.getType() != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) continue;
                String name = String.valueOf(d.getProductName());
                if (Car.looksLikeCar(name) && carDevices.add(d.getId()) && engine != null) {
                    engine.onCarConnected(name);
                }
            }
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            for (AudioDeviceInfo d : removed) {
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
        if (audio != null) audio.registerAudioDeviceCallback(deviceCallback, handler);
    }

    @Override
    public void onListenerDisconnected() {
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

    private void attach(List<MediaController> list) {
        if (list == null) return;
        MediaController ytm = null;
        for (MediaController c : list) {
            if (Engine.YTM.equals(c.getPackageName())) { ytm = c; break; }
        }
        if (ytm == null) return; // YT Music kapalı; açılınca tekrar haber gelir
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
