package org.discoos.p2p.internal;

import android.os.Handler;
import android.os.Looper;

import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.annotation.BusSignalHandler;
import org.discoos.p2p.P2P;
import org.discoos.p2p.P2PUtils;
import org.discoos.p2p.PeerInfo;

import java.util.Arrays;

/**
 * This base class implements P2PNetworkObject interface
 */
public class P2PNetworkObjectImpl implements P2PNetworkObject, BusObject {

    /**
     * Network name
     */
    private final String mName;

    /**
     * Network path
     */
    private final String mPath;

    /**
     * Signal handler
     */
    private Handler mHandler;

    /**
     * Constructor
     * @param name Network name
     * @param path Network path
     * @param name Handler instance
     */
    public P2PNetworkObjectImpl(String name, String path, Handler handler) {
        assert Looper.getMainLooper() != handler.getLooper() : "Handler looper is main looper";
        mName = name;
        mPath = path;
        mHandler = handler;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public String getPath() {
        return mPath;
    }

    /**
     * Implements signal handler for signal {@link #alive(byte[], String)}
     * @param appId Application unique id
     * @param uniqueName Peer bus unique name
     * @throws BusException
     * @see P2PUtils#toShortId(byte[])
     */
    @BusSignalHandler(iface = NAME, signal = "alive")
    public final void alive(byte[] appId, String uniqueName) throws BusException {
        if(!Arrays.equals(P2PAboutData.getAppId(), appId)) {
            String id = P2PUtils.toShortId(appId);
            PeerInfo info = P2P.getPeer(id);
            raise(P2P.ALIVE, info.alive(uniqueName));
        }
    }

    /**
     * Raise signal
     * @param signal Signal id
     * @param value Signal value
     */
    final void raise(int signal, Object value) {
        if(mHandler != null) {
            P2PUtils.raise(signal, mHandler, value);
        }
    }
}
