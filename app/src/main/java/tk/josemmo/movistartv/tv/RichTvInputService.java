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

import android.content.ComponentName;
import android.content.Context;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.media.tv.companionlibrary.BaseTvInputService;
import com.google.android.media.tv.companionlibrary.TvPlayer;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.ModelUtils;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.model.RecordedProgram;
import com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService;

import tk.josemmo.movistartv.JobService;
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
    class RichTvInputSessionImpl extends BaseTvInputService.Session {
        private static final String LOGTAG = "RichTvInputService";
        private static final String UNKNOWN_LANGUAGE = "und";
        private static final long EPG_SYNC_DELAYED_PERIOD_MS = 2000;

        private final String mInputId;
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
            mInputId = inputId;
        }


        /**
         * Create player
         * @param videoUrl Video URL
         */
        private void createPlayer(Uri videoUrl) {
            releasePlayer();
            mPlayer = new AppPlayer(mContext, videoUrl);
            //mPlayer.addListener(this);
            //mPlayer.setCaptionListener(this);
            mPlayer.prepare();
        }


        /**
         * Release player
         */
        private void releasePlayer() {
            if (mPlayer != null) {
                //mPlayer.removeListener(this);
                mPlayer.setSurface(null);
                mPlayer.stop();
                mPlayer.release();
                mPlayer = null;
            }
        }


        /**
         * Request EPG sync
         * @param channelUri Channel URI
         */
        private void requestEpgSync(final Uri channelUri) {
            EpgSyncJobService.requestImmediateSync(RichTvInputService.this, mInputId,
                    new ComponentName(RichTvInputService.this, JobService.class));
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    onTune(channelUri);
                }
            }, EPG_SYNC_DELAYED_PERIOD_MS);
        }


        /**
         * Get TV player
         * @return TV player instance
         */
        @Override
        public TvPlayer getTvPlayer() {
            return mPlayer;
        }


//        private List<TvTrackInfo> getAllTracks() {
//            String trackId;
//            List<TvTrackInfo> tracks = new ArrayList<>();
//
//            int[] trackTypes = {AppPlayer.TYPE_AUDIO, AppPlayer.TYPE_VIDEO, AppPlayer.TYPE_TEXT};
//            for (int trackType : trackTypes) {
//                int count = mPlayer.getTrackCount(trackType);
//                for (int i=0; i<count; i++) {
//                    Format format = mPlayer.getTrackFormat(trackType, i);
//                    trackId = getTrackId(trackType, i);
//                    TvTrackInfo.Builder builder = new TvTrackInfo.Builder(trackType, trackId);
//
//                    if (trackType == AppPlayer.TYPE_VIDEO) {
//                        if (format.width != Format.NO_VALUE) {
//                            builder.setVideoWidth(format.width);
//                        }
//                        if (format.height != Format.NO_VALUE) {
//                            builder.setVideoHeight(format.height);
//                        }
//                    } else if (trackType == AppPlayer.TYPE_AUDIO) {
//                        builder.setAudioChannelCount(format.channelCount);
//                        builder.setAudioSampleRate(format.sampleRate);
//                        if (format.language != null && !UNKNOWN_LANGUAGE.equals(format.language)) {
//                            // TvInputInfo expects {@code null} for unknown language.
//                            builder.setLanguage(format.language);
//                        }
//                    } else {
//                        if (format.language != null && !UNKNOWN_LANGUAGE.equals(format.language)) {
//                            // TvInputInfo expects {@code null} for unknown language.
//                            builder.setLanguage(format.language);
//                        }
//                    }
//
//                    tracks.add(builder.build());
//                }
//            }
//            return tracks;
//        }


        /**
         * On play program
         * @param  program    Program instance
         * @param  startPosMs Start position
         * @return            Success
         */
        @Override
        public boolean onPlayProgram(Program program, long startPosMs) {
            if (program == null) {
                requestEpgSync(getCurrentChannelUri());
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
                return false;
            }

            createPlayer(Uri.parse(program.getInternalProviderData().getVideoUrl()));
            if (startPosMs > 0) {
                mPlayer.seekTo(startPosMs);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
            }
            mPlayer.setPlayWhenReady(true);
            return true;
        }


        /**
         * On play recorded program
         * @param  recordedProgram Recorded program
         * @return                 Success
         */
        @RequiresApi(api = Build.VERSION_CODES.N)
        public boolean onPlayRecordedProgram(RecordedProgram recordedProgram) {
            createPlayer(Uri.parse(recordedProgram.getInternalProviderData().getVideoUrl()));

            long recordingStartTime = recordedProgram.getInternalProviderData()
                    .getRecordedProgramStartTime();
            mPlayer.seekTo(recordingStartTime - recordedProgram.getStartTimeUtcMillis());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
            }
            mPlayer.setPlayWhenReady(true);
            return true;
        }


        @Override
        public boolean onTune(Uri channelUri) {
            Channel channel = ModelUtils.getChannel(mContext.getContentResolver(), channelUri);
            Log.d(LOGTAG, "Tune to " + channel.getDisplayName());
            createPlayer(Uri.parse("udp://" + channel.getInternalProviderData().getVideoUrl()));
            mPlayer.setPlayWhenReady(true);
            return true;
        }


        /**
         * On set caption enabled
         * @param b Are captions enabled
         */
        @Override
        public void onSetCaptionEnabled(boolean b) {
            // TODO: subtitles not implemented... yet
        }


