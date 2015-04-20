package net.axionvpn.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.blinkt.openvpn.LaunchVPN;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.activities.DisconnectVPN;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;

public class MainActivity extends Activity implements View.OnClickListener, VpnStatus.StateListener {

    private EditText editUser,editPass;
    private Spinner regionList;
    private boolean disconnected = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vpn_select);

        editUser = (EditText)findViewById(R.id.et_username);
        editPass = (EditText)findViewById(R.id.et_password);
        regionList = (Spinner) findViewById(R.id.sp_region_select);

        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        editUser.setText(prefs.getString("username", ""));
        editPass.setText(prefs.getString("password", ""));

        VpnDesc [] region_cache = retrieveVpnRegionCache();
        if(region_cache!=null) {
            updateVpnRegionUi(region_cache);
        } else {
            updateRegions();
        }
    }

    public void rememberLastRegion(VpnDesc vpn) {
        getPreferences(MODE_PRIVATE).edit().putInt("region_last_selected", vpn.id).apply();
    }

    public void updateVpnRegionUi(VpnDesc [] vpns) {
        ArrayAdapter<VpnDesc> adapter = new ArrayAdapter<VpnDesc>(getApplicationContext(), android.R.layout.simple_spinner_dropdown_item, vpns);
        regionList.setAdapter(adapter);
        int lastSelected = getPreferences(MODE_PRIVATE).getInt("region_last_selected", -1);
        if (lastSelected>=0) {
            for(int i=0;i<vpns.length;i++) {
                if (vpns[i].id == lastSelected) {
                    regionList.setSelection(i);
                    break;
                }
            }
        }
    }

    public void storeVpnRegionCache(VpnDesc [] vpns) {
        SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
        Gson gson = new Gson(); // json encode the objects for storage as strings
        Set<String> region_cache = new HashSet<String>();
        for(VpnDesc v : vpns)
            region_cache.add(gson.toJson(v));
        editor.putStringSet("region_cache",region_cache);
        editor.apply();
    }

    public VpnDesc [] retrieveVpnRegionCache() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        Set<String> region_cache = prefs.getStringSet("region_cache",null);

        if(region_cache==null)
            return null;

        List<VpnDesc> vpns = new ArrayList<VpnDesc>(region_cache.size());
        Gson gson = new Gson(); // objects stored as json strings
        for(String s:region_cache) {
            VpnDesc vpn = gson.fromJson(s,VpnDesc.class);
            vpns.add(vpn);
        }
        return vpns.toArray(new VpnDesc[vpns.size()]);
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
                    updateVpnRegionUi(vpns);
                    storeVpnRegionCache(vpns);
                } else if (backgroundException != null) {
                    Toast.makeText(getApplicationContext(), backgroundException.getMessage(), Toast.LENGTH_SHORT).show();
                    LogManager.e("Error updating regions", backgroundException);
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
                    Toast.makeText(getApplicationContext(), backgroundException.getMessage(), Toast.LENGTH_SHORT).show();
                    LogManager.e("Error fetching config data", backgroundException);
                } else {
                    Context ctx = getApplicationContext();

                    // save into openvpn profile manager
                    ProfileManager vpl = ProfileManager.getInstance(ctx);
                    VpnProfile prevProfile = vpl.getProfileByName("AxionVPN");
                    if (prevProfile != null)
                        vpl.removeProfile(ctx, prevProfile);
                    vpl.addProfile(vpnProfile);
                    vpl.saveProfile(ctx, vpnProfile);
                    vpl.saveProfileList(ctx);

                    // launch vpn client
                    Intent intent = new Intent(ctx, LaunchVPN.class);
                    intent.putExtra(LaunchVPN.EXTRA_KEY, vpnProfile.getUUID().toString());
                    intent.setAction(Intent.ACTION_MAIN);
                    intent.putExtra(LaunchVPN.EXTRA_HIDELOG, true);

                    startActivity(intent);
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        VpnStatus.removeStateListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        VpnStatus.addStateListener(this);
    }

    @Override
    public void updateState(String state, String logmessage, int localizedResId, VpnStatus.ConnectionStatus level) {
        switch (level) {

            case LEVEL_CONNECTED:
                new AsyncTask<Void,Void,RespGetConnInfo>() {
                    private Exception backgroundExc = null;
                    @Override
                    protected RespGetConnInfo doInBackground(Void... voids) {
                        try {
                            return AxionService.getConnInfo();
                        } catch (Exception e) {
                            backgroundExc = e;
                            return null;
                        }
                    }
                    @Override
                    protected void onPostExecute(RespGetConnInfo info) {
                        if (info!=null) {
                            ((EditText) findViewById(R.id.et_ip)       ).setText(info.ip_address);
                            ((EditText) findViewById(R.id.et_acct_type)).setText(info.acc_type);
                        }
                    }
                }.execute();
            case LEVEL_CONNECTING_NO_SERVER_REPLY_YET:
            case LEVEL_CONNECTING_SERVER_REPLIED:
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((Button)findViewById(R.id.button_connect)).setText(R.string.disconnect_selected);
                    }
                });

                disconnected = false;
                break;

            default:
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((Button)findViewById(R.id.button_connect)).setText(R.string.connect_selected);
                        ((EditText) findViewById(R.id.et_ip)       ).setText("");
                        ((EditText) findViewById(R.id.et_acct_type)).setText("");
                    }
                });
                disconnected = true;
                break;
        }
    }

    public void onClick(View view) {
        switch (view.getId()) {

            case R.id.button_get_regions:
                updateRegions();
                break;

            case R.id.button_connect:
                if(disconnected) {
                    // get username/pass from text fields and store in prefs
                    updateLoginInfo();
                    // get selected VPN id
                    VpnDesc vpn = (VpnDesc) regionList.getSelectedItem();
                    rememberLastRegion(vpn);
                    // fetch config and connect
                    connectVpn(vpn.id);
                } else {
                    Intent disconnectVPN = new Intent(this, DisconnectVPN.class);
                    disconnectVPN.setAction(OpenVPNService.DISCONNECT_VPN);
                    startActivity(disconnectVPN);
                }
                break;

        }
    }
}
