package org.discoos.p2p.internal;

import org.alljoyn.bus.BusException;
import org.alljoyn.bus.annotation.BusInterface;
import org.alljoyn.bus.annotation.BusSignal;

@BusInterface(name = P2PNetworkObject.NAME, announced="true")
public interface P2PNetworkObject {

    String NAME = "org.discoos.p2p.network";
    String ALIVE_SIGNAL = "alive";
    String ALIVE_RULE = "member='alive',path='%s',sessionless='t'";

    /**
     * Network name
     * @return String
     */
    String getName();

    /**
     * Network path
     * @return String
     */
    String getPath();

    /**
     * Invoked
     * <ol>
     *  <li>each time peer reconnect to P2PNetwork with WIFI (network state changed to {@link P2PNetworkImpl.Connectivity#WIFI})</li>
     * </ol>
     * @param appId Application id
     * @param uniqueName Name of bus which peer is attached
     */
    @BusSignal(name = ALIVE_SIGNAL, sessionless = true)
    void alive(byte[] appId, String uniqueName) throws BusException;

}
