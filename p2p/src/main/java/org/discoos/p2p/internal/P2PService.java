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

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.alljoyn.bus.alljoyn.DaemonInit;
import org.discoos.p2p.P2P;
import org.discoos.p2p.PeerInfo;
import org.discoos.p2p.R;
import org.discoos.p2p.activity.PeerListActivity;
import org.discoos.p2p.activity.QuitActivity;
import org.discoos.signal.Event;
import org.discoos.signal.Observer;

/**
 * P2P service class. Runs in main thread, same as P2PApplication and Activities. Long running
 * task are enqueued for processing on another thread using P2PHandler.
 */
public final class P2PService extends Service {

    private static final String TAG = "P2PService";

    private static final int NOTIFICATION_ID = 1;

    private static final String PARAM_NETWORK_NAME = "param.bus.name";

    public static final String ACTION_JOIN = "action.join";
    public static final String ACTION_LEAVE = "action.leave";
    public static final String ACTION_LEAVE_ALL = "action.leave.all";

    /**
     * We don't use the bindery to communicate between any client and this
     * service so we return null.
     */
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind()");
        return null;
    }

    /**
     * P2PHandler instance. When Android decides that this service is no longer needed, it will
     * call onDestroy(), which spins down this handlers looper thread.
     */
    private P2PHandler mHandler = new P2PHandler();

    /**
     * P2PApplication observer
     */
    private Observer mObserver = null;

    /**
     * SharedPreferences instance for P2PApplication context
     */
    private SharedPreferences mPreferences;

    /**
     * Our onCreate() method is called by the Android appliation framework
     * when the service is first created.  We spin up a background thread
     * to handle any long-lived requests (pretty much all AllJoyn calls that
     * involve communication with remote processes) that need to be done and
     * insinuate ourselves into the list of observers of the model so we can
     * get event notifications.
     */
    public void onCreate() {

        Log.i(TAG, "onCreate()");

        DaemonInit.PrepareDaemon(getApplicationContext());

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mObserver = createObserver();
        P2P.getDispatcher().add(mObserver);

        /**
         * Initialize P2P bus handler
         */
        mHandler.init();

        /*
         * Start the service in the foreground to keep it and the
         * associated P2PHandler thread alive as long as possible
          */
        Notification notification = createNotification("P2P Proximity Network Test", true);
        startForeground(NOTIFICATION_ID, notification);
    }

    /**
     * Helper method for joining a proximity network
     * @param name P2P network name
     */
    public static ComponentName join(String name) {
        Intent starter = new Intent(P2P.getApplication(), P2PService.class);
        starter.putExtra(PARAM_NETWORK_NAME, name);
        starter.setAction(ACTION_JOIN);
        return P2P.getApplication().startService(starter);
    }

    /**
     * Helper method for leaving a proximity network
     * @param name P2P network name
     */
    public static ComponentName leave(String name) {
        Intent starter = new Intent(P2P.getApplication(), P2PService.class);
        starter.putExtra(PARAM_NETWORK_NAME, name);
        starter.setAction(ACTION_LEAVE);
        return P2P.getApplication().startService(starter);
    }

    /**
     * Helper method for leaving all proximity networks
     */
    public static ComponentName leaveAll() {
        Intent starter = new Intent(P2P.getApplication(), P2PService.class);
        starter.setAction(ACTION_LEAVE_ALL);
        return P2P.getApplication().startService(starter);
    }

    /**
     * The method onStartCommand() is called by the Android application
     * framework each time a client explicitly starts this service by calling
     * startService().
     *
     * We return START_STICKY to enable us to be explicitly started and stopped
     * which means that our Service will essentially run "forever" (or until
     * Android decides that we should die for resource management issues) since
     * our Application class is left running as long as the process is left running.
     */
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand()");

        if(intent !=null) {

            if (ACTION_JOIN.equals(intent.getAction())) {
                String name = intent.getStringExtra(PARAM_NETWORK_NAME);
                onJoin(name);
            }
            else if (ACTION_LEAVE.equals(intent.getAction())) {
                String name = intent.getStringExtra(PARAM_NETWORK_NAME);
                onLeave(name);
            }else if (ACTION_LEAVE_ALL.equals(intent.getAction())) {
                onLeaveAll();
            }
        }

        return START_STICKY;
    }

    private boolean onJoin(String name) {

        Log.i(TAG, String.format("onJoin(): network=%s", name));

        /** Forward action to handler */
        if(!mHandler.join(name)){
            Log.e(TAG, String.format("Failed to join network %s", name));
            return false;
        }
        return true;
    }

    private Observer createObserver() {
        return new Observer() {
            @Override
            public void handle(Object signal, Object observable) {
                try {
                    switch ((int) signal) {
                        case P2P.BROADCAST:
                            mHandler.broadcast((Event)observable);
                            break;
                        case P2P.CANCEL:
                            mHandler.cancel((Event)observable);
                            break;
                        case P2P.PING:
                            int timeout = Integer.parseInt(mPreferences.getString("ping_timeout", "60"));
                            mHandler.ping((PeerInfo) observable, timeout * 1000);
                            break;
                        case P2P.NOTIFY:
                            if (mPreferences.getBoolean("notifications_peer", false)) {
                                Notification notification = createNotification((String) observable, false);
                                startForeground(NOTIFICATION_ID, notification);
                            }
                            break;
                        case P2P.QUIT:
                            /* The Android application framework will invoke #onDestroy() */
                            stopSelf();
                            break;
                    }
                } catch (Exception e) {
                    String msg = String.format("Failed to handle signal %s", signal);
                    Log.e(TAG, msg, e);
                }
            }
        };
    }

    @NonNull
    private Notification createNotification(String message, boolean init) {

        Intent intent = new Intent(this, PeerListActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(P2PService.this);
        builder.setSmallIcon(R.drawable.icon);
        builder.setWhen(System.currentTimeMillis());
        builder.setContentTitle("P2PApplication");
        builder.setContentText(message);
        builder.setContentIntent(pendingIntent);

        if(!init) {

            int flags = Notification.DEFAULT_LIGHTS;

            if(mPreferences.getBoolean("notifications_peer_ringtone", false)) {
                flags |= Notification.DEFAULT_SOUND;
                Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                builder.setSound(alarmSound);
            }
            if(mPreferences.getBoolean("notifications_peer_vibrate", false)) {
                flags |= Notification.DEFAULT_VIBRATE;
            }

            builder.setDefaults(flags);
        }

        intent = new Intent(this, QuitActivity.class);
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        NotificationCompat.Action.Builder action =
                new NotificationCompat.Action.Builder(0, "Quit", pendingIntent);
        builder.addAction(action.build());

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        return notification;
    }

    /**
     * Leave P2P network
     */
    private boolean onLeave(String name) {

        Log.i(TAG, String.format("onLeave(): network=%s", name));

        /** Forward action to handler */
        if(!mHandler.leave(name)){
            Log.e(TAG, String.format("Failed to leave network %s", name));
            return false;
        }
        return true;
    }

    /**
     * Leave all P2P networks
     */
    private int onLeaveAll() {
        int count = 0;
        for(String name : P2P.getNetworkNames()) {
            if(onLeave(name)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Our onDestroy() is called by the Android application framework when it
     * decides that our Service is no longer needed.  We tell our background
     * thread to exit andremove ourselves from the list of observers of the
     * model.
     */
    public void onDestroy() {

        Log.i(TAG, "onDestroy()");

        /**
         * When Android decides that our Service is no longer needed, we need to
         * leave all networks and the associated background thread that serves them.
         */
        mHandler.quit();

        /**
         * Stop receiving events from dispatcher and release reference to P2PApplication
         */
        P2P.getDispatcher().remove(mObserver);

        /* Release references*/
        mObserver = null;
        mPreferences = null;
    }


}
