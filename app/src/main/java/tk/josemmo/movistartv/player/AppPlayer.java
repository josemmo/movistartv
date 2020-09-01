/*
 * Copyright 2016 The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tk.josemmo.movistartv.player;

import android.content.Context;
import android.content.res.Resources;
import android.media.PlaybackParams;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.Surface;

import com.google.android.media.tv.companionlibrary.TvPlayer;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;

import java.util.ArrayList;

/**
 * A wrapper around ExoPlayer which implements TvPlayer. This is the class that actually renders
 * the video, subtitles and all these sorts of things.
 */
public class AppPlayer implements TvPlayer {
    private LibVLC libVlc;
    private MediaPlayer player;

    /**
     * AppPlayer constructor
     * @param context Context
     */
    public AppPlayer(Context context) {
        ArrayList<String> options = new ArrayList<>();
        options.add("-vvv");

        // Stream-related options
        options.add("--http-reconnect");
        options.add("--clock-jitter=0");
        options.add("--clock-synchro=0");

        // Speed-up decoding in low-end devices
        options.add("--avcodec-hurry-up");
        options.add("--avcodec-skiploopfilter=4");
        options.add("--avcodec-skip-idct=4");
        options.add("--avcodec-fast");

        // Enable deinterlacing
        options.add("--deinterlace=1");
        options.add("--deinterlace-mode=yadif");

        libVlc = new LibVLC(context, options);
        player = new MediaPlayer(libVlc);
    }


    /**
     * Load media
     * @param mediaUri Media URI
     */
    public void loadMedia(String mediaUri) {
        loadMedia(Uri.parse(mediaUri));
    }


    /**
     * Load media
     * @param mediaUri Media URI
     */
    public void loadMedia(Uri mediaUri) {
        final Media media = new Media(libVlc, mediaUri);
        media.setHWDecoderEnabled(true, false);
        player.setMedia(media);
        media.release();
    }


    /**
     * Release player
     */
    public void release() {
        player.release();
        libVlc.release();
    }


    /**
     * Set surface
     * @param surface Video surface
     */
    @Override
    public void setSurface(Surface surface) {
        final IVLCVout vlcVout = player.getVLCVout();
        if (surface != null) {
            DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
            vlcVout.setVideoSurface(surface, null);
            vlcVout.setWindowSize(dm.widthPixels, dm.heightPixels);
            vlcVout.attachViews();
        } else {
            vlcVout.detachViews();
        }
    }


    /**
     * Get current position
     * @return Current position in milliseconds
     */
    @Override
    public long getCurrentPosition() {
        return (long) (player.getPosition() * 1000);
    }


    /**
     * Get duration
     * @return Duration in milliseconds
     */
    @Override
    public long getDuration() {
        return player.getLength();
    }


    /**
     * Start or resume player
     */
    @Override
    public void play() {
        player.play();
    }


    /**
     * Pause player
     */
    @Override
    public void pause() {
        player.pause();
    }


    /**
     * Stop player
     */
    public void stop() {
        player.stop();
    }


    /**
     * Seek to
     * @param position Position in milliseconds
     */
    @Override
    public void seekTo(long position) {
        float pos = (float) position;
        pos /= 1000;
        player.setPosition(pos);
    }


    /**
     * Set volume
     * @param volume Volume between 0 and 1
     */
    @Override
    public void setVolume(float volume) {
        player.setVolume((int) (volume * 100));
    }


    @Override
    public void setPlaybackParams(PlaybackParams params) {
        // This method is intentionally left blank
    }


    @Override
    public void registerCallback(Callback callback) {
        // This method is intentionally left blank
    }


    @Override
    public void unregisterCallback(Callback callback) {
        // This method is intentionally left blank
    }
}