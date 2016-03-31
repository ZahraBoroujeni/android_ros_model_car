package com.github.turtlebot.turtlebot_android.modelCar.view;

import android.app.Fragment;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import com.github.turtlebot.turtlebot_android.modelCar.ModelCarActivity;
import com.github.turtlebot.turtlebot_android.modelCar.R;

/**
 * Created by Daniel Neumann on 29.03.16.
 */
public class ControlFragment extends Fragment {

    private RelativeLayout layout1;
    private Toast lastToast;
    private ModelCarActivity modelCarActivity;
    private short emergency_stop_mode = 1;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.main, container, false);
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();

        buildView(false);

        modelCarActivity = (ModelCarActivity) getActivity();
        modelCarActivity.setLayoutControl(layout1);
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // TODO this is not called now, so we cannot flip the screen
        super.onConfigurationChanged(newConfig);

        buildView(true);
    }

    private void buildView(boolean rebuild) {
        SeekBar prevSpeedBar = null;
        SeekBar prevSteeringBar = null;

        if (rebuild) {
            // If we are rebuilding GUI (probably because the screen was rotated) we must save widgets'
            // previous content, as setContentView will destroy and replace them with new instances
            prevSpeedBar = (SeekBar) getView().findViewById(R.id.seekBar_speed);
            prevSteeringBar = (SeekBar) getView().findViewById(R.id.seekBar_steering);
        }

        ImageButton stopButton = (ImageButton) getView().findViewById(R.id.button_stop);
        stopButton.setOnClickListener(stopButtonListener);

        SeekBar speedBar = (SeekBar) getView().findViewById(R.id.seekBar_speed);
        speedBar.setOnSeekBarChangeListener(speedBarListener);
        if (rebuild)
            speedBar.setProgress(prevSpeedBar.getProgress());

        SeekBar steeringBar = (SeekBar) getView().findViewById(R.id.seekBar_steering);
        steeringBar.setOnSeekBarChangeListener(steeringBarListener);
        if (rebuild)
            steeringBar.setProgress(prevSteeringBar.getProgress());

        // Take a reference to the image view to show incoming panoramic pictures
        layout1 = (RelativeLayout) getView().findViewById(R.id.main_inner);

        if (rebuild) {
            layout1.setBackground(null);
        }

    }

    /************************************************************
     * Android code:
     ************************************************************/


    private final View.OnClickListener stopButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            ImageButton emergency_stopButton = (ImageButton) getView().findViewById(R.id.button_stop);
            if (emergency_stop_mode == 1) {
                emergency_stop_mode = 0;
                modelCarActivity.callPublishStopStart(emergency_stop_mode);
                emergency_stopButton.setImageResource(R.drawable.emergency_stop_inactive);
            } else {
                emergency_stop_mode = 1;
                modelCarActivity.callPublishStopStart(emergency_stop_mode);
                emergency_stopButton.setImageResource(R.drawable.emergency_stop_active);
                SeekBar speedBar1 = (SeekBar) getView().findViewById(R.id.seekBar_speed);
                speedBar1.setProgress(1000);

            }
        }

    };


    private final SeekBar.OnSeekBarChangeListener speedBarListener = new SeekBar.OnSeekBarChangeListener() {
        short lastProgress = 0;

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            lastProgress = (short) (seekBar.getMax() / 2 - progress);
            modelCarActivity.callPublishSpeed(lastProgress);
            if (lastToast == null)
                lastToast = Toast.makeText(modelCarActivity.getBaseContext(), -lastProgress + " deg/s", Toast.LENGTH_SHORT);
            else
                lastToast.setText(-lastProgress + " deg/s");

            lastToast.show();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            modelCarActivity.callPublishSpeed(lastProgress);
        }
    };

    private final SeekBar.OnSeekBarChangeListener steeringBarListener = new SeekBar.OnSeekBarChangeListener() {
        short lastProgress = 50;
        short angle = 0;

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            lastProgress = (short) (seekBar.getMax() / 2 - progress);
            angle = (short) (((seekBar.getMax() - progress) / 5) * 9);
            modelCarActivity.callPublishSteering(angle);
            if (lastToast == null)
                lastToast = Toast.makeText(modelCarActivity.getBaseContext(), lastProgress + " deg", Toast.LENGTH_SHORT);
            else
                lastToast.setText(lastProgress + " deg");

            lastToast.show();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    };

}