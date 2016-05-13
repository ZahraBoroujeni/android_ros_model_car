package autoauto.github.com.modelcarlocalization;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.github.rosjava.android_remocons.common_tools.apps.RosAppActivity;

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
import org.ros.node.topic.Subscriber;

import autoauto.github.com.modelcarlocalization.view.ViewPagerAdapter;
import sensor_msgs.CompressedImage;

public class MainActivity extends RosAppActivity implements NodeMain {

    private ConnectedNode node;
    private final MessageCallable<Bitmap, sensor_msgs.CompressedImage> callable = new ScaledBitmapFromCompressedImage(2);
    private LinearLayout layoutGPS;
    private ImageView imageViewGPS;

    public static final int MAP_ID = R.drawable.map;
    public static final int CAR_MARKER_ID = R.drawable.model_car_marker;
    public static final int MAP_X_WIDTH_CENTIMETER = 500;
    public static final int MAP_Y_HEIGHT_CENTIMETER = 700;

    /**
     * The pager widget, which handles animation and allows swiping horizontally to access previous
     * and next wizard steps.
     */
    private ViewPager mPager;

    /**
     * The pager adapter, which provides the pages to the view pager widget.
     */
    private PagerAdapter mPagerAdapter;

    public MainActivity() {
        super("ModelCarLocalization", "ModelCarLocalization");
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
    public GraphName getDefaultNodeName() {
        return GraphName.of("android/modelCarLocalization");
    }

    @Override
    public void onStart(ConnectedNode connectedNode) {
        node = connectedNode;

        NameResolver appNameSpace = getMasterNameSpace();

        String gpsImgTopic = appNameSpace.resolve("/app/usb_cam/image_raw/compressed").toString();

        Subscriber<sensor_msgs.CompressedImage> subscriberGpsImg = connectedNode.newSubscriber(gpsImgTopic, sensor_msgs.CompressedImage._TYPE);
        subscriberGpsImg.addMessageListener(new MessageListener<CompressedImage>() {
            @Override
            public void onNewMessage(final sensor_msgs.CompressedImage message) {

                if (layoutGPS != null) {
                    layoutGPS.post(new Runnable() {
                        @Override
                        public void run() {
                            BitmapDrawable ob = new BitmapDrawable(getResources(), callable.call(message));
                            if (layoutGPS != null) {
                                layoutGPS.setBackground(ob);
                            }
                        }
                    });
                    layoutGPS.postInvalidate();
                }

            }
        });


        String gpsTransformTopic = appNameSpace.resolve("/app/Transform").toString();

        Subscriber<geometry_msgs.Transform> subscriberGpsTf = connectedNode.newSubscriber(gpsTransformTopic, geometry_msgs.Transform._TYPE);
        subscriberGpsTf.addMessageListener(new MessageListener<geometry_msgs.Transform>() {
            @Override
            public void onNewMessage(final geometry_msgs.Transform message) {

                if (imageViewGPS != null) {
                    imageViewGPS.post(new Runnable() {
                        @Override
                        public void run() {
                            geometry_msgs.Vector3 translation = message.getTranslation();
                            geometry_msgs.Quaternion rotation = message.getRotation();

                            // rotation for z-axis = atan2(2*(qw*qz+qx*qy),1-2*(qx^2+qy^2))
                            double qw = rotation.getW();
                            double qx = rotation.getX();
                            double qy = rotation.getY();
                            double qz = rotation.getZ();

                            double rotation_angle = Math.atan2(2 * (qw * qz + qx * qy), 1 - 2 * (qy * qy + qz * qz));
                            double degree = rotation_angle * 180.0 / Math.PI;
                            drawMarker(translation.getX(), translation.getY(), degree);
                        }
                    });
                    imageViewGPS.postInvalidate();
                }

            }
        });

    }

    @Override
    protected void onStop() {
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
    public void onShutdown(Node node) {

    }

    @Override
    public void onShutdownComplete(Node node) {

    }

    @Override
    public void onError(Node node, Throwable throwable) {

    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        super.init(nodeMainExecutor);

        NodeConfiguration nodeConfiguration =
                NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress(), getMasterUri());

        nodeMainExecutor.execute(this, nodeConfiguration.setNodeName("android/video_view"));
    }

    public void setLayoutGPS(LinearLayout layout, ImageView imageView) {
        this.layoutGPS = layout;
        this.imageViewGPS = imageView;
    }

    /**
     * @param x              in centimeter
     * @param y              in centimeter
     * @param rotation_angle in degree
     */
    public void drawMarker(double x, double y, double rotation_angle) {
        Drawable map = ContextCompat.getDrawable(this, MAP_ID);
        Drawable marker = ContextCompat.getDrawable(this, CAR_MARKER_ID);

        // translation x in pixel = x_centimeter * image_width_pixel / map_width_centimeter
        float xf = map.getIntrinsicWidth() * (float) x / (float) MAP_X_WIDTH_CENTIMETER;
        // translation y in pixel = y_centimeter * image_height_pixel / map_height_centimeter
        float yf = map.getIntrinsicHeight() * (float) y / (float) MAP_Y_HEIGHT_CENTIMETER;
        float markerSize = map.getIntrinsicWidth() * 0.1f;   // size 10% of image width

        Bitmap tempBitmap = Bitmap.createBitmap(map.getIntrinsicWidth(), map.getIntrinsicHeight(), Bitmap.Config.ARGB_4444);
        Canvas tempCanvas = new Canvas(tempBitmap);

        map.setBounds(0, 0, map.getIntrinsicWidth(), map.getIntrinsicHeight());
        map.draw(tempCanvas);

        int markerSize_half = (int) (markerSize / 2.0f);
        marker.setBounds((int) xf - markerSize_half, (int) yf - markerSize_half,
                (int) xf + markerSize_half, (int) yf + markerSize_half);

        tempCanvas.save();
        tempCanvas.rotate((float) rotation_angle, xf, yf);
        marker.draw(tempCanvas);
        tempCanvas.restore();

        if (imageViewGPS != null) {
            imageViewGPS.setImageBitmap(tempBitmap);
        }
    }
}
