package org.discoos.p2p.internal;

import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.annotation.BusInterface;
import org.alljoyn.bus.annotation.BusSignal;

@BusInterface(name = P2PBusEndpoint.NAME, announced="true")
public interface P2PBusEndpoint  extends BusObject {

    String NAME = "org.discoos.p2p.bus";

    String ALIVE_SIGNAL = "alive";
    String ALIVE_RULE = "member='alive',sessionless='t'";

    String LEFT_SIGNAL = "left";
    String LEFT_RULE = "member='left',sessionless='t'";

    /**
     * Invoked each time peer reconnect to WIFI
     *
     * @param appId Application id
     * @param uniqueName Unique name of bus which peer is attached
     */
    @BusSignal(name = ALIVE_SIGNAL, sessionless = true)
    void alive(byte[] appId, String uniqueName) throws BusException;

    /**
     * Invoked when peer intentionally leave a network or the bus altogether
     * @param appId Peer's application instance id
     * @param networks Peer's networks
     * @throws BusException
     * @see org.discoos.p2p.P2PUtils#toShortId(byte[])
     */
    @BusSignal(name = LEFT_SIGNAL, sessionless = true)
    void left(byte[] appId, String... networks) throws BusException;


}
