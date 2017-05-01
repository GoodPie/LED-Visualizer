package com.app.reallygoodpie.ledvisualalizer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.SeekBar;
import android.widget.Toast;

import com.app.reallygoodpie.ledvisualalizer.adapters.ColorGridAdapter;
import com.app.reallygoodpie.ledvisualalizer.models.ColorGridModel;
import com.pes.androidmaterialcolorpickerdialog.ColorPicker;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    public static final String TAG = "MainActivity";
    public static final String APP_NAME = "com.brandyn.LEDVisualizer";
    private static final UUID MY_UUID = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Fill flag is used as a substitute for LED index
    private static final String FILL_FLAG = "000";
    private static final String TIME_FLAG = "257";
    private static final String ALARM_FLAG = "258";
    private static final String TEMP_FLAG = "259";
    private static final String ANIM_FLAG = "300";
    private static final String BRIGHTNESS_FLAG = "301";

    // Information
    private ColorGridModel currentGrid;
    private int currentGlobalColor;

    private int brightness;

    // UI Elements
    private GridView gridView;
    private SeekBar brightnessBar;
    private Button colorSelectButton, connectButton, fillButton, timeButton, tempButton, alarmButton, animButton;
    private ColorPicker mColorPicker;

    private Context mContext;
    private ColorGridAdapter mAdapter;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mDevice;
    private DataThread mDataThread;
    private ConnectThread mConnectThread;


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mConnectThread = null;
        mDataThread = null;
        mContext = getApplicationContext();

        brightness = 100;

        mColorPicker = new ColorPicker(MainActivity.this, 33, 159, 243);

        // Initialize the grid
        currentGrid = new ColorGridModel();
        currentGrid.init(ContextCompat.getColor(getApplicationContext(), R.color.md_blue_500)); // Default color to blue

        // Get UI elements
        gridView = (GridView) findViewById(R.id.color_gridview);

        // Set onclick listeners for buttons
        initializeButtons();

        brightnessBar = (SeekBar) findViewById(R.id.brightnessBar);
        brightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                brightness = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sendBrightness();
            }
        });

        // Set the default color to green
        currentGlobalColor = ContextCompat.getColor(mContext, R.color.md_green_500);

        // Initialize the grid
        mAdapter = new ColorGridAdapter(getApplicationContext(), currentGrid);
        gridView.setAdapter(mAdapter);
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                Log.i(TAG, "Grid view clicked at position " + i);

                // Painting so change the clicked grid element to the current color without
                // bringing up the color picker
                updateGridElement(i);

            }
        });
    }

    private void sendBrightness() {

        Log.i(TAG, "brightness: " + brightness);
        if (mDataThread != null)
        {
            mDataThread.write((ALARM_FLAG
                    + ColorGridModel.checkValidDeviceString(brightness) +
                    "000000").getBytes());
        }
        else
        {
            Toast.makeText(mContext, "Bluetooth connection not established!",
                    Toast.LENGTH_SHORT).show();
        }

    }

    private void initializeButtons() {
        colorSelectButton = (Button) findViewById(R.id.brush_color_button);
        colorSelectButton.setOnClickListener(this);

        fillButton = (Button) findViewById(R.id.fill_button);
        fillButton.setOnClickListener(this);

        connectButton = (Button) findViewById(R.id.connect_button);
        connectButton.setOnClickListener(this);

        timeButton = (Button) findViewById(R.id.time_button);
        timeButton.setOnClickListener(this);

        alarmButton = (Button) findViewById(R.id.alarm_button);
        alarmButton.setOnClickListener(this);

        tempButton = (Button) findViewById(R.id.temp_button);
        tempButton.setOnClickListener(this);

        animButton = (Button) findViewById(R.id.animation_button);
        animButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();

        switch (viewId)
        {
            // Allow the user to select another color for the global color
            case R.id.brush_color_button:
                mColorPicker.show();
                Button okColorSelection = (Button) mColorPicker.findViewById(R.id.okColorButton);
                okColorSelection.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        currentGlobalColor = mColorPicker.getColor();
                        mColorPicker.dismiss();
                    }
                });
                break;
            case R.id.alarm_button:
                alarmClick();
                break;
            case R.id.temp_button:
                tempClick();
                break;
            case R.id.animation_button:
                animClick();
                break;
            // Fill the grid with one color
            case R.id.fill_button:
                fillGrid();
                break;
            case R.id.connect_button:
                Log.i(TAG, "Attempting to make connection to bluetooth...");
                connectBluetooth();
                break;
            case R.id.time_button:
                displayTime();
                break;
        }
    }

    private void alarmClick()
    {
        View numberPickerView = getLayoutInflater().inflate(R.layout.number_picker_view, null);
        final EditText numEt = (EditText) numberPickerView.findViewById(R.id.alarmEditText);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Set Alarm Time")
                .setView(numberPickerView)
                .setPositiveButton("Set Alarm",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                int alarmValue = Integer.parseInt(numEt.getText().toString());
                                if (mDataThread != null)
                                {
                                    mDataThread.write((ALARM_FLAG
                                        + ColorGridModel.checkValidDeviceString(alarmValue) +
                                    "000000").getBytes());
                                }
                                else
                                {
                                    Toast.makeText(mContext, "Bluetooth connection not established!",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        builder.show();
    }

    private void tempClick()
    {
        if (mDataThread != null)
        {
            mDataThread.write((TEMP_FLAG + "000000000").getBytes());
        }
        else
        {
            Toast.makeText(mContext, "Bluetooth connection not established!",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void animClick()
    {
        if (mDataThread != null)
        {
            mDataThread.write((ANIM_FLAG + "000000000").getBytes());
        }
        else
        {
            Toast.makeText(mContext, "Bluetooth connection not established!",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void displayTime() {
        if (mDataThread != null) {

            Calendar calendar = Calendar.getInstance();
            int hours = calendar.get(Calendar.HOUR_OF_DAY);
            int minutes = calendar.get(Calendar.MINUTE);
            int seconds = calendar.get(Calendar.SECOND);

            String localTime = "" + hours + "" + minutes + "" + seconds;
            mDataThread.write((TIME_FLAG + localTime + "000").getBytes());
        }
        else
        {
            Toast.makeText(mContext, "Bluetooth connection not established!",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Updates the grid element only if bluetooth connection has been established
     * @param index Grid element index
     */
    private void updateGridElement(int index)
    {
        if (mDataThread != null)
        {
            // Send a message containing the index and color to the device
            int deviceIndex = currentGrid.getPositionOnDevice(index);
            String deviceIndexPad = ColorGridModel.checkValidDeviceString(deviceIndex);
            String message = deviceIndexPad + ColorGridModel.getColorString(currentGlobalColor);
            mDataThread.write(message.getBytes());

            // Update the grid visually
            currentGrid.setColor(currentGlobalColor, index);
            mAdapter.notifyDataSetInvalidated();
        }
        else
        {
            // Notify the user that the device isn't connected
            Toast.makeText(mContext, "Bluetooth connection not established!",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Fill the grid only if the bluetooth connection has been established
     */
    private void fillGrid()
    {
        if (mDataThread != null) {

            // Send fill signal to device with the color codes
            String message = FILL_FLAG + ColorGridModel.getColorString((currentGlobalColor));
            mDataThread.write(message.getBytes());

            // Update the grid on display
            currentGrid.init(currentGlobalColor);
            mAdapter.notifyDataSetChanged();
        } else {
            // Don't do anything if Bluetooth has not been established
            Toast.makeText(mContext, "Bluetooth connection not established!",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Initializes the bluetooth on the device and connects to the paired device
     */
    public void initBluetooth()
    {
        // Check if bluetooth can be enabled
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null)
        {
            // Device is not supported
            Toast.makeText(mContext, "Device does not support bluetooth", Toast.LENGTH_SHORT).show();
        }

        // Check if bluetooth is enabled
        if (!mBluetoothAdapter.isEnabled())
        {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }

        // Discover Devices
        // Assume all other devices are disconnected
        // TODO: Create dialog for user to select device
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0)
        {
            for (BluetoothDevice device : pairedDevices)
            {
                mDevice = device;
            }
        }
    }

    /**
     * Attempt to establish a connection with a bluetooth device using a connect thread. Will
     * first check if a device is already connected and then establish a connect thread.
     */
    public void connectBluetooth()
    {
        // Disable the connect button so user can't spam it
        connectButton.setEnabled(false);

        // Notify the user that a connection is being attempted
        Toast.makeText(mContext, "Attempting to connect to device...", Toast.LENGTH_SHORT).show();

        // Ensure we have a device to connect to
        if (mDevice == null)
        {
            initBluetooth();
        }

        // Establish connect thread
        mConnectThread = new ConnectThread(mDevice);
        mConnectThread.run();

    }

    /**
     * Connect thread for establishing connection to Bluetooth device
     */
    private class ConnectThread extends Thread
    {

        private BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        ConnectThread(BluetoothDevice device) {

            // Use a temp object that is later assigned to mmSocket
            BluetoothSocket tmpSocket = null;
            mmDevice = device;

            try {
                // Get a bluetooth socket to connect with the given BluetoothDevice
                tmpSocket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }

            // Establish the connection
            mmSocket = tmpSocket;
        }

        public void run()
        {
            // Cancel discovery because it is slow af
            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect to remote device through the socket. This call will block until it
                // succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; Close the socket and return
                try {
                    // Use a fall back method that should work
                    Log.e(TAG, "Could not connect the socket. Attempting Fallback", connectException);
                    mmSocket =(BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(mmDevice,1);
                    mmSocket.connect();
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | IOException fallbackException) {
                    Log.e(TAG, "Failed fallback", fallbackException);
                    try {
                        // Attempt to close the socket
                        mmSocket.close();
                    } catch (IOException closeException) {
                        Log.e(TAG, "Failed to close socket", closeException);
                    }
                }

                // Notify user that connection has failed
                Toast.makeText(mContext, "Failed to connect the bluetooth device",
                        Toast.LENGTH_SHORT).show();

                // Re-enable the connect button so user can reattempt
                connectButton.setEnabled(true);

                return;
            }

            // Notify the user that the connection had been established
            Toast.makeText(mContext, "Successfully connected to: " + mDevice.getName(),
                    Toast.LENGTH_SHORT).show();
            connectButton.setText(("Connected to: " + mDevice.getName()));
            connectButton.setEnabled(false);

            // Establish data thread
            mDataThread =  new DataThread(mmSocket);
            mDataThread.run();
        }

        public void cancel()
        {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }

    }

    private class DataThread extends Thread
    {
        private final BluetoothSocket mmSocket;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer;

        Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                byte[] writeBuf = (byte[]) msg.obj;
                int begin = (int)msg.arg1;
                int end = (int)msg.arg2;

                switch(msg.what) {
                    case 1:
                        String writeMessage = new String(writeBuf);
                        writeMessage = writeMessage.substring(begin, end);
                        break;
                }
            }
        };


        DataThread(BluetoothSocket socket)
        {
            mmSocket = socket;

            // Create output stream
            OutputStream tmpOut = null;

            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occured when creating output stream", e);
            }

            mmOutStream = tmpOut;
        }

        public void run()
        {
            mmBuffer = new byte[1024];
            // Do nothing as we need no input stream
        }

        void write(byte[] bytes)
        {
            try {
                mmOutStream.write(bytes);
                Message writtenMsg = mHandler.obtainMessage(0,
                        -1, -1, mmBuffer);
                writtenMsg.sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);
                // Send a failure message back to the activity.
                Message writeErrorMsg =
                        mHandler.obtainMessage(1);
                Bundle bundle = new Bundle();
                bundle.putString("toast",
                        "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                mHandler.sendMessage(writeErrorMsg);
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }


    }

}