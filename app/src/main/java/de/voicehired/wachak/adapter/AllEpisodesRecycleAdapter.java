package de.voicehired.wachak.adapter;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;
import com.joanzapata.iconify.Iconify;
import com.nineoldandroids.view.ViewHelper;

import java.lang.ref.WeakReference;

import de.voicehired.wachak.R;
import de.voicehired.wachak.activity.MainActivity;
import de.voicehired.wachak.core.feed.FeedItem;
import de.voicehired.wachak.core.feed.FeedMedia;
import de.voicehired.wachak.core.glide.ApGlideSettings;
import de.voicehired.wachak.core.preferences.UserPreferences;
import de.voicehired.wachak.core.storage.DownloadRequester;
import de.voicehired.wachak.core.util.Converter;
import de.voicehired.wachak.core.util.DateUtils;
import de.voicehired.wachak.core.util.LongList;
import de.voicehired.wachak.core.util.NetworkUtils;
import de.voicehired.wachak.fragment.ItemFragment;
import de.voicehired.wachak.menuhandler.FeedItemMenuHandler;

/**
 * List adapter for the list of new episodes
 */
public class AllEpisodesRecycleAdapter extends RecyclerView.Adapter<AllEpisodesRecycleAdapter.Holder> {

    private static final String TAG = AllEpisodesRecycleAdapter.class.getSimpleName();

    private final WeakReference<MainActivity> mainActivityRef;
    private final ItemAccess itemAccess;
    private final ActionButtonCallback actionButtonCallback;
    private final ActionButtonUtils actionButtonUtils;
    private final boolean showOnlyNewEpisodes;

    private int position = -1;

    private final int playingBackGroundColor;
    private final int normalBackGroundColor;

    public AllEpisodesRecycleAdapter(MainActivity mainActivity,
                                     ItemAccess itemAccess,
                                     ActionButtonCallback actionButtonCallback,
                                     boolean showOnlyNewEpisodes) {
        super();
        this.mainActivityRef = new WeakReference<>(mainActivity);
        this.itemAccess = itemAccess;
        this.actionButtonUtils = new ActionButtonUtils(mainActivity);
        this.actionButtonCallback = actionButtonCallback;
        this.showOnlyNewEpisodes = showOnlyNewEpisodes;

        if(UserPreferences.getTheme() == R.style.Theme_AntennaPod_Dark) {
            playingBackGroundColor = mainActivity.getResources().getColor(R.color.highlight_dark);
        } else {
            playingBackGroundColor = mainActivity.getResources().getColor(R.color.highlight_light);
        }
        normalBackGroundColor = mainActivity.getResources().getColor(android.R.color.transparent);
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.new_episodes_listitem, parent, false);
        Holder holder = new Holder(view);
        holder.container = (FrameLayout) view.findViewById(R.id.container);
        holder.placeholder = (TextView) view.findViewById(R.id.txtvPlaceholder);
        holder.title = (TextView) view.findViewById(R.id.txtvTitle);
        holder.pubDate = (TextView) view
                .findViewById(R.id.txtvPublished);
        holder.statusUnread = view.findViewById(R.id.statusUnread);
        holder.butSecondary = (ImageButton) view
                .findViewById(R.id.butSecondaryAction);
        holder.queueStatus = (ImageView) view
                .findViewById(R.id.imgvInPlaylist);
        holder.progress = (ProgressBar) view
                .findViewById(R.id.pbar_progress);
        holder.cover = (ImageView) view.findViewById(R.id.imgvCover);
        holder.txtvDuration = (TextView) view.findViewById(R.id.txtvDuration);
        holder.item = null;
        holder.mainActivityRef = mainActivityRef;
        holder.position = -1;
        // so we can grab this later
        view.setTag(holder);

