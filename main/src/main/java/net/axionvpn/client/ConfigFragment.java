package net.axionvpn.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
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
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;


/**
 */
public class ConfigFragment extends Fragment implements View.OnClickListener,VpnStatus.StateListener {

    private EditText editUser,editPass;
    private Spinner regionList;
    private Button connectButton;
    private WaitingTask bgTask = null;
    private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getActivity().getPreferences(Activity.MODE_PRIVATE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_axion_config, container, false);

        editUser   = (EditText)v.findViewById(R.id.et_username);
        editPass   = (EditText)v.findViewById(R.id.et_password);
        regionList = (Spinner) v.findViewById(R.id.sp_region_select);
        connectButton = (Button) v.findViewById(R.id.button_connect);

        editUser.setText(prefs.getString("username", ""));
        editPass.setText(prefs.getString("password", ""));

        v.findViewById(R.id.button_get_regions).setOnClickListener(this);
        v.findViewById(R.id.button_connect).setOnClickListener(this);

        VpnDesc [] region_cache = retrieveVpnRegionCache();
        if(region_cache!=null) {
            updateVpnRegionUi(region_cache);
        } else {
            updateRegions();
        }
        return v;
    }

    public void rememberLastRegion(VpnDesc vpn) {
        prefs.edit().putInt("region_last_selected", vpn.id).apply();
    }

    public void updateVpnRegionUi(VpnDesc [] vpns) {
        ArrayAdapter<VpnDesc> adapter = new ArrayAdapter<VpnDesc>(getActivity(), android.R.layout.simple_spinner_item, vpns);
        adapter.setDropDownViewResource(R.layout.spinner_region_item);
        regionList.setAdapter(adapter);
        int lastSelected = prefs.getInt("region_last_selected", -1);
        if (lastSelected>=0) {
            for(int i=0;i<vpns.length;i++) {
                if (vpns[i].id == lastSelected) {
                    regionList.setSelection(i);
                    break;
                }
            }
        }
    }

    public void storeVpnRegionCache(VpnDesc[] vpns) {
        Gson gson = new Gson(); // json encode the objects for storage as strings
        Set<String> region_cache = new HashSet<String>();
        for(VpnDesc v : vpns)
            region_cache.add(gson.toJson(v));
        prefs.edit().putStringSet("region_cache", region_cache).apply();
    }

    public VpnDesc[] retrieveVpnRegionCache() {
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

        bgTask = new WaitingTask(getActivity(),"Fetching VPN Regions");
        bgTask.execute(new WaitingRunnable() {
            private VpnDesc[] vpns = null;

            @Override
            public void inBackground() throws Exception {
                vpns = AxionService.getRegions();
            }

            @Override
            public void onCompleted() {
                if (vpns != null) {
                    updateVpnRegionUi(vpns);
                    storeVpnRegionCache(vpns);
                } else if (backgroundException != null) {
                    Toast.makeText(getActivity(), backgroundException.getMessage(), Toast.LENGTH_SHORT).show();
                    LogManager.e("Error updating regions", backgroundException);
                }
                bgTask = null;
            }
        });
    }

    public boolean updateLoginInfo() {
        String username = editUser.getText().toString();
        String password = editPass.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(getActivity(),"Username and password to Axion service must be provided",
                    Toast.LENGTH_SHORT).show();
            if (username.isEmpty())
                editUser.requestFocus();
            else
                editPass.requestFocus();
            return false;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("username", username);
        editor.putString("password", password);
        editor.apply();

        AxionService.setLoginInfo(username, password);
        return true;
    }

    /* fetches the config from Axion's HTTP server for this region, and hands it off
       to the vpn client.
     */
    public void connectVpn(final int regionId) {
        connectButton.setEnabled(false);

        bgTask = new WaitingTask(getActivity(),"Fetching VPN Config");
        bgTask.execute(new WaitingRunnable() {
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
                    Toast.makeText(getActivity(), backgroundException.getMessage(), Toast.LENGTH_SHORT).show();
                    LogManager.e("Error fetching config data", backgroundException);
                    connectButton.setEnabled(true);
                } else {
                    Context ctx = getActivity().getApplicationContext();

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
                bgTask = null;
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        VpnStatus.addStateListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        VpnStatus.removeStateListener(this);
        if(bgTask != null)
            bgTask.cancel(true);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_get_regions:
                updateRegions();
                break;

            case R.id.button_connect:
                // get username/pass from text fields and store in prefs
                if (!updateLoginInfo())
                    break;
                // get selected VPN id
                VpnDesc vpn = (VpnDesc) regionList.getSelectedItem();
                if(vpn==null)
                    break;
                rememberLastRegion(vpn);
                // fetch config and connect
                connectVpn(vpn.id);
            break;
        }
    }

    @Override
    public void updateState(String state, String logmessage, int localizedResId, VpnStatus.ConnectionStatus level) {
        switch (level) {
            case LEVEL_NONETWORK:
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getActivity(), "No Available Network", Toast.LENGTH_SHORT).show();
                    }
                });
                break;
            case LEVEL_NOTCONNECTED:
            case LEVEL_AUTH_FAILED:
            case UNKNOWN_LEVEL:
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        connectButton.setEnabled(true);
                    }
                });
                break;
        }
    }
}
