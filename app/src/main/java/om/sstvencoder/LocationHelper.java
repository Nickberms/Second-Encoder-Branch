package om.sstvencoder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;

import java.text.DecimalFormat;

public class LocationHelper {
    private static final String TAG = "LocationHelper";
    private final LocationManager mLocationManager;
    private LocationListener mLocationListener;
    private final EditText inputEditText;

    public LocationHelper(Context context, EditText editText) {
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        inputEditText = editText;
    }

    @SuppressLint("MissingPermission")
    public void startLocationUpdates() {
        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (location != null) {
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    DecimalFormat decimalFormat = new DecimalFormat("#.####");
                    String formattedLatitude = decimalFormat.format(latitude);
                    String formattedLongitude = decimalFormat.format(longitude);
                    String geolocationLink = "maps.google.com/?q=" + formattedLatitude + "," + formattedLongitude;
                    Log.d(TAG, "Geolocation Link: " + geolocationLink);
                    inputEditText.setText(geolocationLink);
                }
                stopLocationUpdates();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };
        mLocationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, mLocationListener, null);
    }

    public void stopLocationUpdates() {
        if (mLocationListener != null) {
            mLocationManager.removeUpdates(mLocationListener);
        }
    }
}