package de.voicehired.wachak.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Log;

import com.afollestad.materialdialogs.MaterialDialog;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

import de.voicehired.wachak.R;

public class RatingDialog {

    private static final String TAG = RatingDialog.class.getSimpleName();
    private static final int AFTER_DAYS = 7;

    private static WeakReference<Context> mContext;
    private static SharedPreferences mPreferences;
    private static Dialog mDialog;

    private static final String PREFS_NAME = "RatingPrefs";
    private static final String KEY_RATED = "KEY_WAS_RATED";
    private static final String KEY_FIRST_START_DATE = "KEY_FIRST_HIT_DATE";

    public static void init(Context context) {
        mContext = new WeakReference<>(context);
        mPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        long firstDate = mPreferences.getLong(KEY_FIRST_START_DATE, 0);
        if (firstDate == 0) {
            resetStartDate();
        }
    }

    public static void check() {
        if (mDialog != null && mDialog.isShowing()) {
            return;
        }
        if (shouldShow()) {
            try {
                mDialog = createDialog();
                if (mDialog != null) {
                    mDialog.show();
                }
            } catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }
    }

    public static void rateNow() {
        Context context = mContext.get();
        if(context == null) {
            return;
        }
        final String appPackage = "de.voicehired.wachak";
        final Uri uri = Uri.parse("https://play.google.com/store/apps/details?id=" + appPackage);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        saveRated();
    }

    public static boolean rated() {
        return mPreferences.getBoolean(KEY_RATED, false);
    }

    public static void saveRated() {
        mPreferences
                .edit()
                .putBoolean(KEY_RATED, true)
                .apply();
    }

    private static void resetStartDate() {
        mPreferences
                .edit()
                .putLong(KEY_FIRST_START_DATE, System.currentTimeMillis())
                .apply();
    }

    private static boolean shouldShow() {
        if (rated()) {
            return false;
        }

        long now = System.currentTimeMillis();
        long firstDate = mPreferences.getLong(KEY_FIRST_START_DATE, now);
        long diff = now - firstDate;
        long diffDays = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
        if (diffDays >= AFTER_DAYS) {
            return true;
        } else {
            return false;
        }
    }

    @Nullable
    private static MaterialDialog createDialog() {
        Context context = mContext.get();
        if(context == null) {
            return null;
        }
        MaterialDialog dialog = new MaterialDialog.Builder(context)
                .title(R.string.rating_title)
                .content(R.string.rating_message)
                .positiveText(R.string.rating_now_label)
                .negativeText(R.string.rating_never_label)
                .neutralText(R.string.rating_later_label)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        rateNow();
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        saveRated();
                    }

                    @Override
                    public void onNeutral(MaterialDialog dialog) {
                        resetStartDate();
                    }
                })
                .cancelListener(dialog1 -> resetStartDate())
                .build();
        return dialog;
    }
}
