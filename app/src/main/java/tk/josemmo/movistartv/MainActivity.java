package tk.josemmo.movistartv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;

import tk.josemmo.movistartv.client.TvClient;

public class MainActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getApplicationContext();
        String inputId = context.getSharedPreferences(EpgSyncJobService.PREFERENCE_EPG_SYNC,
                Context.MODE_PRIVATE).getString(EpgSyncJobService.BUNDLE_KEY_INPUT_ID, null);
        EpgSyncJobService.requestImmediateSync(context, inputId, new ComponentName(context, JobService.class));

        /*
        final Context appContext = getApplicationContext();
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // TODO: for debug

                TvClient client = new TvClient(appContext);
                try {
                    String[] testData = new String[3]; // TODO: remove test data (including files)
                    testData[0] = getStringFromStream(getResources().openRawResource(R.raw.test1));
                    testData[1] = getStringFromStream(getResources().openRawResource(R.raw.test2));
                    testData[2] = getStringFromStream(getResources().openRawResource(R.raw.test3));
                    client.getChannelsList(testData);
                    client.getEpgData();
                } catch (Exception e) {
                    Log.e("HEY", "Failed to get EPG and channel data");
                    e.printStackTrace();
                }
                Log.d("HEY", "Activity loaded!");
            }
        });
        */

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
