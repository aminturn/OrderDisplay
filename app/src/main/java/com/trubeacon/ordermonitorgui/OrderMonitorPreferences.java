package com.trubeacon.ordermonitorgui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.tru.clover.api.inventory.Tag;
import com.tru.clover.api.merchant.Device;
import com.tru.clover.api.merchant.Devices;
import com.tru.clover.api.order.Order;
import com.tru.clover.api.order.OrderType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class OrderMonitorPreferences extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener, Refreshable {

    private static String actionBarTitle = "Order Display Settings";
    private static String CLOVER_PREF_KEY = "clover_pref_key";
    private static String ORDER_TYPE_FIRST_TIME = "order_type_first_time";
    private static String DEVICE_FIRST_TIME = "device_first_time";
    private OrderMonitorData orderMonitorData = OrderMonitorData.getOrderMonitorData();

    private List<Device> deviceList;
    private List<OrderType> orderTypeList;
    private List<Tag> tagList;

    private MultiSelectListPreference tagPref;
    private MultiSelectListPreference orderTypePref;
    private MultiSelectListPreference devicePref;
    private PreferenceScreen colorPrefScreen;

    private BroadcastReceiver devicesAndOrderTypesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onRefresh();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        tagPref = (MultiSelectListPreference) findPreference((getString(R.string.item_tag_pref)));
        orderTypePref = (MultiSelectListPreference) findPreference(getString(R.string.order_type_pref));
        devicePref = (MultiSelectListPreference) findPreference(getString(R.string.devices_pref));
        colorPrefScreen = (PreferenceScreen) findPreference(getString(R.string.color_settings));


        Preference cloverPref = findPreference(CLOVER_PREF_KEY);
        cloverPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                getActivity().getFragmentManager().beginTransaction().replace(R.id.container, new MyWebViewFragment()).addToBackStack("").commit();

                return false;
            }
        });

        setHasOptionsMenu(true);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        Log.v("preference fragment", "oncreateview");

        //set up the actionbar with the fragment title and enable the home button
        ActionBar actionBar = ((MainActivity) getActivity()).getSupportActionBar();
        actionBar.setTitle(actionBarTitle);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());

        Preference displayPref = findPreference(getString(R.string.display_pref));
        displayPref.setSummary(sp.getString(getString(R.string.display_pref), ""));

        Preference fontPref = findPreference(getString(R.string.font_size_pref));
        fontPref.setSummary(sp.getString(getString(R.string.font_size_pref), "") + " sp");

        Preference agePref = findPreference(getString(R.string.age_of_orders_pref));
        agePref.setSummary(sp.getString(getString(R.string.age_of_orders_pref),"") + " Hour(s)");

        refresh();

        return super.onCreateView(inflater, container, savedInstanceState);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if(itemId==android.R.id.home){
            getActivity().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        OrderMonitorBroadcaster.registerReceiver(devicesAndOrderTypesReceiver, OrderMonitorData.BroadcastEvent.REFRESH_DEVICES_AND_ORDER_TYPES);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        OrderMonitorBroadcaster.unregisterReceiver(devicesAndOrderTypesReceiver);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {

        if (s.equals(getString(R.string.display_pref))) {
            Preference displayPref = findPreference(s);
            // Set summary to be the user-description for the selected value
            displayPref.setSummary(sharedPreferences.getString(s, ""));
        }
        else if(s.equals(getString(R.string.font_size_pref))) {
            Preference fontPref = findPreference(s);
            // Set summary to be the user-description for the selected value
            fontPref.setSummary(sharedPreferences.getString(s, "") + " sp");
        }else if(s.equals(getString(R.string.age_of_orders_pref))){
            Preference agePref = findPreference(s);
            agePref.setSummary(sharedPreferences.getString(s,"") + " Hour(s)");
        }
    }

    @Override
    public void refresh() {

        colorPrefScreen.removeAll();

        if(orderMonitorData.getDeviceList().isEmpty()&&orderMonitorData.getOrderTypesList().isEmpty()){
            colorPrefScreen.setEnabled(false);
        }else{
            colorPrefScreen.setEnabled(true);
        }

        if(!orderMonitorData.getDeviceList().isEmpty()){
            deviceList = orderMonitorData.getDeviceList();
            updateDevicePreferences();
            devicePref.setEnabled(true);
            devicePref.setSummary(getString(R.string.devices_enabled_summary));
        }else{
            devicePref.setEnabled(false);
            devicePref.setSummary(R.string.no_devices_string);
        }

        if(!orderMonitorData.getOrderTypesList().isEmpty()){
            orderTypeList = orderMonitorData.getOrderTypesList();
            updateOrderTypePreferences();
            orderTypePref.setEnabled(true);
            orderTypePref.setSummary(getString(R.string.order_types_enabled_summary));
        }else{
            orderTypePref.setEnabled(false);
            orderTypePref.setSummary(R.string.no_order_types_string);
        }

        if(!orderMonitorData.getTagList().isEmpty()){
            tagList = orderMonitorData.getTagList();
            updateTagPreferences();
            tagPref.setEnabled(true);
            tagPref.setSummary(getString(R.string.tag_pref_summary));
        }else{
            tagPref.setEnabled(false);
            tagPref.setSummary(R.string.tag_pref_summary_disabled);
        }

        orderMonitorData.refreshDevicesOrderTypesTags();
    }

    @Override
    public void onPreRefresh() {

    }

    @Override
    public void onRefresh() {

        colorPrefScreen.removeAll();

        if(orderMonitorData.getDeviceList().isEmpty()&&orderMonitorData.getOrderTypesList().isEmpty()){
            colorPrefScreen.setEnabled(false);
        }else{
            colorPrefScreen.setEnabled(true);
        }

        if(!orderMonitorData.getDeviceList().isEmpty()){
            deviceList = orderMonitorData.getDeviceList();
            updateDevicePreferences();
            devicePref.setEnabled(true);
            devicePref.setSummary(getString(R.string.devices_enabled_summary));
        }else{
            devicePref.setEnabled(false);
            devicePref.setSummary(R.string.no_devices_string);
        }

        if(!orderMonitorData.getOrderTypesList().isEmpty()) {
            orderTypeList = orderMonitorData.getOrderTypesList();
            updateOrderTypePreferences();
            orderTypePref.setEnabled(true);
            orderTypePref.setSummary(getString(R.string.order_types_enabled_summary));
        }else{
            orderTypePref.setEnabled(false);
            orderTypePref.setSummary(R.string.no_order_types_string);
        }

        if(!orderMonitorData.getTagList().isEmpty()){
            tagList = orderMonitorData.getTagList();
            updateTagPreferences();
            tagPref.setEnabled(true);
            tagPref.setSummary(getString(R.string.tag_pref_summary));
        }else{
            tagPref.setEnabled(false);
            tagPref.setSummary(R.string.tag_pref_summary_disabled);
        }

    }

    private void updateTagPreferences(){
        String[] entries = new String[tagList.size()];
        String[] entryVals = new String[tagList.size()];

        int i =0;
        for(Tag t:tagList){
            entries[i] = t.getName();
            //maybe use ID?
            entryVals[i] = t.getName();
            i++;
        }

        tagPref.setEntries(entries);
        tagPref.setEntryValues(entryVals);


    }


    private void updateOrderTypePreferences(){

        String[] entries = new String[orderTypeList.size()];
        String[] entryVals = new String[orderTypeList.size()];

        PreferenceCategory typePrefCat = new PreferenceCategory(getActivity());
        typePrefCat.setTitle(getString(R.string.order_type_colors));
        typePrefCat.setSummary("The background color of the order header can be changed to quickly indicate the order type");

        colorPrefScreen.addPreference(typePrefCat);

        int i = 0;
        for(OrderType ot:orderTypeList){
            ListPreference orderColorList = new ListPreference(getActivity());
            orderColorList.setEntries(getResources().getStringArray(R.array.order_type_color));
            orderColorList.setEntryValues(getResources().getStringArray(R.array.order_type_color_vals));
            orderColorList.setTitle(ot.getLabel());
            orderColorList.setKey(ot.getLabel());
            orderColorList.setSummary("Order type indicated by order header color");
            typePrefCat.addPreference(orderColorList);
            entries[i] =ot.getLabel();
            entryVals[i] = ot.getLabel();
            i++;
        }

        orderTypePref.setEntries(entries);
        orderTypePref.setEntryValues(entryVals);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if(sp.getBoolean(ORDER_TYPE_FIRST_TIME,true)){

            Set<String> valSet = new HashSet<String>(Arrays.asList(entryVals));

            orderTypePref.setValues(valSet);

            sp.edit().putBoolean(ORDER_TYPE_FIRST_TIME,false).apply();
        }


    }

    private void updateDevicePreferences(){

        String[] entries = new String[deviceList.size()];
        String[] entryVals = new String[deviceList.size()];

        PreferenceCategory originPrefCat = new PreferenceCategory(getActivity());
        originPrefCat.setTitle(getString(R.string.device_type_colors));
        originPrefCat.setSummary("The background color of the body of the order can be changed to quickly indicate the origin");

        colorPrefScreen.addPreference(originPrefCat);

        int i = 0;

        for(Device d:deviceList){
            ListPreference originColorList = new ListPreference(getActivity());
            originColorList.setEntries(getResources().getStringArray(R.array.origin_device_color));
            originColorList.setEntryValues(getResources().getStringArray(R.array.origin_device_color_vals));
            originColorList.setSummary("Order origin indicated by order body color");
            if(d.getName()!=null) {
                entries[i] = d.getName();
                originColorList.setTitle(d.getName());
                orderMonitorData.addDevicetoHash(d.getUuid(),d.getName());
            }else{
                entries[i] = d.getModel();
                originColorList.setTitle(d.getModel());
                orderMonitorData.addDevicetoHash(d.getUuid(),d.getModel());
            }
            originColorList.setKey(d.getUuid());
            originPrefCat.addPreference(originColorList);
            entryVals[i] = d.getUuid();
            i++;
        }

        devicePref.setEntries(entries);
        devicePref.setEntryValues(entryVals);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if(sp.getBoolean(DEVICE_FIRST_TIME,true)){

            Set<String> valSet = new HashSet<String>(Arrays.asList(entryVals));

            devicePref.setValues(valSet);

            sp.edit().putBoolean(DEVICE_FIRST_TIME,false).apply();
        }

    }
}
