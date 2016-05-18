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

import android.app.Application;
import android.support.annotation.CallSuper;
import android.util.Log;

import org.discoos.p2p.internal.P2PContext;

/**
 * P2PApplication is instantiated when the process for it is created by Android.
 * It is never tear down by Android, nor does Android provide any way for it to tear down it
 * self. Thus, the lifecycle of P2PApplication is the same as the process that created it.
 * The only way to tear down P2PApplication is by killing the Android process. This property
 * makes it suitable to maintain global application state, like the state of P2PService.
 *
 */
public class P2PApplication extends Application {

    private static final String TAG = "P2PApplication";

    /**
     * Reference to singleton instance
     */
    public static P2PApplication INSTANCE;

    /**
     * P2P context singleton
     */
    private final P2PContext mContext = P2PContext.getInstance();

    /**
     * Default constructor. Singleton, initializes INSTANCE member
     */
    public P2PApplication() {
        INSTANCE = this;
    }

    /**
     * Get singleton instance
     * @return P2PApplication
     */
    public static P2PApplication getInstance() {
        return INSTANCE;
    }

    /**
     * When created, the application fires an intent to create a P2PService instance.
     * @see P2PContext#ensure()
     * @see P2PContext#quit()
     */
    @CallSuper
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "onCreate()");

        mContext.onCreate();

    }

}
