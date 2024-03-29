package de.voicehired.wachak.core.util.gui;

import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import de.voicehired.wachak.core.R;

/**
 * Utility methods for the more_content_list_footer layout.
 */
public class MoreContentListFooterUtil {

    private final View root;

    private boolean loading;

    private Listener listener;

    public MoreContentListFooterUtil(View root) {
        this.root = root;
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null && !loading) {
                    listener.onClick();
                }
            }
        });
    }

    public void setLoadingState(boolean newState) {
        final ImageView imageView = (ImageView) root.findViewById(R.id.imgExpand);
        final ProgressBar progressBar = (ProgressBar) root.findViewById(R.id.progBar);
        if (newState) {
            imageView.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
        } else {
            imageView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
        }
        loading = newState;
    }

    public void setClickListener(Listener l) {
        listener = l;
    }

    public static interface Listener {
        public void onClick();
    }

    public View getRoot() {
        return root;
    }
}
