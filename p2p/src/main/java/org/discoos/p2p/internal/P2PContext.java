package org.discoos.p2p.internal;

import android.content.ComponentName;
import android.support.annotation.Nullable;
import android.util.Log;

import org.discoos.p2p.BuildConfig;
import org.discoos.p2p.P2P;
import org.discoos.p2p.P2PApplication;
import org.discoos.p2p.P2PNetwork;
import org.discoos.p2p.P2PUtils;
import org.discoos.p2p.PeerInfo;
import org.discoos.p2p.R;
import org.discoos.signal.Dispatcher;
import org.discoos.signal.Event;
import org.discoos.signal.Observer;

import java.util.Collection;
import java.util.List;

public class P2PContext {

    private static final String TAG = "P2PContext";

    /**
     * Singleton instance
     */
    private static final P2PContext INSTANCE = new P2PContext();

    /**
     * Event dispatcher
     */
    private final Dispatcher mDispatcher = new Dispatcher();

    /**
     * Network cache
     */
    private final P2PNetworkCache mNetworkCache = P2PNetworkCache.getInstance();

    /**
     * Peer info cache
     */
    private final PeerInfoCache mPeerInfoCache = PeerInfoCache.getInstance();

    /**
     * Ensure only created once
     */
    private boolean mCreated = false;

    /**
     * Stores the global state of of P2PService, which runs in the background.
     * If set, this implies that the P2PService is running. It is set by
     * {@link P2PApplication#onCreate} when the Android process for P2PApplication is
     * created. Since the only standard way to tear down P2PApplication is by killing the
     * application process, the method {@link P2PContext#quit()} must be called whenever the
     * user want to reclaim resources taken by the application, including the P2PService. This will
     * reset {@link #mRunningService} to indicate that the service must be restated
     * next time {@link P2PContext#ensure()} is called.
     *
     * @see P2PApplication#onCreate()
     * @see P2PContext#ensure()
     * @see P2PContext#quit()
     */
    public ComponentName mRunningService;

    /**
     * Only allowed to instantiate from this class
     */
    P2PContext() { }

    /**
     * Get singleton instance
     * @return P2PNetworkCache
     */
    public static P2PContext getInstance() {
        return INSTANCE;
    }

    /**
     * Create P2P context
     */
    public boolean onCreate() {

        if(mCreated) {
            Log.w(TAG, "Already created");
            return false;
        }

        Log.i(TAG, "onCreate()");

        mDispatcher.add(new Observer() {
            @Override
            public void handle(Object signal, Object observable) {
                switch ((int) signal) {
                    /** Inform user that peer status has changed */
                    case P2P.ADDED:
                    case P2P.TIMEOUT:
                        String action = (P2P.ADDED == (int) signal) ? "joined" : "left";
                        PeerInfo info = ((PeerInfo) observable);
                        String name = info.get(P2PAboutData.MODEL_NUMBER).toString();
                        String msg = String.format("Peer %s has %s the network", name, action);
                        mDispatcher.raise(P2P.NOTIFY, msg);
                        break;
                }
            }
        });
        mNetworkCache.setDispatcher(mDispatcher);
        mPeerInfoCache.setDispatcher(mDispatcher);

        // Load persisted state
        String root = P2P.getFilesDir().getAbsolutePath();
        P2PTaskManager.getInstance().execute(new P2PNetworkCache.LoadNetworks(root));
        P2PTaskManager.getInstance().execute(new PeerInfoCache.LoadPeerInfoCache(root));

        return mCreated = true;

    }

    public String getName() {
        return P2P.getApplication().getString(R.string.app_name);
    }

    public String getVersion() {
        return BuildConfig.VERSION_NAME;
    }

    public String getDescription() {
        return P2P.getApplication().getString(R.string.app_description);
    }

    public String getManufacturer() {
        return P2P.getApplication().getString(R.string.app_manufacturer);
    }

    public String getSupportUrl() {
        return P2P.getApplication().getString(R.string.app_support_url);
    }

    /**
     * P2PApplication components that depends on P2PService
     * must call this method to ensure that it is running.
     *
     * @see P2PApplication#onCreate()
     * @see P2PContext#quit()
     */
    public void ensure() {
        ensure(mNetworkCache.getNetworks());
    }

    void ensure(Collection<P2PNetworkImpl> networks) {

        Log.i(TAG, String.format("ensure() count: %s", networks.size()));

        // Join default network?
        if (!networks.isEmpty()) {

            String id = P2PUtils.toShortId(P2PAboutData.getAppId());

            // Only re-join networks which this peer has joined already
            for (P2PNetworkImpl it : networks) {
                if (it.getPeerIds().contains(id)) {
                    join(it);
                } else {
                    it.init();
                }
            }
        }
    }

