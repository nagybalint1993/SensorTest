package kpwhrj.sensortest;

/**
 * Created by BalintGyorgy on 2015.03.14..
 */

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import kpwhrj.sensortest.Sensor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import kpwhrj.sensortest.service.BluetoothLeService;


public class DeviceControlActivity extends Activity {

    public static final String EXTRA_DEVICE = "EXTRA_DEVICE";
    private static final int GATT_TIMEOUT = 250; // milliseconds

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice= null;
    private BluetoothLeService mBtLeService= null;
    private ArrayList<BluetoothGattService> mServiceList=null;
    private BluetoothGattService mOadService = null;
    private BluetoothGattService mConnControlService = null;
    private List<Sensor> mEnabledSensors = new ArrayList<Sensor>();
    private BluetoothGatt mBtGatt;

    private TextView mAmbValue;
    private TextView mObjValue;
    String mAmb;
    String mObj;

    private DecimalFormat decimal = new DecimalFormat("+0.00;-0.00");

    private boolean mServicesRdy=false;
    private boolean mBusy=false;
    private String mFwRev;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_control);


        Intent intent = getIntent();
        mBtLeService = BluetoothLeService.getInstance();
        mBluetoothDevice = intent.getParcelableExtra(EXTRA_DEVICE);
        mServiceList = new ArrayList<BluetoothGattService>();
       // mBtGatt = BluetoothLeService.getBtGatt();

        System.out.println(mBluetoothDevice.getAddress());


        mAmbValue= (TextView) this.findViewById(R.id.TVTemperatureAmb);
        mObjValue= (TextView) this.findViewById(R.id.TVTemperatureObj);

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter fi = new IntentFilter();
        fi.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        fi.addAction(BluetoothLeService.ACTION_DATA_NOTIFY);
        fi.addAction(BluetoothLeService.ACTION_DATA_WRITE);
        fi.addAction(BluetoothLeService.ACTION_DATA_READ);
        return fi;
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            int status = intent.getIntExtra(BluetoothLeService.EXTRA_STATUS,
                    BluetoothGatt.GATT_SUCCESS);

            if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    setStatus("Service discovery complete");
                    displayServices();
                    checkOad();
                    enableDataCollection(true);
                    getFirmwareRevison();
                } else {
                    Toast.makeText(getApplication(), "Service discovery failed",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            } else if (BluetoothLeService.ACTION_DATA_NOTIFY.equals(action)) {
                // Notification
                byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
                onCharacteristicChanged(uuidStr, value);
            } else if (BluetoothLeService.ACTION_DATA_WRITE.equals(action)) {
                // Data written
                String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
                onCharacteristicWrite(uuidStr, status);
            } else if (BluetoothLeService.ACTION_DATA_READ.equals(action)) {
                // Data read
                String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
                byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                onCharacteristicsRead(uuidStr, value, status);
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                setError("GATT error code: " + status);
            }
        }
    };


    public void onCharacteristicChanged(String uuidStr, byte[] rawValue) {
        Point3D v;
        String msg;

        if(uuidStr.equals(SensorTagGatt.UUID_IRT_DATA.toString())){
            Log.d("fasza","fasza");
            v = kpwhrj.sensortest.Sensor.IR_TEMPERATURE.convert(rawValue);
            mAmb = decimal.format(v.x) + "\n";
            mObj = decimal.format(v.y) + "\n";

            if(mAmb.equals(null) && mObj.equals(null)){
                mObjValue.setText("Nincs Adat");
                mAmbValue.setText("Nincs Adat");
            }
            else{
                mObjValue.setText(mObj);
                mAmbValue.setText(mAmb);
            }


        }

    }

    private void onCharacteristicsRead(String uuidStr, byte[] value, int status) {
        // Log.i(TAG, "onCharacteristicsRead: " + uuidStr);

        if (uuidStr.equals(SensorTagGatt.UUID_DEVINFO_FWREV.toString())) {
            mFwRev = new String(value, 0, 3);
            Toast.makeText(this, "Firmware revision: " + mFwRev,Toast.LENGTH_LONG).show();
        }
/*
        if (uuidStr.equals(SensorTagGatt.UUID_BAR_CALI.toString())) {
            // Sanity check
            if (value.length != 16)
                return;

            // Barometer calibration values are read.
            List<Integer> cal = new ArrayList<Integer>();
            for (int offset = 0; offset < 8; offset += 2) {
                Integer lowerByte = (int) value[offset] & 0xFF;
                Integer upperByte = (int) value[offset + 1] & 0xFF;
                cal.add((upperByte << 8) + lowerByte);
            }

            for (int offset = 8; offset < 16; offset += 2) {
                Integer lowerByte = (int) value[offset] & 0xFF;
                Integer upperByte = (int) value[offset + 1];
                cal.add((upperByte << 8) + lowerByte);
            }

            BarometerCalibrationCoefficients.INSTANCE.barometerCalibrationCoefficients = cal;
        }*/
    }
    private void onCharacteristicWrite(String uuidStr, int status) {
        // Log.d(TAG, "onCharacteristicWrite: " + uuidStr);
    }

    private void setStatus(String txt) {
        Toast.makeText(this, txt, Toast.LENGTH_SHORT).show();
    }
    private void displayServices() {
        mServicesRdy = true;

        try {
            mServiceList =(ArrayList) mBtLeService.getSupportedGattServices();
        } catch (Exception e) {
            e.printStackTrace();
            mServicesRdy = false;
        }

        // Characteristics descriptor readout done
        if (!mServicesRdy) {
            setError("Failed to read services");
        }
    }
    private void checkOad() {
        // Check if OAD is supported (needs OAD and Connection Control service)
        mOadService = null;
        mConnControlService = null;

        for (int i = 0; i < mServiceList.size()
                && (mOadService == null || mConnControlService == null); i++) {
            BluetoothGattService srv = mServiceList.get(i);
            if (srv.getUuid().equals(GattInfo.OAD_SERVICE_UUID)) {
                mOadService = srv;
            }
            if (srv.getUuid().equals(GattInfo.CC_SERVICE_UUID)) {
                mConnControlService = srv;
            }
        }
    }
    private void enableDataCollection(boolean enable) {
        setBusy(true);
        enableSensors(enable);
        enableNotifications(enable);
        setBusy(false);
    }
    private void getFirmwareRevison() {
        UUID servUuid = SensorTagGatt.UUID_DEVINFO_SERV;
        UUID charUuid = SensorTagGatt.UUID_DEVINFO_FWREV;
        BluetoothGattService serv = mBtGatt.getService(servUuid);
        BluetoothGattCharacteristic charFwrev = serv.getCharacteristic(charUuid);

        // Write the calibration code to the configuration registers
        mBtLeService.readCharacteristic(charFwrev);
        mBtLeService.waitIdle(GATT_TIMEOUT);

    }

    private void setError(String txt) {
        setBusy(false);
        Toast.makeText(this, txt, Toast.LENGTH_LONG).show();
    }

    void setBusy(boolean f) {
        if (f != mBusy)
        {
            mBusy = f;
        }
    }

    private void enableSensors(boolean f) {
        final boolean enable = f;

        for (Sensor sensor : mEnabledSensors) {
            UUID servUuid = sensor.getService();
            UUID confUuid = sensor.getConfig();

            // Skip keys
            if (confUuid == null)
                break;
/*
                // Barometer calibration
                if (confUuid.equals(SensorTagGatt.UUID_BAR_CONF) && enable) {
                    calibrateBarometer();
                }
 */           

            BluetoothGattService serv = mBtGatt.getService(servUuid);
            if (serv != null) {
                BluetoothGattCharacteristic charac = serv.getCharacteristic(confUuid);
                byte value = enable ? sensor.getEnableSensorCode()
                        : Sensor.DISABLE_SENSOR_CODE;
                if (mBtLeService.writeCharacteristic(charac, value)) {
                    mBtLeService.waitIdle(GATT_TIMEOUT);
                } else {
                    setError("Sensor config failed: " + serv.getUuid().toString());
                    break;
                }
            }
        }
    }

    private void enableNotifications(boolean f) {
        final boolean enable = f;

        for (Sensor sensor : mEnabledSensors) {
            UUID servUuid = sensor.getService();
            UUID dataUuid = sensor.getData();
            BluetoothGattService serv = mBtGatt.getService(servUuid);
            if (serv != null) {
                BluetoothGattCharacteristic charac = serv.getCharacteristic(dataUuid);

                if (mBtLeService.setCharacteristicNotification(charac, enable)) {
                    mBtLeService.waitIdle(GATT_TIMEOUT);
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    setError("Sensor notification failed: " + serv.getUuid().toString());
                    break;
                }
            }
        }
    }



}