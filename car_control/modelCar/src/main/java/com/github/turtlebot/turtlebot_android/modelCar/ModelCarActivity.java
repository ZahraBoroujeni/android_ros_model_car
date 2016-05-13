package com.github.turtlebot.turtlebot_android.modelCar;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import com.github.rosjava.android_remocons.common_tools.apps.RosAppActivity;
import com.github.turtlebot.turtlebot_android.modelCar.view.ViewPagerAdapter;

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

import sensor_msgs.CompressedImage;


public class ModelCarActivity extends RosAppActivity implements NodeMain {

    private RelativeLayout layoutControl;
    Publisher<std_msgs.Int16> speed_pub;
    Publisher<std_msgs.Int16> steering_pub;
    Publisher<std_msgs.Int16> stop_pub;
    Publisher<std_msgs.String> blinker_light_pub;

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
    private short stop = 1;

    public ModelCarActivity() {
        super("ModelCarActivity", "ModelCarActivity");
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
        Log.e("ModelCarActivity", "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
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
            Log.e("ModelCarActivity", "Still doesn't have a connected node");
            return;
        }

        std_msgs.Int16 gas = speed_pub.newMessage();
        gas.setData(mode);
        speed_pub.publish(gas);
    }

    public void callPublishSteering(short mode) {
        if (node == null) {
            Log.e("ModelCarActivity", "Still doesn't have a connected node");
            return;
        }

        std_msgs.Int16 steering = steering_pub.newMessage();
        steering.setData(mode);
        steering_pub.publish(steering);
    }

    public void callPublishStopStart(short mode) {
        if (node == null) {
            Log.e("ModelCarActivity", "Still doesn't have a connected node");
            return;
        }

        std_msgs.Int16 stop_msg = stop_pub.newMessage();
        stop_msg.setData(mode);
        stop_pub.publish(stop_msg);
    }

    public void callPublishBlinkerLight(String mode) {
        if (node == null) {
            Log.e("ModelCarActivity", "Still doesn't have a connected node");
            return;
        }

        std_msgs.String blinker_msg = blinker_light_pub.newMessage();
        blinker_msg.setData(mode);
        blinker_light_pub.publish(blinker_msg);

        // Log.e("ModelCarActivity", mode);
    }

    public void setLayoutControl(RelativeLayout layoutControl) {
        this.layoutControl = layoutControl;
    }


    @Override
    public void onStart(ConnectedNode connectedNode) {
        Log.e("ModelCarActivity", connectedNode.getName() + " node started");
        node = connectedNode;

        NameResolver appNameSpace = getMasterNameSpace();
        String panoImgTopic = appNameSpace.resolve("/app/camera/rgb/image_raw/compressed").toString();

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

        speed_pub = node.newPublisher("/manual_control/speed", std_msgs.Int16._TYPE);
        steering_pub = node.newPublisher("/manual_control/steering", std_msgs.Int16._TYPE);
        stop_pub = node.newPublisher("/manual_control/stop_start", std_msgs.Int16._TYPE);
        blinker_light_pub = node.newPublisher("/manual_control/lights", std_msgs.String._TYPE);

        callPublishStopStart(stop);//emergency stop active
        SeekBar speedBar1 = (SeekBar) findViewById(R.id.seekBar_speed);
        speedBar1.setProgress(1000);

    }

    @Override
    public void onError(Node n, Throwable e) {
        Log.d("ModelCarActivity", n.getName() + " node error: " + e.getMessage());
    }

    @Override
    public void onShutdown(Node n) {
        Log.d("ModelCarActivity", n.getName() + " node shuting down...");
        callPublishStopStart(stop);//emergency stop active
    }

    @Override
    public void onShutdownComplete(Node n) {
        Log.d("ModelCarActivity", n.getName() + " node shutdown completed");
    }

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("android/freieCar");
    }


}
