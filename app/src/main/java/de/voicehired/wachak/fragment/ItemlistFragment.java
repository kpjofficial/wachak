package de.voicehired.wachak.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.joanzapata.iconify.IconDrawable;
import com.joanzapata.iconify.Iconify;
import com.joanzapata.iconify.fonts.FontAwesomeIcons;
import com.joanzapata.iconify.widget.IconTextView;

import org.apache.commons.lang3.Validate;

import java.util.List;

import de.voicehired.wachak.R;
import de.voicehired.wachak.activity.FeedInfoActivity;
import de.voicehired.wachak.activity.MainActivity;
import de.voicehired.wachak.adapter.DefaultActionButtonCallback;
import de.voicehired.wachak.adapter.FeedItemlistAdapter;
import de.voicehired.wachak.core.asynctask.FeedRemover;
import de.voicehired.wachak.core.dialog.ConfirmationDialog;
import de.voicehired.wachak.core.dialog.DownloadRequestErrorDialogCreator;
import de.voicehired.wachak.core.event.DownloadEvent;
import de.voicehired.wachak.core.event.DownloaderUpdate;
import de.voicehired.wachak.core.event.FavoritesEvent;
import de.voicehired.wachak.core.event.FeedItemEvent;
import de.voicehired.wachak.core.event.QueueEvent;
import de.voicehired.wachak.core.feed.EventDistributor;
import de.voicehired.wachak.core.feed.Feed;
import de.voicehired.wachak.core.feed.FeedEvent;
import de.voicehired.wachak.core.feed.FeedItem;
import de.voicehired.wachak.core.feed.FeedItemFilter;
import de.voicehired.wachak.core.feed.FeedMedia;
import de.voicehired.wachak.core.glide.ApGlideSettings;
import de.voicehired.wachak.core.glide.FastBlurTransformation;
import de.voicehired.wachak.core.preferences.UserPreferences;
import de.voicehired.wachak.core.service.download.DownloadService;
import de.voicehired.wachak.core.service.download.Downloader;
import de.voicehired.wachak.core.storage.DBReader;
import de.voicehired.wachak.core.storage.DBTasks;
import de.voicehired.wachak.core.storage.DownloadRequestException;
import de.voicehired.wachak.core.storage.DownloadRequester;
import de.voicehired.wachak.core.util.FeedItemUtil;
import de.voicehired.wachak.core.util.LongList;
import de.voicehired.wachak.core.util.gui.MoreContentListFooterUtil;
import de.voicehired.wachak.dialog.EpisodesApplyActionFragment;
import de.voicehired.wachak.menuhandler.FeedItemMenuHandler;
import de.voicehired.wachak.menuhandler.FeedMenuHandler;
import de.voicehired.wachak.menuhandler.MenuItemUtils;
import de.greenrobot.event.EventBus;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Displays a list of FeedItems.
 */
@SuppressLint("ValidFragment")
public class ItemlistFragment extends ListFragment {
    private static final String TAG = "ItemlistFragment";

    private static final int EVENTS = EventDistributor.UNREAD_ITEMS_UPDATE
            | EventDistributor.FEED_LIST_UPDATE
            | EventDistributor.PLAYER_STATUS_UPDATE;

    public static final String EXTRA_SELECTED_FEEDITEM = "extra.de.voicehired.wachak.activity.selected_feeditem";
    public static final String ARGUMENT_FEED_ID = "argument.de.voicehired.wachak.feed_id";

    protected FeedItemlistAdapter adapter;
    private ContextMenu contextMenu;
    private AdapterView.AdapterContextMenuInfo lastMenuInfo = null;

    private long feedID;
    private Feed feed;
    private LongList queuedItemsIds;
    private LongList favoritedItemsId;

    private boolean itemsLoaded = false;
    private boolean viewsCreated = false;

    private List<Downloader> downloaderList;

    private MoreContentListFooterUtil listFooter;

    private boolean isUpdatingFeed;
    
