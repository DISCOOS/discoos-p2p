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

import android.os.Looper;
import android.util.Log;

import org.discoos.p2p.P2P;
import org.discoos.p2p.P2PNetwork;
import org.discoos.p2p.PeerInfo;
import org.discoos.p2p.internal.PeerInfoCache.PeerInfoImpl;
import org.discoos.signal.Dispatcher;
import org.discoos.signal.Event;
import org.discoos.signal.Observer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class for providing peers to list views
 */
public class P2PNetworkImpl implements P2PNetwork, Serializable {

    private static final String TAG = "P2PNetworkImpl";

    static final long serialVersionUID = 1L;

    final String mName;

    String mLabel;

    private transient Dispatcher mDispatcher;

    private transient Set<Observer> mObservers;

    /**
     * A array of peer ids.
     */
    private final List<String> mPeerList = new ArrayList<>();

    /**
     * Constructor
     * @param name Network name
     */
    P2PNetworkImpl(String name, String label) {
        mName = name;
        mLabel = label;
    }

    /**
     * Register signal handles
     */
    P2PNetworkImpl init() {
        if(mDispatcher == null) {
            mDispatcher = P2P.getDispatcher();
            mDispatcher.add(P2P.INIT, register(new Observer() {
                public void handle(Object signal, Object observable) {
                    if(mName.equals(observable)) {
                        int count = clear();
                        log("INIT", "peers=" + count);
                        mDispatcher.schedule(P2P.CHANGED, new Event(P2P.INIT, this, count));
                    }
                }
            })).add(P2P.ANNOUNCED, register(new Observer() {
                public void handle(Object signal, Object observable) {
                    PeerInfoImpl info = add(observable);
                    if(info != null) {
                        log("JOINED", info);
                        mDispatcher.schedule(P2P.CHANGED, new Event(P2P.ADDED, this, info));
                    }
                }
            })).add(P2P.ALIVE, register(new Observer() {
                @Override
                public void handle(Object signal, Object observable) {
                    PeerInfoImpl info = add(observable);
                    if(info != null) {
                        log("ALIVE_EVENT", info);
                        mDispatcher.schedule(P2P.CHANGED, new Event(P2P.ADDED, this, info));
                    }
                }
            })).add(P2P.LEFT, register(new Observer() {
                @Override
                public void handle(Object signal, Object observable) {
                    PeerInfoImpl info = remove(observable);
                    if(info != null) {
                        log("LEFT", info);
                        mDispatcher.schedule(P2P.CHANGED, new Event(P2P.REMOVED, this, info));
                    }
                }
            }));
            int count = 0;
            for(PeerInfo it : P2P.getPeerList()) {
                PeerInfoImpl info = add(it);
                if(info != null) {
                    log("JOINED", info);
                    count++;
                }
                if(count > 0) {
                    mDispatcher.schedule(P2P.CHANGED, new Event(P2P.INIT, this, count));
                }
            }
        }
        return this;
    }

    private Observer register(Observer observer) {
        if(mObservers == null) {
            mObservers  = new HashSet<>();
        }
        mObservers.add(observer);
        return observer;
    }

    void release() {
        if(mDispatcher != null) {
            for (Observer it : mObservers) {
                mDispatcher.removeAll(it);
            }
        }
        mObservers.clear();
        mDispatcher = null;
    }

    @Override
    public String getName() {
        return mName;
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
        return PeerInfoCache.getInstance().get(id);
    }

    @Override
    public PeerInfo getPeer(int position) {
        return PeerInfoCache.getInstance().get(mPeerList.get(position));
    }

    @Override
    public Set<String> getPeerIds() {
        return Collections.unmodifiableSet(new HashSet<>(mPeerList));
    }


    @Override
    public List<PeerInfo> getPeerList() {
        List<PeerInfo> items = new ArrayList<>();
        synchronized (PeerInfoCache.class) {
            for(String id : mPeerList) {
                items.add(PeerInfoCache.getInstance().get(id));
            }
        }
        return Collections.unmodifiableList(items);
    }

    PeerInfoImpl add(Object observable) {
        PeerInfoImpl info = (PeerInfoImpl) observable;
        if(info != null && info.isMemberOf(mName)) {
            if (!mPeerList.contains(info.id)) {
                P2PNetworkCache.getInstance().onNetworkChanged();
                mPeerList.add(info.id);
                return info;
            }
        }
        return null;
    }

    PeerInfoImpl remove(Object observable) {
        PeerInfoImpl info = (PeerInfoImpl) observable;
        if(info != null && !info.isMemberOf(mName)) {
            return remove(info.id);
        }
        return null;
    }

    PeerInfoImpl remove(String id) {
        synchronized (PeerInfoCache.class) {
            if(mPeerList.remove(id)) {
                P2PNetworkCache.getInstance().onNetworkChanged();
                return PeerInfoCache.getInstance().get(id);
            }
        }
        return null;
    }

    private int clear() {
        int count = mPeerList.size();
        mPeerList.clear();
        P2PNetworkCache.getInstance().onNetworkChanged();
        return count;
    }

    private static void log(String signal, String msg) {
        assert Looper.getMainLooper().equals(Looper.myLooper())
                : String.format("Not on main looper: %s",Looper.myLooper());
        Log.i(TAG, String.format("%s: %s", signal, msg));
    }

    private void log(String signal, PeerInfo info) {
        log(signal, String.format("%s@%s",info.getId(), mName));
    }

}
