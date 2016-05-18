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

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.ContactsContract;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import org.alljoyn.bus.Variant;
import org.discoos.p2p.internal.P2PAboutData;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

/**
 * P2P utilities
 */
public class P2PUtils {

    public static final int SHORT_LENGTH = 5;

    /**
     * Android threadtime log format
     */
    public static final String LOG_PATTERN
            = "(\\d{2}-\\d{2})\\s*(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s*(\\d*)\\s*(\\d*)\\s*(\\w)\\s*(\\w+):\\s*(.*)";

    public static final SimpleDateFormat LOG_DATE_FORMAT =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
    public static final String TAG = "P2PUtils";

    public static byte[] randomUUID() {
        UUID uuid = UUID.randomUUID();
        long hi = uuid.getMostSignificantBits();
        long lo = uuid.getLeastSignificantBits();
        return ByteBuffer.allocate(16).putLong(hi).putLong(lo).array();
    }

    public static UUID toUUID(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long firstLong = bb.getLong();
        long secondLong = bb.getLong();
        return new UUID(firstLong, secondLong);
    }

    public static String toShortId(Map<String, Variant> data) {
        byte[] id = (byte[])toObject(P2PAboutData.APP_ID, data);
        return toShortId(id);
    }

    public static String toUniqueName(Map<String, Variant> data) {
        return (String)toObject(P2PAboutData.BUS_UNIQUE_NAME, data);
    }

    public static String toShortId(byte[] id) {
        String uuid = P2PUtils.toUUID(id).toString().replaceAll("-", "");
        return uuid.substring(Math.max(0, uuid.length() - SHORT_LENGTH));
    }

    public static Object toObject(String key, Map<String, Variant> data) {
        Variant value = data.get(key);
        if(value !=null ) {
            try {
                if ("s".equals(value.getSignature())) {
                    return value.getObject(String.class);
                } else if ("ay".equals(value.getSignature())) {
                    return value.getObject(byte[].class);
                } else if ("as".equals(value.getSignature())) {
                    return value.getObject(String[].class);
                }
                return value.toString() + ", " + value.getSignature();

            } catch (Exception e) {
                return e.toString();
            }
        }
        return null;
    }

    public static String toSummary(Map<String, Variant> data) {
        StringBuilder builder = new StringBuilder();
        builder.append(toObject(P2PAboutData.DEVICE_BRAND, data));
        builder.append(" ");
        builder.append(toObject(P2PAboutData.MODEL_NUMBER, data));
        builder.append(" @ ");
        builder.append(toObject(P2PAboutData.INET_4_ADDRESS, data));
        Object value = toObject(P2PAboutData.USER_NAME, data);
        if(value != null) {
            builder.append(" ");
            builder.append(value);
        }
        return builder.toString();
    }


    public static String toDetails(Map<String, Variant> data) {
        StringBuilder builder = new StringBuilder();
        builder.append("AboutData details:");
        for(String key : data.keySet()) {
            builder.append("\n");
            builder.append(toDetail(key, data));
        }
        return builder.toString();
    }

    public static String toDetail(String key, Map<String, Variant> data) {
        Object value = toObject(key, data);
        if(value instanceof Object[]) {
            value = Arrays.toString((Object[]) value);
        }
        return " * " + key + ": " + value;
    }

    public static Map<String, Object> toParams(Map<String, Variant> data) {
        Map<String, Object> params = new HashMap<>();
        for(String key : data.keySet()) {
            params.put(key, toObject(key, data));
        }
        return params;
    }

    public static void alert(String title, String message, Activity activity) {
        alert(title, message, activity, null);
    }

    public static void alert(String title, String message, Activity activity,
                             DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton("OK", listener);
        AlertDialog alert = builder.create();
        alert.show();
    }

    public static List<LogItem> readLog() {
        return readLog("");
    }

