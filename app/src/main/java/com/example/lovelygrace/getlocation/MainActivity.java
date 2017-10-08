package com.example.lovelygrace.getlocation;

import android.*;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static final int MY_PERMISSION_REQUEST_CODE = 7171;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 7172;
    private TextView txtCoordinates, txtlatitude, txtlongitude;
    private Button btnGetCoordinates, btnLocationUpdates, btnSendToDb;
    private boolean mRequestingLocationUpdates = false;
    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient; //provied common entry point to all Google Play Services
    private Location mLastLocation;
    private DatabaseReference mRoot;

    private static int UPDATE_INTERVAL = 5000; // SEC
    private static int FATEST_INTERVAL = 3000; // SEC
    private static int DISPLACEMENT = 10; // METERS


    @Override
    //callback for the results from requesting permissions
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (checkPlayServices())
                        buildGoogleApiClient();
                    createLocationRequest();
                }
                break;
        }
    }

    @Override
    //initialize everything
    //followed by onStart()
    //the bundle here contains the previous frozen state of the activity if there is one
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        txtCoordinates = (TextView) findViewById(R.id.txtCoordinates);
        txtlatitude = (TextView) findViewById(R.id.textView2);
        txtlongitude = (TextView) findViewById(R.id.textView3);
        btnGetCoordinates = (Button) findViewById(R.id.btnGetCoordinates);
        btnLocationUpdates = (Button) findViewById(R.id.btnTrackLocation);
        btnSendToDb = (Button) findViewById(R.id.btnSendLocation);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //Run-time request permission
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, MY_PERMISSION_REQUEST_CODE);
        } else {
            if (checkPlayServices()) {
                buildGoogleApiClient();
                createLocationRequest();
            }
        }

        //takes care of displaying the coordinates in the textfield
        btnGetCoordinates.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                displayLocation();
            }
        });

        //takes care of the updating of the location
        btnLocationUpdates.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                tooglePeriodicLocationUpdates();
            }
        });
        mRoot = FirebaseDatabase.getInstance().getReferenceFromUrl("https://getlocation-72c91.firebaseio.com/");

        btnSendToDb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String latVal = txtlatitude.getText().toString();
                String longVal = txtlongitude.getText().toString();
                //this has a warning so i might just comment this out.
                //txtlatitude.setText("Sending latitude to database");
                //txtlongitude.setText("Sending longitude to database");
                DatabaseReference latitude = mRoot.child("latitude");
                DatabaseReference longitude = mRoot.child("longitude");
                latitude.setValue(latVal);
                longitude.setValue(longVal);
            }
        });

    }

    @Override
    //called when the activity is starting to interact with the user
    protected void onResume() {
        super.onResume();
        checkPlayServices();
    }

    @Override
    //called when the actvity is becoming visible to the user
    protected void onStart() {
        super.onStart();
        if(mGoogleApiClient != null)
            mGoogleApiClient.connect();
    }

    @Override
    //called when the activity is no longer visible to the user because another activity has been resumed and covering this one
    protected void onStop() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient,this);
        if (mGoogleApiClient != null)
            mGoogleApiClient.disconnect();
        super.onStop();
    }

    //updates location
    private void tooglePeriodicLocationUpdates() {
        if(!mRequestingLocationUpdates) {
            btnLocationUpdates.setText("Stop location update");
            mRequestingLocationUpdates = true;
            startLocationUpdates();
        } else {
            btnLocationUpdates.setText("Start location update");
            mRequestingLocationUpdates = false;
            stopLocationUpdates();
        }
    }

    //displays the location (latitude & longitude)
    private void displayLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            double latitude = mLastLocation.getLatitude();
            double longitude = mLastLocation.getLongitude();
            txtlatitude.setText(latitude + "");
            txtlongitude.setText(longitude + "");
            //txtCoordinates.setText(latitude + " / " + longitude);
        } else
            txtCoordinates.setText("Couldn't get the location. Make sure location is enabled on the device");

    }

    //create location request
    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FATEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY); //request most accurate locations available
        mLocationRequest.setSmallestDisplacement(DISPLACEMENT);
    }

    //build Google API
    private synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this) //provides methods that allow you to specify the API u wanted to use
                .addConnectionCallbacks(this) //receive callbacks in response to the asynchronous connect()
                // method whem the connection to Google Play services succeeds, fails, etc.
                .addOnConnectionFailedListener(this) //receive unresolvable connection errors
                .addApi(LocationServices.API).build();

        //Fix first time run app if permission doesn't grant yet so can't get anything
        mGoogleApiClient.connect();
    }

    //checks whether there is a connection
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this, PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Toast.makeText(getApplicationContext(), "This device is not supported", Toast.LENGTH_LONG).show();
                finish();
            }
            return false;
        }
        return true;
    }

    //start location updates
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        txtlatitude.setText("Updating...");
        txtlongitude.setText("Updating...");
    }

    //stop location updates
    private void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient,this);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        displayLocation();
        if(mRequestingLocationUpdates)
            startLocationUpdates();
    }



    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    @Override
    //GoogleApiClient.OnConnectionFailedListener
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    //LocationListener
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        displayLocation();
    }
}
