package com.github.turtlebot.turtlebot_android.modelCar.view;

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.github.turtlebot.turtlebot_android.modelCar.ModelCarActivity;
import com.github.turtlebot.turtlebot_android.modelCar.R;

/**
 * Created by Daniel Neumann on 29.03.16.
 */
public class VisualGPSFragment extends Fragment {

    public static final int mapId = R.drawable.map;
    public static final int carMarkerId = R.drawable.model_car_marker;
    public static final int mapXWidthCentimeter = 500;
    public static final int mapYHeightCentimeter = 700;

    private ModelCarActivity modelCarActivity;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.visual_gps, container, false);
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();

        modelCarActivity = (ModelCarActivity) getActivity();

        LinearLayout layout = (LinearLayout) getView().findViewById(R.id.gps_camera);
        ImageView imageView = (ImageView) getView().findViewById(R.id.gps_image_view);
        modelCarActivity.setLayoutGPS(layout, imageView);

        modelCarActivity.drawMarker(200, 400);
    }

}