package org.discoos.p2p.internal;

import android.os.AsyncTask;
import android.support.annotation.CallSuper;

import java.util.Map;
import java.util.Set;

/**
 * Base class for asynchronous P2P task
 * @param <R> Result type
 */
public abstract class P2PTask<R>  extends AsyncTask<Object, Object, R> {

    protected final String mRoot;

    protected Set<AsyncTask> mTasks;

    public P2PTask(String root) {
        mRoot = root;
    }

    protected abstract R doInBackground();

    @Override
    protected final R doInBackground(Object[] params) {
        mTasks = (Set<AsyncTask>)params[0];
        return doInBackground();
    }

    @Override
    protected final void onPostExecute(R result) {
        mTasks.remove(this);
        onFinished(result);
    }

    protected void onFinished(R result) {

    }

}
