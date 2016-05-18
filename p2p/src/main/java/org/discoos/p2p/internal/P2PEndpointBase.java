package org.discoos.p2p.internal;

import android.os.Handler;

import org.discoos.p2p.P2PUtils;

/**
 * Created by kengu on 10.05.16.
 */
public class P2PEndpointBase {

    /**
     * BusObject path
     */
    final String mPath;


    /**
     * Signal handler
     */
    final Handler mHandler;


    public P2PEndpointBase(String path, Handler handler) {
        mPath = path;
        mHandler = handler;
    }

    /**
     * Raise signal
     * @param signal Signal id
     * @param value Signal value
     */
    protected void raise(int signal, Object value) {
        if(mHandler != null) {
            P2PUtils.raise(signal, mHandler, value);
        }
    }


}
