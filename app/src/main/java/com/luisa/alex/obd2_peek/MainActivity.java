package com.luisa.alex.obd2_peek;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.nightonke.boommenu.BoomMenuButton;
import com.nightonke.boommenu.Types.BoomType;
import com.nightonke.boommenu.Types.ButtonType;
import com.nightonke.boommenu.Types.PlaceType;
import com.nightonke.boommenu.Util;
import com.shinelw.library.ColorArcProgressBar;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;

import java.util.ArrayList;
import java.util.Set;

import cn.pedant.SweetAlert.SweetAlertDialog;
import pl.pawelkleczkowski.customgauge.CustomGauge;

//Floating menu
//Gauge import

public class MainActivity
        extends AppCompatActivity
        implements ConnectionHandler,
        BoomMenuButton.OnSubButtonClickListener,
        LocationListener{

    //************************VARIABLES************************
    //-----------Static-------------
    public static final String TAG = "MainActivity";

    //-----------Member-------------
    //BLUETOOTH MEMBERS
    public static final int REQUEST_ENABLE_BT = 8100;

    //Code for all the activities
    public static final int ABOUT_REQ = 1;
    public static final int LOCATION_REQ = 2;
    public static final int HELP_REQ = 3;
    public static final int TRIPS_REQ = 4;
    private static final int START_TRIP_PERMISSIONS_REQ_CODE = 1001;
    private static final int LOCATOR_PERMISSIONS_REQ_CODE = 1002;

    public ConnectBTAsync connBTAsync = null;
    public BluetoothSocket commSocket;
    private Boolean isTripOngoing = false;

    //TOAST
    private static Toast toast;

    //GAUGES
    //private CustomGauge gaugeSpeed;
    private ColorArcProgressBar gaugeSpeed;
    private TextView gaugeViewSpeed;
    private CustomGauge gaugeRPM;
    private TextView gaugeViewRPM;

    private Button startTrip;
    private TextView btStatus;
    private SweetAlertDialog connectingDialog;

    private boolean init = false;
    private BoomMenuButton boomMenuButton;
    private boolean isLocationPermissionEnabled = false;
    //private boolean isLocationEnabled = false;

    private Boolean simulateTrip;
    private Switch simulateTripSwitch;

    public static boolean firstRunMainActivity;
    public static boolean firstRunHelpActivity;

    private TripDatabase tripDatabase;

    //****************************METHODS******************************

    //-----------------------LIFECYCLE METHODS-----------------------
    //-----------on Create-------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Log.d("onCreate", "OnCreate() called.");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Initialize Location Permissions and other things
        initApp();

    }

    @Override
    protected void onResume(){
        super.onResume();
    }

    //-----------on Destroy-------------
    @Override
    protected void onDestroy() {
        Log.d(TAG, "MainActivity.onDestroy() Called");

        //Close the Bluetooth Connection
        if (connBTAsync != null) {
            connBTAsync.closeSocket();
        }
        super.onDestroy();
    }

    protected void initApp() {
        //Load the database
        tripDatabase = new TripDatabase(this);

        //Obtain the shared preferences to see if it is a first time user
        checkFirstTimeUser();

        //Load UI elements into member variables
        initUIElements();
    }

    //-----------------------UI ELEMENT HELPERS-----------------------
    private void checkFirstTimeUser(){
        SharedPreferences prefs = getSharedPreferences("com.luisa.alex.obd2_peek", MODE_PRIVATE);

        if (prefs.getBoolean("firstRun", true)) {
            //Log.d("onResume", "firstRunMainActivity = true");
            //Set the first run to true
            MainActivity.firstRunMainActivity = true;
            MainActivity.firstRunHelpActivity = true;
            //Set the first time the app is run to false
            prefs.edit().putBoolean("firstRun", false).commit();
        }else{
            //Log.d("onResume", "firstRunMainActivity = false");
            //this.prefs.edit().putBoolean("firstRunMainActivity", true).commit();
            MainActivity.firstRunMainActivity = false;
            MainActivity.firstRunHelpActivity = false;
        }
    }

    //-----------Init UI elements-------------
    private void initUIElements() {
        //Load the UI elements from the resources into the private members of this class

        //Check if the data should be simulated
        simulateTripSwitch = (Switch) findViewById(R.id.switch_Main_simulate_trip);
        this.simulateTrip = simulateTripSwitch.isChecked();

        //Initialize the toast
        this.toast = Toast.makeText(this, "", Toast.LENGTH_LONG);

        //Init gauge elements

        //this.gaugeSpeed = (CustomGauge) findViewById(R.id.gauge_speed);
        this.gaugeSpeed = (ColorArcProgressBar) findViewById(R.id.gauge_speed);
        this.gaugeViewSpeed = (TextView) findViewById(R.id.gaugeView_speed);
        this.gaugeRPM = (CustomGauge) findViewById(R.id.gauge_rpm);
        this.gaugeViewRPM = (TextView) findViewById(R.id.gaugeView_rpm);

        //Obtain the start button
        this.startTrip = (Button) findViewById(R.id.btn_Main_startTrip);

        //Init the menu
        boomMenuButton = (BoomMenuButton) findViewById(R.id.boom);

        //Init Bluetooth connection elements
        btStatus = (TextView) findViewById(R.id.bt_status);

        connectingDialog = new SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE);
        connectingDialog.getProgressHelper().setBarColor(Color.parseColor("#A5DC86"));
        connectingDialog.setTitleText(getString(R.string.connecting));
        connectingDialog.setCancelable(false);

        //Init the TapTarget
        Log.d("Tap Target", "firstRunMainActivity = " + MainActivity.firstRunMainActivity);
        if(MainActivity.firstRunMainActivity) {
            TapTargetView.showFor(this,                 // `this` is an Activity
                    TapTarget.forView(findViewById(R.id.btn_Main_connect_obd), getString(R.string.welcome_msg), getString(R.string.welcome_msg_desec))
                            // All options below are optional
                            .outerCircleColor(R.color.md_amber_600)      // Specify a color for the outer circle
                            .targetCircleColor(R.color.white)   // Specify a color for the target circle
                            .titleTextSize(20)                  // Specify the size (in sp) of the title text
                            .titleTextColor(R.color.white)      // Specify the color of the title text
                            .descriptionTextSize(15)            // Specify the size (in sp) of the description text
                            .descriptionTextColor(R.color.md_amber_900)  // Specify the color of the description text
                            .textColor(R.color.md_white_1000)            // Specify a color for both the title and description text
                            //.textTypeFace(Typeface.SANS_SERIF)  // Specify a typeface for the text
                            .dimColor(R.color.md_black_1000)            // If set, will dim behind the view with 30% opacity of the given color
                            .drawShadow(true)                   // Whether to draw a drop shadow or not
                            .cancelable(true)                  // Whether tapping outside the outer circle dismisses the view
                            .tintTarget(true)                   // Whether to tint the target view's color
                            .transparentTarget(true),           // Specify whether the target is transparent (displays the content underneath)
                    new TapTargetView.Listener() {          // The listener can listen for regular clicks, long clicks or cancels
                        @Override
                        public void onTargetClick(TapTargetView view) {
                            super.onTargetClick(view);      // This call is optional
                            MainActivity.firstRunMainActivity = false;
                        }
                    });
        }
    }

    //-----------Update Gauges via Handler-------------
    @Override
    public void updateGauges(Integer speedInt, Integer rpmInt) {
        //gaugeSpeed.setValue(speedInt);
        gaugeSpeed.setCurrentValues(speedInt);
        gaugeViewSpeed.setText(speedInt + "");

        gaugeRPM.setValue(rpmInt);
        gaugeViewRPM.setText(rpmInt+"");
    }

    //-----------Reset the Gauges via Handler-------------
    @Override
    public void resetGauges(){
        //Reset the Gauges back to 0
        this.updateGauges(0,0);
    }

    //-----------------------BLUETOOTH HELPERS-----------------------
    //-----------Is Bluetooth enabled-------------
    private void enableBluetooth() {

        //check if device supports bluetooth
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            //Device does not support bluetooth
            Log.d(TAG, "[MainActivity.enableBTBtnClick] Device does not support Bluetooth");
            return;
        }

        //Enabled bluetooth if it is not already enabled
        if (!mBluetoothAdapter.isEnabled()) {
            //Launch intent to ask to turn bluetooth on
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

        } else {
            Log.d(TAG, "[MainActivity.enableBTBtnClick] Bluetooth already enabled");
            //MainActivity.showToast("Bluetooth is already enabled");

            //Connect to a paired bluetooth device
            connectToPairedDevice();
        }
    }

    //-----------Connect Bluetooth to paired device-------------
    private void connectToPairedDevice() {
        //Connects the already enabled bluetooth to a paired device
        //The user is given a list of paired devices from which
        //they can click and select the device they wish to connec to

        //get a reference to the mainActivity;
        final ConnectionHandler connHandler = this;

        //Init arrays that hold devices information
        ArrayList<String> deviceStrs = new ArrayList<>(); //Holds the device names + addresses
        final ArrayList<String> devicesAddress = new ArrayList<>(); //Holds only the device addresses
        final ArrayList<BluetoothDevice> devices = new ArrayList<>(); //Holds the actual device object

        //Obtain any already Paired/Bonded Bluetooth Devices
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        //If there is more than 1 paired device
        if (pairedDevices.size() > 0) {
            //Iterate through all paired devices
            for (BluetoothDevice device : pairedDevices) {
                //Add the devices to the 3 respective arrays defined above
                deviceStrs.add(device.getName() + "\n" + device.getAddress());
                devicesAddress.add(device.getAddress());
                devices.add(device);
            }
        } else {
            //There are no existing Paired Devices
            Log.d(TAG, "[MainActivity.connectBtnClick] No Paired Devices Found");
            MainActivity.showToast(getString(R.string.need_to_pair_msg));
            return;
        }

        //Set up the list of paired devices to be shown to the user
        ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.select_dialog_singlechoice, deviceStrs.toArray(new String[deviceStrs.size()]));
        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
            //Once the user clicks on the paired device they wish to connect to
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //Close the dialog
                dialog.dismiss();
                connectingDialog.show();

                //Obtain the index of the device they clicked on
                int deviceIndex = ((AlertDialog) dialog).getListView().getCheckedItemPosition();

                //Obtain the device given the index
                BluetoothDevice device = devices.get(deviceIndex);
                String deviceAddress = devicesAddress.get(deviceIndex);
                Log.d(TAG, "deviceAddress: " + deviceAddress);

                //Finally Attempt to Initialize the connection
                //Check if the async or thread method should be used to connect
                //MainActivity.showToast("Connecting...");
                //ASYNC METHOD
                connBTAsync = new ConnectBTAsync(commSocket, connHandler);
                connBTAsync.execute(device);
            }
        });

        //Display the list of paired Devices to the user
        alertDialog.setTitle("Select Bluetooth Device");
        alertDialog.show();
    }

    //-----------Bluetooth Handler-------------
    @Override
    public void handleBTConnection(BluetoothSocket mmSocket) {
        //Called after bluetooth has attempted to connect, either success or failure
        //This is called by the onPostExecute method after the ConnectBTAsync has finished doInBackground

        //Check if the connection is valid
        Boolean isConnected = mmSocket.isConnected();
        //Placeholder message to be displayed to user
        String toastMessage;

        //Let the user know that the connection was a success
        if (isConnected) {
            toastMessage = getString(R.string.connection_success);
            //set the socket
            this.commSocket = mmSocket;

            // Set Bluetooth status to ON
            btStatus.setText(R.string.CONNECTED);
            btStatus.setTextColor(getResources().getColor(R.color.md_green_600));

            simulateTripSwitch.setEnabled(false);

            //Hide the connect button and show the Disconnect one
            afterConnectDisplay();
            connectingDialog.dismiss();

        } else {
            toastMessage = getString(R.string.unsuccessful_connection);
            //MainActivity.showToast(toastMessage);
            connectingDialog.dismiss();
            new SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
                    .setTitleText(getString(R.string.something_went_wrong))
                    .setContentText(getString(R.string.unable_to_connect_to_device))
                    .showCancelButton(true)
                    .setCancelClickListener(new SweetAlertDialog.OnSweetClickListener() {
                        @Override
                        public void onClick(SweetAlertDialog sDialog) {
                            sDialog.cancel();
                        }
                    })
                    .show();
        }

        //Show Log + Toast
        Log.d(TAG, "[MainActivity.handleBTConnection] " + toastMessage);
        //MainActivity.showToast(toastMessage);
    }

    //-----------Disconnect Bluetooth-------------
    private void disconnectFromBluetooth() {
        if (connBTAsync != null) {
            //connBTThread.cancel();
            if (connBTAsync.closeSocket()) {
                //Show Success Toast
                //MainActivity.showToast("Disconnect Successful!");

                // Set Bluetooth status to OFF
                btStatus.setText(R.string.DISCONNECTED);
                btStatus.setTextColor(getResources().getColor(R.color.md_red_800));

                simulateTripSwitch.setEnabled(true);

                isTripOngoing = false;

            } else {
                //Show UnSuccess Toast
                MainActivity.showToast(getString(R.string.disconnect_unsuccessful));
            }
        }
    }

    //-----------------------INTENT RESULTS-----------------------
    @Override
    public void onActivityResult(int requestCode,
                                 int responseCode,
                                 Intent resultIntent) {

        //-----------After enabling bluetooth-------------
        //Response Intent once the Bluetooth has been enabled
        if (requestCode == REQUEST_ENABLE_BT && responseCode == RESULT_OK) {
            Log.d(TAG, "[MainActivity.onActivityResult] Bluetooth has been Enabled");
            MainActivity.showToast(getString(R.string.bluetooth_enabled));

            //Connect to a paired bluetooth device
            connectToPairedDevice();
        }
        switch(responseCode){
            case ABOUT_REQ: {LaunchAboutCarActivity();} break;
            case LOCATION_REQ: {LaunchLocatorActivity();} break;
            case HELP_REQ: {LaunchHelpActivity();} break;
            case TRIPS_REQ: {LaunchPastTripsActivity();} break;
            default: Log.d(TAG, "No response Code Given");
        }

    }

    //-----------------------BUTTON/SWITCH CLICKS-----------------------

    //-----------Simulate Trip Switch-------------
    public void simulateTripSwitch(View view){
        Switch simulateTripSwitch = (Switch) findViewById(R.id.switch_Main_simulate_trip);
        this.simulateTrip = simulateTripSwitch.isChecked();
    }

    //-----------Connect Bluetooth to the OBD Device-------------
    public void connectOBDClick(View view) {

        String METHOD = "connectOBDClick";
        Log.d(METHOD, "called");

        //Check if bluetooth is enabled and connect to OBD
        enableBluetooth();
    }

    //-----------start trip-------------
    public void startTripClick(View view) {
        String METHOD = "startTripClick";
        Log.d(METHOD, "called");

        MediaPlayer player = MediaPlayer.create(this, R.raw.engine);

        // Check if Location Permissions are OK
        if(this.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //Not granted. Prompt user to enable.
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, START_TRIP_PERMISSIONS_REQ_CODE);
        } else {
            isLocationPermissionEnabled = true;
        }

        if (isLocationPermissionEnabled) {
            // Granted! Check if GPS is ON
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                //GPS is OFF
                //Dialog before prompting the user to turn location services on
                new SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE)
                        .setTitleText(getString(R.string.location_services_off))
                        .setContentText(getString(R.string.take_to_settings))
                        .setConfirmText(getString(R.string.ok_take_me))
                        .setCancelText(getString(R.string.CANCEL))
                        .showCancelButton(true)
                        .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                            @Override
                            public void onClick(SweetAlertDialog sDialog) {
                                sDialog.dismissWithAnimation();

                                String locConfig = Settings.ACTION_LOCATION_SOURCE_SETTINGS;
                                Intent enableGPSIntent = new Intent(locConfig);
                                startActivity(enableGPSIntent);
                            }
                        })
                        .setCancelClickListener(new SweetAlertDialog.OnSweetClickListener() {
                            @Override
                            public void onClick(SweetAlertDialog sDialog) {
                                sDialog.cancel();
                            }
                        })
                        .show();
            }

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                // GPS is ON. Check if socket is connected
                if (this.commSocket.isConnected()) {
                    //showToast("Starting communication stream...");

                    player.start();

                    //Start the OBD Communcation Stream - false (2nd arg) indicating we are no quering for vin
                    OBDCommunicator obdConnection = new OBDCommunicator(this, this, this, false, this.simulateTrip);
                    obdConnection.execute(this.commSocket);

                    //Hide/Show Buttons
                    afterStartTripDisplay();

                    isTripOngoing = true;

                } else {
                    showToast(getString(R.string.not_connected_to_obd));
                }
            } else {
                //showToast("Either permissions or GPS are disabled.");
            }
        }
    }

    //-----------end trip-------------
    public void endTripClick(View view) {
        String METHOD = "endTripClick";
        Log.d(METHOD, "called");

        //Disconnect the bluetooth (closes the bluetooth socket)
        disconnectFromBluetooth();

        //Hide Show the Buttons
        afterEndTripDisplay();
    }

    //-----------------------MENU-----------------------

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // Use a param to record whether the boom button has been initialized
        // Because we don't need to init it again when onResume()
        if (init)
            return;

        init = true;

        int[][] subButton1Colors = new int[1][2];
        int[][] subButton2Colors = new int[1][2];
        int[][] subButton3Colors = new int[1][2];
        int[][] subButton4Colors = new int[1][2];

        subButton1Colors[0][1] = ContextCompat.getColor(this, R.color.md_red_400);
        subButton1Colors[0][0] = Util.getInstance().getPressedColor(subButton1Colors[0][1]);

        subButton2Colors[0][1] = ContextCompat.getColor(this, R.color.md_green_400);
        subButton2Colors[0][0] = Util.getInstance().getPressedColor(subButton2Colors[0][1]);

        subButton3Colors[0][1] = ContextCompat.getColor(this, R.color.md_amber_600);
        subButton3Colors[0][0] = Util.getInstance().getPressedColor(subButton3Colors[0][1]);

        subButton4Colors[0][1] = ContextCompat.getColor(this, R.color.md_deep_purple_400);
        subButton4Colors[0][0] = Util.getInstance().getPressedColor(subButton4Colors[0][1]);

        // Now with Builder, you can init BMB more convenient
        new BoomMenuButton.Builder()
                .addSubButton(ContextCompat.getDrawable(this, R.drawable.car), subButton1Colors[0], getString(R.string.about))
                .addSubButton(ContextCompat.getDrawable(this, R.drawable.where), subButton2Colors[0], getString(R.string.locator))
                .addSubButton(ContextCompat.getDrawable(this, R.drawable.help), subButton3Colors[0], getString(R.string.help))
                .addSubButton(ContextCompat.getDrawable(this, R.drawable.past), subButton4Colors[0], getString(R.string.trips))
                .button(ButtonType.CIRCLE)
                .boom(BoomType.HORIZONTAL_THROW_2)
                .place(PlaceType.CIRCLE_4_2)
                //.subButtonTextColor(Color.BLACK)
                .subButtonsShadow(Util.getInstance().dp2px(2), Util.getInstance().dp2px(2))
                .onSubButtonClick(this)
                .init(boomMenuButton);
    }

    //-----------------------VIN QUERY-----------------------
    @Override
    public void handleVin(String vinNumber){
        String METHOD = "handleVin";
        Log.d(METHOD, "called");

        //Check if Vin was not found
        if(vinNumber.isEmpty()){
            showToast(getString(R.string.unable_to_obtain_vin));

            return;
        }

        //Vin was found
        //Log.d(METHOD, "Vin = " + vinNumber);
        //showToast("Got Vin! Getting Data...");
        Log.d(METHOD, "Got Vin! Getting Data...");

        //Start downloading the car data from the internet
        vinDataDownloader vinDataDownloader = new vinDataDownloader(this);
        vinDataDownloader.execute(vinNumber);
    }

    //-----------------------LOCATION PERMISSIONS-----------------------

    //-----------Request Permissions Result-------------
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case START_TRIP_PERMISSIONS_REQ_CODE: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    isLocationPermissionEnabled = true;
                    Log.d("TAG", "isLocationPermissionEnabled is now true.");
                    startTrip.performClick();
                    //checkLocationEnabled();
                } else {
                    // tell the user that the feature will not work
                }
                return;
            }
            case LOCATOR_PERMISSIONS_REQ_CODE: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    isLocationPermissionEnabled = true;
                    Log.d("TAG", "isLocationPermissionEnabled is now true.");

                    onClick(1);
                    //checkLocationEnabled();
                } else {
                    // tell the user that the feature will not work
                }
                return;
            }
        }
    }

    //-----------------------ACTIVITY LAUNCHERS-----------------------
    //-----------About Car Activity-------------
    public void LaunchAboutCarActivity() {
        String METHOD = "testBtnClick";
        Log.d(METHOD, "called");

        if (isTripOngoing) {
            new SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
                    .setTitleText(getString(R.string.oh_no))
                    .setContentText(getString(R.string.one_conn_at_a_time))
                    .showCancelButton(true)
                    .setCancelClickListener(new SweetAlertDialog.OnSweetClickListener() {
                        @Override
                        public void onClick(SweetAlertDialog sDialog) {
                            sDialog.cancel();
                        }
                    })
                    .show();
            return;
        }

        if(this.commSocket == null){
            //showToast("Please Connect First!");
            new SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
                    .setTitleText(getString(R.string.no_connection))
                    .setContentText(getString(R.string.need_to_connect_to_vehicle))
                    .showCancelButton(true)
                    .setCancelClickListener(new SweetAlertDialog.OnSweetClickListener() {
                        @Override
                        public void onClick(SweetAlertDialog sDialog) {
                            sDialog.cancel();
                        }
                    })
                    .show();
            return;
        }

        //If not null, check if there is a connection
        if(!this.commSocket.isConnected()){
            //showToast("OBD Not Connected!");
            new SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
                    .setTitleText(getString(R.string.there_is_no_connection))
                    .setContentText(getString(R.string.please_connect_to_car))
                    .showCancelButton(true)
                    .setCancelClickListener(new SweetAlertDialog.OnSweetClickListener() {
                        @Override
                        public void onClick(SweetAlertDialog sDialog) {
                            sDialog.cancel();
                        }
                    })
                    .show();
            return;
        }

        Log.d(METHOD, "Obtaining Vin...");
        //showToast("Loading...");
        //Query for vin - 2nd arg is set to true
        //Start the OBD Communication Stream - false (2nd arg) indicating we are no quering for vin
        OBDCommunicator obdConnection = new OBDCommunicator(this, this, this, true, this.simulateTrip);
        obdConnection.execute(this.commSocket);
        //After the Vin is obtain a handleVin() function is called
    }

    //-----------Locator Activity-------------
    private void LaunchLocatorActivity() {

        // Check if Location Permissions are OK
        if(this.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //Not granted. Prompt user to enable.
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATOR_PERMISSIONS_REQ_CODE);
        } else {
            isLocationPermissionEnabled = true;
        }

        if (isLocationPermissionEnabled) {
            // Granted! Check if GPS is ON
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                //GPS is OFF
                //Dialog before prompting the user to turn location services on
                new SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE)
                        .setTitleText(getString(R.string.location_services_off))
                        .setContentText(getString(R.string.take_to_settings))
                        .setConfirmText(getString(R.string.ok_take_me))
                        .setCancelText(getString(R.string.CANCEL))
                        .showCancelButton(true)
                        .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                            @Override
                            public void onClick(SweetAlertDialog sDialog) {
                                sDialog.dismissWithAnimation();

                                String locConfig = Settings.ACTION_LOCATION_SOURCE_SETTINGS;
                                Intent enableGPSIntent = new Intent(locConfig);
                                startActivity(enableGPSIntent);
                            }
                        })
                        .setCancelClickListener(new SweetAlertDialog.OnSweetClickListener() {
                            @Override
                            public void onClick(SweetAlertDialog sDialog) {
                                sDialog.cancel();
                            }
                        })
                        .show();
            }

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                // GPS is ON
                Intent intent = new Intent(MainActivity.this, LocatorActivity.class);
                startActivityForResult(intent, LOCATION_REQ);
            }
        }

    }

    //-----------Help Activity-------------
    private void LaunchHelpActivity() {
        Intent intent = new Intent(MainActivity.this, HelpActivity.class);
        startActivityForResult(intent, HELP_REQ);
    }

    //-----------Past Trip Activity-------------
    private void LaunchPastTripsActivity() {
        Intent intent = new Intent(MainActivity.this, PastTripsActivity.class);
        startActivityForResult(intent, TRIPS_REQ);
    }

    private void LaunchDetailedStatsActivity(Trip trip) {
        Intent intent = new Intent(MainActivity.this, DetailedStatsActivity.class);
        intent.putExtra("date", trip.getDate());
        intent.putExtra("duration", trip.getDuration());
        intent.putExtra("origin", trip.getOrigin());
        intent.putExtra("timeDeparture", trip.getTimeDeparture());
        intent.putExtra("destination", trip.getDestination());
        intent.putExtra("timeArrival", trip.getTimeArrival());
        intent.putExtra("maxSpeed", trip.getMaxSpeed());
        intent.putExtra("maxRPM", trip.getMaxRPM());
        startActivity(intent);
    }

    //-----------Show car data list-------------
    @Override
    public void showCarDataList(ArrayList<Tuple> data){
        String METHOD = "showCarDataList";

        //Check if there was an error in downloading the data from the site
        if(data.isEmpty()){
            showToast(getString(R.string.unable_to_download_data));
            return;
        }

        //Launch the intent to display the car data
        Intent intent = new Intent(this, AboutCarActivity.class);
        intent.putExtra("carData", data);
        startActivityForResult(intent, ABOUT_REQ);

        //TEMP - log the data
        /*
        for(String ele : data){
            Log.d(METHOD, ele);
        }
        */
    }

    //-----------------------UTILITY METHODS-----------------------
    //-----------After Connect has been Clicked-------------
    private void afterConnectDisplay() {
        //Hide the connect button
        //Show the Start trip one

        //Obtain the button references
        Button connButton = (Button) findViewById(R.id.btn_Main_connect_obd);
        Button startTripButton = (Button) findViewById(R.id.btn_Main_startTrip);

        //Set the visibilities
        connButton.setVisibility(View.GONE);
        startTripButton.setVisibility(View.VISIBLE);
    }

    //-----------After Start Trip has been Clicked-------------
    private void afterStartTripDisplay() {
        //Hide the Start Trip
        //Show the End Trip

        //Obtain the button references
        Button startTripButton = (Button) findViewById(R.id.btn_Main_startTrip);
        Button endTripButton = (Button) findViewById(R.id.btn_Main_endTrip);

        //Set the visibilities
        startTripButton.setVisibility(View.GONE);
        endTripButton.setVisibility(View.VISIBLE);
    }

    //-----------After End Trip has been Clicked-------------
    private void afterEndTripDisplay() {
        //Hide the End Trip
        //Show the Connect Button

        //Obtain the button references
        Button endTripButton = (Button) findViewById(R.id.btn_Main_endTrip);
        Button connButton = (Button) findViewById(R.id.btn_Main_connect_obd);

        //Set the visibilities
        endTripButton.setVisibility(View.GONE);
        connButton.setVisibility(View.VISIBLE);
    }

    //-----------Save Trip Alert-------------
    public void saveTripAlert(final Trip tripMissingID){
        //Takes in a Trip with a missing ID since it hasn't been added to the database

        final String METHOD = "saveTripAlert";
        //Log.d("saveTripAlert", "called");

        //Obtain the database incase in the event that user wishes to save the trip
        //tripDatabase = new TripDatabase(this);

        //Display an alert asking if the user wants to save the trip
        new SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE)
                .setTitleText(getString(R.string.save_the_trip))
                .setContentText(getString(R.string.cant_recover_if_no_clicked))
                .setConfirmText(getString(R.string.yes_save_trip))
                .setCancelText(getString(R.string.no_discard_it))
                .showCancelButton(true)
                .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog sDialog) {
                        //Log.d(METHOD, "Saving Trip:" + tripMissingID.toString());
                        sDialog.dismissWithAnimation();

                        //Save the trip details in the database
                        Trip trip = tripDatabase.addTrip(tripMissingID);
                        saveTripSuccessAlert(trip);
                    }
                })
                .setCancelClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog sDialog) {
                        //Log.d(METHOD, "cancelled!");
                        sDialog.cancel();
                    }
                })
                .show();
    }

    public void saveTripSuccessAlert(Trip trip){
        final String METHOD = "saveTripSuccessAlert";
        final Trip currentTrip = trip;

        //Display an alert asking if the user wants to save the trip
        new SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
                .setTitleText(getString(R.string.trip_has_been_saved))
                .setContentText(getString(R.string.view_details_later))
                .setConfirmText(getString(R.string.show_details))
                .setCancelText(getString(R.string.OK))
                .showCancelButton(true)
                .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog sDialog) {
                        //Log.d(METHOD, "Showing details...!");
                        sDialog.dismissWithAnimation();
                        LaunchDetailedStatsActivity(currentTrip);
                    }
                })
                .setCancelClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog sDialog) {
                        //Log.d(METHOD, "Home screen!!");
                        sDialog.cancel();
                    }
                })
                .show();
    }


    //-----------Show a Toast-------------
    public static void showToast(String message) {
        //Displays a toast given a message
        MainActivity.toast.setText(message);
        MainActivity.toast.show();
    }

    //-----------------------DEBUGGING/TESTING-----------------------
    //-----------Test Button-------------
    public void testBtnClick(View view) {
        //Trip currentTrip = new Trip((new Date()).toString(), new Long(1400), "Toronto, Canada", "Tokyo, Japan", 150, 4000);
        //saveTripAlert(currentTrip);
    }

    @Override
    public void onClick(int buttonIndex) {
        Log.d(TAG, "Button " + buttonIndex + " was clicked.");

        switch (buttonIndex) {
            case 0:
                LaunchAboutCarActivity();
                break;
            case 1:
                LaunchLocatorActivity();
                break;
            case 2:
                LaunchHelpActivity();
                break;
            case 3:
                LaunchPastTripsActivity();
                break;
            default:
                Log.d(TAG, "There has been an error with the subbuttons.");
                break;
        }
    }

    //----------------------LOCATION LISTENER METHODS----------------------------
    @Override
    public void onLocationChanged(Location location) {

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

}