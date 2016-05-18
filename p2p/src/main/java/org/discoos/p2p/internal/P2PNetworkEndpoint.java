package org.discoos.p2p.internal;

import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.annotation.BusInterface;

@BusInterface(name = P2PNetworkEndpoint.NAME, announced="true")
public interface P2PNetworkEndpoint extends BusObject {

    String NAME = "org.discoos.p2p.network";

}
