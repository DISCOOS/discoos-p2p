package org.discoos.p2p.internal;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.Status;
import org.discoos.p2p.P2P;
import org.discoos.p2p.P2PUtils;
import org.discoos.p2p.internal.PeerInfoCache.PeerInfoImpl;

/**
 * Implements P2PNetworkEndpoint interface.
 * TODO: Describe usage. Requirement is all handler methods are invoked on background thread.
 */
final class P2PNetworkEndpointImpl extends P2PEndpointBase implements P2PNetworkEndpoint {

    private static final String TAG = "P2PNetworkEndpoint";

    /**
     * Network name
     */
    final String mName;

    /**
     * Network port
     */
    final short mPort;

    /**
     * Bus attachment
     */
    private BusAttachment mBus;

    /**
     * Constructor
     * @param name Network name
     * @param port Network port
     * @param handler Handler instance
     */
    P2PNetworkEndpointImpl(String name, short port, Handler handler) {
        super("/".concat(name.replaceAll("\\.","/")), handler);
        assert Looper.getMainLooper() == handler.getLooper() :
                String.format("Not main looper: %s",handler.getLooper());
        mName = name;
        mPort = port;
    }

    /**
     * Check if this network endpoint has joined the bus
     * @return boolean
     */
    boolean isJoined() {
        return mBus != null;
    }

    /**
     * Join network. Must be called from background thread.
     * @param bus Bus attachment
     */
    boolean onJoin(BusAttachment bus) {

        debugNotMainThread("onJoin()");

        if(isJoined()) {
            String msg = "Already connected to bus %s";
            warning(String.format(msg, mBus.getUniqueName()));
            return false;
        }
        mBus = bus;

        /* Bind peer to point-to-point session for this network */
        if (!onBind()) return false;

        /* Register network bus object and listen for signals */
        return onRegister();

    }

    private boolean onBind() {

        debug("onBind()");

        SessionOpts sessionOpts = createSessionOpts();
        Mutable.ShortValue port = new Mutable.ShortValue(mPort);
        SessionPortListener listener = new SessionPortListener() {
            /**
             * Accept or reject an incoming JoinSession request.  The session does not exist until
             * this after this function returns.
             *
             * @param sessionPort Session port that was joined.
             * @param joiner Unique name of potential joiner.
             * @param sessionOpts Session options requested by the joiner
             * @return boolean
             */
            public boolean acceptSessionJoiner(short sessionPort, String joiner, SessionOpts sessionOpts) {
                return sessionPort == mPort;
            }
        };

        /**
         * Make a SessionPort available for external BusAttachments to join. Each BusAttachment
         * binds its own set of SessionPorts. Session joiners use the bound session port along with
         * the name of the attachment to create a persistent logical connection (called a Session)
         * with the original BusAttachment. A SessionPort and bus name form a unique identifier that
         * BusAttachments use when joining a session.
         */
        Status status = mBus.bindSessionPort(port, sessionOpts, listener);
        if (status != Status.OK) {
            error(String.format("Failed to bind to session port=%s, status=%s", port, status));
            return false;
        }

        return true;
    }

    @NonNull
    private SessionOpts createSessionOpts() {
        SessionOpts sessionOpts = new SessionOpts();
        sessionOpts.traffic = SessionOpts.TRAFFIC_MESSAGES;
        sessionOpts.isMultipoint = false;
        sessionOpts.proximity = SessionOpts.PROXIMITY_ANY;
        sessionOpts.transports = SessionOpts.TRANSPORT_IP;
        return sessionOpts;
    }

    /**
     * This method registers network bus object listeners and handlers of session-less signals
     * @return boolean
     */
    private boolean onRegister() {

        debug("onRegister()");

        Status status = mBus.registerBusObject(this, mPath);
        if (status != Status.OK) {
            String msg = "Failed to register bus object, status: %s:%s";
            error(String.format(msg, status.name(), status.getErrorCode()));
            return false;
        }

        return true;
    }

    /**
     * Leave network. Must be called from background thread.
     * @return boolean
     */
    boolean onLeave() {

        debugNotMainThread("onLeave()");

        if (!isJoined()) {
            error("onLeave(): Bus not connected to any network");
            return false;
        }

        mBus.unregisterBusObject(this);
        mBus.unbindSessionPort(mPort);

        /** Notify listeners that this bus ("me") has left the network */
        String id = P2PUtils.toShortId((P2PAboutData.getAppId()));
        PeerInfoImpl info = (PeerInfoImpl) P2P.getPeer(id);
        P2PUtils.raise(P2P.LEFT, mHandler, info.remove(mName));

        /** Cleanup */
        mBus = null;

        return true;
    }

    private void debug(String msg) {
        Log.d(TAG, String.format("[name=%s, path=%s] %s", mName, mPath, msg));
        P2PUtils.raise(P2P.ERROR, mHandler);
    }

    private void info(String msg) {
        Log.i(TAG, String.format("[name=%s, path=%s] %s", mName, mPath, msg));
        P2PUtils.raise(P2P.ERROR, mHandler);
    }

    private void error(String msg) {
        Log.e(TAG, String.format("[name=%s, path=%s] %s", mName, mPath, msg));
        P2PUtils.raise(P2P.ERROR, mHandler);
    }

    private void warning(String msg) {
        Log.w(TAG, String.format("[name=%s, path=%s] %s", mName, mPath, msg));
        P2PUtils.raise(P2P.WARNING, mHandler);
    }

    private void exception(String msg, Exception e) {
        Log.e(TAG, String.format("[name=%s, path=%s] %s", mName, mPath, msg), e);
        P2PUtils.raise(P2P.ERROR, mHandler);
    }

    private void debugNotMainThread(String method) {
        assert Looper.getMainLooper() != Looper.myLooper() :
                String.format("Method %s can not be invoked on main thread", method);
        debug(method);
    }

}
