package de.voicehired.wachak.activity;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import de.voicehired.wachak.BuildConfig;
import de.voicehired.wachak.R;
import de.voicehired.wachak.core.preferences.UserPreferences;
import de.voicehired.wachak.core.util.flattr.FlattrUtils;
import de.voicehired.wachak.preferences.PreferenceController;

import org.shredzone.flattr4j.exception.FlattrException;

/** Guides the user through the authentication process */

public class FlattrAuthActivity extends ActionBarActivity {
	private static final String TAG = "FlattrAuthActivity";

	private TextView txtvExplanation;
	private Button butAuthenticate;
	private Button butReturn;
	
	private boolean authSuccessful;
	
	private static FlattrAuthActivity singleton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setTheme(UserPreferences.getTheme());
		super.onCreate(savedInstanceState);
		singleton = this;
		authSuccessful = false;
		if (BuildConfig.DEBUG) Log.d(TAG, "Activity created");
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		setContentView(R.layout.flattr_auth);
		txtvExplanation = (TextView) findViewById(R.id.txtvExplanation);
		butAuthenticate = (Button) findViewById(R.id.but_authenticate);
		butReturn = (Button) findViewById(R.id.but_return_home);

		butReturn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(FlattrAuthActivity.this, MainActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
			}
		});
		
		butAuthenticate.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					FlattrUtils.startAuthProcess(FlattrAuthActivity.this);
				} catch (FlattrException e) {
					e.printStackTrace();
				}
			}	
		});
	}
	
	public static FlattrAuthActivity getInstance() {
		return singleton;
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (BuildConfig.DEBUG) Log.d(TAG, "Activity resumed");
		Uri uri = getIntent().getData();
		if (uri != null) {
			if (BuildConfig.DEBUG) Log.d(TAG, "Received uri");
			FlattrUtils.handleCallback(this, uri);
		}
	}

	public void handleAuthenticationSuccess() {
		authSuccessful = true;
		txtvExplanation.setText(R.string.flattr_auth_success);
		butAuthenticate.setEnabled(false);
		butReturn.setVisibility(View.VISIBLE);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
		return true;
	}
	
	

	@Override
	protected void onPause() {
		super.onPause();
		if (authSuccessful) {
			finish();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			if (authSuccessful) {
				Intent intent = new Intent(this, PreferenceController.getPreferenceActivity());
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
			} else {
				finish();
			}
			break;
		default:
			return false;
		}
		return true;
	}
	

}