    private IconTextView txtvFailure;

    private TextView txtvInformation;

    private Subscription subscription;

    /**
     * Creates new ItemlistFragment which shows the Feeditems of a specific
     * feed. Sets 'showFeedtitle' to false
     *
     * @param feedId The id of the feed to show
     * @return the newly created instance of an ItemlistFragment
     */
    public static ItemlistFragment newInstance(long feedId) {
        ItemlistFragment i = new ItemlistFragment();
        Bundle b = new Bundle();
        b.putLong(ARGUMENT_FEED_ID, feedId);
        i.setArguments(b);
        return i;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);

        Bundle args = getArguments();
        Validate.notNull(args);
        feedID = args.getLong(ARGUMENT_FEED_ID);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (viewsCreated && itemsLoaded) {
            onFragmentLoaded();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        EventDistributor.getInstance().register(contentUpdate);
        EventBus.getDefault().registerSticky(this);
        ((MainActivity)getActivity()).getSupportActionBar().setTitle("");
        updateProgressBarVisibility();
        loadItems();
    }

    @Override
    public void onPause() {
        super.onPause();
        EventDistributor.getInstance().unregister(contentUpdate);
        EventBus.getDefault().unregister(this);
        if(subscription != null) {
            subscription.unsubscribe();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        resetViewState();
    }

    private void resetViewState() {
        adapter = null;
        viewsCreated = false;
        listFooter = null;
    }

    private final MenuItemUtils.UpdateRefreshMenuItemChecker updateRefreshMenuItemChecker = new MenuItemUtils.UpdateRefreshMenuItemChecker() {
        @Override
        public boolean isRefreshing() {
            if (feed != null && DownloadService.isRunning && DownloadRequester.getInstance().isDownloadingFile(feed)) {
                return true;
            } else {
                return false;
            }
        }
    };

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if(!isAdded()) {
            return;
        }
        super.onCreateOptionsMenu(menu, inflater);

        if (itemsLoaded) {
            FeedMenuHandler.onCreateOptionsMenu(inflater, menu);

            MenuItem searchItem = menu.findItem(R.id.action_search);
            final SearchView sv = (SearchView) MenuItemCompat.getActionView(searchItem);
            MenuItemUtils.adjustTextColor(getActivity(), sv);
            sv.setQueryHint(getString(R.string.search_hint));
            sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String s) {
                    sv.clearFocus();
                    if (itemsLoaded) {
                        ((MainActivity) getActivity()).loadChildFragment(SearchFragment.newInstance(s, feed.getId()));
                    }
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String s) {
                    return false;
                }
            });
            if(feed == null || feed.getLink() == null) {
                menu.findItem(R.id.share_link_item).setVisible(false);
//                menu.findItem(R.id.visit_website_item).setVisible(false);
            }
            int[] attrs = { R.attr.action_bar_icon_color };
            TypedArray ta = getActivity().obtainStyledAttributes(UserPreferences.getTheme(), attrs);
            int textColor = ta.getColor(0, Color.GRAY);
            ta.recycle();

            menu.findItem(R.id.episode_actions).setIcon(new IconDrawable(getActivity(),
                    FontAwesomeIcons.fa_gears).color(textColor).actionBarSize());

            isUpdatingFeed = MenuItemUtils.updateRefreshMenuItem(menu, R.id.refresh_item, updateRefreshMenuItemChecker);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if (itemsLoaded) {
            FeedMenuHandler.onPrepareOptionsMenu(menu, feed);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!super.onOptionsItemSelected(item)) {
            try {
                if (!FeedMenuHandler.onOptionsItemClicked(getActivity(), item, feed)) {
                    switch (item.getItemId()) {
                        case R.id.episode_actions:
                            EpisodesApplyActionFragment fragment = EpisodesApplyActionFragment
                                    .newInstance(feed.getItems());
                            ((MainActivity)getActivity()).loadChildFragment(fragment);
                            return true;
                        case R.id.remove_item:
                            final FeedRemover remover = new FeedRemover(
                                    getActivity(), feed) {
                                @Override
                                protected void onPostExecute(Void result) {
                                    super.onPostExecute(result);
                                    ((MainActivity) getActivity()).loadFragment(EpisodesFragment.TAG, null);
                                }
                            };
                            ConfirmationDialog conDialog = new ConfirmationDialog(getActivity(),
                                    R.string.remove_feed_label,
                                    R.string.feed_delete_confirmation_msg) {

                                @Override
                                public void onConfirmButtonPressed(
                                        DialogInterface dialog) {
                                    dialog.dismiss();
                                    remover.executeAsync();
                                }
                            };
                            conDialog.createNewDialog().show();
                            return true;
                        default:
                            return false;

                    }
                } else {
                    return true;
                }
            } catch (DownloadRequestException e) {
                e.printStackTrace();
                DownloadRequestErrorDialogCreator.newRequestErrorDialog(getActivity(), e.getMessage());
                return true;
            }
        } else {
            return true;
        }
    }

    private final FeedItemMenuHandler.MenuInterface contextMenuInterface = new FeedItemMenuHandler.MenuInterface() {
        @Override
        public void setItemVisibility(int id, boolean visible) {
            if(contextMenu == null) {
                return;
            }
            MenuItem item = contextMenu.findItem(id);
            if (item != null) {
                item.setVisible(visible);
            }
        }
    };

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo adapterInfo = (AdapterView.AdapterContextMenuInfo) menuInfo;

        // because of addHeaderView(), positions are increased by 1!
        FeedItem item = itemAccess.getItem(adapterInfo.position-1);

        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.feeditemlist_context, menu);

        if (item != null) {
            menu.setHeaderTitle(item.getTitle());
        }

        contextMenu = menu;
        lastMenuInfo = (AdapterView.AdapterContextMenuInfo) menuInfo;
        FeedItemMenuHandler.onPrepareMenu(contextMenuInterface, item, true, queuedItemsIds,
                favoritedItemsId);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if(menuInfo == null) {
            menuInfo = lastMenuInfo;
        }
        // because of addHeaderView(), positions are increased by 1!
        FeedItem selectedItem = itemAccess.getItem(menuInfo.position-1);

        if (selectedItem == null) {
            Log.i(TAG, "Selected item at position " + menuInfo.position + " was null, ignoring selection");
            return super.onContextItemSelected(item);
        }

        try {
            return FeedItemMenuHandler.onMenuItemClicked(getActivity(), item.getItemId(), selectedItem);
        } catch (DownloadRequestException e) {
            // context menu doesn't contain download functionality
            return true;
        }
    }


    @Override
    public void setListAdapter(ListAdapter adapter) {
        // This workaround prevents the ListFragment from setting a list adapter when its state is restored.
        // This is only necessary on API 10 because addFooterView throws an internal exception in this case.
        if (Build.VERSION.SDK_INT > 10 || insideOnFragmentLoaded) {
            super.setListAdapter(adapter);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        registerForContextMenu(getListView());

        viewsCreated = true;
        if (itemsLoaded) {
            onFragmentLoaded();
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if(adapter == null) {
            return;
        }
        FeedItem selection = adapter.getItem(position - l.getHeaderViewsCount());
        if (selection != null) {
            MainActivity activity = (MainActivity) getActivity();
            activity.loadChildFragment(ItemFragment.newInstance(selection.getId()));
            activity.getSupportActionBar().setTitle(feed.getTitle());
        }
    }

    public void onEvent(QueueEvent event) {
        Log.d(TAG, "onEvent() called with: " + "event = [" + event + "]");
        loadItems();
    }

    public void onEvent(FavoritesEvent event) {
        Log.d(TAG, "onEvent() called with: " + "event = [" + event + "]");
        loadItems();
    }

    public void onEvent(FeedEvent event) {
        Log.d(TAG, "onEvent() called with: " + "event = [" + event + "]");
        if(event.feedId == feedID) {
            loadItems();
        }
    }

    public void onEventMainThread(FeedItemEvent event) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]");
        boolean queueChanged = false;
        if(feed == null || feed.getItems() == null || adapter == null) {
            return;
        }
        for(FeedItem item : event.items) {
            int pos = FeedItemUtil.indexOfItemWithId(feed.getItems(), item.getId());
            if(pos >= 0) {
                loadItems();
                return;
            }
        }
    }

    public void onEventMainThread(DownloadEvent event) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]");
        DownloaderUpdate update = event.update;
        downloaderList = update.downloaders;
        if (isUpdatingFeed != event.update.feedIds.length > 0) {
            updateProgressBarVisibility();
        }
        if(adapter != null && update.mediaIds.length > 0) {
            adapter.notifyDataSetChanged();
        }
    }

    private EventDistributor.EventListener contentUpdate = new EventDistributor.EventListener() {

        @Override
        public void update(EventDistributor eventDistributor, Integer arg) {
            if ((EVENTS & arg) != 0) {
                Log.d(TAG, "Received contentUpdate Intent. arg " + arg);
                loadItems();
                updateProgressBarVisibility();
            }
        }
    };

    private void updateProgressBarVisibility() {
        if (isUpdatingFeed != updateRefreshMenuItemChecker.isRefreshing()) {
            getActivity().supportInvalidateOptionsMenu();
        }
        if (listFooter != null) {
            listFooter.setLoadingState(DownloadRequester.getInstance().isDownloadingFeeds());
        }

    }

    private boolean insideOnFragmentLoaded = false;

    private void onFragmentLoaded() {
        if(!isVisible()) {
            return;
        }
        insideOnFragmentLoaded = true;
        if (adapter == null) {
            setListAdapter(null);
            setupHeaderView();
            setupFooterView();
            adapter = new FeedItemlistAdapter(getActivity(), itemAccess, new DefaultActionButtonCallback(getActivity()), false, true);
            setListAdapter(adapter);
        }
        refreshHeaderView();
        setListShown(true);
        adapter.notifyDataSetChanged();

        getActivity().supportInvalidateOptionsMenu();

        if (feed != null && feed.getNextPageLink() == null && listFooter != null) {
            getListView().removeFooterView(listFooter.getRoot());
        }

        insideOnFragmentLoaded = false;

    }

    private void refreshHeaderView() {
        if (getListView() == null || feed == null) {
            Log.e(TAG, "Unable to setup listview: recyclerView = null or feed = null");
            return;
        }
        if(feed.hasLastUpdateFailed()) {
            txtvFailure.setVisibility(View.VISIBLE);
        } else {
            txtvFailure.setVisibility(View.GONE);
        }
        if(feed.getItemFilter() != null) {
            FeedItemFilter filter = feed.getItemFilter();
            if(filter.getValues().length > 0) {
                if(feed.hasLastUpdateFailed()) {
                    RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) txtvInformation.getLayoutParams();
                    p.addRule(RelativeLayout.BELOW, R.id.txtvFailure);
                }
                txtvInformation.setText("{fa-info-circle} " + this.getString(R.string.filtered_label));
                Iconify.addIcons(txtvInformation);
                txtvInformation.setVisibility(View.VISIBLE);
            } else {
                txtvInformation.setVisibility(View.GONE);
            }
        } else {
            txtvInformation.setVisibility(View.GONE);
        }
    }

    private void setupHeaderView() {
        if (getListView() == null || feed == null) {
            Log.e(TAG, "Unable to setup listview: recyclerView = null or feed = null");
            return;
        }
        ListView lv = getListView();
        LayoutInflater inflater = (LayoutInflater)
                getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View header = inflater.inflate(R.layout.feeditemlist_header, lv, false);
        lv.addHeaderView(header);

        TextView txtvTitle = (TextView) header.findViewById(R.id.txtvTitle);
        TextView txtvAuthor = (TextView) header.findViewById(R.id.txtvAuthor);
        ImageView imgvBackground = (ImageView) header.findViewById(R.id.imgvBackground);
        ImageView imgvCover = (ImageView) header.findViewById(R.id.imgvCover);
        ImageButton butShowInfo = (ImageButton) header.findViewById(R.id.butShowInfo);
        txtvInformation = (TextView) header.findViewById(R.id.txtvInformation);
        txtvFailure = (IconTextView) header.findViewById(R.id.txtvFailure);

        txtvTitle.setText(feed.getTitle());
        txtvAuthor.setText(feed.getAuthor());


        // https://github.com/bumptech/glide/issues/529
        imgvBackground.setColorFilter(new LightingColorFilter(0xff828282, 0x000000));

        Glide.with(getActivity())
                .load(feed.getImageUri())
                .placeholder(R.color.image_readability_tint)
                .error(R.color.image_readability_tint)
                .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                .transform(new FastBlurTransformation(getActivity()))
                .dontAnimate()
                .into(imgvBackground);

        Glide.with(getActivity())
                .load(feed.getImageUri())
                .placeholder(R.color.light_gray)
                .error(R.color.light_gray)
                .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                .fitCenter()
                .dontAnimate()
                .into(imgvCover);

        butShowInfo.setOnClickListener(v -> {
            if (viewsCreated && itemsLoaded) {
                Intent startIntent = new Intent(getActivity(), FeedInfoActivity.class);
                startIntent.putExtra(FeedInfoActivity.EXTRA_FEED_ID,
                        feed.getId());
                startActivity(startIntent);
            }
        });
    }


    private void setupFooterView() {
        if (getListView() == null || feed == null) {
            Log.e(TAG, "Unable to setup listview: recyclerView = null or feed = null");
            return;
        }
        if (feed.isPaged() && feed.getNextPageLink() != null) {
            ListView lv = getListView();
            LayoutInflater inflater = (LayoutInflater)
                    getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View header = inflater.inflate(R.layout.more_content_list_footer, lv, false);
            lv.addFooterView(header);
            listFooter = new MoreContentListFooterUtil(header);
            listFooter.setClickListener(() -> {
                if (feed != null) {
                    try {
                        DBTasks.loadNextPageOfFeed(getActivity(), feed, false);
                    } catch (DownloadRequestException e) {
                        e.printStackTrace();
                        DownloadRequestErrorDialogCreator.newRequestErrorDialog(getActivity(), e.getMessage());
                    }
                }
            });
        }
    }

    private FeedItemlistAdapter.ItemAccess itemAccess = new FeedItemlistAdapter.ItemAccess() {

        @Override
        public FeedItem getItem(int position) {
            if (feed != null && 0 <= position && position < feed.getNumOfItems()) {
                return feed.getItemAtIndex(position);
            } else {
                return null;
            }
        }

        @Override
        public int getCount() {
            return (feed != null) ? feed.getNumOfItems() : 0;
        }

        @Override
        public boolean isInQueue(FeedItem item) {
            return (queuedItemsIds != null) && queuedItemsIds.contains(item.getId());
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
                        feed = (Feed) result[0];
                        queuedItemsIds = (LongList) result[1];
                        favoritedItemsId = (LongList) result[2];
                        itemsLoaded = true;
                        if (viewsCreated) {
                            onFragmentLoaded();
                        }
                    }
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                });
    }

    private Object[] loadData() {
        Feed feed = DBReader.getFeed(feedID);
        if(feed != null && feed.getItemFilter() != null) {
            FeedItemFilter filter = feed.getItemFilter();
            feed.setItems(filter.filter(feed.getItems()));
        }
        LongList queuedItemsIds = DBReader.getQueueIDList();
        LongList favoritedItemsId = DBReader.getFavoriteIDList();
        return new Object[] { feed, queuedItemsIds, favoritedItemsId };
    }

}
