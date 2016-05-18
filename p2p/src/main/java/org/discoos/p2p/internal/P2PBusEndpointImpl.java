package org.discoos.p2p.internal;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.annotation.BusSignalHandler;
import org.discoos.p2p.P2P;
import org.discoos.p2p.P2PUtils;
import org.discoos.p2p.internal.PeerInfoCache.PeerInfoImpl;

import java.util.Arrays;

/**
 * Implement signal handlers for signals in {@link P2PBusEndpoint}
 */
public class P2PBusEndpointImpl extends P2PEndpointBase implements P2PBusEndpoint {

    private static final String TAG = "P2PBusEndpoint";

    public static final String PATH = "/";

    /**
     * Bus attachment
     */
    private BusAttachment mBus;

    /**
     * Constructor
     * @param handler Handler instance
     */
    public P2PBusEndpointImpl(Handler handler) {
        super(PATH, handler);
    }

    /**
     * Check if this network endpoint has joined the bus
     * @return
     */
    boolean isRegistered() {
        return mBus != null;
    }


    /**
     * Register endpoint on bus
     * @param bus
     * @return
     */
    boolean onRegister(BusAttachment bus) {

        debugNotMainThread("onRegister()");

        if(isRegistered()) {
            String msg = "Already registered on bus %s";
            warning(String.format(msg, mBus.getUniqueName()));
            return false;
        }
        mBus = bus;

        Status status = mBus.registerBusObject(this, mPath);
        if (status != Status.OK) {
            String msg = "Failed to register endpoint, status: %s";
            error(String.format(msg, this, status));
            return false;
        }

        /* Handle session-less signals */
        status = mBus.registerSignalHandlers(this);
        if (status != Status.OK) {
            String msg = "Failed to register signal handlers, status: %s";
            error(String.format(msg, this, status));
            return false;
        }

        /* Add signal match rules */
        status = mBus.addMatch(ALIVE_RULE);
        if (status != Status.OK) {
            error(String.format("AddMatch [%s] failed, status: %s", ALIVE_RULE, status));
            return false;
        }
        status = mBus.addMatch(LEFT_RULE);
        if (status != Status.OK) {
            error(String.format("AddMatch [%s] failed, status: %s", LEFT_RULE, status));
            return false;
        }

        return true;
    }

    /**
     * Unregister endpoint on bus
     * @return
     */
    boolean onUnRegister() {

        debugNotMainThread("onUnregister()");

        if(!isRegistered()) {
            String msg = "Not registered on bus %s";
            warning(String.format(msg, mBus.getUniqueName()));
            return false;
        }

        mBus.unregisterBusObject(this);

        /* Handle session-less signals */
        mBus.unregisterSignalHandlers(this);

        /* Remove signal match rules */
        Status status = mBus.removeMatch(ALIVE_RULE);
        if (status != Status.OK) {
            error(String.format("RemoveMatch [%s] failed, status: %s", ALIVE_RULE, status));
            return false;
        }
        status = mBus.removeMatch(LEFT_RULE);
        if (status != Status.OK) {
            error(String.format("RemoveMatch  [%s] failed, status: %s", LEFT_RULE, status));
            return false;
        }

        mBus = null;

        return true;
    }

    /**
     * Implements handler for signal {@link #alive(byte[], String)}
     * @param appId Application unique id
     * @param uniqueName Peer bus unique name
     * @throws BusException
     * @see P2PUtils#toShortId(byte[])
     */
    @BusSignalHandler(iface = NAME, signal = "alive")
    public final void alive(byte[] appId, String uniqueName) throws BusException {
        if(!Arrays.equals(P2PAboutData.getAppId(), appId)) {
            String id = P2PUtils.toShortId(appId);
            PeerInfoImpl info = (PeerInfoImpl) P2P.getPeer(id);
            if(info != null) {
                raise(P2P.ALIVE, info.alive(uniqueName));
            }
        }
    }

    /**
     * Implements handler for signal {@link #left(byte[], String...)}
     * @param appId Peer's application instance id
     * @param networks Peer's networks
     * @throws BusException
     * @see P2PUtils#toShortId(byte[])
     */
    @BusSignalHandler(iface = NAME, signal = "left")
    public void left(byte[] appId, String... networks) throws BusException {
        if(!Arrays.equals(P2PAboutData.getAppId(), appId)) {
            String id = P2PUtils.toShortId(appId);
            PeerInfoImpl info = (PeerInfoImpl) P2P.getPeer(id);
            if(info != null) {
                raise(P2P.LEFT, info.remove(networks));
            }
        }
    }

    private void debug(String msg) {
        Log.d(TAG, String.format("[path=%s] %s", mPath, msg));
        P2PUtils.raise(P2P.ERROR, mHandler);
    }

    private void info(String msg) {
        Log.i(TAG, String.format("[path=%s] %s", mPath, msg));
        P2PUtils.raise(P2P.ERROR, mHandler);
    }

    private void error(String msg) {
        Log.e(TAG, String.format("[path=%s] %s", mPath, msg));
        P2PUtils.raise(P2P.ERROR, mHandler);
    }

    private void warning(String msg) {
        Log.w(TAG, String.format("[path=%s] %s", mPath, msg));
        P2PUtils.raise(P2P.WARNING, mHandler);
    }

    private void exception(String msg, Exception e) {
        Log.e(TAG, String.format("[path=%s] %s", mPath, msg));
        P2PUtils.raise(P2P.ERROR, mHandler);
    }

    private void debugNotMainThread(String method) {
        assert Looper.getMainLooper() != Looper.myLooper() :
                String.format("Method %s can not be invoked on main thread", method);
        debug(method);
    }
}
