package de.voicehired.wachak.adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.joanzapata.iconify.Iconify;
import com.joanzapata.iconify.widget.IconTextView;

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.voicehired.wachak.R;
import de.voicehired.wachak.activity.MainActivity;
import de.voicehired.wachak.core.feed.Feed;
import de.voicehired.wachak.core.glide.ApGlideSettings;
import de.voicehired.wachak.core.preferences.UserPreferences;
import de.voicehired.wachak.fragment.AddFeedFragment;
import de.voicehired.wachak.fragment.AllEpisodesFragment;
import de.voicehired.wachak.fragment.DownloadsFragment;
import de.voicehired.wachak.fragment.EpisodesFragment;
import de.voicehired.wachak.fragment.NewEpisodesFragment;
import de.voicehired.wachak.fragment.PlaybackHistoryFragment;
import de.voicehired.wachak.fragment.QueueFragment;
import de.voicehired.wachak.wachakchanges.DiscoverAuthors;

/**
 * BaseAdapter for the navigation drawer
 */
public class NavListAdapter extends BaseAdapter
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final int VIEW_TYPE_COUNT = 3;
    public static final int VIEW_TYPE_NAV = 0;
    public static final int VIEW_TYPE_SECTION_DIVIDER = 1;
    public static final int VIEW_TYPE_SUBSCRIPTION = 2;

    private static List<String> tags;
    private static String[] titles;

    private ItemAccess itemAccess;
    private Context context;

    public NavListAdapter(ItemAccess itemAccess, Context context) {
        this.itemAccess = itemAccess;
        this.context = context;

        titles = context.getResources().getStringArray(R.array.nav_drawer_titles);
        loadItems();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(UserPreferences.PREF_HIDDEN_DRAWER_ITEMS)) {
            loadItems();
        }
    }

    private void loadItems() {
        List<String> newTags = new ArrayList<String>(Arrays.asList(MainActivity.NAV_DRAWER_TAGS));
        List<String> hiddenFragments = UserPreferences.getHiddenDrawerItems();
        for(String hidden : hiddenFragments) {
            newTags.remove(hidden);
        }
        tags = newTags;
        notifyDataSetChanged();
    }

    public String getLabel(String tag) {
        int index = ArrayUtils.indexOf(MainActivity.NAV_DRAWER_TAGS, tag);
        return titles[index];
    }

    private Drawable getDrawable(String tag) {
        int icon;
        switch (tag) {
            case QueueFragment.TAG:
                icon = R.attr.stat_playlist;
                break;
            case NewEpisodesFragment.TAG:
                icon = R.attr.ic_new;
                break;
            case EpisodesFragment.TAG:
                icon = R.attr.feed;
                break;
            case AllEpisodesFragment.TAG:
                icon = R.attr.feed;
                break;
            case DownloadsFragment.TAG:
                icon = R.attr.av_download;
                break;
            case PlaybackHistoryFragment.TAG:
                icon = R.attr.ic_history;
                break;
            case DiscoverAuthors.TAG:
                icon = R.attr.action_search;
                break;
            default:
                return null;
        }
        TypedArray ta = context.obtainStyledAttributes(new int[] { icon } );
        Drawable result = ta.getDrawable(0);
        ta.recycle();
        return result;
    }

    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }


    @Override
    public int getCount() {
        return getSubscriptionOffset() + itemAccess.getCount();
    }

    @Override
    public Object getItem(int position) {
        int viewType = getItemViewType(position);
        if (viewType == VIEW_TYPE_NAV) {
            return getLabel(tags.get(position));
        } else if (viewType == VIEW_TYPE_SECTION_DIVIDER) {
            return "";
        } else {
            return itemAccess.getItem(position);
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        if (0 <= position && position < tags.size()) {
            return VIEW_TYPE_NAV;
        } else if (position < getSubscriptionOffset()) {
            return VIEW_TYPE_SECTION_DIVIDER;
        } else {
            return VIEW_TYPE_SUBSCRIPTION;
        }
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    public int getSubscriptionOffset() {
        return tags.size() > 0 ? tags.size() + 1 : 0;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int viewType = getItemViewType(position);
        View v = null;
        if (viewType == VIEW_TYPE_NAV) {
            v = getNavView((String) getItem(position), position, convertView, parent);
        } else if (viewType == VIEW_TYPE_SECTION_DIVIDER) {
            v = getSectionDividerView(convertView, parent);
        } else {
            v = getFeedView(position - getSubscriptionOffset(), convertView, parent);
        }
        if (v != null && viewType != VIEW_TYPE_SECTION_DIVIDER) {
            TextView txtvTitle = (TextView) v.findViewById(R.id.txtvTitle);
            if (position == itemAccess.getSelectedItemIndex()) {
                txtvTitle.setTypeface(null, Typeface.BOLD);
            } else {
                txtvTitle.setTypeface(null, Typeface.NORMAL);
            }
        }
        return v;
    }

    private View getNavView(String title, int position, View convertView, ViewGroup parent) {
        NavHolder holder;
        if (convertView == null) {
            holder = new NavHolder();
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            convertView = inflater.inflate(R.layout.nav_listitem, parent, false);

            holder.image = (ImageView) convertView.findViewById(R.id.imgvCover);
            holder.title = (TextView) convertView.findViewById(R.id.txtvTitle);
            holder.count = (TextView) convertView.findViewById(R.id.txtvCount);
            convertView.setTag(holder);
        } else {
            holder = (NavHolder) convertView.getTag();
        }

        holder.title.setText(title);

        String tag = tags.get(position);
        if (tag.equals(QueueFragment.TAG)) {
            int queueSize = itemAccess.getQueueSize();
            if (queueSize > 0) {
                holder.count.setVisibility(View.VISIBLE);
                holder.count.setText(String.valueOf(queueSize));
            } else {
                holder.count.setVisibility(View.GONE);
            }
        } else if (tag.equals(EpisodesFragment.TAG)) {
            int unreadItems = itemAccess.getNumberOfNewItems();
            if (unreadItems > 0) {
                holder.count.setVisibility(View.VISIBLE);
                holder.count.setText(String.valueOf(unreadItems));
            } else {
                holder.count.setVisibility(View.GONE);
            }
        } else if(tag.equals(DownloadsFragment.TAG) && UserPreferences.isEnableAutodownload()) {
            int epCacheSize = UserPreferences.getEpisodeCacheSize();
            // don't count episodes that can be reclaimed
            int spaceUsed = itemAccess.getNumberOfDownloadedItems() -
                    itemAccess.getReclaimableItems();

            if (spaceUsed >= epCacheSize) {
                holder.count.setText("{md-disc-full 150%}");
                Iconify.addIcons(holder.count);
                holder.count.setVisibility(View.VISIBLE);
                holder.count.setOnClickListener(v -> {
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.episode_cache_full_title)
                            .setMessage(R.string.episode_cache_full_message)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> {})
                            .show();
                });
            } else {
                holder.count.setVisibility(View.GONE);
            }
        } else {
            holder.count.setVisibility(View.GONE);
        }

        holder.image.setImageDrawable(getDrawable(tags.get(position)));

        return convertView;
    }

    private View getSectionDividerView(View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        convertView = inflater.inflate(R.layout.nav_section_item, parent, false);

        convertView.setEnabled(false);
        convertView.setOnClickListener(null);

        return convertView;
    }

    private View getFeedView(int feedPos, View convertView, ViewGroup parent) {
        FeedHolder holder;
        Feed feed = itemAccess.getItem(feedPos);

        if (convertView == null) {
            holder = new FeedHolder();
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            convertView = inflater.inflate(R.layout.nav_feedlistitem, parent, false);

            holder.image = (ImageView) convertView.findViewById(R.id.imgvCover);
            holder.title = (TextView) convertView.findViewById(R.id.txtvTitle);
            holder.failure = (IconTextView) convertView.findViewById(R.id.itxtvFailure);
            holder.count = (TextView) convertView.findViewById(R.id.txtvCount);
            convertView.setTag(holder);
        } else {
            holder = (FeedHolder) convertView.getTag();
        }

        Glide.with(context)
                .load(feed.getImageUri())
                .placeholder(R.color.light_gray)
                .error(R.color.light_gray)
                .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                .fitCenter()
                .dontAnimate()
                .into(holder.image);

        holder.title.setText(feed.getTitle());


        if(feed.hasLastUpdateFailed()) {
            RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) holder.title.getLayoutParams();
            p.addRule(RelativeLayout.LEFT_OF, R.id.itxtvFailure);
            holder.failure.setVisibility(View.VISIBLE);
        } else {
            RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) holder.title.getLayoutParams();
            p.addRule(RelativeLayout.LEFT_OF, R.id.txtvCount);
            holder.failure.setVisibility(View.GONE);
        }
        int counter = itemAccess.getFeedCounter(feed.getId());
        if(counter > 0) {
            holder.count.setVisibility(View.VISIBLE);
            holder.count.setText(String.valueOf(counter));
            holder.count.setTypeface(holder.title.getTypeface());
        } else {
            holder.count.setVisibility(View.GONE);
        }
        return convertView;
    }

    static class NavHolder {
        ImageView image;
        TextView title;
        TextView count;
    }

    static class FeedHolder {
        ImageView image;
        TextView title;
        IconTextView failure;
        TextView count;
    }

    public interface ItemAccess {
        int getCount();
        Feed getItem(int position);
        int getSelectedItemIndex();
        int getQueueSize();
        int getNumberOfNewItems();
        int getNumberOfDownloadedItems();
        int getReclaimableItems();
        int getFeedCounter(long feedId);
    }

}
