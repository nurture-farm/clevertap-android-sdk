package com.clevertap.android.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

@SuppressWarnings("unused")
@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class StorageHelper {

    public static SharedPreferences getPreferences(Context context, String namespace) {
        String path = Constants.CLEVERTAP_STORAGE_TAG;

        if (namespace != null) {
            path += "_" + namespace;
        }
        return context.getSharedPreferences(path, Context.MODE_PRIVATE);
    }

    public static SharedPreferences getPreferences(Context context) {
        return getPreferences(context, null);
    }

    public static String getStringFromPrefs(Context context, CleverTapInstanceConfig config, String rawKey,
            String defaultValue) {
        if (config.isDefaultInstance()) {
            String _new = getString(context, storageKeyWithSuffix(config, rawKey), defaultValue);

            return _new != null ? _new : getString(context, rawKey, defaultValue);
        } else {
            return getString(context, storageKeyWithSuffix(config, rawKey), defaultValue);
        }
    }

    public static void persist(final SharedPreferences.Editor editor) {
        try {
            editor.apply();
        } catch (Throwable t) {
            Logger.v("CRITICAL: Failed to persist shared preferences!", t);
        }
    }

    /**
     * Use this method, when you are sure that you are on background thread
     */
    @WorkerThread
    public static void persistImmediately(final SharedPreferences.Editor editor) {
        try {
            editor.commit();
        } catch (Throwable t) {
            Logger.v("CRITICAL: Failed to persist shared preferences!", t);
        }
    }

    public static void putString(Context context, String key, String value) {
        SharedPreferences prefs = getPreferences(context);
        SharedPreferences.Editor editor = prefs.edit().putString(key, value);
        persist(editor);
    }

    public static void putStringImmediate(Context context, String key, String value) {

        SharedPreferences prefs = getPreferences(context);
        SharedPreferences.Editor editor = prefs.edit().putString(key, value);
        persistImmediately(editor);
    }

    public static void remove(Context context, String key) {
        SharedPreferences prefs = getPreferences(context);
        SharedPreferences.Editor editor = prefs.edit().remove(key);
        persist(editor);
    }

    public static void removeImmediate(Context context, String key) {
        SharedPreferences prefs = getPreferences(context);
        SharedPreferences.Editor editor = prefs.edit().remove(key);
        persistImmediately(editor);
    }

    //Preferences
    public static String storageKeyWithSuffix(CleverTapInstanceConfig config, String key) {
        return key + ":" + config.getAccountId();
    }

    @SuppressWarnings("SameParameterValue")
    static boolean getBoolean(Context context, String key, boolean defaultValue) {
        return getPreferences(context).getBoolean(key, defaultValue);
    }

    static boolean getBooleanFromPrefs(Context context, CleverTapInstanceConfig config, String rawKey) {
        if (config.isDefaultInstance()) {
            boolean _new = getBoolean(context, storageKeyWithSuffix(config, rawKey), false);
            //noinspection ConstantConditions
            return !_new ? getBoolean(context, rawKey, false) : _new;
        } else {
            return getBoolean(context, storageKeyWithSuffix(config, rawKey), false);
        }
    }

    static int getInt(Context context, String key, int defaultValue) {
        return getPreferences(context).getInt(key, defaultValue);
    }

    @SuppressWarnings("SameParameterValue")
    static int getIntFromPrefs(Context context, CleverTapInstanceConfig config, String rawKey, int defaultValue) {
        if (config.isDefaultInstance()) {
            int dummy = -1000;
            int _new = getInt(context, storageKeyWithSuffix(config, rawKey), dummy);
            return _new != dummy ? _new : getInt(context, rawKey, defaultValue);
        } else {
            return getInt(context, storageKeyWithSuffix(config, rawKey), defaultValue);
        }
    }

    static long getLong(Context context, String key, long defaultValue) {
        return getPreferences(context).getLong(key, defaultValue);
    }

    static long getLong(Context context, String nameSpace, String key, long defaultValue) {
        return getPreferences(context, nameSpace).getLong(key, defaultValue);
    }

    @SuppressWarnings("SameParameterValue")
    static long getLongFromPrefs(Context context, CleverTapInstanceConfig config, String rawKey, int defaultValue,
            String nameSpace) {
        if (config.isDefaultInstance()) {
            long dummy = -1000;
            long _new = getLong(context, nameSpace, storageKeyWithSuffix(config, rawKey), dummy);
            return _new != dummy ? _new : getLong(context, nameSpace, rawKey, defaultValue);
        } else {
            return getLong(context, nameSpace, storageKeyWithSuffix(config, rawKey), defaultValue);
        }
    }

    static String getString(Context context, String key, String defaultValue) {
        return getPreferences(context).getString(key, defaultValue);
    }

    static String getString(Context context, String nameSpace, String key, String defaultValue) {
        return getPreferences(context, nameSpace).getString(key, defaultValue);
    }

    static void putBoolean(Context context, String key, boolean value) {
        SharedPreferences prefs = getPreferences(context);
        SharedPreferences.Editor editor = prefs.edit().putBoolean(key, value);
        persist(editor);
    }

    static void putInt(Context context, String key, int value) {
        SharedPreferences prefs = getPreferences(context);
        SharedPreferences.Editor editor = prefs.edit().putInt(key, value);
        persist(editor);
    }

    static void putLong(Context context, String key, long value) {
        SharedPreferences prefs = getPreferences(context);
        SharedPreferences.Editor editor = prefs.edit().putLong(key, value);
        persist(editor);
    }
}
