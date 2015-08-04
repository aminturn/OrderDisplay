package com.trubeacon.ordermonitorgui;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.tru.clover.api.app.AppBillingInfo;
import com.tru.clover.api.app.service.GetBillingInfo;
import com.tru.clover.api.client.error.*;
import com.tru.clover.api.client.error.Error;
import com.tru.clover.api.client.filter.Filter;
import com.tru.clover.api.common.WrappedList;
import com.tru.clover.api.inventory.Item;
import com.tru.clover.api.inventory.Tag;
import com.tru.clover.api.inventory.service.GetTags;
import com.tru.clover.api.merchant.Device;
import com.tru.clover.api.merchant.Devices;
import com.tru.clover.api.merchant.service.GetDevices;
import com.tru.clover.api.merchant.service.GetOrderTypes;
import com.tru.clover.api.order.LineItem;
import com.tru.clover.api.order.Modification;
import com.tru.clover.api.order.Order;
import com.tru.clover.api.order.OrderType;
import com.tru.clover.api.order.service.GetOrders;
import com.tru.clover.api.order.service.UpdateOrder;

import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.joda.time.format.DateTimeFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class OrderMonitorData {

    private static String appId = "MMGZVXP1ARPEC";
    private static OrderMonitorData orderMonitorData;
    private static List<Order> progressOrdersList = new ArrayList<>();
    private static List<Order> doneOrdersList = new ArrayList<>();
    private static List<Device> deviceList = new ArrayList<>();
    private static List<OrderType> orderTypesList = new ArrayList<>();
    private static List<Tag> tagList = new ArrayList<>();
    private static Context mContext;
    private static HashMap<String,String> deviceHashMap = new HashMap<String,String>();
    private static boolean tagsUpdated;
    private static boolean devicesUpdated;
    private static boolean orderTypesUpdated;
    private static AppBillingInfo.Status billingStatus;


    //75314KYGAMC4P

//Scooters merchant credentials
//    private static String mId = "75314KYGAMC4P";
//    private static String token = "02da5d87-8240-2a5d-38d0-f6bfa4e8627d";

//TruBeacon, Inc merchant credentials
    //private static String mId = "N0H6K4ZCTSW1P";
    //private static String token = "77b77258-4de3-cf35-2c4f-d856ba598cec";

    private static String mId;
    private static String token;

    private static String ORDER_DONE_KEY = "#trubeacon_ordermonitor_done";

    private static int billingSeconds = 30;

    public static OrderMonitorData getOrderMonitorData(){
        if(orderMonitorData==null){
            orderMonitorData = new OrderMonitorData();
            mContext = OrderMonitorGUI.getAppContext();
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        mId = sp.getString(mContext.getString(R.string.merchant_id_key),"");
        token = sp.getString(mContext.getString(R.string.saved_token_key),"");
        String statusString = sp.getString(mContext.getString(R.string.billing_status),AppBillingInfo.Status.ACTIVE.toString());
        billingStatus = AppBillingInfo.Status.fromString(statusString);
        return orderMonitorData;
    }

    public List<Order> getDoneOrdersList(){
        return doneOrdersList;
    }

    public static List<Order> getProgressOrdersList(){
        return new ArrayList<>(progressOrdersList);
    }

    public static List<Device> getDeviceList() {
        return deviceList;
    }

    public static List<OrderType> getOrderTypesList() {
        return orderTypesList;
    }

    public static void addDevicetoHash(String id, String name){
        //id is key, name is value
        deviceHashMap.put(id,name);
    }

    public static String getDeviceNamefromId(String id){
        return (String) deviceHashMap.get(id);
    }

    public static List<Tag> getTagList() {
        return tagList;
    }

    public static void setTagList(List<Tag> tagList) {
        OrderMonitorData.tagList = new ArrayList<>(tagList);
    }

    public static void setmId(String mId) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        sp.edit().putString(mContext.getString(R.string.merchant_id_key),mId).apply();
        OrderMonitorData.mId = mId;
    }

    public static void setToken(String token) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        sp.edit().putString(mContext.getString(R.string.saved_token_key),token).apply();
        OrderMonitorData.token = token;
    }

    public void refreshDevicesOrderTypesTags(){
        tagsUpdated=false;
        devicesUpdated=false;
        orderTypesUpdated=false;
        refreshDevices();
        refreshOrderTypes();
        refreshTags();
    }

    private void refreshBillingInfo(){

        CloverService.getService().getBillingInfo(mId,token,appId, new GetBillingInfo.GetBillingInfoCallback(){
            @Override
            public void onGetBillingInfo(AppBillingInfo appBillingInfo) {

                billingStatus = appBillingInfo.getStatus();
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
                sp.edit().putString(mContext.getString(R.string.billing_status),billingStatus.toString()).apply();
                Log.v("status", billingStatus.toString());
        }

            @Override
            public void onFailGetBillingInfo(Error error) {

                Log.v("failed getbillinginfo", error.getMessage());

            }
        });
    }


    public void refreshDevices(){
        CloverService.getService().getDevices(mId, token, new GetDevices.GetDevicesCallback() {
            @Override
            public void onGetDevices(Devices devices) {
                deviceList = new ArrayList<Device>(devices.getDevices());
                devicesUpdated = true;
                broadcastDevicesOrderTypesTags();
            }

            @Override
            public void onFailGetDevices(Error error) {
                Log.v("failed to get devices", error.getMessage());
            }
        });
    }

    public void refreshOrderTypes(){

        CloverService.getService().getOrderTypes(mId, token, new GetOrderTypes.GetOrderTypesCallback() {
            @Override
            public void onGetOrderTypes(WrappedList<OrderType> wrappedList) {
                orderTypesUpdated = true;
                orderTypesList = new ArrayList<OrderType>(wrappedList);
                broadcastDevicesOrderTypesTags();
            }

            @Override
            public void onFailGetOrderTypes(Error error) {
                Log.v("failed get order types", error.getMessage());
            }
        });

    }

    public void refreshTags(){

        CloverService.getService().getTags(mId, token, new GetTags.GetTagsCallback() {
            @Override
            public void onGetTags(WrappedList<Tag> wrappedList) {
                tagList = new ArrayList<Tag>(wrappedList);
                tagsUpdated = true;
                broadcastDevicesOrderTypesTags();
            }

            @Override
            public void onFailGetTags(Error error) {

                Log.v("failed to get tags", error.getMessage());

            }
        });

    }

    private void broadcastDevicesOrderTypesTags(){
        if(devicesUpdated&&tagsUpdated&&orderTypesUpdated) {
            OrderMonitorBroadcaster.sendBroadcast(BroadcastEvent.REFRESH_DEVICES_AND_ORDER_TYPES);
        }
    }


    public void refreshOrders(){

        DateTime start = DateTime.now().minusMinutes(30);
        DateTime stop = DateTime.now();

        if(mId.equals("")||token.equals("")){
            Toast.makeText(OrderMonitorGUI.getAppContext(), "Please connect to your Clover account from the Settings menu", Toast.LENGTH_LONG).show();
        }else {

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
            Long lastBillingCheck = sp.getLong(mContext.getString(R.string.last_billing_check), 0);
            DateTime lastBillingDateTime = new DateTime(lastBillingCheck);
            DateTime now = DateTime.now();
            int elapsedSeconds = Seconds.secondsBetween(lastBillingDateTime, now).getSeconds();

            if (elapsedSeconds > billingSeconds) {
                Log.v("check billing",now.toString());
                refreshBillingInfo();
                sp.edit().putLong(mContext.getString(R.string.last_billing_check),now.getMillis()).apply();
            }

            if (billingStatus.equals(AppBillingInfo.Status.ACTIVE)) {
                // fetch all orders from the last 6 months
                CloverService.getService().getOrders(mId, token, new GetOrders.GetOrdersCallback() {

                            @Override
                            public void onGetOrders(WrappedList<Order> orders) {

                                Log.v("orders fetched", String.valueOf(orders.size()));

                                List<Order> allOrders = new ArrayList<Order>(orders);
                                updateProgressOrdersList(allOrders);

                                //show oldest orders first, should come in chronological order with most recent order first, so reverse the list
                                Collections.reverse(progressOrdersList);
                                Collections.reverse(doneOrdersList);

                                OrderMonitorBroadcaster.sendBroadcast(BroadcastEvent.REFRESH_ORDERS);

                            }

                            @Override
                            public void onFailGetOrders(com.tru.clover.api.client.error.Error error) {
                                Log.e("Failed to fetch orders", error.getMessage());
                            }

                        },
                        Filter.filter("clientCreatedTime", Filter.Comparator.GREATER_THAN, start.getMillis()),
                        Filter.filter("clientCreatedTime", Filter.Comparator.LESS_THAN, stop.getMillis()));
            }else {
                Toast.makeText(mContext, "Please check your billing status", Toast.LENGTH_LONG).show();
                progressOrdersList.clear();
                doneOrdersList.clear();
                OrderMonitorBroadcaster.sendBroadcast(BroadcastEvent.REFRESH_ORDERS);
            }
        }
    }


    public void markAllOrdersDone(){

        for(Order o:progressOrdersList){
            o.setNote(ORDER_DONE_KEY);
            markDone(o.getId(),o);
        }

    }


    public boolean showLineItem(String itemName){

        boolean showLineItem = true;


            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
            String[] defStrings = new String[1];
            Set<String> defset = new HashSet<>(Arrays.asList(defStrings));

            Set<String> tagsSelected = sp.getStringSet(mContext.getString(R.string.item_tag_pref), defset);

            for (Tag t : tagList) {
                if (t.getItems() != null && tagsSelected.contains(t.getName())) {
                    for (Item item : t.getItems()) {
                        if (item.getName().equals(itemName)) {
                            showLineItem = false;
                        }
                    }
                }
            }


        return showLineItem;
    }


    //TODO: add a try catch here...not having the permissions caused the app to crash
    public void markDone(String orderId,Order updateOrder){

        updateOrder.setNote(ORDER_DONE_KEY);

        CloverService.getService().updateOrder(mId, token, orderId, updateOrder, new UpdateOrder.UpdateOrderCallback() {
            @Override
            public void onUpdateOrder(Order order) {
                Log.v("order updated", "order updated");
            }

            @Override
            public void onFailUpdateOrder(com.tru.clover.api.client.error.Error error) {
                Log.v("failed to update order", error.getMessage());
            }
        });
    }

    public void markUnDone(String orderId,Order updateOrder){

        updateOrder.setNote("");

        CloverService.getService().updateOrder(mId, token, orderId, updateOrder, new UpdateOrder.UpdateOrderCallback() {
            @Override
            public void onUpdateOrder(Order order) {
                Log.v("order updated", "order updated");
            }

            @Override
            public void onFailUpdateOrder(com.tru.clover.api.client.error.Error error) {
                Log.v("failed to update order", error.getMessage());
            }
        });
    }

    private void updateProgressOrdersList(List<Order> allOrders){

        if(allOrders!=null) {

            boolean hasLineItems;
            boolean onlyShowPaid;
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);

            String[] defStrings = new String[1];
            Set<String> defset = new HashSet<>(Arrays.asList(defStrings));

            Set<String> orderTypesSelected = sp.getStringSet(mContext.getString(R.string.order_type_pref), defset);
            Set<String> devicesSelected = sp.getStringSet(mContext.getString(R.string.devices_pref),defset);
            onlyShowPaid = sp.getBoolean(mContext.getString(R.string.after_paid_pref),false);
            //filter out order if it doesn't have line items

            List<Order> filteredList = new ArrayList<>();

            for(Order order:allOrders) {

                hasLineItems=false;

                if(order.getLineItems()!=null){
                    for (LineItem li : order.getLineItems()) {
                        if (showLineItem(li.getName())) {
                            hasLineItems = true;
                        }
                    }
                }


               if(onlyShowPaid) {
                   if (order.getOrderType() == null) {
                       if (devicesSelected.contains(order.getDevice().getId()) && hasLineItems&&order.getState().equals(mContext.getString(R.string.locked))) {
                           filteredList.add(order);
                       }
                   } else if (orderTypesSelected.contains(order.getOrderType().getLabel()) && devicesSelected.contains(order.getDevice().getId()) && hasLineItems&&order.getState().equals(mContext.getString(R.string.locked))) {
                       filteredList.add(order);
                   }
               }else{
                   if (order.getOrderType() == null) {
                       if (devicesSelected.contains(order.getDevice().getId()) && hasLineItems) {
                           filteredList.add(order);
                       }
                   } else if (orderTypesSelected.contains(order.getOrderType().getLabel()) && devicesSelected.contains(order.getDevice().getId()) && hasLineItems) {
                       filteredList.add(order);
                   }
               }

            }


            progressOrdersList.clear();
            doneOrdersList.clear();

            for (Order o : filteredList) {
                if (o.getNote() != null) {
                    if (o.getNote().equals(ORDER_DONE_KEY)) {
                        doneOrdersList.add(o);
                    } else {
                        progressOrdersList.add(o);
                    }
                } else {
                    progressOrdersList.add(o);
                }
            }

            Collections.sort(progressOrdersList, new Comparator<Order>() {
                @Override
                public int compare(Order order1, Order order2) {
                    if(order1.getCreatedTime()<order2.getCreatedTime()){
                        return 1;
                    }else if(order1.getCreatedTime()>order2.getCreatedTime()){
                        return-1;
                    }else{
                        return 0;
                    }
                }
            });

        }
    }


    public enum BroadcastEvent{

        REFRESH_ORDERS,
        REFRESH_DEVICES_AND_ORDER_TYPES;

        public static final String REFRESH_ORDERS_VALUE = "REFRESH_ORDERS";
        public static final String REFRESH_DEVS_AND_ORDER_TYPES_VALUE = "REFRESH_DEVICES_AND_ORDER_TYPES";

    }

}
