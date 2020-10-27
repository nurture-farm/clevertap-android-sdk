package com.clevertap.android.sdk;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import androidx.core.app.NotificationManagerCompat;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;

class DeviceInfo {

    private class DeviceCachedInfo {

        private String bluetoothVersion;

        private int build;

        private String carrier;

        private String countryCode;

        private int dpi;

        private double height;

        private int heightPixels;

        private String manufacturer;

        private String model;

        private String networkType;

        private boolean notificationsEnabled;

        private String osName;

        private String osVersion;

        private int sdkVersion;

        private String versionName;

        private double width;

        private int widthPixels;

        DeviceCachedInfo() {
            versionName = getVersionName();
            osName = getOsName();
            osVersion = getOsVersion();
            manufacturer = getManufacturer();
            model = getModel();
            carrier = getCarrier();
            build = getBuild();
            networkType = getNetworkType();
            bluetoothVersion = getBluetoothVersion();
            countryCode = getCountryCode();
            sdkVersion = getSdkVersion();
            height = getHeight();
            heightPixels = getHeightPixels();
            width = getWidth();
            widthPixels = getWidthPixels();
            dpi = getDPI();
            notificationsEnabled = getNotificationEnabledForUser();
        }

        private String getBluetoothVersion() {
            String bluetoothVersion = "none";
            if (android.os.Build.VERSION.SDK_INT >= 18 &&
                    context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                bluetoothVersion = "ble";
            } else if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
                bluetoothVersion = "classic";
            }

            return bluetoothVersion;
        }

        private int getBuild() {
            PackageInfo packageInfo;
            try {
                packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                return packageInfo.versionCode;
            } catch (PackageManager.NameNotFoundException e) {
                Logger.d("Unable to get app build");
            }
            return 0;
        }

        private String getCarrier() {
            try {
                TelephonyManager manager = (TelephonyManager) context
                        .getSystemService(Context.TELEPHONY_SERVICE);
                if (manager != null) {
                    return manager.getNetworkOperatorName();
                }

            } catch (Exception e) {
                // Failed to get network operator name from network
            }
            return null;
        }

