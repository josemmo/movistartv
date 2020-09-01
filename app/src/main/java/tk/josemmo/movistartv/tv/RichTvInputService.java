/*
 * Copyright 2015 The Android Open Source Project.
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

package tk.josemmo.movistartv.tv;

import android.content.Context;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.media.tv.companionlibrary.BaseTvInputService;
import com.google.android.media.tv.companionlibrary.TvPlayer;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.ModelUtils;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.model.RecordedProgram;

import tk.josemmo.movistartv.player.AppPlayer;

public class RichTvInputService extends BaseTvInputService {
    /**
     * On create session
     * @param  inputId Input ID
     * @return         Session
     */
    @Override
    public final Session onCreateSession(String inputId) {
        RichTvInputSessionImpl session = new RichTvInputSessionImpl(this, inputId);
        session.setOverlayViewEnabled(true);
        return super.sessionCreated(session);
    }


    /**
     * RichTvInputSessionImpl is an implementation of a TV Input Service session, required
     * by Android TV to render something on screen when a channel of ours is tuned by the user.
     */
    static class RichTvInputSessionImpl extends BaseTvInputService.Session {
        private static final String LOGTAG = "RichTvInputService";
        private final Context mContext;
        private AppPlayer mPlayer = null;

        /**
         * RichTvInputSessionImpl constructor
         * @param context Context
         * @param inputId Input ID
         */
        RichTvInputSessionImpl(Context context, String inputId) {
            super(context, inputId);
            mContext = context;
            Log.d(LOGTAG, "Started service");
        }


        /**
         * Load TV player if released
         */
        private void loadTvPlayerIfReleased() {
            if (mPlayer == null) {
                mPlayer = new AppPlayer(mContext);
            }
        }


        /**
         * Get TV player
         * @return TV player instance
         */
        @Override
        public TvPlayer getTvPlayer() {
            return mPlayer;
        }


        /**
         * On play recorded program
         * @param  recordedProgram Recorded program
         * @return                 Success
         */
        @RequiresApi(api = Build.VERSION_CODES.N)
        public boolean onPlayRecordedProgram(RecordedProgram recordedProgram) {
            Log.d(LOGTAG, "onPlayRecordedProgram called");
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
            return false;
        }


        /**
         * On play program
         * @param  program    Program instance
         * @param  startPosMs Start position
         * @return            Success
         */
        @Override
        public boolean onPlayProgram(@Nullable Program program, long startPosMs) {
            Log.d(LOGTAG, "onPlayProgram called");
            Uri channelUri = getCurrentChannelUri();
            Channel channel = ModelUtils.getChannel(mContext.getContentResolver(), channelUri);

            loadTvPlayerIfReleased();
            mPlayer.loadMedia("rtp://@" + channel.getInternalProviderData().getVideoUrl());

            mPlayer.play();
            return true;
        }


        @Override
        public boolean onTune(Uri channelUri) {
            Log.d(LOGTAG, "onTune called with URI " + channelUri);
            return super.onTune(channelUri);
        }


        /**
         * On set caption enabled
         * @param b Are captions enabled
         */
        @Override
        public void onSetCaptionEnabled(boolean b) {
            Log.d(LOGTAG, "onSetCaptionEnabled called");
            // TODO: subtitles not implemented... yet
        }


        /**
         * On release
         */
        @Override
        public void onRelease() {
            Log.d(LOGTAG, "onRelease called");
            super.onRelease();

            if (mPlayer != null) {
                mPlayer.stop();
                mPlayer.setSurface(null);
                mPlayer.release();
                mPlayer = null;
            }
        }


        /**
         * On block content
         * @param rating TV rating
         */
        @Override
        public void onBlockContent(TvContentRating rating) {
            Log.d(LOGTAG, "onBlockContent called");
            super.onBlockContent(rating);

            if (mPlayer != null) {
                mPlayer.stop();
            }
        }
    }
}