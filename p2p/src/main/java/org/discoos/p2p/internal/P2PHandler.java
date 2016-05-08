/*
 * Copyright DISCO Open Source. All rights reserved
 *
 *    Redistribution and use in source and binary forms, with or without
 *    modification, are permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this
 *       list of conditions and the following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice,
 *       this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *
 *    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *    ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *    WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *    DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 *    ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *    (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *    LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *    ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *    (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *    The views and conclusions contained in the software and documentation are those
 *    of the authors and should not be interpreted as representing official policies,
 *    either expressed or implied, of DISCO Open Source.
 */
package org.discoos.p2p.internal;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;

import org.alljoyn.bus.AboutListener;
import org.alljoyn.bus.AboutObj;
import org.alljoyn.bus.AboutObjectDescription;
import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.MessageContext;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.OnPingListener;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.SignalEmitter;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.Variant;
import org.discoos.p2p.P2P;
import org.discoos.p2p.P2PUtils;
import org.discoos.p2p.PeerInfo;
import org.discoos.signal.Dispatcher;
import org.discoos.signal.Observable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.discoos.p2p.internal.P2PNetworkObject.ALIVE_RULE;

/**
 * <p>
 * Class which handles messaging between components running in main thread of
 * P2PApplication hosting process and P2PService which runs in a separate thread.
 * </p>
 *
 * <p>
 * It uses the Android Service message handler internally to translate UI-related events
 * into P2P events. The message handler runs in the context of the main Android Service thread,
 * which is also shared with Activities since Android is a fundamentally a single-threaded system.
 * This thread cannot be blocked for a significant amount of time or we risk the dreaded
 * "force close" message. We can run relatively short-lived operations here, but we need to run our
 * distributed system calls in another thread.
 * </p>
 *
 * <p>
 * <b>Note</b> that only signaling is handled by this class. Any exchange of data between
 * components in main thread and P2PService in the service thread must be protected by keyword
 * <em>synchronized</em> to prevent data corruption. Preventing deadlocks due to concurrent
 * invocations of methods, or critical sections protected by <em>synchronized</em>, from components
 * in the main thread and from P2PService in the service thread, must be handled outside this class
 * which uses the global message dispatching thread in Android to deliver signals to and from the
 * P2PService.
 * </p>
 */
public final class P2PHandler implements Observable {

    private static final String TAG = "P2PHandler";

    /*
     * Load the native alljoyn_java library.  The actual AllJoyn code is
     * written in C++ and the alljoyn_java library provides the language
     * bindings from Java to C++ and vice versa.
     */
    static {
        Log.i(TAG, "System.loadLibrary(\"alljoyn_java\")");
        System.loadLibrary("alljoyn_java");

    }

    /**
     * P2P network name
     */
    private final String mName;

    /**
     * P2P network path
     */
    private final String mPath;

    /**
     * P2P network contact port
     */
    private final short mPort;

    /**
     * Global signal dispatcher
     */
    private final Dispatcher mDispatcher;

    /**
     * Handler of inbound handles (FROM main thread)
     */
    private final Handler mInboundHandler;

    /**
     * Handler of outbound handles (TO main thread)
     */
    private final Handler mOutboundHandler;

    /**
     * Inbound handles
     */
    private final List<P2PHandle> mInbound = new ArrayList<>();

    /**
     * Outbound handles
     */
    private final List<P2PHandle> mOutbound = new ArrayList<>();

    /**
     * Cache of {@link BusObject}s registered with this handler.
     */
    private final Map<BusObject, String> mBusObjectMap = new HashMap<>();

//    /**
//     * Cache of {@link BusObject}s registered with all handlers. Is used to assert single-use.
//     */
//    private static final Map<Class<? extends BusObject>, String> mBusObjectAllMap = new HashMap<>();

    /**
     * P2PHandler constructor.
     * @param name P2P network bus name.
     * @param port  P2P network bus port.
     */
    public P2PHandler(String name, short port) {

        /**
         * Configure P2P network
         */
        mName = name;
        mPort = port;
        mPath = "/".concat(name.replaceAll("\\.","/"));
        mDispatcher = P2P.getDispatcher();

        /**
         * Start background thread to handle long-lived remote P2P operations
         */
        String thread = String.format("P2PHandler::%s", name);
        HandlerThread busThread = new HandlerThread(thread);
        busThread.start();

        /**
         * Handle inbound messages on bus thread
         */
        mInboundHandler = createInboundHandler(busThread.getLooper());

        /**
         * Handle inbound messages on main thread
         */
        mOutboundHandler = createOutboundHandler();

    }


