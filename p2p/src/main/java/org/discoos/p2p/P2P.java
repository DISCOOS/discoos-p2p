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

import org.alljoyn.bus.BusAttachment;
import org.discoos.p2p.PeerInfo;
import org.discoos.signal.Dispatcher;

import java.io.File;
import java.util.List;

public class P2P {

    /* Quit signal */
    public static final int QUIT = 0;

    /* Initialization signal */
    public static final int INIT = 1;

    /* Join P2P network */
    public static final int JOIN = 2;

    /* Leave P2P network */
    public static final int LEAVE = 3;

    /* Broadcast signal to P2P network */
    public static final int BROADCAST = 4;

    /* Cancel broadcast signal to P2P network */
    public static final int CANCEL = 5;

    /* Peer joined network */
    public static final int JOINED = 6;

    /* Peer left network */
    public static final int TIMEOUT = 7;

    /* Network changed */
    public static final int CHANGED = 8;

    /* Ping Peer */
    public static final int PING = 9;

    /* Peer is alive */
    public static final int ALIVE = 10;

    /* Set notification */
    public static final int NOTIFY = 100;

    /* Network warning */
    public static final int WARNING = -254;

    /* Network error */
    public static final int ERROR = -255;


    public static final int LOADER_SYSTEM_LOG_ID = 1;
    public static final String FILE_NETWORK_LIST = "network.list";
    public static final String FILE_PEERINFO_LIST = "peerinfo.list";

    /**
     * Get singleton instance
     * @return P2PApplication
     */
    public static P2PApplication getApplication() {
        return P2PApplication.getInstance();
    }

    /**
     * Get signal dispatcher
     * @return Dispatcher
     */
    public static Dispatcher getDispatcher() {
        return P2PApplication.getInstance().getDispatcher();
    }

    /**
     * Get P2P network from name
     * @return P2PNetwork
     */
    public static P2PNetwork getNetwork(String name) {
        return P2PApplication.getInstance().getNetwork(name);
    }

    /**
     * Get unmodifiable list of network names
     * @return Set
     */
    public static List<String> getNetworkNames() {
        return P2PApplication.getInstance().getNetworkNames();
    }

    /**
     * Get unmodifiable peer from id
     * @param id Peer id
     * @return PeerInfo
     */
    public static PeerInfo getPeer(String id) {
        return P2PApplication.getInstance().getPeer(id);
    }

    /**
     * Get unmodifiable list of all peer ids
     * @return Set
     */
    public static List<String> getPeerIds() {
        return P2PApplication.getInstance().getPeerIds();
    }

    /**
     * Get unmodifiable set of all peers
     * @return Set
     */
    public static List<PeerInfo> getPeerList() {
        return P2PApplication.getInstance().getPeerList();
    }

    /**
     * Get application cache directory
     * @return File
     */
    public static File getCacheDir() {
        return getApplication().getExternalCacheDir();
    }

}
