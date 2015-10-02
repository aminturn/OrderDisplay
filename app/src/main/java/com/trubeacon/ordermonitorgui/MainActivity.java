package com.trubeacon.ordermonitorgui;

import android.app.Fragment;
import android.app.FragmentManager;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.tru.clover.api.client.filter.Filter;
import com.tru.clover.api.common.WrappedList;
import com.tru.clover.api.order.service.GetOrders;
import com.tru.clover.api.order.Order;

import org.joda.time.DateTime;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        if(savedInstanceState==null) {
            getFragmentManager().beginTransaction().add(R.id.container, new OrdersInProgressFragment()).commit();
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.action_settings) {
            getFragmentManager().beginTransaction().replace(R.id.container, new OrderMonitorPreferences()).addToBackStack("").commit();
            return true;
        }
        else if (id==R.id.action_orders_in_progress){
            getFragmentManager().beginTransaction().replace(R.id.container,new OrdersInProgressFragment()).addToBackStack("").commit();
            return true;
        }
        else if(id == R.id.action_done_orders){
            getFragmentManager().beginTransaction().replace(R.id.container,new DoneOrdersFragment()).addToBackStack("").commit();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
            FragmentManager fm = getFragmentManager();
            if (fm.getBackStackEntryCount() > 0) {
                fm.popBackStack();
            } else {
                super.onBackPressed();
            }
    }
}
