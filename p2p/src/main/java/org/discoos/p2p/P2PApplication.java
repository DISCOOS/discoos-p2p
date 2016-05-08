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
package org.discoos.p2p;

import android.app.Application;
import android.content.ComponentName;
import android.support.annotation.Nullable;
import android.util.Log;

import org.discoos.p2p.internal.P2PAboutData;
import org.discoos.p2p.internal.P2PNetworkImpl;
import org.discoos.p2p.internal.P2PService;
import org.discoos.p2p.internal.P2PTask;
import org.discoos.p2p.internal.P2PTaskManager;
import org.discoos.signal.Dispatcher;
import org.discoos.signal.Observable;
import org.discoos.signal.Observer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P2PApplication is instantiated when the process for it is created by Android.
 * It is never tear down by Android, nor does Android provide any way for it to tear down it
 * self. Thus, the lifecycle of P2PApplication is the same as the process that created it.
 * The only way to tear down P2PApplication is by killing the Android process. This property
 * makes it suitable to maintain global application state, like the state of P2PService.
 *
 */
public class P2PApplication extends Application implements Observable {

    private static final String TAG = "P2PApplication";

    /**
     * Reference to singleton instance
     */
    public static P2PApplication INSTANCE;

    /**
     * Event dispatcher
     */
    private Dispatcher mDispatcher = new Dispatcher();

    /**
     * Set of networks
     */
    private Map<String, P2PNetwork> mNetworkMap = new HashMap<>();

    /**
     * Stores the global state of of P2PService, which runs in the background.
     * If set, this implies that the P2PService is running. It is set by
     * {@link P2PApplication#onCreate} when the Android process for P2PApplication is
     * created. Since the only standard way to tear down P2PApplication is by killing the
     * application process, the method {@link P2PApplication#quit()} must be called whenever the
     * user want to reclaim resources taken by the application, including the P2PService. This will
     * reset {@link #mRunningService} to indicate that the service must be restated
     * next time {@link P2PApplication#ensure()} is called.
     *
     * @see P2PApplication#onCreate()
     * @see P2PApplication#ensure()
     * @see P2PApplication#quit()
     *
     */
    private ComponentName mRunningService;

    /**
     * Default constructor. Singleton, initializes INSTANCE member
     */
    public P2PApplication() {
        INSTANCE = this;
    }

    /**
     * Get singleton instance
     * @return P2PApplication
     */
    public static P2PApplication getInstance() {
        return INSTANCE;
    }

