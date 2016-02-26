package com.github.turtlebot.turtlebot_android.freieCar;

import org.ros.address.InetAddressFactory;
import org.ros.android.MessageCallable;
import com.github.rosjava.android_remocons.common_tools.apps.RosAppActivity;
import org.ros.exception.RemoteException;
import org.ros.exception.ServiceNotFoundException;
import org.ros.message.MessageListener;
import org.ros.node.topic.Publisher;
import org.ros.namespace.GraphName;
import org.ros.namespace.NameResolver;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMain;
import org.ros.node.NodeMainExecutor;
import org.ros.node.service.ServiceClient;
import org.ros.node.service.ServiceResponseListener;
import org.ros.node.topic.Subscriber;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Handler;


public class FreieCarActivity extends RosAppActivity implements NodeMain,SensorEventListener
{
  private RelativeLayout layout1;
  private Toast   lastToast;
  private ConnectedNode node;
  private final MessageCallable<Bitmap, sensor_msgs.CompressedImage> callable = new ScaledBitmapFromCompressedImage(2);

  private SensorManager mSensorManager = null;

  // angular speeds from gyro
  private float[] gyro = new float[3];

  // rotation matrix from gyro data
  private float[] gyroMatrix = new float[9];

  // orientation angles from gyro matrix
  private float[] gyroOrientation = new float[3];

  // magnetic field vector
  private float[] rotation = new float[3];

  private float[] magnet = new float[3];

  // accelerometer vector
  private float[] accel = new float[3];

  // orientation angles from accel and magnet
  private float[] accMagOrientation = new float[3];

  // final orientation angles from sensor fusion
  private float[] fusedOrientation = new float[3];

  // accelerometer and magnetometer based rotation matrix
  private float[] rotationMatrix = new float[9];

  public static final float EPSILON = 0.000000001f;
  private static final float NS2S = 1.0f / 1000000000.0f;
  private long timestamp;
  private boolean initState = true;

  public static final int TIME_CONSTANT = 30;
  public static final float FILTER_COEFFICIENT = 0.98f;
  private Timer fuseTimer = new Timer();

  private short emergency_stop_mode=1;
  private boolean first_time=true;
  private short first_head=0;
  private short stop=1;
  // The following members are only for displaying the sensor output.
  public Handler mHandler;


  public FreieCarActivity()
  {
    super("FreieCarActivity", "FreieCarActivity");
  }

  /************************************************************
    Android code:
    Activity life cycle and GUI management
   ************************************************************/

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    setDefaultMasterName(getString(R.string.default_robot));
    setDefaultAppName(getString(R.string.default_app));
    setDashboardResource(R.id.top_bar);
    setMainWindowResource(R.layout.main);

    super.onCreate(savedInstanceState);
    buildView(false);
    // get sensorManager and initialise sensor listeners
    mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
    initListeners();

    // wait for one second until gyroscope and magnetometer/accelerometer
    // data is initialised then scedule the complementary filter task
    fuseTimer.scheduleAtFixedRate(new calculateFusedOrientationTask(),1000, TIME_CONSTANT);

    // GUI stuff
   // mHandler = new Handler();

