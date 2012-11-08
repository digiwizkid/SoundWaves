package info.bottiger.podcast.service;

import info.bottiger.podcast.R;
import info.bottiger.podcast.fetcher.FeedFetcher;
import info.bottiger.podcast.parser.FeedHandler;
import info.bottiger.podcast.provider.FeedItem;
import info.bottiger.podcast.provider.ItemColumns;
import info.bottiger.podcast.provider.Subscription;
import info.bottiger.podcast.provider.SubscriptionColumns;
import info.bottiger.podcast.utils.LockHandler;
import info.bottiger.podcast.utils.Log;
import info.bottiger.podcast.utils.SDCardMgr;

import java.util.PriorityQueue;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.PowerManager;
import android.widget.Toast;

public class PodcastUpdateManager extends BroadcastReceiver {
	
	private static final long ONE_MINUTE = 60L * 1000L;
	private static final long ONE_HOUR = 60L * ONE_MINUTE;
	private static final long ONE_DAY = 24L * ONE_HOUR;

	
    @Override
    public void onReceive(Context context, Intent intent) 
    {   
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");
        wl.acquire();

        // Put here YOUR code.
        //Toast.makeText(context, "Alarm !!!!!!!!!!", Toast.LENGTH_LONG).show(); // For example
        
        PodcastDownloadManager pdm = new PodcastDownloadManager();
		pdm.start_update(context);
		pdm.removeExpires(context);
		pdm.do_download(false, context);

        wl.release();
    }

    public static void setUpdate(Context context)
    {
        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, PodcastUpdateManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        //am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 1000 * 60 * 10, pi); // Millisec * Second * Minute
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+ONE_HOUR,  pi);
    }

    public static void cancelUpdate(Context context)
    {
        Intent intent = new Intent(context, PodcastUpdateManager.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
    }
    
    public static void updateNow(Context context) 
    {
    	cancelUpdate(context);
    	setUpdate(context);
    }

    
}
