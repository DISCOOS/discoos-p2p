package org.discoos.p2p.internal;

import android.os.AsyncTask;

/**
 * Base class for asynchronous P2P task execution
 * @param <R> Result type
 */
public abstract class P2PTask<R>  {

    AsyncTask mExecutor;

    protected boolean isCancelled() {
        return mExecutor !=null && mExecutor.isCancelled();
    }

    protected abstract R doInBackground();

    protected void onCancelled(R result) {}

    protected void onFinished(R result) {}

}