    // TODO Tricky solution to the StrictMode; the recommended way is by using AsyncTask
    if (android.os.Build.VERSION.SDK_INT > 9) {
      StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
      StrictMode.setThreadPolicy(policy);
    }

  }

  @Override
  protected void onStop()
  {
    mSensorManager.unregisterListener(this);
    callPublishStopStart(stop);//emergency stop active
    super.onStop();
  }

  @Override
  protected void onRestart()
  {
    super.onRestart();
  }

  @Override
  protected void onPause()
  {
    mSensorManager.unregisterListener(this);
    super.onPause();
  }

  @Override
  protected void onResume()
  {
    initListeners();
    super.onResume();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig)
  {
    // TODO this is not called now, so we cannot flip the screen
    Log.e("FreieCarActivity", "onConfigurationChanged");
    super.onConfigurationChanged(newConfig);

    buildView(true);
  }

  private void buildView(boolean rebuild)
  {
    SeekBar  prevSpeedBar  = null;

    if (rebuild)
    {
      // If we are rebuilding GUI (probably because the screen was rotated) we must save widgets'
      // previous content, as setContentView will destroy and replace them with new instances
      prevSpeedBar  =  (SeekBar)findViewById(R.id.seekBar_speed);
    }

    // Register input controls callbacks
    Button backButton = (Button) findViewById(R.id.back_button);
    backButton.setOnClickListener(backButtonListener);

    ImageButton streeingButton = (ImageButton)findViewById(R.id.button_steering);
    streeingButton.setOnTouchListener(steeringButtonListener);

    ImageButton stopButton  = (ImageButton)findViewById(R.id.button_stop);
    stopButton.setOnClickListener(stopButtonListener);

    SeekBar speedBar  = (SeekBar)findViewById(R.id.seekBar_speed);
    speedBar.setOnSeekBarChangeListener(speedBarListener);
    if (rebuild)
      speedBar.setProgress(prevSpeedBar.getProgress());


    // Take a reference to the image view to show incoming panoramic pictures
    layout1= (RelativeLayout)findViewById(R.id.RelativeLayout1);
    if (rebuild)
    {
      layout1.setBackground(null);
    }

  }

  /**
   * Call Toast on UI thread.
   * @param message Message to show on toast.
   */
  public void showToast(final String message)
  {
    runOnUiThread(new Runnable()
    {
      @Override
      public void run() {
        Toast.makeText(getBaseContext(), message, Toast.LENGTH_LONG).show();
      }
    });
  }

  /************************************************************
    ROS code:
    NodeMain implementation and service call code
   ************************************************************/

  @Override
  protected void init(NodeMainExecutor nodeMainExecutor)
  {
    super.init(nodeMainExecutor);

    NodeConfiguration nodeConfiguration =
      NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress(), getMasterUri());

    nodeMainExecutor.execute(this, nodeConfiguration.setNodeName("android/video_view"));
  }



  protected void callPublishSpeed(short mode)
  {
    if (node == null)
    {
      Log.e("FreieCarActivity", "Still doesn't have a connected node");
      return;
    }
    final Publisher<std_msgs.Int16> speed_pub =
            node.newPublisher("/manual_control/speed", std_msgs.Int16._TYPE);

    std_msgs.Int16 gas = speed_pub.newMessage();
    gas.setData(mode);
    speed_pub.publish(gas);
  }

  protected void callPublishSteering(short mode)
  {
    if (node == null)
    {
      Log.e("FreieCarActivity", "Still doesn't have a connected node");
      return;
    }
    final Publisher<std_msgs.Int16> steering_pub =
            node.newPublisher("/manual_control/steering", std_msgs.Int16._TYPE);

    std_msgs.Int16 steering = steering_pub.newMessage();
    steering.setData(mode);
    steering_pub.publish(steering);
  }
  protected void callPublishStopStart(short mode)
  {
    if (node == null)
    {
      Log.e("FreieCarActivity", "Still doesn't have a connected node");
      return;
    }

    final Publisher<std_msgs.Int16> stop_pub =
            node.newPublisher("/manual_control/stop_start", std_msgs.Int16._TYPE);
    std_msgs.Int16 stop_msg = stop_pub.newMessage();
    stop_msg.setData(mode);
    stop_pub.publish(stop_msg);
  }
  @Override
  public void onStart(ConnectedNode connectedNode)
  {
    Log.e("FreieCarActivity", connectedNode.getName() + " node started");
    node = connectedNode;

    NameResolver appNameSpace = getMasterNameSpace();
    String panoImgTopic = appNameSpace.resolve("/camera/rgb/image_raw/compressed").toString();

    Subscriber<sensor_msgs.CompressedImage> subscriber =
            connectedNode.newSubscriber(panoImgTopic, sensor_msgs.CompressedImage._TYPE);
    subscriber.addMessageListener(new MessageListener<sensor_msgs.CompressedImage>() {
      @Override
      public void onNewMessage(final sensor_msgs.CompressedImage message) {
        layout1.post(new Runnable() {
          @Override
          public void run() {
            BitmapDrawable ob = new BitmapDrawable(getResources(), callable.call(message));
            layout1.setBackground(ob);
          }
        });
        layout1.postInvalidate();
      }
    });
    callPublishStopStart(stop);//emergency stop active
    SeekBar speedBar1  = (SeekBar)findViewById(R.id.seekBar_speed);
    speedBar1.setProgress(1000);

  }

  @Override
  public void onError(Node n, Throwable e)
  {
    Log.d("FreieCarActivity", n.getName() + " node error: " + e.getMessage());
  }

  @Override
  public void onShutdown(Node n) {
    Log.d("FreieCarActivity", n.getName() + " node shuting down...");
    mSensorManager.unregisterListener(this);
    callPublishStopStart(stop);//emergency stop active
  }

  @Override
  public void onShutdownComplete(Node n)
  {
    Log.d("FreieCarActivity", n.getName() + " node shutdown completed");
    mSensorManager.unregisterListener(this);
  }

  @Override
  public GraphName getDefaultNodeName()
  {
    return GraphName.of("android/freieCar");
  }

  /************************************************************
     Android code:
     Anonymous implementation for input controls callbacks
   ************************************************************/

  private final OnClickListener backButtonListener = new OnClickListener()
  {
    @Override
    public void onClick(View v)
    {
      onBackPressed();
    }
  };

  private final OnTouchListener steeringButtonListener = new OnTouchListener()
  {
    @Override
    public boolean onTouch(View v,MotionEvent event) {

      ImageButton streeingButton = (ImageButton)findViewById(R.id.button_steering);
      short mode = 0;
      int action = event.getAction();
      if (action == MotionEvent.ACTION_DOWN) {
        first_head=(short) (accMagOrientation[0]* 180/Math.PI);
        streeingButton.setImageResource(R.drawable.wheel_active);
      }
      else if (action == MotionEvent.ACTION_UP)
      {
        first_head=0;
        streeingButton.setImageResource(R.drawable.wheel_inactive);
        return false;
      }

      mode=(short) (first_head-(short) (accMagOrientation[0]* 180/Math.PI));
      if (mode>180)
        mode=(short)(mode-360);
      else if (mode<-180)
        mode=(short)(360+mode);

      if (lastToast == null)
        lastToast = Toast.makeText(getBaseContext(), mode+ " deg", Toast.LENGTH_SHORT);
      else
        lastToast.setText(mode + " deg" );
      lastToast.show();
      mode=(short) (mode*2);
      mode=(short)(mode+90);

      callPublishSteering(mode);
      return false;   //  the listener has NOT consumed the event, pass it on
    }
  };


  private final OnClickListener stopButtonListener = new OnClickListener()
  {
    @Override
    public void onClick(View v)
    {

      ImageButton emergency_stopButton = (ImageButton)findViewById(R.id.button_stop);
      if (emergency_stop_mode==1)
      {
        emergency_stop_mode = 0;
        callPublishStopStart(emergency_stop_mode);
        emergency_stopButton.setImageResource(R.drawable.emergency_stop_inactive);
      }
      else
      {
        emergency_stop_mode=1;
        callPublishStopStart(emergency_stop_mode);
        emergency_stopButton.setImageResource(R.drawable.emergency_stop_active);
        SeekBar speedBar1  = (SeekBar)findViewById(R.id.seekBar_speed);
        speedBar1.setProgress(1000);

      }
    }

  };



  private final OnSeekBarChangeListener speedBarListener = new OnSeekBarChangeListener()
  {
    short lastProgress = 0;
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
    {

      lastProgress = (short) (seekBar.getMax()/2-progress);
      callPublishSpeed(lastProgress);
      if (lastToast == null)
        lastToast = Toast.makeText(getBaseContext(), -lastProgress + " deg/s", Toast.LENGTH_SHORT);
      else
        lastToast.setText(-lastProgress + " deg/s");

      lastToast.show();
    }

    @Override public void onStartTrackingTouch(SeekBar seekBar) { }
    @Override public void onStopTrackingTouch(SeekBar seekBar) {
      callPublishSpeed(lastProgress);}
  };


  // This function registers sensor listeners for the accelerometer, magnetometer and gyroscope.
  public void initListeners(){
    mSensorManager.registerListener(this,
            mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_FASTEST);

    mSensorManager.registerListener(this,
            mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
            SensorManager.SENSOR_DELAY_FASTEST);

    mSensorManager.registerListener(this,
            mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
            SensorManager.SENSOR_DELAY_FASTEST);
    mSensorManager.registerListener(this,
            mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
            SensorManager.SENSOR_DELAY_FASTEST);
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    switch(event.sensor.getType()) {
      case Sensor.TYPE_ACCELEROMETER:
        // copy new accelerometer data into accel array and calculate orientation
        System.arraycopy(event.values, 0, accel, 0, 3);
        calculateAccMagOrientation();
        break;

      case Sensor.TYPE_GYROSCOPE:
        // process gyro data
        gyroFunction(event);
        break;

      case Sensor.TYPE_MAGNETIC_FIELD:
        // copy new magnetometer data into magnet array
        System.arraycopy(event.values, 0, magnet, 0, 3);
        break;
      case Sensor.TYPE_ROTATION_VECTOR:
        // copy new magnetometer data into magnet array
        System.arraycopy(event.values, 0, rotation, 0, 3);
        break;
    }
  }

  // calculates orientation angles from accelerometer and magnetometer output
  public void calculateAccMagOrientation() {
    if(SensorManager.getRotationMatrix(rotationMatrix, null, accel, magnet)) {
      SensorManager.getOrientation(rotationMatrix, accMagOrientation);
    }
  }

  // This function is borrowed from the Android reference
  // at http://developer.android.com/reference/android/hardware/SensorEvent.html#values
  // It calculates a rotation vector from the gyroscope angular speed values.
  private void getRotationVectorFromGyro(float[] gyroValues,
                                         float[] deltaRotationVector,
                                         float timeFactor)
  {
    float[] normValues = new float[3];

    // Calculate the angular speed of the sample
    float omegaMagnitude =
            (float)Math.sqrt(gyroValues[0] * gyroValues[0] +
                    gyroValues[1] * gyroValues[1] +
                    gyroValues[2] * gyroValues[2]);

    // Normalize the rotation vector if it's big enough to get the axis
    if(omegaMagnitude > EPSILON) {
      normValues[0] = gyroValues[0] / omegaMagnitude;
      normValues[1] = gyroValues[1] / omegaMagnitude;
      normValues[2] = gyroValues[2] / omegaMagnitude;
    }

    // Integrate around this axis with the angular speed by the timestep
    // in order to get a delta rotation from this sample over the timestep
    // We will convert this axis-angle representation of the delta rotation
    // into a quaternion before turning it into the rotation matrix.
    float thetaOverTwo = omegaMagnitude * timeFactor;
    float sinThetaOverTwo = (float)Math.sin(thetaOverTwo);
    float cosThetaOverTwo = (float)Math.cos(thetaOverTwo);
    deltaRotationVector[0] = sinThetaOverTwo * normValues[0];
    deltaRotationVector[1] = sinThetaOverTwo * normValues[1];
    deltaRotationVector[2] = sinThetaOverTwo * normValues[2];
    deltaRotationVector[3] = cosThetaOverTwo;
  }

  // This function performs the integration of the gyroscope data.
  // It writes the gyroscope based orientation into gyroOrientation.
  public void gyroFunction(SensorEvent event) {
    // don't start until first accelerometer/magnetometer orientation has been acquired
    if (accMagOrientation == null)
      return;

    // initialisation of the gyroscope based rotation matrix
    if(initState) {
      float[] initMatrix = new float[9];
      initMatrix = getRotationMatrixFromOrientation(accMagOrientation);
      float[] test = new float[3];
      SensorManager.getOrientation(initMatrix, test);
      gyroMatrix = matrixMultiplication(gyroMatrix, initMatrix);
      initState = false;
    }

    // copy the new gyro values into the gyro array
    // convert the raw gyro data into a rotation vector
    float[] deltaVector = new float[4];
    if(timestamp != 0) {
      final float dT = (event.timestamp - timestamp) * NS2S;
      System.arraycopy(event.values, 0, gyro, 0, 3);
      getRotationVectorFromGyro(gyro, deltaVector, dT / 2.0f);
    }

    // measurement done, save current time for next interval
    timestamp = event.timestamp;

    // convert rotation vector into rotation matrix
    float[] deltaMatrix = new float[9];
    SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);

    // apply the new rotation interval on the gyroscope based rotation matrix
    gyroMatrix = matrixMultiplication(gyroMatrix, deltaMatrix);

    // get the gyroscope based orientation from the rotation matrix
    SensorManager.getOrientation(gyroMatrix, gyroOrientation);
  }

  private float[] getRotationMatrixFromOrientation(float[] o) {
    float[] xM = new float[9];
    float[] yM = new float[9];
    float[] zM = new float[9];

    float sinX = (float)Math.sin(o[1]);
    float cosX = (float)Math.cos(o[1]);
    float sinY = (float)Math.sin(o[2]);
    float cosY = (float)Math.cos(o[2]);
    float sinZ = (float)Math.sin(o[0]);
    float cosZ = (float)Math.cos(o[0]);

    // rotation about x-axis (pitch)
    xM[0] = 1.0f; xM[1] = 0.0f; xM[2] = 0.0f;
    xM[3] = 0.0f; xM[4] = cosX; xM[5] = sinX;
    xM[6] = 0.0f; xM[7] = -sinX; xM[8] = cosX;

    // rotation about y-axis (roll)
    yM[0] = cosY; yM[1] = 0.0f; yM[2] = sinY;
    yM[3] = 0.0f; yM[4] = 1.0f; yM[5] = 0.0f;
    yM[6] = -sinY; yM[7] = 0.0f; yM[8] = cosY;

    // rotation about z-axis (azimuth)
    zM[0] = cosZ; zM[1] = sinZ; zM[2] = 0.0f;
    zM[3] = -sinZ; zM[4] = cosZ; zM[5] = 0.0f;
    zM[6] = 0.0f; zM[7] = 0.0f; zM[8] = 1.0f;

    // rotation order is y, x, z (roll, pitch, azimuth)
    float[] resultMatrix = matrixMultiplication(xM, yM);
    resultMatrix = matrixMultiplication(zM, resultMatrix);
    return resultMatrix;
  }

  private float[] matrixMultiplication(float[] A, float[] B) {
    float[] result = new float[9];

    result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6];
    result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7];
    result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8];

    result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6];
    result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7];
    result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8];

    result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6];
    result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7];
    result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8];

    return result;
  }

  class calculateFusedOrientationTask extends TimerTask {
    public void run() {
      float oneMinusCoeff = 1.0f - FILTER_COEFFICIENT;

            /*
             * Fix for 179� <--> -179� transition problem:
             * Check whether one of the two orientation angles (gyro or accMag) is negative while the other one is positive.
             * If so, add 360� (2 * math.PI) to the negative value, perform the sensor fusion, and remove the 360� from the result
             * if it is greater than 180�. This stabilizes the output in positive-to-negative-transition cases.
             */

      // azimuth
      if (gyroOrientation[0] < -0.5 * Math.PI && accMagOrientation[0] > 0.0) {
        fusedOrientation[0] = (float) (FILTER_COEFFICIENT * (gyroOrientation[0] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[0]);
        fusedOrientation[0] -= (fusedOrientation[0] > Math.PI) ? 2.0 * Math.PI : 0;
      }
      else if (accMagOrientation[0] < -0.5 * Math.PI && gyroOrientation[0] > 0.0) {
        fusedOrientation[0] = (float) (FILTER_COEFFICIENT * gyroOrientation[0] + oneMinusCoeff * (accMagOrientation[0] + 2.0 * Math.PI));
        fusedOrientation[0] -= (fusedOrientation[0] > Math.PI)? 2.0 * Math.PI : 0;
      }
      else {
        fusedOrientation[0] = FILTER_COEFFICIENT * gyroOrientation[0] + oneMinusCoeff * accMagOrientation[0];
      }

      // pitch
      if (gyroOrientation[1] < -0.5 * Math.PI && accMagOrientation[1] > 0.0) {
        fusedOrientation[1] = (float) (FILTER_COEFFICIENT * (gyroOrientation[1] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[1]);
        fusedOrientation[1] -= (fusedOrientation[1] > Math.PI) ? 2.0 * Math.PI : 0;
      }
      else if (accMagOrientation[1] < -0.5 * Math.PI && gyroOrientation[1] > 0.0) {
        fusedOrientation[1] = (float) (FILTER_COEFFICIENT * gyroOrientation[1] + oneMinusCoeff * (accMagOrientation[1] + 2.0 * Math.PI));
        fusedOrientation[1] -= (fusedOrientation[1] > Math.PI)? 2.0 * Math.PI : 0;
      }
      else {
        fusedOrientation[1] = FILTER_COEFFICIENT * gyroOrientation[1] + oneMinusCoeff * accMagOrientation[1];
      }

      // roll
      if (gyroOrientation[2] < -0.5 * Math.PI && accMagOrientation[2] > 0.0) {
        fusedOrientation[2] = (float) (FILTER_COEFFICIENT * (gyroOrientation[2] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[2]);
        fusedOrientation[2] -= (fusedOrientation[2] > Math.PI) ? 2.0 * Math.PI : 0;
      }
      else if (accMagOrientation[2] < -0.5 * Math.PI && gyroOrientation[2] > 0.0) {
        fusedOrientation[2] = (float) (FILTER_COEFFICIENT * gyroOrientation[2] + oneMinusCoeff * (accMagOrientation[2] + 2.0 * Math.PI));
        fusedOrientation[2] -= (fusedOrientation[2] > Math.PI)? 2.0 * Math.PI : 0;
      }
      else {
        fusedOrientation[2] = FILTER_COEFFICIENT * gyroOrientation[2] + oneMinusCoeff * accMagOrientation[2];
      }

      // overwrite gyro matrix and orientation with fused orientation
      // to comensate gyro drift
      gyroMatrix = getRotationMatrixFromOrientation(fusedOrientation);
      System.arraycopy(fusedOrientation, 0, gyroOrientation, 0, 3);


      // update sensor output in GUI
      //mHandler.post(updateOreintationDisplayTask);
    }
  }

}
