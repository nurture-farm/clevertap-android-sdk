package com.clevertap.android.sdk;

import static android.content.Context.NOTIFICATION_SERVICE;
import static com.clevertap.android.sdk.CTXtensions.isPackageAndOsTargetsAbove;
import static com.clevertap.android.sdk.Utils.getSCDomain;
import static com.clevertap.android.sdk.pushnotification.PushConstants.FCM_LOG_TAG;
import static com.clevertap.android.sdk.pushnotification.PushConstants.LOG_TAG;
import static com.clevertap.android.sdk.pushnotification.PushConstants.PushType.FCM;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.location.Location;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.WorkerThread;

import com.clevertap.android.sdk.displayunits.DisplayUnitListener;
import com.clevertap.android.sdk.displayunits.model.CleverTapDisplayUnit;
import com.clevertap.android.sdk.events.EventDetail;
import com.clevertap.android.sdk.events.EventGroup;
import com.clevertap.android.sdk.featureFlags.CTFeatureFlagsController;
import com.clevertap.android.sdk.inapp.CTLocalInApp;
import com.clevertap.android.sdk.inbox.CTInboxActivity;
import com.clevertap.android.sdk.inbox.CTInboxMessage;
import com.clevertap.android.sdk.inbox.CTMessageDAO;
import com.clevertap.android.sdk.interfaces.NotificationHandler;
import com.clevertap.android.sdk.interfaces.NotificationRenderedListener;
import com.clevertap.android.sdk.interfaces.OnInitCleverTapIDListener;
import com.clevertap.android.sdk.interfaces.SCDomainListener;
import com.clevertap.android.sdk.network.NetworkManager;
import com.clevertap.android.sdk.product_config.CTProductConfigController;
import com.clevertap.android.sdk.product_config.CTProductConfigListener;
import com.clevertap.android.sdk.pushnotification.CTPushNotificationListener;
import com.clevertap.android.sdk.pushnotification.CoreNotificationRenderer;
import com.clevertap.android.sdk.pushnotification.INotificationRenderer;
import com.clevertap.android.sdk.pushnotification.NotificationInfo;
import com.clevertap.android.sdk.pushnotification.PushConstants;
import com.clevertap.android.sdk.pushnotification.PushConstants.PushType;
import com.clevertap.android.sdk.pushnotification.PushConstants.XiaomiPush;
import com.clevertap.android.sdk.pushnotification.amp.CTPushAmpListener;
import com.clevertap.android.sdk.task.CTExecutorFactory;
import com.clevertap.android.sdk.task.Task;
import com.clevertap.android.sdk.utils.UriHelper;
import com.clevertap.android.sdk.validation.ManifestValidator;
import com.clevertap.android.sdk.validation.ValidationResult;
import com.clevertap.android.sdk.variables.CTVariables;
import com.clevertap.android.sdk.variables.Var;
import com.clevertap.android.sdk.variables.callbacks.FetchVariablesCallback;
import com.clevertap.android.sdk.variables.callbacks.VariablesChangedCallback;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;


/**
 * <h1>CleverTapAPI</h1>
 * This is the main CleverTapAPI class that manages the SDK instances
 */
public class CleverTapAPI implements CTInboxActivity.InboxActivityListener {

    /**
     * Implement to get called back when the device push token is refreshed
     */
    public interface DevicePushTokenRefreshListener {

        /**
         * @param token the device token
         * @param type  the token type com.clevertap.android.sdk.PushType (FCM)
         */
        void devicePushTokenDidRefresh(String token, PushType type);
    }

    /**
     * Implement to get called back when the device push token is received
     */
    @RestrictTo(Scope.LIBRARY)
    public interface RequestDevicePushTokenListener {

        /**
         * @param token the device token
         * @param type  the token type com.clevertap.android.sdk.PushType (FCM)
         */
        void onDevicePushToken(String token, PushType type);
    }

    @SuppressWarnings({"unused"})
    public enum LogLevel {
        OFF(-1),
        INFO(0),
        DEBUG(2),
        VERBOSE(3);

        private final int value;

        LogLevel(final int newValue) {
            value = newValue;
        }

        public int intValue() {
            return value;
        }
    }


    @SuppressWarnings("unused")
    public static final String NOTIFICATION_TAG = "wzrk_pn";

    private static int debugLevel = LogLevel.INFO.intValue();

    static CleverTapInstanceConfig defaultConfig;

    private static HashMap<String, CleverTapAPI> instances;

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private static String sdkVersion;  // For Google Play Store/Android Studio analytics

    private static NotificationHandler sNotificationHandler;

    private static NotificationHandler sSignedCallNotificationHandler;

    private static HashMap<String,NotificationRenderedListener> sNotificationRenderedListenerMap = new HashMap<>();

    private final Context context;

    private CoreState coreState;

    private WeakReference<InboxMessageButtonListener> inboxMessageButtonListener;

    private WeakReference<InboxMessageListener> inboxMessageListener;

    /**
     * This method is used to change the credentials of CleverTap account Id and token programmatically
     *
     * @param accountID CleverTap Account Id
     * @param token     CleverTap Account Token
     */
    @SuppressWarnings("unused")
    public static void changeCredentials(String accountID, String token, String proxy) {
        changeCredentials(accountID, token,null, proxy);
    }

    /**
     * This method is used to change the credentials of CleverTap account Id, token, proxyDomain and spikyProxyDomain programmatically
     *
     * @param accountID         CleverTap Account Id
     * @param token             CleverTap Account Token
     * @param proxyDomain       CleverTap Proxy Domain
     * @param spikyProxyDomain  CleverTap Spiky Proxy Domain
     */
    public static void changeCredentials(String accountID, String token, String proxyDomain, String spikyProxyDomain) {
        if (defaultConfig != null) {
            Logger.i("CleverTap SDK already initialized with accountID:" + defaultConfig.getAccountId()
                    + ", token:" + defaultConfig.getAccountToken() + ", proxyDomain: " + defaultConfig.getProxyDomain() +
                    " and spikyDomain: " + defaultConfig.getSpikyProxyDomain() +
                    ". Cannot change credentials to accountID: " + accountID +
                    ", token: " + token + ", proxyDomain: " + proxyDomain + "and spikyProxyDomain: " + spikyProxyDomain);
            return;
        }

        ManifestInfo.changeCredentials(accountID, token, proxyDomain, spikyProxyDomain);
    }

    /**
     * This method is used to change the credentials of Xiaomi app id and key programmatically.
     *
     * @param xiaomiAppID  Xiaomi App Id
     * @param xiaomiAppKey Xiaomi App Key
     */
    public static void changeXiaomiCredentials(String xiaomiAppID, String xiaomiAppKey) {
        ManifestInfo.changeXiaomiCredentials(xiaomiAppID, xiaomiAppKey);
    }

    /**
     * Launches an asynchronous task to download the notification icon from CleverTap,
     * and create the Android notification.
     * <p>
     * If your app is using CleverTap SDK's built in FCM message handling,
     * this method does not need to be called explicitly.
     * <p/>
     * Use this method when implementing your own FCM handling mechanism. Refer to the
     * SDK documentation for usage scenarios and examples.
     *
     * <p style="color:#4d2e00;background:#ffcc99;font-weight: bold" >
     * Note: Starting from core v5.1.0, this method runs on the caller's thread. Make sure to call it
     * in onMessageReceive() of messaging service.
     * </p>
     *
     * @param context        A reference to an Android context
     * @param extras         The {@link Bundle} object received by the broadcast receiver
     * @param notificationId A custom id to build a notification
     */
    @SuppressWarnings({"WeakerAccess"})
    public static void createNotification(final Context context, final Bundle extras, final int notificationId) {
        CleverTapAPI instance = fromBundle(context, extras);
        if (instance != null) {

            CoreState coreState = instance.coreState;
            CleverTapInstanceConfig config = coreState.getConfig();

            try {
                synchronized (coreState.getPushProviders().getPushRenderingLock()) {
                    coreState.getPushProviders().setPushNotificationRenderer(new CoreNotificationRenderer());
                    coreState.getPushProviders()._createNotification(context, extras, notificationId);
                }
            } catch (Throwable t) {
                config.getLogger().debug(config.getAccountId(), "Failed to process createNotification()", t);
            }
        }
    }

    public static @Nullable
    CleverTapAPI getGlobalInstance(Context context, String _accountId) {
        return fromAccountId(context, _accountId);
    }

    /**
     * Pass Push Notification Payload to CleverTap for smooth functioning of Push Amplification
     *
     * @param context - Application Context
     * @param extras  - Bundle received via FCM/Push Amplification
     */
    @SuppressWarnings("unused")
    public static void processPushNotification(Context context, Bundle extras) {
        CleverTapAPI instance = fromBundle(context, extras);
        if (instance != null) {
            instance.coreState.getPushProviders().processCustomPushNotification(extras);
        }
    }

    /**
     * Launches an asynchronous task to download the notification icon from CleverTap,
     * and create the Android notification.
     * <p/>
     * Use this method when implementing your own FCM handling mechanism. Refer to the
     * SDK documentation for usage scenarios and examples.
     *
     *  <p style="color:#4d2e00;background:#ffcc99;font-weight: bold" >
     *      Note: Starting from core v5.1.0, this method runs on the caller's thread. Make sure to call it
     *      in onMessageReceive() of messaging service.
     *</p>
     *
     * @param context A reference to an Android context
     * @param extras  The {@link Bundle} object received by the broadcast receiver
     */
    @SuppressWarnings({"WeakerAccess"})
    public static void createNotification(final Context context, final Bundle extras) {
        createNotification(context, extras, Constants.EMPTY_NOTIFICATION_ID);
    }

