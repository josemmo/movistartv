package tk.josemmo.movistartv;

import android.net.Uri;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.InternalProviderData;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
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
            String[] testData = new String[3];
            testData[0] = getStringFromStream(getResources().openRawResource(R.raw.test1));
            testData[1] = getStringFromStream(getResources().openRawResource(R.raw.test2));
            testData[2] = getStringFromStream(getResources().openRawResource(R.raw.test3));
            ArrayList<JSONObject> channels = getTvClient().getChannelsList(testData);

            for (JSONObject channel : channels) {
                int dial = channel.getInt("dial");
                InternalProviderData data = new InternalProviderData();
                data.put("serviceName", channel.getInt("serviceName"));
                data.put("mCastAddress", channel.getString("mCastAddress"));
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
        ArrayList<Program> res = new ArrayList<>();
        try {
            InternalProviderData internalData = channel.getInternalProviderData();
            int serviceName = Integer.parseInt(internalData.get("serviceName").toString());
            Log.d(LOGTAG, "Received a petition for the EPG of serviceName=" + serviceName);

            for (JSONObject data : epg.get(serviceName, new ArrayList<JSONObject>())) {
                try {
                    res.add(new Program.Builder()
                            .setTitle(data.getString("title"))
                            .setStartTimeUtcMillis(data.getInt("start") * 1000)
                            .setEndTimeUtcMillis(data.getInt("end") * 1000)
                            .build()
                    );
                } catch (JSONException e2) {
                    Log.e(LOGTAG, "Invalid program data, skipping");
                    e2.printStackTrace();
                }
            }

            Log.d(LOGTAG, "Finished getting EPG for serviceName=" + serviceName);
        } catch (InternalProviderData.ParseException e) {
            Log.e(LOGTAG, "Failed to get serviceName from channel");
        }
        return res;
    }


    // TODO: remove
    public static String getStringFromStream(InputStream stream) throws Exception
    {
        int n = 0;
        char[] buffer = new char[1024 * 4];
        InputStreamReader reader = new InputStreamReader(stream, "UTF8");
        StringWriter writer = new StringWriter();
        while (-1 != (n = reader.read(buffer))) writer.write(buffer, 0, n);
        return writer.toString();
    }
}