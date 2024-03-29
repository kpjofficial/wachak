package de.voicehired.wachak.fragment;

import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.ListView;

import de.voicehired.wachak.R;
import de.voicehired.wachak.activity.AudioplayerActivity.AudioplayerContentFragment;
import de.voicehired.wachak.adapter.ChaptersListAdapter;
import de.voicehired.wachak.core.feed.Chapter;
import de.voicehired.wachak.core.util.playback.Playable;
import de.voicehired.wachak.core.util.playback.PlaybackController;


public class ChaptersFragment extends ListFragment implements AudioplayerContentFragment {

    private Playable media;
    private PlaybackController controller;

    private ChaptersListAdapter adapter;

    public static ChaptersFragment newInstance(Playable media, PlaybackController controller) {
        ChaptersFragment f = new ChaptersFragment();
        f.media = media;
        f.controller = controller;
        return f;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // add padding
        final ListView lv = getListView();
        lv.setClipToPadding(false);
        final int vertPadding = getResources().getDimensionPixelSize(R.dimen.list_vertical_padding);
        lv.setPadding(0, vertPadding, 0, vertPadding);

        adapter = new ChaptersListAdapter(getActivity(), 0, pos -> {
            Chapter chapter = (Chapter) getListAdapter().getItem(pos);
            controller.seekToChapter(chapter);
        });
        setListAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        adapter.setMedia(media);
        adapter.notifyDataSetChanged();
        if(media == null || media.getChapters() == null) {
            setEmptyText(getString(R.string.no_chapters_label));
        } else {
            setEmptyText(null);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        adapter = null;
    }

    @Override
    public void onMediaChanged(Playable media) {
        if(this.media == media || adapter == null) {
            return;
        }
        this.media = media;
        adapter.setMedia(media);
        adapter.notifyDataSetChanged();
        if(media == null || media.getChapters() == null || media.getChapters().size() == 0) {
            setEmptyText(getString(R.string.no_items_label));
        } else {
            setEmptyText(null);
        }
    }
}
