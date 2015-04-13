package net.axionvpn.client;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.prefs.Preferences;

import de.blinkt.openvpn.R;
import de.blinkt.openvpn.activities.ConfigConverter;
import de.blinkt.openvpn.activities.MainActivity;

abstract class WaitingRunnable {
    abstract public void inBackground();
    public void onCompleted() {
    }
}

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
        for (WaitingRunnable r : runnables)
            r.inBackground();
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

    final static int IMPORT_PROFILE = 0;

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

        updateRegions();
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
            private VpnDesc [] vpns = null;
            private Exception error = null;
            @Override
            public void inBackground() {
                try {
                    vpns = AxionService.getRegions();
                } catch (Exception e) {
                    error = e;
                }
            }
            @Override
            public void onCompleted() {
                if(vpns != null) {
                    ArrayAdapter<VpnDesc> adapter = new ArrayAdapter<VpnDesc>(getApplicationContext(), R.layout.spinner_region_item, vpns);
                    spinner.setAdapter(adapter);
                } else if (error != null) {
                    Toast.makeText(getApplicationContext(),error.getMessage(),Toast.LENGTH_SHORT).show();
                    Log.e("AxionVpn","Error updating regions",error);
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
                String username = editUser.getText().toString();
                String password = editPass.getText().toString();

                SharedPreferences.Editor prefs = getPreferences(MODE_PRIVATE).edit();
                prefs.putString("username", username);
                prefs.putString("password", password);
                prefs.apply();

                AxionService.setLoginInfo(username, password);

                // get selected VPN id
                ArrayAdapter<VpnDesc> adapter = (ArrayAdapter<VpnDesc>)spinner.getAdapter();
                final VpnDesc vpn = adapter.getItem(spinner.getSelectedItemPosition());

                ViewGroup root = (ViewGroup) findViewById(R.id.root_layout);
                new WaitingTask(this, root).execute(new WaitingRunnable() {
                    private File path = new File(getFilesDir(), "conf.ovpn");
                    @Override
                    public void inBackground() {
                        String conf = AxionService.getConfigForRegion(vpn.id);
                        try {
                            FileWriter fw = new FileWriter(path);
                            fw.write(conf);
                            fw.close();
                        } catch (IOException e) {
                            Log.e("AxionVpn", Log.getStackTraceString(e));
                        }
                    }
                    @Override
                    public void onCompleted() {
                        if (path.exists()) {
                            Uri uri = Uri.fromFile(path);
                            Intent startImport = new Intent(getApplicationContext(), ConfigConverter.class);
                            startImport.setAction(ConfigConverter.IMPORT_PROFILE);
                            startImport.setData(uri);
                            startActivityForResult(startImport, IMPORT_PROFILE);
                        } // TODO else error dialog
                    }
                });
                break;

            case R.id.button_main:
                startActivity(new Intent(this, MainActivity.class));
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode==IMPORT_PROFILE) {
            Log.d("AxionVpn", "Import result: " + String.valueOf(resultCode));
        }
    }
}
