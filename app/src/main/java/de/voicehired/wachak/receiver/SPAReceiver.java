package de.voicehired.wachak.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;

import de.voicehired.wachak.BuildConfig;
import de.voicehired.wachak.R;
import de.voicehired.wachak.core.feed.Feed;
import de.voicehired.wachak.core.storage.DownloadRequestException;
import de.voicehired.wachak.core.storage.DownloadRequester;

/**
 * Receives intents from AntennaPod Single Purpose apps
 */
public class SPAReceiver extends BroadcastReceiver{
    private static final String TAG = "SPAReceiver";

    public static final String ACTION_SP_APPS_QUERY_FEEDS = "de.voicehired.antennapdsp.intent.SP_APPS_QUERY_FEEDS";
    public static final String ACTION_SP_APPS_QUERY_FEEDS_REPSONSE = "de.voicehired.antennapdsp.intent.SP_APPS_QUERY_FEEDS_RESPONSE";
    public static final String ACTION_SP_APPS_QUERY_FEEDS_REPSONSE_FEEDS_EXTRA = "feeds";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (TextUtils.equals(intent.getAction(), ACTION_SP_APPS_QUERY_FEEDS_REPSONSE)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Received SP_APPS_QUERY_RESPONSE");
            if (intent.hasExtra(ACTION_SP_APPS_QUERY_FEEDS_REPSONSE_FEEDS_EXTRA)) {
                String[] feedUrls = intent.getStringArrayExtra(ACTION_SP_APPS_QUERY_FEEDS_REPSONSE_FEEDS_EXTRA);
                if (feedUrls != null) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Received feeds list: " + Arrays.toString(feedUrls));
                    for (String url : feedUrls) {
                        Feed f  = new Feed(url, null);
                        try {
                            DownloadRequester.getInstance().downloadFeed(context, f);
                        } catch (DownloadRequestException e) {
                            Log.e(TAG, "Error while trying to add feed " + url);
                            e.printStackTrace();
                        }
                    }
                    Toast.makeText(context, R.string.sp_apps_importing_feeds_msg, Toast.LENGTH_LONG).show();

                } else {
                    Log.e(TAG, "Received invalid SP_APPS_QUERY_REPSONSE: extra was null");
                }
            } else {
                Log.e(TAG, "Received invalid SP_APPS_QUERY_RESPONSE: Contains no extra");
            }
        }
    }
}
