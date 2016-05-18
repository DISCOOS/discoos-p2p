package org.discoos.p2p.internal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.discoos.p2p.P2P;
import org.discoos.p2p.P2PNetwork;
import org.discoos.p2p.P2PUtils;
import org.discoos.signal.Event;

/**
 * This class listen for changes in connectivity and
 * notifies peers each time the device connects to WIFI.
 */
public class ConnectivityChangeReceiver extends BroadcastReceiver {

    private static final String TAG = "Connectivity";

    /** Prevent firing when connecting the first time (new instance for each broadcast) */
    private static boolean mInit;

    /** Used when checking if state changed to WIFI (new instance for each broadcast) */
    private static P2PNetwork.Connectivity mState = P2PUtils.getConnectivityStatus(P2P.getApplication());

    @Override
    public synchronized void onReceive(final Context context, final Intent intent) {
        try {
            P2PNetwork.Connectivity state = P2PUtils.getConnectivityStatus(context);

            // Only after initial notification and if state has changed
            if(mInit && !mState.equals(state)) {

                boolean isFailOver = intent.getBooleanExtra("FAILOVER_CONNECTION", false);
                boolean isConnection = !intent.getBooleanExtra("EXTRA_NO_CONNECTIVITY", false);
                String msg = "onReceive(%s->%s,failover=%s,connection=%s)";
                Log.i(TAG, String.format(msg, mState, state, isFailOver, isConnection));

                switch (state) {
                    case WIFI:
                        P2P.getContext().broadcast(new Event(P2P.ALIVE, this));
                        break;
                    case NONE:
                    case MOBILE:
                        P2P.getContext().cancel(new Event(P2P.CANCEL, this, P2P.ALIVE));
                        break;
                }
            }
            mInit = true;
            mState = state;
        }
        catch (Exception e) {
            Log.e(TAG, "onReceive()", e);
        }
    }
}
