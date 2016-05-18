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

import android.os.Environment;
import android.support.annotation.Nullable;

import org.discoos.p2p.internal.P2PContext;
import org.discoos.p2p.internal.P2PNetworkCache;
import org.discoos.p2p.internal.P2PNetworkImpl;
import org.discoos.p2p.internal.PeerInfoCache;
import org.discoos.signal.Dispatcher;
import org.discoos.signal.Event;

import java.io.File;
import java.util.List;

public final class P2P {

    /* Quit signal */
    public static final int QUIT = 0;

    /* Initialization signal */
    public static final int INIT = 1;

    /* Join P2P network */
    public static final int JOIN = 2;

    /* Leave P2P network */
    public static final int LEAVE = 3;

    /* Broadcast signal */
    public static final int BROADCAST = 4;

    /* Cancel signal */
    public static final int CANCEL = 5;

    /* Peer announced signal */
    public static final int ANNOUNCED = 6;

    /* Peer alive signal */
    public static final int ALIVE = 7;

    /* Peer unresponsive */
    public static final int TIMEOUT = 8;

    /* Peer left network */
    public static final int LEFT = 9;

    /* Ping Peer */
    public static final int PING = 10;

    /* Data added */
    public static final int ADDED = 11;

    /* Data removed */
    public static final int REMOVED = 12;

    /* Data changed */
    public static final int CHANGED = 13;

    /* All flag */
    public static final int ALL = 14;

    /* Set notification */
    public static final int NOTIFY = 100;

    /* P2P warning */
    public static final int WARNING = -254;

    /* P2P error */
    public static final int ERROR = -255;

    /**
     * Default cache store delay (500 milliseconds)
     */
    public static final int CACHE_STORE_DELAY = 500;

    /**
     * System log loader id
     */
    public static final int LOADER_SYSTEM_LOG_ID = 1;

    /**
     * File name network list
     */
    public static final String FILE_NETWORK_LIST = "network.list";

    /**
     * File name peer info list
     */
    public static final String FILE_PEERINFO_LIST = "peerinfo.list";

    /**
     * Get P2P context (singleton)
     * @return P2PContext
     */
    public static P2PContext getContext() {
        return P2PContext.getInstance();
    }

    /**
     * Get P2P application (singleton)
     * @return P2PApplication
     */
    public static P2PApplication getApplication() {
        return P2PApplication.getInstance();
    }

    /**
     * Get signal dispatcher (global)
     * @return Dispatcher
     */
    public static Dispatcher getDispatcher() {
        return getContext().getDispatcher();
    }

    /**
     * Get P2P network from name
     * @return P2PNetwork
     */
    public static P2PNetwork getNetwork(String name) {
        return getContext().getNetwork(name);
    }

    /**
     * Get unmodifiable list of network names
     * @return Set
     */
    public static List<String> getNetworkNames() {
        return getContext().getNetworkNames();
    }

    /**
     * Get unmodifiable peer from id
     * @param id Peer id
     * @return PeerInfo
     */
    public static PeerInfo getPeer(String id) {
        return getContext().getPeer(id);
    }

    /**
     * Get unmodifiable list of all peer ids
     * @return Set
     */
    public static List<String> getPeerIds() {
        return getContext().getPeerIds();
    }

    /**
     * Get unmodifiable set of all peers
     * @return Set
     */
    public static List<? extends PeerInfo> getPeerList() {
        return getContext().getPeerList();
    }

    /**
     * Get application cache directory
     * @return File
     */
    public static File getCacheDir() {
        if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            return getApplication().getExternalCacheDir();
        }
        return getApplication().getCacheDir();
    }

    /**
     * Get application cache directory
     * @return File
     */
    public static File getFilesDir() {
        if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            return getApplication().getExternalFilesDir(null);
        }
        return getApplication().getFilesDir();
    }

    /**
     * Join P2P network
     *
     * @param name P2P network name (well-known bus name)
     * @param label P2P network label (used as title in list and detail views)
     *
     * @return P2PNetwork Network instance if joined, false otherwise.
     */
    @Nullable
    public static P2PNetwork join(String name, String label) {
        return getContext().join(name, label);
    }

    /**
     * Leave P2P network
     *
     * @param name P2P network name (well-known bus name)
     *
     * @return P2PNetwork Network instance if left, false otherwise.
     */
    @Nullable
    public static P2PNetwork leave(String name) {
        return getContext().leave(name);
    }

    /**
     * Leave all P2P networks
     *
     * @return boolean
     */
    @Nullable
    public static boolean leaveAll() {
        return getContext().leaveAll();
    }

    /**
     * Leave P2P network and remove from network list
     *
     * @param name P2P network name (well-known bus name)
     *
     * @return P2PNetwork Network instance if deleted, false otherwise.
     */
    @Nullable
    public static P2PNetwork delete(String name) {
        return getContext().delete(name);
    }

    /**
     * Leave all P2P networks and clear network list
     *
     * @return boolean
     */
    @Nullable
    public static boolean deleteAll() {
        return getContext().deleteAll();
    }

    /**
     * Check if given object represents an event
     * @param observable Observable
     * @return boolean
     */
    public static boolean isEvent(Object observable) {
        return observable instanceof Event;
    }

    /**
     * Check if given object represents a peer change
     * @param observable Observable event
     * @return boolean
     */
    public static boolean isPeerChange(Event observable) {
        return observable.getSource() instanceof PeerInfoCache;
    }

    /**
     * Check if given object represents a network change
     * @param observable Observable event
     * @return boolean
     */
    public static boolean isNetworkChange(Event observable) {
        return observable.getSource() instanceof P2PNetworkCache ||
                observable.getSource() instanceof P2PNetworkImpl ;
    }
}
