package de.voicehired.wachak.activity;

import android.net.Uri;
import android.os.Bundle;

import de.voicehired.wachak.core.preferences.UserPreferences;

/** Lets the user start the OPML-import process. */
public class OpmlImportFromIntentActivity extends OpmlImportBaseActivity {

    private static final String TAG = "OpmlImportFromIntentAct";

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		setTheme(UserPreferences.getTheme());
		super.onCreate(savedInstanceState);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Uri uri = getIntent().getData();
        importUri(uri);
    }

    @Override
    protected boolean finishWhenCanceled() {
        return true;
    }

}
