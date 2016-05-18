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
import android.util.Log;

import org.alljoyn.bus.AboutListener;
import org.alljoyn.bus.AboutObj;
import org.alljoyn.bus.AboutObjectDescription;
import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.MessageContext;
import org.alljoyn.bus.OnPingListener;
import org.alljoyn.bus.SignalEmitter;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.Variant;
import org.discoos.p2p.P2P;
import org.discoos.p2p.P2PUtils;
import org.discoos.p2p.PeerInfo;
import org.discoos.p2p.internal.PeerInfoCache.PeerInfoImpl;
import org.discoos.signal.Dispatcher;
import org.discoos.signal.Event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
final class P2PHandler implements AboutListener {

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
     * P2P application name
     */
    private final String mAppName;

    /**
     * List of registered network endpoints.
     */
    private Map<String, P2PNetworkEndpointImpl> mNetworkEndpointMap = new HashMap<>();

    /**
     * Global signal dispatcher
     */
    private final Dispatcher mDispatcher;

    /**
     * Handler of inbound messages (main thread -> handler thread)
     */
    private final Handler mInboundHandler;

    /**
     * Handler of outbound messages (handler thread -> main thread)
     */
    private final Handler mOutboundHandler;

    /**
     * Inbound message handles
     */
    private final List<P2PHandle> mInbound = new ArrayList<>();

    /**
     * Outbound message handles
     */
    private final List<P2PHandle> mOutbound = new ArrayList<>();

    /**
     * Bus attachment instance
     */
    private BusAttachment mBus;

