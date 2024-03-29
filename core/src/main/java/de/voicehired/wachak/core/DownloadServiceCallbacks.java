package de.voicehired.wachak.core;

import android.app.PendingIntent;
import android.content.Context;

import de.voicehired.wachak.core.feed.Feed;
import de.voicehired.wachak.core.service.download.DownloadRequest;

/**
 * Callbacks for the DownloadService of the core module
 */
public interface DownloadServiceCallbacks {

    /**
     * Returns a PendingIntent for a notification the main notification of the DownloadService.
     * <p/>
     * The PendingIntent takes the users to a screen where they can observe all currently running
     * downloads.
     *
     * @return A non-null PendingIntent for the notification.
     */
    public PendingIntent getNotificationContentIntent(Context context);

    /**
     * Returns a PendingIntent for a notification that tells the user to enter a username
     * or a password for a requested download.
     * <p/>
     * The PendingIntent takes users to an Activity that lets the user enter their username
     * and password to retry the download.
     *
     * @return A non-null PendingIntent for the notification.
     */
    public PendingIntent getAuthentificationNotificationContentIntent(Context context, DownloadRequest request);

    /**
     * Returns a PendingIntent for notification that notifies the user about the completion of downloads
     * along with information about failed and successful downloads.
     * <p/>
     * The PendingIntent takes users to an activity where they can look at all successful and failed downloads.
     *
     * @return A non-null PendingIntent for the notification or null if shouldCreateReport()==false
     */
    public PendingIntent getReportNotificationContentIntent(Context context);

    /**
     * Called by the FeedSyncThread after a feed has been downloaded and parsed.
     *
     * @param feed The non-null feed that has been parsed.
     */
    public void onFeedParsed(Context context, Feed feed);

    /**
     * Returns true if the DownloadService should create a report that shows the number of failed
     * downloads when the service shuts down.
     * */
    public boolean shouldCreateReport();
}

