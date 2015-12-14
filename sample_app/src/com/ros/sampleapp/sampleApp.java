package com.ros.sampleapp;

import android.app.Activity;
import android.content.res.AssetManager;
import android.os.Bundle;

//import android.app.NativeActivity;
//import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.TextView;

public class sampleApp extends Activity
{
    /** Called when the activity is first created. */
    /** Load jni .so on initialization */
    
    //static AssetManager assetManager;
   // public native String getMessage();
    TextView txtHello;
    
     static {
         System.loadLibrary("sampleapp");
    }
    private SeekBar seekBarSpeed;
    private SeekBar seekBarSteering;
    private TextView textProgressSpeed;
    private TextView textProgressSteering;
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        seekBarSpeed = (SeekBar) findViewById(R.id.speed);
        seekBarSteering = (SeekBar) findViewById(R.id.steering);

        textProgressSpeed = (TextView) findViewById(R.id.textViewSpeed);
        textProgressSteering = (TextView) findViewById(R.id.textViewSteering);

        // Initialize the textview with '0'
        textProgressSpeed.setText(seekBarSpeed.getProgress()-seekBarSpeed.getMax()/2 + "/" + seekBarSpeed.getMax()/2);
        textProgressSteering.setText(seekBarSteering.getProgress()-seekBarSteering.getMax()/2 + "/" + seekBarSteering.getMax()/2);

        //getMessage();
        ((Button) findViewById(R.id.start)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                // ignore the return value
                init();
                changeStopStart(0);
            }
        });

        ((Button) findViewById(R.id.stop)).setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                // ignore the return value
                changeStopStart(1);
            }
        });

        seekBarSpeed.setOnSeekBarChangeListener(
                new OnSeekBarChangeListener() {
            int lastProgress = 0;
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lastProgress = progress-seekBarSpeed.getMax()/2;
                textProgressSpeed.setText(lastProgress + "/" + seekBarSpeed.getMax()/2);
                changeSpeed(lastProgress);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
                
                changeSpeed(lastProgress);
            }
        });

        seekBarSteering.setOnSeekBarChangeListener(
                new OnSeekBarChangeListener() {
            int lastProgress = 50;
            int steering_angle = lastProgress*(9/5);
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lastProgress = progress;
                textProgressSteering.setText(lastProgress-seekBarSteering.getMax()/2 + "/" + seekBarSteering.getMax()/2);
                steering_angle = (lastProgress/5)*9;
                changeSteering(steering_angle);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            public void onStopTrackingTouch(SeekBar seekBar) {
                steering_angle = (lastProgress/5)*9;
                changeSteering(steering_angle);
            }
        });
    }
    

    
    public native void init();
    public native void changeSpeed(int manualSteering);
    public native void changeSteering(int manualSpeed);
    public native void changeStopStart(int manualStopStart);


   
}
