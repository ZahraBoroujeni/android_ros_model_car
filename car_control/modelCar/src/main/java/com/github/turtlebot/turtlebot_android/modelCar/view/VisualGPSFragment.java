package com.github.turtlebot.turtlebot_android.modelCar.view;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.github.turtlebot.turtlebot_android.modelCar.ModelCarActivity;
import com.github.turtlebot.turtlebot_android.modelCar.R;

import java.util.concurrent.atomic.AtomicBoolean;

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

        simulatedDrive();
    }


/////////////////////// Begin: Simulation ////////////////////
    private void simulatedDrive() {

        final MyHandler handler = new MyHandler(modelCarActivity);
        final AtomicBoolean ContinueThread = new AtomicBoolean(false);
        Thread background = new Thread(new Runnable() {

            public void run() {

                try {
                    Thread.sleep(2000);
                    handler.set(200, 400, 0.0);
                    handler.sendMessage(handler.obtainMessage());
                    ContinueThread.get();
                    Thread.sleep(2000);
                    handler.set(200, 300, 0.0);
                    handler.sendMessage(handler.obtainMessage());
                    ContinueThread.get();
                    Thread.sleep(2000);
                    handler.set(200, 300, 90.0);
                    handler.sendMessage(handler.obtainMessage());
                    ContinueThread.get();
                    Thread.sleep(2000);
                    handler.set(250, 300, 90.0);
                    handler.sendMessage(handler.obtainMessage());
                    ContinueThread.get();
                    Thread.sleep(2000);
                    handler.set(250, 300, 180.0);
                    handler.sendMessage(handler.obtainMessage());
                    ContinueThread.get();
                    Thread.sleep(2000);
                    handler.set(250, 400, 180.0);
                    handler.sendMessage(handler.obtainMessage());
                    ContinueThread.get();
                    Thread.sleep(2000);
                    handler.set(250, 500, 180.0);
                    handler.sendMessage(handler.obtainMessage());

                } catch (Throwable t) {

                }

            }

        });

        ContinueThread.set(true);

        background.start();
    }

    private class MyHandler extends Handler {
        ModelCarActivity modelCarActivity;
        double x;
        double y;
        double r;

        public MyHandler(ModelCarActivity modelCarActivity) {
            this.modelCarActivity = modelCarActivity;
        }

        public void set(double x, double y, double r) {
            this.x = x;
            this.y = y;
            this.r = r;
        }

        @Override
        public void handleMessage(Message msg) {
            modelCarActivity.drawMarker(x, y, r);
        }
    }
/////////////////////// End: Simulation ////////////////////

}