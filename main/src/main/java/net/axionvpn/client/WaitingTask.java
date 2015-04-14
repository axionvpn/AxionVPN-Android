package net.axionvpn.client;

import android.content.Context;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/* this class draws the waiting_overlay layout into the provided layout for the duration of the
   background task. it will execute the optional onCompleted in the UI thread just like asynctask.
   // TODO improve activity recreation behavior
 */

public class WaitingTask extends AsyncTask<WaitingRunnable,Void,Void> {

    private Context context;
    private ViewGroup root;
    private WaitingRunnable[] runnables;

    public WaitingTask(Context context, ViewGroup root) {
        this.context = context;
        this.root = root;
    }

    @Override
    protected void onPreExecute() {
        // overlay progress UI
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.waiting_overlay,root,true);
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
        for (WaitingRunnable r : runnables)
            r.onCompleted();

        // remove progress UI
        View waiting = root.findViewById(R.id.waiting_root);
        if(waiting!=null)
            root.removeView(waiting);
    }
}