        return holder;
    }

    @Override
    public void onBindViewHolder(final Holder holder, int position) {
        final FeedItem item = itemAccess.getItem(position);
        if (item == null) return;
        holder.itemView.setOnLongClickListener(v -> {
            this.position = position;
            return false;
        });
        holder.item = item;
        holder.position = position;
        holder.placeholder.setVisibility(View.VISIBLE);
        holder.placeholder.setText(item.getFeed().getTitle());
        holder.title.setText(item.getTitle());
        String pubDateStr = DateUtils.formatAbbrev(mainActivityRef.get(), item.getPubDate());
        holder.pubDate.setText(pubDateStr);
        if (showOnlyNewEpisodes || false == item.isNew()) {
            holder.statusUnread.setVisibility(View.INVISIBLE);
        } else {
            holder.statusUnread.setVisibility(View.VISIBLE);
        }

        FeedMedia media = item.getMedia();
        if (media != null) {
            final boolean isDownloadingMedia = DownloadRequester.getInstance().isDownloadingFile(media);

            if (media.getDuration() > 0) {
                holder.txtvDuration.setText(Converter.getDurationStringLong(media.getDuration()));
            } else if (media.getSize() > 0) {
                holder.txtvDuration.setText(Converter.byteToString(media.getSize()));
            } else if(false == media.checkedOnSizeButUnknown()) {
                holder.txtvDuration.setText("{fa-spinner}");
                Iconify.addIcons(holder.txtvDuration);
                NetworkUtils.getFeedMediaSizeObservable(media)
                        .subscribe(
                                size -> {
                                    if (size > 0) {
                                        holder.txtvDuration.setText(Converter.byteToString(size));
                                    } else {
                                        holder.txtvDuration.setText("");
                                    }
                                }, error -> {
                                    holder.txtvDuration.setText("");
                                    Log.e(TAG, Log.getStackTraceString(error));
                                });
            } else {
                holder.txtvDuration.setText("");
            }

            FeedItem.State state = item.getState();
            if (isDownloadingMedia) {
                holder.progress.setVisibility(View.VISIBLE);
                // item is being downloaded
                holder.progress.setProgress(itemAccess.getItemDownloadProgressPercent(item));
            } else if (state == FeedItem.State.PLAYING
                    || state == FeedItem.State.IN_PROGRESS) {
                if (media.getDuration() > 0) {
                    int progress = (int) (100.0 * media.getPosition() / media.getDuration());
                    holder.progress.setProgress(progress);
                    holder.progress.setVisibility(View.VISIBLE);
                }
            } else {
                holder.progress.setVisibility(View.GONE);
            }

            if(media.isCurrentlyPlaying()) {
                holder.container.setBackgroundColor(playingBackGroundColor);
            } else {
                holder.container.setBackgroundColor(normalBackGroundColor);
            }
        } else {
            holder.progress.setVisibility(View.GONE);
            holder.txtvDuration.setVisibility(View.GONE);
        }

        boolean isInQueue = itemAccess.isInQueue(item);
        if (isInQueue) {
            holder.queueStatus.setVisibility(View.VISIBLE);
        } else {
            holder.queueStatus.setVisibility(View.INVISIBLE);
        }

        actionButtonUtils.configureActionButton(holder.butSecondary, item, isInQueue);
        holder.butSecondary.setFocusable(false);
        holder.butSecondary.setTag(item);
        holder.butSecondary.setOnClickListener(secondaryActionListener);

        Glide.with(mainActivityRef.get())
                .load(item.getImageUri())
                .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                .fitCenter()
                .dontAnimate()
                .into(new CoverTarget(item.getFeed().getImageUri(), holder.placeholder, holder.cover));
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return itemAccess.getCount();
    }

    public FeedItem getItem(int position) {
        return itemAccess.getItem(position);
    }

    public int getPosition() {
        int pos = position;
        position = -1; // reset
        return pos;
    }

    private class CoverTarget extends GlideDrawableImageViewTarget {

        private final WeakReference<Uri> fallback;
        private final WeakReference<TextView> placeholder;
        private final WeakReference<ImageView> cover;

        public CoverTarget(Uri fallbackUri, TextView txtvPlaceholder, ImageView imgvCover) {
            super(imgvCover);
            fallback = new WeakReference<>(fallbackUri);
            placeholder = new WeakReference<>(txtvPlaceholder);
            cover = new WeakReference<>(imgvCover);
        }

        @Override
        public void onLoadFailed(Exception e, Drawable errorDrawable) {
            Uri fallbackUri = fallback.get();
            TextView txtvPlaceholder = placeholder.get();
            ImageView imgvCover = cover.get();
            if(fallbackUri != null && txtvPlaceholder != null && imgvCover != null) {
                Glide.with(mainActivityRef.get())
                        .load(fallbackUri)
                        .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                        .fitCenter()
                        .dontAnimate()
                        .into(new CoverTarget(null, txtvPlaceholder, imgvCover));
            }
        }

        @Override
        public void onResourceReady(GlideDrawable drawable, GlideAnimation anim) {
            super.onResourceReady(drawable, anim);
            TextView txtvPlaceholder = placeholder.get();
            if(txtvPlaceholder != null) {
                txtvPlaceholder.setVisibility(View.INVISIBLE);
            }
        }
    }

    private View.OnClickListener secondaryActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            FeedItem item = (FeedItem) v.getTag();
            actionButtonCallback.onActionButtonPressed(item);
        }
    };

    public class Holder extends RecyclerView.ViewHolder
            implements View.OnClickListener,
                       View.OnCreateContextMenuListener,
                       ItemTouchHelperViewHolder {
        FrameLayout container;
        TextView placeholder;
        TextView title;
        TextView pubDate;
        View statusUnread;
        ImageView queueStatus;
        ImageView cover;
        ProgressBar progress;
        TextView txtvDuration;
        ImageButton butSecondary;
        FeedItem item;
        WeakReference<MainActivity> mainActivityRef;
        int position;

        public Holder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);
            itemView.setOnCreateContextMenuListener(this);
        }

        @Override
        public void onClick(View v) {
            MainActivity mainActivity = mainActivityRef.get();
            if (mainActivity != null) {
                mainActivity.loadChildFragment(ItemFragment.newInstance(item.getId()));
            }
        }

        @Override
        public void onItemSelected() {
            ViewHelper.setAlpha(itemView, 0.5f);
        }

        @Override
        public void onItemClear() {
            ViewHelper.setAlpha(itemView, 1.0f);
        }

        public FeedItem getFeedItem() { return item; }

        @Override
        public void onCreateContextMenu(final ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            FeedItem item = itemAccess.getItem(getAdapterPosition());

            MenuInflater inflater = mainActivityRef.get().getMenuInflater();
            inflater.inflate(R.menu.allepisodes_context, menu);

            if (item != null) {
                menu.setHeaderTitle(item.getTitle());
            }

            FeedItemMenuHandler.MenuInterface contextMenuInterface = (id, visible) -> {
                if (menu == null) {
                    return;
                }
                MenuItem item1 = menu.findItem(id);
                if (item1 != null) {
                    item1.setVisible(visible);
                }
            };
            FeedItemMenuHandler.onPrepareMenu(contextMenuInterface, item, true,
                    itemAccess.getQueueIds(), itemAccess.getFavoritesIds());
        }

    }

    public interface ItemAccess {

        int getCount();

        FeedItem getItem(int position);

        int getItemDownloadProgressPercent(FeedItem item);

        boolean isInQueue(FeedItem item);

        LongList getQueueIds();

        LongList getFavoritesIds();

    }

    /**
     * Notifies a View Holder of relevant callbacks from
     * {@link ItemTouchHelper.Callback}.
     */
    public interface ItemTouchHelperViewHolder {

        /**
         * Called when the {@link ItemTouchHelper} first registers an
         * item as being moved or swiped.
         * Implementations should update the item view to indicate
         * it's active state.
         */
        void onItemSelected();


        /**
         * Called when the {@link ItemTouchHelper} has completed the
         * move or swipe, and the active item state should be cleared.
         */
        void onItemClear();
    }
}
