package tk.josemmo.movistartv;

import android.media.tv.TvContentRating;
import android.net.Uri;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.InternalProviderData;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import tk.josemmo.movistartv.client.TvClient;

public class JobService extends EpgSyncJobService {
    private static final String LOGTAG = "JobService";

    private TvClient tvClient = null;
    private SparseArray<ArrayList<JSONObject>> epg = null;

    /**
     * Get TV client
     * @return TV client
     */
    private TvClient getTvClient() {
        if (tvClient == null) {
            tvClient = new TvClient(getApplicationContext());
        }
        return tvClient;
    }


    @Override
    public List<Channel> getChannels() throws EpgSyncException {
        Log.d(LOGTAG, "Received a petition for the list of channels");

        ArrayList<Channel> parsedChannels = new ArrayList<>();
        try {
            ArrayList<JSONObject> channels = getTvClient().getChannelsList();

            for (JSONObject channel : channels) {
                int dial = channel.getInt("dial");
                InternalProviderData data = new InternalProviderData();
                data.setVideoUrl(channel.getString("mCastAddress"));
                data.put("serviceName", channel.getInt("serviceName"));
                data.put("epgServiceName", channel.getInt("epgServiceName"));
                parsedChannels.add(new Channel.Builder()
                    .setOriginalNetworkId(dial)
                    .setDisplayName(channel.getString("name"))
                    .setDescription(channel.getString("description"))
                    .setDisplayNumber(dial + "")
                    .setChannelLogo(channel.getString("logoUri"))
                    .setInternalProviderData(data)
                    .build()
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.d(LOGTAG, "Finished getting list of channels");
        return parsedChannels;
    }


    @Override
    public List<Program> getProgramsForChannel(Uri channelUri, Channel channel, long startMs, long endMs) throws EpgSyncException {
        // Download EPG
        if (epg == null) {
            try {
                epg = getTvClient().getEpgData();
            } catch (Exception e) {
                Log.e(LOGTAG, "Failed to get EPG data");
                e.printStackTrace();
                epg = new SparseArray<>();
            }
        }

        // Return programs for channel
        List<Program> res = new ArrayList<>();
        try {
            InternalProviderData internalData = channel.getInternalProviderData();
            int epgServiceName = Integer.parseInt(internalData.get("epgServiceName").toString());
            Log.d(LOGTAG, "Received petition for the EPG of epgServiceName=" + epgServiceName);

            for (JSONObject data : epg.get(epgServiceName, new ArrayList<JSONObject>())) {
                try {
                    long startTime = data.getLong("start") * 1000;
                    long endTime = data.getLong("end") * 1000;
                    int season = data.getInt("season");
                    int episode = data.getInt("episode");
                    TvContentRating[] ratings = new TvContentRating[1];
                    ratings[0] = parseAgeRating(data.getInt("ageRating"));

                    Program.Builder programBuilder = new Program.Builder()
                            .setTitle(data.getString("title"))
                            .setPosterArtUri(data.getString("coverPath"))
                            .setContentRatings(ratings)
                            .setStartTimeUtcMillis(startTime)
                            .setEndTimeUtcMillis(endTime);
                    if (season > 0 && episode > 0) {
                        programBuilder.setSeasonNumber(season).setEpisodeNumber(episode);
                    }

                    res.add(programBuilder.build());
                } catch (JSONException e2) {
                    Log.e(LOGTAG, "Invalid program data, skipping");
                    e2.printStackTrace();
                }
            }

            Log.d(LOGTAG, "Finished getting EPG for epgServiceName=" + epgServiceName);
        } catch (InternalProviderData.ParseException e) {
            Log.e(LOGTAG, "Failed to get epgServiceName from channel");
        }
        return res;
    }


    /**
     * Parse age rating
     * @param  code Age rating code
     * @return      Android TV content rating instance
     */
    private TvContentRating parseAgeRating(int code) {
        String rating = "ES_DVB_ALL";
        switch (code) {
            case 3:
                rating = "ES_DVB_7";
                break;
            case 4:
                rating = "ES_DVB_12";
                break;
            case 5:
                rating = "ES_DVB_16";
                break;
            case 6:
                rating = "ES_DVB_17";
                break;
            case 7:
                rating = "ES_DVB_18";
                break;
        }
        return TvContentRating.createRating("com.android.tv", "ES_DVB", rating);
    }
}