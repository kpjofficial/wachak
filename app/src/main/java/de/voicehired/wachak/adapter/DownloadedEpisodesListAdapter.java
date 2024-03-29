package de.voicehired.wachak.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import de.voicehired.wachak.R;
import de.voicehired.wachak.core.feed.FeedItem;
import de.voicehired.wachak.core.glide.ApGlideSettings;
import de.voicehired.wachak.core.util.Converter;
import de.voicehired.wachak.core.util.DateUtils;

/**
 * Shows a list of downloaded episodes
 */
public class DownloadedEpisodesListAdapter extends BaseAdapter {

    private final Context context;
    private final ItemAccess itemAccess;

    public DownloadedEpisodesListAdapter(Context context, ItemAccess itemAccess) {
        super();
        this.context = context;
        this.itemAccess = itemAccess;
    }

    @Override
    public int getCount() {
        return itemAccess.getCount();
    }

    @Override
    public FeedItem getItem(int position) {
        return itemAccess.getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        final FeedItem item = getItem(position);
        if (item == null) return null;

        if (convertView == null) {
            holder = new Holder();
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.downloaded_episodeslist_item,
                    parent, false);
            holder.imageView = (ImageView) convertView.findViewById(R.id.imgvImage);
            holder.title = (TextView) convertView.findViewById(R.id.txtvTitle);
            holder.txtvSize = (TextView) convertView.findViewById(R.id.txtvSize);
            holder.queueStatus = (ImageView) convertView.findViewById(R.id.imgvInPlaylist);
            holder.pubDate = (TextView) convertView
                    .findViewById(R.id.txtvPublished);
            holder.butSecondary = (ImageButton) convertView
                    .findViewById(R.id.butSecondaryAction);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }

        Glide.with(context)
                .load(item.getImageUri())
                .placeholder(R.color.light_gray)
                .error(R.color.light_gray)
                .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                .fitCenter()
                .dontAnimate()
                .into(holder.imageView);

        holder.title.setText(item.getTitle());
        holder.txtvSize.setText(Converter.byteToString(item.getMedia().getSize()));
        holder.queueStatus.setVisibility(item.isTagged(FeedItem.TAG_QUEUE) ? View.VISIBLE : View.GONE);
        String pubDateStr = DateUtils.formatAbbrev(context, item.getPubDate());
        holder.pubDate.setText(pubDateStr);

        FeedItem.State state = item.getState();
        if (state == FeedItem.State.PLAYING) {
            holder.butSecondary.setEnabled(false);
        } else {
            holder.butSecondary.setEnabled(true);
        }
        holder.butSecondary.setFocusable(false);
        holder.butSecondary.setTag(item);
        holder.butSecondary.setOnClickListener(secondaryActionListener);

        return convertView;
    }

    private View.OnClickListener secondaryActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            FeedItem item = (FeedItem) v.getTag();
            itemAccess.onFeedItemSecondaryAction(item);
        }
    };


    static class Holder {
        ImageView imageView;
        TextView title;
        TextView txtvSize;
        ImageView queueStatus;
        TextView pubDate;
        ImageButton butSecondary;
    }

    public interface ItemAccess {
        int getCount();

        FeedItem getItem(int position);

        void onFeedItemSecondaryAction(FeedItem item);
    }
}
