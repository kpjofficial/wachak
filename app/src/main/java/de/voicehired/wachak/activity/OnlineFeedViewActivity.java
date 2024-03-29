package de.voicehired.wachak.activity;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.examples.HtmlToPlainText;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.voicehired.wachak.R;
import de.voicehired.wachak.adapter.FeedItemlistDescriptionAdapter;
import de.voicehired.wachak.core.dialog.DownloadRequestErrorDialogCreator;
import de.voicehired.wachak.core.event.DownloadEvent;
import de.voicehired.wachak.core.feed.EventDistributor;
import de.voicehired.wachak.core.feed.Feed;
import de.voicehired.wachak.core.feed.FeedItem;
import de.voicehired.wachak.core.feed.FeedPreferences;
import de.voicehired.wachak.core.glide.ApGlideSettings;
import de.voicehired.wachak.core.preferences.UserPreferences;
import de.voicehired.wachak.core.service.download.DownloadRequest;
import de.voicehired.wachak.core.service.download.DownloadStatus;
import de.voicehired.wachak.core.service.download.Downloader;
import de.voicehired.wachak.core.service.download.HttpDownloader;
import de.voicehired.wachak.core.storage.DBReader;
import de.voicehired.wachak.core.storage.DownloadRequestException;
import de.voicehired.wachak.core.storage.DownloadRequester;
import de.voicehired.wachak.core.syndication.handler.FeedHandler;
import de.voicehired.wachak.core.syndication.handler.FeedHandlerResult;
import de.voicehired.wachak.core.syndication.handler.UnsupportedFeedtypeException;
import de.voicehired.wachak.core.util.DownloadError;
import de.voicehired.wachak.core.util.FileNameGenerator;
import de.voicehired.wachak.core.util.StorageUtils;
import de.voicehired.wachak.core.util.URLChecker;
import de.voicehired.wachak.core.util.syndication.FeedDiscoverer;
import de.voicehired.wachak.dialog.AuthenticationDialog;
import de.greenrobot.event.EventBus;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Downloads a feed from a feed URL and parses it. Subclasses can display the
 * feed object that was parsed. This activity MUST be started with a given URL
 * or an Exception will be thrown.
 * <p/>
 * If the feed cannot be downloaded or parsed, an error dialog will be displayed
 * and the activity will finish as soon as the error dialog is closed.
 */
public class OnlineFeedViewActivity extends ActionBarActivity {

    private static final String TAG = "OnlineFeedViewActivity";

    public static final String ARG_FEEDURL = "arg.feedurl";

    // Optional argument: specify a title for the actionbar.
    public static final String ARG_TITLE = "title";

    private static final int EVENTS = EventDistributor.FEED_LIST_UPDATE;

    public static final int RESULT_ERROR = 2;

    private volatile List<Feed> feeds;
    private Feed feed;
    private String selectedDownloadUrl;
    private Downloader downloader;

    private boolean isPaused;

    private Dialog dialog;

    private Button subscribeButton;

    private Subscription download;
    private Subscription parser;
    private Subscription updater;

