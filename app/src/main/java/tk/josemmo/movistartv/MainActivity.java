package tk.josemmo.movistartv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

import com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService;

public class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getApplicationContext();
        String inputId = context.getSharedPreferences(EpgSyncJobService.PREFERENCE_EPG_SYNC,
                Context.MODE_PRIVATE).getString(EpgSyncJobService.BUNDLE_KEY_INPUT_ID, null);
        EpgSyncJobService.requestImmediateSync(context, inputId, new ComponentName(context, JobService.class));
    }
}
