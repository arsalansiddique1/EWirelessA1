package com.openpositioning.PositionMe.fragments;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.common.collect.Maps;
import com.openpositioning.PositionMe.R;
import com.openpositioning.PositionMe.sensors.SensorFusion;
import com.openpositioning.PositionMe.sensors.SensorTypes;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;

public class MapsFragment extends Fragment {
    private GoogleMap mMap;
    private List<LatLng> pathPoints = new ArrayList<>();
    private Polyline currentPath;
    private Marker userMarker;
    private SensorFusion sensorFusion =  SensorFusion.getInstance();
    private Handler handler = new Handler();
    float[] startPosition = sensorFusion.getGNSSLatitude(true);
    private Marker orientationMarker;
    //Buttons to end PDR recording
    private Button stopButton;
    private Button cancelButton;
    //buttons to change map view types
    private Button satelliteButton;
    private Button normalButton;
    //Zoom of google maps
    private float zoom = 19f;

    private CountDownTimer autoStop;


    private final OnMapReadyCallback callback = new OnMapReadyCallback() {

        /**
         * Manipulates the map once available.
         * This callback is triggered when the map is ready to be used.
         * This is where we can add markers or lines, add listeners or move the camera.
         * In this case, we just add a marker near Sydney, Australia.
         * If Google Play services is not installed on the device, the user will be prompted to
         * install it inside the SupportMapFragment. This method will only be triggered once the
         * user has installed Google Play services and returned to the app.
         */
        @SuppressLint("MissingPermission")
        @Override
        public void onMapReady(GoogleMap googleMap) {
            mMap = googleMap;
            LatLng start = new LatLng(startPosition[0], startPosition[1]);
            googleMap.addMarker(new MarkerOptions().position(start).title("Start Position"));
            googleMap.moveCamera(CameraUpdateFactory.newLatLng(start));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(start, zoom ));
            mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
            mMap.getUiSettings().setCompassEnabled(true);
            mMap.getUiSettings().setRotateGesturesEnabled(true);
            mMap.getUiSettings().setScrollGesturesEnabled(true);
            mMap.getUiSettings().setTiltGesturesEnabled(true);
            //Shows GNSS position using google maps API.
            mMap.setMyLocationEnabled(true);
            //Starts displaying live path and orientation once map is ready.
            handler.post(pathUpdater);
        }
    };

    private void addOrientationMarker(LatLng position, float rotation) {
        if (mMap != null) {
            if (orientationMarker == null) {
                orientationMarker = mMap.addMarker(new MarkerOptions()
                        .position(position)
                        .icon(bitmapDescriptorFromVector(getContext(), R.drawable.ic_baseline_navigation_24)) // Custom icon for orientation
                        .anchor(0.5f, 0.5f)); // Center the icon on the position
            }
            orientationMarker.setPosition(position);
            orientationMarker.setRotation(rotation); // Rotation angle in degrees
        }
    }

    //function used to convert the compass icon to a bitmap so it can be used as a marker on maps.
    private BitmapDescriptor bitmapDescriptorFromVector(Context context, @DrawableRes int vectorDrawableResourceId) {
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorDrawableResourceId);
        vectorDrawable.setBounds(0, 0, vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }


        private final Runnable pathUpdater = new Runnable() {
            @Override
            public void run() {
                updatePath();
                Log.d("MapsFragment", "updatePath called");
                handler.postDelayed(this, 1000); // Update path every second
            }
        };
    private void updatePath() {
        // Fetch the latest PDR position (you'll need to implement getPDRPosition() or similar)
        float[] pdrValues = sensorFusion.getSensorValueMap().get(SensorTypes.PDR);
        if (pdrValues != null) {
            // Convert PDR position to LatLng.
            LatLng newPosition = convertPDRtoLatLng(startPosition[0], startPosition[1], pdrValues);
            Log.d("MapsFragment", "Adding new position: " + newPosition.toString());
            //Used to draw the path based on current PDR values.
            if (currentPath == null) {
                PolylineOptions polylineOptions = new PolylineOptions().add(newPosition);
                currentPath = mMap.addPolyline(polylineOptions);
            } else {
                java.util.List<LatLng> points = currentPath.getPoints();
                points.add(newPosition);
                currentPath.setPoints(points);
            }
            //adds the current orientation marker each time the path gets updated
            float newRotation = (float) Math.toDegrees(sensorFusion.passOrientation());
            Log.d("MapsFragment", "Updating orientation to: " + newRotation);
            addOrientationMarker(newPosition, newRotation);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mMap != null) { // Ensure the map is ready
            handler.postDelayed(pathUpdater, 1000);  // Start or resume path updates
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(pathUpdater); // Stop path updates to conserve resources
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && pathUpdater != null) {
            handler.removeCallbacks(pathUpdater);
        }
    }
    private LatLng convertPDRtoLatLng(float startX, float startY, float[] pdrPosition) {
        final double EarthRadius = 6378137; // Radius in meters
        double deltaLatitude = pdrPosition[0] / EarthRadius;
        double deltaLongitude = pdrPosition[1] / (EarthRadius * Math.cos(Math.PI * startY / 180));
        double newLatitude = startX + deltaLatitude * 180 / Math.PI;
        double newLongitude = startY + deltaLongitude * 180 / Math.PI;
        return new LatLng(newLatitude, newLongitude);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_maps, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(callback);
        }
        // Stop button to save trajectory and move to corrections
        this.autoStop = null;
        this.stopButton = getView().findViewById(R.id.stopB);
        this.stopButton.setOnClickListener(new View.OnClickListener() {
            /**
             * {@inheritDoc}
             * OnClick listener for button to go to next fragment.
             * When button clicked the PDR recording is stopped and the {@link CorrectionFragment} is loaded.
             */
            @Override
            public void onClick(View view) {
                if(autoStop != null) autoStop.cancel();
                sensorFusion.stopRecording();
                NavDirections action = MapsFragmentDirections.actionMapsFragmentToCorrectionFragment();
                Navigation.findNavController(view).navigate(action);
            }
        });

        // Cancel button to discard trajectory and return to Home
        this.cancelButton = getView().findViewById(R.id.cancelB);
        this.cancelButton.setOnClickListener(new View.OnClickListener() {
            /**
             * {@inheritDoc}
             * OnClick listener for button to go to home fragment.
             * When button clicked the PDR recording is stopped and the {@link HomeFragment} is loaded.
             * The trajectory is not saved.
             */
            @Override
            public void onClick(View view) {
                sensorFusion.stopRecording();
                NavDirections action = MapsFragmentDirections.actionMapsFragmentToHomeFragment();
                Navigation.findNavController(view).navigate(action);
                if(autoStop != null) autoStop.cancel();
            }
        });

        this.satelliteButton = getView().findViewById(R.id.satellite);
        this.satelliteButton.setOnClickListener(new View.OnClickListener() {
            /**
             * {@inheritDoc}
             * OnClick listener for button to go to switch map view to satellite.
             * When button clicked the map view is change to the satellite view.
             */
            @Override
            public void onClick(View view) {
                mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            }
        });
        this.normalButton = getView().findViewById(R.id.normal);
        this.normalButton.setOnClickListener(new View.OnClickListener() {
            /**
             * {@inheritDoc}
             * OnClick listener for button to go to switch map view to normal.
             * When button clicked the map view is change to the normal view.
             */
            @Override
            public void onClick(View view) {
                mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            }
        });



    }
}