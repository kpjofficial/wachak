package de.voicehired.wachak.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import de.voicehired.wachak.R;
import de.voicehired.wachak.core.service.download.DownloadRequest;
import de.voicehired.wachak.core.service.download.DownloadStatus;
import de.voicehired.wachak.core.service.download.Downloader;
import de.voicehired.wachak.core.util.Converter;
import de.voicehired.wachak.core.util.ThemeUtils;

public class DownloadlistAdapter extends BaseAdapter {

    public static final int SELECTION_NONE = -1;

    private int selectedItemIndex;
    private ItemAccess itemAccess;
    private Context context;

    public DownloadlistAdapter(Context context,
                               ItemAccess itemAccess) {
        super();
        this.selectedItemIndex = SELECTION_NONE;
        this.context = context;
        this.itemAccess = itemAccess;
    }

    @Override
    public int getCount() {
        return itemAccess.getCount();
    }

    @Override
    public Downloader getItem(int position) {
        return itemAccess.getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        Downloader downloader = getItem(position);
        DownloadRequest request = downloader.getDownloadRequest();
        // Inflate layout
        if (convertView == null) {
            holder = new Holder();
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.downloadlist_item, parent, false);
            holder.title = (TextView) convertView.findViewById(R.id.txtvTitle);
            holder.downloaded = (TextView) convertView
                    .findViewById(R.id.txtvDownloaded);
            holder.percent = (TextView) convertView
                    .findViewById(R.id.txtvPercent);
            holder.progbar = (ProgressBar) convertView
                    .findViewById(R.id.progProgress);
            holder.butSecondary = (ImageButton) convertView
                    .findViewById(R.id.butSecondaryAction);

            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }

        if (position == selectedItemIndex) {
            convertView.setBackgroundColor(convertView.getResources().getColor(
                    ThemeUtils.getSelectionBackgroundColor()));
        } else {
            convertView.setBackgroundResource(0);
        }

        holder.title.setText(request.getTitle());

        holder.progbar.setIndeterminate(request.getSoFar() <= 0);

        String strDownloaded = Converter.byteToString(request.getSoFar());
        if (request.getSize() != DownloadStatus.SIZE_UNKNOWN) {
            strDownloaded += " / " + Converter.byteToString(request.getSize());
            holder.percent.setText(request.getProgressPercent() + "%");
            holder.progbar.setProgress(request.getProgressPercent());
            holder.percent.setVisibility(View.VISIBLE);
        } else {
            holder.progbar.setProgress(0);
            holder.percent.setVisibility(View.INVISIBLE);
        }

        holder.downloaded.setText(strDownloaded);

        holder.butSecondary.setFocusable(false);
        holder.butSecondary.setTag(downloader);
        holder.butSecondary.setOnClickListener(butSecondaryListener);

        return convertView;
    }

    private View.OnClickListener butSecondaryListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Downloader downloader = (Downloader) v.getTag();
            itemAccess.onSecondaryActionClick(downloader);
        }
    };

    static class Holder {
        TextView title;
        TextView downloaded;
        TextView percent;
        ProgressBar progbar;
        ImageButton butSecondary;
    }

    public int getSelectedItemIndex() {
        return selectedItemIndex;
    }

    public void setSelectedItemIndex(int selectedItemIndex) {
        this.selectedItemIndex = selectedItemIndex;
        notifyDataSetChanged();
    }

    public interface ItemAccess {
        public int getCount();

        public Downloader getItem(int position);

        public void onSecondaryActionClick(Downloader downloader);
    }

}