        private String getCountryCode() {
            try {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) {
                    return tm.getSimCountryIso();
                }
            } catch (Throwable ignore) {
                return "";
            }
            return "";
        }

        private int getDPI() {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                return 0;
            }
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(dm);
            return dm.densityDpi;
        }

        private double getHeight() {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                return 0.0;
            }
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(dm);
            // Calculate the height in inches
            double rHeight = dm.heightPixels / dm.ydpi;
            return toTwoPlaces(rHeight);
        }

        private int getHeightPixels() {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                return 0;
            }
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(dm);
            return dm.heightPixels;
        }

        private String getManufacturer() {
            return Build.MANUFACTURER;
        }

        private String getModel() {
            String model = Build.MODEL;
            model = model.replace(getManufacturer(), "");
            return model;
        }

        @SuppressLint("MissingPermission")
        private String getNetworkType() {
            return Utils.getDeviceNetworkType(context);
        }

        private boolean getNotificationEnabledForUser() {
            return NotificationManagerCompat.from(context).areNotificationsEnabled();
        }

        private String getOsName() {
            return OS_NAME;
        }

        private String getOsVersion() {
            return Build.VERSION.RELEASE;
        }

        private int getSdkVersion() {
            return BuildConfig.VERSION_CODE;
        }

        private String getVersionName() {
            PackageInfo packageInfo;
            try {
                packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                return packageInfo.versionName;
            } catch (PackageManager.NameNotFoundException e) {
                Logger.d("Unable to get app version");
            }
            return null;
        }

        private double getWidth() {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                return 0.0;
            }
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(dm);
            // Calculate the width in inches
            double rWidth = dm.widthPixels / dm.xdpi;
            return toTwoPlaces(rWidth);

        }

        private int getWidthPixels() {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                return 0;
            }
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(dm);
            return dm.widthPixels;
        }

        private double toTwoPlaces(double n) {
            double result = n * 100;
            result = Math.round(result);
            result = result / 100;
            return result;
        }
    }

    private static final String GUID_PREFIX = "__";

    private static final String OS_NAME = "Android";

    private final Object adIDLock = new Object();

    private boolean adIdRun = false;

    private DeviceCachedInfo cachedInfo;

    private CleverTapInstanceConfig config;

    private Context context;

    private final Object deviceIDLock = new Object();

    private String googleAdID = null;

    private String library;

    private boolean limitAdTracking = false;

    private ArrayList<ValidationResult> validationResults = new ArrayList<>();

    DeviceInfo(Context context, CleverTapInstanceConfig config, String cleverTapID) {
        this.context = context;
        this.config = config;
        this.library = null;
        Thread deviceInfoCacheThread = new Thread(new Runnable() {
            @Override
            public void run() {
                getDeviceCachedInfo();
            }
        });
        deviceInfoCacheThread.start();
        initDeviceID(cleverTapID);
    }

    void forceNewDeviceID() {
        String deviceID = generateGUID();
        forceUpdateDeviceId(deviceID);
    }

    void forceUpdateCustomCleverTapID(String cleverTapID) {
        if (Utils.validateCTID(cleverTapID)) {
            getConfigLogger()
                    .info(config.getAccountId(), "Setting CleverTap ID to custom CleverTap ID : " + cleverTapID);
            forceUpdateDeviceId(Constants.CUSTOM_CLEVERTAP_ID_PREFIX + cleverTapID);
        } else {
            setOrGenerateFallbackDeviceID();
            removeDeviceID();
            String error = recordDeviceError(Constants.INVALID_CT_CUSTOM_ID, cleverTapID, getFallBackDeviceID());
            getConfigLogger().info(config.getAccountId(), error);
        }
    }

    /**
     * Force updates the device ID, with the ID specified.
     * <p>
     * This is used internally by the SDK, there is no need to call this explicitly.
     * </p>
     *
     * @param id The new device ID
     */
    @SuppressLint("CommitPrefEdits")
    void forceUpdateDeviceId(String id) {
        getConfigLogger().verbose(this.config.getAccountId(), "Force updating the device ID to " + id);
        synchronized (deviceIDLock) {
            StorageHelper.putString(context, getDeviceIdStorageKey(), id);
        }
    }

    String getAttributionID() {
        return getDeviceID();
    }

    String getBluetoothVersion() {
        return getDeviceCachedInfo().bluetoothVersion;
    }

    int getBuild() {
        return getDeviceCachedInfo().build;
    }

    String getCarrier() {
        return getDeviceCachedInfo().carrier;
    }

    String getCountryCode() {
        return getDeviceCachedInfo().countryCode;
    }

    int getDPI() {
        return getDeviceCachedInfo().dpi;
    }

    String getDeviceID() {
        return _getDeviceID() != null ? _getDeviceID() : getFallBackDeviceID();
    }

    String getGoogleAdID() {
        synchronized (adIDLock) {
            return googleAdID;
        }
    }

    double getHeight() {
        return getDeviceCachedInfo().height;
    }

    int getHeightPixels() {
        return getDeviceCachedInfo().heightPixels;
    }

    String getLibrary() {
        return library;
    }

    void setLibrary(String library) {
        this.library = library;
    }

    String getManufacturer() {
        return getDeviceCachedInfo().manufacturer;
    }

    String getModel() {
        return getDeviceCachedInfo().model;
    }

    String getNetworkType() {
        return getDeviceCachedInfo().networkType;
    }

    boolean getNotificationsEnabledForUser() {
        return getDeviceCachedInfo().notificationsEnabled;
    }

    String getOsName() {
        return getDeviceCachedInfo().osName;
    }

    String getOsVersion() {
        return getDeviceCachedInfo().osVersion;
    }

    int getSdkVersion() {
        return getDeviceCachedInfo().sdkVersion;
    }

    ArrayList<ValidationResult> getValidationResults() {
        // noinspection unchecked
        ArrayList<ValidationResult> tempValidationResults = (ArrayList<ValidationResult>) validationResults.clone();
        validationResults.clear();
        return tempValidationResults;
    }

    String getVersionName() {
        return getDeviceCachedInfo().versionName;
    }

    double getWidth() {
        return getDeviceCachedInfo().width;
    }

    int getWidthPixels() {
        return getDeviceCachedInfo().widthPixels;
    }

    @SuppressLint("MissingPermission")
    @SuppressWarnings("MissingPermission")
    Boolean isBluetoothEnabled() {
        Boolean isBluetoothEnabled = null;
        try {
            PackageManager pm = context.getPackageManager();
            int hasBluetoothPermission = pm.checkPermission(Manifest.permission.BLUETOOTH, context.getPackageName());
            if (hasBluetoothPermission == PackageManager.PERMISSION_GRANTED) {
                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (bluetoothAdapter != null) {
                    isBluetoothEnabled = bluetoothAdapter.isEnabled();
                }
            }
        } catch (Throwable e) {
            // do nothing since we don't have permissions
        }
        return isBluetoothEnabled;
    }

    boolean isErrorDeviceId() {
        return getDeviceID() != null && getDeviceID().startsWith(Constants.ERROR_PROFILE_PREFIX);
    }

    boolean isLimitAdTrackingEnabled() {
        synchronized (adIDLock) {
            return limitAdTracking;
        }
    }

    Boolean isWifiConnected() {
        Boolean ret = null;

        if (PackageManager.PERMISSION_GRANTED == context
                .checkCallingOrSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE)) {
            ConnectivityManager connManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connManager != null) {
                @SuppressLint("MissingPermission")
                NetworkInfo networkInfo = connManager.getActiveNetworkInfo();
                ret = (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo
                        .isConnected());
            }
        }

        return ret;
    }

    private String _getDeviceID() {
        synchronized (deviceIDLock) {
            if (this.config.isDefaultInstance()) {
                String _new = StorageHelper.getString(this.context, getDeviceIdStorageKey(), null);
                return _new != null ? _new : StorageHelper.getString(this.context, Constants.DEVICE_ID_TAG, null);
            } else {
                return StorageHelper.getString(this.context, getDeviceIdStorageKey(), null);
            }
        }
    }

    private synchronized void fetchGoogleAdID() {
        if (getGoogleAdID() == null && !adIdRun) {
            String advertisingID = null;
            try {
                adIdRun = true;
                Class adIdClient = Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient");
                // noinspection unchecked
                Method getAdInfo = adIdClient.getMethod("getAdvertisingIdInfo", Context.class);
                Object adInfo = getAdInfo.invoke(null, context);
                Method isLimitAdTracking = adInfo.getClass().getMethod("isLimitAdTrackingEnabled");
                Boolean limitedAdTracking = (Boolean) isLimitAdTracking.invoke(adInfo);
                synchronized (adIDLock) {
                    limitAdTracking = limitedAdTracking != null && limitedAdTracking;
                    if (limitAdTracking) {
                        return;
                    }
                }
                Method getAdId = adInfo.getClass().getMethod("getId");
                advertisingID = (String) getAdId.invoke(adInfo);
            } catch (Throwable t) {
                if (t.getCause() != null) {
                    getConfigLogger().verbose(config.getAccountId(),
                            "Failed to get Advertising ID: " + t.toString() + t.getCause().toString());
                } else {
                    getConfigLogger().verbose(config.getAccountId(), "Failed to get Advertising ID: " + t.toString());
                }
            }
            if (advertisingID != null && advertisingID.trim().length() > 2) {
                synchronized (adIDLock) {
                    googleAdID = advertisingID.replace("-", "");
                }
            }
        }
    }

    private synchronized void generateDeviceID() {
        String generatedDeviceID;
        String adId = getGoogleAdID();
        if (adId != null) {
            generatedDeviceID = Constants.GUID_PREFIX_GOOGLE_AD_ID + adId;
        } else {
            synchronized (deviceIDLock) {
                generatedDeviceID = generateGUID();
            }
        }
        forceUpdateDeviceId(generatedDeviceID);
    }

    private String generateGUID() {
        return GUID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    private Logger getConfigLogger() {
        return this.config.getLogger();
    }

    private DeviceCachedInfo getDeviceCachedInfo() {
        if (cachedInfo == null) {
            cachedInfo = new DeviceCachedInfo();
        }
        return cachedInfo;
    }

    private String getDeviceIdStorageKey() {
        return Constants.DEVICE_ID_TAG + ":" + this.config.getAccountId();
    }

    private String getFallBackDeviceID() {
        return StorageHelper.getString(this.context, getFallbackIdStorageKey(), null);
    }

    private String getFallbackIdStorageKey() {
        return Constants.FALLBACK_ID_TAG + ":" + this.config.getAccountId();
    }

    private void initDeviceID(String cleverTapID) {

        //Show logging as per Manifest flag
        if (config.getEnableCustomCleverTapId()) {
            if (cleverTapID == null) {
                String error = recordDeviceError(Constants.USE_CUSTOM_ID_FALLBACK);
                config.getLogger().info(error);
            }
        } else {
            if (cleverTapID != null) {
                String error = recordDeviceError(Constants.USE_CUSTOM_ID_MISSING_IN_MANIFEST);
                config.getLogger().info(error);
            }
        }

        String deviceID = _getDeviceID();
        if (deviceID != null && deviceID.trim().length() > 2) {
            getConfigLogger().verbose(config.getAccountId(), "CleverTap ID already present for profile");
            if (cleverTapID != null) {
                String error = recordDeviceError(Constants.UNABLE_TO_SET_CT_CUSTOM_ID, deviceID, cleverTapID);
                getConfigLogger().info(config.getAccountId(), error);
            }
            return;
        }

        if (this.config.getEnableCustomCleverTapId()) {
            forceUpdateCustomCleverTapID(cleverTapID);
            return;
        }

        if (!this.config.isUseGoogleAdId()) {
            generateDeviceID();
            return;
        }

        // fetch the googleAdID to generate GUID
        //has to be called on background thread
        Thread generateGUIDFromAdIDThread = new Thread(new Runnable() {
            @Override
            public void run() {
                fetchGoogleAdID();
                generateDeviceID();
                CleverTapAPI.instanceWithConfig(context, config).deviceIDCreated(getDeviceID());
            }
        });
        generateGUIDFromAdIDThread.start();
    }

    private String recordDeviceError(int messageCode, String... varargs) {
        ValidationResult validationResult = ValidationResultFactory.create(514, messageCode, varargs);
        validationResults.add(validationResult);
        return validationResult.getErrorDesc();
    }

    private void removeDeviceID() {
        StorageHelper.remove(this.context, getDeviceIdStorageKey());
    }

    private synchronized void setOrGenerateFallbackDeviceID() {
        if (getFallBackDeviceID() == null) {
            synchronized (deviceIDLock) {
                String fallbackDeviceID = Constants.ERROR_PROFILE_PREFIX + UUID.randomUUID().toString()
                        .replace("-", "");
                if (fallbackDeviceID.trim().length() > 2) {
                    updateFallbackID(fallbackDeviceID);
                } else {
                    getConfigLogger()
                            .verbose(this.config.getAccountId(), "Unable to generate fallback error device ID");
                }
            }
        }
    }

    private void updateFallbackID(String fallbackId) {
        getConfigLogger().verbose(this.config.getAccountId(), "Updating the fallback id - " + fallbackId);
        StorageHelper.putString(context, getFallbackIdStorageKey(), fallbackId);
    }

    /**
     * Returns the integer identifier for the default app icon.
     *
     * @param context The Android context
     * @return The integer identifier for the image resource
     */
    static int getAppIconAsIntId(final Context context) {
        ApplicationInfo ai = context.getApplicationInfo();
        return ai.icon;
    }
}