    /**
     * Launches an asynchronous task to create the notification channel from CleverTap
     * <p/>
     * Use this method when implementing your own FCM handling mechanism. Refer to the
     * SDK documentation for usage scenarios and examples.
     *
     * @param context            A reference to an Android context
     * @param channelId          A String for setting the id of the notification channel
     * @param channelName        A String for setting the name of the notification channel
     * @param channelDescription A String for setting the description of the notification channel
     * @param importance         An Integer value setting the importance of the notifications sent in this channel
     * @param showBadge          An boolean value as to whether this channel shows a badge
     */
    @SuppressWarnings("unused")
    public static void createNotificationChannel(final Context context, final String channelId,
            final CharSequence channelName, final String channelDescription, final int importance,
            final boolean showBadge) {
        final CleverTapAPI instance = getDefaultInstanceOrFirstOther(context);
        if (instance == null) {
            Logger.v("No CleverTap Instance found in CleverTapAPI#createNotificatonChannel");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Task<Void> task = CTExecutorFactory.executors(instance.coreState.getConfig()).postAsyncSafelyTask();
                task.execute("createNotificationChannel", new Callable<Void>() {
                    @RequiresApi(api = Build.VERSION_CODES.O)
                    @Override
                    public Void call() {

                        NotificationManager notificationManager = (NotificationManager) context
                                .getSystemService(NOTIFICATION_SERVICE);
                        if (notificationManager == null) {
                            return null;
                        }
                        NotificationChannel notificationChannel = new NotificationChannel(channelId,
                                channelName,
                                importance);
                        notificationChannel.setDescription(channelDescription);
                        notificationChannel.setShowBadge(showBadge);
                        notificationManager.createNotificationChannel(notificationChannel);
                        instance.getConfigLogger().info(instance.getAccountId(),
                                "Notification channel " + channelName.toString() + " has been created");
                        return null;
                    }
                });
            }
        } catch (Throwable t) {
            instance.getConfigLogger().verbose(instance.getAccountId(), "Failure creating Notification Channel", t);
        }

    }

    /**
     * Launches an asynchronous task to create the notification channel from CleverTap
     * <p/>
     * Use this method when implementing your own FCM handling mechanism and creating
     * notification channel groups. Refer to the
     * SDK documentation for usage scenarios and examples.
     *
     * @param context            A reference to an Android context
     * @param channelId          A String for setting the id of the notification channel
     * @param channelName        A String for setting the name of the notification channel
     * @param channelDescription A String for setting the description of the notification channel
     * @param importance         An Integer value setting the importance of the notifications sent in this
     *                           channel
     * @param groupId            A String for setting the notification channel as a part of a notification
     *                           group
     * @param showBadge          An boolean value as to whether this channel shows a badge
     */
    @SuppressWarnings("unused")
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void createNotificationChannel(final Context context, final String channelId,
            final CharSequence channelName, final String channelDescription, final int importance,
            final String groupId, final boolean showBadge) {
        final CleverTapAPI instance = getDefaultInstanceOrFirstOther(context);
        if (instance == null) {
            Logger.v("No CleverTap Instance found in CleverTapAPI#createNotificatonChannel");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Task<Void> task = CTExecutorFactory.executors(instance.coreState.getConfig()).postAsyncSafelyTask();
                task.execute("creatingNotificationChannel", new Callable<Void>() {
                    @Override
                    public Void call() {
                        NotificationManager notificationManager = (NotificationManager) context
                                .getSystemService(NOTIFICATION_SERVICE);
                        if (notificationManager == null) {
                            return null;
                        }
                        NotificationChannel notificationChannel = new NotificationChannel(channelId,
                                channelName,
                                importance);
                        notificationChannel.setDescription(channelDescription);
                        notificationChannel.setGroup(groupId);
                        notificationChannel.setShowBadge(showBadge);
                        notificationManager.createNotificationChannel(notificationChannel);
                        instance.getConfigLogger().info(instance.getAccountId(),
                                "Notification channel " + channelName.toString() + " has been created");

                        return null;
                    }
                });
            }
        } catch (Throwable t) {
            instance.getConfigLogger().verbose(instance.getAccountId(), "Failure creating Notification Channel", t);
        }

    }

    /**
     * Launches an asynchronous task to create the notification channel from CleverTap
     * <p/>
     * Use this method when implementing your own FCM handling mechanism. Refer to the
     * SDK documentation for usage scenarios and examples.
     *
     * @param context            A reference to an Android context
     * @param channelId          A String for setting the id of the notification channel
     * @param channelName        A String for setting the name of the notification channel
     * @param channelDescription A String for setting the description of the notification channel
     * @param importance         An Integer value setting the importance of the notifications sent in this channel
     * @param showBadge          An boolean value as to whether this channel shows a badge
     * @param sound              A String denoting the custom sound raw file for this channel
     */
    @SuppressWarnings("unused")
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void createNotificationChannel(final Context context, final String channelId,
            final CharSequence channelName, final String channelDescription, final int importance,
            final boolean showBadge, final String sound) {
        final CleverTapAPI instance = getDefaultInstanceOrFirstOther(context);
        if (instance == null) {
            Logger.v("No CleverTap Instance found in CleverTapAPI#createNotificatonChannel");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Task<Void> task = CTExecutorFactory.executors(instance.coreState.getConfig()).postAsyncSafelyTask();
                task.execute("createNotificationChannel", new Callable<Void>() {
                    @Override
                    public Void call() {
                        NotificationManager notificationManager = (NotificationManager) context
                                .getSystemService(NOTIFICATION_SERVICE);
                        if (notificationManager == null) {
                            return null;
                        }

                        String soundfile = "";
                        Uri soundUri = null;

                        if (!sound.isEmpty()) {
                            if (sound.contains(".mp3") || sound.contains(".ogg") || sound.contains(".wav")) {
                                soundfile = sound.substring(0, (sound.length() - 4));
                            } else {
                                instance.getConfigLogger()
                                        .debug(instance.getAccountId(), "Sound file name not supported");
                            }
                            if (!soundfile.isEmpty()) {
                                soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context
                                        .getPackageName() + "/raw/" + soundfile);
                            }

                        }

                        NotificationChannel notificationChannel = new NotificationChannel(channelId,
                                channelName,
                                importance);
                        notificationChannel.setDescription(channelDescription);
                        notificationChannel.setShowBadge(showBadge);
                        if (soundUri != null) {
                            notificationChannel.setSound(soundUri,
                                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                            .build());
                        } else {
                            instance.getConfigLogger().debug(instance.getAccountId(),
                                    "Sound file not found, notification channel will be created without custom sound");
                        }
                        notificationManager.createNotificationChannel(notificationChannel);
                        instance.getConfigLogger().info(instance.getAccountId(),
                                "Notification channel " + channelName.toString() + " has been created");
                        return null;
                    }
                });
            }
        } catch (Throwable t) {
            instance.getConfigLogger().verbose(instance.getAccountId(), "Failure creating Notification Channel", t);
        }
    }

    /**
     * Launches an asynchronous task to create the notification channel from CleverTap
     * <p/>
     * Use this method when implementing your own FCM handling mechanism and creating
     * notification channel groups. Refer to the
     * SDK documentation for usage scenarios and examples.
     *
     * @param context            A reference to an Android context
     * @param channelId          A String for setting the id of the notification channel
     * @param channelName        A String for setting the name of the notification channel
     * @param channelDescription A String for setting the description of the notification channel
     * @param importance         An Integer value setting the importance of the notifications sent in this
     *                           channel
     * @param groupId            A String for setting the notification channel as a part of a notification
     *                           group
     * @param showBadge          An boolean value as to whether this channel shows a badge
     * @param sound              A String denoting the custom sound raw file for this channel
     */
    @SuppressWarnings({"unused"})
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void createNotificationChannel(final Context context, final String channelId,
            final CharSequence channelName, final String channelDescription, final int importance,
            final String groupId, final boolean showBadge, final String sound) {
        final CleverTapAPI instance = getDefaultInstanceOrFirstOther(context);
        if (instance == null) {
            Logger.v("No CleverTap Instance found in CleverTapAPI#createNotificatonChannel");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Task<Void> task = CTExecutorFactory.executors(instance.coreState.getConfig()).postAsyncSafelyTask();
                task.execute("creatingNotificationChannel", new Callable<Void>() {
                    @Override
                    public Void call() {
                        NotificationManager notificationManager = (NotificationManager) context
                                .getSystemService(NOTIFICATION_SERVICE);
                        if (notificationManager == null) {
                            return null;
                        }

                        String soundfile = "";
                        Uri soundUri = null;

                        if (!sound.isEmpty()) {
                            if (sound.contains(".mp3") || sound.contains(".ogg") || sound.contains(".wav")) {
                                soundfile = sound.substring(0, (sound.length() - 4));
                            } else {
                                instance.getConfigLogger()
                                        .debug(instance.getAccountId(), "Sound file name not supported");
                            }
                            if (!soundfile.isEmpty()) {
                                soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context
                                        .getPackageName() + "/raw/" + soundfile);
                            }

                        }
                        NotificationChannel notificationChannel = new NotificationChannel(channelId,
                                channelName,
                                importance);
                        notificationChannel.setDescription(channelDescription);
                        notificationChannel.setGroup(groupId);
                        notificationChannel.setShowBadge(showBadge);
                        if (soundUri != null) {
                            notificationChannel.setSound(soundUri,
                                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                            .build());
                        } else {
                            instance.getConfigLogger().debug(instance.getAccountId(),
                                    "Sound file not found, notification channel will be created without custom sound");
                        }
                        notificationManager.createNotificationChannel(notificationChannel);
                        instance.getConfigLogger().info(instance.getAccountId(),
                                "Notification channel " + channelName.toString() + " has been created");
                        return null;
                    }
                });
            }
        } catch (Throwable t) {
            instance.getConfigLogger().verbose(instance.getAccountId(), "Failure creating Notification Channel", t);
        }

    }

    /**
     * Launches an asynchronous task to create the notification channel group from CleverTap
     * <p/>
     * Use this method when implementing your own FCM handling mechanism. Refer to the
     * SDK documentation for usage scenarios and examples.
     *
     * @param context   A reference to an Android context
     * @param groupId   A String for setting the id of the notification channel group
     * @param groupName A String for setting the name of the notification channel group
     */
    @SuppressWarnings("unused")
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void createNotificationChannelGroup(final Context context, final String groupId,
            final CharSequence groupName) {
        final CleverTapAPI instance = getDefaultInstanceOrFirstOther(context);
        if (instance == null) {
            Logger.v("No CleverTap Instance found in CleverTapAPI#createNotificationChannelGroup");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Task<Void> task = CTExecutorFactory.executors(instance.coreState.getConfig()).postAsyncSafelyTask();
                task.execute("creatingNotificationChannelGroup", new Callable<Void>() {
                    @Override
                    public Void call() {
                        NotificationManager notificationManager = (NotificationManager) context
                                .getSystemService(NOTIFICATION_SERVICE);
                        if (notificationManager == null) {
                            return null;
                        }
                        notificationManager
                                .createNotificationChannelGroup(
                                        new NotificationChannelGroup(groupId, groupName));
                        instance.getConfigLogger().info(instance.getAccountId(),
                                "Notification channel group " + groupName.toString() + " has been created");
                        return null;
                    }
                });
            }
        } catch (Throwable t) {
            instance.getConfigLogger()
                    .verbose(instance.getAccountId(), "Failure creating Notification Channel Group", t);
        }
    }

    /**
     * Launches an asynchronous task to delete the notification channel from CleverTap
     * <p/>
     * Use this method when implementing your own FCM handling mechanism. Refer to the
     * SDK documentation for usage scenarios and examples.
     *
     * @param context   A reference to an Android context
     * @param channelId A String for setting the id of the notification channel
     */
    @SuppressWarnings("unused")
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void deleteNotificationChannel(final Context context, final String channelId) {
        final CleverTapAPI instance = getDefaultInstanceOrFirstOther(context);
        if (instance == null) {
            Logger.v("No CleverTap Instance found in CleverTapAPI#deleteNotificationChannel");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Task<Void> task = CTExecutorFactory.executors(instance.coreState.getConfig()).postAsyncSafelyTask();
                task.execute("deletingNotificationChannel", new Callable<Void>() {
                    @Override
                    public Void call() {
                        NotificationManager notificationManager = (NotificationManager) context
                                .getSystemService(NOTIFICATION_SERVICE);
                        if (notificationManager == null) {
                            return null;
                        }
                        notificationManager.deleteNotificationChannel(channelId);
                        instance.getConfigLogger().info(instance.getAccountId(),
                                "Notification channel " + channelId + " has been deleted");
                        return null;
                    }
                });
            }
        } catch (Throwable t) {
            instance.getConfigLogger().verbose(instance.getAccountId(), "Failure deleting Notification Channel", t);
        }
    }

    /**
     * Launches an asynchronous task to delete the notification channel from CleverTap
     * <p/>
     * Use this method when implementing your own FCM handling mechanism. Refer to the
     * SDK documentation for usage scenarios and examples.
     *
     * @param context A reference to an Android context
     * @param groupId A String for setting the id of the notification channel group
     */
    @SuppressWarnings("unused")
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void deleteNotificationChannelGroup(final Context context, final String groupId) {
        final CleverTapAPI instance = getDefaultInstanceOrFirstOther(context);
        if (instance == null) {
            Logger.v("No CleverTap Instance found in CleverTapAPI#deleteNotificationChannelGroup");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Task<Void> task = CTExecutorFactory.executors(instance.coreState.getConfig()).postAsyncSafelyTask();
                task.execute("deletingNotificationChannelGroup", new Callable<Void>() {
                    @Override
                    public Void call() {

                        NotificationManager notificationManager = (NotificationManager) context
                                .getSystemService(NOTIFICATION_SERVICE);
                        if (notificationManager == null) {
                            return null;
                        }
                        notificationManager.deleteNotificationChannelGroup(groupId);
                        instance.getConfigLogger().info(instance.getAccountId(),
                                "Notification channel group " + groupId + " has been deleted");

                        return null;
                    }
                });
            }
        } catch (Throwable t) {
            instance.getConfigLogger()
                    .verbose(instance.getAccountId(), "Failure deleting Notification Channel Group", t);
        }
    }

    /*
    This method was generating the token using Custom FCM Sender ID. The token was overriden from onNewToken
    in the presence of Custom FCM Sender ID present in the AndroidManifest.xml.
    Deprecated now as we can directly call tokenRefresh method from onNewToken implementation
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Deprecated
    public static void fcmTokenRefresh(Context context, String token) {

        for (CleverTapAPI instance : getAvailableInstances(context)) {
            if (instance == null || instance.getCoreState().getConfig().isAnalyticsOnly()) {
                Logger.d("Instance is Analytics Only not processing device token");
                continue;
            }
            instance.getCoreState().getPushProviders().doTokenRefresh(token, PushType.FCM);
        }
    }

    /**
     * Returns the log level set for CleverTapAPI
     *
     * @return The {@link LogLevel} int value
     */
    @SuppressWarnings("WeakerAccess")
    public static int getDebugLevel() {
        return debugLevel;
    }

    /**
     * Enables or disables debugging. If enabled, see debug messages in Android's logcat utility.
     * Debug messages are tagged as CleverTap.
     *
     * @param level Can be one of the following:  -1 (disables all debugging), 0 (default, shows minimal SDK
     *              integration related logging),
     *              1(shows debug output), 3(shows verbose output)
     */
    @SuppressWarnings("WeakerAccess")
    public static void setDebugLevel(int level) {
        debugLevel = level;
    }

    /**
     * Enables or disables debugging. If enabled, see debug messages in Android's logcat utility.
     * Debug messages are tagged as CleverTap.
     *
     * @param level Can be one of the following: LogLevel.OFF (disables all debugging), LogLevel.INFO (default, shows
     *              minimal SDK integration related logging),
     *              LogLevel.DEBUG(shows debug output),LogLevel.VERBOSE(shows verbose output)
     */
    @SuppressWarnings({"unused"})
    public static void setDebugLevel(LogLevel level) {
        debugLevel = level.intValue();
    }

    /**
     * Returns the default shared instance of the CleverTap SDK.
     *
     * @param context     The Android context
     * @param cleverTapID Custom CleverTapID passed by the app
     * @return The {@link CleverTapAPI} object
     */
    @SuppressWarnings("WeakerAccess")
    public static CleverTapAPI getDefaultInstance(Context context, String cleverTapID) {
        // For Google Play Store/Android Studio tracking
        sdkVersion = BuildConfig.SDK_VERSION_STRING;

        if (defaultConfig != null) {
            return instanceWithConfig(context, defaultConfig, cleverTapID);
        } else {
            defaultConfig = getDefaultConfig(context);
            if (defaultConfig != null) {
                return instanceWithConfig(context, defaultConfig, cleverTapID);
            }
        }
        return null;
    }

    /**
     * Returns the default shared instance of the CleverTap SDK.
     *
     * @param context The Android context
     * @return The {@link CleverTapAPI} object
     */
    @SuppressWarnings("WeakerAccess")
    public static @Nullable
    CleverTapAPI getDefaultInstance(Context context) {
        return getDefaultInstance(context, null);
    }

    private static CleverTapAPI fromAccountId(final Context context, final String _accountId) {
        if (instances == null) {
            return createInstanceIfAvailable(context, _accountId);
        }

        for (String accountId : CleverTapAPI.instances.keySet()) {
            CleverTapAPI instance = CleverTapAPI.instances.get(accountId);
            boolean shouldProcess = false;
            if (instance != null) {
                shouldProcess = (_accountId == null && instance.coreState.getConfig().isDefaultInstance())
                        || instance
                        .getAccountId()
                        .equals(_accountId);
            }
            if (shouldProcess) {
                return instance;
            }
        }

        return null;// failed to get instance
    }

    public static HashMap<String, CleverTapAPI> getInstances() {
        return instances;
    }

    public static void setInstances(final HashMap<String, CleverTapAPI> instances) {
        CleverTapAPI.instances = instances;
    }

    /**
     * Checks whether this notification is from CleverTap.
     *
     * @param extras The payload from the FCM intent
     * @return See {@link NotificationInfo}
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public static NotificationInfo getNotificationInfo(final Bundle extras) {
        if (extras == null) {
            return new NotificationInfo(false, false);
        }

        boolean fromCleverTap = extras.containsKey(Constants.NOTIFICATION_TAG);
        boolean shouldRender = fromCleverTap && extras.containsKey("nm");
        return new NotificationInfo(fromCleverTap, shouldRender);
    }

    // other static handlers
    @RestrictTo(Scope.LIBRARY)
    public static void handleNotificationClicked(Context context, Bundle notification) {
        if (notification == null) {
            return;
        }

        String _accountId = null;
        try {
            _accountId = notification.getString(Constants.WZRK_ACCT_ID_KEY);
        } catch (Throwable t) {
            // no-op
        }

        if (instances == null) {
            CleverTapAPI instance = createInstanceIfAvailable(context, _accountId);
            if (instance != null) {
                instance.pushNotificationClickedEvent(notification);
            }
            return;
        }

        for (String accountId : instances.keySet()) {
            CleverTapAPI instance = CleverTapAPI.instances.get(accountId);
            boolean shouldProcess = false;
            if (instance != null) {
                shouldProcess = (_accountId == null && instance.coreState.getConfig().isDefaultInstance())
                        || instance
                        .getAccountId()
                        .equals(_accountId);
            }
            if (shouldProcess) {
                instance.pushNotificationClickedEvent(notification);
                break;
            }
        }
    }

    /**
     * Returns an instance of the CleverTap SDK using CleverTapInstanceConfig.
     *
     * @param context The Android context
     * @param config  The {@link CleverTapInstanceConfig} object
     * @return The {@link CleverTapAPI} object
     */
    public static CleverTapAPI instanceWithConfig(Context context, CleverTapInstanceConfig config) {
        return instanceWithConfig(context, config, null);
    }

    /**
     * Returns an instance of the CleverTap SDK using CleverTapInstanceConfig.
     *
     * @param context The Android context
     * @param config  The {@link CleverTapInstanceConfig} object
     * @return The {@link CleverTapAPI} object
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public static CleverTapAPI instanceWithConfig(Context context, @NonNull CleverTapInstanceConfig config,
            String cleverTapID) {
        //noinspection Constant Conditions
        if (config == null) {
            Logger.v("CleverTapInstanceConfig cannot be null");
            return null;
        }
        if (instances == null) {
            instances = new HashMap<>();
        }

        CleverTapAPI instance = instances.get(config.getAccountId());
        if (instance == null) {
            instance = new CleverTapAPI(context, config, cleverTapID);
            instances.put(config.getAccountId(), instance);
            final CleverTapAPI finalInstance = instance;
            Task<Void> task = CTExecutorFactory.executors(instance.coreState.getConfig()).postAsyncSafelyTask();
            task.execute("recordDeviceIDErrors", new Callable<Void>() {
                @Override
                public Void call() {
                    if (finalInstance.getCleverTapID() != null) {
                        finalInstance.coreState.getLoginController().recordDeviceIDErrors();
                    }
                    return null;
                }
            });
        } else if (instance.isErrorDeviceId() && instance.getConfig().getEnableCustomCleverTapId() && Utils
                .validateCTID(cleverTapID)) {
            instance.coreState.getLoginController().asyncProfileSwitchUser(null, null, cleverTapID);
        }
        Logger.v(config.getAccountId() + ":async_deviceID", "CleverTapAPI instance = " + instance);
        return instance;
    }

    /**
     * Returns whether or not the app is in the foreground.
     *
     * @return The foreground status
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isAppForeground() {
        return CoreMetaData.isAppForeground();
    }

    /**
     * Use this method to notify CleverTap that the app is in foreground
     *
     * @param appForeground boolean true/false
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public static void setAppForeground(boolean appForeground) {
        CoreMetaData.setAppForeground(appForeground);
    }

    @SuppressWarnings("WeakerAccess")
    public static void onActivityPaused() {
        if (instances == null) {
            return;
        }

        for (String accountId : CleverTapAPI.instances.keySet()) {
            CleverTapAPI instance = CleverTapAPI.instances.get(accountId);
            try {
                if (instance != null) {
                    instance.coreState.getActivityLifeCycleManager().activityPaused();
                }
            } catch (Throwable t) {
                // Ignore
            }
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static void onActivityResumed(Activity activity) {
        onActivityResumed(activity, null);
    }

    @SuppressWarnings("WeakerAccess")
    public static void onActivityResumed(Activity activity, String cleverTapID) {
        if (instances == null) {
            CleverTapAPI.createInstanceIfAvailable(activity.getApplicationContext(), null, cleverTapID);
        }

        CoreMetaData.setAppForeground(true);

        if (instances == null) {
            Logger.v("Instances is null in onActivityResumed!");
            return;
        }

        String currentActivityName = CoreMetaData.getCurrentActivityName();
        CoreMetaData.setCurrentActivity(activity);
        if (currentActivityName == null || !currentActivityName.equals(activity.getLocalClassName())) {
            CoreMetaData.incrementActivityCount();
        }

        if (CoreMetaData.getInitialAppEnteredForegroundTime() <= 0) {
            int initialAppEnteredForegroundTime = Utils.getNow();
            CoreMetaData.setInitialAppEnteredForegroundTime(initialAppEnteredForegroundTime);
        }

        for (String accountId : CleverTapAPI.instances.keySet()) {
            CleverTapAPI instance = CleverTapAPI.instances.get(accountId);
            try {
                if (instance != null) {
                    instance.coreState.getActivityLifeCycleManager().activityResumed(activity);
                }
            } catch (Throwable t) {
                Logger.v("Throwable - " + t.getLocalizedMessage());
            }
        }
    }
    private static CleverTapAPI fromBundle(final Context context, final Bundle extras) {
        String _accountId = extras.getString(Constants.WZRK_ACCT_ID_KEY);
        return fromAccountId(context, _accountId);
    }

    @RestrictTo(Scope.LIBRARY)
    public static void runBackgroundIntentService(Context context) {
        if (instances == null) {
            CleverTapAPI instance = CleverTapAPI.getDefaultInstance(context);
            if (instance != null) {
                if (instance.getConfig().isBackgroundSync()) {
                    instance.coreState.getPushProviders().runInstanceJobWork(context, null);
                } else {
                    Logger.d("Instance doesn't allow Background sync, not running the Job");
                }
            }
            return;
        }
        for (String accountId : CleverTapAPI.instances.keySet()) {
            CleverTapAPI instance = CleverTapAPI.instances.get(accountId);
            if (instance == null) {
                continue;
            }
            if (instance.getConfig().isAnalyticsOnly()) {
                Logger.d(accountId, "Instance is Analytics Only not processing device token");
                continue;
            }
            if (!instance.getConfig().isBackgroundSync()) {
                Logger.d(accountId, "Instance doesn't allow Background sync, not running the Job");
                continue;
            }
            instance.coreState.getPushProviders().runInstanceJobWork(context, null);
        }
    }

    @RestrictTo(Scope.LIBRARY)
    public static void runJobWork(Context context, JobParameters parameters) {
        if (instances == null) {
            CleverTapAPI instance = CleverTapAPI.getDefaultInstance(context);
            if (instance != null) {
                if (instance.getConfig().isBackgroundSync()) {
                    instance.coreState.getPushProviders().runInstanceJobWork(context, parameters);
                } else {
                    Logger.d("Instance doesn't allow Background sync, not running the Job");
                }
            }
            return;
        }
        for (String accountId : CleverTapAPI.instances.keySet()) {
            CleverTapAPI instance = CleverTapAPI.instances.get(accountId);
            if (instance != null && instance.getConfig().isAnalyticsOnly()) {
                Logger.d(accountId, "Instance is Analytics Only not running the Job");
                continue;
            }
            if (!(instance != null && instance.getConfig().isBackgroundSync())) {
                Logger.d(accountId, "Instance doesn't allow Background sync, not running the Job");
                continue;
            }
            instance.coreState.getPushProviders().runInstanceJobWork(context, parameters);
        }
    }

    @RestrictTo(Scope.LIBRARY_GROUP)
    public static void tokenRefresh(Context context, String token, PushType pushType) {
        for (CleverTapAPI instance : getAvailableInstances(context)) {
            instance.coreState.getPushProviders().doTokenRefresh(token, pushType);
        }
    }

    /**
     * Checks whether notification permission is granted or denied for Android 13 and above devices.
     * @return boolean Returns true/false based on whether permission is granted or denied.
     */
    @SuppressLint("NewApi")
    public boolean isPushPermissionGranted(){
        if (isPackageAndOsTargetsAbove(context, 32)) {
            return coreState.getInAppController().isPushPermissionGranted();
        } else {
            return false;
        }
    }

    /**
     * Calls the push primer flow for Android 13 and above devices.
     * @param jsonObject JSONObject - Accepts jsonObject created by {@link CTLocalInApp} object
     */
    @SuppressLint("NewApi")
    public void promptPushPrimer(JSONObject jsonObject) {
        if (isPackageAndOsTargetsAbove(context, 32)) {
            coreState.getInAppController().promptPushPrimer(jsonObject);
        } else {
            Logger.v("Ensure your app supports Android 13 to verify permission access for notifications.");
        }
    }

    /**
     * Calls directly hard permission dialog, if push primer is not required.
     * @param showFallbackSettings - boolean - If `showFallbackSettings` is true then we show a alert
     *                             dialog which routes to app's notification settings page.
     */
    @SuppressLint("NewApi")
    public void promptForPushPermission(boolean showFallbackSettings){
        if (isPackageAndOsTargetsAbove(context, 32)) {
            coreState.getInAppController().promptPermission(showFallbackSettings);
        } else {
            Logger.v("Ensure your app supports Android 13 to verify permission access for notifications.");
        }
    }

    // Initialize
    private CleverTapAPI(final Context context, final CleverTapInstanceConfig config, String cleverTapID) {
        this.context = context;

        CoreState coreState = CleverTapFactory
                .getCoreState(context, config, cleverTapID);
        setCoreState(coreState);
        getConfigLogger().verbose(config.getAccountId() + ":async_deviceID", "CoreState is set");

        Task<Void> task = CTExecutorFactory.executors(config).postAsyncSafelyTask();
        task.execute("CleverTapAPI#initializeDeviceInfo", new Callable<Void>() {
            @Override
            public Void call() {
                if (config.isDefaultInstance()) {
                    manifestAsyncValidation();
                }
                return null;
            }
        });

        int now = Utils.getNow();
        if (now - CoreMetaData.getInitialAppEnteredForegroundTime() > 5) {
            this.coreState.getConfig().setCreatedPostAppLaunch();
        }

        task = CTExecutorFactory.executors(config).postAsyncSafelyTask();
        task.execute("setStatesAsync", new Callable<Void>() {
            @Override
            public Void call() {
                CleverTapAPI.this.coreState.getSessionManager().setLastVisitTime();
                CleverTapAPI.this.coreState.getDeviceInfo().setDeviceNetworkInfoReportingFromStorage();
                CleverTapAPI.this.coreState.getDeviceInfo().setCurrentUserOptOutStateFromStorage();
                return null;
            }
        });

        task = CTExecutorFactory.executors(config).postAsyncSafelyTask();
        task.execute("saveConfigtoSharedPrefs", new Callable<Void>() {
            @Override
            public Void call() {
                String configJson = config.toJSONString();
                if (configJson == null) {
                    Logger.v("Unable to save config to SharedPrefs, config Json is null");
                    return null;
                }
                StorageHelper.putString(context, StorageHelper.storageKeyWithSuffix(config, "instance"), configJson);
                return null;
            }
        });

        Logger.i("CleverTap SDK initialized with accountId: " + config.getAccountId() + " accountToken: " + config
                .getAccountToken() + " accountRegion: " + config.getAccountRegion());
    }

    /**
     * Add a unique value to a multi-value user profile property
     * If the property does not exist it will be created
     * <p/>
     * Max 100 values, on reaching 100 cap, oldest value(s) will be removed.
     * Values must be Strings and are limited to 512 characters.
     * <p/>
     * If the key currently contains a scalar value, the key will be promoted to a multi-value property
     * with the current value cast to a string and the new value(s) added
     *
     * @param key   String
     * @param value String
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void addMultiValueForKey(String key, String value) {
        if (value == null || value.isEmpty()) {
            coreState.getAnalyticsManager()._generateEmptyMultiValueError(key);
            return;
        }

        addMultiValuesForKey(key, new ArrayList<>(Collections.singletonList(value)));
    }

    /**
     * Add a collection of unique values to a multi-value user profile property
     * If the property does not exist it will be created
     * <p/>
     * Max 100 values, on reaching 100 cap, oldest value(s) will be removed.
     * Values must be Strings and are limited to 512 characters.
     * <p/>
     * If the key currently contains a scalar value, the key will be promoted to a multi-value property
     * with the current value cast to a string and the new value(s) added
     *
     * @param key    String
     * @param values {@link ArrayList} with String values
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void addMultiValuesForKey(final String key, final ArrayList<String> values) {
        coreState.getAnalyticsManager().addMultiValuesForKey(key, values);
    }

    /**
     * Deletes the given {@link CTInboxMessage} object
     *
     * @param message {@link CTInboxMessage} public object of inbox message
     */
    @SuppressWarnings({"unused"})
    public void deleteInboxMessage(final CTInboxMessage message) {
        if (coreState.getControllerManager().getCTInboxController() != null) {
            coreState.getControllerManager().getCTInboxController().deleteInboxMessage(message);
        } else {
            getConfigLogger().debug(getAccountId(), "Notification Inbox not initialized");
        }
    }

    /**
     * Deletes the {@link CTInboxMessage} object for given messageId
     *
     * @param messageId String - messageId of {@link CTInboxMessage} public object of inbox message
     */
    @SuppressWarnings("unused")
    public void deleteInboxMessage(String messageId) {
        CTInboxMessage message = getInboxMessageForId(messageId);
        deleteInboxMessage(message);
    }

    /**
     * Deletes multiple {@link CTInboxMessage} objects for given list of messageIDs
     *
     * @param messageIDs {@link ArrayList} with String values - list of messageIDs of {@link CTInboxMessage} public object of inbox message
     */
    public void deleteInboxMessagesForIDs(final ArrayList<String> messageIDs){
        if (coreState.getControllerManager().getCTInboxController() != null) {
            coreState.getControllerManager().getCTInboxController().deleteInboxMessagesForIDs(messageIDs);
        } else {
            getConfigLogger().debug(getAccountId(), "Notification Inbox not initialized");
        }
    }

    /**
     * Disables the Profile/Events Read and Synchronization API
     * Personalization is enabled by default
     */
    @SuppressWarnings({"unused"})
    public void disablePersonalization() {
        this.coreState.getConfig().enablePersonalization(false);
    }

    /**
     * Suspends the display of InApp Notifications and discards any new InApp Notifications to be shown
     * after this method is called.
     * The InApp Notifications will be displayed only once resumeInAppNotifications() is called.
     */
    public void discardInAppNotifications() {
        if (!getCoreState().getConfig().isAnalyticsOnly()) {
            getConfigLogger().debug(getAccountId(), "Discarding InApp Notifications...");
            getConfigLogger().debug(getAccountId(),
                    "Please Note - InApp Notifications will be dropped till resumeInAppNotifications() is not called again");
            getCoreState().getInAppController().discardInApps();
        } else {
            getConfigLogger().debug(getAccountId(),
                    "CleverTap instance is set for Analytics only! Cannot discard InApp Notifications.");
        }
    }

    /**
     * Use this method to enable device network-related information tracking, including IP address.
     * This reporting is disabled by default.  To re-disable tracking call this method with enabled set to false.
     *
     * @param value boolean Whether device network info reporting should be enabled/disabled.
     */
    @SuppressWarnings({"unused"})
    public void enableDeviceNetworkInfoReporting(boolean value) {
        coreState.getDeviceInfo().enableDeviceNetworkInfoReporting(value);
    }

    /**
     * Enables the Profile/Events Read and Synchronization API
     * Personalization is enabled by default
     */
    @SuppressWarnings({"unused"})
    public void enablePersonalization() {
        this.coreState.getConfig().enablePersonalization(true);
    }

    /**
     * <p style="color:#4d2e00;background:#ffcc99;font-weight: bold" >
     *      Note: This method has been deprecated since v5.0.0 and will be removed in the future versions of this SDK.
     * </p>
     * @return object of {@link CTFeatureFlagsController}
     * Handler to get the feature flag values
     */
    @Deprecated
    public CTFeatureFlagsController featureFlag() {
        if (getConfig().isAnalyticsOnly()) {
            getConfig().getLogger()
                    .debug(getAccountId(), "Feature flag is not supported with analytics only configuration");
        }
        return coreState.getControllerManager().getCTFeatureFlagsController();
    }

    /**
     * Sends all the events in the event queue.
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void flush() {
        coreState.getBaseEventQueueManager().flush();
    }

    /**
     * This method is used to set the SCDomain callback.
     * <p>
     * Register to handle the domain related events from CleverTap
     * This is to be used only by clevertap-signedcall-sdk
     *
     * @param scDomainListener - the {@link SCDomainListener} instance
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public void setSCDomainListener(SCDomainListener scDomainListener) {
        coreState.getCallbackManager().setSCDomainListener(scDomainListener);

        if(coreState.getNetworkManager() != null) {
            NetworkManager networkManager = (NetworkManager) coreState.getNetworkManager();
            String domain = networkManager.getDomainFromPrefsOrMetadata(EventGroup.REGULAR);
            if(domain != null) {
                scDomainListener.onSCDomainAvailable(getSCDomain(domain));
            }
        }
    }

    public String getAccountId() {
        return coreState.getConfig().getAccountId();
    }

    private String getProxyDomain() {
        return coreState.getConfig().getProxyDomain();
    }

    /**
     * Getter for retrieving all the Display Units.
     *
     * @return ArrayList<CleverTapDisplayUnit> - could be null, if there is no Display Unit campaigns
     */
    @Nullable
    public ArrayList<CleverTapDisplayUnit> getAllDisplayUnits() {

        if (coreState.getControllerManager().getCTDisplayUnitController() != null) {
            return coreState.getControllerManager().getCTDisplayUnitController().getAllDisplayUnits();
        } else {
            getConfigLogger()
                    .verbose(getAccountId(), Constants.FEATURE_DISPLAY_UNIT + "Failed to get all Display Units");
            return null;
        }
    }

    /**
     * Returns an ArrayList of all {@link CTInboxMessage} objects
     *
     * @return ArrayList of {@link CTInboxMessage} of Inbox Messages
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public ArrayList<CTInboxMessage> getAllInboxMessages() {
        Logger.d("CleverTapAPI:getAllInboxMessages: called" );
        ArrayList<CTInboxMessage> inboxMessageArrayList = new ArrayList<>();
        synchronized (coreState.getCTLockManager().getInboxControllerLock()) {
            if (coreState.getControllerManager().getCTInboxController() != null) {
                ArrayList<CTMessageDAO> messageDAOArrayList =
                        coreState.getControllerManager().getCTInboxController().getMessages();
                for (CTMessageDAO messageDAO : messageDAOArrayList) {
                    Logger.v("CTMessage Dao - " + messageDAO.toJSON().toString());
                    inboxMessageArrayList.add(new CTInboxMessage(messageDAO.toJSON()));
                }
                return inboxMessageArrayList;
            } else {
                getConfigLogger().debug(getAccountId(), "Notification Inbox not initialized");
                return inboxMessageArrayList; //return empty list to avoid null pointer exceptions
            }
        }
    }

    /**
     * Returns the CTInboxListener object
     *
     * @return An {@link CTInboxListener} object
     */
    @SuppressWarnings({"unused"})
    public CTInboxListener getCTNotificationInboxListener() {
        return coreState.getCallbackManager().getInboxListener();
    }

    /**
     * This method sets the CTInboxListener
     *
     * @param notificationInboxListener An {@link CTInboxListener} object
     */
    @SuppressWarnings({"unused"})
    public void setCTNotificationInboxListener(CTInboxListener notificationInboxListener) {
        coreState.getCallbackManager().setInboxListener(notificationInboxListener);
    }

    //Debug

    /**
     * Returns the CTPushAmpListener object
     *
     * @return The {@link CTPushAmpListener} object
     */
    @SuppressWarnings("WeakerAccess")
    public CTPushAmpListener getCTPushAmpListener() {
        return coreState.getCallbackManager().getPushAmpListener();
    }

    /**
     * This method is used to set the CTPushAmpListener
     *
     * @param pushAmpListener - The{@link CTPushAmpListener} object
     */
    @SuppressWarnings("unused")
    public void setCTPushAmpListener(CTPushAmpListener pushAmpListener) {
        coreState.getCallbackManager().setPushAmpListener(pushAmpListener);
    }

    /**
     * Returns the CTPushNotificationListener object
     *
     * @return The {@link CTPushNotificationListener} object
     */
    @SuppressWarnings("WeakerAccess")
    public CTPushNotificationListener getCTPushNotificationListener() {
        return coreState.getCallbackManager().getPushNotificationListener();
    }

    /**
     * This method is used to set the CTPushNotificationListener
     *
     * @param pushNotificationListener - The{@link CTPushNotificationListener} object
     */
    @SuppressWarnings("unused")
    public void setCTPushNotificationListener(CTPushNotificationListener pushNotificationListener) {
        coreState.getCallbackManager().setPushNotificationListener(pushNotificationListener);
    }

    /**
     * Returns a unique CleverTap identifier suitable for use with install attribution providers.
     *
     * @return The attribution identifier currently being used to identify this user.
     *
     * <p><br><span style="background:#ffcc99" >&#9888; this method may take a long time to return,
     * so you should not call it from the application main thread</span></p>
     *
     * <p style="color:red;font-size: 25px;margin-left:10px">&#9760;</p>
     * <b><span style="color:#4d2e00;background:#ffcc99" >Deprecated as of version <code>4.2.0</code> and will be
     * removed in future versions</span>
     * </b><br>
     * <code>Use {@link CleverTapAPI#getCleverTapID(OnInitCleverTapIDListener)} instead</code>
     */
    @SuppressWarnings("unused")
    @WorkerThread
    @Deprecated
    public String getCleverTapAttributionIdentifier() {
        return coreState.getDeviceInfo().getAttributionID();
    }

    /**
     * Returns a unique identifier by which CleverTap identifies this user.
     *
     * @return The user identifier currently being used to identify this user.
     *
     * <p><br><span style="color:red;background:#ffcc99" >&#9888; this method may take a long time to return,
     * so you should not call it from the application main thread</span></p>
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    @WorkerThread
    public String getCleverTapID() {
        return coreState.getDeviceInfo().getDeviceID();
    }

    /**
     * This method is used to decrement the given value
     *
     * Number should be in positive range
     *
     * @param key   String
     * @param value Number
     */
    @SuppressWarnings("unused")
    public void decrementValue(final String key, final Number value) {
        coreState.getAnalyticsManager().decrementValue(key, value);
    }

    @RestrictTo(Scope.LIBRARY)
    public CoreState getCoreState() {
        return coreState;
    }

    void setCoreState(final CoreState cleverTapState) {
        coreState = cleverTapState;
    }

    /**
     * Returns the total count of the specified event
     *
     * @param event The event for which you want to get the total count
     * @return Total count in int
     */
    @SuppressWarnings({"unused"})
    public int getCount(String event) {
        EventDetail eventDetail = coreState.getLocalDataStore().getEventDetail(event);
        if (eventDetail != null) {
            return eventDetail.getCount();
        }

        return -1;
    }

    /**
     * Returns an EventDetail object for the particular event passed. EventDetail consists of event name, count, first
     * time
     * and last time timestamp of the event.
     *
     * @param event The event name for which you want the Event details
     * @return The {@link EventDetail} object
     */
    @SuppressWarnings({"unused"})
    public EventDetail getDetails(String event) {
        return coreState.getLocalDataStore().getEventDetail(event);
    }

    /**
     * Returns the device push token or null
     *
     * @param type com.clevertap.android.sdk.PushType (FCM)
     * @return String device token or null
     * NOTE: on initial install calling getDevicePushToken may return null, as the device token is
     * not yet available
     * Implement CleverTapAPI.DevicePushTokenRefreshListener to get a callback once the token is
     * available
     */
    @SuppressWarnings("unused")
    public String getDevicePushToken(final PushType type) {
        return coreState.getPushProviders().getCachedToken(type);
    }

    /**
     * Returns the DevicePushTokenRefreshListener
     *
     * @return The {@link DevicePushTokenRefreshListener} object
     */
    @SuppressWarnings("unused")
    public DevicePushTokenRefreshListener getDevicePushTokenRefreshListener() {
        return coreState.getPushProviders().getDevicePushTokenRefreshListener();
    }

    /**
     * This method is used to set the DevicePushTokenRefreshListener object
     *
     * @param tokenRefreshListener The {@link DevicePushTokenRefreshListener} object
     */
    @SuppressWarnings("unused")
    public void setDevicePushTokenRefreshListener(DevicePushTokenRefreshListener tokenRefreshListener) {
        coreState.getPushProviders().setDevicePushTokenRefreshListener(tokenRefreshListener);

    }

    /**
     * This method is used to set the RequestDevicePushTokenListener object
     *
     * @param requestTokenListener The {@link RequestDevicePushTokenListener} object
     */
    @SuppressWarnings("unused")
    @RestrictTo(Scope.LIBRARY_GROUP)
    public void setRequestDevicePushTokenListener(RequestDevicePushTokenListener requestTokenListener) {
        try {
            Logger.v(LOG_TAG, FCM_LOG_TAG + "Requesting FCM token using googleservices.json");
            FirebaseMessaging
                    .getInstance()
                    .getToken()
                    .addOnCompleteListener
                            (new OnCompleteListener<String>() {
                                 @Override
                                 public void onComplete(@NonNull final com.google.android.gms.tasks.Task<String> task) {
                                     if (!task.isSuccessful()) {
                                         Logger.v(LOG_TAG,
                                                 FCM_LOG_TAG + "FCM token using googleservices.json failed",
                                                 task.getException());
                                         requestTokenListener.onDevicePushToken(null, FCM);
                                         return;
                                     }
                                     String token = task.getResult() != null ? task.getResult() : null;
                                     Logger.v(LOG_TAG,
                                             FCM_LOG_TAG + "FCM token using googleservices.json - " + token);
                                     requestTokenListener.onDevicePushToken(token, FCM);
                                 }
                             }
                            );
        } catch (Throwable t) {
            Logger.v(LOG_TAG, FCM_LOG_TAG + "Error requesting FCM token", t);
            requestTokenListener.onDevicePushToken(null, FCM);
        }
    }

    //Util

    /**
     * Getter for retrieving Display Unit using the unitID
     *
     * @param unitID - unitID of the Display Unit {@link CleverTapDisplayUnit#getUnitID()}
     * @return CleverTapDisplayUnit - could be null, if there is no Display Unit campaign with the identifier
     */
    @Nullable
    public CleverTapDisplayUnit getDisplayUnitForId(String unitID) {
        if (coreState.getControllerManager().getCTDisplayUnitController() != null) {
            return coreState.getControllerManager().getCTDisplayUnitController().getDisplayUnitForID(unitID);
        } else {
            getConfigLogger().verbose(getAccountId(),
                    Constants.FEATURE_DISPLAY_UNIT + "Failed to get Display Unit for id: " + unitID);
            return null;
        }
    }

    /**
     * Returns the timestamp of the first time the given event was raised
     *
     * @param event The event name for which you want the first time timestamp
     * @return The timestamp in int
     */
    @SuppressWarnings({"unused"})
    public int getFirstTime(String event) {
        EventDetail eventDetail = coreState.getLocalDataStore().getEventDetail(event);
        if (eventDetail != null) {
            return eventDetail.getFirstTime();
        }

        return -1;
    }

    /**
     * Returns the GeofenceCallback object
     *
     * @return The {@link GeofenceCallback} object
     */
    @SuppressWarnings("unused")
    public GeofenceCallback getGeofenceCallback() {
        return coreState.getCallbackManager().getGeofenceCallback();
    }

    /**
     * This method is used to set the geofence callback
     * Register to handle geofence responses from CleverTap
     * This is to be used only by clevertap-geofence-sdk
     *
     * @param geofenceCallback The {@link GeofenceCallback} instance
     */

    @SuppressWarnings("unused")
    public void setGeofenceCallback(GeofenceCallback geofenceCallback) {
        coreState.getCallbackManager().setGeofenceCallback(geofenceCallback);
    }

    /**
     * Returns a Map of event names and corresponding event details of all the events raised
     *
     * @return A Map of Event Name and its corresponding EventDetail object
     */
    @SuppressWarnings({"unused"})
    public Map<String, EventDetail> getHistory() {
        return coreState.getLocalDataStore().getEventHistory(context);
    }

    /**
     * Returns the InAppNotificationListener object
     *
     * @return An {@link InAppNotificationListener} object
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public InAppNotificationListener getInAppNotificationListener() {
        return coreState.getCallbackManager().getInAppNotificationListener();
    }

    /**
     * This method sets the InAppNotificationListener
     *
     * @param inAppNotificationListener An {@link InAppNotificationListener} object
     */
    @SuppressWarnings({"unused"})
    public void setInAppNotificationListener(InAppNotificationListener inAppNotificationListener) {
        coreState.getCallbackManager().setInAppNotificationListener(inAppNotificationListener);
    }

    /**
     * This method unregisters the given instance of the PushPermissionResponseListener if
     * previously registered.
     * <p>
     * Use this method to stop observing the push permission result.
     *
     * @param pushPermissionResponseListener An {@link PushPermissionResponseListener} object
     */
    @SuppressWarnings({"unused"})
    public void unregisterPushPermissionNotificationResponseListener(PushPermissionResponseListener
                                                                           pushPermissionResponseListener) {
        coreState.getCallbackManager().
                unregisterPushPermissionResponseListener(pushPermissionResponseListener);
    }

    /**
     * This method registers the PushPermissionNotificationResponseListener.
     * <p>
     * Call this method only from the onCreate() of the activity/fragment and unregister the
     * listener from the onDestroy() method using the
     * {@link #unregisterPushPermissionNotificationResponseListener(PushPermissionResponseListener)}
     *
     * @param pushPermissionResponseListener An {@link PushPermissionResponseListener} object
     */
    @SuppressWarnings({"unused"})
    public void registerPushPermissionNotificationResponseListener(PushPermissionResponseListener
                                                                          pushPermissionResponseListener) {
        coreState.getCallbackManager().
                registerPushPermissionResponseListener(pushPermissionResponseListener);
    }

    /**
     * Returns the count of all inbox messages for the user
     *
     * @return int - count of all inbox messages
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public int getInboxMessageCount() {
        synchronized (coreState.getCTLockManager().getInboxControllerLock()) {
            if (coreState.getControllerManager().getCTInboxController() != null) {
                return coreState.getControllerManager().getCTInboxController().count();
            } else {
                getConfigLogger().debug(getAccountId(), "Notification Inbox not initialized");
                return -1;
            }
        }
    }

    /**
     * Returns the {@link CTInboxMessage} object that belongs to the given message id
     *
     * @param messageId String - unique id of the inbox message
     * @return {@link CTInboxMessage} public object of inbox message
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public CTInboxMessage getInboxMessageForId(String messageId) {
        Logger.d("CleverTapAPI:getInboxMessageForId() called with: messageId = [" + messageId + "]");
        synchronized (coreState.getCTLockManager().getInboxControllerLock()) {
            if (coreState.getControllerManager().getCTInboxController() != null) {
                CTMessageDAO message = coreState.getControllerManager().getCTInboxController()
                        .getMessageForId(messageId);
                return (message != null) ? new CTInboxMessage(message.toJSON()) : null;
            } else {
                getConfigLogger().debug(getAccountId(), "Notification Inbox not initialized");
                return null;
            }
        }
    }

    /**
     * Returns the count of total number of unread inbox messages for the user
     *
     * @return int - count of all unread messages
     */
    @SuppressWarnings({"unused"})
    public int getInboxMessageUnreadCount() {
        synchronized (coreState.getCTLockManager().getInboxControllerLock()) {
            if (coreState.getControllerManager().getCTInboxController() != null) {
                return coreState.getControllerManager().getCTInboxController().unreadCount();
            } else {
                getConfigLogger().debug(getAccountId(), "Notification Inbox not initialized");
                return -1;
            }
        }
    }

    /**
     * Returns the timestamp of the last time the given event was raised
     *
     * @param event The event name for which you want the last time timestamp
     * @return The timestamp in int
     */
    @SuppressWarnings({"unused"})
    public int getLastTime(String event) {
        EventDetail eventDetail = coreState.getLocalDataStore().getEventDetail(event);
        if (eventDetail != null) {
            return eventDetail.getLastTime();
        }

        return -1;
    }

    /**
     * get the current device location
     * requires Location Permission in AndroidManifest e.g. "android.permission.ACCESS_COARSE_LOCATION"
     * You can then use the returned Location value to update the user profile location in CleverTap via {@link
     * #setLocation(Location)}
     *
     * @return android.location.Location
     */
    @SuppressWarnings({"unused"})
    public Location getLocation() {
        return coreState.getLocationManager()._getLocation();
    }

    /**
     * set the user profile location in CleverTap
     * location can then be used for geo-segmentation etc.
     *
     * @param location android.location.Location
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setLocation(Location location) {
        coreState.getLocationManager()._setLocation(location);
    }

    /**
     * Returns the timestamp of the previous visit
     *
     * @return Timestamp of previous visit in int
     */
    @SuppressWarnings({"unused"})
    public int getPreviousVisitTime() {
        return coreState.getSessionManager().getLastVisitTime();
    }

    /**
     * Return the user profile property value for the specified key
     *
     * @param name String
     * @return {@link JSONArray}, String or null
     */
    @SuppressWarnings({"unused"})
    public Object getProperty(String name) {
        if (!coreState.getConfig().isPersonalizationEnabled()) {
            return null;
        }
        return coreState.getLocalDataStore().getProfileProperty(name);
    }

    /**
     * Returns the token for a particular push type
     */
    public String getPushToken(@NonNull PushType pushType) {
        return coreState.getPushProviders().getCachedToken(pushType);
    }

    /**
     * Returns the number of screens which have been displayed by the app
     *
     * @return Total number of screens which have been displayed by the app
     */
    @SuppressWarnings({"unused"})
    public int getScreenCount() {
        return CoreMetaData.getActivityCount();
    }

    /**
     * Returns the SyncListener object
     *
     * @return The {@link SyncListener} object
     */
    @SuppressWarnings("WeakerAccess")
    public SyncListener getSyncListener() {
        return coreState.getCallbackManager().getSyncListener();
    }

    /**
     * This method is used to set the SyncListener
     *
     * @param syncListener The {@link SyncListener} object
     */
    @SuppressWarnings("unused")
    public void setSyncListener(SyncListener syncListener) {
        coreState.getCallbackManager().setSyncListener(syncListener);
    }

    /**
     * Returns the time elapsed by the user on the app
     *
     * @return Time elapsed by user on the app in int
     */
    @SuppressWarnings({"unused"})
    public int getTimeElapsed() {
        int currentSession = coreState.getCoreMetaData().getCurrentSessionId();
        if (currentSession == 0) {
            return -1;
        }

        int now = Utils.getNow();
        return now - currentSession;
    }

    /**
     * Returns the total number of times the app has been launched
     *
     * @return Total number of app launches in int
     */
    @SuppressWarnings({"unused"})
    public int getTotalVisits() {
        EventDetail ed = coreState.getLocalDataStore().getEventDetail(Constants.APP_LAUNCHED_EVENT);
        if (ed != null) {
            return ed.getCount();
        }

        return 0;
    }

    /**
     * Returns a UTMDetail object which consists of UTM parameters like source, medium & campaign
     *
     * @return The {@link UTMDetail} object
     */
    @SuppressWarnings({"unused"})
    public UTMDetail getUTMDetails() {
        UTMDetail ud = new UTMDetail();
        ud.setSource(coreState.getCoreMetaData().getSource());
        ud.setMedium(coreState.getCoreMetaData().getMedium());
        ud.setCampaign(coreState.getCoreMetaData().getCampaign());
        return ud;
    }

    /**
     * Returns an ArrayList of unread {@link CTInboxMessage} objects
     *
     * @return ArrayList of {@link CTInboxMessage} of unread Inbox Messages
     */
    @SuppressWarnings({"unused"})
    public ArrayList<CTInboxMessage> getUnreadInboxMessages() {
        ArrayList<CTInboxMessage> inboxMessageArrayList = new ArrayList<>();
        synchronized (coreState.getCTLockManager().getInboxControllerLock()) {
            if (coreState.getControllerManager().getCTInboxController() != null) {
                ArrayList<CTMessageDAO> messageDAOArrayList =
                        coreState.getControllerManager().getCTInboxController().getUnreadMessages();
                for (CTMessageDAO messageDAO : messageDAOArrayList) {
                    inboxMessageArrayList.add(new CTInboxMessage(messageDAO.toJSON()));
                }
                return inboxMessageArrayList;
            } else {
                getConfigLogger().debug(getAccountId(), "Notification Inbox not initialized");
                return inboxMessageArrayList; //return empty list to avoid null pointer exceptions
            }
        }
    }

    /**
     * Initializes the inbox controller and sends a callback to the {@link CTInboxListener}
     * This method needs to be called separately for each instance of {@link CleverTapAPI}
     */
    @SuppressWarnings({"unused"})
    public void initializeInbox() {
        coreState.getControllerManager().initializeInbox();
    }

    /**
     * Marks the given {@link CTInboxMessage} object as read
     *
     * @param message {@link CTInboxMessage} public object of inbox message
     */
    //marks the message as read
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void markReadInboxMessage(final CTInboxMessage message) {
        if (coreState.getControllerManager().getCTInboxController() != null) {
            coreState.getControllerManager().getCTInboxController().markReadInboxMessage(message);
        } else {
            getConfigLogger().debug(getAccountId(), "Notification Inbox not initialized");
        }
    }

    /**
     * Marks the given messageId of {@link CTInboxMessage} object as read
     *
     * @param messageId String - messageId of {@link CTInboxMessage} public object of inbox message
     */
    @SuppressWarnings("unused")
    public void markReadInboxMessage(String messageId) {
        CTInboxMessage message = getInboxMessageForId(messageId);
        markReadInboxMessage(message);
    }

    /**
     * Marks multiple {@link CTInboxMessage} objects as read for given list of messageIDs
     *
     * @param messageIDs {@link ArrayList} with String values - list of messageIDs of {@link CTInboxMessage} public object of inbox message
     */
    public void markReadInboxMessagesForIDs(final ArrayList<String> messageIDs){
        if (coreState.getControllerManager().getCTInboxController() != null) {
            coreState.getControllerManager().getCTInboxController().markReadInboxMessagesForIDs(messageIDs);
        } else {
            getConfigLogger().debug(getAccountId(), "Notification Inbox not initialized");
        }
    }

    @Override
    public void messageDidClick(CTInboxActivity ctInboxActivity, int contentPageIndex, CTInboxMessage inboxMessage, Bundle data, HashMap<String, String> keyValue, int buttonIndex) {

        coreState.getAnalyticsManager().pushInboxMessageStateEvent(true, inboxMessage, data);

        Logger.v("clicked inbox notification.");
        //notify the onInboxItemClicked callback if the listener is set.
        if (inboxMessageListener != null && inboxMessageListener.get() != null) {
            inboxMessageListener.get().onInboxItemClicked(inboxMessage, contentPageIndex, buttonIndex);
        }

        if (keyValue != null && !keyValue.isEmpty()) {
            Logger.v("clicked button of an inbox notification.");
            //notify the onInboxButtonClick callback if the listener is set.
            if (inboxMessageButtonListener != null && inboxMessageButtonListener.get() != null) {
                inboxMessageButtonListener.get().onInboxButtonClick(keyValue);
            }
        }
    }

    //Session

    @Override
    public void messageDidShow(CTInboxActivity ctInboxActivity, final CTInboxMessage inboxMessage, final Bundle data) {
        Task<Void> task = CTExecutorFactory.executors(coreState.getConfig()).postAsyncSafelyTask();
        task.execute("handleMessageDidShow", new Callable<Void>() {
            @Override
            public Void call() {
                Logger.d("CleverTapAPI:messageDidShow() called  in async with: messageId = [" + inboxMessage.getMessageId() + "]");

                CTInboxMessage message = getInboxMessageForId(inboxMessage.getMessageId());
                if (!message.isRead()) {
                    markReadInboxMessage(inboxMessage);
                    coreState.getAnalyticsManager().pushInboxMessageStateEvent(false, inboxMessage, data);
                }
                return null;
            }
        });
    }

    /**
     * Creates a separate and distinct user profile identified by one or more of Identity,
     * Email, FBID or GPID values,
     * and populated with the key-values included in the profile map argument.
     * <p>
     * If your app is used by multiple users, you can use this method to assign them each a
     * unique profile to track them separately.
     * <p>
     * If instead you wish to assign multiple Identity, Email, FBID and/or GPID values to the same
     * user profile,
     * use profile.push rather than this method.
     * <p>
     * If none of Identity, Email, FBID or GPID is included in the profile map,
     * all profile map values will be associated with the current user profile.
     * <p>
     * When initially installed on this device, your app is assigned an "anonymous" profile.
     * The first time you identify a user on this device (whether via onUserLogin or profilePush),
     * the "anonymous" history on the device will be associated with the newly identified user.
     * <p>
     * Then, use this method to switch between subsequent separate identified users.
     * <p>
     * Please note that switching from one identified user to another is a costly operation
     * in that the current session for the previous user is automatically closed
     * and data relating to the old user removed, and a new session is started
     * for the new user and data for that user refreshed via a network call to CleverTap.
     * In addition, any global frequency caps are reset as part of the switch.
     *
     * @param profile     The map keyed by the type of identity, with the value as the identity
     * @param cleverTapID Custom CleverTap ID passed by the App
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void onUserLogin(final Map<String, Object> profile, final String cleverTapID) {
        coreState.getLoginController().onUserLogin(profile, cleverTapID);
    }

    /**
     * Creates a separate and distinct user profile identified by one or more of Identity,
     * Email, FBID or GPID values,
     * and populated with the key-values included in the profile map argument.
     * <p>
     * If your app is used by multiple users, you can use this method to assign them each a
     * unique profile to track them separately.
     * <p>
     * If instead you wish to assign multiple Identity, Email, FBID and/or GPID values to the same
     * user profile,
     * use profile.push rather than this method.
     * <p>
     * If none of Identity, Email, FBID or GPID is included in the profile map,
     * all profile map values will be associated with the current user profile.
     * <p>
     * When initially installed on this device, your app is assigned an "anonymous" profile.
     * The first time you identify a user on this device (whether via onUserLogin or profilePush),
     * the "anonymous" history on the device will be associated with the newly identified user.
     * <p>
     * Then, use this method to switch between subsequent separate identified users.
     * <p>
     * Please note that switching from one identified user to another is a costly operation
     * in that the current session for the previous user is automatically closed
     * and data relating to the old user removed, and a new session is started
     * for the new user and data for that user refreshed via a network call to CleverTap.
     * In addition, any global frequency caps are reset as part of the switch.
     *
     * @param profile The map keyed by the type of identity, with the value as the identity
     */
    @SuppressWarnings("unused")
    public void onUserLogin(final Map<String, Object> profile) {
        onUserLogin(profile, null);
    }

    /**
     * <p style="color:#4d2e00;background:#ffcc99;font-weight: bold" >
     *      Note: This method has been deprecated since v5.0.0 and will be removed in the future versions of this SDK.
     * </p>
     * The handle for product config functionalities(fetch/activate etc.)
     *
     * @return - the instance of {@link CTProductConfigController}
     */
    @SuppressWarnings("WeakerAccess")
    @Deprecated
    public CTProductConfigController productConfig() {
        if (getConfig().isAnalyticsOnly()) {
            getConfig().getLogger().debug(getAccountId(),
                    "Product config is not supported with analytics only configuration");
        }
        return coreState.getCtProductConfigController();
    }

    /**
     * Sends the Baidu registration ID to CleverTap.
     *
     * @param regId    The Baidu registration ID
     * @param register Boolean indicating whether to register
     *                 or not for receiving push messages from CleverTap.
     *                 Set this to true to receive push messages from CleverTap,
     *                 and false to not receive any messages from CleverTap.
     */
    @SuppressWarnings("unused")
    public void pushBaiduRegistrationId(String regId, boolean register) {
        coreState.getPushProviders().handleToken(regId, PushType.BPS, register);
    }

    /**
     * Push Charged event, which describes a purchase made.
     *
     * @param chargeDetails A {@link HashMap}, with keys as strings, and values as {@link String},
     *                      {@link Integer}, {@link Long}, {@link Boolean}, {@link Float}, {@link Double},
     *                      {@link java.util.Date}, or {@link Character}
     * @param items         An {@link ArrayList} which contains up to 15 {@link HashMap} objects,
     *                      where each HashMap object describes a particular item purchased
     */
    @SuppressWarnings({"unused"})
    public void pushChargedEvent(HashMap<String, Object> chargeDetails,
            ArrayList<HashMap<String, Object>> items) {
        coreState.getAnalyticsManager().pushChargedEvent(chargeDetails, items);
    }

    /**
     * Use this method to pass the deeplink with UTM parameters to track installs
     *
     * @param uri URI of the deeplink
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void pushDeepLink(Uri uri) {
        coreState.getAnalyticsManager().pushDeepLink(uri, false);
    }

    /**
     * Raises the Display Unit Clicked event
     *
     * @param unitID - unitID of the Display Unit{@link CleverTapDisplayUnit#getUnitID()}
     */
    @SuppressWarnings("unused")
    public void pushDisplayUnitClickedEventForID(String unitID) {
        coreState.getAnalyticsManager().pushDisplayUnitClickedEventForID(unitID);
    }

    /**
     * Raises the Display Unit Viewed event
     *
     * @param unitID - unitID of the Display Unit{@link CleverTapDisplayUnit#getUnitID()}
     */
    @SuppressWarnings("unused")
    public void pushDisplayUnitViewedEventForID(String unitID) {
        coreState.getAnalyticsManager().pushDisplayUnitViewedEventForID(unitID);
    }

    /**
     * Internally records an "Error Occurred" event, which can be viewed in the dashboard.
     *
     * @param errorMessage The error message
     * @param errorCode    The error code
     */
    @SuppressWarnings({"unused"})
    public void pushError(final String errorMessage, final int errorCode) {
        coreState.getAnalyticsManager().pushError(errorMessage, errorCode);
    }

    /**
     * Pushes a basic event.
     *
     * @param eventName The name of the event
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void pushEvent(String eventName) {
        if (eventName == null || eventName.trim().equals("")) {
            return;
        }
        pushEvent(eventName, null);
    }

    /**
     * Push an event with a set of attribute pairs.
     *
     * @param eventName    The name of the event
     * @param eventActions A {@link HashMap}, with keys as strings, and values as {@link String},
     *                     {@link Integer}, {@link Long}, {@link Boolean}, {@link Float}, {@link Double},
     *                     {@link java.util.Date}, or {@link Character}
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void pushEvent(String eventName, Map<String, Object> eventActions) {
        coreState.getAnalyticsManager().pushEvent(eventName, eventActions);
    }


    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setCommonEventData(Map<String, Object> data) {
        coreState.getBaseEventQueueManager().setCommonEventData(data);
    }

    /**
     * Sends the FCM registration ID to CleverTap.
     *
     * @param fcmId    The FCM registration ID
     * @param register Boolean indicating whether to register
     *                 or not for receiving push messages from CleverTap.
     *                 Set this to true to receive push messages from CleverTap,
     *                 and false to not receive any messages from CleverTap.
     */
    @SuppressWarnings("unused")
    public void pushFcmRegistrationId(String fcmId, boolean register) {
        coreState.getPushProviders().handleToken(fcmId, PushType.FCM, register);
    }

    /**
     * Pushes a Signed Call event to CleverTap with a set of attribute pairs.
     *
     * @param eventName    The name of the event
     * @param eventProperties The {@link JSONObject} object that contains the
     *                           event properties regarding Signed Call event
     */
    @SuppressWarnings("unused")
    public Future<?> pushSignedCallEvent(String eventName, JSONObject eventProperties) {
        return coreState.getAnalyticsManager()
                .raiseEventForSignedCall(eventName, eventProperties);
    }

    /**
     * Used to record errors of the Geofence module
     *
     * @param errorCode    - int - predefined error code for geofences
     * @param errorMessage - String - error message
     */
    @SuppressWarnings("unused")
    public void pushGeoFenceError(int errorCode, String errorMessage) {
        ValidationResult validationResult = new ValidationResult(errorCode, errorMessage);
        coreState.getValidationResultStack().pushValidationResult(validationResult);
    }

    /**
     * Pushes the Geofence Cluster Exited event to CleverTap.
     *
     * @param geoFenceProperties The {@link JSONObject} object that contains the
     *                           event properties regarding GeoFence Cluster Exited event
     */
    @SuppressWarnings("unused")
    public Future<?> pushGeoFenceExitedEvent(JSONObject geoFenceProperties) {
        return coreState.getAnalyticsManager()
                .raiseEventForGeofences(Constants.GEOFENCE_EXITED_EVENT_NAME, geoFenceProperties);
    }

    /**
     * Pushes the Geofence Cluster Entered event to CleverTap.
     *
     * @param geofenceProperties The {@link JSONObject} object that contains the
     *                           event properties regarding GeoFence Cluster Entered event
     */
    @SuppressWarnings("unused")
    public Future<?> pushGeofenceEnteredEvent(JSONObject geofenceProperties) {
        return coreState.getAnalyticsManager()
                .raiseEventForGeofences(Constants.GEOFENCE_ENTERED_EVENT_NAME, geofenceProperties);
    }

    /**
     * Sends the Huawei registration ID to CleverTap.
     *
     * @param regId    The Huawei registration ID
     * @param register Boolean indicating whether to register
     *                 or not for receiving push messages from CleverTap.
     *                 Set this to true to receive push messages from CleverTap,
     *                 and false to not receive any messages from CleverTap.
     */
    @SuppressWarnings("unused")
    public void pushHuaweiRegistrationId(String regId, boolean register) {
        coreState.getPushProviders().handleToken(regId, PushType.HPS, register);
    }

    /**
     * Pushes the Notification Clicked event for App Inbox to CleverTap.
     *
     * @param messageId String - messageId of {@link CTInboxMessage}
     */
    @SuppressWarnings("unused")
    public void pushInboxNotificationClickedEvent(String messageId) {
        Logger.v( "CleverTapAPI:pushInboxNotificationClickedEvent() called with: messageId = [" +messageId + "]");

        CTInboxMessage message = getInboxMessageForId(messageId);
        coreState.getAnalyticsManager().pushInboxMessageStateEvent(true, message, null);
    }

    /**
     * Pushes the Notification Viewed event for App Inbox to CleverTap.
     *
     * @param messageId String - messageId of {@link CTInboxMessage}
     */
    @SuppressWarnings("unused")
    public void pushInboxNotificationViewedEvent(String messageId) {
        Logger.v( "CleverTapAPI:pushInboxNotificationViewedEvent() called with: messageId = [" +messageId + "]");
        CTInboxMessage message = getInboxMessageForId(messageId);
        coreState.getAnalyticsManager().pushInboxMessageStateEvent(false, message, null);
    }

    /**
     * This method is used to push install referrer via url String
     *
     * @param url A String with the install referrer parameters
     */
    @SuppressWarnings({"unused"})
    public void pushInstallReferrer(String url) {
        coreState.getAnalyticsManager().pushInstallReferrer(url);
    }

    /**
     * This method is used to push install referrer via UTM source, medium & campaign parameters
     *
     * @param source   The UTM source parameter
     * @param medium   The UTM medium parameter
     * @param campaign The UTM campaign parameter
     */
    @SuppressWarnings({"unused"})
    public synchronized void pushInstallReferrer(String source, String medium, String campaign) {
        coreState.getAnalyticsManager().pushInstallReferrer(source, medium, campaign);
    }

    /**
     * Pushes the Notification Clicked event to CleverTap.
     *
     * @param extras The {@link Bundle} object that contains the
     *               notification details
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void pushNotificationClickedEvent(final Bundle extras) {
        coreState.getAnalyticsManager().pushNotificationClickedEvent(extras);
    }

    /**
     * Pushes the Notification Viewed event to CleverTap.
     *
     * @param extras The {@link Bundle} object that contains the
     *               notification details
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void pushNotificationViewedEvent(Bundle extras) {
        coreState.getAnalyticsManager().pushNotificationViewedEvent(extras);
    }

    /**
     * Push a profile update.
     *
     * @param profile A {@link Map}, with keys as strings, and values as {@link String},
     *                {@link Integer}, {@link Long}, {@link Boolean}, {@link Float}, {@link Double},
     *                {@link java.util.Date}, or {@link Character}
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void pushProfile(final Map<String, Object> profile) {
        coreState.getAnalyticsManager().pushProfile(profile);
    }

    //Session

    /**
     * Sends the Xiaomi registration ID to CleverTap.
     *
     * @param regId    The Xiaomi registration ID
     * @param region   The Server room region provided by Xiaomi. Value must be not null or empty
     * @param register Boolean indicating whether to register
     *                 or not for receiving push messages from CleverTap.
     *                 Set this to true to receive push messages from CleverTap,
     *                 and false to not receive any messages from CleverTap.
     */
    @SuppressWarnings("unused")
    public void pushXiaomiRegistrationId(String regId,@NonNull String region, boolean register)  {
        if(TextUtils.isEmpty(region)){
            Logger.d("CleverTapApi : region must not be null or empty , use  MiPushClient.getAppRegion(context) to provide appropriate region");
        }else{
            Logger.d("CleverTapAPI: client called pushXiaomiRegistrationId called with region:"+region);
            PushType.XPS.setServerRegion(region);
            coreState.getPushProviders().handleToken(regId, PushType.XPS, register);
        }

    }

    /**
     * Record a Screen View event
     *
     * @param screenName String, the name of the screen
     */
    @SuppressWarnings({"unused"})
    public void recordScreen(String screenName) {
        String currentScreenName = coreState.getCoreMetaData().getScreenName();
        if (screenName == null || (currentScreenName != null && !currentScreenName.isEmpty() && currentScreenName
                .equals(screenName))) {
            return;
        }
        getConfigLogger().debug(getAccountId(), "Screen changed to " + screenName);
        coreState.getCoreMetaData().setCurrentScreenName(screenName);
        coreState.getAnalyticsManager().recordPageEventWithExtras(null);
    }

    /**
     * Remove a unique value from a multi-value user profile property
     * <p/>
     * If the key currently contains a scalar value, prior to performing the remove operation
     * the key will be promoted to a multi-value property with the current value cast to a string.
     * If the multi-value property is empty after the remove operation, the key will be removed.
     *
     * @param key   String
     * @param value String
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void removeMultiValueForKey(String key, String value) {
        if (value == null || value.isEmpty()) {
            coreState.getAnalyticsManager()._generateEmptyMultiValueError(key);
            return;
        }

        removeMultiValuesForKey(key, new ArrayList<>(Collections.singletonList(value)));
    }

    /**
     * Remove a collection of unique values from a multi-value user profile property
     * <p/>
     * If the key currently contains a scalar value, prior to performing the remove operation
     * the key will be promoted to a multi-value property with the current value cast to a string.
     * <p/>
     * If the multi-value property is empty after the remove operation, the key will be removed.
     *
     * @param key    String
     * @param values {@link ArrayList} with String values
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void removeMultiValuesForKey(final String key, final ArrayList<String> values) {
        coreState.getAnalyticsManager().removeMultiValuesForKey(key, values);
    }

    /**
     * Remove the user profile property value specified by key from the user profile. Alternatively this method
     * can also be used to remove PII data (for eg. Email,Name,Phone), locally from database and shared prefs
     *
     * @param key String
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void removeValueForKey(final String key) {
        coreState.getAnalyticsManager().removeValueForKey(key);
    }

    /**
     * Resumes display of InApp Notifications.
     *
     * If suspendInAppNotifications() was called previously, calling this method will instantly show
     * all queued InApp Notifications and also resume InApp Notifications on events raised after this
     * method is called.
     *
     * If discardInAppNotifications() was called previously, calling this method will only resume
     * InApp Notifications on events raised after this method is called.
     */
    public void resumeInAppNotifications() {
        if (!getCoreState().getConfig().isAnalyticsOnly()) {
            getConfigLogger().debug(getAccountId(), "Resuming InApp Notifications...");
            getCoreState().getInAppController().resumeInApps();
        } else {
            getConfigLogger().debug(getAccountId(),
                    "CleverTap instance is set for Analytics only! Cannot resume InApp Notifications.");
        }
    }

    public static NotificationHandler getNotificationHandler() {
        return sNotificationHandler;
    }

    public static NotificationHandler getSignedCallNotificationHandler() {
        return sSignedCallNotificationHandler;
    }

    /**
     * This method is used to increment the given value
     *
     * Number should be in positive range
     *
     * @param key   String
     * @param value Number
     */
    @SuppressWarnings("unused")
    public void incrementValue(final String key, final Number value) {
        coreState.getAnalyticsManager().incrementValue(key, value);
    }

    /**
     * <p style="color:#4d2e00;background:#ffcc99;font-weight: bold" >
     *      Note: This method has been deprecated since v5.0.0 and will be removed in the future versions of this SDK.
     * </p>
     *
     * This method is used to set the CTFeatureFlagsListener
     * Register to receive feature flag callbacks
     *
     * @param featureFlagsListener The {@link CTFeatureFlagsListener} object
     */
    @SuppressWarnings("unused")
    @Deprecated
    public void setCTFeatureFlagsListener(CTFeatureFlagsListener featureFlagsListener) {
        coreState.getCallbackManager().setFeatureFlagListener(featureFlagsListener);
    }

    /**
     * <p style="color:#4d2e00;background:#ffcc99;font-weight: bold" >
     *      Note: This method has been deprecated since v5.0.0 and will be removed in the future versions of this SDK.
     * </p>
     * This method is used to set the product config listener
     * Register to receive callbacks
     *
     * @param listener The {@link CTProductConfigListener} instance
     */
    @SuppressWarnings("unused")
    @Deprecated
    public void setCTProductConfigListener(CTProductConfigListener listener) {
        coreState.getCallbackManager().setProductConfigListener(listener);
    }

    /**
     * Sets the listener to get the list of currently running Display Campaigns via callback
     *
     * @param listener- {@link DisplayUnitListener}
     */
    public void setDisplayUnitListener(DisplayUnitListener listener) {
        coreState.getCallbackManager().setDisplayUnitListener(listener);
    }

    //Listener

    @SuppressWarnings("unused")
    public void setInAppNotificationButtonListener(InAppNotificationButtonListener listener) {
        coreState.getCallbackManager().setInAppNotificationButtonListener(listener);
    }

    @SuppressWarnings("unused")
    public void setInboxMessageButtonListener(InboxMessageButtonListener listener) {
        this.inboxMessageButtonListener = new WeakReference<>(listener);
    }

    @SuppressWarnings("unused")
    public void setCTInboxMessageListener(InboxMessageListener listener){
        this.inboxMessageListener = new WeakReference<>(listener);
    }

    @RestrictTo(Scope.LIBRARY_GROUP)
    public static void addNotificationRenderedListener(String id, final NotificationRenderedListener notificationRenderedListener) {
       sNotificationRenderedListenerMap.put(id, notificationRenderedListener);
    }

    @RestrictTo(Scope.LIBRARY_GROUP)
    public static NotificationRenderedListener getNotificationRenderedListener(String id) {
       return sNotificationRenderedListenerMap.get(id);
    }

    @RestrictTo(Scope.LIBRARY_GROUP)
    public static NotificationRenderedListener removeNotificationRenderedListener(String id) {
       return sNotificationRenderedListenerMap.remove(id);
    }

    /**
     * Not to be used by developers. This is used internally to help CleverTap know which library is wrapping the
     * native SDK
     *
     * @param library {@link String} library name
     */
    public void setLibrary(String library) {
        if (coreState.getDeviceInfo() != null) {
            coreState.getDeviceInfo().setLibrary(library);
        }
    }

    /**
     * Sets the location in CleverTap to get updated GeoFences
     *
     * @param location android.location.Location
     */
    @SuppressWarnings("unused")
    public Future<?> setLocationForGeofences(Location location, int sdkVersion) {
        coreState.getCoreMetaData().setLocationForGeofence(true);
        coreState.getCoreMetaData().setGeofenceSDKVersion(sdkVersion);
        return coreState.getLocationManager()._setLocation(location);
    }

    /**
     * Set a collection of unique values as a multi-value user profile property, any existing value will be
     * overwritten.
     * Max 100 values, on reaching 100 cap, oldest value(s) will be removed.
     * Values must be Strings and are limited to 512 characters.
     *
     * @param key    String
     * @param values {@link ArrayList} with String values
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void setMultiValuesForKey(final String key, final ArrayList<String> values) {
        coreState.getAnalyticsManager().setMultiValuesForKey(key, values);
    }

    /**
     * If you want to stop recorded events from being sent to the server, use this method to set the SDK instance to
     * offline.
     * Once offline, events will be recorded and queued locally but will not be sent to the server until offline is
     * disabled.
     * Calling this method again with offline set to false will allow events to be sent to server and the SDK instance
     * will immediately attempt to send events that have been queued while offline.
     *
     * @param value boolean, true sets the sdk offline, false sets the sdk back online
     */
    @SuppressWarnings({"unused"})
    public void setOffline(boolean value) {
        coreState.getCoreMetaData().setOffline(value);
        if (value) {
            getConfigLogger()
                    .debug(getAccountId(), "CleverTap Instance has been set to offline, won't send events queue");
        } else {
            getConfigLogger()
                    .debug(getAccountId(), "CleverTap Instance has been set to online, sending events queue");
            flush();
        }
    }

    /**
     * Use this method to opt the current user out of all event/profile tracking.
     * You must call this method separately for each active user profile (e.g. when switching user profiles using
     * onUserLogin).
     * Once enabled, no events will be saved remotely or locally for the current user. To re-enable tracking call this
     * method with enabled set to false.
     *
     * @param userOptOut boolean Whether tracking opt out should be enabled/disabled.
     */
    @SuppressWarnings({"unused"})
    public void setOptOut(boolean userOptOut) {
        final boolean enable = userOptOut;
        Task<Void> task = CTExecutorFactory.executors(coreState.getConfig()).postAsyncSafelyTask();
        task.execute("setOptOut", new Callable<Void>() {
            @Override
            public Void call() {
                // generate the data for a profile push to alert the server to the optOut state change
                HashMap<String, Object> optOutMap = new HashMap<>();
                optOutMap.put(Constants.CLEVERTAP_OPTOUT, enable);

                // determine order of operations depending on enabled/disabled
                if (enable) {  // if opting out first push profile event then set the flag
                    pushProfile(optOutMap);
                    coreState.getCoreMetaData().setCurrentUserOptedOut(true);
                } else {  // if opting back in first reset the flag to false then push the profile event
                    coreState.getCoreMetaData().setCurrentUserOptedOut(false);
                    pushProfile(optOutMap);
                }
                // persist the new optOut state
                String key = coreState.getDeviceInfo().optOutKey();
                if (key == null) {
                    getConfigLogger()
                            .verbose(getAccountId(), "Unable to persist user OptOut state, storage key is null");
                    return null;
                }
                StorageHelper.putBoolean(context, StorageHelper.storageKeyWithSuffix(getConfig(), key), enable);
                getConfigLogger().verbose(getAccountId(), "Set current user OptOut state to: " + enable);
                return null;
            }
        });
    }

    /**
     * Opens {@link CTInboxActivity} to display Inbox Messages
     *
     * @param styleConfig {@link CTInboxStyleConfig} configuration of various style parameters for the {@link
     *                    CTInboxActivity}
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void showAppInbox(CTInboxStyleConfig styleConfig) {
        synchronized (coreState.getCTLockManager().getInboxControllerLock()) {
            if (coreState.getControllerManager().getCTInboxController() == null) {
                getConfigLogger().debug(getAccountId(), "Notification Inbox not initialized");
                return;
            }
        }

        // make styleConfig immutable
        final CTInboxStyleConfig _styleConfig = new CTInboxStyleConfig(styleConfig);

        Intent intent = new Intent(context, CTInboxActivity.class);
        intent.putExtra("styleConfig", _styleConfig);
        Bundle configBundle = new Bundle();
        configBundle.putParcelable("config", getConfig());
        intent.putExtra("configBundle", configBundle);
        try {
            Activity currentActivity = CoreMetaData.getCurrentActivity();
            if (currentActivity == null) {
                throw new IllegalStateException("Current activity reference not found");
            }
            currentActivity.startActivity(intent);
            Logger.d("Displaying Notification Inbox");

        } catch (Throwable t) {
            Logger.v("Please verify the integration of your app." +
                    " It is not setup to support Notification Inbox yet.", t);
        }

    }

    /**
     * Dismisses the App Inbox Activity if already opened
     */
    public void dismissAppInbox() {
        try {
            Activity appInboxActivity = getCoreState().getCoreMetaData().getAppInboxActivity();
            if (appInboxActivity == null) {
                throw new IllegalStateException("AppInboxActivity reference not found");
            }
            if (!appInboxActivity.isFinishing()) {
                getConfigLogger().verbose(getAccountId(), "Finishing the App Inbox");
                appInboxActivity.finish();
            }
        } catch (Throwable t) {
            getConfigLogger().verbose(getAccountId(), "Can't dismiss AppInbox, please ensure to call this method after the usage of " +
                    "cleverTapApiInstance.showAppInbox(). \n" + t);
        }
    }

    /**
     * Opens {@link CTInboxActivity} to display Inbox Messages with default {@link CTInboxStyleConfig} object
     */
    @SuppressWarnings({"unused"})
    public void showAppInbox() {
        CTInboxStyleConfig styleConfig = new CTInboxStyleConfig();
        showAppInbox(styleConfig);
    }

    /**
     * Suspends display of InApp Notifications.
     * The InApp Notifications are queued once this method is called
     * and will be displayed once resumeInAppNotifications() is called.
     */
    public void suspendInAppNotifications() {
        if (!getCoreState().getConfig().isAnalyticsOnly()) {
            getConfigLogger().debug(getAccountId(), "Suspending InApp Notifications...");
            getConfigLogger().debug(getAccountId(),
                    "Please Note - InApp Notifications will be suspended till resumeInAppNotifications() is not called again");
            getCoreState().getInAppController().suspendInApps();
        } else {
            getConfigLogger().debug(getAccountId(),
                    "CleverTap instance is set for Analytics only! Cannot suspend InApp Notifications.");
        }
    }

    @RestrictTo(Scope.LIBRARY_GROUP)
    public int getCustomSdkVersion(String customSdkName) {
        return coreState.getCoreMetaData().getCustomSdkVersion(customSdkName);
    }

    @RestrictTo(Scope.LIBRARY_GROUP)
    public void setCustomSdkVersion(String customSdkName,int customSdkVersion) {
        coreState.getCoreMetaData().setCustomSdkVersion(customSdkName,customSdkVersion);
    }

    //To be called from DeviceInfo AdID GUID generation
    void deviceIDCreated(String deviceId) {

        String accountId = coreState.getConfig().getAccountId();

        if (coreState.getControllerManager() == null) {
            getConfigLogger().verbose(accountId + ":async_deviceID",
                    "ControllerManager not set yet! Returning from deviceIDCreated()");
            return;
        }

        /**
         * Reinitialising InAppFCManager with device id, if it's null
         * during first initialisation from CleverTapFactory.getCoreState()
         */
        if (coreState.getControllerManager().getInAppFCManager() == null) {
            getConfigLogger().verbose(accountId + ":async_deviceID",
                    "Initializing InAppFC after Device ID Created = " + deviceId);
            coreState.getControllerManager()
                    .setInAppFCManager(new InAppFCManager(context, coreState.getConfig(), deviceId));
        }

        //todo : replace with variables
        /**
         * Reinitialising product config & Feature Flag controllers with device id, if it's null
         * during first initialisation from CleverTapFactory.getCoreState()
         */
        CTFeatureFlagsController ctFeatureFlagsController = coreState.getControllerManager()
                .getCTFeatureFlagsController();

        if (ctFeatureFlagsController != null && TextUtils.isEmpty(ctFeatureFlagsController.getGuid())) {
            getConfigLogger().verbose(accountId + ":async_deviceID",
                    "Initializing Feature Flags after Device ID Created = " + deviceId);
            ctFeatureFlagsController.setGuidAndInit(deviceId);
        }
        //todo: replace with variables
        CTProductConfigController ctProductConfigController = coreState.getControllerManager()
                .getCTProductConfigController();

        if (ctProductConfigController != null && TextUtils
                .isEmpty(ctProductConfigController.getSettings().getGuid())) {
            getConfigLogger().verbose(accountId + ":async_deviceID",
                    "Initializing Product Config after Device ID Created = " + deviceId);
            ctProductConfigController.setGuidAndInit(deviceId);
        }
        getConfigLogger().verbose(accountId + ":async_deviceID",
                "Got device id from DeviceInfo, notifying user profile initialized to SyncListener");
        coreState.getCallbackManager().notifyUserProfileInitialized(deviceId);

        if (coreState.getCallbackManager().getOnInitCleverTapIDListener() != null) {
            coreState.getCallbackManager().getOnInitCleverTapIDListener().onInitCleverTapID(deviceId);
        }

    }

    private CleverTapInstanceConfig getConfig() {
        return coreState.getConfig();
    }

    private Logger getConfigLogger() {
        return getConfig().getLogger();
    }

    private int getCurrentSession() {
        return currentSessionId;
    }

    private String getDomain(boolean defaultToHandshakeURL, final EventGroup eventGroup) {
        String domain = getDomainFromPrefsOrMetadata(eventGroup);

        final boolean emptyDomain = domain == null || domain.trim().length() == 0;
        if (emptyDomain && !defaultToHandshakeURL) {
            return null;
        }

        if (emptyDomain) {
            domain = Constants.PRIMARY_DOMAIN + "/hello";
        } else {
            domain += "/a1";
        }

        return domain;
    }

    private String getDomainFromPrefsOrMetadata(final EventGroup eventGroup) {
        try {
            final String region = this.config.getAccountRegion();
            if (region != null && region.trim().length() > 0) {
                // Always set this to 0 so that the handshake is not performed during a HTTP failure
                mResponseFailureCount = 0;
                if (eventGroup.equals(EventGroup.PUSH_NOTIFICATION_VIEWED)) {
                    return region.trim().toLowerCase() + eventGroup.httpResource + "." + Constants.PRIMARY_DOMAIN;
                } else {
                    return region.trim().toLowerCase() + "." + Constants.PRIMARY_DOMAIN;
                }
            }
        } catch (Throwable t) {
            // Ignore
        }
        if (eventGroup.equals(EventGroup.PUSH_NOTIFICATION_VIEWED)) {
            return StorageHelper.getStringFromPrefs(context, config, Constants.SPIKY_KEY_DOMAIN_NAME, null);
        } else {
            return StorageHelper.getStringFromPrefs(context, config, Constants.KEY_DOMAIN_NAME, null);
        }
    }

    private String getEndpoint(final boolean defaultToHandshakeURL, final EventGroup eventGroup) {
        String domain = getDomain(defaultToHandshakeURL, eventGroup);
        if (domain == null) {
            getConfigLogger().verbose(getAccountId(), "Unable to configure endpoint, domain is null");
            return null;
        }

        final String accountId = getAccountId();
        if (accountId == null) {
            getConfigLogger().verbose(getAccountId(), "Unable to configure endpoint, accountID is null");
            return null;
        }

        //here we redirect to our domain and add this domain as url param
        final String proxyDomain = getProxyDomain();
        String endpoint = "https://";
        if(TextUtils.isEmpty(proxyDomain)) {
            endpoint += domain + "?os=Android";
        } else {
            endpoint += proxyDomain + "?os=Android&rt=" + domain;
        }
        endpoint += "&t=" + this.deviceInfo.getSdkVersion() + "&z=" + accountId;

        final boolean needsHandshake = needsHandshakeForDomain(eventGroup);
        // Don't attach ts if its handshake
        if (needsHandshake) {
            return endpoint;
        }

        currentRequestTimestamp = (int) (System.currentTimeMillis() / 1000);
        endpoint += "&ts=" + currentRequestTimestamp;

        return endpoint;
    }

    private int getFirstRequestTimestamp() {
        return StorageHelper.getIntFromPrefs(context, config, Constants.KEY_FIRST_TS, 0);
    }

    private String getGUIDForIdentifier(String key, String identifier) {
        if (key == null || identifier == null) {
            return null;
        }

        String cacheKey = key + "_" + identifier;
        JSONObject cache = getCachedGUIDs();
        try {
            return cache.getString(cacheKey);
        } catch (Throwable t) {
            getConfigLogger().verbose(getAccountId(), "Error reading guid cache: " + t.toString());
            return null;
        }
    }

    private int getGeofenceSDKVersion() {
        return geofenceSDKVersion;
    }

    //Util

    private void setGeofenceSDKVersion(int geofenceSDKVersion) {
        this.geofenceSDKVersion = geofenceSDKVersion;
    }

    private String getGraphUserPropertySafely(JSONObject graphUser, String key, String def) {
        try {
            String prop = (String) graphUser.get(key);
            if (prop != null) {
                return prop;
            } else {
                return def;
            }
        } catch (Throwable t) {
            return def;
        }
    }

    /**
     * Returns the generic handler object which is used to post
     * runnables. The returned value will never be null.
     *
     * @return The generic handler
     * @see Handler
     */
    private Handler getHandlerUsingMainLooper() {
        return handlerUsingMainLooper;
    }

    private long getI() {
        return StorageHelper.getLongFromPrefs(context, config, Constants.KEY_I, 0, Constants.NAMESPACE_IJ);
    }

    private long getJ() {
        return StorageHelper.getLongFromPrefs(context, config, Constants.KEY_J, 0, Constants.NAMESPACE_IJ);
    }

    private int getLastRequestTimestamp() {
        return StorageHelper.getIntFromPrefs(context, config, Constants.KEY_LAST_TS, 0);
    }

    //Session
    private int getLastSessionLength() {
        return lastSessionLength;
    }

    private LocalDataStore getLocalDataStore() {
        return localDataStore;
    }

    private synchronized String getMedium() {
        return medium;
    }

    // only set if not already set during the session
    private synchronized void setMedium(String medium) {
        if (this.medium == null) {
            this.medium = medium;
        }
    }

    //Session
    //Old namespace for ARP Shared Prefs
    private String getNamespaceARPKey() {

        final String accountId = getAccountId();
        if (accountId == null) {
            return null;
        }

        getConfigLogger().verbose(getAccountId(), "Old ARP Key = ARP:" + accountId);
        return "ARP:" + accountId;
    }

    //New namespace for ARP Shared Prefs
    private String getNewNamespaceARPKey() {

        final String accountId = getAccountId();
        if (accountId == null) {
            return null;
        }

        getConfigLogger().verbose(getAccountId(), "New ARP Key = ARP:" + accountId + ":" + getCleverTapID());
        return "ARP:" + accountId + ":" + getCleverTapID();
    }

    private int getPingFrequency(Context context) {
        return StorageHelper.getInt(context, Constants.PING_FREQUENCY,
                Constants.PING_FREQUENCY_VALUE); //intentional global key because only one Job is running
    }

    private QueueCursor getPushNotificationViewedQueuedEvents(final Context context, final int batchSize,
            final QueueCursor previousCursor) {
        return getQueueCursor(context, DBAdapter.Table.PUSH_NOTIFICATION_VIEWED, batchSize, previousCursor);
    }

    private QueueCursor getQueueCursor(final Context context, DBAdapter.Table table, final int batchSize,
            final QueueCursor previousCursor) {
        synchronized (eventLock) {
            DBAdapter adapter = loadDBAdapter(context);
            DBAdapter.Table tableName = (previousCursor != null) ? previousCursor.getTableName() : table;

            // if previousCursor that means the batch represented by the previous cursor was processed so remove those from the db
            if (previousCursor != null) {
                adapter.cleanupEventsFromLastId(previousCursor.getLastId(), previousCursor.getTableName());
            }

            // grab the new batch
            QueueCursor newCursor = new QueueCursor();
            newCursor.setTableName(tableName);
            JSONObject queuedDBEvents = adapter.fetchEvents(tableName, batchSize);
            newCursor = updateCursorForDBObject(queuedDBEvents, newCursor);

            return newCursor;
        }
    }

    private QueueCursor getQueuedDBEvents(final Context context, final int batchSize,
            final QueueCursor previousCursor) {

        synchronized (eventLock) {
            QueueCursor newCursor = getQueueCursor(context, DBAdapter.Table.EVENTS, batchSize, previousCursor);

            if (newCursor.isEmpty() && newCursor.getTableName().equals(DBAdapter.Table.EVENTS)) {
                newCursor = getQueueCursor(context, DBAdapter.Table.PROFILE_EVENTS, batchSize, null);
            }

            return newCursor.isEmpty() ? null : newCursor;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private QueueCursor getQueuedEvents(final Context context, final int batchSize, final QueueCursor previousCursor,
            final EventGroup eventGroup) {
        if (eventGroup == EventGroup.PUSH_NOTIFICATION_VIEWED) {
            getConfigLogger().verbose(getAccountId(), "Returning Queued Notification Viewed events");
            return getPushNotificationViewedQueuedEvents(context, batchSize, previousCursor);
        } else {
            getConfigLogger().verbose(getAccountId(), "Returning Queued events");
            return getQueuedDBEvents(context, batchSize, previousCursor);
        }
    }

    private long getReferrerClickTime() {
        return referrerClickTime;
    }

    private String getScreenName() {
        return currentScreenName.equals("") ? null : currentScreenName;
    }

    private synchronized String getSource() {
        return source;
    }

    //UTM
    // only set if not already set during the session
    private synchronized void setSource(String source) {
        if (this.source == null) {
            this.source = source;
        }
    }

    private synchronized JSONObject getWzrkParams() {
        return wzrkParams;
    }

    private synchronized void setWzrkParams(JSONObject wzrkParams) {
        if (this.wzrkParams == null) {
            this.wzrkParams = wzrkParams;
        }
    }

    //Saves ARP directly to new namespace
    private void handleARPUpdate(final Context context, final JSONObject arp) {
        if (arp == null || arp.length() == 0) {
            return;
        }

        final String nameSpaceKey = getNewNamespaceARPKey();
        if (nameSpaceKey == null) {
            return;
        }

        final SharedPreferences prefs = StorageHelper.getPreferences(context, nameSpaceKey);
        final SharedPreferences.Editor editor = prefs.edit();

        final Iterator<String> keys = arp.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            try {
                final Object o = arp.get(key);
                if (o instanceof Number) {
                    final int update = ((Number) o).intValue();
                    editor.putInt(key, update);
                } else if (o instanceof String) {
                    if (((String) o).length() < 100) {
                        editor.putString(key, (String) o);
                    } else {
                        getConfigLogger().verbose(getAccountId(),
                                "ARP update for key " + key + " rejected (string value too long)");
                    }
                } else if (o instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) o);
                } else {
                    getConfigLogger()
                            .verbose(getAccountId(), "ARP update for key " + key + " rejected (invalid data type)");
                }
            } catch (JSONException e) {
                // Ignore
            }
        }
        getConfigLogger().verbose(getAccountId(),
                "Stored ARP for namespace key: " + nameSpaceKey + " values: " + arp.toString());
        StorageHelper.persist(editor);
    }

    private void handleInstallReferrerOnFirstInstall() {
        getConfigLogger().verbose(getAccountId(), "Starting to handle install referrer");
        try {
            final InstallReferrerClient referrerClient = InstallReferrerClient.newBuilder(context).build();
            referrerClient.startConnection(new InstallReferrerStateListener() {
                @Override
                public void onInstallReferrerServiceDisconnected() {
                    if (!installReferrerDataSent) {
                        handleInstallReferrerOnFirstInstall();
                    }
                }

                @Override
                public void onInstallReferrerSetupFinished(int responseCode) {
                    switch (responseCode) {
                        case InstallReferrerClient.InstallReferrerResponse.OK:
                            // Connection established.
                            ReferrerDetails response = null;
                            try {
                                response = referrerClient.getInstallReferrer();
                                String referrerUrl = response.getInstallReferrer();
                                referrerClickTime = response.getReferrerClickTimestampSeconds();
                                appInstallTime = response.getInstallBeginTimestampSeconds();
                                pushInstallReferrer(referrerUrl);
                                installReferrerDataSent = true;
                                getConfigLogger().debug(getAccountId(),
                                        "Install Referrer data set [Referrer URL-" + referrerUrl + "]");
                            } catch (RemoteException e) {
                                getConfigLogger().debug(getAccountId(),
                                        "Remote exception caused by Google Play Install Referrer library - " + e
                                                .getMessage());
                                referrerClient.endConnection();
                                installReferrerDataSent = false;
                            }
                            referrerClient.endConnection();
                            break;
                        case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
                            // API not available on the current Play Store app.
                            getConfigLogger().debug(getAccountId(),
                                    "Install Referrer data not set, API not supported by Play Store on device");
                            break;
                        case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE:
                            // Connection couldn't be established.
                            getConfigLogger().debug(getAccountId(),
                                    "Install Referrer data not set, connection to Play Store unavailable");
                            break;
                    }
                }
            });
        } catch (Throwable t) {
            getConfigLogger().verbose(getAccountId(),
                    "Google Play Install Referrer's InstallReferrerClient Class not found - " + t
                            .getLocalizedMessage()
                            + " \n Please add implementation 'com.android.installreferrer:installreferrer:2.1' to your build.gradle");
        }
    }

    //PN
    private void handlePushNotificationsInResponse(JSONArray pushNotifications) {
        try {
            for (int i = 0; i < pushNotifications.length(); i++) {
                Bundle pushBundle = new Bundle();
                JSONObject pushObject = pushNotifications.getJSONObject(i);
                if (pushObject.has("wzrk_acct_id")) {
                    pushBundle.putString("wzrk_acct_id", pushObject.getString("wzrk_acct_id"));
                }
                if (pushObject.has("wzrk_acts")) {
                    pushBundle.putString("wzrk_acts", pushObject.getString("wzrk_acts"));
                }
                if (pushObject.has("nm")) {
                    pushBundle.putString("nm", pushObject.getString("nm"));
                }
                if (pushObject.has("nt")) {
                    pushBundle.putString("nt", pushObject.getString("nt"));
                }
                if (pushObject.has("wzrk_bp")) {
                    pushBundle.putString("wzrk_bp", pushObject.getString("wzrk_bp"));
                }
                if (pushObject.has("pr")) {
                    pushBundle.putString("pr", pushObject.getString("pr"));
                }
                if (pushObject.has("wzrk_pivot")) {
                    pushBundle.putString("wzrk_pivot", pushObject.getString("wzrk_pivot"));
                }
                if (pushObject.has("wzrk_sound")) {
                    pushBundle.putString("wzrk_sound", pushObject.getString("wzrk_sound"));
                }
                if (pushObject.has("wzrk_cid")) {
                    pushBundle.putString("wzrk_cid", pushObject.getString("wzrk_cid"));
                }
                if (pushObject.has("wzrk_bc")) {
                    pushBundle.putString("wzrk_bc", pushObject.getString("wzrk_bc"));
                }
                if (pushObject.has("wzrk_bi")) {
                    pushBundle.putString("wzrk_bi", pushObject.getString("wzrk_bi"));
                }
                if (pushObject.has("wzrk_id")) {
                    pushBundle.putString("wzrk_id", pushObject.getString("wzrk_id"));
                }
                if (pushObject.has("wzrk_pn")) {
                    pushBundle.putString("wzrk_pn", pushObject.getString("wzrk_pn"));
                }
                if (pushObject.has("ico")) {
                    pushBundle.putString("ico", pushObject.getString("ico"));
                }
                if (pushObject.has("wzrk_ck")) {
                    pushBundle.putString("wzrk_ck", pushObject.getString("wzrk_ck"));
                }
                if (pushObject.has("wzrk_dl")) {
                    pushBundle.putString("wzrk_dl", pushObject.getString("wzrk_dl"));
                }
                if (pushObject.has("wzrk_pid")) {
                    pushBundle.putString("wzrk_pid", pushObject.getString("wzrk_pid"));
                }
                if (pushObject.has("wzrk_ttl")) {
                    pushBundle.putLong("wzrk_ttl", pushObject.getLong("wzrk_ttl"));
                }
                if (pushObject.has("wzrk_rnv")) {
                    pushBundle.putString("wzrk_rnv", pushObject.getString("wzrk_rnv"));
                }
                Iterator iterator = pushObject.keys();
                while (iterator.hasNext()) {
                    String key = iterator.next().toString();
                    pushBundle.putString(key, pushObject.getString(key));
                }
                if (!pushBundle.isEmpty() && !dbAdapter
                        .doesPushNotificationIdExist(pushObject.getString("wzrk_pid"))) {
                    getConfigLogger().verbose("Creating Push Notification locally");
                    if (pushAmpListener != null) {
                        pushAmpListener.onPushAmpPayloadReceived(pushBundle);
                    } else {
                        createNotification(context, pushBundle);
                    }
                } else {
                    getConfigLogger().verbose(getAccountId(),
                            "Push Notification already shown, ignoring local notification :" + pushObject
                                    .getString("wzrk_pid"));
                }
            }
        } catch (JSONException e) {
            getConfigLogger().verbose(getAccountId(), "Error parsing push notification JSON");
        }
    }

    /**
     * This method handles send Test flow for Display Units
     *
     * @param extras - bundled data of notification payload
     */
    private void handleSendTestForDisplayUnits(Bundle extras) {
        try {
            String pushJsonPayload = extras.getString(Constants.DISPLAY_UNIT_PREVIEW_PUSH_PAYLOAD_KEY);
            Logger.v("Received Display Unit via push payload: " + pushJsonPayload);
            JSONObject r = new JSONObject();
            JSONArray displayUnits = new JSONArray();
            r.put(Constants.DISPLAY_UNIT_JSON_RESPONSE_KEY, displayUnits);
            JSONObject testPushObject = new JSONObject(pushJsonPayload);
            displayUnits.put(testPushObject);
            processDisplayUnitsResponse(r);
        } catch (Throwable t) {
            Logger.v("Failed to process Display Unit from push notification payload", t);
        }
    }

    private boolean hasDomainChanged(final String newDomain) {
        final String oldDomain = StorageHelper.getStringFromPrefs(context, config, Constants.KEY_DOMAIN_NAME, null);
        return !newDomain.equals(oldDomain);
    }

    private boolean inCurrentSession() {
        return currentSessionId > 0;
    }

    private void initABTesting() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        if (!config.isAnalyticsOnly()) {
            if (!config.isABTestingEnabled()) {
                getConfigLogger().debug(config.getAccountId(), "AB Testing is not enabled for this instance");
                return;
            }

            if (getCleverTapID() == null) {
                getConfigLogger()
                        .verbose(config.getAccountId(), "GUID not set yet, deferring ABTesting initialization");
                return;
            }

            config.setEnableUIEditor(isUIEditorEnabled);
            if (ctABTestController == null) {
                ctABTestController = new CTABTestController(context, config, getCleverTapID(), this);
                getConfigLogger().verbose(config.getAccountId(), "AB Testing initialized");
            }
        }
    }

    private void initFeatureFlags(boolean fromPlayServices) {
        Logger.v("Initializing Feature Flags with device Id = " + getCleverTapID());

        if (config.isAnalyticsOnly()) {
            getConfigLogger().debug(config.getAccountId(), "Feature Flag is not enabled for this instance");
            return;
        }

        if (ctFeatureFlagsController == null) {
            ctFeatureFlagsController = new CTFeatureFlagsController(context, getCleverTapID(), config, this);
            getConfigLogger().verbose(config.getAccountId(), "Feature Flags initialized");
        }

        if (fromPlayServices && !ctFeatureFlagsController.isInitialized()) {
            ctFeatureFlagsController.setGuidAndInit(getCleverTapID());
        }
    }

    private void initProductConfig(boolean fromPlayServices) {
        Logger.v("Initializing Product Config with device Id = " + getCleverTapID());

        if (config.isAnalyticsOnly()) {
            getConfigLogger().debug(config.getAccountId(), "Product Config is not enabled for this instance");
            return;
        }

        if (ctProductConfigController == null) {
            ctProductConfigController = new CTProductConfigController(context, getCleverTapID(), config, this);
        }
        if (fromPlayServices && !ctProductConfigController.isInitialized()) {
            ctProductConfigController.setGuidAndInit(getCleverTapID());
        }
    }

    //Networking
    private String insertHeader(Context context, JSONArray arr) {
        try {
            final JSONObject header = new JSONObject();

            String deviceId = getCleverTapID();
            if (deviceId != null && !deviceId.equals("")) {
                header.put("g", deviceId);
            } else {
                getConfigLogger().verbose(getAccountId(),
                        "CRITICAL: Couldn't finalise on a device ID! Using error device ID instead!");
            }

            header.put("type", "meta");

            JSONObject appFields = getAppLaunchedFields();
            header.put("af", appFields);

            long i = getI();
            if (i > 0) {
                header.put("_i", i);
            }

            long j = getJ();
            if (j > 0) {
                header.put("_j", j);
            }

            String accountId = getAccountId();
            String token = this.config.getAccountToken();

            if (accountId == null || token == null) {
                getConfigLogger()
                        .debug(getAccountId(), "Account ID/token not found, unable to configure queue request");
                return null;
            }

            header.put("id", accountId);
            header.put("tk", token);
            header.put("l_ts", getLastRequestTimestamp());
            header.put("f_ts", getFirstRequestTimestamp());
            header.put("ddnd",
                    !(this.deviceInfo.getNotificationsEnabledForUser() && (pushProviders.isNotificationSupported())));
            if (isBgPing) {
                header.put("bk", 1);
                isBgPing = false;
            }
            header.put("rtl", getRenderedTargetList(this.dbAdapter));
            if (!installReferrerDataSent) {
                header.put("rct", getReferrerClickTime());
                header.put("ait", getAppInstallTime());
            }
            header.put("frs", isFirstRequestInSession());
            setFirstRequestInSession(false);

            // Attach ARP
            try {
                final JSONObject arp = getARP();
                if (arp != null && arp.length() > 0) {
                    header.put("arp", arp);
                }
            } catch (Throwable t) {
                getConfigLogger().verbose(getAccountId(), "Failed to attach ARP", t);
            }

            JSONObject ref = new JSONObject();
            try {

                String utmSource = getSource();
                if (utmSource != null) {
                    ref.put("us", utmSource);
                }

                String utmMedium = getMedium();
                if (utmMedium != null) {
                    ref.put("um", utmMedium);
                }

                String utmCampaign = getCampaign();
                if (utmCampaign != null) {
                    ref.put("uc", utmCampaign);
                }

                if (ref.length() > 0) {
                    header.put("ref", ref);
                }

            } catch (Throwable t) {
                getConfigLogger().verbose(getAccountId(), "Failed to attach ref", t);
            }

            JSONObject wzrkParams = getWzrkParams();
            if (wzrkParams != null && wzrkParams.length() > 0) {
                header.put("wzrk_ref", wzrkParams);
            }

            if (inAppFCManager != null) {
                Logger.v("Attaching InAppFC to Header");
                inAppFCManager.attachToHeader(context, header);
            }

            // Resort to string concat for backward compatibility
            return "[" + header.toString() + ", " + arr.toString().substring(1);
        } catch (Throwable t) {
            getConfigLogger().verbose(getAccountId(), "CommsManager: Failed to attach header", t);
            return arr.toString();
        }
    }

    private boolean isAnonymousDevice() {
        JSONObject cachedGUIDs = getCachedGUIDs();
        return cachedGUIDs.length() <= 0;
    }

    private boolean isAppLaunchPushed() {
        synchronized (appLaunchPushedLock) {
            return appLaunchPushed;
        }
    }

    private void setAppLaunchPushed(boolean pushed) {
        synchronized (appLaunchPushedLock) {
            appLaunchPushed = pushed;
        }
    }

    private boolean isAppLaunchReportingDisabled() {
        return this.config.isDisableAppLaunchedEvent();
    }

    private boolean isCurrentUserOptedOut() {
        synchronized (optOutFlagLock) {
            return currentUserOptedOut;
        }
    }

    private void setCurrentUserOptedOut(boolean enable) {
        synchronized (optOutFlagLock) {
            currentUserOptedOut = enable;
        }
    }

    private boolean isErrorDeviceId() {
        return coreState.getDeviceInfo().isErrorDeviceId();
    }

    //Run manifest validation in async
    private void manifestAsyncValidation() {
        Task<Void> task = CTExecutorFactory.executors(coreState.getConfig()).postAsyncSafelyTask();
        task.execute("Manifest Validation", new Callable<Void>() {
            @Override
            public Void call() {
                ManifestValidator
                        .validate(context, coreState.getDeviceInfo(), coreState.getPushProviders());
                return null;
            }
        });
    }

    /**
     * Sends the ADM registration ID to CleverTap.
     *
     * @param token    The ADM registration ID
     * @param register Boolean indicating whether to register
     *                 or not for receiving push messages from CleverTap.
     *                 Set this to true to receive push messages from CleverTap,
     *                 and false to not receive any messages from CleverTap.
     */
    @SuppressWarnings("unused")
    private void pushAmazonRegistrationId(String token, boolean register) {
        coreState.getPushProviders().handleToken(token, PushType.ADM, register);
    }

    //Event
    private void pushAppLaunchedEvent() {
        if (isAppLaunchReportingDisabled()) {
            setAppLaunchPushed(true);
            getConfigLogger().debug(getAccountId(), "App Launched Events disabled in the Android Manifest file");
            return;
        }
        if (isAppLaunchPushed()) {
            getConfigLogger()
                    .verbose(getAccountId(), "App Launched has already been triggered. Will not trigger it ");
            return;
        } else {
            getConfigLogger().verbose(getAccountId(), "Firing App Launched event");
        }
        setAppLaunchPushed(true);
        JSONObject event = new JSONObject();
        try {
            event.put("evtName", Constants.APP_LAUNCHED_EVENT);
            JSONObject eventData = getAppLaunchedFields();
            insertCommonParamsInEventData(eventData);
            event.put("evtData", eventData);
        } catch (Throwable t) {
            // We won't get here
        }
        queueEvent(context, event, Constants.RAISED_EVENT);
    }

    //Profile
    private void pushBasicProfile(JSONObject baseProfile) {
        try {
            String guid = getCleverTapID();

            JSONObject profileEvent = new JSONObject();

            if (baseProfile != null && baseProfile.length() > 0) {
                Iterator i = baseProfile.keys();
                while (i.hasNext()) {
                    String next = i.next().toString();

                    // need to handle command-based JSONObject props here now
                    Object value = null;
                    try {
                        value = baseProfile.getJSONObject(next);
                    } catch (Throwable t) {
                        try {
                            value = baseProfile.get(next);
                        } catch (JSONException e) {
                            //no-op
                        }
                    }

                    if (value != null) {
                        profileEvent.put(next, value);

                        // cache the valid identifier: guid pairs
                        if (Constants.PROFILE_IDENTIFIER_KEYS.contains(next)) {
                            try {
                                cacheGUIDForIdentifier(guid, next, value.toString());
                            } catch (Throwable t) {
                                // no-op
                            }
                        }
                    }
                }
            }

            try {
                String carrier = this.deviceInfo.getCarrier();
                if (carrier != null && !carrier.equals("")) {
                    profileEvent.put("Carrier", carrier);
                }

                String cc = this.deviceInfo.getCountryCode();
                if (cc != null && !cc.equals("")) {
                    profileEvent.put("cc", cc);
                }

                profileEvent.put("tz", TimeZone.getDefault().getID());

                JSONObject event = new JSONObject();
                event.put("profile", profileEvent);
                queueEvent(context, event, Constants.PROFILE_EVENT);
            } catch (JSONException e) {
                getConfigLogger().verbose(getAccountId(), "FATAL: Creating basic profile update event failed!");
            }
        } catch (Throwable t) {
            getConfigLogger().verbose(getAccountId(), "Basic profile sync", t);
        }
    }

    private synchronized void pushDeepLink(Uri uri, boolean install) {
        if (uri == null) {
            return;
        }

        try {
            JSONObject referrer = UriHelper.getUrchinFromUri(uri);
            if (referrer.has("us")) {
                setSource(referrer.get("us").toString());
            }
            if (referrer.has("um")) {
                setMedium(referrer.get("um").toString());
            }
            if (referrer.has("uc")) {
                setCampaign(referrer.get("uc").toString());
            }

            referrer.put("referrer", uri.toString());
            if (install) {
                referrer.put("install", true);
            }
            recordPageEventWithExtras(referrer);

        } catch (Throwable t) {
            getConfigLogger().verbose(getAccountId(), "Failed to push deep link", t);
        }
    }

    //Event
    private void pushInitialEventsAsync() {
        postAsyncSafely("CleverTapAPI#pushInitialEventsAsync", new Runnable() {
            @Override
            public void run() {
                try {
                    getConfigLogger().verbose(getAccountId(), "Queuing daily events");
                    pushBasicProfile(null);
                } catch (Throwable t) {
                    getConfigLogger().verbose(getAccountId(), "Daily profile sync failed", t);
                }
            }
        });
    }

    //Event
    private Future<?> queueEvent(final Context context, final JSONObject event, final int eventType) {
        return postAsyncSafely("queueEvent", new Runnable() {
            @Override
            public void run() {
                if (shouldDropEvent(event, eventType)) {
                    return;
                }
                if (shouldDeferProcessingEvent(event, eventType)) {
                    getConfigLogger().debug(getAccountId(),
                            "App Launched not yet processed, re-queuing event " + event + "after 2s");
                    getHandlerUsingMainLooper().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            postAsyncSafely("queueEventWithDelay", new Runnable() {
                                @Override
                                public void run() {
                                    lazyCreateSession(context);
                                    addToQueue(context, event, eventType);
                                }
                            });
                        }
                    }, 2000);
                } else {
                    if (eventType == Constants.FETCH_EVENT) {
                        addToQueue(context, event, eventType);
                    } else {
                        lazyCreateSession(context);
                        addToQueue(context, event, eventType);
                    }
                }
            }
        });
    }

    private void queueEventInternal(final Context context, final JSONObject event, DBAdapter.Table table) {
        synchronized (eventLock) {
            DBAdapter adapter = loadDBAdapter(context);
            int returnCode = adapter.storeObject(event, table);

            if (returnCode > 0) {
                getConfigLogger().debug(getAccountId(), "Queued event: " + event.toString());
                getConfigLogger()
                        .verbose(getAccountId(), "Queued event to DB table " + table + ": " + event.toString());
            }
        }
    }

    //Event
    private void queueEventToDB(final Context context, final JSONObject event, final int type) {
        DBAdapter.Table table = (type == Constants.PROFILE_EVENT) ? DBAdapter.Table.PROFILE_EVENTS
                : DBAdapter.Table.EVENTS;
        queueEventInternal(context, event, table);
    }

    private void queuePushNotificationViewedEventToDB(final Context context, final JSONObject event) {
        queueEventInternal(context, event, DBAdapter.Table.PUSH_NOTIFICATION_VIEWED);
    }

    private Future<?> raiseEventForGeofences(String eventName, JSONObject geofenceProperties) {

        Future<?> future = null;

        JSONObject event = new JSONObject();
        try {
            event.put("evtName", eventName);
            event.put("evtData", geofenceProperties);

            Location location = new Location("");
            location.setLatitude(geofenceProperties.getDouble("triggered_lat"));
            location.setLongitude(geofenceProperties.getDouble("triggered_lng"));

            geofenceProperties.remove("triggered_lat");
            geofenceProperties.remove("triggered_lng");

            locationFromUser = location;

            future = queueEvent(context, event, Constants.RAISED_EVENT);
        } catch (JSONException e) {
            getConfigLogger().debug(getAccountId(), Constants.LOG_TAG_GEOFENCES +
                    "JSON Exception when raising GeoFence event "
                    + eventName + " - " + e.getLocalizedMessage());
        }

        return future;
    }

    private void recordDeviceIDErrors() {
        for (ValidationResult validationResult : this.deviceInfo.getValidationResults()) {
            validationResultStack.pushValidationResult(validationResult);
        }
    }

    //Event
    private void recordPageEventWithExtras(JSONObject extras) {
        try {
            JSONObject jsonObject = new JSONObject();
            // Add the extras
            if (extras != null && extras.length() > 0) {
                Iterator keys = extras.keys();
                while (keys.hasNext()) {
                    try {
                        String key = (String) keys.next();
                        jsonObject.put(key, extras.getString(key));
                    } catch (ClassCastException ignore) {
                        // Really won't get here
                    }
                }
            }
            queueEvent(context, jsonObject, Constants.PAGE_EVENT);
        } catch (Throwable t) {
            // We won't get here
        }
    }

    private void resetABTesting() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        if (!this.config.isAnalyticsOnly()) {
            if (!config.isABTestingEnabled()) {
                getConfigLogger().debug(config.getAccountId(), "AB Testing is not enabled for this instance");
                return;
            }
        }
        if (ctABTestController != null) {
            ctABTestController.resetWithGuid(getCleverTapID());
        }
    }

    private void resetAlarmScheduler(Context context) {
        if (getPingFrequency(context) <= 0) {
            stopAlarmScheduler(context);
        } else {
            stopAlarmScheduler(context);
            createAlarmScheduler(context);
        }
    }

    /**
     * Resets the Display Units in the cache
     */
    private void resetDisplayUnits() {
        if (mCTDisplayUnitController != null) {
            mCTDisplayUnitController.reset();
        } else {
            getConfigLogger().verbose(getAccountId(),
                    Constants.FEATURE_DISPLAY_UNIT + "Can't reset Display Units, DisplayUnitcontroller is null");
        }
    }

    private void resetFeatureFlags() {
        if (ctFeatureFlagsController != null && ctFeatureFlagsController.isInitialized()) {
            ctFeatureFlagsController.resetWithGuid(getCleverTapID());
            ctFeatureFlagsController.fetchFeatureFlags();
        }
    }

    // always call async
    private void resetInbox() {
        synchronized (inboxControllerLock) {
            this.ctInboxController = null;
        }
        _initializeInbox();
    }

    private void resetProductConfigs() {
        if (this.config.isAnalyticsOnly()) {
            getConfigLogger().debug(config.getAccountId(), "Product Config is not enabled for this instance");
            return;
        }
        if (ctProductConfigController != null) {
            ctProductConfigController.resetSettings();
        }
        ctProductConfigController = new CTProductConfigController(context, getCleverTapID(), config, this);
        getConfigLogger().verbose(config.getAccountId(), "Product Config reset");
    }

    private void runInstanceJobWork(final Context context, final JobParameters parameters) {
        postAsyncSafely("runningJobService", new Runnable() {
            @Override
            public void run() {
                if (pushProviders.isNotificationSupported()) {
                    Logger.v(getAccountId(), "Token is not present, not running the Job");
                    return;
                }

                Calendar now = Calendar.getInstance();

                int hour = now.get(Calendar.HOUR_OF_DAY); // Get hour in 24 hour format
                int minute = now.get(Calendar.MINUTE);

                Date currentTime = parseTimeToDate(hour + ":" + minute);
                Date startTime = parseTimeToDate(Constants.DND_START);
                Date endTime = parseTimeToDate(Constants.DND_STOP);

                if (isTimeBetweenDNDTime(startTime, endTime, currentTime)) {
                    Logger.v(getAccountId(), "Job Service won't run in default DND hours");
                    return;
                }

                long lastTS = loadDBAdapter(context).getLastUninstallTimestamp();

                if (lastTS == 0 || lastTS > System.currentTimeMillis() - 24 * 60 * 60 * 1000) {
                    try {
                        JSONObject eventObject = new JSONObject();
                        eventObject.put("bk", 1);
                        queueEvent(context, eventObject, Constants.PING_EVENT);

                        if (parameters == null) {
                            int pingFrequency = getPingFrequency(context);
                            AlarmManager alarmManager = (AlarmManager) context
                                    .getSystemService(Context.ALARM_SERVICE);
                            Intent cancelIntent = new Intent(CTBackgroundIntentService.MAIN_ACTION);
                            cancelIntent.setPackage(context.getPackageName());
                            PendingIntent alarmPendingIntent = PendingIntent
                                    .getService(context, getAccountId().hashCode(), cancelIntent,
                                            PendingIntent.FLAG_UPDATE_CURRENT);
                            if (alarmManager != null) {
                                alarmManager.cancel(alarmPendingIntent);
                            }
                            Intent alarmIntent = new Intent(CTBackgroundIntentService.MAIN_ACTION);
                            alarmIntent.setPackage(context.getPackageName());
                            PendingIntent alarmServicePendingIntent = PendingIntent
                                    .getService(context, getAccountId().hashCode(), alarmIntent,
                                            PendingIntent.FLAG_UPDATE_CURRENT);
                            if (alarmManager != null) {
                                if (pingFrequency != -1) {
                                    alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                            SystemClock.elapsedRealtime() + (pingFrequency
                                                    * Constants.ONE_MIN_IN_MILLIS),
                                            Constants.ONE_MIN_IN_MILLIS * pingFrequency, alarmServicePendingIntent);
                                }
                            }
                        }
                    } catch (JSONException e) {
                        Logger.v("Unable to raise background Ping event");
                    }

                }
            }
        });
    }

    //InApp
    private void runOnNotificationQueue(final Runnable runnable) {
        try {
            final boolean executeSync = Thread.currentThread().getId() == NOTIFICATION_THREAD_ID;

            if (executeSync) {
                runnable.run();
            } else {
                ns.submit(new Runnable() {
                    @Override
                    public void run() {
                        NOTIFICATION_THREAD_ID = Thread.currentThread().getId();
                        try {
                            runnable.run();
                        } catch (Throwable t) {
                            getConfigLogger().verbose(getAccountId(),
                                    "Notification executor service: Failed to complete the scheduled task", t);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            getConfigLogger()
                    .verbose(getAccountId(), "Failed to submit task to the notification executor service", t);
        }
    }

    private void schedulePushNotificationViewedQueueFlush(final Context context) {
        if (pushNotificationViewedRunnable == null) {
            pushNotificationViewedRunnable = new Runnable() {
                @Override
                public void run() {
                    getConfigLogger()
                            .verbose(getAccountId(), "Pushing Notification Viewed event onto queue flush async");
                    flushQueueAsync(context, EventGroup.PUSH_NOTIFICATION_VIEWED);
                }
            };
        }
        getHandlerUsingMainLooper().removeCallbacks(pushNotificationViewedRunnable);
        getHandlerUsingMainLooper().post(pushNotificationViewedRunnable);
    }

    //Event
    private void scheduleQueueFlush(final Context context) {
        if (commsRunnable == null) {
            commsRunnable = new Runnable() {
                @Override
                public void run() {
                    flushQueueAsync(context, EventGroup.REGULAR);
                    flushQueueAsync(context, EventGroup.PUSH_NOTIFICATION_VIEWED);
                }
            };
        }
        // Cancel any outstanding send runnables, and issue a new delayed one
        getHandlerUsingMainLooper().removeCallbacks(commsRunnable);
        getHandlerUsingMainLooper().postDelayed(commsRunnable, getDelayFrequency());

        getConfigLogger().verbose(getAccountId(), "Scheduling delayed queue flush on main event loop");
    }

    /**
     * @return true if the network request succeeded. Anything non 200 results in a false.
     */
    private boolean sendQueue(final Context context, final EventGroup eventGroup, final JSONArray queue) {

        if (queue == null || queue.length() <= 0) {
            return false;
        }

        if (getCleverTapID() == null) {
            getConfigLogger().debug(getAccountId(), "CleverTap Id not finalized, unable to send queue");
            return false;
        }

        HttpsURLConnection conn = null;
        try {
            final String endpoint = getEndpoint(false, eventGroup);

            // This is just a safety check, which would only arise
            // if upstream didn't adhere to the protocol (sent nothing during the initial handshake)
            if (endpoint == null) {
                getConfigLogger().debug(getAccountId(), "Problem configuring queue endpoint, unable to send queue");
                return false;
            }

            conn = buildHttpsURLConnection(endpoint);

            final String body;
            final String req = insertHeader(context, queue);
            if (req == null) {
                getConfigLogger().debug(getAccountId(), "Problem configuring queue request, unable to send queue");
                return false;
            }

            getConfigLogger().debug(getAccountId(), "Send queue contains " + queue.length() + " items: " + req);
            getConfigLogger().debug(getAccountId(), "Sending queue to: " + endpoint);
            conn.setDoOutput(true);
            // noinspection all
            conn.getOutputStream().write(req.getBytes("UTF-8"));

            final int responseCode = conn.getResponseCode();

            // Always check for a 200 OK
            if (responseCode != 200) {
                throw new IOException("Response code is not 200. It is " + responseCode);
            }

            // Check for a change in domain
            final String newDomain = conn.getHeaderField(Constants.HEADER_DOMAIN_NAME);
            if (newDomain != null && newDomain.trim().length() > 0) {
                if (hasDomainChanged(newDomain)) {
                    // The domain has changed. Return a status of -1 so that the caller retries
                    setDomain(context, newDomain);
                    getConfigLogger().debug(getAccountId(),
                            "The domain has changed to " + newDomain + ". The request will be retried shortly.");
                    return false;
                }
            }

            if (processIncomingHeaders(context, conn)) {
                // noinspection all
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                body = sb.toString();
                processResponse(context, body);
            }

            setLastRequestTimestamp(context, currentRequestTimestamp);
            setFirstRequestTimestampIfNeeded(context, currentRequestTimestamp);

            getConfigLogger().debug(getAccountId(), "Queue sent successfully");

            mResponseFailureCount = 0;
            networkRetryCount = 0; //reset retry count when queue is sent successfully
            return true;
        } catch (Throwable e) {
            getConfigLogger().debug(getAccountId(),
                    "An exception occurred while sending the queue, will retry: " + e.getLocalizedMessage());
            mResponseFailureCount++;
            networkRetryCount++;
            scheduleQueueFlush(context);
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.getInputStream().close();
                    conn.disconnect();
                } catch (Throwable t) {
                    // Ignore
                }
            }
        }
    }

    private void setCurrentUserOptOutStateFromStorage() {
        String key = optOutKey();
        if (key == null) {
            getConfigLogger().verbose(getAccountId(),
                    "Unable to set current user OptOut state from storage: storage key is null");
            return;
        }
        boolean storedOptOut = StorageHelper.getBooleanFromPrefs(context, config, key);
        setCurrentUserOptedOut(storedOptOut);
        getConfigLogger().verbose(getAccountId(),
                "Set current user OptOut state from storage to: " + storedOptOut + " for key: " + key);
    }

    // -----------------------------------------------------------------------//
    // ********                        Display Unit LOGIC                *****//
    // -----------------------------------------------------------------------//

    private void setDeviceNetworkInfoReportingFromStorage() {
        boolean enabled = StorageHelper.getBooleanFromPrefs(context, config, Constants.NETWORK_INFO);
        getConfigLogger()
                .verbose(getAccountId(), "Setting device network info reporting state from storage to " + enabled);
        enableNetworkInfoReporting = enabled;
    }

    private void setDomain(final Context context, String domainName) {
        getConfigLogger().verbose(getAccountId(), "Setting domain to " + domainName);
        StorageHelper.putString(context, StorageHelper.storageKeyWithSuffix(config, Constants.KEY_DOMAIN_NAME),
                domainName);
    }

    private void setFirstRequestTimestampIfNeeded(Context context, int ts) {
        if (getFirstRequestTimestamp() > 0) {
            return;
        }
        StorageHelper.putInt(context, StorageHelper.storageKeyWithSuffix(config, Constants.KEY_FIRST_TS), ts);
    }

    @SuppressLint("CommitPrefEdits")
    private void setI(Context context, long i) {
        final SharedPreferences prefs = StorageHelper.getPreferences(context, Constants.NAMESPACE_IJ);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(StorageHelper.storageKeyWithSuffix(config, Constants.KEY_I), i);
        StorageHelper.persist(editor);
    }

    @SuppressLint("CommitPrefEdits")
    private void setJ(Context context, long j) {
        final SharedPreferences prefs = StorageHelper.getPreferences(context, Constants.NAMESPACE_IJ);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(StorageHelper.storageKeyWithSuffix(config, Constants.KEY_J), j);
        StorageHelper.persist(editor);
    }

    //Session
    private void setLastRequestTimestamp(Context context, int ts) {
        StorageHelper.putInt(context, StorageHelper.storageKeyWithSuffix(config, Constants.KEY_LAST_TS), ts);
    }

    //Session
    private void setLastVisitTime() {
        EventDetail ed = getLocalDataStore().getEventDetail(Constants.APP_LAUNCHED_EVENT);
        if (ed == null) {
            lastVisitTime = -1;
        } else {
            lastVisitTime = ed.getLastTime();
        }
    }

    //Util
    private void setMuted(final Context context, boolean mute) {
        if (mute) {
            final int now = (int) (System.currentTimeMillis() / 1000);
            StorageHelper.putInt(context, StorageHelper.storageKeyWithSuffix(config, Constants.KEY_MUTED), now);
            setDomain(context, null);

            // Clear all the queues
            postAsyncSafely("CommsManager#setMuted", new Runnable() {
                @Override
                public void run() {
                    clearQueues(context);
                }
            });
        } else {
            StorageHelper.putInt(context, StorageHelper.storageKeyWithSuffix(config, Constants.KEY_MUTED), 0);
        }
    }

    private void setPingFrequency(Context context, int pingFrequency) {
        StorageHelper.putInt(context, Constants.PING_FREQUENCY, pingFrequency);
    }

    private void setSpikyDomain(final Context context, String spikyDomainName) {
        getConfigLogger().verbose(getAccountId(), "Setting spiky domain to " + spikyDomainName);
        StorageHelper.putString(context, StorageHelper.storageKeyWithSuffix(config, Constants.SPIKY_KEY_DOMAIN_NAME),
                spikyDomainName);
    }

    // -----------------------------------------------------------------------//
    // ********                        Feature Flags Logic               *****//
    // -----------------------------------------------------------------------//

    // ********                       Feature Flags Public API           *****//

    private boolean shouldDeferProcessingEvent(JSONObject event, int eventType) {
        //noinspection SimplifiableIfStatement
        if (getConfig().isCreatedPostAppLaunch()) {
            return false;
        }
        if (event.has("evtName")) {
            try {
                if (Arrays.asList(Constants.SYSTEM_EVENTS).contains(event.getString("evtName"))) {
                    return false;
                }
            } catch (JSONException e) {
                //no-op
            }
        }
        return (eventType == Constants.RAISED_EVENT && !isAppLaunchPushed());
    }

    private boolean shouldDropEvent(JSONObject event, int eventType) {
        if (eventType == Constants.FETCH_EVENT) {
            return false;
        }

        if (isCurrentUserOptedOut()) {
            String eventString = event == null ? "null" : event.toString();
            getConfigLogger().debug(getAccountId(), "Current user is opted out dropping event: " + eventString);
            return true;
        }

        if (isMuted()) {
            getConfigLogger().verbose(getAccountId(), "CleverTap is muted, dropping event - " + event.toString());
            return true;
        }

        return false;
    }

    // ********                        Feature Flags Internal methods        *****//

    private void showInAppNotificationIfAny() {
        if (!this.config.isAnalyticsOnly()) {
            runOnNotificationQueue(new Runnable() {
                @Override
                public void run() {
                    _showNotificationIfAvailable(context);
                }
            });
        }
    }

    //InApp
    @SuppressWarnings({"unused"})
    private void showNotificationIfAvailable(final Context context) {
        if (!this.config.isAnalyticsOnly()) {
            runOnNotificationQueue(new Runnable() {
                @Override
                public void run() {
                    _showNotificationIfAvailable(context);
                }
            });
        }
    }

    private void stopAlarmScheduler(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent cancelIntent = new Intent(CTBackgroundIntentService.MAIN_ACTION);
        cancelIntent.setPackage(context.getPackageName());
        PendingIntent alarmPendingIntent = PendingIntent
                .getService(context, getAccountId().hashCode(), cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (alarmManager != null && alarmPendingIntent != null) {
            alarmManager.cancel(alarmPendingIntent);
        }
    }

    private void triggerNotification(Context context, Bundle extras, String notifMessage, String notifTitle,
            int notificationId) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            String notificationManagerError = "Unable to render notification, Notification Manager is null.";
            getConfigLogger().debug(getAccountId(), notificationManagerError);
            return;
        }

        String channelId = extras.getString(Constants.WZRK_CHANNEL_ID, "");
        boolean requiresChannelId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int messageCode = -1;
            String value = "";

            if (channelId.isEmpty()) {
                messageCode = Constants.CHANNEL_ID_MISSING_IN_PAYLOAD;
                value = extras.toString();
            } else if (notificationManager.getNotificationChannel(channelId) == null) {
                messageCode = Constants.CHANNEL_ID_NOT_REGISTERED;
                value = channelId;
            }
            if (messageCode != -1) {
                ValidationResult channelIdError = ValidationResultFactory.create(512, messageCode, value);
                getConfigLogger().debug(getAccountId(), channelIdError.getErrorDesc());
                validationResultStack.pushValidationResult(channelIdError);
                return;
            }
        }

        String icoPath = extras.getString(Constants.NOTIF_ICON);
        Intent launchIntent = new Intent(context, CTPushNotificationReceiver.class);

        PendingIntent pIntent;

        // Take all the properties from the notif and add it to the intent
        launchIntent.putExtras(extras);
        launchIntent.removeExtra(Constants.WZRK_ACTIONS);
        pIntent = PendingIntent.getBroadcast(context, (int) System.currentTimeMillis(),
                launchIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Style style;
        String bigPictureUrl = extras.getString(Constants.WZRK_BIG_PICTURE);
        if (bigPictureUrl != null && bigPictureUrl.startsWith("http")) {
            try {
                Bitmap bpMap = Utils.getNotificationBitmap(bigPictureUrl, false, context);

                if (bpMap == null) {
                    throw new Exception("Failed to fetch big picture!");
                }

                if (extras.containsKey(Constants.WZRK_MSG_SUMMARY)) {
                    String summaryText = extras.getString(Constants.WZRK_MSG_SUMMARY);
                    style = new NotificationCompat.BigPictureStyle()
                            .setSummaryText(summaryText)
                            .bigPicture(bpMap);
                } else {
                    style = new NotificationCompat.BigPictureStyle()
                            .setSummaryText(notifMessage)
                            .bigPicture(bpMap);
                }
            } catch (Throwable t) {
                style = new NotificationCompat.BigTextStyle()
                        .bigText(notifMessage);
                getConfigLogger()
                        .verbose(getAccountId(), "Falling back to big text notification, couldn't fetch big picture",
                                t);
            }
        } else {
            style = new NotificationCompat.BigTextStyle()
                    .bigText(notifMessage);
        }

        int smallIcon;
        try {
            String x = ManifestInfo.getInstance(context).getNotificationIcon();
            if (x == null) {
                throw new IllegalArgumentException();
            }
            smallIcon = context.getResources().getIdentifier(x, "drawable", context.getPackageName());
            if (smallIcon == 0) {
                throw new IllegalArgumentException();
            }
        } catch (Throwable t) {
            smallIcon = DeviceInfo.getAppIconAsIntId(context);
        }

        int priorityInt = NotificationCompat.PRIORITY_DEFAULT;
        String priority = extras.getString(Constants.NOTIF_PRIORITY);
        if (priority != null) {
            if (priority.equals(Constants.PRIORITY_HIGH)) {
                priorityInt = NotificationCompat.PRIORITY_HIGH;
            }
            if (priority.equals(Constants.PRIORITY_MAX)) {
                priorityInt = NotificationCompat.PRIORITY_MAX;
            }
        }

        // if we have no user set notificationID then try collapse key
        if (notificationId == Constants.EMPTY_NOTIFICATION_ID) {
            try {
                Object collapse_key = extras.get(Constants.WZRK_COLLAPSE);
                if (collapse_key != null) {
                    if (collapse_key instanceof Number) {
                        notificationId = ((Number) collapse_key).intValue();
                    } else if (collapse_key instanceof String) {
                        try {
                            notificationId = Integer.parseInt(collapse_key.toString());
                            getConfigLogger().debug(getAccountId(),
                                    "Converting collapse_key: " + collapse_key + " to notificationId int: "
                                            + notificationId);
                        } catch (NumberFormatException e) {
                            notificationId = (collapse_key.toString().hashCode());
                            getConfigLogger().debug(getAccountId(),
                                    "Converting collapse_key: " + collapse_key + " to notificationId int: "
                                            + notificationId);
                        }
                    }
                }
            } catch (NumberFormatException e) {
                // no-op
            }
        } else {
            getConfigLogger().debug(getAccountId(), "Have user provided notificationId: " + notificationId
                    + " won't use collapse_key (if any) as basis for notificationId");
        }

        // if after trying collapse_key notification is still empty set to random int
        if (notificationId == Constants.EMPTY_NOTIFICATION_ID) {
            notificationId = (int) (Math.random() * 100);
            getConfigLogger().debug(getAccountId(), "Setting random notificationId: " + notificationId);
        }

        NotificationCompat.Builder nb;
        if (requiresChannelId) {
            nb = new NotificationCompat.Builder(context, channelId);

            // choices here are Notification.BADGE_ICON_NONE = 0, Notification.BADGE_ICON_SMALL = 1, Notification.BADGE_ICON_LARGE = 2.  Default is  Notification.BADGE_ICON_LARGE
            String badgeIconParam = extras.getString(Constants.WZRK_BADGE_ICON, null);
            if (badgeIconParam != null) {
                try {
                    int badgeIconType = Integer.parseInt(badgeIconParam);
                    if (badgeIconType >= 0) {
                        nb.setBadgeIconType(badgeIconType);
                    }
                } catch (Throwable t) {
                    // no-op
                }
            }

            String badgeCountParam = extras.getString(Constants.WZRK_BADGE_COUNT, null);
            if (badgeCountParam != null) {
                try {
                    int badgeCount = Integer.parseInt(badgeCountParam);
                    if (badgeCount >= 0) {
                        nb.setNumber(badgeCount);
                    }
                } catch (Throwable t) {
                    // no-op
                }
            }
            if (extras.containsKey(Constants.WZRK_SUBTITLE)) {
                nb.setSubText(extras.getString(Constants.WZRK_SUBTITLE));
            }
        } else {
            // noinspection all
            nb = new NotificationCompat.Builder(context);
        }

        if (extras.containsKey(Constants.WZRK_COLOR)) {
            int color = Color.parseColor(extras.getString(Constants.WZRK_COLOR));
            nb.setColor(color);
            nb.setColorized(true);
        }

        nb.setContentTitle(notifTitle)
                .setContentText(notifMessage)
                .setContentIntent(pIntent)
                .setAutoCancel(true)
                .setStyle(style)
                .setPriority(priorityInt)
                .setSmallIcon(smallIcon);

        nb.setLargeIcon(Utils.getNotificationBitmap(icoPath, true, context));

        try {
            if (extras.containsKey(Constants.WZRK_SOUND)) {
                Uri soundUri = null;

                Object o = extras.get(Constants.WZRK_SOUND);

                if ((o instanceof Boolean && (Boolean) o)) {
                    soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                } else if (o instanceof String) {
                    String s = (String) o;
                    if (s.equals("true")) {
                        soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                    } else if (!s.isEmpty()) {
                        if (s.contains(".mp3") || s.contains(".ogg") || s.contains(".wav")) {
                            s = s.substring(0, (s.length() - 4));
                        }
                        soundUri = Uri
                                .parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.getPackageName()
                                        + "/raw/" + s);

                    }
                }

                if (soundUri != null) {
                    nb.setSound(soundUri);
                }
            }
        } catch (Throwable t) {
            getConfigLogger().debug(getAccountId(), "Could not process sound parameter", t);
        }

        // add actions if any
        JSONArray actions = null;
        String actionsString = extras.getString(Constants.WZRK_ACTIONS);
        if (actionsString != null) {
            try {
                actions = new JSONArray(actionsString);
            } catch (Throwable t) {
                getConfigLogger()
                        .debug(getAccountId(), "error parsing notification actions: " + t.getLocalizedMessage());
            }
        }

        String intentServiceName = ManifestInfo.getInstance(context).getIntentServiceName();
        Class clazz = null;
        if (intentServiceName != null) {
            try {
                clazz = Class.forName(intentServiceName);
            } catch (ClassNotFoundException e) {
                try {
                    clazz = Class.forName("com.clevertap.android.sdk.pushnotification.CTNotificationIntentService");
                } catch (ClassNotFoundException ex) {
                    Logger.d("No Intent Service found");
                }
            }
        } else {
            try {
                clazz = Class.forName("com.clevertap.android.sdk.pushnotification.CTNotificationIntentService");
            } catch (ClassNotFoundException ex) {
                Logger.d("No Intent Service found");
            }
        }

        boolean isCTIntentServiceAvailable = isServiceAvailable(context, clazz);

        if (actions != null && actions.length() > 0) {
            for (int i = 0; i < actions.length(); i++) {
                try {
                    JSONObject action = actions.getJSONObject(i);
                    String label = action.optString("l");
                    String dl = action.optString("dl");
                    String ico = action.optString(Constants.NOTIF_ICON);
                    String id = action.optString("id");
                    boolean autoCancel = action.optBoolean("ac", true);
                    if (label.isEmpty() || id.isEmpty()) {
                        getConfigLogger().debug(getAccountId(),
                                "not adding push notification action: action label or id missing");
                        continue;
                    }
                    int icon = 0;
                    if (!ico.isEmpty()) {
                        try {
                            icon = context.getResources().getIdentifier(ico, "drawable", context.getPackageName());
                        } catch (Throwable t) {
                            getConfigLogger().debug(getAccountId(),
                                    "unable to add notification action icon: " + t.getLocalizedMessage());
                        }
                    }

                    boolean sendToCTIntentService = (autoCancel && isCTIntentServiceAvailable);

                    Intent actionLaunchIntent;
                    if (sendToCTIntentService) {
                        actionLaunchIntent = new Intent(CTNotificationIntentService.MAIN_ACTION);
                        actionLaunchIntent.setPackage(context.getPackageName());
                        actionLaunchIntent.putExtra("ct_type", CTNotificationIntentService.TYPE_BUTTON_CLICK);
                        if (!dl.isEmpty()) {
                            actionLaunchIntent.putExtra("dl", dl);
                        }
                    } else {
                        if (!dl.isEmpty()) {
                            actionLaunchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(dl));
                        } else {
                            actionLaunchIntent = context.getPackageManager()
                                    .getLaunchIntentForPackage(context.getPackageName());
                        }
                    }

                    if (actionLaunchIntent != null) {
                        actionLaunchIntent.putExtras(extras);
                        actionLaunchIntent.removeExtra(Constants.WZRK_ACTIONS);
                        actionLaunchIntent.putExtra("actionId", id);
                        actionLaunchIntent.putExtra("autoCancel", autoCancel);
                        actionLaunchIntent.putExtra("wzrk_c2a", id);
                        actionLaunchIntent.putExtra("notificationId", notificationId);

                        actionLaunchIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    }

                    PendingIntent actionIntent;
                    int requestCode = ((int) System.currentTimeMillis()) + i;
                    if (sendToCTIntentService) {
                        actionIntent = PendingIntent.getService(context, requestCode,
                                actionLaunchIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    } else {
                        actionIntent = PendingIntent.getActivity(context, requestCode,
                                actionLaunchIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    }
                    nb.addAction(icon, label, actionIntent);

                } catch (Throwable t) {
                    getConfigLogger()
                            .debug(getAccountId(), "error adding notification action : " + t.getLocalizedMessage());
                }
            }
        }

        Notification n = nb.build();
        notificationManager.notify(notificationId, n);
        getConfigLogger().debug(getAccountId(), "Rendered notification: " + n.toString());

        String ttl = extras.getString(Constants.WZRK_TIME_TO_LIVE,
                (System.currentTimeMillis() + Constants.DEFAULT_PUSH_TTL) / 1000 + "");
        long wzrk_ttl = Long.parseLong(ttl);
        String wzrk_pid = extras.getString(Constants.WZRK_PUSH_ID);
        DBAdapter dbAdapter = loadDBAdapter(context);
        getConfigLogger().verbose("Storing Push Notification..." + wzrk_pid + " - with ttl - " + ttl);
        dbAdapter.storePushNotificationId(wzrk_pid, wzrk_ttl);

        boolean notificationViewedEnabled = "true".equals(extras.getString(Constants.WZRK_RNV, ""));
        if (!notificationViewedEnabled) {
            ValidationResult notificationViewedError = ValidationResultFactory
                    .create(512, Constants.NOTIFICATION_VIEWED_DISABLED, extras.toString());
            getConfigLogger().debug(notificationViewedError.getErrorDesc());
            validationResultStack.pushValidationResult(notificationViewedError);
            return;
        }
        pushNotificationViewedEvent(extras);
    }

    private void updateBlacklistedActivitySet() {
        if (inappActivityExclude == null) {
            inappActivityExclude = new HashSet<>();
            try {
                String activities = ManifestInfo.getInstance(context).getExcludedActivities();
                if (activities != null) {
                    String[] split = activities.split(",");
                    for (String a : split) {
                        inappActivityExclude.add(a.trim());
                    }
                }
            } catch (Throwable t) {
                // Ignore
            }
            getConfigLogger().debug(getAccountId(),
                    "In-app notifications will not be shown on " + Arrays.toString(inappActivityExclude.toArray()));
        }
    }

    // helper extracts the cursor data from the db object
    private QueueCursor updateCursorForDBObject(JSONObject dbObject, QueueCursor cursor) {

        if (dbObject == null) {
            return cursor;
        }

        Iterator<String> keys = dbObject.keys();
        if (keys.hasNext()) {
            String key = keys.next();
            cursor.setLastId(key);
            try {
                cursor.setData(dbObject.getJSONArray(key));
            } catch (JSONException e) {
                cursor.setLastId(null);
                cursor.setData(null);
            }
        }

        return cursor;
    }
    // -----------------------------------------------------------------------//
    // ********                        PRODUCT CONFIG Logic              *****//
    // -----------------------------------------------------------------------//

    // ********                       PRODUCT CONFIG Public API           *****//

    //Util
    // only call async
    private void updateLocalStore(final Context context, final JSONObject event, final int type) {
        if (type == Constants.RAISED_EVENT) {
            getLocalDataStore().persistEvent(context, event, type);
        }
    }

    /**
     * updates the ping frequency if there is a change & reschedules existing ping tasks.
     */
    private void updatePingFrequencyIfNeeded(final Context context, int frequency) {
        getConfigLogger().verbose("Ping frequency received - " + frequency);
        getConfigLogger().verbose("Stored Ping Frequency - " + getPingFrequency(context));
        if (frequency != getPingFrequency(context)) {
            setPingFrequency(context, frequency);
            if (this.config.isBackgroundSync() && !this.config.isAnalyticsOnly()) {
                postAsyncSafely("createOrResetJobScheduler", new Runnable() {
                    @Override
                    public void run() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            getConfigLogger().verbose("Creating job");
                            createOrResetJobScheduler(context);
                        } else {
                            getConfigLogger().verbose("Resetting alarm");
                            resetAlarmScheduler(context);
                        }
                    }
                });
            }
        }
    }

    // ********                       PRODUCT CONFIG Internal API           *****//

    //Deprecation warning because Google Play install referrer via intent will be deprecated in March 2020
    @Deprecated
    static void handleInstallReferrer(Context context, Intent intent) {
        if (instances == null) {
            Logger.v("No CleverTap Instance found");
            CleverTapAPI instance = CleverTapAPI.getDefaultInstance(context);
            if (instance != null) {
                instance.pushInstallReferrer(intent);
            }
            return;
        }

        for (String accountId : CleverTapAPI.instances.keySet()) {
            CleverTapAPI instance = CleverTapAPI.instances.get(accountId);
            if (instance != null) {
                instance.pushInstallReferrer(intent);
            }
        }

    }

    /**
     * Use this method to notify CleverTap that the app is in foreground
     *
     * @param appForeground boolean true/false
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public static void setAppForeground(boolean appForeground) {
        CleverTapAPI.appForeground = appForeground;
    }

    static void onActivityCreated(Activity activity) {
        onActivityCreated(activity, null);
    }

    // static lifecycle callbacks
    static void onActivityCreated(Activity activity, String cleverTapID) {
        // make sure we have at least the default instance created here.
        if (instances == null) {
            CleverTapAPI.createInstanceIfAvailable(activity.getApplicationContext(), null, cleverTapID);
        }

        if (instances == null) {
            Logger.v("Instances is null in onActivityCreated!");
            return;
        }

        boolean alreadyProcessedByCleverTap = false;
        Bundle notification = null;
        Uri deepLink = null;
        String _accountId = null;

        // check for launch deep link
        try {
            Intent intent = activity.getIntent();
            deepLink = intent.getData();
            if (deepLink != null) {
                Bundle queryArgs = UriHelper.getAllKeyValuePairs(deepLink.toString(), true);
                _accountId = queryArgs.getString(Constants.WZRK_ACCT_ID_KEY);
            }
        } catch (Throwable t) {
            // Ignore
        }

        // check for launch via notification click
        try {
            notification = activity.getIntent().getExtras();
            if (notification != null && !notification.isEmpty()) {
                try {
                    alreadyProcessedByCleverTap = (notification.containsKey(Constants.WZRK_FROM_KEY)
                            && Constants.WZRK_FROM.equals(notification.get(Constants.WZRK_FROM_KEY)));
                    if (alreadyProcessedByCleverTap) {
                        Logger.v("ActivityLifecycleCallback: Notification Clicked already processed for "
                                + notification.toString() + ", dropping duplicate.");
                    }
                    if (notification.containsKey(Constants.WZRK_ACCT_ID_KEY)) {
                        _accountId = (String) notification.get(Constants.WZRK_ACCT_ID_KEY);
                    }
                } catch (Throwable t) {
                    // no-op
                }
            }
        } catch (Throwable t) {
            // Ignore
        }

        if (alreadyProcessedByCleverTap && deepLink == null) {
            return;
        }

        try {
            for (String accountId : CleverTapAPI.instances.keySet()) {
                CleverTapAPI instance = CleverTapAPI.instances.get(accountId);
                if (instance != null) {
                    instance.coreState.getActivityLifeCycleManager()
                            .onActivityCreated(notification, deepLink, _accountId);
                }
            }
        } catch (Throwable t) {
            Logger.v("Throwable - " + t.getLocalizedMessage());
        }
    }

    private static CleverTapAPI createInstanceIfAvailable(Context context, String _accountId) {
        return createInstanceIfAvailable(context, _accountId, null);
    }

    private static @Nullable
    CleverTapAPI createInstanceIfAvailable(Context context, String _accountId, String cleverTapID) {
        try {
            if (_accountId == null) {
                try {
                    return CleverTapAPI.getDefaultInstance(context, cleverTapID);
                } catch (Throwable t) {
                    Logger.v("Error creating shared Instance: ", t.getCause());
                    return null;
                }
            }
            String configJson = StorageHelper.getString(context, "instance:" + _accountId, "");
            if (!configJson.isEmpty()) {
                CleverTapInstanceConfig config = CleverTapInstanceConfig.createInstance(configJson);
                Logger.v("Inflated Instance Config: " + configJson);
                return config != null ? CleverTapAPI.instanceWithConfig(context, config, cleverTapID) : null;
            } else {
                try {
                    CleverTapAPI instance = CleverTapAPI.getDefaultInstance(context);
                    return (instance != null && instance.coreState.getConfig().getAccountId().equals(_accountId))
                            ? instance : null;
                } catch (Throwable t) {
                    Logger.v("Error creating shared Instance: ", t.getCause());
                    return null;
                }
            }
        } catch (Throwable t) {
            return null;
        }
    }

    @RestrictTo(Scope.LIBRARY_GROUP)
    public static ArrayList<CleverTapAPI> getAvailableInstances(Context context) {
        ArrayList<CleverTapAPI> apiArrayList = new ArrayList<>();
        if (instances == null || instances.isEmpty()) {
            CleverTapAPI cleverTapAPI = CleverTapAPI.getDefaultInstance(context);
            if (cleverTapAPI != null) {
                apiArrayList.add(cleverTapAPI);
            }
        } else {
            apiArrayList.addAll(CleverTapAPI.instances.values());
        }
        return apiArrayList;
    }

    /**
     * Creates default {@link CleverTapInstanceConfig} object and returns it
     *
     * @param context The Android context
     * @return The {@link CleverTapInstanceConfig} object
     */
    private static CleverTapInstanceConfig getDefaultConfig(Context context) {
        ManifestInfo manifest = ManifestInfo.getInstance(context);
        String accountId = manifest.getAccountId();
        String accountToken = manifest.getAcountToken();
        String accountRegion = manifest.getAccountRegion();
        String proxyDomain = manifest.getProxyDomain();
        String spikyProxyDomain = manifest.getSpikeyProxyDomain();
        if (accountId == null || accountToken == null) {
            Logger.i(
                    "Account ID or Account token is missing from AndroidManifest.xml, unable to create default instance");
            return null;
        }
        if (accountRegion == null) {
            Logger.i("Account Region not specified in the AndroidManifest - using default region");
        }
        CleverTapInstanceConfig defaultInstanceConfig = CleverTapInstanceConfig.createDefaultInstance(context, accountId, accountToken, accountRegion);

        if (proxyDomain != null && proxyDomain.trim().length() > 0) {
            defaultInstanceConfig.setProxyDomain(proxyDomain);
        }
        if (spikyProxyDomain != null && spikyProxyDomain.trim().length() > 0) {
            defaultInstanceConfig.setSpikyProxyDomain(spikyProxyDomain);
        }
        return defaultInstanceConfig;
    }

    private static @Nullable
    CleverTapAPI getDefaultInstanceOrFirstOther(Context context) {
        CleverTapAPI instance = getDefaultInstance(context);
        if (instance == null && instances != null && !instances.isEmpty()) {
            for (String accountId : CleverTapAPI.instances.keySet()) {
                instance = CleverTapAPI.instances.get(accountId);
                if (instance != null) {
                    break;
                }
            }
        }
        return instance;
    }

    public static void setNotificationHandler(NotificationHandler notificationHandler) {
        sNotificationHandler = notificationHandler;
    }

    public static void setSignedCallNotificationHandler(NotificationHandler notificationHandler) {
        sSignedCallNotificationHandler = notificationHandler;
    }

    /**
     * Returns a unique identifier by which CleverTap identifies this user, on Main thread Callback.
     *
     * @param onInitCleverTapIDListener non-null callback to retrieve identifier on main thread.
     */
    public void getCleverTapID(@NonNull final OnInitCleverTapIDListener onInitCleverTapIDListener) {
        Task<Void> taskDeviceCachedInfo = CTExecutorFactory.executors(getConfig()).ioTask();
        taskDeviceCachedInfo.execute("getCleverTapID", new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                String deviceID = coreState.getDeviceInfo().getDeviceID();
                if (deviceID != null) {
                    onInitCleverTapIDListener.onInitCleverTapID(deviceID);
                } else {
                    /**
                     * If cleverTapID not yet generated during first init then set listener, through which
                     * cleverTapID will be notified when it's generated and ready to use from deviceIDCreated()
                     *
                     * Setting callback here makes sure that callback will be give only once, either from
                     * getCleverTapID() or deviceIDCreated()
                     */
                    coreState.getCallbackManager().setOnInitCleverTapIDListener(onInitCleverTapIDListener);
                }
                return null;
            }
        });
    }

    //TODO: start synchronizing entire flow from here
    public Future<?> renderPushNotification(@NonNull INotificationRenderer iNotificationRenderer, Context context,
            Bundle extras) {

        CleverTapInstanceConfig config = coreState.getConfig();
        Future<?> future = null;

        try {
            Task<Void> task = CTExecutorFactory.executors(config).postAsyncSafelyTask();
            future = task.submit("CleverTapAPI#renderPushNotification",
                    new Callable<Void>() {
                        @Override
                        public Void call() {
                            synchronized (coreState.getPushProviders().getPushRenderingLock()) {
                                coreState.getPushProviders().setPushNotificationRenderer(iNotificationRenderer);

                                if (extras != null && extras.containsKey(Constants.PT_NOTIF_ID)) {
                                    coreState.getPushProviders()
                                            ._createNotification(context, extras,
                                                    extras.getInt(Constants.PT_NOTIF_ID));
                                } else {
                                    coreState.getPushProviders()
                                            ._createNotification(context, extras, Constants.EMPTY_NOTIFICATION_ID);
                                }
                            }
                            return null;
                        }
                    });
        } catch (Throwable t) {
            config.getLogger().debug(config.getAccountId(), "Failed to process renderPushNotification()", t);
        }

        return future;

    }

    @RestrictTo(Scope.LIBRARY_GROUP)
    public void renderPushNotificationOnCallerThread(@NonNull INotificationRenderer iNotificationRenderer, Context context,
            Bundle extras) {

        CleverTapInstanceConfig config = coreState.getConfig();

        try {
            synchronized (coreState.getPushProviders().getPushRenderingLock()) {
                config.getLogger().verbose(config.getAccountId(),
                        "rendering push on caller thread with id = " + Thread.currentThread().getId());
                coreState.getPushProviders().setPushNotificationRenderer(iNotificationRenderer);

                if (extras != null && extras.containsKey(Constants.PT_NOTIF_ID)) {
                    coreState.getPushProviders()
                            ._createNotification(context, extras,
                                    extras.getInt(Constants.PT_NOTIF_ID));
                } else {
                    coreState.getPushProviders()
                            ._createNotification(context, extras, Constants.EMPTY_NOTIFICATION_ID);
                }
            }
        } catch (Throwable t) {
            config.getLogger().debug(config.getAccountId(), "Failed to process renderPushNotification()", t);
        }

    }

    /**
     * Use this method if you want to run xiaomi sdk all devices, xiaomi only devices or turn off push on all devices.
     * Default value is {@link PushConstants#ALL_DEVICES}
     * @param xpsRunningDevices can be one of the following,<br>
     *                          1. {@link PushConstants#ALL_DEVICES} (int value = 1)<br>
     *                          2. {@link PushConstants#XIAOMI_MIUI_DEVICES} (int value = 2)<br>
     *                          3. {@link PushConstants#NO_DEVICES} (int value = 3)<br>
     */
    public static void enableXiaomiPushOn(@XiaomiPush int xpsRunningDevices) {
        PushType.XPS.setRunningDevices(xpsRunningDevices);
    }

    public void resetUser(CTEventNotifier eventNotifier) {
        coreState.getLoginController().clearData(getCleverTapID(),eventNotifier);
    }

    public void deferClevertapEventsUntilProfileAndDeviceIsFetched(boolean value) {
        coreState.getBaseEventQueueManager().deferClevertapEventsUntilProfileAndDeviceIsFetched(value);
    }

    public void setTrackingDeviceId(String trackingDeviceId) {
        coreState.getDeviceInfo().setTrackingDeviceId(trackingDeviceId);
    }

    public void setTrackingEnabled(boolean trackingEnabled) {
        coreState.getDeviceInfo().setTrackingEnabled(trackingEnabled);
    }

   /**
     *
     * @return one of the following,<br>
     *                          1. {@link XiaomiPush#ALL_DEVICES} (int value = 1)<br>
     *                          2. {@link XiaomiPush#XIAOMI_MIUI_DEVICES} (int value = 2)<br>
     *                          3. {@link XiaomiPush#NO_DEVICES} (int value = 3)<br>
     */
    public static @XiaomiPush int getEnableXiaomiPushOn() {
        return PushType.XPS.getRunningDevices();
    }

    /**
     * Retrieves a notification bitmap with a specified timeout and size constraint.
     *
     * @param context           The context of the application. Must be non null.
     * @param bundle            The {@link Bundle} object received by the push receiver. Must be non null.
     * @param bitmapSrcUrl      The URL of the bitmap to download.
     * @param fallbackToAppIcon Specifies whether to fallback to the app icon if the bitmap is not available.
     * @param timeoutInMillis   The timeout duration for the bitmap download in milliseconds.  Must be in range of 1 - 20000.
     * @param sizeInBytes       The maximum size of the bitmap in bytes. Must be greater than 0.
     * @return The downloaded bitmap or null if it couldn't be downloaded or doesn't exist.
     *
     * <p style="color:#4d2e00;background:#ffcc99;font-weight: bold" >
     *      Note: This method must be called on background thread.
     *</p>
     */
    public static @Nullable Bitmap getNotificationBitmapWithTimeoutAndSize(
            final Context context, final Bundle bundle, String bitmapSrcUrl,
            boolean fallbackToAppIcon, long timeoutInMillis, int sizeInBytes) {

        if (checkNotificationBitmapRequestInvalid(context, bundle, timeoutInMillis)) return null;

        if (sizeInBytes < 1) {
            Logger.v("Given sizeInBytes is less than 1 bytes. Not downloading bitmap!");
            return null;
        }

        CleverTapAPI cleverTapAPI = fromBundle(context, bundle);
        if (cleverTapAPI == null) {
            Logger.v("cleverTapAPI is null. Not downloading bitmap!");
            return null;
        }

        return Utils.getNotificationBitmapWithTimeoutAndSize(bitmapSrcUrl, fallbackToAppIcon, context, cleverTapAPI.getConfig(),
                timeoutInMillis, sizeInBytes).getBitmap();
    }

    private static boolean checkNotificationBitmapRequestInvalid(Context context, Bundle bundle, long timeoutInMillis) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Logger.v("Notification Bitmap Download is not allowed on main thread");
            return true;
        }
        if (context == null) {
            Logger.v("Given Context is null. Not downloading bitmap!");
            return true;
        }
        if (bundle == null) {
            Logger.v("Given Bundle is null. Not downloading bitmap!");
            return true;
        }
        if (timeoutInMillis < 1) {
            Logger.v("Given timeoutInMillis is less than 1 millis. Not downloading bitmap!");
            return true;
        }
        if (timeoutInMillis > 20000) {
            Logger.v("Given timeoutInMillis exceeds 20 secs limit. Not downloading bitmap!");
            return true;
        }
        return false;
    }

    /**
     * Retrieves a notification bitmap with a specified timeout.
     *
     * @param context           The context of the application. Must be non null.
     * @param bundle            The {@link Bundle} object received by the push receiver. Must be non null.
     * @param bitmapSrcUrl      The URL of the bitmap to download.
     * @param fallbackToAppIcon Specifies whether to fallback to the app icon if the bitmap is not available.
     * @param timeoutInMillis   The timeout duration for the bitmap download in milliseconds.  Must be in range of 1 - 20000.
     * @return The downloaded bitmap or null if it couldn't be downloaded or doesn't exist.
     *
     * <p style="color:#4d2e00;background:#ffcc99;font-weight: bold" >
     * Note: This method must be called on background thread.
     * </p>
     */
    public static @Nullable Bitmap getNotificationBitmapWithTimeout(
            final Context context, final Bundle bundle, String bitmapSrcUrl,
            boolean fallbackToAppIcon, long timeoutInMillis) {

        if (checkNotificationBitmapRequestInvalid(context, bundle, timeoutInMillis)) return null;

        CleverTapAPI cleverTapAPI = fromBundle(context, bundle);
        if (cleverTapAPI == null) {
            Logger.v("cleverTapAPI is null. Not downloading bitmap!");
            return null;
        }

        return Utils.getNotificationBitmapWithTimeout(bitmapSrcUrl, fallbackToAppIcon, context, cleverTapAPI.getConfig(),
                timeoutInMillis).getBitmap();
    }

    /**
     * Check if your app is in development mode. <br>
     * the following function: {@link CleverTapAPI#syncVariables()} will only work if the app is in
     * development mode and profile is set as a test profile in CT Dashboard.
     *
     * @return boolean True if development mode, false otherwise.
     */
    boolean isDevelopmentMode() {
        return CTVariables.isDevelopmentMode(context);
    }

    /**
     * Defines a new variable. If the default vale is null it won't resolve the type properly. In
     * that case it is better to use the @Variable annotation instead of this method.
     *
     * @param name Name of the variable.
     * @param defaultValue Default value of variable, used when resolving the underlying value type.
     * @param <T> Type of value.
     * @return Returns the Var instance.
     */
    public <T> Var<T> defineVariable(String name, T defaultValue) {
        return Var.define(name, defaultValue,coreState.getCTVariables());
    }

    /**
     * Parses the @Variable annotated fields from a given instance or multiple instances.
     *
     * @param instances Instance or instances to parse.
     */
    public void parseVariables(Object... instances) {
        coreState.getParser().parseVariables(instances);
    }

    /**
     * Parses the @Variable annotated static fields from a given class or multiple classes.
     *
     * @param classes Class object or objects to parse.
     */
    public void parseVariablesForClasses(Class<?>... classes) {
        coreState.getParser().parseVariablesForClasses(classes);
    }

    /**
     * Get a copy of the current value of a variable or a group.
     *
     * @param name The name of the variable or the group.
     * @return The value of the variable or the group.
     */
    public Object getVariableValue(String name) {
        if (name == null) {
            return null;
        }
        return coreState.getVarCache().getMergedValue(name);
    }

    /**
     * Get an instance of a variable or a group.
     *
     * @param name The name of the variable or the group.
     * @return The instance of the variable or the group, or null if not created yet.
     */
    public <T> Var<T> getVariable(String name) {
        if (name == null) {
            return null;
        }
        return coreState.getVarCache().getVariable(name);
    }

    /**
     * Fetches variable values from server.
     */
    public void fetchVariables() {
        fetchVariables(null);
    }

    /**
     * Fetches variable values from server.
     * Note that SDK keeps only one registered callback, if you call that method again it would
     * override the callback.
     *
     * @param callback Callback instance to be invoked when fetching is done.
     */
    public void fetchVariables(FetchVariablesCallback callback) {
        if (coreState.getConfig().isAnalyticsOnly()) {
            return;
        }
        Logger.v("variables", "Fetching  variables");
        if (callback != null) {
            coreState.getCallbackManager().setFetchVariablesCallback(callback);
        }

        JSONObject event = new JSONObject();
        JSONObject notif = new JSONObject();
        try {
            notif.put("t", Constants.FETCH_TYPE_VARIABLES);
            event.put("evtName", Constants.WZRK_FETCH);
            event.put("evtData", notif);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        coreState.getAnalyticsManager().sendFetchEvent(event);
    }

    /**
     * Uploads variables to server.
     */
    public void syncVariables() {
        if (isDevelopmentMode()) {
            Logger.v("variables", "syncVariables: waiting for id to be available");
            getCleverTapID(x -> {
                JSONObject js = coreState.getVarCache().getDefineVarsData();
                Logger.v("variables", "syncVariables: sending following vars to server:" + js);
                coreState.getAnalyticsManager().pushDefineVarsEvent(js);
            });
        } else {
            Logger.v("variables", "Your app is NOT in development mode, variables data will not be sent to server");
        }
    }

    /**
     * Adds a callback to be invoked when variables are initialised with server values.
     * Will be called each time new values are fetched.
     *
     * @param callback Callback to register.
     */
    public void addVariablesChangedCallback(@NonNull VariablesChangedCallback callback) {
        coreState.getCTVariables().addVariablesChangedCallback(callback);
    }

    /**
     * Adds a callback to be invoked when variables are initialised with server values. Will be
     * called only once and then removed.
     *
     * @param callback Callback to register.
     */
    public void addOneTimeVariablesChangedCallback(@NonNull VariablesChangedCallback callback) {
        coreState.getCTVariables().addOneTimeVariablesChangedCallback(callback);
    }

    /**
     * Removes previously registered callback.
     *
     * @param callback Callback to remove.
     */
    public void removeVariablesChangedCallback(@NonNull VariablesChangedCallback callback) {
        coreState.getCTVariables().removeVariablesChangedCallback(callback);
    }

    /**
     * Removes previously registered callback.
     *
     * @param callback Callback to remove.
     */
    public void removeOneTimeVariablesChangedCallback(@NonNull VariablesChangedCallback callback) {
        coreState.getCTVariables().removeOneTimeVariablesChangedHandler(callback);
    }

    /**
     *  Removes all previously registered callbacks.
     */
    public void removeAllVariablesChangedCallbacks() {
        coreState.getCTVariables().removeAllVariablesChangedCallbacks();
    }

    /**
     *  Removes all previously registered one time callbacks.
     */
    public void removeAllOneTimeVariablesChangedCallbacks() {
        coreState.getCTVariables().removeAllOneTimeVariablesChangedCallbacks();
    }

    /**
     * Use this method to set a custom locale for the current CleverTap instance
     *
     * @param locale - The custom locale to be set
     */
    @SuppressWarnings({"unused"})
    public void setLocale(String locale) {
        if(TextUtils.isEmpty(locale)) {
            Logger.i("Empty Locale provided for setLocale, not setting it");
            return;
        }
        coreState.getDeviceInfo().setCustomLocale(locale);
    }

    /**
     * Returns the custom locale set for the current CleverTap instance
     *
     * @return The customLocale string value
     */
    @SuppressWarnings({"unused"})
    public String getLocale() {
        return coreState.getDeviceInfo().getCustomLocale();
    }
}
