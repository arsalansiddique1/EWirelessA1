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
import android.graphics.Color;
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
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;
import com.google.common.collect.Maps;
import com.openpositioning.PositionMe.R;
import com.openpositioning.PositionMe.sensors.SensorFusion;
import com.openpositioning.PositionMe.sensors.SensorTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.util.Log;
import android.widget.TextView;

public class MapsFragment extends Fragment {
    private GoogleMap mMap;
    private List<LatLng> pathPoints = new ArrayList<>();
    private Polyline currentPath;
    private Marker userMarker;
    private SensorFusion sensorFusion =  SensorFusion.getInstance();
    private Handler handler = new Handler();
    float[] startPosition = sensorFusion.getGNSSLatitude(true);
    float[] currGNSSPos;
    private Marker orientationMarker;
    //Buttons to end PDR recording
    private Button stopButton;
    private Button cancelButton;
    //buttons to change map view types
    private Button satelliteButton;
    private Button normalButton;
    //Zoom of google maps
    private float zoom = 19f;

    //Used to store the current latlng pdr position
    LatLng newPosition;

    // Create a new HashMap or similar structure to hold your building names and PolygonOptions
    HashMap<String, PolygonOptions> buildingPolygons = new HashMap<>();

    private GroundOverlay currentGroundOverlay = null; // Field to keep track of the current overlay

