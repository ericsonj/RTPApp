package net.ericsonj.rtpapp;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Created by ericson on 2/15/18.
 */

public class MainService extends Service {

    public static final String TAG = MainService.class.getSimpleName();

    /**
     * RTPManager
     */
    private RTPManager rtp;

    /**
     * SERVICE
     */
    private MyServiceBinder binder;
    private boolean isStart;
    private AudioManager audioManager;
    private boolean isRun;

    public MainService() {
        isRun = false;
        isStart = false;
        binder = new MyServiceBinder();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class MyServiceBinder extends Binder {
        public MainService getService() {
            Log.d(TAG, "getService()");
            return MainService.this;
        }
    }

    public boolean isRun() {
        return isRun;
    }

    public void setRun(boolean run) {
        isRun = run;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isStart) {
            Log.d(TAG, "INIT SERVICE");
            isStart = true;
        } else {
            Log.d(TAG, "onStartCommand is run");
        }
        return START_STICKY;
    }

    public void initAudio() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(true);
        audioManager.setMicrophoneMute(true);
    }

    public void startAudioManager(boolean isCCO, String name, String id) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String rtpserver = prefs.getString(getString(R.string.pref_key_rtpserver), "ericsonj.net");
        String rtpserverport = prefs.getString(getString(R.string.pref_key_rtpserverport), "19000");

        Log.d(TAG, "Server: " + rtpserver);
        Log.d(TAG, "Server port: " + rtpserverport);

        rtp = new RTPManager(name, id, rtpserver, Integer.parseInt(rtpserverport));
        rtp.setSendAudio(false);
    }

    public void activeMicMute(boolean active) {
        rtp.setSendAudio(!active);
        audioManager.setMicrophoneMute(active);
    }

    public void activeSpeaker(boolean active) {
        audioManager.setSpeakerphoneOn(active);
    }

}
