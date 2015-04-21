package net.axionvpn.client;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import de.blinkt.openvpn.activities.DisconnectVPN;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.VpnStatus;


/**
 */
public class StatusFragment extends Fragment implements VpnStatus.StateListener,View.OnClickListener {

    private EditText publicIp;
    private EditText acctType;
    private TextView statusLabel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_status, container, false);
        publicIp = (EditText)v.findViewById(R.id.et_ip);
        acctType = (EditText)v.findViewById(R.id.et_acct_type);
        statusLabel = (TextView)v.findViewById(R.id.status_label);
        v.findViewById(R.id.button_disconnect).setOnClickListener(this);

        return v;
    }

    @Override
    public void updateState(String state, String logmessage, int localizedResId, VpnStatus.ConnectionStatus level) {

        if(level == VpnStatus.ConnectionStatus.LEVEL_CONNECTED) {
            // launch a background task to fetch and populate the config info fields
            new AsyncTask<Void, Void, RespGetConnInfo>() {
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
                    if (info != null) {
                        publicIp.setText(info.ip_address);
                        acctType.setText(info.acc_type);
                    }
                    if (backgroundExc != null) {
                        LogManager.e("calling getConnInfo", backgroundExc);
                    }
                }
            }.execute();
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    statusLabel.setText("Connection Secured");
                }
            });

        } else {
            // not connected yet, so just zero out these display fields
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    statusLabel.setText("Connecting to Service");
                    publicIp.setText("");
                    acctType.setText("");
                }
            });
        }
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()) {
            case R.id.button_disconnect:
                Intent disconnectVPN = new Intent(getActivity(), DisconnectVPN.class);
                disconnectVPN.setAction(OpenVPNService.DISCONNECT_VPN);
                startActivity(disconnectVPN);
                break;
        }
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
    }

}