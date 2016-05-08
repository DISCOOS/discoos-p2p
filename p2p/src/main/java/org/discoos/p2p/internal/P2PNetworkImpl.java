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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.util.Log;

import org.alljoyn.bus.Variant;
import org.discoos.p2p.P2P;
import org.discoos.p2p.P2PNetwork;
import org.discoos.p2p.P2PUtils;
import org.discoos.p2p.PeerInfo;
import org.discoos.signal.Dispatcher;
import org.discoos.signal.Observer;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper class for providing peers to list views
 */
public class P2PNetworkImpl implements P2PNetwork, Serializable {

    private static final String TAG = "P2PNetwork";

    static final long serialVersionUID = 1L;

    private transient Dispatcher mDispatcher;

    final String mName;

    final short mPort;

    String mLabel;

    /**
     * A array of peer ids.
     */
    private final List<String> mPeerList = new ArrayList<>();

    /**
     * Constructor
     * @param name Network name
     * @param port Network port
     */
    public P2PNetworkImpl(String name, short port, String label) {
        mName = name;
        mPort = port;
        mLabel = label;
        register();
    }

    /**
     * Inovked by ObjectInputStream during deserialization.
     * @return
     * @throws ObjectStreamException
     */
    Object readResolve() throws ObjectStreamException {
        register();
        return this;
    }

