package com.clevertap.android.sdk.pushnotification.fcm;

import static com.clevertap.android.sdk.pushnotification.PushConstants.ANDROID_PLATFORM;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import com.clevertap.android.sdk.pushnotification.CTPushProvider;
import com.clevertap.android.sdk.pushnotification.CTPushProviderListener;
import com.clevertap.android.sdk.pushnotification.PushConstants;

/**
 * Clevertap's Firebase Plugin Ref: {@link CTPushProvider}
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@SuppressLint(value = "unused")
public class FcmPushProvider implements CTPushProvider {

    private IFcmSdkHandler mHandler;

    @SuppressLint(value = "unused")
    public FcmPushProvider(CTPushProviderListener ctPushListener) {
        mHandler = new FcmSdkHandlerImpl(ctPushListener);
    }

    @NonNull
    @Override
    public PushConstants.PushType getPushType() {
        return mHandler.getPushType();
    }

    @Override
    public int getPlatform() {
        return ANDROID_PLATFORM;
    }

    /**
     * App supports FCM
     *
     * @return boolean true if FCM services are available
     */
    @Override
    public boolean isAvailable() {
        return mHandler.isAvailable();
    }

    /**
     * Device supports FCM
     *
     * @return - true if FCM is supported in the platform
     */
    @Override
    public boolean isSupported() {
        return mHandler.isSupported();
    }

    @Override
    public void requestToken() {
        mHandler.requestToken();
    }

    @Override
    public int minSDKSupportVersionCode() {
        return 0;// supporting FCM from base version
    }

    void setHandler(final IFcmSdkHandler handler) {
        mHandler = handler;
    }
}