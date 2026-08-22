package moe.rukamori.archivetune.visualizer;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;

/**
 * Manages audio device callbacks and latency compensation settings.
 */
public class AudioDeviceManager extends AudioDeviceCallback {


    private final Runnable latencyCallback;

    public AudioDeviceManager(Context unused, Runnable latencyCallback) {
        this.latencyCallback = latencyCallback;
    }

    @Override
    public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
        latencyCallback.run();
    }

    @Override
    public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
        latencyCallback.run();
    }

}