//        @Override
//        public boolean onSelectTrack(int type, String trackId) {
//            if (trackId == null) {
//                return true;
//            }
//
//            int trackIndex = getIndexFromTrackId(trackId);
//            if (mPlayer != null) {
//                if (type == TvTrackInfo.TYPE_SUBTITLE) {
//                    if (! mCaptionEnabled) {
//                        return false;
//                    }
//                    mSelectedSubtitleTrackIndex = trackIndex;
//                }
//
//                mPlayer.setSelectedTrack(type, trackIndex);
//                notifyTrackSelected(type, trackId);
//                return true;
//            }
//            return false;
//        }


        /**
         * On release
         */
        @Override
        public void onRelease() {
            super.onRelease();
            releasePlayer();
        }


        /**
         * On block content
         * @param rating TV rating
         */
        @Override
        public void onBlockContent(TvContentRating rating) {
            super.onBlockContent(rating);
            releasePlayer();
        }


//        @Override
//        public void onStateChanged(boolean playWhenReady, int playbackState) {
//            if (mPlayer == null) {
//                return;
//            }
//
//            if (playWhenReady && playbackState == ExoPlayer.STATE_READY) {
//                notifyTracksChanged(getAllTracks());
//                String audioId = getTrackId(TvTrackInfo.TYPE_AUDIO,
//                        mPlayer.getSelectedTrack(TvTrackInfo.TYPE_AUDIO));
//                String videoId = getTrackId(TvTrackInfo.TYPE_VIDEO,
//                        mPlayer.getSelectedTrack(TvTrackInfo.TYPE_VIDEO));
//                String textId = getTrackId(TvTrackInfo.TYPE_SUBTITLE,
//                        mPlayer.getSelectedTrack(TvTrackInfo.TYPE_SUBTITLE));
//
//                notifyTrackSelected(TvTrackInfo.TYPE_AUDIO, audioId);
//                notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, videoId);
//                notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, textId);
//                notifyVideoAvailable();
//            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
//                    Math.abs(mPlayer.getPlaybackSpeed() - 1) < 0.1 &&
//                    playWhenReady && playbackState == ExoPlayer.STATE_BUFFERING) {
//                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
//            }
//        }


//        @Override
//        public void onError(Exception e) {
//            Log.e(LOGTAG, "An error occurred --> " + e.getMessage());
//        }
    }
}