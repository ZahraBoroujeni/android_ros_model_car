package com.github.turtlebot.turtlebot_android.freieCar;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import com.github.rosjava.android_remocons.common_tools.apps.RosAppActivity;
import com.github.turtlebot.turtlebot_android.freieCar.view.ViewPagerAdapter;

import org.ros.address.InetAddressFactory;
import org.ros.android.MessageCallable;
import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.namespace.NameResolver;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMain;
import org.ros.node.NodeMainExecutor;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;

import java.util.Timer;
import java.util.logging.Handler;

import sensor_msgs.CompressedImage;


public class FreieCarActivity extends RosAppActivity implements NodeMain {

    private RelativeLayout layoutControl;
    private LinearLayout layoutGPS;
    private ImageView imageViewGPS;

    /**
     * The number of pages (wizard steps) to show in this demo.
     */
    private static final int NUM_PAGES = 2;
    /**
     * The pager widget, which handles animation and allows swiping horizontally to access previous
     * and next wizard steps.
     */
    private ViewPager mPager;

    /**
     * The pager adapter, which provides the pages to the view pager widget.
     */
    private PagerAdapter mPagerAdapter;

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

    private boolean first_time = true;
    private short first_head = 0;
    private short stop = 1;
    // The following members are only for displaying the sensor output.
    public Handler mHandler;


    public FreieCarActivity() {
        super("FreieCarActivity", "FreieCarActivity");
    }

