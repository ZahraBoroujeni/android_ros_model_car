package autoauto.github.com.car_status;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private Handler handler;

    // Repeat the runnable code block every 20 seconds
    private final int REFRESH_DELAY = 20000;

    private SSHConnection connection;

    private final String CHECK_ROS_CORE = "ps -e | grep rosmaster";
    private final String CHECK_FT4232H_BOARD = "lsusb | grep FT4232H";
    private final String CHECK_ARDUINO = "ls /dev/ttyUSB3";
    private final String CHECK_MOTOR = "ls /dev/ttyUSB2";
    private final String CHECK_LIDAR = "ls /dev/ttyUSB0";
    private final String CHECK_CAMERA_REALSENSE = "v4l2-ctl --list-devices | grep RealSense";
    private final String CHECK_CAMERA_TOP = "v4l2-ctl --list-devices | grep 'USB 2.0 Camera'";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.status);

        Log.e("car_status", "onCreate");

        connection = new SSHConnection("root", "elfmeter", "192.168.43.102", 22);

        Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText editText = (EditText) findViewById(R.id.editText);
                String command = editText.getText().toString();
                new SendSshTask().execute(command);
            }
        });

        handler = new Handler();
        handler.post(runnableCode);
    }

    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            Log.d("car_status", "refreshing...");

            sendAll();

            handler.postDelayed(runnableCode, REFRESH_DELAY);
        }
    };

    private void sendAll() {

        new SendSshTask().execute(CHECK_ROS_CORE, "" + R.id.status_ros);
        new SendSshTask().execute(CHECK_FT4232H_BOARD, "" + R.id.status_FT4232H_board);
        new SendSshTask().execute(CHECK_ARDUINO, "" + R.id.status_arduino_board);
        new SendSshTask().execute(CHECK_MOTOR, "" + R.id.status_motor);
        new SendSshTask().execute(CHECK_LIDAR, "" + R.id.status_lidar);
        new SendSshTask().execute(CHECK_CAMERA_REALSENSE, "" + R.id.status_front_camera);
        new SendSshTask().execute(CHECK_CAMERA_TOP, "" + R.id.status_top_camera);

    }

    private void updateTextView(boolean result, int textViewId) {
        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.status_layout);
        TextView textView = (TextView) findViewById(textViewId);
        TextView textViewError = (TextView) findViewById(R.id.status_error);

        textViewError.setText("");
        textViewError.requestLayout();

        String text = "NO";
        int color = ContextCompat.getColor(this.getApplicationContext(), R.color.colorNO);

        if (result) {
            text = "YES";
            color = ContextCompat.getColor(this.getApplicationContext(), R.color.colorYES);
        }

        textView.setText(text);
        textView.setTextColor(color);
        textView.requestLayout();

        linearLayout.requestLayout();
    }


    class SendSshTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... commands) {
            try {

                String command = commands[0];


                Log.d("car_status", "sending '" + command + "' ...");

                String result = "";
                result += connection.execCMD(command);

                Log.d("car_status", result);

                if (result.startsWith("Could not connect")) {

                    uiUpdateError();

                } else if (commands.length > 1) {

                    Integer textViewId = Integer.parseInt(commands[1]);
                    if (result.length() > 0 && !result.startsWith("ls: cannot access")) {
                        uiUpdate(true, textViewId);
                    } else {
                        uiUpdate(false, textViewId);
                    }

                }

            } catch (Exception e) {
            }

            return null;
        }

        private void uiUpdate(final boolean result, final int textViewId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateTextView(result, textViewId);
                }
            });
        }

        private void uiUpdateError() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView textView = (TextView) findViewById(R.id.status_error);
                    textView.setText("No connection to Odroid!");
                    textView.requestLayout();

                    LinearLayout linearLayout = (LinearLayout) findViewById(R.id.status_layout);
                    linearLayout.requestLayout();
                }
            });
        }
    }
}