    //Used to get distance error between GNSS and PDR.
    private double errDist;
    private TextView errDistT;
    private CountDownTimer autoStop;
    //Used for converting displacements to latitude,longitudes
    static final double earthRadius = 6378137; // Radius in meters


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
            //Loads polygons onto the map for building coordinates defined within the function.
            initializeAndAddPolygons();
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
                //Updates user PDR trajectory and orientation in real time
                updatePath();
                //Invokes a series of functions which overlay indoor floor plan if user is inside a building
                checkUserLocationAndUpdateMap(newPosition);
                handler.postDelayed(this, 500); // Update path and orientation every 0.5 seconds
            }
        };

    //Function is used to plot path, orientation marker and distance error between PDR and GNSS.
    private void updatePath() {
        // Fetch the latest PDR position (you'll need to implement getPDRPosition() or similar)
        float[] pdrValues = sensorFusion.getSensorValueMap().get(SensorTypes.PDR);
        if (pdrValues != null) {
            // Convert PDR position to LatLng.
            newPosition = convertPDRtoLatLng(startPosition[0], startPosition[1], pdrValues);


            //Log.d("MapsFragment", "Adding new position: " + newPosition.toString());
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
            //Log.d("MapsFragment", "Updating orientation to: " + newRotation);
            addOrientationMarker(newPosition, newRotation);

            //to be used to calculate error between PDR and GNSS locations
            currGNSSPos = sensorFusion.getGNSSLatitude(false);
            //Euclidean error distance between latlng values, appropriate for short distances.
            //errDist = (float) Math.sqrt(Math.pow(currGNSSPos[0] - newPosition.latitude, 2) + Math.pow(currGNSSPos[1] -  newPosition.longitude, 2));
            errDist = calculatePositioningError(currGNSSPos[0], currGNSSPos[1], newPosition.latitude, newPosition.longitude);
            Log.d("errDist", "Distance Error: " + errDist);
            errDistT.setText(getString(R.string.meter, String.format("%.2f", errDist)));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mMap != null) { // Ensure the map is ready
            handler.postDelayed(pathUpdater, 500);  // Start or resume path updates
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
    //Used to update current position using PDR displacement
    //PDR x-value represents Westwards movement, +ve when moving East, -ve when West
    //PDR Y-value represents Northwards movement, +ve when moving North, -ve when moving South
    private LatLng convertPDRtoLatLng(float startLat, float startLon, float[] pdrPosition) {
        // Convert PDR y-coordinate (northward movement) to delta latitude
        double deltaLatitude = (pdrPosition[1] / earthRadius) * (180 / Math.PI);
        // Convert PDR x-coordinate (eastward/westward movement) to delta longitude
        double deltaLongitude = (pdrPosition[0] / (earthRadius * Math.cos(Math.PI * startLat / 180))) * (180 / Math.PI);
        // Calculate new latitude and longitude
        double newLatitude = startLat + deltaLatitude;
        double newLongitude = startLon + deltaLongitude;
        // Return the new position as a LatLng object
        return new LatLng(newLatitude, newLongitude);
    }
    public static double calculatePositioningError(double gnssLat, double gnssLon, double pdrLat, double pdrLon) {

        double lat1Rad = Math.toRadians(gnssLat);
        double lat2Rad = Math.toRadians(pdrLat);
        double deltaLatRad = Math.toRadians(pdrLat - gnssLat);
        double deltaLonRad = Math.toRadians(pdrLon - gnssLon);

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return earthRadius * c; // Returns positioning error in meters
    }


    //Function adds building boundaries onto the map so app is aware of when user is in building
    private void initializeAndAddPolygons() {

        // Define each building polygon with its coordinates
        buildingPolygons.put("Nucleus", new PolygonOptions()
                .add(new LatLng(55.92280, -3.17463)) // SW corner
                .add(new LatLng(55.92334, -3.17463)) // NW corner
                .add(new LatLng(55.92334, -3.17384)) // NE corner
                .add(new LatLng(55.92287, -3.17384)) // SE corner
                .add(new LatLng(55.92280, -3.17411))
                .strokeColor(Color.BLUE)
                .fillColor(Color.argb(20, 0, 0, 255))); // fill color with some transparency

        buildingPolygons.put("MurrayLibrary", new PolygonOptions()
                .add(new LatLng(55.92280, -3.17519)) // SW corner
                .add(new LatLng(55.92303, -3.17519)) // NW corner
                .add(new LatLng(55.92308, -3.17510)) // ...
                .add(new LatLng(55.92308, -3.17490))
                .add(new LatLng(55.92303, -3.17478))
                .add(new LatLng(55.92296, -3.17475))
                .add(new LatLng(55.92289, -3.17475))
                .add(new LatLng(55.92280, -3.17479))
                .strokeColor(Color.RED)
                .fillColor(Color.argb(20, 255, 0, 0))); // fill color with some transparency

        buildingPolygons.put("FleemingJenkin", new PolygonOptions()
                .add(new LatLng(55.92207, -3.17232)) // SW corner
                .add(new LatLng(55.92271, -3.17297)) // NW corner
                .add(new LatLng(55.92283, -3.17259)) // ...
                .add(new LatLng(55.92218, -3.17184))

                .strokeColor(Color.CYAN)
                .fillColor(Color.argb(20, 0, 255, 255))); // fill color with some transparency

        buildingPolygons.put("HudsonBeare", new PolygonOptions()
                .add(new LatLng(55.92237, -3.17154)) // SW corner
                .add(new LatLng(55.92252, -3.17170)) // NW corner
                .add(new LatLng(55.92270, -3.17115)) // ...
                .add(new LatLng(55.92241, -3.17074))
                .add(new LatLng(55.92232, -3.17101))
                .add(new LatLng(55.92247, -3.17119))

                .strokeColor(Color.MAGENTA)
                .fillColor(Color.argb(20, 255, 0, 255))); // fill color with some transparency


        buildingPolygons.put("Sanderson", new PolygonOptions()
                .add(new LatLng(55.92267, -3.17202)) // SW corner
                .add(new LatLng(55.92313, -3.17255)) // NW corner
                .add(new LatLng(55.92340, -3.17186)) // ...
                .add(new LatLng(55.92290, -3.17135))

                .strokeColor(Color.YELLOW)
                .fillColor(Color.argb(20, 255, 255, 0))); // fill color with some transparency

        // Add more buildings as needed

        // Iterate over the HashMap and add each polygon to the map
        for (Map.Entry<String, PolygonOptions> entry : buildingPolygons.entrySet()) {
            mMap.addPolygon(entry.getValue()); // Add the polygon to the map
        }
    }
    // Method to check if user is inside any building and switch to indoor map
    public void checkUserLocationAndUpdateMap(LatLng currentUserLocation) {
        boolean foundBuilding = false;

        for (Map.Entry<String, PolygonOptions> entry : buildingPolygons.entrySet()) {
            String buildingId = entry.getKey();
            PolygonOptions polygonOptions = entry.getValue();

            if (PolyUtil.containsLocation(currentUserLocation, polygonOptions.getPoints(), true)) {
                // User is inside this building, switch to indoor map if not already displayed
                if (currentGroundOverlay == null || currentGroundOverlay.getTag() == null || !currentGroundOverlay.getTag().equals(buildingId)) {
                    if (currentGroundOverlay != null) {
                        currentGroundOverlay.remove(); // Remove existing overlay
                    }
                    switchToIndoorMap(buildingId);
                }
                foundBuilding = true;
                break; // Exit after finding the building user is in
            }
        }
        // If the user is not inside any building, remove any existing indoor maps overlays
        if (!foundBuilding && currentGroundOverlay != null) {
            currentGroundOverlay.remove();
            currentGroundOverlay = null; // Clear the reference to prevent memory leaks
        }
    }
    // Method to switch to an indoor map based on the buildingId
    private void switchToIndoorMap(String buildingId) {


        // Define GroundOverlayOptions for the indoor map
        GroundOverlayOptions indoorMapOverlay = new GroundOverlayOptions();

        // Example to set the overlay - Adjust location, image, and dimensions as necessary
        indoorMapOverlay.positionFromBounds(getBoundsFromPolygon(buildingId)); // Assuming you have bounds for each building
        indoorMapOverlay.image(BitmapDescriptorFactory.fromResource(getIndoorMapResource(buildingId))); // Method to get the correct resource


        // Add the overlay to the map
        currentGroundOverlay = mMap.addGroundOverlay(indoorMapOverlay);
        currentGroundOverlay.setTag(buildingId);
    }
    //Helper function to find LatLng bounds from the set of polygon points
    public LatLngBounds getBoundsFromPolygon(String buildingId) {
        PolygonOptions polygonOptions = buildingPolygons.get(buildingId);
        if (polygonOptions == null) {
            return null; // Building ID not found
        }

        List<LatLng> points = polygonOptions.getPoints();
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : points) {
            builder.include(point);
        }

        return builder.build();
    }

    // Helper method to get the resource ID of the indoor map based on the buildingId
    // Adjust this method to return the correct floor map resource for each building
    private int getIndoorMapResource(String buildingId) {
        switch (buildingId) {
            case "Nucleus":
                return R.drawable.nucleusg;
            case "MurrayLibrary":
                return R.drawable.libraryg;
            case "FleemingJenkin":
                return R.drawable.fleeming_jenkin1;
            case "HudsonBeare":
                return R.drawable.hudson_beare1;
            case "Sanderson":
                return R.drawable.sanderson1;
            // Add cases for other buildings
            default:
                return -1; // Invalid resource
        }
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
        this.errDistT = getView().findViewById(R.id.errDist);
        this.errDistT.setText(getString(R.string.meter, "0"));
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