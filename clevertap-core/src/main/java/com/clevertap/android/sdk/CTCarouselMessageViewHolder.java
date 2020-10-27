package com.clevertap.android.sdk;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.viewpager.widget.ViewPager;

/**
 * Viewholder class for Carousels
 */
class CTCarouselMessageViewHolder extends CTInboxBaseMessageViewHolder {

    /**
     * Custom PageChangeListener for Carousel
     */
    class CarouselPageChangeListener implements ViewPager.OnPageChangeListener {

        private Context context;

        private ImageView[] dots;

        private CTInboxMessage inboxMessage;

        private CTCarouselMessageViewHolder viewHolder;

        CarouselPageChangeListener(Context context, CTCarouselMessageViewHolder viewHolder, ImageView[] dots,
                CTInboxMessage inboxMessage) {
            this.context = context;
            this.viewHolder = viewHolder;
            this.dots = dots;
            this.inboxMessage = inboxMessage;
            this.dots[0].setImageDrawable(
                    ResourcesCompat.getDrawable(context.getResources(), R.drawable.ct_selected_dot, null));
        }

        @Override
        public void onPageScrollStateChanged(int i) {
        }

        @Override
        public void onPageScrolled(int i, float v, int i1) {

        }

        @Override
        public void onPageSelected(int position) {
            for (ImageView dot : this.dots) {
                dot.setImageDrawable(
                        ResourcesCompat.getDrawable(context.getResources(), R.drawable.ct_unselected_dot, null));
            }
            dots[position].setImageDrawable(
                    ResourcesCompat.getDrawable(context.getResources(), R.drawable.ct_selected_dot, null));
            viewHolder.title.setText(inboxMessage.getInboxMessageContents().get(position).getTitle());
            viewHolder.title.setTextColor(
                    Color.parseColor(inboxMessage.getInboxMessageContents().get(position).getTitleColor()));
            viewHolder.message.setText(inboxMessage.getInboxMessageContents().get(position).getMessage());
            viewHolder.message.setTextColor(
                    Color.parseColor(inboxMessage.getInboxMessageContents().get(position).getMessageColor()));
        }
    }

    private RelativeLayout clickLayout;

    private CTCarouselViewPager imageViewPager;

    private ImageView readDot, carouselReadDot;

    private LinearLayout sliderDots;

    private TextView title, message, timestamp, carouselTimestamp;

    CTCarouselMessageViewHolder(@NonNull View itemView) {
        super(itemView);
        imageViewPager = itemView.findViewById(R.id.image_carousel_viewpager);
        sliderDots = itemView.findViewById(R.id.sliderDots);
        title = itemView.findViewById(R.id.messageTitle);
        message = itemView.findViewById(R.id.messageText);
        timestamp = itemView.findViewById(R.id.timestamp);
        readDot = itemView.findViewById(R.id.read_circle);
        clickLayout = itemView.findViewById(R.id.body_linear_layout);
    }

    @Override
    void configureWithMessage(final CTInboxMessage inboxMessage, final CTInboxListViewFragment parent,
            final int position) {
        super.configureWithMessage(inboxMessage, parent, position);
        final CTInboxListViewFragment parentWeak = getParent();
        // noinspection ConstantConditions
        final Context appContext = parent.getActivity().getApplicationContext();
        CTInboxMessageContent content = inboxMessage.getInboxMessageContents().get(0);
        this.title.setVisibility(View.VISIBLE);
        this.message.setVisibility(View.VISIBLE);
        this.title.setText(content.getTitle());
        this.title.setTextColor(Color.parseColor(content.getTitleColor()));
        this.message.setText(content.getMessage());
        this.message.setTextColor(Color.parseColor(content.getMessageColor()));
        if (inboxMessage.isRead()) {
            this.readDot.setVisibility(View.GONE);
        } else {
            this.readDot.setVisibility(View.VISIBLE);
        }
        this.timestamp.setVisibility(View.VISIBLE);
        String carouselDisplayTimestamp = calculateDisplayTimestamp(inboxMessage.getDate());
        this.timestamp.setText(carouselDisplayTimestamp);
        this.timestamp.setTextColor(Color.parseColor(content.getTitleColor()));
        this.clickLayout.setBackgroundColor(Color.parseColor(inboxMessage.getBgColor()));

        //Loads the viewpager
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) this.imageViewPager.getLayoutParams();
        CTCarouselViewPagerAdapter carouselViewPagerAdapter = new CTCarouselViewPagerAdapter(appContext, parent,
                inboxMessage, layoutParams, position);
        this.imageViewPager.setAdapter(carouselViewPagerAdapter);
        //Adds the dots for the carousel
        int dotsCount = inboxMessage.getInboxMessageContents().size();
        if (this.sliderDots.getChildCount() > 0) {
            this.sliderDots.removeAllViews();
        }
        ImageView[] dots = new ImageView[dotsCount];
        setDots(dots, dotsCount, appContext, this.sliderDots);
        dots[0].setImageDrawable(
                ResourcesCompat.getDrawable(appContext.getResources(), R.drawable.ct_selected_dot, null));
        CTCarouselMessageViewHolder.CarouselPageChangeListener carouselPageChangeListener
                = new CTCarouselMessageViewHolder.CarouselPageChangeListener(
                parent.getActivity().getApplicationContext(), this, dots, inboxMessage);
        this.imageViewPager.addOnPageChangeListener(carouselPageChangeListener);

        this.clickLayout.setOnClickListener(
                new CTInboxButtonClickListener(position, inboxMessage, null, parentWeak, this.imageViewPager));

        Runnable carouselRunnable = new Runnable() {
            @Override
            public void run() {
                Activity activity = parent.getActivity();
                if (activity == null) {
                    return;
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (inboxMessage.getType() == CTInboxMessageType.CarouselImageMessage) {
                            if (carouselReadDot.getVisibility() == View.VISIBLE) {
                                if (parentWeak != null) {
                                    parentWeak.didShow(null, position);
                                }
                            }
                            carouselReadDot.setVisibility(View.GONE);
                        } else {
                            if (readDot.getVisibility() == View.VISIBLE) {
                                if (parentWeak != null) {
                                    parentWeak.didShow(null, position);
                                }
                            }
                            readDot.setVisibility(View.GONE);
                        }
                    }
                });
            }
        };
        Handler carouselHandler = new Handler();
        carouselHandler.postDelayed(carouselRunnable, 2000);
    }
}