    /**
     * Join P2P network
     *
     * @param name P2P network name (well-known bus name)
     * @return P2PNetwork Network instance if joined, false otherwise.
     */
    @Nullable
    public P2PNetwork join(String name, String label) {
        P2PNetworkImpl network = mNetworkCache.get(name);
        if (network == null) {
            network = new P2PNetworkImpl(name, label);
        } else {
            network.setLabel(label);
        }
        return join(network) ? network : null;
    }

    /**
     * Join P2P network
     *
     * @param network P2P network instance
     */
    public boolean join(P2PNetworkImpl network) {

        String name = network.getName();

        network.init();

        mRunningService = P2PService.join(name);
        if (mRunningService == null) {
            Log.i(TAG, "P2PService is not running");
        } else if (!mNetworkCache.contains(name)) {
            mNetworkCache.add(network);
        } else if (!mNetworkCache.contains(network)) {
            network.release();
        }
        return mNetworkCache.contains(name);
    }

    /**
     * Leave P2P network
     *
     * @param name P2P network name (well-known bus name)
     * @return P2PNetwork
     */
    public P2PNetwork leave(String name) {
        P2PNetworkImpl network = mNetworkCache.get(name);
        if (network != null) {
            leave(network);
        } else {
            Log.i(TAG, String.format("Network %s is unknown", name));
        }
        return network;
    }

    /**
     * Leave P2P network
     *
     * @param network P2P network
     * @return boolean
     */
    public boolean leave(P2PNetworkImpl network) {
        if (network != null) {
            mRunningService = P2PService.leave(network.getName());
            if (mRunningService == null) {
                Log.i(TAG, "P2PService is not running");
                return false;
            }
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
        releaseAll();
        return true;
    }

    /**
     * Leave P2P network and remove from network list
     *
     * @param name P2P network name (well-known bus name)
     * @return P2PNetwork
     */
    public P2PNetwork delete(String name) {
        P2PNetworkImpl network = mNetworkCache.get(name);
        if (network != null) {
            leave(network);
            network.release();
            mNetworkCache.remove(network);
        } else {
            Log.i(TAG, String.format("Unknown network %s", name));
        }
        return network;
    }

    /**
     * Leave all P2P networks and clear network list
     *
     * @return boolean
     */
    public boolean deleteAll() {
        boolean delete = leaveAll();
        if (delete) {
            releaseAll();
            int count = mNetworkCache.getCount();
            mNetworkCache.removeAll();
        }
        return delete;
    }

    /**
     * Release all references
     */
    private void releaseAll() {
        for (P2PNetworkImpl it : mNetworkCache.getNetworks()) {
            it.release();
        }
    }

    /**
     * Ping peer with given id
     *
     * @param id Peer id
     * @return boolean true if peer found, false otherwise
     */
    public boolean ping(String id) {
        int count = 0;
        Log.i(TAG, String.format("ping(%s)", id));
        for (P2PNetwork network : mNetworkCache.getNetworks()) {
            PeerInfo info = network.getPeer(id);
            if (info != null) {
                mDispatcher.raise(P2P.PING, info);
                count++;
            }
        }
        return count > 0;
    }

    /**
     * Broadcast signal to all peers
     */
    public void broadcast(Event event) {
        Log.i(TAG, "broadcast()");
        mDispatcher.raise(P2P.BROADCAST, event);
    }

    /**
     * Cancel broadcast signal to all peers
     */
    public void cancel(Event event) {
        Log.i(TAG, "cancel()");
        mDispatcher.raise(P2P.CANCEL, event);
    }

    /**
     * Since the only standard way to tear down P2PApplication is by killing the
     * application process, this method should be called whenever the user want to terminate all
     * P2PApplication components, including the P2PService.
     *
     * @see P2PApplication#onCreate()
     * @see P2PContext#ensure()
     */
    public void quit() {
        Log.i(TAG, "quit()");
        mDispatcher.raise(P2P.QUIT, null);
        releaseAll();
        mRunningService = null;
    }

    /**
     * Get signal dispatcher
     *
     * @return Dispatcher
     */
    public Dispatcher getDispatcher() {
        return mDispatcher;
    }

    /**
     * Get P2P network from name
     *
     * @return P2PNetwork
     */
    public P2PNetwork getNetwork(String name) {
        return mNetworkCache.get(name);
    }

    /**
     * Get unmodifiable list of network names
     *
     * @return Set
     */
    public List<String> getNetworkNames() {
        return mNetworkCache.getNames();
    }

    /**
     * Get unmodifiable peer from id
     *
     * @param id Peer id
     * @return PeerInfo
     */
    public PeerInfo getPeer(String id) {
        return mPeerInfoCache.get(id);
    }

    /**
     * Get unmodifiable list of all peer ids
     *
     * @return Set
     */
    public List<String> getPeerIds() {
        return mPeerInfoCache.getIds();
    }

    /**
     * Get unmodifiable set of all peers
     *
     * @return Set
     */
    public List<? extends PeerInfo> getPeerList() {
        return mPeerInfoCache.getList();
    }

}