package com.axionvpn.client;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DialerFilter;
import android.widget.Toast;

/* this class draws the waiting_overlay layout into the provided layout for the duration of the
   background task. it will execute the optional onCompleted in the UI thread just like asynctask.
   // TODO improve activity recreation behavior
 */

public class WaitingTask extends AsyncTask<WaitingRunnable,Void,Void> {

    private Activity activity;
    private String message;
    private WaitingRunnable[] runnables;
    private ProgressDialog dialog;

    public WaitingTask(Activity activity, String dialogMessage) {
        this.activity = activity;
        message = dialogMessage;
    }

    @Override
    protected void onPreExecute() {
        dialog = ProgressDialog.show(activity,
                "Please wait", message, true, true);
    }

    @Override
    protected Void doInBackground(WaitingRunnable...runnables) {
        this.runnables = runnables;
        for (WaitingRunnable r : runnables) {
            try {
                r.inBackground();
            } catch (Exception e) {
                r.backgroundException = e;
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        dialog.dismiss();
        for (WaitingRunnable r : runnables)
            r.onCompleted();
    }
}
