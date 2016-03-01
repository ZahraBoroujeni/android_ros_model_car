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


public class FreieCarActivity extends RosAppActivity implements NodeMain
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
    super.onPause();
  }

  @Override
  protected void onResume()
  {
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
    SeekBar  prevSteeringBar  = null;

    if (rebuild)
    {
      // If we are rebuilding GUI (probably because the screen was rotated) we must save widgets'
      // previous content, as setContentView will destroy and replace them with new instances
      prevSpeedBar  =  (SeekBar)findViewById(R.id.seekBar_speed);
      prevSteeringBar  = (SeekBar)findViewById(R.id.seekBar_steering);
    }

    ImageButton stopButton  = (ImageButton)findViewById(R.id.button_stop);
    stopButton.setOnClickListener(stopButtonListener);

    SeekBar speedBar  = (SeekBar)findViewById(R.id.seekBar_speed);
    speedBar.setOnSeekBarChangeListener(speedBarListener);
    if (rebuild)
      speedBar.setProgress(prevSpeedBar.getProgress());

    SeekBar steeringBar  = (SeekBar)findViewById(R.id.seekBar_steering);
    steeringBar.setOnSeekBarChangeListener(steeringBarListener);
    if (rebuild)
      steeringBar.setProgress(prevSteeringBar.getProgress());


    // Take a reference to the image view to show incoming panoramic pictures
    layout1= (RelativeLayout)findViewById(R.id.RelativeLayout);
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
    callPublishStopStart(stop);//emergency stop active
  }

  @Override
  public void onShutdownComplete(Node n) {
    Log.d("FreieCarActivity", n.getName() + " node shutdown completed");
  }

  @Override
  public GraphName getDefaultNodeName()
  {
    return GraphName.of("android/freieCar");
  }

  /************************************************************
     Android code:
   ************************************************************/



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

  private final OnSeekBarChangeListener steeringBarListener = new OnSeekBarChangeListener()
  {
    short lastProgress = 50;
    short angle=0;
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
    {

      lastProgress = (short) (seekBar.getMax()/2-progress);
      angle= (short) (((seekBar.getMax()-progress)/5)*9);
      callPublishSteering(angle);
      if (lastToast == null)
        lastToast = Toast.makeText(getBaseContext(), lastProgress + " deg", Toast.LENGTH_SHORT);
      else
        lastToast.setText(lastProgress + " deg");

      lastToast.show();
    }

    @Override public void onStartTrackingTouch(SeekBar seekBar) { }
    @Override public void onStopTrackingTouch(SeekBar seekBar) {
      }
  };

}
