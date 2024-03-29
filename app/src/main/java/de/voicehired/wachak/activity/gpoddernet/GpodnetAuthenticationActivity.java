package de.voicehired.wachak.activity.gpoddernet;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ViewFlipper;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import de.voicehired.wachak.BuildConfig;
import de.voicehired.wachak.R;
import de.voicehired.wachak.activity.MainActivity;
import de.voicehired.wachak.core.gpoddernet.GpodnetService;
import de.voicehired.wachak.core.gpoddernet.GpodnetServiceException;
import de.voicehired.wachak.core.gpoddernet.model.GpodnetDevice;
import de.voicehired.wachak.core.preferences.GpodnetPreferences;
import de.voicehired.wachak.core.preferences.UserPreferences;
import de.voicehired.wachak.core.service.GpodnetSyncService;

/**
 * Guides the user through the authentication process
 * Step 1: Request username and password from user
 * Step 2: Choose device from a list of available devices or create a new one
 * Step 3: Choose from a list of actions
 */
public class GpodnetAuthenticationActivity extends ActionBarActivity {
    private static final String TAG = "GpodnetAuthenticationActivity";

    private static final String CURRENT_STEP = "current_step";

    private ViewFlipper viewFlipper;

    private static final int STEP_DEFAULT = -1;
    private static final int STEP_LOGIN = 0;
    private static final int STEP_DEVICE = 1;
    private static final int STEP_FINISH = 2;

    private int currentStep = -1;

    private GpodnetService service;
    private volatile String username;
    private volatile String password;
    private volatile GpodnetDevice selectedDevice;

