package de.voicehired.wachak.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import de.voicehired.wachak.R;
import de.voicehired.wachak.activity.MainActivity;
import de.voicehired.wachak.activity.OnlineFeedViewActivity;
import de.voicehired.wachak.activity.OpmlImportFromPathActivity;
import de.voicehired.wachak.fragment.gpodnet.GpodnetMainFragment;

/**
 * Provides actions for adding new podcast subscriptions
 */
public class AddFeedFragment extends Fragment {

    public static final String TAG = "AddFeedFragment";

    /**
     * Preset value for url text field.
     */
    public static final String ARG_FEED_URL = "feedurl";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View root = inflater.inflate(R.layout.addfeed, container, false);

        final EditText etxtFeedurl = (EditText) root.findViewById(R.id.etxtFeedurl);

        Bundle args = getArguments();
        if (args != null && args.getString(ARG_FEED_URL) != null) {
            etxtFeedurl.setText(args.getString(ARG_FEED_URL));
        }

        Button butBrowserGpoddernet = (Button) root.findViewById(R.id.butBrowseGpoddernet);
        Button butOpmlImport = (Button) root.findViewById(R.id.butOpmlImport);
        Button butConfirm = (Button) root.findViewById(R.id.butConfirm);
        Button butSearchITunes = (Button) root.findViewById(R.id.butSearchItunes);

        final MainActivity activity = (MainActivity) getActivity();
        activity.getSupportActionBar().setTitle(R.string.add_feed_label);

        butSearchITunes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.loadChildFragment(new ItunesSearchFragment());
            }
        });

        butBrowserGpoddernet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.loadChildFragment(new GpodnetMainFragment());
            }
        });

        butOpmlImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(),
                        OpmlImportFromPathActivity.class));
            }
        });

        butConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), OnlineFeedViewActivity.class);
                intent.putExtra(OnlineFeedViewActivity.ARG_FEEDURL, etxtFeedurl.getText().toString());
                intent.putExtra(OnlineFeedViewActivity.ARG_TITLE, getString(R.string.add_feed_label));
                startActivity(intent);
            }
        });

        return root;
    }
}
