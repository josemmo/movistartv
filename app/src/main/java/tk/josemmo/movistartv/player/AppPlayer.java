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
import android.view.Surface;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.media.tv.companionlibrary.TvPlayer;

/**
 * A wrapper around ExoPlayer which implements TvPlayer. This is the class that actually renders
 * the video, subtitles and all these sorts of things.
 */
public class AppPlayer implements TvPlayer {
    private Context context;
    private SimpleExoPlayer player;
    private Uri videoUrl;

    /**
     * AppPlayer constuctor
     * @param context  Context
     * @param videoUrl Video stream URL
     */
    public AppPlayer(Context context, Uri videoUrl) {
        this.context = context;
        this.player = ExoPlayerFactory.newSimpleInstance(context);
        this.videoUrl = videoUrl;
    }


    /**
     * Prepare player
     */
    public void prepare() {
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(context,
                Util.getUserAgent(context,"movistartv")
        );
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(videoUrl);

        player.prepare(mediaSource);
    }


    /**
     * Release player
     */
    public void release() {
        player.release();
    }


    /**
     * Set play when ready
     * @param playWhenReady Play when ready
     */
    public void setPlayWhenReady(boolean playWhenReady) {
        player.setPlayWhenReady(playWhenReady);
    }


    /**
     * Get current position
     * @return Current position in milliseconds
     */
    @Override
    public long getCurrentPosition() {
        return player.getCurrentPosition();
    }


    /**
     * Get duration
     * @return Duration in milliseconds
     */
    @Override
    public long getDuration() {
        return player.getDuration();
    }


    /**
     * Start or resume player
     */
    @Override
    public void play() {
        player.setPlayWhenReady(true);
    }


    /**
     * Pause player
     */
    @Override
    public void pause() {
        player.setPlayWhenReady(false);
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
        player.seekTo(position);
    }


    /**
     * Set volume
     * @param volume Volume between 0 and 1
     */
    @Override
    public void setVolume(float volume) {
        player.setVolume(volume);
    }


    /**
     * Set surface
     * @param surface Video surface
     */
    @Override
    public void setSurface(Surface surface) {
        player.setVideoSurface(surface);
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
