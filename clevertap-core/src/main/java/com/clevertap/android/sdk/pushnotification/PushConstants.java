package com.clevertap.android.sdk.pushnotification;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

public interface PushConstants {

    @StringDef({FCM_DELIVERY_TYPE, XIAOMI_DELIVERY_TYPE, HMS_DELIVERY_TYPE, BAIDU_DELIVERY_TYPE, ADM_DELIVERY_TYPE})
    @Retention(RetentionPolicy.SOURCE)
    @interface DeliveryType {

    }

    @StringDef({CT_FIREBASE_PROVIDER_CLASS, CT_XIAOMI_PROVIDER_CLASS, CT_BAIDU_PROVIDER_CLASS,
            CT_HUAWEI_PROVIDER_CLASS, CT_ADM_PROVIDER_CLASS})
    @Retention(RetentionPolicy.SOURCE)
    @interface CTPushProviderClass {

    }

    @StringDef({FIREBASE_SDK_CLASS, XIAOMI_SDK_CLASS, BAIDU_SDK_CLASS, HUAWEI_SDK_CLASS, ADM_SDK_CLASS})
    @Retention(RetentionPolicy.SOURCE)
    @interface PushMessagingClass {

    }

    @StringDef({FCM_PROPERTY_REG_ID, HPS_PROPERTY_REG_ID, XPS_PROPERTY_REG_ID, BPS_PROPERTY_REG_ID,
            ADM_PROPERTY_REG_ID})
    @Retention(RetentionPolicy.SOURCE)
    @interface RegKeyType {

    }

    @IntDef({ANDROID_PLATFORM, AMAZON_PLATFORM})
    @Retention(RetentionPolicy.SOURCE)
    @interface Platform {

    }

    enum PushType {
        FCM(FCM_DELIVERY_TYPE, FCM_PROPERTY_REG_ID, CT_FIREBASE_PROVIDER_CLASS, FIREBASE_SDK_CLASS),
        XPS(XIAOMI_DELIVERY_TYPE, XPS_PROPERTY_REG_ID, CT_XIAOMI_PROVIDER_CLASS, XIAOMI_SDK_CLASS),
        HPS(HMS_DELIVERY_TYPE, HPS_PROPERTY_REG_ID, CT_HUAWEI_PROVIDER_CLASS, HUAWEI_SDK_CLASS),
        BPS(BAIDU_DELIVERY_TYPE, BPS_PROPERTY_REG_ID, CT_BAIDU_PROVIDER_CLASS, BAIDU_SDK_CLASS),
        ADM(ADM_DELIVERY_TYPE, ADM_PROPERTY_REG_ID, CT_ADM_PROVIDER_CLASS, ADM_SDK_CLASS);

        private static final ArrayList<String> ALL;

        private final String ctProviderClassName;

        private final String messagingSDKClassName;

        private final String tokenPrefKey;

        private final String type;

        public static ArrayList<String> getAll() {
            return ALL;
        }

        public static PushType[] getPushTypes(ArrayList<String> types) {
            PushType[] pushTypes = new PushType[0];
            if (types != null && !types.isEmpty()) {
                pushTypes = new PushType[types.size()];
                for (int i = 0; i < types.size(); i++) {
                    pushTypes[i] = PushType.valueOf(types.get(i));
                }
            }
            return pushTypes;
        }

        PushType(@DeliveryType String type, @RegKeyType String prefKey, @CTPushProviderClass String className,
                @PushMessagingClass String messagingSDKClassName) {
            this.type = type;
            this.tokenPrefKey = prefKey;
            this.ctProviderClassName = className;
            this.messagingSDKClassName = messagingSDKClassName;
        }

        public String getCtProviderClassName() {
            return ctProviderClassName;
        }

        public String getMessagingSDKClassName() {
            return messagingSDKClassName;
        }

        public @RegKeyType
        String getTokenPrefKey() {
            return tokenPrefKey;
        }

        public String getType() {
            return type;
        }

        @NonNull
        @Override
        public @DeliveryType
        String toString() {
            return " [PushType:" + name() + "] ";
        }

        static {
            ALL = new ArrayList<>();
            for (PushType pushType : PushType.values()) {
                ALL.add(pushType.name());
            }
        }
    }

    String LOG_TAG = "PushProvider";

    String FCM_LOG_TAG = "FirebaseMessaging : ";

    @NonNull
    String FCM_DELIVERY_TYPE = "fcm";
    @NonNull
    String BAIDU_DELIVERY_TYPE = "bps";
    @NonNull
    String HMS_DELIVERY_TYPE = "hps";
    @NonNull
    String XIAOMI_DELIVERY_TYPE = "xps";
    @NonNull
    String ADM_DELIVERY_TYPE = "adm";
    String CT_FIREBASE_PROVIDER_CLASS = "com.clevertap.android.sdk.pushnotification.fcm.FcmPushProvider";
    String CT_XIAOMI_PROVIDER_CLASS = "com.clevertap.android.xps.XiaomiPushProvider";
    String CT_BAIDU_PROVIDER_CLASS = "com.clevertap.android.bps.BaiduPushProvider";
    String CT_HUAWEI_PROVIDER_CLASS = "com.clevertap.android.hms.HmsPushProvider";
    String CT_ADM_PROVIDER_CLASS = "com.clevertap.android.adm.AmazonPushProvider";
    String FIREBASE_SDK_CLASS = "com.google.firebase.messaging.FirebaseMessagingService";
    String XIAOMI_SDK_CLASS = "com.xiaomi.mipush.sdk.MiPushClient";
    String BAIDU_SDK_CLASS = "com.baidu.android.pushservice.PushMessageReceiver";
    String HUAWEI_SDK_CLASS = "com.huawei.hms.push.HmsMessageService";
    String ADM_SDK_CLASS = "com.amazon.device.messaging.ADM";
    String FCM_PROPERTY_REG_ID = "fcm_token";
    String XPS_PROPERTY_REG_ID = "xps_token";
    String BPS_PROPERTY_REG_ID = "bps_token";
    String HPS_PROPERTY_REG_ID = "hps_token";
    String ADM_PROPERTY_REG_ID = "adm_token";
    /**
     * Android platform type. Only GCM transport will be allowed.
     */
    int ANDROID_PLATFORM = 1;
    /**
     * Amazon platform type. Only ADM transport will be allowed.
     */
    int AMAZON_PLATFORM = 2;
}