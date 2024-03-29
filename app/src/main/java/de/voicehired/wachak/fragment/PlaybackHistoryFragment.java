package de.voicehired.wachak.fragment;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.util.Pair;
import android.support.v4.view.MenuItemCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import java.util.List;

import de.voicehired.wachak.R;
import de.voicehired.wachak.activity.MainActivity;
import de.voicehired.wachak.adapter.DefaultActionButtonCallback;
import de.voicehired.wachak.adapter.FeedItemlistAdapter;
import de.voicehired.wachak.core.event.DownloadEvent;
import de.voicehired.wachak.core.event.DownloaderUpdate;
import de.voicehired.wachak.core.event.QueueEvent;
import de.voicehired.wachak.core.feed.EventDistributor;
import de.voicehired.wachak.core.feed.FeedItem;
import de.voicehired.wachak.core.feed.FeedMedia;
import de.voicehired.wachak.core.service.download.Downloader;
import de.voicehired.wachak.core.storage.DBReader;
import de.voicehired.wachak.core.storage.DBWriter;
import de.voicehired.wachak.core.util.LongList;
import de.greenrobot.event.EventBus;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class PlaybackHistoryFragment extends ListFragment {

    public static final String TAG = "PlaybackHistoryFragment";

    private static final int EVENTS = EventDistributor.PLAYBACK_HISTORY_UPDATE |
            EventDistributor.PLAYER_STATUS_UPDATE;

    private List<FeedItem> playbackHistory;
    private LongList queue;
    private FeedItemlistAdapter adapter;

    private boolean itemsLoaded = false;
    private boolean viewsCreated = false;

    private List<Downloader> downloaderList;

    private Subscription subscription;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (viewsCreated && itemsLoaded) {
            onFragmentLoaded();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // add padding
        final ListView lv = getListView();
        lv.setClipToPadding(false);
        final int vertPadding = getResources().getDimensionPixelSize(R.dimen.list_vertical_padding);
        lv.setPadding(0, vertPadding, 0, vertPadding);

        viewsCreated = true;
        if (itemsLoaded) {
            onFragmentLoaded();
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        EventBus.getDefault().registerSticky(this);
        loadItems();
    }

    @Override
    public void onStart() {
        super.onStart();
        EventDistributor.getInstance().register(contentUpdate);
    }

    @Override
    public void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventDistributor.getInstance().unregister(contentUpdate);
        if(subscription != null) {
            subscription.unsubscribe();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if(subscription != null) {
            subscription.unsubscribe();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        adapter = null;
        viewsCreated = false;
    }

    public void onEvent(DownloadEvent event) {
        Log.d(TAG, "onEvent() called with: " + "event = [" + event + "]");
        DownloaderUpdate update = event.update;
        downloaderList = update.downloaders;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        FeedItem item = adapter.getItem(position - l.getHeaderViewsCount());
        if (item != null) {
            ((MainActivity) getActivity()).loadChildFragment(ItemFragment.newInstance(item.getId()));
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if(!isAdded()) {
            return;
        }
        super.onCreateOptionsMenu(menu, inflater);
        if (itemsLoaded) {
            MenuItem clearHistory = menu.add(Menu.NONE, R.id.clear_history_item, Menu.CATEGORY_CONTAINER, R.string.clear_history_label);
            MenuItemCompat.setShowAsAction(clearHistory, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
            TypedArray drawables = getActivity().obtainStyledAttributes(new int[]{R.attr.content_discard});
            clearHistory.setIcon(drawables.getDrawable(0));
            drawables.recycle();
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (itemsLoaded) {
            MenuItem menuItem = menu.findItem(R.id.clear_history_item);
            if (menuItem != null) {
                menuItem.setVisible(playbackHistory != null && !playbackHistory.isEmpty());
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!super.onOptionsItemSelected(item)) {
            switch (item.getItemId()) {
                case R.id.clear_history_item:
                    DBWriter.clearPlaybackHistory();
                    return true;
                default:
                    return false;
            }
        } else {
            return true;
        }
    }

    public void onEvent(QueueEvent event) {
        Log.d(TAG, "onEvent(" + event + ")");
        loadItems();
    }

    private EventDistributor.EventListener contentUpdate = new EventDistributor.EventListener() {

        @Override
        public void update(EventDistributor eventDistributor, Integer arg) {
            if ((arg & EVENTS) != 0) {
                loadItems();
                getActivity().supportInvalidateOptionsMenu();
            }
        }
    };

    private void onFragmentLoaded() {
        if (adapter == null) {
            // played items shoudln't be transparent for this fragment since, *all* items
            // in this fragment will, by definition, be played. So it serves no purpose and can make
            // it harder to read.
            adapter = new FeedItemlistAdapter(getActivity(), itemAccess,
                    new DefaultActionButtonCallback(getActivity()), true, false);
            setListAdapter(adapter);
        }
        setListShown(true);
        adapter.notifyDataSetChanged();
        getActivity().supportInvalidateOptionsMenu();
    }

    private FeedItemlistAdapter.ItemAccess itemAccess = new FeedItemlistAdapter.ItemAccess() {
        @Override
        public boolean isInQueue(FeedItem item) {
            return (queue != null) ? queue.contains(item.getId()) : false;
        }

        @Override
        public int getItemDownloadProgressPercent(FeedItem item) {
            if (downloaderList != null) {
                for (Downloader downloader : downloaderList) {
                    if (downloader.getDownloadRequest().getFeedfileType() == FeedMedia.FEEDFILETYPE_FEEDMEDIA
                            && downloader.getDownloadRequest().getFeedfileId() == item.getMedia().getId()) {
                        return downloader.getDownloadRequest().getProgressPercent();
                    }
                }
            }
            return 0;
        }

        @Override
        public int getCount() {
            return (playbackHistory != null) ? playbackHistory.size() : 0;
        }

        @Override
        public FeedItem getItem(int position) {
            if (playbackHistory != null && 0 <= position && position < playbackHistory.size()) {
                return playbackHistory.get(position);
            } else {
                return null;
            }
        }
    };

    private void loadItems() {
        if(subscription != null) {
            subscription.unsubscribe();
        }
        subscription = Observable.fromCallable(() -> loadData())
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result != null) {
                        playbackHistory = result.first;
                        queue = result.second;
                        itemsLoaded = true;
                        if (viewsCreated) {
                            onFragmentLoaded();
                        }
                    }
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                });
    }

    private Pair<List<FeedItem>, LongList> loadData() {
        List<FeedItem> history = DBReader.getPlaybackHistory();
        LongList queue = DBReader.getQueueIDList();
        DBReader.loadAdditionalFeedItemListData(history);
        return Pair.create(history, queue);
    }

}
