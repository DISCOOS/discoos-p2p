package org.discoos.p2p.internal;

import android.os.Message;

/**
 * Base class for P2P handles invoked by signals.
 *
 * @see P2PHandler
 */
class P2PHandle {

    protected int mSignal;

    public P2PHandle(int mSignal) {
        this.mSignal = mSignal;
    }

    public boolean accepts(int signal) {
        return (this.mSignal == signal);
    }

    public boolean execute(Message msg) {
        return true;
    }
}
