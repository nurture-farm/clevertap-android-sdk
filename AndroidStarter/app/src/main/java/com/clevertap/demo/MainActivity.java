package com.clevertap.demo;

import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.clevertap.android.sdk.CTInboxListener;
import com.clevertap.android.sdk.CTInboxStyleConfig;
import com.clevertap.android.sdk.CleverTapAPI;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements CTInboxListener {

    private Button event, chargedEvent, eventWithProps, profileEvent, inbox,web;
    private CleverTapAPI cleverTapDefaultInstance, cleverTapInstanceTwo;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        event = findViewById(R.id.event);
        chargedEvent = findViewById(R.id.charged_event);
        eventWithProps = findViewById(R.id.event_with_props);
        profileEvent = findViewById(R.id.profile_event);
        inbox = findViewById(R.id.inbox);
        web = findViewById(R.id.web);

        //Set Debug level for CleverTap
        CleverTapAPI.setDebugLevel(3);

        //Create CleverTap's default instance
        cleverTapDefaultInstance = CleverTapAPI.getDefaultInstance(this);
        if (cleverTapDefaultInstance != null) {
            cleverTapDefaultInstance.enableDeviceNetworkInfoReporting(false);
            //Set the Notification Inbox Listener
            cleverTapDefaultInstance.setCTNotificationInboxListener(this);
            //Initialize the inbox and wait for callbacks on overridden methods
            cleverTapDefaultInstance.initializeInbox();
        }

        //With CleverTap Android SDK v3.2.0 you can create additional instances to send data to multiple CleverTap accounts
        //Create config object for an additional instance
        //While using this app, replace the below Account Id and token with your Account Id and token
        //CleverTapInstanceConfig config =  CleverTapInstanceConfig.createInstance(this,"YOUR_ACCOUNT_ID","YOUR_ACCOUNT_TOKEN");

        //Use the config object to create a custom instance
        //cleverTapInstanceTwo = CleverTapAPI.instanceWithConfig(this,config);

        //Record an event
        event.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cleverTapDefaultInstance.pushEvent("EventName");
                //OR
                //cleverTapInstanceTwo.pushEvent("EventName");
            }
        });

        //Record an event with properties
        eventWithProps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HashMap<String, Object> prodViewedAction = new HashMap<>();
                prodViewedAction.put("Product Name", "Casio Chronograph Watch");
                prodViewedAction.put("Category", "Mens Accessories");
                prodViewedAction.put("Price", 59.99);
                prodViewedAction.put("Date", new java.util.Date());

                cleverTapDefaultInstance.pushEvent("Product viewed", prodViewedAction);
                //OR
                //cleverTapInstanceTwo.pushEvent("Product viewed", prodViewedAction);

            }
        });

        //Record a Charged (Transactional) event
        chargedEvent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HashMap<String, Object> chargeDetails = new HashMap<>();
                chargeDetails.put("Amount", 300);
                chargeDetails.put("Payment Mode", "Credit card");
                chargeDetails.put("Charged ID", 24052013);

                HashMap<String, Object> item1 = new HashMap<>();
                item1.put("Product category", "books");
                item1.put("Book name", "The Millionaire next door");
                item1.put("Quantity", 1);

                HashMap<String, Object> item2 = new HashMap<>();
                item2.put("Product category", "books");
                item2.put("Book name", "Achieving inner zen");
                item2.put("Quantity", 1);

                HashMap<String, Object> item3 = new HashMap<>();
                item3.put("Product category", "books");
                item3.put("Book name", "Chuck it, let's do it");
                item3.put("Quantity", 5);

                ArrayList<HashMap<String, Object>> items = new ArrayList<>();
                items.add(item1);
                items.add(item2);
                items.add(item3);

                cleverTapDefaultInstance.pushChargedEvent(chargeDetails, items);
                //OR
                //cleverTapInstanceTwo.pushChargedEvent(chargeDetails, items);
            }
        });

        //Push a profile to CleverTap
        profileEvent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Record a profile
                HashMap<String, Object> profileUpdate = new HashMap<>();
                profileUpdate.put("Name", "User Name");    // String
                profileUpdate.put("Email", "User@gmail.com"); // Email address of the user
                profileUpdate.put("Phone", "+14155551234");   // Phone (with the country code, starting with +)
                profileUpdate.put("Gender", "M");             // Can be either M or F
                profileUpdate.put("Employed", "Y");           // Can be either Y or N
                profileUpdate.put("Education", "Graduate");   // Can be either Graduate, College or School
                profileUpdate.put("Married", "Y");            // Can be either Y or N
                profileUpdate.put("DOB", new Date());         // Date of Birth. Set the Date object to the appropriate value first
                profileUpdate.put("Age", 28);                 // Not required if DOB is set
                profileUpdate.put("MSG-email", false);        // Disable email notifications
                profileUpdate.put("MSG-push", true);          // Enable push notifications
                profileUpdate.put("MSG-sms", false);          // Disable SMS notifications
                cleverTapDefaultInstance.pushProfile(profileUpdate);
                //OR
                //cleverTapInstanceTwo.pushProfile(profileUpdate);
            }
        });

        web.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this,WebViewActivity.class));
            }
        });
    }

    @Override
    public void inboxDidInitialize() {
        inbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> tabs = new ArrayList<>();
                tabs.add("Promotions");
                tabs.add("Offers");
                tabs.add("Others");//Anything after the first 2 will be ignored
                CTInboxStyleConfig styleConfig = new CTInboxStyleConfig();
                styleConfig.setTabs(tabs);//Do not use this if you don't want to use tabs
                styleConfig.setTabBackgroundColor("#FF0000");
                styleConfig.setSelectedTabIndicatorColor("#0000FF");
                styleConfig.setSelectedTabColor("#000000");
                styleConfig.setUnselectedTabColor("#FFFFFF");
                styleConfig.setBackButtonColor("#FF0000");
                styleConfig.setNavBarTitleColor("#FF0000");
                styleConfig.setNavBarTitle("MY INBOX");
                styleConfig.setNavBarColor("#FFFFFF");
                styleConfig.setInboxBackgroundColor("#00FF00");
                cleverTapDefaultInstance.showAppInbox(styleConfig); //Opens activity With Tabs
                //cleverTapDefaultInstance.showAppInbox();//Opens Activity with default style configs
            }
        });
    }

    @Override
    public void inboxMessagesDidUpdate() {
        inbox.setText("Inbox - Unread - "+ cleverTapDefaultInstance.getInboxMessageUnreadCount() + " Total - " + cleverTapDefaultInstance.getInboxMessageCount());
    }
}
