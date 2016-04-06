package app.kimi.camerarobot.ioio;

import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.kimi.camerarobot.R;
import app.kimi.camerarobot.constant.ExtraKey;


public class IOIOSetupActivity extends Activity implements OnClickListener, OnSeekBarChangeListener {
    public final String ACTION_USB_PERMISSION = "app.kimi.camerarobot.USB_PERMISSION";
    Button startButton, sendButton, stopButton;
    UsbManager usbManager;
    UsbDevice device;
    UsbSerialDevice serialPort;
    UsbDeviceConnection connection;
    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {
            String data = null;
            try {
                data = new String(arg0, "UTF-8");
                data.concat("/n");

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }


        }
    };
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {
                    connection = usbManager.openDevice(device);
                    serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                    if (serialPort != null) {
                        if (serialPort.open()) { //Set Serial Connection Parameters.
                            setUiEnabled(true);
                            serialPort.setBaudRate(9600);
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            serialPort.read(mCallback);

                        } else {
                            Log.d("SERIAL", "PORT NOT OPEN");
                        }
                    } else {
                        Log.d("SERIAL", "PORT IS NULL");
                    }
                } else {
                    Log.d("SERIAL", "PERM NOT GRANTED");
                }
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                onClickStart(startButton);
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                onClickStop(stopButton);

            }
        }

        ;
    };
    private TextView tvImageQuality;
    private EditText etPassword;
    private SeekBar sbImageQuality;
    private Button btnOk;
    private Button btnPreviewSizeChooser;
    private ArrayList<String> previewSizeList;
    private int selectedSizePosition;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        setContentView(R.layout.activity_ioio_setup);

        SharedPreferences settings = getSharedPreferences(ExtraKey.SETUP_PREFERENCE, Context.MODE_PRIVATE);
        selectedSizePosition = settings.getInt(ExtraKey.PREVIEW_SIZE, 0);
        String password = settings.getString(ExtraKey.OWN_PASSWORD, "");
        int quality = settings.getInt(ExtraKey.QUALITY, 100);

        initCameraPreviewSize();

        etPassword = (EditText) findViewById(R.id.et_password);
        etPassword.setText(password);

        btnPreviewSizeChooser = (Button) findViewById(R.id.btn_preview_size_chooser);
        updateSeletedPreviewSize();
        btnPreviewSizeChooser.setOnClickListener(this);

        tvImageQuality = (TextView) findViewById(R.id.tv_image_quality);
        updateTextViewQuality(quality);

        sbImageQuality = (SeekBar) findViewById(R.id.sb_image_quality);
        sbImageQuality.setProgress(quality);
        sbImageQuality.setOnSeekBarChangeListener(this);

        btnOk = (Button) findViewById(R.id.btn_ok);
        btnOk.setOnClickListener(this);


        usbManager = (UsbManager) getSystemService(this.USB_SERVICE);
        startButton = (Button) findViewById(R.id.buttonStart);
        sendButton = (Button) findViewById(R.id.buttonSend);

        stopButton = (Button) findViewById(R.id.buttonStop);


        setUiEnabled(false);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);




    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_preview_size_chooser) {
            createPreviewSizeChooserDialog();
        } else if (id == R.id.btn_ok) {
            confirmSetup();
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        saveImageQuality(progress);
        updateTextViewQuality(progress);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    public void updateSeletedPreviewSize() {
        String strSize = previewSizeList.get(selectedSizePosition);
        btnPreviewSizeChooser.setText(strSize);
    }

    public void updateTextViewQuality(int quality) {
        tvImageQuality.setText(getString(R.string.image_quality, quality));
    }

    public void savePassword(String password) {
        getPreferenceEditor().putString(ExtraKey.OWN_PASSWORD, password).apply();
    }

    public void saveImageQuality(int quality) {
        getPreferenceEditor().putInt(ExtraKey.QUALITY, quality).apply();
    }

    public void saveImagePreviewSize(int size) {
        getPreferenceEditor().putInt(ExtraKey.PREVIEW_SIZE, size).apply();
    }

    public SharedPreferences.Editor getPreferenceEditor() {
        SharedPreferences settings = getSharedPreferences(ExtraKey.SETUP_PREFERENCE, Context.MODE_PRIVATE);
        return settings.edit();
    }

    public void goToIOIOController() {
        Intent intent = new Intent(this, IOIOControllerActivity.class);
        intent.putExtra(ExtraKey.OWN_PASSWORD, etPassword.getText().toString());
        intent.putExtra(ExtraKey.PREVIEW_SIZE, selectedSizePosition);
        intent.putExtra(ExtraKey.QUALITY, sbImageQuality.getProgress());
        startActivity(intent);
    }

    @SuppressWarnings("deprecation")
    public void initCameraPreviewSize() {
        Camera mCamera;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
            mCamera = Camera.open();
        } else {
            mCamera = Camera.open(0);
        }
        Camera.Parameters params = mCamera.getParameters();
        initPreviewSizeList(params.getSupportedPreviewSizes());
        mCamera.release();
    }

    @SuppressWarnings("deprecation")
    public void initPreviewSizeList(List<Size> previewSize) {
        previewSizeList = new ArrayList<>();
        for (int i = 0; i < previewSize.size(); i++) {
            String str = previewSize.get(i).width + " x " + previewSize.get(i).height;
            previewSizeList.add(str);
        }
    }

    public void createPreviewSizeChooserDialog() {
        final Dialog dialogSize = new Dialog(this);
        dialogSize.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialogSize.setContentView(R.layout.dialog_camera_size);
        dialogSize.setCancelable(true);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.view_simple_textview, previewSizeList);
        ListView lvAvailablePreviewSize = (ListView) dialogSize.findViewById(R.id.lv_available_preview_size);
        lvAvailablePreviewSize.setAdapter(adapter);
        lvAvailablePreviewSize.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                selectedSizePosition = position;
                saveImagePreviewSize(position);
                updateSeletedPreviewSize();
                dialogSize.cancel();
            }
        });
        dialogSize.show();
    }

    public void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    public void confirmSetup() {
        String strPassword = etPassword.getText().toString();
        if (strPassword.length() != 0) {
            savePassword(strPassword);
            goToIOIOController();
        } else {
            showToast(getString(R.string.password_is_required));
        }
    }

    public void setUiEnabled(boolean bool) {
        startButton.setEnabled(!bool);
        sendButton.setEnabled(bool);
        stopButton.setEnabled(bool);


    }

    public void onClickStart(View view) {

        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                if (deviceVID == 0x2341)//Arduino Vendor ID
                {
                    PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(device, pi);
                    keep = false;
                } else {
                    connection = null;
                    device = null;
                }

                if (!keep)
                    break;
            }
        }


    }

    public void onClickSend(View view) {
        String string = "ledh";
        serialPort.write(string.getBytes());


    }

    public void onClickStop(View view) {
        setUiEnabled(false);
        serialPort.close();


    }





}







