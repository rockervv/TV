package com.fongmi.android.tv.dlna;

import android.content.Context;
import android.media.AudioManager;

import org.jupnp.model.types.UnsignedIntegerFourBytes;
import org.jupnp.model.types.UnsignedIntegerTwoBytes;
import org.jupnp.support.model.Channel;
import org.jupnp.support.renderingcontrol.AbstractAudioRenderingControl;

public class DLNARenderingControlImpl extends AbstractAudioRenderingControl {

    private final Context context;

    public DLNARenderingControlImpl(Context context) {
        this.context = context;
    }

    private AudioManager getAudioManager() {
        return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public UnsignedIntegerFourBytes[] getCurrentInstanceIds() {
        return new UnsignedIntegerFourBytes[]{getDefaultInstanceID()};
    }

    @Override
    public boolean getMute(UnsignedIntegerFourBytes instanceId, String channelName) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return getAudioManager().isStreamMute(AudioManager.STREAM_MUSIC);
        } else {
            return getAudioManager().getStreamVolume(AudioManager.STREAM_MUSIC) == 0;
        }
    }

    @Override
    public void setMute(UnsignedIntegerFourBytes instanceId, String channelName, boolean desiredMute) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getAudioManager().adjustStreamVolume(AudioManager.STREAM_MUSIC, desiredMute ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
        } else {
            getAudioManager().setStreamMute(AudioManager.STREAM_MUSIC, desiredMute);
        }
    }

    @Override
    public UnsignedIntegerTwoBytes getVolume(UnsignedIntegerFourBytes instanceId, String channelName) {
        AudioManager am = getAudioManager();
        int current = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return new UnsignedIntegerTwoBytes(max > 0 ? current * 100L / max : 100);
    }

    @Override
    public void setVolume(UnsignedIntegerFourBytes instanceId, String channelName, UnsignedIntegerTwoBytes desiredVolume) {
        AudioManager manager = getAudioManager();
        int max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int volume = (int) (desiredVolume.getValue() * max / 100);
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
    }

    @Override
    protected Channel[] getCurrentChannels() {
        return new Channel[]{Channel.Master};
    }
}