    /**
     * Bus endpoint instance
     */
    private P2PBusEndpointImpl mBusEndpoint;

//    /**
//     * About object registered with bus attachment by {@link #onInit()}
//     */
//    private AboutObj mAboutObj;
//
//
    /**
     * P2PHandler constructor.
     */
    P2PHandler() {

        /**
         * Configure P2P network
         */
        mAppName = P2P.getContext().getName();
        mDispatcher = P2P.getDispatcher();

        /**
         * Start background thread to handle long-lived remote P2P operations
         */
        String thread = String.format("P2PHandler::%s", mAppName);
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
     * Check if handler is connected to P2P network
     * @return boolean
     */
    boolean isConnected() {
        return mBus != null;
    }

    /**
     * Create inbound signal handler.
     * @param looper Handler looper
     * @return Handler
     */
    private Handler createInboundHandler(Looper looper) {

        // Handle following inbound messages
        this.in(new P2PHandle(P2P.INIT) {
            public boolean execute(Message msg) {
                return onInit();
            }
        }).in(new P2PHandle(P2P.JOIN) {
            public boolean execute(Message msg) {
                NetworkInfo info = createNetworkInfo(msg.obj);
                return info != null && onJoin(info);
            }
        }).in(new P2PHandle(P2P.BROADCAST) {
            public boolean execute(Message msg) {
                return onBroadcast((Event)msg.obj);
            }
        }).in(new P2PHandle(P2P.CANCEL) {
            public boolean execute(Message msg) {
                return onCancel((Event)msg.obj);
            }
        }).in(new P2PHandle(P2P.PING) {
            public boolean execute(Message msg) {
                return onPing(msg);
            }
        }).in(new P2PHandle(P2P.LEAVE) {
            public boolean execute(Message msg) {
                return onLeave(String.valueOf(msg.obj));
            }
        }).in(new P2PHandle(P2P.QUIT) {
            public boolean execute(Message msg) {
                return onQuit();
            }
        });

        /**
         * Dispatch inbound signals to inbound handler
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
        this.out(new P2PHandle(P2P.ANNOUNCED) {
            public boolean execute(Message msg) {
                mDispatcher.raise(P2P.ANNOUNCED, msg.obj);
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
        }).out(new P2PHandle(P2P.LEFT) {
            public boolean execute(Message msg) {
                mDispatcher.raise(P2P.LEFT, msg.obj);
                return false;
            }
        });

        /**
         * Dispatch outbound signals to outbound handler
         */
        return new Handler(Looper.getMainLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                handle(msg, mOutbound);
                return true;
            }
        });
    }

    /* Initialize message bus */
    boolean init() { return P2PUtils.raise(P2P.INIT, mInboundHandler); }

    /**
     * Implements initialization
     * @return boolean
     */
    private boolean onInit() {

        if (isConnected()) {
            String msg = "Handler %s already initialized";
            error(String.format(msg, mAppName));
            return false;
        }
        mBus = new BusAttachment(mAppName, BusAttachment.RemoteMessage.Receive);
        mAboutObj = new AboutObj(mBus);

        mBus.useOSLogging(true);
        mBus.setDebugLevel("ALLJOYN_JAVA", 7);

        Status status = mBus.connect();
        if (status != Status.OK) {
            mBus = null;
            error(String.format("Failed to connect, status: %s",status));
        }

        mAboutData = new P2PAboutData(mBus.getUniqueName());
        mBusEndpoint = new P2PBusEndpointImpl(mOutboundHandler);
        if(!mBusEndpoint.onRegister(mBus)) {
            error(String.format("Failed to register bus endpoint, status: %s", mBusEndpoint));
            return false;
        }

        return onDiscover();
    }

    /**
     * Discover new networks
     * @return boolean
     */
    private boolean onDiscover() {

        mBus.registerAboutListener(this);

        /* Start discovering network endpoints on bus */
        Status status = mBus.whoImplements(new String[]{P2PNetworkEndpoint.NAME});
        if (status != Status.OK) {
            error(String.format("Failed to discover peers, status: %s", status));
            return false;
        }
        return true;
    }


    /**
     * Listen for peers on this network.
     *
     * <b>IMPORTANT</b>: Invoked from bundled router thread, execute on inbound handler thread.
     * @param busName Well know name of the remote BusAttachment
     * @param version Version of the Announce signal from the remote About Object
     * @param port SessionPort used by the announcer
     * @param descriptions  A list of object paths and interfaces in the announcement
     * @param data A dictionary of key/value pairs of the AboutData
     */
    @Override
    public void announced(final String busName, final int version,
                          final short port,
                          final AboutObjectDescription[] descriptions,
                          final Map<String, Variant> data) {

        /** Ensure thread-safe access to members in P2PHandler */
        mInboundHandler.post(new Runnable() {
            @Override
            public void run() {

                /** Cache all peers heard on bus */
                for (AboutObjectDescription it : descriptions) {
                    if (P2PUtils.contains(it.interfaces, P2PNetworkEndpoint.NAME)) {
                        String name = it.path.substring(1).replaceAll("/", "\\.");
                        String msg = "ANNOUNCED: %s@%s%s";
                        Log.i(TAG, String.format(msg, P2PUtils.toShortId(data), name, busName));
                        PeerInfoImpl info = PeerInfoCache.getInstance().newInstance(data).add(name, port);
                        P2PUtils.raise(P2P.ANNOUNCED, mOutboundHandler, info);
                    }
                }
            }
        });

    }

    /**
     * Join network
     * @param name Network name
     * @return boolean
     */
    boolean join(String name) {
        return P2PUtils.raise(P2P.JOIN, mInboundHandler, name);
    }

    /**
     * About object registered with bus attachment in {@link #onInit()}
     */
    private AboutObj mAboutObj;

    /**
     * P2P AboutData for this application instance
     */
    private P2PAboutData mAboutData;

    /**
     * Handle join network
     * @param info NetworkInfo
     * @return boolean
     */
    private boolean onJoin(NetworkInfo info) {

        P2PNetworkEndpointImpl endpoint = mNetworkEndpointMap.get(info.name);

        if(endpoint == null) {
            endpoint = new P2PNetworkEndpointImpl(info.name, info.port, mOutboundHandler);
            mNetworkEndpointMap.put(info.name, endpoint);
        } else {
            String msg = "Already joined network %s on bus %s%s";
            warning(String.format(msg, info.name, mAppName, mBus.getUniqueName()));
            return false;
        }

        if(!endpoint.onJoin(mBus)) {
            return false;
        }

        Status status = mAboutObj.announce(info.port, mAboutData);
        if (status != Status.OK) {
            String msg = "Failed to announce network endpoint, status: %s, code: %s";
            error(String.format(msg, status.name(), status.getErrorCode()));
            return false;
        }
        return true;
    }

    /**
     * Leave P2P network
     * @param name Network name
     * @return boolean
     */
    public boolean leave(String name) {
        return P2PUtils.raise(P2P.LEAVE, mInboundHandler, name);
    }

    /**
     * Handle leave network on background thread.
     * @return boolean
     */
    private boolean onLeave(String name) {

        Log.i(TAG, String.format("onLeave(): %s", name));

        P2PNetworkEndpointImpl endpoint = mNetworkEndpointMap.get(name);
        if(endpoint == null) {
            String msg = "Network %s not connected to bus %s";
            warning(String.format(msg, name, mBus.getUniqueName()));
            return false;
        }
        if(!endpoint.onLeave()) {
            return false;
        }
        if(!onBroadcast(new Event(P2P.LEFT, this, new String[]{name}))) {
            String msg = "Failed to broadcast P2P.LEFT for network %s";
            warning(String.format(msg, name));
        }
        mNetworkEndpointMap.remove(name);
        return true;
    }

    /* Ping peer */
    public boolean ping(PeerInfo info, int timeout) {
        return P2PUtils.raise(P2P.PING, mInboundHandler, new Object[]{info, timeout});
    }

    /**
     * Handle peer ping on background thread.
     * @return boolean
     */
    private boolean onPing(Message msg) {

        if(!isReady("onPing")) {
            return false;
        }

        PeerInfo info = (PeerInfo)((Object[])msg.obj)[0];
        int timeout = (int)((Object[])msg.obj)[1];

        Log.i(TAG, String.format("onPing(): %s", info.getName()));

        /* Invoke asynchronous ping request */
        Status status = mBus.ping(info.getName(), timeout, new OnPingListener() {
            @Override
            public void onPing(final Status status, final Object observable) {

                /** Consume multiple ping requests */
                if(Status.ALLJOYN_PING_REPLY_IN_PROGRESS != status) {

                    /**
                     * Response is invoked on router thread.
                     * Since access to cached peers is thread-safe,
                     * there no need for post on inbound handler */

                    PeerInfoImpl info = (PeerInfoImpl) observable;
                    if (Status.OK == status) {
                        P2PUtils.raise(P2P.ALIVE, mOutboundHandler, info.alive());
                    } else {
                        P2PUtils.raise(P2P.TIMEOUT, mOutboundHandler, info.timeout());
                    }

                }
            }
        }, info);

        return Status.OK == status;
    }

    /* Broadcast signal to all peers in network */
    boolean broadcast(Event event) {
        return P2PUtils.raise(P2P.BROADCAST, mInboundHandler, event);
    }

    private SignalEmitter mSignalEmitter;
    private Map<Object, MessageContext> mMessageContextMap = new HashMap<>();

    private boolean onBroadcast(Event event)  {

        if(!isReady("onBroadcast")) {
            return false;
        }

        if(mSignalEmitter == null) {
            /* Create an emitter to emit a sessionless signal with the desired message.
             * The session ID is set to zero and the session-less flag is set to true.
             * Broadcast signals will not be forwarded across bus-to-bus connections (default). */
            mSignalEmitter = new SignalEmitter(mBusEndpoint, 0, SignalEmitter.GlobalBroadcast.Off);
            mSignalEmitter.setSessionlessFlag(true);
        }

        /* Get the P2PBusEndpoint for the emitter */
        P2PBusEndpoint broadcast = mSignalEmitter.getInterface(P2PBusEndpoint.class);

        try {
            String msg = "onBroadcast(): %s";
            int signal = (int)event.getSignal();
            switch(signal) {
                case P2P.ALIVE:
                    broadcast.alive(P2PAboutData.getAppId(), mBus.getUniqueName());
                    Log.i(TAG, String.format(msg, "Alive " + mBus.getUniqueName()));
                    break;
                case P2P.LEFT:
                    String[] networks = (String[])event.getObservable();
                    broadcast.left(P2PAboutData.getAppId(), networks);
                    Log.i(TAG, String.format(msg, "Left networks " + Arrays.toString(networks)));
                    break;
                default:
                    Log.i(TAG, String.format(msg, "Unknown signal " + event));
                    return false;
            }
            mMessageContextMap.put(signal, mSignalEmitter.getMessageContext());
        } catch (Exception e) {
            String msg = "Failed to broadcast signal %s";
            exception(String.format(msg, event.getSignal()), e);
            return false;
        }

        return true;

    }

    /* Cancel broadcast signal sent to all peers in network */
    boolean cancel(Event event) {
        return P2PUtils.raise(P2P.CANCEL, mInboundHandler, event);
    }

    /**
     * Handle cancel broadcast signals pending delivery on background thread.
     * @return boolean
     */
    private boolean onCancel(Event event)  {

        if(!isReady("onCancel")) {
            return false;
        }

        if(mSignalEmitter == null) {
            warning("No broadcast signal sent yet");
            return false;
        }

        if(P2P.CANCEL != event.getSignal()) {
            warning(String.format("Expected signal %s, found: %s", P2P.CANCEL, event.getSignal()));
            return false;
        }

        if(P2P.ALL == event.getObservable()) {
            int count = 0;
            for (MessageContext it : mMessageContextMap.values()) {
                if(onCancel(event, it)) {
                    count++;
                }
            }
            return count>0;
        }

        return onCancel(event, mMessageContextMap.get(event.getSignal()));

    }

    private boolean onCancel(Event event, MessageContext context) {
        if (context == null) {
            warning("No broadcast sent for signal " + event.getSignal());
            return false;
        }
        Status status = mSignalEmitter.cancelSessionlessSignal(context.serial);
        if (Status.OK != status) {
            warning(String.format("Failed to cancel signal %s, status: %s",
                    context.memberName, status));
        }
        return true;
    }

    /**
     * Leave all networks, disconnect from bus and release all resources and quit looper thread
     */
    public boolean quit() {

        if(!P2PUtils.raise(P2P.QUIT, mInboundHandler)) {
            return false;
        }

        mInboundHandler.getLooper().quitSafely();
        return true;
    }

    /**
     * Handle quit on background thread.
     * @return boolean
     */
    private boolean onQuit() {

        if (isConnected()) {

            /**
             * Cancel any pending signals
             */
            onCancel(new Event(P2P.CANCEL, this, P2P.ALL));

            mBus.unregisterAboutListener(this);

            if(!mNetworkEndpointMap.isEmpty()) {
                mAboutObj.unannounce();
            }

            mBusEndpoint.onUnRegister();

            for(P2PNetworkEndpointImpl it : mNetworkEndpointMap.values()) {
                it.onLeave();
            }

            mBus.disconnect();
            mBus.release();

        } else {
            error("onQuit(): Bus attachment not connected");
            return false;
        }

        /** Cleanup */
        mBus = null;
        mAboutObj = null;
        mAboutData = null;
        mBusEndpoint = null;

        mInbound.clear();
        mOutbound.clear();
        mNetworkEndpointMap.clear();

        return true;

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
        P2PUtils.raise(P2P.ERROR, mOutboundHandler);
    }

    private void warning(String msg) {
        Log.w(TAG, msg);
        P2PUtils.raise(P2P.WARNING, mOutboundHandler);
    }

    private void exception(String msg, Exception e) {
        Log.e(TAG, msg , e);
        P2PUtils.raise(P2P.ERROR, mOutboundHandler);
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

    /**
     * Check if handler is ready to execute given method
     * @param method Name of method
     * @return boolean
     */
    private boolean isReady(String method) {
        if(!isConnected()) {
            error(String.format("%s(): Bus not connected", method));
            return false;
        }
        if(!mInboundHandler.getLooper().getThread().isAlive()) {
            error(String.format("%s(): Handler is released", method));
            return false;
        }
        return true;
    }

    private NetworkInfo createNetworkInfo(Object args) {
        String name;
        short port = 0;
        try {
            name = String.valueOf(args);
            /** Ensure monotonously increasing port number */
            for(P2PNetworkEndpointImpl it : mNetworkEndpointMap.values()) {
                port = (short)Math.max((int)port, (int)it.mPort);
            }
            port = (short)((int)port + 1);
        } catch (Exception e) {
            String msg = "Invalid arguments %s, %s";
            error(String.format(msg, args, e.toString()));
            return null;
        }
        return new NetworkInfo(name, port);
    }

    private final class NetworkInfo {
        final String name;
        final short port;

        public NetworkInfo(String name, short port) {
            this.name = name;
            this.port = port;
        }
    }

}

