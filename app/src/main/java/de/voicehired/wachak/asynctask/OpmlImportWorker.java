package de.voicehired.wachak.asynctask;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import de.voicehired.wachak.core.R;
import de.voicehired.wachak.core.opml.OpmlElement;
import de.voicehired.wachak.core.opml.OpmlReader;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

public class OpmlImportWorker extends
		AsyncTask<Void, Void, ArrayList<OpmlElement>> {
	private static final String TAG = "OpmlImportWorker";

	private Context context;
	private Exception exception;

	private ProgressDialog progDialog;

    private Reader mReader;

    public OpmlImportWorker(Context context, Reader reader) {
        super();
        this.context = context;
        this.mReader=reader;
    }

	@Override
	protected ArrayList<OpmlElement> doInBackground(Void... params) {
		Log.d(TAG, "Starting background work");

        if (mReader==null) {
            return null;
        }

		OpmlReader opmlReader = new OpmlReader();
		try {
            ArrayList<OpmlElement> result = opmlReader.readDocument(mReader);
			mReader.close();
			return result;
		} catch (XmlPullParserException e) {
			e.printStackTrace();
			exception = e;
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			exception = e;
			return null;
		}

	}

	@Override
	protected void onPostExecute(ArrayList<OpmlElement> result) {
        if (mReader != null) {
            try {
                mReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
		progDialog.dismiss();
		if (exception != null) {
			Log.d(TAG, "An error occurred while trying to parse the opml document");
			AlertDialog.Builder alert = new AlertDialog.Builder(context);
			alert.setTitle(R.string.error_label);
			alert.setMessage(context.getString(R.string.opml_reader_error)
					+ exception.getMessage());
			alert.setNeutralButton(android.R.string.ok, (dialog, which) -> {
                dialog.dismiss();
            });
			alert.create().show();
		}
	}

	@Override
	protected void onPreExecute() {
		progDialog = new ProgressDialog(context);
		progDialog.setMessage(context.getString(R.string.reading_opml_label));
		progDialog.setIndeterminate(true);
		progDialog.setCancelable(false);
		progDialog.show();
	}
	
	public boolean wasSuccessful() {
		return exception != null;
	}
	
	@SuppressLint("NewApi")
	public void executeAsync() {
		if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.GINGERBREAD_MR1) {
			executeOnExecutor(THREAD_POOL_EXECUTOR);
		} else {
			execute();
		}
	}

}