    View[] views;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(UserPreferences.getTheme());
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.gpodnetauth_activity);
        service = new GpodnetService();

        viewFlipper = (ViewFlipper) findViewById(R.id.viewflipper);
        LayoutInflater inflater = (LayoutInflater)
                getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        views = new View[]{
                inflater.inflate(R.layout.gpodnetauth_credentials, viewFlipper, false),
                inflater.inflate(R.layout.gpodnetauth_device, viewFlipper, false),
                inflater.inflate(R.layout.gpodnetauth_finish, viewFlipper, false)
        };
        for (View view : views) {
            viewFlipper.addView(view);
        }
        advance();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (service != null) {
            service.shutdown();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    }

    private void setupLoginView(View view) {
        final EditText username = (EditText) view.findViewById(R.id.etxtUsername);
        final EditText password = (EditText) view.findViewById(R.id.etxtPassword);
        final Button login = (Button) view.findViewById(R.id.butLogin);
        final TextView txtvError = (TextView) view.findViewById(R.id.txtvError);
        final ProgressBar progressBar = (ProgressBar) view.findViewById(R.id.progBarLogin);

        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                final String usernameStr = username.getText().toString();
                final String passwordStr = password.getText().toString();

                if (BuildConfig.DEBUG) Log.d(TAG, "Checking login credentials");
                new AsyncTask<GpodnetService, Void, Void>() {

                    volatile Exception exception;

                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        login.setEnabled(false);
                        progressBar.setVisibility(View.VISIBLE);
                        txtvError.setVisibility(View.GONE);

                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        super.onPostExecute(aVoid);
                        login.setEnabled(true);
                        progressBar.setVisibility(View.GONE);

                        if (exception == null) {
                            advance();
                        } else {
                            txtvError.setText(exception.getMessage());
                            txtvError.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    protected Void doInBackground(GpodnetService... params) {
                        try {
                            params[0].authenticate(usernameStr, passwordStr);
                            GpodnetAuthenticationActivity.this.username = usernameStr;
                            GpodnetAuthenticationActivity.this.password = passwordStr;
                        } catch (GpodnetServiceException e) {
                            e.printStackTrace();
                            exception = e;
                        }
                        return null;
                    }
                }.execute(service);
            }
        });
    }

    private void setupDeviceView(View view) {
        final EditText deviceID = (EditText) view.findViewById(R.id.etxtDeviceID);
        final EditText caption = (EditText) view.findViewById(R.id.etxtCaption);
        final Button createNewDevice = (Button) view.findViewById(R.id.butCreateNewDevice);
        final Button chooseDevice = (Button) view.findViewById(R.id.butChooseExistingDevice);
        final TextView txtvError = (TextView) view.findViewById(R.id.txtvError);
        final ProgressBar progBarCreateDevice = (ProgressBar) view.findViewById(R.id.progbarCreateDevice);
        final Spinner spinnerDevices = (Spinner) view.findViewById(R.id.spinnerChooseDevice);


        // load device list
        final AtomicReference<List<GpodnetDevice>> devices = new AtomicReference<List<GpodnetDevice>>();
        new AsyncTask<GpodnetService, Void, List<GpodnetDevice>>() {

            private volatile Exception exception;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                chooseDevice.setEnabled(false);
                spinnerDevices.setEnabled(false);
                createNewDevice.setEnabled(false);
            }

            @Override
            protected void onPostExecute(List<GpodnetDevice> gpodnetDevices) {
                super.onPostExecute(gpodnetDevices);
                if (gpodnetDevices != null) {
                    List<String> deviceNames = new ArrayList<String>();
                    for (GpodnetDevice device : gpodnetDevices) {
                        deviceNames.add(device.getCaption());
                    }
                    spinnerDevices.setAdapter(new ArrayAdapter<String>(GpodnetAuthenticationActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, deviceNames));
                    spinnerDevices.setEnabled(true);
                    if (!deviceNames.isEmpty()) {
                        chooseDevice.setEnabled(true);
                    }
                    devices.set(gpodnetDevices);
                    createNewDevice.setEnabled(true);
                }
            }

            @Override
            protected List<GpodnetDevice> doInBackground(GpodnetService... params) {
                try {
                    return params[0].getDevices(username);
                } catch (GpodnetServiceException e) {
                    e.printStackTrace();
                    exception = e;
                    return null;
                }
            }
        }.execute(service);


        createNewDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkDeviceIDText(deviceID, txtvError, devices.get())) {
                    final String deviceStr = deviceID.getText().toString();
                    final String captionStr = caption.getText().toString();

                    new AsyncTask<GpodnetService, Void, GpodnetDevice>() {

                        private volatile Exception exception;

                        @Override
                        protected void onPreExecute() {
                            super.onPreExecute();
                            createNewDevice.setEnabled(false);
                            chooseDevice.setEnabled(false);
                            progBarCreateDevice.setVisibility(View.VISIBLE);
                            txtvError.setVisibility(View.GONE);
                        }

                        @Override
                        protected void onPostExecute(GpodnetDevice result) {
                            super.onPostExecute(result);
                            createNewDevice.setEnabled(true);
                            chooseDevice.setEnabled(true);
                            progBarCreateDevice.setVisibility(View.GONE);
                            if (exception == null) {
                                selectedDevice = result;
                                advance();
                            } else {
                                txtvError.setText(exception.getMessage());
                                txtvError.setVisibility(View.VISIBLE);
                            }
                        }

                        @Override
                        protected GpodnetDevice doInBackground(GpodnetService... params) {
                            try {
                                params[0].configureDevice(username, deviceStr, captionStr, GpodnetDevice.DeviceType.MOBILE);
                                return new GpodnetDevice(deviceStr, captionStr, GpodnetDevice.DeviceType.MOBILE.toString(), 0);
                            } catch (GpodnetServiceException e) {
                                e.printStackTrace();
                                exception = e;
                            }
                            return null;
                        }
                    }.execute(service);
                }
            }
        });

        deviceID.setText(generateDeviceID());
        chooseDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int position = spinnerDevices.getSelectedItemPosition();
                if (position != AdapterView.INVALID_POSITION) {
                    selectedDevice = devices.get().get(position);
                    advance();
                }
            }
        });
    }


    private String generateDeviceID() {
        final int DEVICE_ID_LENGTH = 10;
        StringBuilder buffer = new StringBuilder(DEVICE_ID_LENGTH);
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < DEVICE_ID_LENGTH; i++) {
            buffer.append(random.nextInt(10));

        }
        return buffer.toString();
    }

    private boolean checkDeviceIDText(EditText deviceID, TextView txtvError, List<GpodnetDevice> devices) {
        String text = deviceID.getText().toString();
        if (text.length() == 0) {
            txtvError.setText(R.string.gpodnetauth_device_errorEmpty);
            txtvError.setVisibility(View.VISIBLE);
            return false;
        } else {
            if (devices != null) {
                for (GpodnetDevice device : devices) {
                    if (device.getId().equals(text)) {
                        txtvError.setText(R.string.gpodnetauth_device_errorAlreadyUsed);
                        txtvError.setVisibility(View.VISIBLE);
                        return false;
                    }
                }
                txtvError.setVisibility(View.GONE);
                return true;
            }
            return true;
        }

    }

    private void setupFinishView(View view) {
        final Button sync = (Button) view.findViewById(R.id.butSyncNow);
        final Button back = (Button) view.findViewById(R.id.butGoMainscreen);

        sync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GpodnetSyncService.sendSyncIntent(GpodnetAuthenticationActivity.this);
                finish();
            }
        });
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(GpodnetAuthenticationActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        });
    }

    private void writeLoginCredentials() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Writing login credentials");
        GpodnetPreferences.setUsername(username);
        GpodnetPreferences.setPassword(password);
        GpodnetPreferences.setDeviceID(selectedDevice.getId());
    }

    private void advance() {
        if (currentStep < STEP_FINISH) {

            View view = views[currentStep + 1];
            if (currentStep == STEP_DEFAULT) {
                setupLoginView(view);
            } else if (currentStep == STEP_LOGIN) {
                if (username == null || password == null) {
                    throw new IllegalStateException("Username and password must not be null here");
                } else {
                    setupDeviceView(view);
                }
            } else if (currentStep == STEP_DEVICE) {
                if (selectedDevice == null) {
                    throw new IllegalStateException("Device must not be null here");
                } else {
                    writeLoginCredentials();
                    setupFinishView(view);
                }
            }
            if (currentStep != STEP_DEFAULT) {
                viewFlipper.showNext();
            }
            currentStep++;
        } else {
            finish();
        }
    }
}
