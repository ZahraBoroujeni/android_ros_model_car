package com.github.turtlebot.turtlebot_android.freieCar.view;

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.github.turtlebot.turtlebot_android.freieCar.FreieCarActivity;
import com.github.turtlebot.turtlebot_android.freieCar.R;

/**
 * Created by Daniel Neumann on 29.03.16.
 */
public class VisualGPSFragment extends Fragment {

    private FreieCarActivity freieCarActivity;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.visual_gps, container, false);
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();

        freieCarActivity = (FreieCarActivity) getActivity();

        LinearLayout layout = (LinearLayout) getView().findViewById(R.id.gps_camera);
        ImageView imageView = (ImageView) getView().findViewById(R.id.gps_image_view);
        freieCarActivity.setLayoutGPS(layout, imageView);

        freieCarActivity.dummyDrawMarker(50, 50);
    }


}