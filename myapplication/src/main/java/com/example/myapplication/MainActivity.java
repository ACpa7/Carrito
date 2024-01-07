package com.example.myapplication;

import java.util.Timer;
import java.util.TimerTask;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import amr.plt.rcParkingRobot.AndroidHmiPLT;
import androidx.appcompat.app.AppCompatActivity;
import parkingRobot.IGuidance;
import parkingRobot.INxtHmi;


public class MainActivity extends AppCompatActivity {

    private ImageView carImageView;

    private float x = 500f;  // set your initial x-axis value
    private float y = 500f;   // set your initial y-axis value

    //representing local Bluetooth adapter
    BluetoothAdapter mBtAdapter = null;
    //representing the bluetooth hardware device
    BluetoothDevice btDevice = null;
    //instance handles Bluetooth communication to NXT
    AndroidHmiPLT hmiModule = null;
    //request code
    final int REQUEST_SETUP_BT_CONNECTION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find the car ImageView
        carImageView = findViewById(R.id.car);

        // Set the initial position of the car ImageView
        carImageView.setX(x);
        carImageView.setY(y);

        //get the BT-Adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        final Button connectButton = findViewById(R.id.buttonSetupBluetooth);
        //on click call the BluetoothActivity to choose a listed device
        connectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent serverIntent = new Intent(getApplicationContext(), BluetoothActivity.class);
                startActivityForResult(serverIntent, REQUEST_SETUP_BT_CONNECTION);
            }
        });

        //toggle button allows user to set mode of the NXT device
        final ToggleButton toggleButton = findViewById(R.id.toggleMode);
        //disable button initially
        toggleButton.setEnabled(false);
        //on click change mode
        toggleButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean checked = ((ToggleButton) v).isChecked();
                if (checked) {
                    //if toggle is checked change mode to SCOUT
                    hmiModule.setMode(INxtHmi.Mode.SCOUT);
                    Log.e("Toggle", "Toggled to Scout");
                } else {
                    // otherwise change mode to PAUSE
                    hmiModule.setMode(INxtHmi.Mode.PAUSE);
                    Log.e("Toggle", "Toggled to Pause");
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBtAdapter != null) {
            //release resources
            mBtAdapter.cancelDiscovery();
        }
    }

    /**
     * handle pressing button with alert dialog if connected(non-Javadoc)
     *
     * @see android.app.Activity#onBackPressed()
     */
    @Override
    public void onBackPressed() {
        super.onBackPressed();

        if (hmiModule != null && hmiModule.connected) {
            //creating new AlertDialog
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Are you sure you want to terminate the connection?")
                    .setCancelable(false)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //disconnect and return to initial screen
                            terminateBluetoothConnection();
                            restartActivity();
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        }
    }

    /**
     * instantiating AndroidHmiPlt object and display received data(non-Javadoc)
     *
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (resultCode) {

            //user pressed back button on Bluetooth activity, so return to initial screen
            case Activity.RESULT_CANCELED:
                break;
            //user chose device
            case Activity.RESULT_OK:
                //connect to chosen NXT
                establishBluetoothConnection(data);
                //display received data from NXT
                if (hmiModule.connected) {
                    //After establishing the connection make sure the start mode of the NXT is set to PAUSE
                    //hmiModule.setMode(Mode.PAUSE);

                    //enable toggle button
                    final ToggleButton toggleMode = findViewById(R.id.toggleMode);
                    toggleMode.setEnabled(true);

                    //disable connect button
                    final Button connectButton = findViewById(R.id.buttonSetupBluetooth);
                    connectButton.setEnabled(false);

                    displayDataNXT();
                    break;
                } else {
                    Toast.makeText(this, "Bluetooth connection failed!", Toast.LENGTH_SHORT).show();
                    Toast.makeText(this, "Is the selected NXT really present & switched on?", Toast.LENGTH_LONG).show();
                    break;
                }
        }
    }

    /**
     * Connect to the chosen device
     *
     * @param data
     */
    private void establishBluetoothConnection(Intent data) {
        //get instance of the chosen Bluetooth device
        String address = data.getExtras().getString(BluetoothActivity.EXTRA_DEVICE_ADDRESS);
        btDevice = mBtAdapter.getRemoteDevice(address);

        //get name and address of the device
        String btDeviceAddress = btDevice.getAddress();
        String btDeviceName = btDevice.getName();

        //instantiate client module
        hmiModule = new AndroidHmiPLT(btDeviceName, btDeviceAddress);

        //connect to the specified device
        hmiModule.connect();

        //wait till connection really is established and go for it...
        // sleepXTimes * 1s = 20s warten...
        int sleepXTimes = 20;
        while (!hmiModule.isConnected() && sleepXTimes > 0) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            sleepXTimes--;
        }
    }

    /**
     * Display the current data of NXT
     */
    private void displayDataNXT() {
        /* For Error:
        android.view.WindowManager$BadTokenException: Unable to add window -- token android.os.BinderProxy@83ae9fd is not valid; is your activity running?
        delete comment and activate sleep
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        */
        new Timer().schedule(new TimerTask() {

            @Override
            public void run() {

                runOnUiThread(new Runnable() {
                    public void run() {
                        if (hmiModule != null) {
                            //display x value
                            final TextView fld_xPos = findViewById(R.id.textViewValueX);
                            fld_xPos.setText(String.valueOf(hmiModule.getPosition().getX() + " cm"));
                            //display y value
                            final TextView fld_yPos = findViewById(R.id.textViewValueY);
                            fld_yPos.setText(String.valueOf(hmiModule.getPosition().getY() + " cm"));
                            //display angle value
                            final TextView fld_angle = findViewById(R.id.TextViewValueAngle);
                            fld_angle.setText(String.valueOf(hmiModule.getPosition().getAngle() + "°"));
                            //display status of NXT
                            final TextView fld_status = findViewById(R.id.textViewValueStatus);
                            fld_status.setText(String.valueOf(hmiModule.getCurrentStatus()));
                            //display distance front
                            final TextView fld_distance_front = findViewById(R.id.textViewValueDistanceFront);
                            fld_distance_front.setText(String.valueOf(hmiModule.getPosition().getDistanceFront()) + " mm");
                            //display distance back
                            final TextView fld_distance_back = findViewById(R.id.textViewValueDistanceBack);
                            fld_distance_back.setText(String.valueOf(hmiModule.getPosition().getDistanceBack()) + " mm");
                            //display distance right
                            final TextView fld_distance_front_side = findViewById(R.id.textViewValueDistanceFrontSide);
                            fld_distance_front_side.setText(String.valueOf(hmiModule.getPosition().getDistanceFrontSide()) + " mm");
                            //display distance left
                            final TextView fld_distance_back_side = findViewById(R.id.textViewValueDistanceBackSide);
                            fld_distance_back_side.setText(String.valueOf(hmiModule.getPosition().getDistanceBackSide()) + " mm");
                            //display Parking lot x1
                            //final TextView fld_distance_back_side = findViewById(R.id.textViewValueDistanceBackSide);
                            //fld_parking_slot_x1.setText(String.valueOf(hmiModule.getPosition().getDistanceBackSide()) + " mm");

                            //display Parking lot y1
                            //final TextView fld_distance_back_side = findViewById(R.id.textViewValueDistanceBackSide);
                            //fld_distance_back_side.setText(String.valueOf(hmiModule.getPosition().getDistanceBackSide()) + " mm");

                            //display Bluetooth connection status
                            final TextView fld_bluetooth = findViewById(R.id.textViewValueBluetooth);
                            //display connection status

                            // Get the new x and y coordinates from real-time data
                            float newX = hmiModule.getPosition().getX(); // replace with actual x-coordinate from data
                            float newY = hmiModule.getPosition().getY(); // replace with actual y-coordinate from data

                            // Update car position
                            updateCarPosition(newX, newY);

                            if (hmiModule.isConnected()) {
                                fld_bluetooth.setText("connected");
                            } else {
                                fld_bluetooth.setText("not connected");
                            }
                            //restart activity when disconnecting
                            if (hmiModule.getCurrentStatus() == IGuidance.CurrentStatus.EXIT) {
                                terminateBluetoothConnection();
                                restartActivity();
                            }
                        }
                    }
                });
            }
        }, 200, 100);

    }

    /**
     * Terminate the Bluetooth connection to NXT
     */
    private void terminateBluetoothConnection() {
        Toast.makeText(this, "Bluetooth connection was terminated!", Toast.LENGTH_LONG).show();
        hmiModule.setMode(INxtHmi.Mode.DISCONNECT);
        hmiModule.disconnect();

        while (hmiModule.isConnected()) {
            //wait until disconnected
        }
        hmiModule = null;
    }

    /**
     * restart the activity
     */
    private void restartActivity() {
        Intent restartIntent = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(restartIntent);
        finish();
    }
    //private LineView lineView;
    /**
     * draw line

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }*/

    /**
     * car moving
     */
    private void updateCarPosition(float newX, float newY) {
        // Update the data (replace with your actual data update logic)
        x = newX;
        y = newY;

        // Update the position of the car ImageView
        carImageView.setX(x);
        carImageView.setY(y);
        // Update the line
       // lineView.updateLine(newX, newY);
    }



}

