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
import android.media.PlaybackParams;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;

import com.google.android.media.tv.companionlibrary.TvPlayer;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;

/**
 * A wrapper around ExoPlayer which implements TvPlayer. This is the class that actually renders
 * the video, subtitles and all these sorts of things.
 */
public class AppPlayer implements TvPlayer {
    private Context context;
    private Uri videoUrl;
    private LibVLC libVlc;
    private MediaPlayer player;

    /**
     * AppPlayer constructor
     * @param context  Context
     * @param videoUrl Video stream URL
     */
    public AppPlayer(Context context, Uri videoUrl) {
        this.context = context;
        this.videoUrl = videoUrl;

        final ArrayList<String> args = new ArrayList<>();
        args.add("--file-caching=2000");
        args.add("-v");
        libVlc = new LibVLC(context, args);
        player = new MediaPlayer(libVlc);
    }


    /**
     * Prepare player
     */
    public void prepare() {
        final Media media = new Media(libVlc, videoUrl);
        media.setHWDecoderEnabled(true, false);
        media.addOption(":network-caching=150");
        media.addOption(":clock-jitter=0");
        media.addOption(":clock-synchro=0");

        player.setMedia(media);
    }


    /**
     * Release player
     */
    public void release() {
        stop();
        player.getMedia().release();
        player.getVLCVout().detachViews();
        player.release();
        libVlc.release();
    }


    /**
     * Set surface
     * @param surface Video surface
     */
    @Override
    public void setSurface(Surface surface) {
        Log.d("VLC_EVENT", "setSurface called with " + surface);
        final IVLCVout vlcVout = player.getVLCVout();

        if (surface != null) {
            vlcVout.setVideoSurface(surface, null);
            vlcVout.setWindowSize(1920, 1080); // TODO
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
        // TODO
    }


    @Override
    public void registerCallback(Callback callback) {
        // TODO
    }


    @Override
    public void unregisterCallback(Callback callback) {
        // TODO
    }
}
