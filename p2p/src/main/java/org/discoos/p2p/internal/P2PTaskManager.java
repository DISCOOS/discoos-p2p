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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * P2P asynchronous task manager
 */
public class P2PTaskManager {

    /**
     * Reference to singleton instance
     */
    public static P2PTaskManager INSTANCE;

    /**
     * Handler on main thread. Executes periodic task.
     */
    private Handler mHandler = new Handler();


    /**
     * Set of scheduled asynchronous tasks.
     */
    private Set<AsyncTask> mScheduleSet = new HashSet<>();

    /**
     * Default constructor. Singleton, initializes INSTANCE member
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
     * Execute asynchronous P2P task
     * @param task P2P task
     */
    public void execute(P2PTask task) {
        if(mScheduleSet.contains(task)) {
            String msg = "AsyncTask [%s] already scheduled";
            throw new IllegalStateException(String.format(msg, task));
        }
        mScheduleSet.add(task);
        task.execute(mScheduleSet);
    }

    /**
     * Execute task after given milliseconds
     * @param task P2PTask
     * @param millis Delay this execution
     */

    public void delay(final P2PTask task, int millis) {

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                execute(task);
            }
        }, millis);
    }

    /**
     * Execute task in loop
     * @param task P2PTask
     * @param delay Delay each execution
     */

    public void loop(final P2PTask task, final int delay) {
        loop(task, delay, delay);
    }

    /**
     * Execute task in loop
     * @param task P2PTask
     * @param first Delay first execution
     * @param next Delay next execution
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


}