    /**
     *
     */
    public void register() {
        if(mDispatcher == null) {
            mDispatcher = P2P.getDispatcher();
            mDispatcher.add(P2P.INIT, new Observer() {
                @Override
                public void handle(Object signal, Object observable) {
                    log("INIT", "peers=" + clear());
                    mDispatcher.schedule(P2P.CHANGED, signal);
                }
            }).add(P2P.JOINED, new Observer() {
                @Override
                public void handle(Object signal, Object observable) {
                    log("JOINED", join(observable));
                    mDispatcher.schedule(P2P.CHANGED, signal);
                }
            }).add(P2P.ALIVE, new Observer() {
                @Override
                public void handle(Object signal, Object observable) {
                    log("ALIVE", join(observable));
                    mDispatcher.schedule(P2P.CHANGED, signal);
                }
            }).add(P2P.TIMEOUT, new Observer() {
                @Override
                public void handle(Object signal, Object observable) {
                    log("TIMEOUT", join(observable));
                    mDispatcher.schedule(P2P.CHANGED, signal);
                }
            }).add(P2P.LEAVE, new Observer() {
                @Override
                public void handle(Object signal, Object observable) {
                    log("LEAVE", leave(observable));
                    mDispatcher.schedule(P2P.CHANGED, signal);
                }
            });
        }
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public short getPort() {
        return mPort;
    }

    @Override
    public String getLabel() {
        return mLabel;
    }

    @Override
    public void setLabel(String label) {
        mLabel = label;
    }

    @Override
    public PeerInfo getPeer(String id) {
        return mPeerCache.get(id);
    }

    @Override
    public PeerInfo getPeer(int position) {
        return mPeerCache.get(mPeerList.get(position));
    }

    @Override
    public Set<String> getPeerIds() {
        return Collections.unmodifiableSet(new HashSet<>(mPeerList));
    }

    @Override
    public List<PeerInfo> getPeerList() {
        List<PeerInfo> items = new ArrayList<>();
        for(String id : mPeerList) {
            items.add(mPeerCache.get(id));
        }
        return Collections.unmodifiableList(items);
    }


    public static Map<String, PeerInfo> getPeerCache() {
        return Collections.unmodifiableMap(mPeerCache);
    }

    private PeerInfoImpl join(Object observable) {
        PeerInfoImpl info = (PeerInfoImpl) observable;
        if(!mPeerList.contains(info.id)) {
            mPeerList.add(info.id);
        }
        return info;
    }


    private PeerInfoImpl leave(Object observable) {
        PeerInfoImpl info = (PeerInfoImpl) observable;
        leave(info.id);
        return info;
    }

    private PeerInfo leave(String id) {
        if(mPeerList.remove(id)) {
            return mPeerCache.get(id);
        }
        return null;
    }

    private int clear() {
        int count = mPeerList.size();
        mPeerList.clear();
        return count;
    }

    /**
     * Package private map of peers across networks
     */
    static Map<String, PeerInfo> mPeerCache = new LinkedHashMap<>();

    public static PeerInfo createPeerInfo(String network, Map<String, Variant> data) {
        PeerInfo info;
        String id = P2PUtils.toShortId(data);
        if(mPeerCache.containsKey(id)) {
            info = mPeerCache.get(id).update(
                    P2PUtils.toUniqueName(data),
                    P2PUtils.toSummary(data),
                    P2PUtils.toDetails(data),
                    P2PUtils.toParams(data)
            );
        } else {
            info = new PeerInfoImpl(network, id,
                    P2PUtils.toUniqueName(data),
                    P2PUtils.toSummary(data),
                    P2PUtils.toDetails(data),
                    P2PUtils.toParams(data)
            );
        }
        mPeerCache.put(id, info);
        String root = P2P.getCacheDir().getAbsolutePath();
        P2PTaskManager.getInstance().execute(new P2PNetworkImpl.StorePeerInfoCache(root));

        return info;
    }


    private static void log(String signal, String msg) {
        assert !Looper.getMainLooper().equals(Looper.myLooper()) : "Not on main looper";
        Log.i(TAG, signal + ": " + msg);
    }

    private static void log(String signal, PeerInfoImpl info) {
        log(signal, info.summary);
    }

    /**
     * This class listen for changes in connectivity and
     * notifies peers each time the device connects to WIFI.
     */
    public static class ChangeReceiver extends BroadcastReceiver {

        private static final String TAG = "ChangeReceiver";

        /** Prevent firing when connecting the first time (new instance for each broadcast) */
        private static boolean mInit;

        /** Used when checking if state changed to WIFI (new instance for each broadcast) */
        private static Connectivity mState = P2PUtils.getConnectivityStatus(P2P.getApplication());

        @Override
        public synchronized void onReceive(final Context context, final Intent intent) {
            try {
                Connectivity state = P2PUtils.getConnectivityStatus(context);

                // Only after initial notification and if state has changed
                if(mInit && !mState.equals(state)) {

                    boolean isFailOver = intent.getBooleanExtra("FAILOVER_CONNECTION", false);
                    boolean isConnection = !intent.getBooleanExtra("EXTRA_NO_CONNECTIVITY", false);
                    String msg = "onReceive(%s->%s,failover=%s,connection=%s)";
                    Log.i(TAG, String.format(msg, mState, state, isFailOver, isConnection));

                    switch (state) {
                        case WIFI:
                            P2P.getApplication().broadcast();
                            break;
                        case MOBILE:
                            /* Only broadcast alive signal if changed to WIFI*/
                            P2P.getApplication().cancel();
                            break;
                        case NONE:
                            /* Only broadcast alive signal if changed to WIFI*/
                            P2P.getApplication().cancel();
                            break;
                    }
                }

                mInit = true;
                mState = state;
            }
            catch (Exception e) {
                Log.e(TAG, "onReceive()", e);
            }
        }
    }

    /**
     * Immutable (thread-safe) peer information.
     */
    public static class PeerInfoImpl implements PeerInfo, Serializable {
        public final List<String> networks;
        public final String id;
        public final String name;
        public final String summary;
        public final String details;
        public final boolean timeout;
        public final Map<String, Object> params;
        public final Date timestamp;

        private static final long serialVersionUID = 1L;

        public PeerInfoImpl(String network, String id, String name, String summary, String details, Map<String, Object> params) {
            this(Collections.singletonList(network), id, name, summary, details, params);
        }

        public PeerInfoImpl(List<String> networks, String id, String name, String summary, String details, Map<String, Object> params) {
            this.id = id;
            this.name = name;
            this.summary = summary;
            this.details = details;
            this.timeout = false;
            this.params = Collections.unmodifiableMap(params);
            this.timestamp = Calendar.getInstance().getTime();
            this.networks = Collections.unmodifiableList(networks);
        }

        public PeerInfoImpl(Date timeout, List<String> networks, String id, String name, String summary, String details, Map<String, Object> params) {
            this.id = id;
            this.name = name;
            this.summary = summary;
            this.details = details;
            this.timeout = true;
            this.params = Collections.unmodifiableMap(params);
            this.timestamp = timeout;
            this.networks = Collections.unmodifiableList(networks);
        }

        public List<String> getNetworks() {
            return networks;
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

        public Map<String, Object> getParams() {
            return params;
        }

        public Date getTimestamp() {
            return timestamp;
        }

        @Override
        public PeerInfo alive() {
            return alive(name);
        }

        @Override
        public PeerInfo alive(String name) {
            PeerInfoImpl info = new PeerInfoImpl(networks, id, name, summary, details, params);
            mPeerCache.put(info.id, info);
            return info;
        }

        @Override
        public PeerInfo timeout() {
            PeerInfoImpl info = new PeerInfoImpl(timestamp, networks, id, name, summary, details, params );
            mPeerCache.put(info.id, info);
            return info;
        }

        @Override
        public PeerInfo update(String name, String summary, String details, Map<String, Object> params) {
            PeerInfoImpl info = new PeerInfoImpl(networks, id, name, summary, details, params);
            mPeerCache.put(info.id, info);
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
    public static final class LoadPeerInfoCache extends P2PTask<List<PeerInfoImpl>> {

        public LoadPeerInfoCache(String root) {
            super(root);
        }

        @Override
        protected List<PeerInfoImpl> doInBackground() {
            return P2PUtils.readList(mRoot, P2P.FILE_PEERINFO_LIST, PeerInfoImpl.class);
        }

        @Override
        protected void onFinished(List<PeerInfoImpl> result) {
            for(PeerInfoImpl info : result) {
                if(!mPeerCache.containsKey(info.id)) {
                    mPeerCache.put(info.id, info);
                }
            }
        }
    }

    /**
     * Store PeerInfo cache
     */
    public static final class StorePeerInfoCache extends P2PTask<Void> {

        public StorePeerInfoCache(String root) {
            super(root);
        }

        @Override
        protected Void doInBackground() {
            P2PUtils.writeObject(mRoot, P2P.FILE_PEERINFO_LIST,
                    new ArrayList<>(mPeerCache.values()));
            return null;
        }

        @Override
        protected void onFinished(Void result) {
            Log.i(TAG, "Stored peerinfo list");
        }

    }
}
