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
package org.discoos.signal;

import android.os.Handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Dispatcher {

    private final Handler mHandler = new Handler();

    private Map<Object, List<Observer>> mObserverMap = new HashMap<>();

    private static Object ALL = new String();

    public Dispatcher() {
        mObserverMap.put(ALL, new ArrayList<Observer>());
    }

    public Dispatcher add(Observer observer) {
        return this.add(ALL, observer);
    }

    public Dispatcher add(Object signal, Observer observer) {
        if (!mObserverMap.containsKey(signal)) {
            mObserverMap.put(signal, new ArrayList<Observer>());
        }
        mObserverMap.get(signal).add(observer);
        return this;
    }

    public Dispatcher remove(Observer observer) { return this.remove(ALL, observer); }

    public Dispatcher remove(Object signal, Observer observer) {
        if (mObserverMap.containsKey(signal)) {
            mObserverMap.get(signal).remove(observer);
        }
        return this;
    }

    public Dispatcher removeAll(Observer observer) {
        for(Object signal : mObserverMap.keySet()) {
            mObserverMap.get(signal).remove(observer);
        }
        return this;
    }



    public Dispatcher schedule(final Object signal, final Object observable) {
        // Post scheduled event for processing on current thread
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                raise(signal, observable);
            }
        });
        return this;
    }



    public void raise(Object signal, Object observable) {
        List<Observer> notified = mObserverMap.get(ALL);
        if (!notified.isEmpty()) {
            for (Observer observer : notified) {
                observer.handle(signal, observable);

            }
        }
        if (mObserverMap.containsKey(signal)) {
            for (Observer observer : mObserverMap.get(signal)) {
                if(!notified.contains(observer)) {
                    observer.handle(signal, observable);
                }
            }
        }

    }
    
}
