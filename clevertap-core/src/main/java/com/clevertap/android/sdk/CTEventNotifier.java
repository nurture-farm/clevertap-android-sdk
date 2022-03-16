package com.clevertap.android.sdk;

public abstract interface CTEventNotifier {
    public abstract void onEventComplete();//by default abstract
    public abstract void onEventCompleteWithError(Throwable e);
}