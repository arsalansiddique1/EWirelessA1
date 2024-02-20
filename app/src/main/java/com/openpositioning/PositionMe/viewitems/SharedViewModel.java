package com.openpositioning.PositionMe.viewitems;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.GroundOverlay;

public class SharedViewModel extends ViewModel {
    private MutableLiveData<GroundOverlay> currentGroundOverlay = new MutableLiveData<>();
    private MutableLiveData<CameraPosition> cameraPosition = new MutableLiveData<>();
    private int overlayResourceId = 0; // Default/invalid resource ID

    public void setCurrentGroundOverlay(GroundOverlay overlay) {
        currentGroundOverlay.setValue(overlay);
    }

    public LiveData<GroundOverlay> getCurrentGroundOverlay() {
        return currentGroundOverlay;
    }
    // Setter for the resource ID
    public void setOverlayResourceId(int resourceId) {
        this.overlayResourceId = resourceId;
    }

    // Getter for the resource ID
    public int getOverlayResourceId() {
        return this.overlayResourceId;
    }


    public void setCameraPosition(CameraPosition position) {
        cameraPosition.setValue(position);
    }

    public LiveData<CameraPosition> getCameraPosition() {
        return cameraPosition;
    }
}
