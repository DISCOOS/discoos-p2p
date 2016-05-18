package org.discoos.p2p.internal;

import android.os.Handler;
import android.util.Log;

import org.alljoyn.bus.Variant;
import org.discoos.p2p.P2P;
import org.discoos.p2p.P2PUtils;
import org.discoos.p2p.PeerInfo;
import org.discoos.signal.Dispatcher;
import org.discoos.signal.Event;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PeerInfoCache implementation
 */
public final class PeerInfoCache {

    private static final String TAG = "PeerInfoCache";

    /**
     * Reference to singleton instance
     */
    private final static PeerInfoCache INSTANCE = new PeerInfoCache();

    /**
     * Stores cache to file (lacy initialization)
     * @see PeerInfoCache#onPeerChanged(int, PeerInfoImpl)
     */
    private P2PTask mStorePeerInfoCache;

    /**
     * Get signal dispatcher
     */
    private Dispatcher mDispatcher;

    /**
     * Handler on main thread
     */
    private Handler mHandler = new Handler();

    /**
     * Only allowed to instantiate from this class
     */
    private PeerInfoCache() {}

    /**
     * Get singleton instance
     * @return PeerInfoCache
     */
    public static PeerInfoCache getInstance() {
        return INSTANCE;
    }

    void setDispatcher(Dispatcher dispatcher) {
        mDispatcher = dispatcher;
    }


    public boolean contains(String id) {
        synchronized (PeerInfoCache.class) {
            return mPeerCache.containsKey(id);
        }
    }

    public PeerInfoImpl get(String id) {
        synchronized (PeerInfoCache.class) {
            return mPeerCache.get(id);
        }
    }

    public PeerInfoImpl getMe() {
        String id = P2PUtils.toShortId(P2PAboutData.getAppId());
        return get(id);
    }

    public List<String> getIds() {
        return Collections.unmodifiableList(new ArrayList<>(mPeerCache.keySet()));
    }

    public List<PeerInfoImpl> getList() {
        synchronized (PeerInfoCache.class) {
            return Collections.unmodifiableList(new ArrayList<>(mPeerCache.values()));
        }
    }

    PeerInfoImpl put(PeerInfoImpl info) {
        PeerInfoImpl previous;
        synchronized (PeerInfoCache.class) {
            previous = mPeerCache.put(info.id, info);
        }
        if(!info.equals(previous)) {
            onPeerChanged(previous == null ? P2P.ADDED : P2P.CHANGED, info);
        }
        return previous;
    }

    /**
     * Package private map of peers across networks
     */
    private final Map<String, PeerInfoImpl> mPeerCache = new LinkedHashMap<>();

    PeerInfoImpl newInstance(Map<String, Variant> data) {
        PeerInfoImpl info;
        String id = P2PUtils.toShortId(data);
        synchronized (PeerInfoCache.class) {
            if(mPeerCache.containsKey(id)) {
                info = mPeerCache.get(id).update(
                        P2PUtils.toUniqueName(data),
                        P2PUtils.toSummary(data),
                        P2PUtils.toDetails(data),
                        P2PUtils.toParams(data)
                );
            } else {
                info = new PeerInfoImpl(id,
                        P2PUtils.toUniqueName(data),
                        P2PUtils.toSummary(data),
                        P2PUtils.toDetails(data),
                        P2PUtils.toParams(data)
                );
                onPeerChanged(P2P.ADDED, info);
            }
        }
        return info;
    }