    public void onEventMainThread(DownloadEvent event) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]");
        setSubscribeButtonState(feed);
    }

    private EventDistributor.EventListener listener = new EventDistributor.EventListener() {
        @Override
        public void update(EventDistributor eventDistributor, Integer arg) {
            if ((arg & EventDistributor.FEED_LIST_UPDATE) != 0) {
                updater = Observable.fromCallable(() -> DBReader.getFeedList())
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(feeds -> {
                                    OnlineFeedViewActivity.this.feeds = feeds;
                                    setSubscribeButtonState(feed);
                                }
                        );
            } else if ((arg & EVENTS) != 0) {
                setSubscribeButtonState(feed);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(UserPreferences.getTheme());
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (getIntent() != null && getIntent().hasExtra(ARG_TITLE)) {
            getSupportActionBar().setTitle(getIntent().getStringExtra(ARG_TITLE));
        }

        StorageUtils.checkStorageAvailability(this);

        final String feedUrl;
        if (getIntent().hasExtra(ARG_FEEDURL)) {
            feedUrl = getIntent().getStringExtra(ARG_FEEDURL);
        } else if (TextUtils.equals(getIntent().getAction(), Intent.ACTION_SEND)
                || TextUtils.equals(getIntent().getAction(), Intent.ACTION_VIEW)) {
            feedUrl = (TextUtils.equals(getIntent().getAction(), Intent.ACTION_SEND))
                    ? getIntent().getStringExtra(Intent.EXTRA_TEXT) : getIntent().getDataString();
            getSupportActionBar().setTitle(R.string.add_new_feed_label);
        } else {
            throw new IllegalArgumentException("Activity must be started with feedurl argument!");
        }

        Log.d(TAG, "Activity was started with url " + feedUrl);
        setLoadingLayout();
        if (savedInstanceState == null) {
            startFeedDownload(feedUrl, null, null);
        } else {
            startFeedDownload(feedUrl, savedInstanceState.getString("username"), savedInstanceState.getString("password"));
        }
    }

    /**
     * Displays a progress indicator.
     */
    private void setLoadingLayout() {
        RelativeLayout rl = new RelativeLayout(this);
        RelativeLayout.LayoutParams rlLayoutParams = new RelativeLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);

        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminate(true);
        RelativeLayout.LayoutParams pbLayoutParams = new RelativeLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pbLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        rl.addView(pb, pbLayoutParams);
        addContentView(rl, rlLayoutParams);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPaused = false;
        EventDistributor.getInstance().register(listener);
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPaused = true;
        EventDistributor.getInstance().unregister(listener);
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (downloader != null && !downloader.isFinished()) {
            downloader.cancel();
        }
        if(dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(updater != null) {
            updater.unsubscribe();
        }
        if(download != null) {
            download.unsubscribe();
        }
        if(parser != null) {
            parser.unsubscribe();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (feed != null && feed.getPreferences() != null) {
            outState.putString("username", feed.getPreferences().getUsername());
            outState.putString("password", feed.getPreferences().getPassword());
        }
    }

    private void resetIntent(String url, String title) {
        Intent intent = new Intent();
        intent.putExtra(ARG_FEEDURL, url);
        intent.putExtra(ARG_TITLE, title);
        setIntent(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent destIntent = new Intent(this, MainActivity.class);
                if (NavUtils.shouldUpRecreateTask(this, destIntent)) {
                    startActivity(destIntent);
                } else {
                    NavUtils.navigateUpFromSameTask(this);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startFeedDownload(String url, String username, String password) {
        Log.d(TAG, "Starting feed download");
        url = URLChecker.prepareURL(url);
        feed = new Feed(url, null);
        if (username != null && password != null) {
            feed.setPreferences(new FeedPreferences(0, false, FeedPreferences.AutoDeleteAction.GLOBAL, username, password));
        }
        String fileUrl = new File(getExternalCacheDir(),
                FileNameGenerator.generateFileName(feed.getDownload_url())).toString();
        feed.setFile_url(fileUrl);
        final DownloadRequest request = new DownloadRequest(feed.getFile_url(),
                feed.getDownload_url(), "OnlineFeed", 0, Feed.FEEDFILETYPE_FEED, username, password,
                true, null);

        download = Observable.create(new Observable.OnSubscribe<DownloadStatus>() {
                    @Override
                    public void call(Subscriber<? super DownloadStatus> subscriber) {
                        feeds = DBReader.getFeedList();
                        downloader = new HttpDownloader(request);
                        downloader.call();
                        Log.d(TAG, "Download was completed");
                        subscriber.onNext(downloader.getResult());
                        subscriber.onCompleted();
                    }
                })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(status -> {
                    if (status != null) {
                        if (!status.isCancelled()) {
                            if (status.isSuccessful()) {
                                parseFeed();
                            } else if (status.getReason() == DownloadError.ERROR_UNAUTHORIZED) {
                                if (!isFinishing() && !isPaused) {
                                    dialog = new FeedViewAuthenticationDialog(OnlineFeedViewActivity.this,
                                            R.string.authentication_notification_title, downloader.getDownloadRequest().getSource());
                                    dialog.show();
                                }
                            } else {
                                String errorMsg = status.getReason().getErrorString(OnlineFeedViewActivity.this);
                                if (errorMsg != null && status.getReasonDetailed() != null) {
                                    errorMsg += " (" + status.getReasonDetailed() + ")";
                                }
                                showErrorDialog(errorMsg);
                            }
                        }
                    } else {
                        Log.wtf(TAG, "DownloadStatus returned by Downloader was null");
                        finish();
                    }
                });
    }

    private void parseFeed() {
        if (feed == null || feed.getFile_url() == null && feed.isDownloaded()) {
            throw new IllegalStateException("feed must be non-null and downloaded when parseFeed is called");
        }
        Log.d(TAG, "Parsing feed");

        parser = Observable.create(new Observable.OnSubscribe<FeedHandlerResult>() {
            @Override
            public void call(Subscriber<? super FeedHandlerResult> subscriber) {
                        FeedHandler handler = new FeedHandler();
                        try {
                            FeedHandlerResult result = handler.parseFeed(feed);
                            subscriber.onNext(result);
                        } catch (UnsupportedFeedtypeException e) {
                            Log.d(TAG, "Unsupported feed type detected");
                            if (TextUtils.equals("html", e.getRootElement().toLowerCase())) {
                                showFeedDiscoveryDialog(new File(feed.getFile_url()), feed.getDownload_url());
                            } else {
                                subscriber.onError(e);
                            }
                        } catch (Exception e) {
                            subscriber.onError(e);
                        } finally {
                            boolean rc = new File(feed.getFile_url()).delete();
                            Log.d(TAG, "Deleted feed source file. Result: " + rc);
                            subscriber.onCompleted();
                        }
                    }
                })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    beforeShowFeedInformation(result.feed);
                    showFeedInformation(result.feed, result.alternateFeedUrls);
                }, error -> {
                    String errorMsg = DownloadError.ERROR_PARSER_EXCEPTION.getErrorString(
                            OnlineFeedViewActivity.this) + " (" + error.getMessage() + ")";
                    showErrorDialog(errorMsg);
                });
    }

    /**
     * Called after the feed has been downloaded and parsed and before showFeedInformation is called.
     * This method is executed on a background thread
     */
    private void beforeShowFeedInformation(Feed feed) {
        // remove HTML tags from descriptions
        Log.d(TAG, "Removing HTML from shownotes");
        if (feed.getItems() != null) {
            HtmlToPlainText formatter = new HtmlToPlainText();
            for (FeedItem item : feed.getItems()) {
                if (item.getDescription() != null) {
                    Document description = Jsoup.parse(item.getDescription());
                    item.setDescription(StringUtils.trim(formatter.getPlainText(description)));
                }
            }
        }
    }

    /**
     * Called when feed parsed successfully.
     * This method is executed on the GUI thread.
     */
    private void showFeedInformation(final Feed feed, Map<String, String> alternateFeedUrls) {
        setContentView(R.layout.listview_activity);

        this.feed = feed;
        this.selectedDownloadUrl = feed.getDownload_url();
        EventDistributor.getInstance().register(listener);
        ListView listView = (ListView) findViewById(R.id.listview);
        LayoutInflater inflater = (LayoutInflater)
                getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View header = inflater.inflate(R.layout.onlinefeedview_header, listView, false);
        listView.addHeaderView(header);

        listView.setAdapter(new FeedItemlistDescriptionAdapter(this, 0, feed.getItems()));

        ImageView cover = (ImageView) header.findViewById(R.id.imgvCover);
        TextView title = (TextView) header.findViewById(R.id.txtvTitle);
        TextView author = (TextView) header.findViewById(R.id.txtvAuthor);
        TextView description = (TextView) header.findViewById(R.id.txtvDescription);
        Spinner spAlternateUrls = (Spinner) header.findViewById(R.id.spinnerAlternateUrls);

        subscribeButton = (Button) header.findViewById(R.id.butSubscribe);

        if (feed.getImage() != null && StringUtils.isNotBlank(feed.getImage().getDownload_url())) {
            Glide.with(this)
                    .load(feed.getImage().getDownload_url())
                    .placeholder(R.color.light_gray)
                    .error(R.color.light_gray)
                    .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                    .fitCenter()
                    .dontAnimate()
                    .into(cover);
        }

        title.setText(feed.getTitle());
        author.setText(feed.getAuthor());
        description.setText(feed.getDescription());

        subscribeButton.setOnClickListener(v -> {
            try {
                Feed f = new Feed(selectedDownloadUrl, null, feed.getTitle());
                f.setPreferences(feed.getPreferences());
                this.feed = f;

                DownloadRequester.getInstance().downloadFeed(this, f);
            } catch (DownloadRequestException e) {
                e.printStackTrace();
                DownloadRequestErrorDialogCreator.newRequestErrorDialog(OnlineFeedViewActivity.this,
                        e.getMessage());
            }
            setSubscribeButtonState(feed);
        });

        if (alternateFeedUrls.isEmpty()) {
            spAlternateUrls.setVisibility(View.GONE);
        } else {
            spAlternateUrls.setVisibility(View.VISIBLE);

            final List<String> alternateUrlsList = new ArrayList<>();
            final List<String> alternateUrlsTitleList = new ArrayList<>();

            alternateUrlsList.add(feed.getDownload_url());
            alternateUrlsTitleList.add(feed.getTitle());


            alternateUrlsList.addAll(alternateFeedUrls.keySet());
            for (String url : alternateFeedUrls.keySet()) {
                alternateUrlsTitleList.add(alternateFeedUrls.get(url));
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, alternateUrlsTitleList);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spAlternateUrls.setAdapter(adapter);
            spAlternateUrls.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    selectedDownloadUrl = alternateUrlsList.get(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
        }
        setSubscribeButtonState(feed);
    }

    private void setSubscribeButtonState(Feed feed) {
        if (subscribeButton != null && feed != null) {
            if (DownloadRequester.getInstance().isDownloadingFile(feed.getDownload_url())) {
                subscribeButton.setEnabled(false);
                subscribeButton.setText(R.string.downloading_label);
            } else if (feedInFeedlist(feed)) {
                subscribeButton.setEnabled(false);
                subscribeButton.setText(R.string.subscribed_label);
            } else {
                subscribeButton.setEnabled(true);
                subscribeButton.setText(R.string.subscribe_label);
            }
        }
    }

    private boolean feedInFeedlist(Feed feed) {
        if (feeds == null || feed == null) {
            return false;
        }
        for (Feed f : feeds) {
            if (f.getIdentifyingValue().equals(feed.getIdentifyingValue())) {
                return true;
            }
        }
        return false;
    }

    private void showErrorDialog(String errorMsg) {
        assert(Looper.myLooper() == Looper.getMainLooper()); // run on UI thread
        if (!isFinishing() && !isPaused) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.error_label);
            if (errorMsg != null) {
                builder.setMessage(getString(R.string.error_msg_prefix) + errorMsg);
            } else {
                builder.setMessage(R.string.error_msg_prefix);
            }
            builder.setNeutralButton(android.R.string.ok,
                    (dialog, which) -> {
                        dialog.cancel();
                    }
            );
            builder.setOnCancelListener(dialog -> {
                setResult(RESULT_ERROR);
                finish();
            });
            if(dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
            dialog = builder.show();
        }
    }

    private void showFeedDiscoveryDialog(File feedFile, String baseUrl) {
        FeedDiscoverer fd = new FeedDiscoverer();
        final Map<String, String> urlsMap;
        try {
            urlsMap = fd.findLinks(feedFile, baseUrl);
            if (urlsMap == null || urlsMap.isEmpty()) {
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        if (isPaused || isFinishing()) {
            return;
        }

        final List<String> titles = new ArrayList<>();
        final List<String> urls = new ArrayList<>();

        urls.addAll(urlsMap.keySet());
        for (String url : urls) {
            titles.add(urlsMap.get(url));
        }

        final ArrayAdapter<String> adapter = new ArrayAdapter<>(OnlineFeedViewActivity.this, R.layout.ellipsize_start_listitem, R.id.txtvTitle, titles);
        DialogInterface.OnClickListener onClickListener = (dialog, which) -> {
            String selectedUrl = urls.get(which);
            dialog.dismiss();
            resetIntent(selectedUrl, titles.get(which));
            FeedPreferences prefs = feed.getPreferences();
            if(prefs != null) {
                startFeedDownload(selectedUrl, prefs.getUsername(), prefs.getPassword());
            } else {
                startFeedDownload(selectedUrl, null, null);
            }
        };

        AlertDialog.Builder ab = new AlertDialog.Builder(OnlineFeedViewActivity.this)
                .setTitle(R.string.feeds_label)
                .setCancelable(true)
                .setOnCancelListener(dialog -> finish())
                .setAdapter(adapter, onClickListener);

        runOnUiThread(() -> {
            if(dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
            dialog = ab.show();
        });
    }

    private class FeedViewAuthenticationDialog extends AuthenticationDialog {

        private String feedUrl;

        public FeedViewAuthenticationDialog(Context context, int titleRes, String feedUrl) {
            super(context, titleRes, true, false, null, null);
            this.feedUrl = feedUrl;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            finish();
        }

        @Override
        protected void onConfirmed(String username, String password, boolean saveUsernamePassword) {
            startFeedDownload(feedUrl, username, password);
        }
    }

}
