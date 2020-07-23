package com.clevertap.android.sdk;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.viewpager.widget.ViewPager;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

class CTCarouselImageViewHolder extends CTInboxBaseMessageViewHolder {

    private CTCarouselViewPager imageViewPager;
    private LinearLayout sliderDots;
    private TextView carouselTimestamp;
    private ImageView carouselReadDot;
    private RelativeLayout clickLayout;

    CTCarouselImageViewHolder(@NonNull View itemView) {
        super(itemView);
        imageViewPager = itemView.findViewById(R.id.image_carousel_viewpager);
        sliderDots = itemView.findViewById(R.id.sliderDots);
        carouselTimestamp = itemView.findViewById(R.id.carousel_timestamp);
        carouselReadDot = itemView.findViewById(R.id.carousel_read_circle);
        clickLayout = itemView.findViewById(R.id.body_linear_layout);
    }

    @Override
    void configureWithMessage(final CTInboxMessage inboxMessage, final CTInboxListViewFragment parent, final int position) {
        super.configureWithMessage(inboxMessage, parent, position);
        final CTInboxListViewFragment parentWeak = getParent();
        // noinspection ConstantConditions
        final Context appContext = parent.getActivity().getApplicationContext();
        CTInboxMessageContent content = inboxMessage.getInboxMessageContents().get(0);
        this.carouselTimestamp.setVisibility(View.VISIBLE);
        if(inboxMessage.isRead()){
            this.carouselReadDot.setVisibility(View.GONE);
        }else{
            this.carouselReadDot.setVisibility(View.VISIBLE);
        }
        String carouselImageDisplayTimestamp  = calculateDisplayTimestamp(inboxMessage.getDate());
        this.carouselTimestamp.setText(carouselImageDisplayTimestamp);
        this.carouselTimestamp.setTextColor(Color.parseColor(content.getTitleColor()));

        this.clickLayout.setBackgroundColor(Color.parseColor(inboxMessage.getBgColor()));

        //Loads the viewpager
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) this.imageViewPager.getLayoutParams();
        CTCarouselViewPagerAdapter carouselViewPagerAdapter = new CTCarouselViewPagerAdapter(appContext, parent, inboxMessage,layoutParams,position);
        this.imageViewPager.setAdapter(carouselViewPagerAdapter);
        //Adds the dots for the carousel
        int dotsCount = inboxMessage.getInboxMessageContents().size();
        if(this.sliderDots.getChildCount()>0){
            this.sliderDots.removeAllViews();
        }
        ImageView[] dots = new ImageView[dotsCount];
        for(int k=0;k<dotsCount;k++){
            dots[k] = new ImageView(parent.getActivity());
            dots[k].setVisibility(View.VISIBLE);
            dots[k].setImageDrawable(appContext.getResources().getDrawable(R.drawable.ct_unselected_dot));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(8, 6, 4, 6);
            params.gravity = Gravity.CENTER;
            if(this.sliderDots.getChildCount() < dotsCount)
                this.sliderDots.addView(dots[k],params);
        }
        dots[0].setImageDrawable(parent.getActivity().getApplicationContext().getResources().getDrawable(R.drawable.ct_selected_dot));
        CTCarouselImageViewHolder.CarouselPageChangeListener carouselPageChangeListener = new CTCarouselImageViewHolder.CarouselPageChangeListener(parent.getActivity().getApplicationContext(), this, dots, inboxMessage);
        this.imageViewPager.addOnPageChangeListener(carouselPageChangeListener);

        this.clickLayout.setOnClickListener(new CTInboxButtonClickListener(position, inboxMessage,null, parentWeak, this.imageViewPager));

        Runnable carouselRunnable = new Runnable() {
            @Override
            public void run() {
                Activity activity = parent.getActivity();
                if (activity == null) return;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(carouselReadDot.getVisibility() == View.VISIBLE){
                            if (parentWeak != null) {
                                parentWeak.didShow(null, position);
                            }
                        }
                        carouselReadDot.setVisibility(View.GONE);
                    }
                });
            }
        };
        Handler carouselHandler = new Handler();
        carouselHandler.postDelayed(carouselRunnable,2000);
    }

    /**
     * Custom PageChangeListener for Carousel
     */
    class CarouselPageChangeListener implements ViewPager.OnPageChangeListener {
        private CTCarouselImageViewHolder viewHolder;
        private ImageView[] dots;
        private CTInboxMessage inboxMessage;
        private Context context;

        CarouselPageChangeListener(Context context, CTCarouselImageViewHolder viewHolder, ImageView[] dots, CTInboxMessage inboxMessage) {
            this.context = context;
            this.viewHolder = viewHolder;
            this.dots = dots;
            this.inboxMessage = inboxMessage;
            this.dots[0].setImageDrawable(context.getResources().getDrawable(R.drawable.ct_selected_dot));
        }

        @Override
        public void onPageScrolled(int i, float v, int i1) {

        }

        @Override
        public void onPageSelected(int position) {
            for (ImageView dot : this.dots) {
                dot.setImageDrawable(context.getResources().getDrawable(R.drawable.ct_unselected_dot));
            }
            dots[position].setImageDrawable(context.getResources().getDrawable(R.drawable.ct_selected_dot));
        }

        @Override
        public void onPageScrollStateChanged(int i) {
        }
    }
}
