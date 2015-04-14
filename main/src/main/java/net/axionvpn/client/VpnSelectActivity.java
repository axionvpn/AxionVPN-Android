package net.axionvpn.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import de.blinkt.openvpn.LaunchVPN;
import de.blinkt.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.activities.MainActivity;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ProfileManager;

abstract class WaitingRunnable {
    public Exception backgroundException = null;
    abstract public void inBackground() throws Exception; // required
    public void onCompleted() { }        // optional
}

/* this class draws the waiting_overlay layout into the provided layout for the duration of the
   background task. it will execute the optional onCompleted in the UI thread just like asynctask.
   // TODO improve activity recreation behavior
 */
class WaitingTask extends AsyncTask<WaitingRunnable,Void,Void> {

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

public class VpnSelectActivity extends Activity implements View.OnClickListener {

    private EditText editUser,editPass;
    private Spinner spinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vpn_select);

        editUser = (EditText)findViewById(R.id.edit_username);
        editPass = (EditText)findViewById(R.id.edit_password);
        spinner = (Spinner) findViewById(R.id.spinner_region_select);

        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        editUser.setText(prefs.getString("username", ""));
        editPass.setText(prefs.getString("password", ""));

        if (savedInstanceState==null) {
            updateRegions();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_vpn_select, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void updateRegions() {
        ViewGroup root = (ViewGroup) findViewById(R.id.root_layout);
        new WaitingTask(this, root).execute(new WaitingRunnable() {
            private VpnDesc[] vpns = null;

            @Override
            public void inBackground() {
                vpns = AxionService.getRegions();
            }

            @Override
            public void onCompleted() {
                if (vpns != null) {
                    ArrayAdapter<VpnDesc> adapter = new ArrayAdapter<VpnDesc>(getApplicationContext(), R.layout.spinner_region_item, vpns);
                    spinner.setAdapter(adapter);
                } else if (backgroundException != null) {
                    Toast.makeText(getApplicationContext(), backgroundException.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("AxionVpn", "Error updating regions", backgroundException);
                }
            }
        });
    }
    public void updateLoginInfo() {
        String username = editUser.getText().toString();
        String password = editPass.getText().toString();

        SharedPreferences.Editor prefs = getPreferences(MODE_PRIVATE).edit();
        prefs.putString("username", username);
        prefs.putString("password", password);
        prefs.apply();

        AxionService.setLoginInfo(username, password);
    }

    /* fetches the config from Axion's HTTP server for this region, and hands it off
       to the vpn client.
     */
    public void connectVpn(final int regionId) {
        ViewGroup root = (ViewGroup) findViewById(R.id.root_layout);
        new WaitingTask(this, root).execute(new WaitingRunnable() {
            private VpnProfile vpnProfile = null;
            @Override
            public void inBackground() throws Exception {
                String conf = AxionService.getConfigForRegion(regionId);
                ByteArrayInputStream is = new ByteArrayInputStream(conf.getBytes());
                InputStreamReader isr = new InputStreamReader(is);
                ConfigParser cp = new ConfigParser();
                cp.parseConfig(isr);
                vpnProfile = cp.convertProfile();
                vpnProfile.mName = "AxionVPN";
            }
            @Override
            public void onCompleted() {
                if (backgroundException != null) {
                    Toast.makeText(getApplicationContext(),backgroundException.getMessage(),Toast.LENGTH_SHORT).show();
                    Log.e("AxionVpn","Error fetching config data",backgroundException);
                } else {
                    Context ctx = getApplicationContext();

                    // save into openvpn profile manager
                    ProfileManager vpl = ProfileManager.getInstance(ctx);
                    VpnProfile prevProfile = vpl.getProfileByName("AxionVPN");
                    if (prevProfile!=null)
                        vpl.removeProfile(ctx,prevProfile);
                    vpl.addProfile(vpnProfile);
                    vpl.saveProfile(ctx, vpnProfile);
                    vpl.saveProfileList(ctx);

                    // launch vpn client
                    Intent intent = new Intent(ctx,LaunchVPN.class);
                    intent.putExtra(LaunchVPN.EXTRA_KEY, vpnProfile.getUUID().toString());
                    intent.setAction(Intent.ACTION_MAIN);
                    intent.putExtra(LaunchVPN.EXTRA_HIDELOG, true);

                    startActivity(intent);
                }
            }
        });

    }

    public void onClick(View view) {
        switch (view.getId()) {

            case R.id.button_get_regions:
                updateRegions();
                break;

            case R.id.button_connect:
                // get username/pass from text fields and store in prefs
                updateLoginInfo();
                // get selected VPN id
                ArrayAdapter<VpnDesc> adapter = (ArrayAdapter<VpnDesc>)spinner.getAdapter();
                final VpnDesc vpn = adapter.getItem(spinner.getSelectedItemPosition());
                // fetch config and connect
                connectVpn(vpn.id);
                break;

            case R.id.button_main:
                startActivity(new Intent(this, MainActivity.class));
                break;
        }
    }
}