    void onPeerChanged(int type, PeerInfoImpl info) {
        synchronized (PeerInfoCache.class) {
            mPeerCache.put(info.id, info);
            if(mStorePeerInfoCache == null) {
                String root = P2P.getFilesDir().getAbsolutePath();
                mStorePeerInfoCache = new StorePeerInfoCache(root);
            }
            P2PTaskManager.getInstance().schedule(mStorePeerInfoCache, P2P.CACHE_STORE_DELAY);
            final Event event = new Event(type, this, info);
            /** Ensure executed on main thread */
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDispatcher.raise(P2P.CHANGED, event);
                }

            });
        }
    }

    /**
     * Immutable (thread-safe) peer information.
     */
    static class PeerInfoImpl implements PeerInfo, Serializable {

        private static final long serialVersionUID = 1L;

        public final String id;
        public final String name;
        public final String summary;
        public final String details;
        public final Map<String, Object> params;

        private boolean timeout;
        private Date timestamp;

        private Map<String, Short> networks = new LinkedHashMap<>();

        PeerInfoImpl(String id, String name, String summary, String details, Map<String, Object> params) {
            this.id = id;
            this.name = name;
            this.summary = summary;
            this.details = details;
            this.params = Collections.unmodifiableMap(params);
            this.timeout = false;
            this.timestamp = Calendar.getInstance().getTime();
        }

        public List<String> getNetworks() {
            return Collections.unmodifiableList(new ArrayList<>(networks.keySet()));
        }

        @Override
        public boolean isMemberOf(String network) {
            return networks.containsKey(network);
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getSummary() {
            return summary;
        }

        public String getDetails() {
            return details;
        }

        public boolean isTimeout() {
            return timeout;
        }

        public Object get(String parameter) {
            return params.get(parameter);
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public boolean isMe() {
            return id.equals(P2PUtils.toShortId(P2PAboutData.getAppId()));
        }

        PeerInfoImpl alive() {
            return alive(name);
        }

        PeerInfoImpl alive(String name) {
            synchronized (PeerInfoCache.class) {
                return cache(new PeerInfoImpl(id, name, summary, details, params));
            }
        }

        PeerInfoImpl timeout() {
            synchronized (PeerInfoCache.class) {
                this.timeout = true;
                PeerInfoCache.getInstance().onPeerChanged(P2P.CHANGED, this);
                return this;
            }
        }

        PeerInfoImpl update(String name, String summary, String details, Map<String, Object> params) {
            synchronized (PeerInfoCache.class) {
                return cache(new PeerInfoImpl(id, name, summary, details, params));
            }
        }

        PeerInfoImpl add(String network, Short port) {
            synchronized (PeerInfoCache.class) {
                Short previous = networks.put(network, port);
                if(port != previous) {
                    PeerInfoCache.getInstance().onPeerChanged(P2P.CHANGED, this);
                }
                return this;
            }
        }

        PeerInfoImpl remove(String... networks) {
            synchronized (PeerInfoCache.class) {
                if(this.networks.keySet().removeAll(Arrays.asList(networks))) {
                    PeerInfoCache.getInstance().onPeerChanged(P2P.CHANGED, this);
                }
                return this;
            }
        }

        private PeerInfoImpl cache(PeerInfoImpl info) {
            info.networks = new LinkedHashMap<>(networks);
            PeerInfoCache.getInstance().onPeerChanged(P2P.CHANGED, info);
            return info;
        }

        @Override
        public String toString() {
            return summary;
        }
    }

    /**
     * Load PeerInfo cache
     */
    static final class LoadPeerInfoCache extends P2PTask<List<PeerInfoImpl>> {

        private final String mRoot;

        public LoadPeerInfoCache(String root) {
            mRoot = root;
        }

        @Override
        protected List<PeerInfoImpl> doInBackground() {
            return P2PUtils.readList(mRoot, P2P.FILE_PEERINFO_LIST, PeerInfoImpl.class);
        }

        @Override
        protected void onFinished(List<PeerInfoImpl> result) {
            synchronized (PeerInfoCache.class) {
                for (PeerInfoImpl info : result) {
                    if (!PeerInfoCache.getInstance().contains(info.id)) {
                        PeerInfoCache.getInstance().put(info);
                    }
                }
            }
        }
    }

    /**
     * Store PeerInfo cache
     */
    static final class StorePeerInfoCache extends P2PTask<Void> {

        private final String mRoot;

        public StorePeerInfoCache(String root) {
            mRoot = root;
        }

        @Override
        protected Void doInBackground() {
            synchronized (PeerInfoCache.class) {
                P2PUtils.writeObject(mRoot, P2P.FILE_PEERINFO_LIST,
                        new ArrayList<>(PeerInfoCache.getInstance().getList()));
            }
            return null;
        }

        @Override
        protected void onFinished(Void result) {
            Log.d(TAG, "Stored peerinfo list");
        }

    }
}
