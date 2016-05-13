package autoauto.github.com.modelcarlocalization.view;

import android.app.Fragment;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import autoauto.github.com.modelcarlocalization.MainActivity;
import autoauto.github.com.modelcarlocalization.R;

/**
 * Created by Daniel Neumann on 13.05.16.
 */
public class CameraFragment extends Fragment {

    private RelativeLayout layout;
    private MainActivity mainActivity;


    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.visual_gps_camera, container, false);
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();

        mainActivity = (MainActivity) getActivity();

        LinearLayout layout = (LinearLayout) getView().findViewById(R.id.gps_camera);
        mainActivity.setLayoutGPS(layout, null);
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // TODO this is not called now, so we cannot flip the screen
        super.onConfigurationChanged(newConfig);
    }

}