    /**
     * When created, the application fires an intent to create a P2PService instance.
     * @see P2PApplication#ensure()
     * @see P2PApplication#quit()
     */
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "onCreate()");

        mDispatcher.add(new Observer() {
            @Override
            public void handle(Object signal, Object observable) {
                switch ((int) signal) {
                    /** Inform user that peer status has changed */
                    case P2P.JOINED:
                    case P2P.TIMEOUT:
                        String action = (P2P.JOINED == (int) signal) ? "joined" : "left";
                        PeerInfo info = ((PeerInfo) observable);
                        String name = info.get(P2PAboutData.MODEL_NUMBER).toString();
                        String msg = String.format("Peer %s has %s the network", name, action);
                        mDispatcher.raise(P2P.NOTIFY, msg);
                        break;
                }
            }
        });

        String root = getExternalCacheDir().getAbsolutePath();

        // Load persisted state (last stored before reboot of device)
        P2PTaskManager.getInstance().execute(new LoadNetworks(root));
        P2PTaskManager.getInstance().execute(new P2PNetworkImpl.LoadPeerInfoCache(root));

    }

    public String getName() {
        return getResources().getString(R.string.app_name);
    }

    public String getVersion() {
        return BuildConfig.VERSION_NAME;
    }

    public String getDescription() {
        return getResources().getString(R.string.app_description);
    }

    public String getManufacturer() {
        return getResources().getString(R.string.app_manufacturer);
    }

    public String getSupportUrl() {
        return getResources().getString(R.string.app_support_url);
    }

    /**
     * Join P2P network
     *
     * @param name P2P network name (well-known bus name)
     * @param port P2P network port (bus attachment port)
     *
     * @return P2PNetwork Network instance if joined, false otherwise.
     */
    @Nullable
    public P2PNetwork join(String name, int port, String label) {
        P2PNetwork network = new P2PNetworkImpl(name, (short)port, label);
        return join(network) ? network : null;
    }

    /**
     * Join P2P network
     *
     * @param network P2P network instance
     */
    private boolean join(P2PNetwork network) {
        String name = network.getName();
        short port = network.getPort();
        mRunningService = P2PService.join(name, port);
        if (mRunningService == null) {
            String msg = String.format("P2PService is not running");
            Log.i(TAG, msg);
        } else if(!mNetworkMap.containsKey(name)) {
            mNetworkMap.put(name, network);

            String root = getExternalCacheDir().getAbsolutePath();

            // Store networks
            P2PTaskManager.getInstance().execute(new StoreNetworks(root));

        }
        return mNetworkMap.containsKey(name);
    }

    /**
     * Leave P2P network
     *
     * @param name P2P network name (well-known bus name)
     *
     * @return boolean
     */
    public boolean leave(String name) {
        P2PNetwork network = mNetworkMap.get(name);
        if(network != null) {
            mRunningService = P2PService.leave(name);
            if (mRunningService == null) {
                Log.i(TAG, "P2PService is not running");
                return false;
            }
        } else {
            Log.i(TAG, String.format("Network %s unknown", name));
            return false;
        }
        return network != null;
    }

    /**
     * Leave all P2P networks
     *
     * @return boolean
     */
    public boolean leaveAll() {
        mRunningService = P2PService.leaveAll();
        if (mRunningService == null) {
            Log.i(TAG, "P2PService is not running");
            return false;
        }
        return true;
    }


    /**
     * P2PApplication components that depends on P2PService should call this method to ensure that
     * it is running.
     *
     * @see P2PApplication#onCreate()
     * @see P2PApplication#quit()
     */
    public void ensure() {
        Log.i(TAG, "ensure()");
        if (mRunningService == null && !mNetworkMap.isEmpty()) {
            Log.i(TAG, "Joining networks...");
            for(P2PNetwork it : mNetworkMap.values()) {
                join(it);
            }
        }
    }

    /**
     * Ping peer with given id
     * @param id Peer id
     * @return boolean true if peer found, false otherwise
     */
    public boolean ping(String id) {
        int count = 0;
        Log.i(TAG, String.format("ping(%s)", id));
        for(P2PNetwork network : mNetworkMap.values()) {
            PeerInfo info = network.getPeer(id);
            if( info !=null ) {
                mDispatcher.raise(P2P.PING, info);
                count++;
            }
        }
        return count > 0;
    }

    /**
     * Broadcast signal to all peers
     */
    public void broadcast() {
        Log.i(TAG, "broadcast()");
        mDispatcher.raise(P2P.BROADCAST, this);
    }

    /**
     * Cancel broadcast signal to all peers
     */
    public void cancel() {
        Log.i(TAG, "cancel()");
        mDispatcher.raise(P2P.CANCEL, this);
    }

    /**
     * Since the only standard way to tear down P2PApplication is by killing the
     * application process, this method should be called whenever the user want to terminate all
     * P2PApplication components, including the P2PService.
     *
     * @see P2PApplication#onCreate()
     * @see P2PApplication#ensure()
     */
    public void quit() {
        Log.i(TAG, "quit()");
        mDispatcher.raise(P2P.QUIT, this);
        mRunningService = null;
    }

    /**
     * Get signal dispatcher
     * @return Dispatcher
     */
    public Dispatcher getDispatcher() {
        return mDispatcher;
    }

    /**
     * Get P2P network from name
     * @return P2PNetwork
     */
    public P2PNetwork getNetwork(String name) {
        return mNetworkMap.get(name);
    }

    /**
     * Get unmodifiable list of network names
     * @return Set
     */
    public List<String> getNetworkNames() {
        return Collections.unmodifiableList(new ArrayList<>(mNetworkMap.keySet()));
    }

    /**
     * Get unmodifiable peer from id
     * @param id Peer id
     * @return PeerInfo
     */
    public PeerInfo getPeer(String id) {
        return P2PNetworkImpl.getPeerCache().get(id);
    }

    /**
     * Get unmodifiable list of all peer ids
     * @return Set
     */
    public List<String> getPeerIds() {
        return Collections.unmodifiableList(new ArrayList<>(P2PNetworkImpl.getPeerCache().keySet()));
    }

    /**
     * Get unmodifiable set of all peers
     * @return Set
     */
    public List<PeerInfo> getPeerList() {
        return Collections.unmodifiableList(new ArrayList<>(P2PNetworkImpl.getPeerCache().values()));
    }

    /**
     * Load stored networks
     */
    public final class LoadNetworks extends P2PTask<List<P2PNetworkImpl>> {

        public LoadNetworks(String root) {
            super(root);
        }

        @Override
        protected List<P2PNetworkImpl> doInBackground() {
            return P2PUtils.readList(mRoot, P2P.FILE_NETWORK_LIST, P2PNetworkImpl.class);
        }

        @Override
        protected void onFinished(List<P2PNetworkImpl> result) {

            // Join default network?
            if(result.isEmpty()) {

                join(getPackageName(), 929, "P2PApp");

            } else {
                // Join previous networks
                for(P2PNetworkImpl it : result) {
                    join(it);
                }
            }
        }
    }

    /**
     * Store current networks
     */
    public final class StoreNetworks extends P2PTask<Void> {

        public StoreNetworks(String root) {
            super(root);
        }

        @Override
        protected Void doInBackground() {
            P2PUtils.writeObject(mRoot, P2P.FILE_NETWORK_LIST,
                    new ArrayList<>(mNetworkMap.values()));
            return null;
        }

        @Override
        protected void onFinished(Void result) {
            Log.i(TAG, "Stored network list");
        }
    }

}