    /************************************************************
     Android code:
     Activity life cycle and GUI management
     ************************************************************/

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        setDefaultMasterName(getString(R.string.default_robot));
        setDefaultAppName(getString(R.string.default_app));
        setDashboardResource(R.id.top_bar);
        setMainWindowResource(R.layout.sliding_layout);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.sliding_layout);

        // Instantiate a ViewPager and a PagerAdapter.
        mPager = (ViewPager) findViewById(R.id.pager);
        mPagerAdapter = new ViewPagerAdapter(getFragmentManager());
        mPager.setAdapter(mPagerAdapter);

        // buildView(false);

        // GUI stuff
        // mHandler = new Handler();

        // TODO Tricky solution to the StrictMode; the recommended way is by using AsyncTask
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

    }

    @Override
    protected void onStop() {
        callPublishStopStart(stop);//emergency stop active
        super.onStop();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // TODO this is not called now, so we cannot flip the screen
        Log.e("FreieCarActivity", "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);

        // buildView(true);
    }

    /************************************************************
     * ROS code:
     * NodeMain implementation and service call code
     ************************************************************/

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        super.init(nodeMainExecutor);

        NodeConfiguration nodeConfiguration =
                NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress(), getMasterUri());

        nodeMainExecutor.execute(this, nodeConfiguration.setNodeName("android/video_view"));
    }


    public void callPublishSpeed(short mode) {
        if (node == null) {
            Log.e("FreieCarActivity", "Still doesn't have a connected node");
            return;
        }
        final Publisher<std_msgs.Int16> speed_pub =
                node.newPublisher("/manual_control/speed", std_msgs.Int16._TYPE);

        std_msgs.Int16 gas = speed_pub.newMessage();
        gas.setData(mode);
        speed_pub.publish(gas);
    }

    public void callPublishSteering(short mode) {
        if (node == null) {
            Log.e("FreieCarActivity", "Still doesn't have a connected node");
            return;
        }
        final Publisher<std_msgs.Int16> steering_pub =
                node.newPublisher("/manual_control/steering", std_msgs.Int16._TYPE);

        std_msgs.Int16 steering = steering_pub.newMessage();
        steering.setData(mode);
        steering_pub.publish(steering);
    }

    public void callPublishStopStart(short mode) {
        if (node == null) {
            Log.e("FreieCarActivity", "Still doesn't have a connected node");
            return;
        }

        final Publisher<std_msgs.Int16> stop_pub =
                node.newPublisher("/manual_control/stop_start", std_msgs.Int16._TYPE);
        std_msgs.Int16 stop_msg = stop_pub.newMessage();
        stop_msg.setData(mode);
        stop_pub.publish(stop_msg);
    }

    public void setLayoutControl(RelativeLayout layoutControl) {
        this.layoutControl = layoutControl;
    }

    public void setLayoutGPS(LinearLayout layoutGPS, ImageView imageViewGPS) {
        this.layoutGPS = layoutGPS;
        this.imageViewGPS = imageViewGPS;
    }


    @Override
    public void onStart(ConnectedNode connectedNode) {
        Log.e("FreieCarActivity", connectedNode.getName() + " node started");
        node = connectedNode;

        NameResolver appNameSpace = getMasterNameSpace();
        String panoImgTopic = appNameSpace.resolve("/camera/rgb/image_raw/compressed").toString();

        Subscriber<sensor_msgs.CompressedImage> subscriber = connectedNode.newSubscriber(panoImgTopic, sensor_msgs.CompressedImage._TYPE);
        subscriber.addMessageListener(new MessageListener<CompressedImage>() {
            @Override
            public void onNewMessage(final sensor_msgs.CompressedImage message) {
                layoutControl.post(new Runnable() {
                    @Override
                    public void run() {
                        BitmapDrawable ob = new BitmapDrawable(getResources(), callable.call(message));
                        layoutControl.setBackground(ob);
                    }
                });
                layoutControl.postInvalidate();
            }
        });

        String gpsImgTopic = appNameSpace.resolve("/camera/rgb/image_raw/compressed").toString();

        Subscriber<sensor_msgs.CompressedImage> subscriberGpsImg = connectedNode.newSubscriber(panoImgTopic, sensor_msgs.CompressedImage._TYPE);
        subscriberGpsImg.addMessageListener(new MessageListener<CompressedImage>() {
            @Override
            public void onNewMessage(final sensor_msgs.CompressedImage message) {
                layoutGPS.post(new Runnable() {
                    @Override
                    public void run() {
                        BitmapDrawable ob = new BitmapDrawable(getResources(), callable.call(message));
                        layoutGPS.setBackground(ob);
                    }
                });
                layoutGPS.postInvalidate();
            }
        });


        // TODO subscribe to transformation msgs and convert position for marker in image
//        String gpsTfTopic = appNameSpace.resolve("/camera/rgb/image_raw/compressed").toString();
//
//        Subscriber<sensor_msgs.CompressedImage> subscriberGpsTf = connectedNode.newSubscriber(panoImgTopic, sensor_msgs.CompressedImage._TYPE);
//        subscriberGpsTf.addMessageListener(new MessageListener<CompressedImage>() {
//            @Override
//            public void onNewMessage(final sensor_msgs.CompressedImage message) {
//                imageViewGPS.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        BitmapDrawable ob = new BitmapDrawable(getResources(), callable.call(message));
//
//                    }
//                });
//                imageViewGPS.postInvalidate();
//            }
//        });

        callPublishStopStart(stop);//emergency stop active
        SeekBar speedBar1 = (SeekBar) findViewById(R.id.seekBar_speed);
        speedBar1.setProgress(1000);

    }

    public void dummyDrawMarker(int x, int y) {
        Drawable d = ContextCompat.getDrawable(this, R.drawable.kitchen);
        Log.d("dummyDrawMarker",d.getIntrinsicWidth()+"");

        Bitmap tempBitmap = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(), Bitmap.Config.RGB_565);
        Canvas tempCanvas = new Canvas(tempBitmap);
        Paint myPaint = new Paint();
        myPaint.setColor(Color.rgb(200, 0, 0));
        myPaint.setStrokeWidth(10);
        tempCanvas.drawRoundRect(new RectF(x, y, 20, 20), 2, 2, myPaint);
        d.draw(tempCanvas);
        imageViewGPS.setImageDrawable(d);
    }

    @Override
    public void onError(Node n, Throwable e) {
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
    public GraphName getDefaultNodeName() {
        return GraphName.of("android/freieCar");
    }


}
