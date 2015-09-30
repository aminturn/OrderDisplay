package com.trubeacon.ordermonitorgui;

import android.animation.LayoutTransition;
import android.app.Fragment;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.tru.clover.api.order.LineItem;
import com.tru.clover.api.order.Modification;
import com.tru.clover.api.order.Order;

import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.joda.time.format.DateTimeFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;


public class DoneOrdersFragment extends Fragment {

        private List<Order> doneOrdersList = new ArrayList<>();
        private LinearLayout horizLinearLayout;
        private LayoutInflater mLayoutInflater;

        private String DISPLAY_KEY = "display preference";
        private String ORDER_TYPE_KEY = "order type preference";

        private static String FONT_SIZE_KEY = "font size preference";
        private static int refreshRateMs;

        private int previousListSize = 0;
        private float fontSize;
        private boolean twoRows;
        private boolean showOrderType;
        private boolean showDevice;
        private boolean showTimer;
        private ActionBar actionBar;
        private boolean isOrderUndoneReceiverRegistered = false;

        private Integer screenWidthDp;
        private OrderMonitorData orderMonitorData = OrderMonitorData.getOrderMonitorData();

        private Handler doneOrdersHandler = new Handler();
        private List<TextView> countdownTvList = new ArrayList<>();


        private Handler countdownHandler = new Handler();



        private Runnable updateCountdown = new Runnable() {
            @Override
            public void run() {
                long orderCreatedTime;

                for(TextView tv:countdownTvList){
                    orderCreatedTime = (long) tv.getTag();
                    DateTime orderCreated = new DateTime(orderCreatedTime);
                    int seconds = Seconds.secondsBetween(orderCreated, DateTime.now()).getSeconds();
                    int minutes = seconds/60;
                    int sec = seconds%60;
                    String secondsString = null;
                    if(sec<10){
                        secondsString = "0" + String.valueOf(sec);
                    }else{
                        secondsString = String.valueOf(sec);
                    }
                    tv.setText(String.valueOf(minutes)+":"+secondsString);
                }
                countdownHandler.postDelayed(updateCountdown,100);
            }
        };

        private Runnable doneOrdersRunnable = new Runnable(){
            @Override
            public void run() {
                orderMonitorData.refreshOrders();
            }
        };