    /**
     * Create inbound signal handler.
     * @param looper Handler looper
     * @return Handler
     */
    private Handler createInboundHandler(Looper looper) {

        // Handle following inbound messages
        this.in(new P2PHandle(P2P.JOIN) {
            public boolean execute(Message msg) {
                return onJoin();
            }
        }).in(new P2PHandle(P2P.BROADCAST) {
            public boolean execute(Message msg) {
                return onBroadcast();
            }
        }).in(new P2PHandle(P2P.CANCEL) {
            public boolean execute(Message msg) {
                return onCancel();
            }
        }).in(new P2PHandle(P2P.PING) {
            public boolean execute(Message msg) {
                return onPing(msg);
            }
        }).in(new P2PHandle(P2P.LEAVE) {
            public boolean execute(Message msg) {
                return onLeave();
            }
        });

        /**
         * Ensure inbound messages are not handled on main thread
         */
        return new Handler(looper, new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                handle(msg, mInbound);
                return true;
            }
        });

    }

    /**
     * Create outbound signal handler.
     * @return Handler
     */
    private Handler createOutboundHandler() {

        // Handle following outbound messages
        this.out(new P2PHandle(P2P.JOINED) {
            public boolean execute(Message msg) {
                mDispatcher.raise(P2P.JOINED, msg.obj);
                return false;
            }
        }).out(new P2PHandle(P2P.TIMEOUT) {
            public boolean execute(Message msg) {
                mDispatcher.raise(P2P.TIMEOUT, msg.obj);
                return false;
            }
        }).out(new P2PHandle(P2P.ALIVE) {
            public boolean execute(Message msg) {
                mDispatcher.raise(P2P.ALIVE, msg.obj);
                return false;
            }
        });

        /**
         * Ensure outbound messages are invoked on main looper thread
         */
        return new Handler(Looper.getMainLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                handle(msg, mOutbound);
                return true;
            }
        });
    }

    /**
     * Register {@link BusObject} with network
     * @param object BusObject instance
     * @param path BusObject path
     * @throws IllegalArgumentException
     */
    public void registerBusObject(BusObject object, String path) throws IllegalStateException {
        if(mBus.isConnected()) {
            throw new IllegalStateException("Peer has joined the network");
        }
//        if(mBusObjectAllMap.containsKey(object)) {
//            String msg = "BusObject %s registered with network %s.";
//            msg = String.format(msg, object, mBusObjectAllMap.get(object));
//            throw new IllegalStateException(msg);
//        }
//        if(mBusObjectMap.containsKey(object)) {
//            String msg = "BusObject %s already registered with path %s";
//            msg = String.format(msg, object, mBusObjectMap.get(object));
//            throw new IllegalStateException(msg);
//        }
        mBusObjectMap.put(object, path);
//        mBusObjectAllMap.put(object, mName);
    }

    public void unregisterBusObject(BusObject object) throws IllegalStateException {
        if(mBus.isConnected()) {
            throw new IllegalStateException("Peer has joined the network");
        }
//        if(!mBusObjectMap.containsKey(object)) {
//            String msg = "BusObject %s not registered";
//            msg = String.format(msg, object);
//            throw new IllegalStateException(msg);
//        }
        mBusObjectMap.remove(object);
//        mBusObjectAllMap.remove(object);
    }

    /* Join P2P proximity network */
    public boolean join() { return raise(P2P.JOIN, mInboundHandler); }

    /**
     * Implements joining network. Called from background thread
     * @return boolean
     */
    private boolean onJoin() {

        /* Connect BusAttachment to bundled router */
        if (!onConnect()) return false;

        /* Bind to multipoint session on given port */
        if (!onBind()) return false;

        /* Announce bus objects */
        if (!onAnnounce()) return false;

        /* Listen for bus events */
        return onListen();

    }

    private BusAttachment mBus;

    private boolean onConnect() {

        if (mBus != null) {
            String msg = "Bus already connected to network %s@%s";
            error(String.format(msg, mName, mPort));
            return false;
        }

        mBus = new BusAttachment(mName, BusAttachment.RemoteMessage.Receive);

        mBus.useOSLogging(true);
        mBus.setDebugLevel("ALLJOYN_JAVA", 7);

        Status status = mBus.connect();
        if (status != Status.OK) {
            mBus = null;
            error("Failed to connect, status: " + status.name());
        }

        return mBus != null;
    }

    @NonNull
    private SessionOpts createSessionOpts() {
        SessionOpts sessionOpts = new SessionOpts();
        sessionOpts.traffic = SessionOpts.TRAFFIC_MESSAGES;
        sessionOpts.isMultipoint = true;
        sessionOpts.proximity = SessionOpts.PROXIMITY_ANY;
        sessionOpts.transports = SessionOpts.TRANSPORT_IP;
        return sessionOpts;
    }

    private boolean onBind() {

        Status status;

        SessionOpts sessionOpts = createSessionOpts();
        Mutable.ShortValue port = new Mutable.ShortValue(mPort);
        SessionPortListener listener = new SessionPortListener() {
            public boolean acceptSessionJoiner(short sessionPort, String joiner, SessionOpts sessionOpts) {
                return sessionPort == mPort;
            }
        };

        status = mBus.bindSessionPort(port, sessionOpts, listener);
        if (status != Status.OK) {
            error(String.format("Failed to bind to session port=%s, status=%s", port, status));
            return false;
        }

        return true;
    }

    private AboutObj mAboutObj;
    private P2PNetworkObjectImpl mNetwork;

    private boolean onAnnounce() {

        Status status;

        /**
         * Create and register network bus object.
         */
        mNetwork = new P2PNetworkObjectImpl(mName, mPath, mOutboundHandler);
        if(!onRegisterBusObject(mNetwork, mPath)) {
            return false;
        }

        /**
         * Register custom bus objects
         */
        for(BusObject it : mBusObjectMap.keySet()) {
            onRegisterBusObject(it, mBusObjectMap.get(it));
        }

        /**
         * Announce registered bus objects to peers in same multipoint session
         */
        mAboutObj = new AboutObj(mBus);
        P2PAboutData data = new P2PAboutData(mBus);
        status = mAboutObj.announce(mPort, data);
        if (status != Status.OK) {
            String msg = "Failed to announce endpoint %s, status: %s";
            error(String.format(msg, mNetwork, status));
            return false;
        }

        return true;

    }

    private boolean onRegisterBusObject(BusObject object, String path) {
        Status status = mBus.registerBusObject(object, path);
        if (status != Status.OK) {
            String msg = "Failed to register bus object %s, status: %s";
            error(String.format(msg, object, status));
            return false;
        }
        return true;
    }

    private BusListener mBusListener;
    private AboutListener mAboutListener;

    private boolean onListen() {

        /* Listen for announcements */
        mAboutListener = new AboutListener() {
            @Override
            public void announced(String bus, int version, short port, AboutObjectDescription[] descriptions, Map<String, Variant> data) {
                P2PUtils.raise(P2P.JOINED, mOutboundHandler, P2PNetworkImpl.createPeerInfo(mName, data));
            }
        };
        mBus.registerAboutListener(mAboutListener);

        /* Listen for bus events */
        mBusListener = new BusListener() {
            @Override
            public void foundAdvertisedName(String name, short transport, String namePrefix) {
                Log.i(TAG, "foundAdvertisedName(" + name + ". " + transport + ", " + namePrefix + ")");
            }

            @Override
            public void nameOwnerChanged(String nameOwnerChanged, String previousOwner, String newOwner) {
                Log.i(TAG, "nameOwnerChanged(" + nameOwnerChanged + ". " + previousOwner + ", " + newOwner + ")");
            }

            @Override
            public void lostAdvertisedName(String name, short transport, String namePrefix) {
                Log.i(TAG, "lostAdvertisedName(" + name + ". " + transport + ", " + namePrefix + ")");
            }

            @Override
            public void busStopping() {
                Log.i(TAG, "busStopping()");
            }
        };
        mBus.registerBusListener(mBusListener);

        /* Listen for P2PNetworkObject session-less signals */
        Status status = mBus.registerSignalHandlers(mNetwork);
        if (status != Status.OK) {
            error("Failed to register signal handlers, status: " + status.name());
            return false;
        }

        /* Only receive session-less signal 'alive' from peers on same path as this network endpoint */
        status = mBus.addMatch(String.format(ALIVE_RULE, mPath));
        if (status != Status.OK) {
            error(String.format("AddMatch [%s] failed, status: %s", ALIVE_RULE, status));
            return false;
        }

        /* Discover peers on network */
        status = mBus.whoImplements(new String[]{P2PNetworkObject.NAME});
        if (status != Status.OK) {
            error("Failed to discover peers on network, status: " + status.name());
            return false;
        }

        return true;
    }

    /* Leave P2P network */
    public boolean leave() { return raise(P2P.LEAVE, mInboundHandler); }

    /**
     * Implements leaving P2P network. Called from background thread
     * @return boolean
     */
    private boolean onLeave() {

        Log.i(TAG, "onLeave()");

        // Connected?
        if (mBus != null) {
            mAboutObj.unannounce();
            mBus.unregisterBusObject(mNetwork);
            for (BusObject it : mBusObjectMap.keySet()) {
                mBus.unregisterBusObject(it);
            }
            mBus.unregisterBusListener(mBusListener);
            mBus.unregisterSignalHandlers(mNetwork);
            mBus.unregisterAboutListener(mAboutListener);
            mBus.unbindSessionPort(mPort);
            mBus.disconnect();
        } else {
            error("Bus not connected to any network");
            return false;
        }

        /** Cleanup */
        mBus.release();
        mBus = null;
        mAboutObj = null;
        mNetwork = null;
        mBusListener = null;
        mAboutListener = null;

        return true;
    }

    /* Ping peer */
    public boolean ping(PeerInfo info, int timeout) {
        return P2PUtils.raise(P2P.PING, mInboundHandler, new Object[]{info, timeout});
    }

    /**
     * Implements P2P peer ping. Called from background thread
     * @return boolean
     */
    private boolean onPing(Message msg) {

        PeerInfo info = (PeerInfo)((Object[])msg.obj)[0];
        int timeout = (int)((Object[])msg.obj)[1];

        Log.i(TAG, "onPing(" + info.getName() + ")");

        /* Invoke asynchronous ping request */
        Status status = mBus.ping(info.getName(), timeout, new OnPingListener() {
            @Override
            public void onPing(Status status, Object observable) {
                PeerInfo info = (PeerInfo)observable;
                if (Status.OK == status) {
                    P2PUtils.raise(P2P.ALIVE, mOutboundHandler, info.alive());
                } else {
                    P2PUtils.raise(P2P.TIMEOUT, mOutboundHandler, info.timeout());
                }
            }
        }, info);

        return Status.OK == status;
    }

    /* Broadcast signal to all peers in network */
    public boolean broadcast() {
        return raise(P2P.BROADCAST, mInboundHandler);
    }

    private SignalEmitter mSignalEmitter;
    private List<MessageContext> mMessageContextList = new ArrayList<>();

    private boolean onBroadcast()  {

        if(mSignalEmitter == null) {
            /* Create an emitter to emit a sessionless signal with the desired message.
             * The session ID is set to zero and the session-less flag is set to true.
             * Broadcast signals will not be forwarded across bus-to-bus connections (default). */
            mSignalEmitter = new SignalEmitter(mNetwork, 0, SignalEmitter.GlobalBroadcast.Off);
            mSignalEmitter.setSessionlessFlag(true);
        }

        /* Get the P2PNetworkObject for the emitter */
        P2PNetworkObject broadcast = mSignalEmitter.getInterface(P2PNetworkObject.class);

        try {
            Log.i(TAG, "onBroadcast: " + mBus.getUniqueName());
            broadcast.alive(P2PAboutData.getAppId(), mBus.getUniqueName());
            mMessageContextList.add(mSignalEmitter.getMessageContext());
        } catch (BusException e) {
            exception("Failed to signal P2PNetworkObject.alive()", e);
            return false;
        }

        return true;

    }

    /* Cancel broadcast signal sent to all peers in network */
    public boolean cancel() {
        return raise(P2P.CANCEL, mInboundHandler);
    }

    private boolean onCancel()  {

        if(mSignalEmitter == null) {
            return false;
        }

        for(MessageContext it : mMessageContextList) {
            Status status = mSignalEmitter.cancelSessionlessSignal(it.serial);
            if(Status.OK != status) {
                warning(String.format("Failed to cancel signal %s, status: %s",
                        it.memberName, status));
            }
        }

        return true;

    }

    /* Quit inbound message handler thread */
    public void quit() {
        mInboundHandler.getLooper().quitSafely();
    }

    /* Send signal as message to handler thread */
    static boolean raise(int signal, Handler handler) {
        return P2PUtils.raise(signal, handler, null);
    }

    private void handle(Message msg, List<P2PHandle> tasks) {
        for (P2PHandle task: tasks) {
            try {
                if(task.accepts(msg.what)) {
                    // Stop processing signal?
                    if(!task.execute(msg)) {
                        break;
                    }
                }
            }
            catch (Exception e) {
                exception("handleMessage(" + msg + ") failed", e);
            }
        }
    }


    private void error(String msg) {
        Log.e(TAG, msg);
        raise(P2P.ERROR, mOutboundHandler);
    }

    private void warning(String msg) {
        Log.w(TAG, msg);
        raise(P2P.WARNING, mOutboundHandler);
    }

    private void exception(String msg, Exception e) {
        Log.e(TAG, msg , e);
        raise(P2P.ERROR, mOutboundHandler);
    }

    /**
     * Add inbound messages task
     * @param task Inbound task
     * @return P2PHandler
     */
    private P2PHandler in(P2PHandle task) {
        mInbound.add(task);
        return this;
    }

    /**
     * Add outbound messages task
     * @param task Outbound task
     * @return P2PHandler
     */
    private P2PHandler out(P2PHandle task) {
        mOutbound.add(task);
        return this;
    }

}

