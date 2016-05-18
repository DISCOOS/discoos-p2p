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

import android.os.Build;
import android.util.Log;

import org.alljoyn.bus.AboutDataListener;
import org.alljoyn.bus.ErrorReplyBusException;
import org.alljoyn.bus.Variant;
import org.alljoyn.bus.Version;
import org.discoos.p2p.P2P;
import org.discoos.p2p.P2PUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

/**
 * P2PAboutData contains all information which AboutDataListeners are required to implement
 */
public final class P2PAboutData implements AboutDataListener {

    private static final String TAG = "P2PAboutData";

    public static final String APP_ID = "AppId";
    public static final String APP_NAME = "AppName";
    public static final String DEFAULT_LANGUAGE = "DefaultLanguage";
    public static final String MODEL_NUMBER = "ModelNumber";
    public static final String SUPPORTED_LANGUAGES = "SupportedLanguages";
    public static final String SOFTWARE_VERSION = "SoftwareVersion";
    public static final String HARDWARE_VERSION = "HardwareVersion";
    public static final String AJ_SOFTWARE_VERSION = "AJSoftwareVersion";
    public static final String SUPPORT_URL = "SupportUrl";
    public static final String DEVICE_ID = "DeviceId";
    public static final String DEVICE_NAME = "DeviceName";
    public static final String DEVICE_BRAND = "DeviceBrand";
    public static final String MANUFACTURER = "Manufacturer";
    public static final String DESCRIPTION = "Description";
    public static final String INET_4_ADDRESS = "Inet4Address";
    public static final String INET_6_ADDRESS = "Inet6Address";
    public static final String BUS_UNIQUE_NAME = "BusUniqueName";
    public static final String USER_NAME= "UserName";


    static byte[] mAppId;

    /**
     * Bus attachment unique name
     */
    final String mUniqueName;

    /**
     * Constructor
     * @param uniqueName Bus attachment unique name
     */
    public P2PAboutData(String uniqueName) {
        mUniqueName = uniqueName;
    }

    @Override
    public Map<String, Variant> getAboutData(String language) throws ErrorReplyBusException {

        Log.i(TAG, "getAboutData(\"" + language + "\")");

        return buildAboutData();
    }

    @Override
    public Map<String, Variant> getAnnouncedAboutData() throws ErrorReplyBusException {

        Log.i(TAG, "getAnnouncedAboutData()");

        return buildAboutData();
    }

    private Map<String, Variant> buildAboutData() {
        Map<String, Variant> aboutData = new HashMap<>();
        try {

            aboutData.put(APP_ID, new Variant(getAppId()));
            aboutData.put(APP_NAME, new Variant(P2P.getContext().getName()));
            aboutData.put(DEFAULT_LANGUAGE, new Variant(new String("en")));
            aboutData.put(MODEL_NUMBER, new Variant(new String(Build.MODEL)));
            aboutData.put(SUPPORTED_LANGUAGES, new Variant(new String[]{"en"}));
            aboutData.put(SOFTWARE_VERSION, new Variant(P2P.getContext().getVersion()));
            aboutData.put(HARDWARE_VERSION, new Variant(new String(Build.SERIAL)));
            aboutData.put(AJ_SOFTWARE_VERSION, new Variant(Version.get()));
            aboutData.put(SUPPORT_URL, new Variant(P2P.getContext().getSupportUrl()));
            aboutData.put(DEVICE_ID, new Variant(new String(Build.SERIAL)));
            aboutData.put(DEVICE_NAME, new Variant(new String(Build.DEVICE)));
            aboutData.put(DEVICE_BRAND, new Variant(new String(Build.BRAND)));
            aboutData.put(MANUFACTURER, new Variant(P2P.getContext().getManufacturer()));
            aboutData.put(DESCRIPTION, new Variant(P2P.getContext().getDescription()));
            aboutData.put(INET_4_ADDRESS, new Variant(P2PUtils.getWifiIpv4()));
            aboutData.put(INET_6_ADDRESS, new Variant(P2PUtils.getWifiIpv6()));
            aboutData.put(BUS_UNIQUE_NAME, new Variant(mUniqueName));

            aboutData.putAll(P2PUtils.getOwnerInfo());

        } catch (Exception e) {
            Log.e(TAG, "Failed to build AboutData", e);
        }

        return aboutData;

    }

    protected String getVersion() {
        return "1.0";
    }

    protected String getSupportUrl() {
        return "http://www.discoos.org";
    }

    protected String getDescription() {
        return "P2P proximity network test";
    }

    protected String getManufacturer() {
        return Build.MANUFACTURER;
    }

    public synchronized static byte[] getAppId() {
        if (mAppId == null) {
            File installation = new File(P2P.getFilesDir(), "INSTALLATION");
            try {
                if (!installation.exists()) {
                    writeInstallationFile(installation);
                }
                mAppId = readInstallationFile(installation);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return mAppId;
    }

    private static byte[] readInstallationFile(File installation) throws IOException {
        RandomAccessFile f = new RandomAccessFile(installation, "r");
        byte[] bytes = new byte[(int) f.length()];
        f.readFully(bytes);
        f.close();
        return bytes;
    }

    private static void writeInstallationFile(File installation) throws IOException {
        FileOutputStream out = new FileOutputStream(installation);

        mAppId = P2PUtils.randomUUID();

        out.write(mAppId);
        out.close();
    }


}
