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

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P2P asynchronous task manager
 */
public class P2PTaskManager {

    public static final String TAG = "P2PTaskManager";

    public static final String ALL = "";

    /**
     * Reference to singleton instance
     */
    public static P2PTaskManager INSTANCE;

    /**
     * Handler on main thread. Executes periodic task.
     */
    private final Handler mHandler = new Handler();

    /**
     * Map of scheduled task groups scheduled for execution.
     */
    private final Map<String, Runnable> mGroupMap = new HashMap<>();

    /**
     * Map of tasks scheduled for execution.
     */
    private final Map<P2PTask, String> mScheduleMap = new HashMap<>();

    /**
     * Default constructor. Singleton, initializes INSTANCE member.
     */
    P2PTaskManager() {
        INSTANCE = this;
    }

    /**
     * Get singleton instance
     * @return P2PTaskManager
     */
    public static P2PTaskManager getInstance() {
        if(INSTANCE == null) {
            INSTANCE = new P2PTaskManager();
        }
        return INSTANCE;
    }

    /**
     * Execute task asynchronous
     * @param task P2P tasks
     */
    public AsyncTask execute(P2PTask task) {
        return execute(ALL, task);
    }

    /**
     * Execute tasks asynchronously
     * @param tasks P2P tasks
     */
    public List<AsyncTask> execute(P2PTask... tasks) {
        List<AsyncTask> asyncTasks = new ArrayList<>();
        for(P2PTask task : tasks) {
            AsyncTask asyncTask = execute(task);
            if( asyncTask != null ) {
                asyncTasks.add(asyncTask);
            }
        }
        return asyncTasks;
    }

    /**
     * Execute all task in group asynchronously
     * @param group Task execution group
     * @return List of AsyncTask instances
     */
    @SuppressWarnings(value = "unchecked")
    public List<AsyncTask> execute(String group) {
        List<AsyncTask> tasks = new ArrayList<>();
        /** Select all tasks in group */
        for(Map.Entry<P2PTask, String> it : mScheduleMap.entrySet()) {
            if(group.equals(it.getValue())) {
                tasks.add(newAsyncTask(it.getKey()).execute());
            }
        }
        return tasks;
    }

    /**
     * Execute task after given milliseconds
     * @param task P2PTask
     * @param millis Delay this execution
     */
    @SuppressWarnings(value = "unchecked")
    public boolean schedule(final P2PTask task, int millis) {
        boolean scheduled = schedule(ALL, task);
        if(scheduled) {
            final AsyncTask executor = newAsyncTask(task);
            scheduled = mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    executor.execute();
                }
            }, millis);
        }
        return scheduled;
    }

    /**
     * Execute task in execution group after given milliseconds.
     * @param group Task execution group
     * @param task P2PTask instance
     * @param millis Delay this execution
     * @return boolean
     */

    public boolean schedule(String group, P2PTask task, int millis) {
        boolean scheduled = schedule(group, task);
        if(scheduled) {
            Runnable runnable = mGroupMap.get(group);
            if(runnable != null) {
                mHandler.removeCallbacks(runnable);
            }
            final List<AsyncTask> tasks = newAsyncTasks(group);
            runnable = new Runnable() {
                @Override
                public void run() {
                    for(AsyncTask task : tasks) {
                        task.execute();
                    }
                }
            };
            mGroupMap.put(group, runnable);
            mHandler.postDelayed(runnable, millis);
        }
        return scheduled;
    }

    /**
     * Schedule execution of task after given milliseconds.
     * @param group P2PTask group
     * @param millis Delay execution (milliseconds)
     * @param tasks P2PTask tasks
     */
    public int schedule(String group, int millis, P2PTask... tasks) {
        int count = 0;
        for(P2PTask task : tasks) {
            if(schedule(group, task, millis)) count++;
        }
        return count;
    }

    /**
     * Execute task in loop
     * @param task P2PTask
     * @param delay Delay each execution (milliseconds)
     */
    public void loop(final P2PTask task, final int delay) {
        loop(task, delay, delay);
    }

    /**
     * Execute task in loop
     * @param task P2PTask
     * @param first Delay first execution (milliseconds)
     * @param next Delay next execution (milliseconds)
     */
    public void loop(final P2PTask task, final int first, final int next) {

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                execute(task);
                mHandler.postDelayed(this, next);
            }
        }, first);
    }

    private boolean schedule(String group, P2PTask task) {
        if(mScheduleMap.containsKey(task)) {
            String msg = "P2PTask [%s] already scheduled";
            Log.d(TAG, String.format(msg, task));
            return false;
        }
        mScheduleMap.put(task, group);
        return true;
    }

    /**
     * Execute asynchronous all tasks in group
     * @param group Task execution group
     * @param task P2P tasks
     */
    @SuppressWarnings(value = "unchecked")
    private AsyncTask execute(String group, P2PTask task) {
        if(schedule(group, task)) {
            return newAsyncTask(task).execute();
        }
        return null;
    }

    private List<AsyncTask> newAsyncTasks(String group) {
        List<AsyncTask> tasks = new ArrayList<>();
        /** Select all tasks in group */
        for(Map.Entry<P2PTask, String> it : mScheduleMap.entrySet()) {
            if(group.equals(it.getValue())) {
                tasks.add(newAsyncTask(it.getKey()));
            }
        }
        return tasks;
    }

    @SuppressWarnings(value = "unchecked")
    private AsyncTask newAsyncTask(final P2PTask task) {
        return new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] params) {
                try {
                    task.mExecutor = this;
                    return task.doInBackground();
                } catch (Exception e) {
                    task.mExecutor = null;
                    String msg = "Failed to invoke P2PTask::doInBackground for task %s";
                    Log.e(TAG, String.format(msg, task), e);
                }
                return null;
            }

            @Override
            protected void onCancelled(Object o) {
                try {
                    task.onCancelled(o);
                } catch (Exception e) {
                    String msg = "Failed to invoke P2PTask::onCancelled for %s with result %s";
                    Log.e(TAG, String.format(msg, task, o), e);
                } finally {
                    task.mExecutor = null;
                    mScheduleMap.remove(task);
                }
            }

            @Override
            protected void onPostExecute(Object o) {
                try {
                    task.onFinished(o);
                } catch (Exception e) {
                    String msg = "Failed to invoke P2PTask::onFinished for %s with result %s";
                    Log.e(TAG, String.format(msg, task, o), e);
                } finally {
                    task.mExecutor = null;
                    mScheduleMap.remove(task);
                }
            }
        };
    }
}
