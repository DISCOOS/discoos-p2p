package org.discoos.p2p.internal;

import android.util.Log;

import org.discoos.p2p.P2P;
import org.discoos.p2p.P2PUtils;
import org.discoos.p2p.internal.PeerInfoCache.PeerInfoImpl;
import org.discoos.signal.Dispatcher;
import org.discoos.signal.Event;
import org.discoos.signal.Observer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class P2PNetworkCache {

    public static final String TAG = "P2PNetworkCache";

    /**
     * Stores cache to file (lacy initialization)
     * @see P2PNetworkCache#onNetworkChanged()
     */
    private P2PTask mStoreNetworkCache;

    /**
     * Reference to singleton instance
     */
    private final static P2PNetworkCache INSTANCE = new P2PNetworkCache();

    /**
     * Get signal dispatcher
     */
    private Dispatcher mDispatcher;

    /**
     * Set of networks
     */
    private final Map<String, P2PNetworkImpl> mNetworkMap = new LinkedHashMap<>();

    /**
     * Only allowed to instantiate from this class
     */
    private P2PNetworkCache() {}

    /**
     * Get singleton instance
     * @return P2PNetworkCache
     */
    public static P2PNetworkCache getInstance() {
        return INSTANCE;
    }

    void setDispatcher(Dispatcher dispatcher) {
        if(mDispatcher != null) {
            mDispatcher.remove(mObserver);
        }
        mDispatcher = dispatcher;
        mDispatcher.add(P2P.CHANGED, mObserver);
    }
    private Observer mObserver = new Observer() {
        @Override
        public void handle(Object signal, Object observable) {
            if(P2P.isEvent(observable)) {
                Event event = (Event)observable;
                if(P2P.isPeerChange(event)) {
                    /**
                     * All all new networks to cache
                     */
                    PeerInfoImpl info = (PeerInfoImpl)event.getObservable();
                    Set<String> names = new HashSet<>(info.getNetworks());
                    names.removeAll(mNetworkMap.keySet());
                    for(String name : names) {
                        P2PNetworkImpl network = new P2PNetworkImpl(name, name);
                        add(network.init());
                        // Add manually since network was added after the signal was raised
                        network.add(event.getObservable());
                    }
                }
            }
        }
    };

    /**
     * Check if cache contains network with given name
     * @return P2PNetwork
     */
    public boolean contains(String name) {
        return mNetworkMap.containsKey(name);
    }

    /**
     * Check if cache contains given network instance
     * @return P2PNetwork
     */
    public boolean contains(P2PNetworkImpl network) {
        return mNetworkMap.containsValue(network);
    }

    /**
     * Get network from name
     * @return P2PNetwork
     */
    public P2PNetworkImpl get(String name) {
        return mNetworkMap.get(name);
    }

    /**
     * Get number of networks
     * @return int
     */
    public int getCount() {
        return mNetworkMap.size();
    }

    /**
     * Get unmodifiable list of network names
     * @return Set
     */
    public List<String> getNames() {
        return Collections.unmodifiableList(new ArrayList<>(mNetworkMap.keySet()));
    }

    /**
     * Get unmodifiable list of network names
     * @return Set
     */
    public List<P2PNetworkImpl> getNetworks() {
        return Collections.unmodifiableList(new ArrayList<>(mNetworkMap.values()));
    }

    /**
     * Add network to cache
     * @param network Network instance
     * @return P2PNetwork
     */
    P2PNetworkImpl add(P2PNetworkImpl network) {
        network = mNetworkMap.put(network.mName, network);
        mDispatcher.raise(P2P.CHANGED, new Event(P2P.ADDED, this, network));
        onNetworkChanged();
        return network;
    }

    /**
     * Remove network from cache
     * @param network Network instance
     * @return boolean
     */
    boolean remove(P2PNetworkImpl network) {
        if(mNetworkMap.remove(network.mName) != null) {
            mDispatcher.raise(P2P.CHANGED, new Event(P2P.REMOVED, this, network));
            return onNetworkChanged();
        }
        return false;
    }

    /**
     * Remove all networks from cache
     */
    void removeAll() {
        int count = mNetworkMap.size();
        mNetworkMap.clear();
        mDispatcher.raise(P2P.CHANGED, new Event(P2P.REMOVED, this, count));
        onNetworkChanged();
    }

    /**
     * Perform change actions
     * @return boolean
     */
    boolean onNetworkChanged() {
        if (mStoreNetworkCache == null) {
            mStoreNetworkCache = new StoreNetworkCache(P2P.getFilesDir().getAbsolutePath());
        }
        P2PTaskManager.getInstance().schedule(mStoreNetworkCache, P2P.CACHE_STORE_DELAY);
        return true;
    }

    /**
     * Load stored networks
     */
    final static class LoadNetworks extends P2PTask<List<P2PNetworkImpl>> {

        private final String mRoot;

        public LoadNetworks(String root) {
            mRoot = root;
        }

        @Override
        protected List<P2PNetworkImpl> doInBackground() {
            return P2PUtils.readList(mRoot, P2P.FILE_NETWORK_LIST, P2PNetworkImpl.class);
        }

        @Override
        protected void onFinished(List<P2PNetworkImpl> result) {
            P2P.getContext().ensure(result);
        }
    }

    /**
     * Store current networks
     */
    final class StoreNetworkCache extends P2PTask<Void> {

        private final String mRoot;

        public StoreNetworkCache(String root) {
            mRoot = root;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Void doInBackground() {
            P2PUtils.writeObject(mRoot, P2P.FILE_NETWORK_LIST,
                    new ArrayList(mNetworkMap.values()));
            return null;
        }

        @Override
        protected void onFinished(Void result) {
            Log.d(TAG, "Stored network list");
        }
    }
}