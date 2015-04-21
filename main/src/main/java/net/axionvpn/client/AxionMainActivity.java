package net.axionvpn.client;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

import de.blinkt.openvpn.core.VpnStatus;


public class AxionMainActivity extends FragmentActivity implements VpnStatus.StateListener {

    private Boolean disconnected = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();
        VpnStatus.addStateListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        VpnStatus.removeStateListener(this);
    }

    @Override
    public void updateState(String state, String logmessage, int localizedResId, VpnStatus.ConnectionStatus level) {
        Fragment fragment = null;
        boolean nowDisconnected = true;
        switch (level) {
            case LEVEL_CONNECTED:
//            case LEVEL_CONNECTING_NO_SERVER_REPLY_YET:
//            case LEVEL_CONNECTING_SERVER_REPLIED:
                nowDisconnected = false;
        }
        // see if the fragment needs to be swapped
        if(disconnected==null || disconnected.booleanValue()!=nowDisconnected) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            if(nowDisconnected)
                fragment = new ConfigFragment();
            else
                fragment = new StatusFragment();
            fragmentTransaction.replace(R.id.fragment, fragment);
            fragmentTransaction.commit();
            disconnected = nowDisconnected;
        }
    }
}
