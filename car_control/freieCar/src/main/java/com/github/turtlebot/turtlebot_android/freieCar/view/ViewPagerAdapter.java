package com.github.turtlebot.turtlebot_android.freieCar.view;

import android.app.Fragment;
import android.app.FragmentManager;
import android.support.v13.app.FragmentStatePagerAdapter;

import com.github.turtlebot.turtlebot_android.freieCar.FreieCarActivity;

/**
 * Created by hp1 on 21.01.2015.
 * Modified by Daniel Neumann on 29.03.16.
 */
public class ViewPagerAdapter extends FragmentStatePagerAdapter {

    FreieCarActivity freieCarActivity;
    final int NumbOfTabs = 2; // Store the number of tabs, this will also be passed when the ViewPagerAdapter is created


    // Build a Constructor and assign the passed Values to appropriate values in the class
    public ViewPagerAdapter(FragmentManager fm) {
        super(fm);
    }

    public void setFreieCarActivity(FreieCarActivity freieCarActivity) {
        this.freieCarActivity = freieCarActivity;
    }

    //This method return the fragment for the every position in the View Pager
    @Override
    public Fragment getItem(int position) {

        if (position == 0) // if the position is 0 we are returning the First tab
        {
            ControlFragment c = new ControlFragment();
            return c;
        } else             // As we are having 2 tabs if the position is now 0 it must be 1 so we are returning second tab
        {
            VisualGPSFragment v = new VisualGPSFragment();
            return v;
        }


    }


    // This method return the Number of tabs for the tabs Strip
    @Override
    public int getCount() {
        return NumbOfTabs;
    }
}