        private BroadcastReceiver ordersBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
            doneOrdersList = orderMonitorData.getDoneOrdersList();
            updateOrdersView();
            doneOrdersHandler.postDelayed(doneOrdersRunnable, refreshRateMs);
            }
        };


        //TODO: order marked undone receiver

        private BroadcastReceiver orderUndoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                OrderMonitorBroadcaster.unregisterReceiver(this);
                isOrderUndoneReceiverRegistered=false;
                OrderMonitorBroadcaster.registerReceiver(ordersBroadcastReceiver, OrderMonitorData.BroadcastEvent.REFRESH_ORDERS);
                doneOrdersHandler.post(doneOrdersRunnable);
            }
        };


        @Override
        public void onResume() {
            super.onResume();
            doneOrdersHandler.postDelayed(doneOrdersRunnable, 0);
            countdownHandler.postDelayed(updateCountdown, 0);
            OrderMonitorBroadcaster.registerReceiver(ordersBroadcastReceiver, OrderMonitorData.BroadcastEvent.REFRESH_ORDERS);
        }

        @Override
        public void onPause() {
            super.onPause();
            OrderMonitorBroadcaster.unregisterReceiver(ordersBroadcastReceiver);
            doneOrdersHandler.removeCallbacks(doneOrdersRunnable);
            countdownHandler.removeCallbacks(updateCountdown);
        }

    @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

            View scrollView = inflater.inflate(R.layout.fragment_done_orders, container, false);

            mLayoutInflater = inflater;

            horizLinearLayout = (LinearLayout) scrollView.findViewById(R.id.horiz_lin_layout);

            //find screen width to set width of relative layout
            DisplayMetrics dm = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);

            screenWidthDp = dm.widthPixels;

            //get display preferences
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String ordersToDisplay = sharedPref.getString(DISPLAY_KEY, "5");

            if (ordersToDisplay.equals(getResources().getString(R.string.five))) {
                twoRows = false;
            } else {
                twoRows = true;
            }

            String fontSizeString = sharedPref.getString(FONT_SIZE_KEY,"30");

            String refreshRateString = sharedPref.getString(getString(R.string.refresh_rate_key), "5");
            int refreshRateSecs = Integer.parseInt(refreshRateString);
            refreshRateMs = refreshRateSecs*1000;

            fontSize = Integer.parseInt(fontSizeString);

            showOrderType = sharedPref.getBoolean(getString(R.string.display_order_type_pref),true);
            showDevice = sharedPref.getBoolean(getString(R.string.display_device_pref),true);
            showTimer = sharedPref.getBoolean(getString(R.string.order_timer_pref),true);

            actionBar = ((MainActivity) getActivity()).getSupportActionBar();
            actionBar.setDisplayHomeAsUpEnabled(false);

            doneOrdersList = orderMonitorData.getDoneOrdersList();

            updateOrdersView();

            return scrollView;
        }


        private void updateOrdersView() {

            actionBar.setTitle(String.valueOf(doneOrdersList.size()) + " ORDER(S) COMPLETED");

            SharedPreferences sp  = PreferenceManager.getDefaultSharedPreferences(getActivity());
            boolean playNotification = sp.getBoolean(getString(R.string.play_notification_pref),true);

            if(playNotification) {
                if (doneOrdersList.size() > previousListSize) {
                    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                    Ringtone r = RingtoneManager.getRingtone(getActivity(), notification);
                    r.play();
                }
            }

            previousListSize = doneOrdersList.size();

            countdownTvList.clear();

            if (twoRows) {
                updateTenOrdersView();
            } else {
                updateFiveOrdersView();
            }
        }

        private void updateTenOrdersView(){

            LinearLayout.LayoutParams linearLayoutParams = new LinearLayout.LayoutParams(
                    screenWidthDp/5,
                    ViewGroup.LayoutParams.MATCH_PARENT);

            horizLinearLayout.removeAllViews();

            int listsize = doneOrdersList.size();
            int pages;

            if(listsize%10==0){
                pages = listsize/10;
            }
            else{
                pages = listsize/10+1;
            }


            int frames = pages*5;
            int pagenumber =0;

            for(int f = 1;f<=frames;f++) {

                LinearLayout linearLayout = new LinearLayout(getActivity());
                linearLayout.setLayoutParams(linearLayoutParams);

                horizLinearLayout.addView(linearLayout);
                mLayoutInflater.inflate(R.layout.order_item_done_layout2, linearLayout);

                RelativeLayout relativeLayoutTop = (RelativeLayout) linearLayout.findViewById(R.id.order_item_top);
                RelativeLayout relativeLayoutBottom = (RelativeLayout) linearLayout.findViewById(R.id.order_item_bottom);

                int topId;
                if(f%5==0){
                    topId = pagenumber*10+5;
                }else {
                    topId = pagenumber * 10 + f % 5;
                }

                int bottomId = topId+5;

                if(topId<=listsize) {

                    relativeLayoutTop.setVisibility(View.VISIBLE);

                    Order topOrder = doneOrdersList.get(topId - 1);

                    String topLabel = "";

                    if(topOrder.getOrderType()!=null&&showOrderType) {
                        topLabel = topOrder.getOrderType().getLabel();
                    }

                    String origin = "";

                    if(topOrder.getDevice().getId()!=null&&showDevice){
                        origin = orderMonitorData.getDeviceNamefromId(topOrder.getDevice().getId());
                    }

                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());

                    boolean showTimeTicket = sp.getBoolean(getString(R.string.show_time_stamp_and_ticket_number), true);

                    int titleBgColor1 = getResources().getColor(R.color.background);
                    if(topOrder.getOrderType()!=null) {
                        String titleColor1 = sp.getString(topOrder.getOrderType().getLabel(), getString(R.string.backgroundcode));
                        titleBgColor1 = Color.parseColor(titleColor1);
                    }

                    int bodyBgColor1 = getResources().getColor(R.color.white);
                    if(topOrder.getDevice()!=null){
                        String bodyColor1 = sp.getString(topOrder.getDevice().getId(),getString(R.string.white));
                        bodyBgColor1 = Color.parseColor(bodyColor1);
                    }

                    String detailString = "";

                    List<LineItem> rawlineItemList= topOrder.getLineItems();
                    List<LineItem> lineItemList = checkForDuplicateLineItems(rawlineItemList);

                    if(lineItemList!=null) {
                        for (LineItem li : lineItemList) {
                            if(orderMonitorData.showLineItem(li.getName())) {
                                detailString = detailString + String.valueOf(Character.toChars(9654)) + li.getName() + "\r\n";
                                List<Modification> modList = li.getModifications();
                                if (modList != null) {

                                    Collections.sort(modList, new Comparator<Modification>() {
                                        @Override
                                        public int compare(Modification m1, Modification m2) {
                                            return m1.getName().compareTo(m2.getName());
                                        }
                                    });

                                    for (Modification mo : modList) {
                                        detailString = detailString + " -" + mo.getName() + "\r\n";
                                    }
                                }
                                if (li.getNote() != null) {
                                    detailString = detailString + " -" + li.getNote() + "\r\n";
                                }

                            }
                        }
                    }


                    TextView orderTitleText = (TextView) relativeLayoutTop.findViewById(R.id.order_id_text);
                    TextView orderDetailText = (TextView) relativeLayoutTop.findViewById(R.id.order_detail_text);
                    TextView countdownText = (TextView) relativeLayoutTop.findViewById(R.id.countdown_text);
                    countdownText.setTag(topOrder.getCreatedTime());

                    if(showTimer){
                        countdownText.setVisibility(View.VISIBLE);
                        countdownTvList.add(countdownText);
                    }else{
                        countdownText.setVisibility(View.GONE);
                    }



                    Button topDoneButton = (Button) relativeLayoutTop.findViewById(R.id.done_button);
                    topDoneButton.setTextSize(fontSize);

                    orderTitleText.setTextSize(fontSize);
                    orderTitleText.setBackgroundColor(titleBgColor1);
                    orderDetailText.setTextSize(fontSize);
                    orderDetailText.setBackgroundColor(bodyBgColor1);
                    topDoneButton.setBackgroundColor(titleBgColor1);
                    countdownText.setTextSize(fontSize);

                    orderDetailText.setMovementMethod(new ScrollingMovementMethod());

                    DateTime orderCreated = new DateTime(topOrder.getCreatedTime());

                    String timeCreatedString = "";
                    String orderTitle = "";

                    if(topOrder.getTitle()!=null){
                        orderTitle = topOrder.getTitle();
                    }

                    if(showTimeTicket){
                        timeCreatedString = "#" + orderTitle + " " + DateTimeFormat.forPattern("hh:mm a").print(orderCreated);
                     }

                    if(showOrderType) {
                        if(showTimeTicket){
                            timeCreatedString = timeCreatedString + "\r\n";
                        }
                        timeCreatedString = timeCreatedString + topLabel;
                    }

                    if(showDevice){
                        if(showOrderType||showTimeTicket){
                            timeCreatedString = timeCreatedString + "\r\n";
                        }
                        timeCreatedString = timeCreatedString + origin;
                    }

                    if(!timeCreatedString.equals("")) {
                        orderTitleText.setText(timeCreatedString);
                    }else{
                        orderTitleText.setVisibility(View.GONE);
                    }

                    orderDetailText.setText(detailString);

                    //add a index as a tag to reference the order in the click listener
                    topDoneButton.setTag(topId);

                    topDoneButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {

                            RelativeLayout parentView = (RelativeLayout) v.getParent();

                            LayoutTransition lt = new LayoutTransition();
                            lt.enableTransitionType(LayoutTransition.CHANGING);
                            lt.addTransitionListener(new LayoutTransition.TransitionListener() {

                                @Override
                                public void startTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {

                                }

                                @Override
                                public void endTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
                                }
                            });

                            Order doneOrder = doneOrdersList.get((Integer) v.getTag() - 1);

                            //TODO: wait for order to be marked undone

                            OrderMonitorBroadcaster.unregisterReceiver(ordersBroadcastReceiver);
                            doneOrdersHandler.removeCallbacks(doneOrdersRunnable);

                            if(!isOrderUndoneReceiverRegistered) {
                                OrderMonitorBroadcaster.registerReceiver(orderUndoneReceiver, OrderMonitorData.BroadcastEvent.ORDER_DONE);
                                isOrderUndoneReceiverRegistered=true;
                            }

                            orderMonitorData.markUnDone(doneOrder.getId(),doneOrder);

                            parentView.setLayoutTransition(lt);
                            v.setVisibility(View.INVISIBLE);

                        }
                    });

                    if (bottomId <=listsize) {
                        relativeLayoutBottom.setVisibility(View.VISIBLE);
                        //minus one index for zero-based list
                        Order bottomOrder = doneOrdersList.get(bottomId-1);

                        String bottomLabel = "";

                        if(bottomOrder.getOrderType()!=null&&showOrderType) {
                            bottomLabel = bottomOrder.getOrderType().getLabel();
                        }

                        String bottomOrigin = "";

                        if(bottomOrder.getDevice().getId()!=null){
                            bottomOrigin = orderMonitorData.getDeviceNamefromId(bottomOrder.getDevice().getId());
                        }

                        int titleBgColor2 = getResources().getColor(R.color.background);
                        if(bottomOrder.getOrderType()!=null) {
                            String titleColor2 = sp.getString(bottomOrder.getOrderType().getLabel(), getString(R.string.backgroundcode));
                            titleBgColor2 = Color.parseColor(titleColor2);
                        }

                        int bodyBgColor2 = getResources().getColor(R.color.white);
                        if(bottomOrder.getDevice()!=null){
                            String bodyColor2 = sp.getString(bottomOrder.getDevice().getId(),getString(R.string.white));
                            bodyBgColor2 = Color.parseColor(bodyColor2);
                        }

                        String detailString2 = "";

                        TextView orderTitleText2 = (TextView) relativeLayoutBottom.findViewById(R.id.order_id_text2);
                        TextView orderDetailText2 = (TextView) relativeLayoutBottom.findViewById(R.id.order_detail_text2);
                        TextView countdownText2 = (TextView) relativeLayoutBottom.findViewById(R.id.countdown_text2);
                        countdownText2.setTag(bottomOrder.getCreatedTime());

                        if(showTimer) {
                            countdownTvList.add(countdownText2);
                            countdownText2.setVisibility(View.VISIBLE);
                        }else{
                            countdownText2.setVisibility(View.GONE);
                        }

                        Button bottomDoneButton = (Button) relativeLayoutBottom.findViewById(R.id.done_button2);
                        bottomDoneButton.setTextSize(fontSize);

                        orderTitleText2.setTextSize(fontSize);
                        orderTitleText2.setBackgroundColor(titleBgColor2);
                        orderDetailText2.setTextSize(fontSize);
                        orderDetailText2.setBackgroundColor(bodyBgColor2);
                        bottomDoneButton.setBackgroundColor(titleBgColor2);
                        countdownText2.setTextSize(fontSize);

                        orderDetailText2.setMovementMethod(new ScrollingMovementMethod());

                        DateTime orderCreated2 = new DateTime(bottomOrder.getCreatedTime());

                        String timeCreatedString2 = "";

                        String orderTitle2 = "";

                        if(bottomOrder.getTitle()!=null){
                            orderTitle2 = bottomOrder.getTitle();
                        }

                        if(showTimeTicket){
                            timeCreatedString2 = "#" + orderTitle2 + " " + DateTimeFormat.forPattern("hh:mm a").print(orderCreated2);
                        }

                        if (showOrderType) {
                            if(showTimeTicket){
                                timeCreatedString2 = timeCreatedString2 + "\r\n";
                            }
                            timeCreatedString2 = timeCreatedString2 + bottomLabel;
                        }

                        if(showDevice){
                            if(showOrderType||showTimeTicket){
                                timeCreatedString2 = timeCreatedString2 + "\r\n";
                            }
                            timeCreatedString2 = timeCreatedString2 + bottomOrigin;
                        }

                        if(!timeCreatedString2.equals("")) {
                            orderTitleText2.setText(timeCreatedString2);
                        }else{
                            orderTitleText2.setVisibility(View.GONE);
                        }

                        List<LineItem> rawlineItemList2 = bottomOrder.getLineItems();
                        List<LineItem> lineItemList2 = checkForDuplicateLineItems(rawlineItemList2);

                        if(lineItemList2!=null) {
                            for (LineItem li2 : lineItemList2) {
                                if(orderMonitorData.showLineItem(li2.getName())) {
                                    detailString2 = detailString2 + String.valueOf(Character.toChars(9654)) + li2.getName() + "\r\n";

                                    List<Modification> modList2 = li2.getModifications();

                                    if (modList2 != null) {

                                        Collections.sort(modList2, new Comparator<Modification>() {
                                            @Override
                                            public int compare(Modification m1, Modification m2) {
                                                return m1.getName().compareTo(m2.getName());
                                            }
                                        });

                                        for (Modification mo2 : modList2) {
                                            detailString2 = detailString2 + " -" + mo2.getName() + "\r\n";
                                        }
                                    }
                                    if (li2.getNote() != null) {
                                        detailString2 = detailString2 + " -" + li2.getNote() + "\r\n";
                                    }
                                }
                            }
                        }

                        orderDetailText2.setText(detailString2);

                        bottomDoneButton.setTag(bottomId);

                        bottomDoneButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {

                                RelativeLayout parentView = (RelativeLayout) v.getParent();

                                LayoutTransition lt = new LayoutTransition();
                                lt.enableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
                                lt.addTransitionListener(new LayoutTransition.TransitionListener() {

                                    @Override
                                    public void startTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {

                                    }

                                    @Override
                                    public void endTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
                                    }
                                });

                                //remove the order from the list (subtract one to reference the zero-based list)

                                Order doneOrder = doneOrdersList.get((Integer) v.getTag() - 1);

                                OrderMonitorBroadcaster.unregisterReceiver(ordersBroadcastReceiver);
                                doneOrdersHandler.removeCallbacks(doneOrdersRunnable);

                                if(!isOrderUndoneReceiverRegistered) {
                                    OrderMonitorBroadcaster.registerReceiver(orderUndoneReceiver, OrderMonitorData.BroadcastEvent.ORDER_DONE);
                                    isOrderUndoneReceiverRegistered=true;
                                }

                                orderMonitorData.markUnDone(doneOrder.getId(), doneOrder);
                                parentView.setLayoutTransition(lt);
                                v.setVisibility(View.INVISIBLE);
                            }
                        });
                    }
                }

                if(f%5==0){pagenumber++;}
            }
        }

        private void updateFiveOrdersView(){

            RelativeLayout.LayoutParams relativeLayoutParams = new RelativeLayout.LayoutParams(
                    screenWidthDp/5,
                    ViewGroup.LayoutParams.MATCH_PARENT);

            horizLinearLayout.removeAllViews();

            for(int i =1;i<=doneOrdersList.size();i++){

                Order thisOrder = doneOrdersList.get(i - 1);

                String label = "";

                if(thisOrder.getOrderType()!=null&&showOrderType) {
                    label = thisOrder.getOrderType().getLabel();
                }

                String origin = "";

                if(thisOrder.getDevice().getId()!=null&&showDevice){
                    origin = orderMonitorData.getDeviceNamefromId(thisOrder.getDevice().getId());
                }

                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());

                int titleBgColor = getResources().getColor(R.color.background);
                if(thisOrder.getOrderType()!=null) {
                    String titleColor = sp.getString(thisOrder.getOrderType().getLabel(), getString(R.string.backgroundcode));
                    titleBgColor = Color.parseColor(titleColor);
                }

                int bodyBgColor = getResources().getColor(R.color.white);
                if(thisOrder.getDevice()!=null){
                    String bodyColor = sp.getString(thisOrder.getDevice().getId(),getString(R.string.white));
                    bodyBgColor = Color.parseColor(bodyColor);
                }

                RelativeLayout relativeLayout= new RelativeLayout(getActivity());
                relativeLayout.setLayoutParams(relativeLayoutParams);
                horizLinearLayout.addView(relativeLayout);
                mLayoutInflater.inflate(R.layout.order_item_done_layout1, relativeLayout);

                TextView orderTitleText = (TextView) relativeLayout.findViewById(R.id.order_id_text);
                TextView orderDetailText = (TextView) relativeLayout.findViewById(R.id.order_detail_text);
                TextView countdownText = (TextView) relativeLayout.findViewById(R.id.countdown_text);
                countdownText.setTag(thisOrder.getCreatedTime());

                if(showTimer) {
                    countdownTvList.add(countdownText);
                    countdownText.setVisibility(View.VISIBLE);
                }else{
                    countdownText.setVisibility(View.GONE);
                }

                Button doneButton = (Button) relativeLayout.findViewById(R.id.done_button);
                doneButton.setTextSize(fontSize);

                orderTitleText.setTextSize(fontSize);
                orderTitleText.setBackgroundColor(titleBgColor);
                orderDetailText.setTextSize(fontSize);
                orderDetailText.setBackgroundColor(bodyBgColor);
                doneButton.setBackgroundColor(titleBgColor);
                countdownText.setTextSize(fontSize);

                orderDetailText.setMovementMethod(new ScrollingMovementMethod());

                DateTime orderCreated = new DateTime(thisOrder.getCreatedTime());

                boolean showTimeTicket = sp.getBoolean(getString(R.string.show_time_stamp_and_ticket_number),true);

                String timeCreatedString = "";

                String orderTitle = "";

                if(thisOrder.getTitle()!=null){
                    orderTitle = thisOrder.getTitle();
                }

                if(showTimeTicket){
                    timeCreatedString = "#" + orderTitle + " " + DateTimeFormat.forPattern("hh:mm a").print(orderCreated);
                }


                if(showOrderType) {
                    if(showTimeTicket){
                        timeCreatedString = timeCreatedString + "\r\n";
                    }
                    timeCreatedString = timeCreatedString + label;
                }

                if(showDevice){
                    if(showOrderType||showTimeTicket){
                        timeCreatedString = timeCreatedString + "\r\n";
                    }
                    timeCreatedString = timeCreatedString + origin;
                }

                if(!timeCreatedString.equals("")) {
                    orderTitleText.setText(timeCreatedString);
                }else{
                    orderTitleText.setVisibility(View.GONE);
                }

                List<LineItem> rawlineItemList = thisOrder.getLineItems();
                List<LineItem> lineItemList = checkForDuplicateLineItems(rawlineItemList);

                String detailString = "";

                if(lineItemList!=null) {
                    for (LineItem li : lineItemList) {
                        if(orderMonitorData.showLineItem(li.getName())) {
                            detailString = detailString + String.valueOf(Character.toChars(9654)) + li.getName() + "\r\n";
                            List<Modification> modList = li.getModifications();
                            if (modList != null) {

                                Collections.sort(modList, new Comparator<Modification>() {
                                    @Override
                                    public int compare(Modification m1, Modification m2) {
                                        return m1.getName().compareTo(m2.getName());
                                    }
                                });

                                for (Modification mo : modList) {
                                    detailString = detailString + " -" + mo.getName() + "\r\n";
                                }
                            }
                            if (li.getNote() != null) {
                                detailString = detailString + " -" + li.getNote() + "\r\n";
                            }
                        }
                    }
                }

                orderDetailText.setText(detailString);

                doneButton.setTag(i);

                doneButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        RelativeLayout parentView = (RelativeLayout) v.getParent();

                        LayoutTransition lt = new LayoutTransition();
                        lt.enableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
                        lt.addTransitionListener(new LayoutTransition.TransitionListener() {

                            @Override
                            public void startTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {

                            }

                            @Override
                            public void endTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {

                            }
                        });

                        //remove the order from the list (subtract one to reference the zero-based list)
                        Order doneOrder = doneOrdersList.get((Integer) v.getTag() - 1);

                        //TODO:wait for order to be marked undone

                        OrderMonitorBroadcaster.unregisterReceiver(ordersBroadcastReceiver);
                        doneOrdersHandler.removeCallbacks(doneOrdersRunnable);

                        if(!isOrderUndoneReceiverRegistered) {
                            OrderMonitorBroadcaster.registerReceiver(orderUndoneReceiver, OrderMonitorData.BroadcastEvent.ORDER_DONE);
                            isOrderUndoneReceiverRegistered=true;
                        }

                        orderMonitorData.markUnDone(doneOrder.getId(),doneOrder);

                        parentView.setLayoutTransition(lt);
                        v.setVisibility(View.INVISIBLE);
                    }
                });

            }

        }

    private List<LineItem> checkForDuplicateLineItems(List<LineItem> rawLiList){

        List<Integer> hashList = new ArrayList<>();
        HashMap<Integer,LineItem> lineItemHashMap = new HashMap<>();
        List<LineItem> noDupeList = new ArrayList<>();

        for(LineItem li:rawLiList){
            int hash = hashCode(li);
            lineItemHashMap.put(hash,li);
            hashList.add(hash);
        }

        for(Integer hashFromMap:lineItemHashMap.keySet()){
            int dupeCount = 0;

            for(Integer hashFromList:hashList) {
                if (hashFromList.equals(hashFromMap)) {
                    dupeCount++;
                }
            }

            if(dupeCount>1){
                LineItem updateNameLi = lineItemHashMap.get(hashFromMap);
                String liName = String.valueOf(dupeCount) + " " + updateNameLi.getName();
                updateNameLi.setName(liName);
                lineItemHashMap.put(hashFromMap,updateNameLi);
            }

            noDupeList.add(lineItemHashMap.get(hashFromMap));
        }


        return noDupeList;
    }


    private int hashCode(LineItem lineItem){

        int nameHashCode = lineItem.getName().hashCode();

        int modHashCode = 0;

        if(lineItem.getModifications()!=null){
            List<Modification> modList= lineItem.getModifications();

            for(Modification mo:modList){
                modHashCode = modHashCode+mo.getName().hashCode();
            }
        }

        return nameHashCode+modHashCode;

    }

    }