    public static List<LogItem> readLog(String query) {
        List<LogItem> log = new ArrayList<>();
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                "logcat", "-d", "-v", "threadtime"});

            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String[] filters = query.split("\\s");

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = Pattern.compile(LOG_PATTERN).matcher(line);
                if(matcher.matches()) {
                    if(query.isEmpty() || matches(filters, matcher)) {
                        log.add(LogItem.create(matcher));
                    }
                }
            }
        } catch (IOException e) {
            log.add(new LogItem(TAG, "E", e.toString()));
        }
        return log;
    }

    private static boolean matches(String[] filters, Matcher matcher) {
        for (String filter : filters) {
            // Is logcat filter?
            if(filter.matches("(\\w+|\\*):[\\w|\\*]")) {
                String[] matches = filter.split(":");
                // Tag and level match?
                if((matches[0].equals("*") || matches[0].equals(matcher.group(6))) &&
                   (matches[1].equals("*") || matches[1].equals(matcher.group(5)))) {
                    return true;
                }
            } else {
                Pattern pattern = Pattern.compile(".*"+Pattern.quote(filter)+".*"
                        , Pattern.CASE_INSENSITIVE);
                for(int i=1; i<8; i++) {
                    if(pattern.matcher(matcher.group(i)).matches()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void clearLog() {
        try {
            Runtime.getRuntime().exec(new String[]{"logcat", "-c"});
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }

    public static File writeLog(String root, String filename) {
        StringBuilder log = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec("logcat -d");
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                log.append(line).append("\n");
            }
        } catch (IOException e) {
            log.append(e.toString());
        }


        File file = new File(root, filename);

        try {
            FileOutputStream fOut = new FileOutputStream(file);
            OutputStreamWriter osw = new OutputStreamWriter(fOut);

            // Write the string to the file
            osw.write(log.toString());
            osw.flush();
            osw.close();

        } catch (Exception e) {
            Log.e(TAG, String.format("Failed to write log to [%s]", file), e);
        }
        return file;
    }

    public static P2PNetwork.Connectivity getConnectivityStatus(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        // Only accept connect networks
        if(activeNetwork !=null && activeNetwork.isConnected()) {
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI)
                return P2PNetwork.Connectivity.WIFI;

            if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE)
                return P2PNetwork.Connectivity.MOBILE;
        }
        return P2PNetwork.Connectivity.NONE;
    }

    /* Send signal as message to handler thread */
    public static boolean raise(int signal, Handler handler, Object value) {
        Message msg = handler.obtainMessage(signal);
        msg.obj = value;
        return handler.sendMessage(msg);
    }

    public static String getWifiIpv4() {

        WifiManager wifiManager = (WifiManager) P2P.getApplication().getSystemService(Context.WIFI_SERVICE);
        int ipv4 = wifiManager.getConnectionInfo().getIpAddress();

        return String.format(
                "%d.%d.%d.%d",
                (ipv4 & 0xff),
                (ipv4 >> 8 & 0xff),
                (ipv4 >> 16 & 0xff),
                (ipv4 >> 24 & 0xff)
        );
    }

    public static String getWifiIpv6() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet6Address) {
                        String[] ipv6 = inetAddress.getHostAddress().split("%");
                        String port = ipv6[1];
                        if(port.matches("wlan\\d+")) {
                            return ipv6[0];
                        }
                    }
                }
            }
        } catch (SocketException ex) { /*CONSUME*/ }
        return null;
    }

    public static Map<String, Variant> getOwnerInfo() {

        Map<String, Variant> info = new HashMap<>();

        if(isGranted(Manifest.permission.READ_CONTACTS)) {

            String[] columnNames = new String[]{ContactsContract.Profile.DISPLAY_NAME};

            ContentResolver resolver = P2P.getApplication().getContentResolver();
            Cursor cursor = resolver.query(ContactsContract.Profile.CONTENT_URI, columnNames, null, null, null);
            if (cursor != null) {
                int count = cursor.getCount();
                cursor.moveToFirst();
                int position = cursor.getPosition();
                if (count == 1 && position == 0) {
                    info.put(P2PAboutData.USER_NAME, new Variant(cursor.getString(0)));
                }
                cursor.close();
            }
        }
        return info;
    }

    public static boolean isGranted(String access) {
        return PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
                P2P.getApplication(), access);
    }

    /* Send signal as message to handler thread */
    public static boolean raise(int signal, Handler handler) {
        return raise(signal, handler, null);
    }

    public static final class LogItem {

        public final Date timestamp = Calendar.getInstance().getTime();
        public final String thread;
        public final String module;
        public final String level;
        public final String message;

        public LogItem(String module, String level, String message) {
            Looper looper = Looper.myLooper();
            this.thread = looper != null ? looper.getThread().getName() : "unknown";
            this.module = module;
            this.level = level;
            this.message = message;
        }
        public LogItem(Date timestamp, String thread, String module, String level, String message) {
            this.timestamp.setTime(timestamp.getTime());
            this.thread = thread;
            this.module = module;
            this.level = level;
            this.message = message;
        }

        private static LogItem create(Matcher matcher) {
            Date timestamp;
            try {
                timestamp = LOG_DATE_FORMAT.parse(matcher.group(2));
            } catch (ParseException e) {
                timestamp = Calendar.getInstance().getTime();
            }
            return new LogItem(
                    timestamp,
                    matcher.group(4),   // Thread
                    matcher.group(6),   // Module
                    matcher.group(5),   // Log level
                    matcher.group(7));  // Message
        }

    }

    public static <T> List<T> readList(String root, String filename, Class<T> type) {
        List<T> objects = new ArrayList<>();
        File file = new File(root, filename);
        try {
            if(file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                ObjectInputStream ois = new ObjectInputStream(fis);
                Object object = ois.readObject();
                if (object instanceof List) {
                    for (Object it : (List) object) {
                        objects.add(type.cast(it));
                    }
                }
                ois.close();
                return Collections.unmodifiableList(objects);
            }
        } catch (Exception e) {
            Log.e(TAG, String.format("Failed to read [%s]", file), e);
        }
        return Collections.emptyList();
    }

    public static void writeObject(String root, String filename, Object object) {
        File file = new File(root, filename);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(object);
            oos.flush();
            oos.close();
        } catch (Exception e) {
            Log.e(TAG, String.format("Failed to write [%s]", file), e);
        }
    }


    public static boolean contains(String[] values, String match) {
        for (String value : values) {
            if (value.equals(match)) {
                return true;
            }
        }
        return false;
    }

}
