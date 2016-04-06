package app.kimi.camerarobot.ioio;


import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import app.kimi.camerarobot.R;
import app.kimi.camerarobot.constant.Command;
import app.kimi.camerarobot.constant.DirectionState;
import app.kimi.camerarobot.constant.ExtraKey;
import app.kimi.camerarobot.utils.Utilities;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;




public class IOIOControllerActivity extends IOIOActivity implements CameraManager.CameraManagerListener, Callback, ConnectionManager.ConnectionListener, ConnectionManager.ControllerCommandListener, ConnectionManager.SendCommandListener {
    private static final int TAKE_PICTURE_COOLDOWN = 1000;
    public final String ACTION_USB_PERMISSION = "app.kimi.camerarobot.USB_PERMISSION";
    Button startButton, stopButton;
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
    private RelativeLayout layoutParent;
    private TextView tvMovementSpeed;
    private TextView tvIpAddress;
    private Button btnMoveForward;
    private Button btnMoveForwardLeft;
    private Button btnMoveForwardRight;
    private Button btnMoveDown;
    private Button btnMoveDownLeft;
    private Button btnMoveDownRight;
    private Button btnMoveRight;
    private Button btnMoveLeft;
    private SurfaceView surfacePreview;
    private int movementSpeed = 0;
    private int lastPictureTakenTime = 0;
    private int directionState = DirectionState.STOP;
    private ConnectionManager connectionManager;
    private CameraManager cameraManager;
    private OrientationManager orientationManager;
    private int imageQuality;
    private boolean isConnected = false;

    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_ioio);

        String password = getIntent().getExtras().getString(ExtraKey.OWN_PASSWORD);
        int selectedPreviewSize = getIntent().getExtras().getInt(ExtraKey.PREVIEW_SIZE);
        imageQuality = getIntent().getExtras().getInt(ExtraKey.QUALITY);

        btnMoveForward = (Button) findViewById(R.id.btn_move_forward);
        btnMoveForwardLeft = (Button) findViewById(R.id.btn_move_forward_left);
        btnMoveForwardRight = (Button) findViewById(R.id.btn_move_forward_right);
        btnMoveDown = (Button) findViewById(R.id.btn_move_backward);
        btnMoveDownLeft = (Button) findViewById(R.id.btn_move_backward_left);
        btnMoveDownRight = (Button) findViewById(R.id.btn_move_backward_right);
        btnMoveRight = (Button) findViewById(R.id.btn_move_right);
        btnMoveLeft = (Button) findViewById(R.id.btn_move_left);

        tvMovementSpeed = (TextView) findViewById(R.id.tv_movement_speed);

        tvIpAddress = (TextView) findViewById(R.id.tv_ip_address);
        tvIpAddress.setText(Utilities.getCurrentIP(this));

        surfacePreview = (SurfaceView) findViewById(R.id.surface_preview);
        surfacePreview.getHolder().addCallback(this);
        surfacePreview.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);


        layoutParent = (RelativeLayout) findViewById(R.id.layout_parent);
        layoutParent.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                cameraManager.requestAutoFocus();
            }
        });
        connectionManager = new ConnectionManager(password);
        connectionManager.start();
        connectionManager.setConnectionListener(this);
        connectionManager.setCommandListener(this);
        connectionManager.setSendCommandListener(this);

        orientationManager = new OrientationManager(this);
        cameraManager = new CameraManager(selectedPreviewSize);
        cameraManager.setCameraManagerListener(this);


        usbManager = (UsbManager) getSystemService(this.USB_SERVICE);
        startButton = (Button) findViewById(R.id.buttonStart);


        stopButton = (Button) findViewById(R.id.buttonStop);


        setUiEnabled(false);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);


    }



    public void onStop() {
        super.onStop();
        connectionManager.stop();
        finish();
    }

    public void clearCheckBox() {
        btnMoveForward.setPressed(false);
        btnMoveForwardLeft.setPressed(false);
        btnMoveForwardRight.setPressed(false);
        btnMoveDown.setPressed(false);
        btnMoveDownLeft.setPressed(false);
        btnMoveDownRight.setPressed(false);
        btnMoveRight.setPressed(false);
        btnMoveLeft.setPressed(false);
    }

    public void updateMovementSpeed(int speed) {
        movementSpeed = speed;
        tvMovementSpeed.setText(getString(R.string.movement_speed, speed));
    }

    @Override
    public void onDataIncoming() {
        clearCheckBox();
    }

    @Override
    public void onControllerConnected() {
        isConnected = true;
        connectionManager.sendCommand(Command.ACCEPT_CONNECTION);
    }

    @Override
    public void onWrongPassword() {
        connectionManager.sendCommand(Command.WRONG_PASSWORD);
        connectionManager.restart();
    }

    @Override
    public void onControllerDisconnected() {
        showToast(getString(R.string.connection_down));
    }

    @Override
    public void onControllerClosed() {
        isConnected = false;
    }

    @Override
    public void onFlashCommand(String command) {
        if (cameraManager.isFlashAvailable()) {
            if (command.equals(Command.LED_ON)) {
                cameraManager.requestFlashOn();
            } else if (command.equals(Command.LED_OFF)) {
                cameraManager.requestFlashOff();
            }
        } else {
            connectionManager.sendCommand(Command.FLASH_UNAVAILABLE);
        }
    }







    @Override
    public void onRequestTakePicture() {






        double currentTimeSeconds = System.currentTimeMillis();
        if (currentTimeSeconds - lastPictureTakenTime > TAKE_PICTURE_COOLDOWN) {
            lastPictureTakenTime = (int) currentTimeSeconds;
            cameraManager.requestTakePicture();
        }
    }

    @Override
    public void onRequestAutoFocus() {

        cameraManager.requestAutoFocus();




    }

    @Override
    public void onMoveForwardCommand(int movementSpeed) {
        btnMoveForward.setPressed(true);
        directionState = DirectionState.UP;
        updateMovementSpeed(movementSpeed);
        String string = "ledh";
        serialPort.write(string.getBytes());

    }

    @Override
    public void onMoveForwardRightCommand(int movementSpeed) {
        btnMoveForwardRight.setPressed(true);
        directionState = DirectionState.UPRIGHT;
        updateMovementSpeed(movementSpeed);
        String string = "ledl";
        serialPort.write(string.getBytes());
    }

    @Override
    public void onMoveForwardLeftCommand(int movementSpeed) {
        btnMoveForwardLeft.setPressed(true);
        directionState = DirectionState.UPLEFT;
        updateMovementSpeed(movementSpeed);
        String string = "ledh";
        serialPort.write(string.getBytes());
    }

    @Override
    public void onMoveBackwardCommand(int movementSpeed) {
        btnMoveDown.setPressed(true);
        directionState = DirectionState.DOWN;
        updateMovementSpeed(movementSpeed);
    }

    @Override
    public void onMoveBackwardRightCommand(int movementSpeed) {
        btnMoveDownRight.setPressed(true);
        directionState = DirectionState.DOWNRIGHT;
        updateMovementSpeed(movementSpeed);
    }

    @Override
    public void onMoveBackwardLeftCommand(int movementSpeed) {
        btnMoveDownLeft.setPressed(true);
        directionState = DirectionState.DOWNLEFT;
        updateMovementSpeed(movementSpeed);
    }

    @Override
    public void onMoveLeftCommand(int movementSpeed) {
        btnMoveLeft.setPressed(true);
        directionState = DirectionState.LEFT;
        updateMovementSpeed(movementSpeed);
    }

    @Override
    public void onMoveRightCommand(int movementSpeed) {
        btnMoveRight.setPressed(true);
        directionState = DirectionState.RIGHT;
        updateMovementSpeed(movementSpeed);
    }

    @Override
    public void onMoveStopCommand() {
        directionState = DirectionState.STOP;
        updateMovementSpeed(0);
    }

    @Override
    public void onSendCommandSuccess() {
    }

    @Override
    public void onSendCommandFailure() {
        isConnected = false;
    }

    @SuppressWarnings("deprecation")
    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
        if (surfacePreview == null)
            return;

        cameraManager.stopCameraPreview();
        cameraManager.initCameraParameter();

        setupPreviewLayout();

        cameraManager.setCameraOrientation(orientationManager.getOrientation());
        cameraManager.startCameraPreview(surfacePreview);
    }

    @SuppressWarnings("deprecation")
    public void setupPreviewLayout() {
        Display display = getWindowManager().getDefaultDisplay();
        LayoutParams lp = layoutParent.getLayoutParams();

        float previewWidth = cameraManager.getPreviewSize().width;
        float previewHeight = cameraManager.getPreviewSize().height;

        int orientation = orientationManager.getOrientation();
        float ratio = 0;
        if (orientation == OrientationManager.LANDSCAPE_NORMAL
                || orientation == OrientationManager.LANDSCAPE_REVERSE) {
            ratio = previewWidth / previewHeight;
        } else if (orientation == OrientationManager.PORTRAIT_NORMAL
                || orientation == OrientationManager.PORTRAIT_REVERSE) {
            ratio = previewHeight / previewWidth;
        }
        if ((int) ((float) surfacePreview.getWidth() / ratio) >= display.getHeight()) {
            lp.height = (int) ((float) surfacePreview.getWidth() / ratio);
            lp.width = surfacePreview.getWidth();
        } else {
            lp.height = surfacePreview.getHeight();
            lp.width = (int) ((float) surfacePreview.getHeight() * ratio);
        }

        layoutParent.setLayoutParams(lp);
        int locationX = (int) (lp.width / 2.0 - surfacePreview.getWidth() / 2.0);
        layoutParent.animate().translationX(-locationX);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        cameraManager.createCameraInstance(holder);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        cameraManager.destroyCameraInstance();
    }

    @Override
    public void onPictureTaken(String filename, String path) {
        connectionManager.sendCommand(Command.SNAP);
    }

    @Override
    public void onPreviewTaken(Bitmap bitmap) {
        if (isConnected) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, imageQuality, bos);
            connectionManager.sendImageData(bos.toByteArray());
        }
    }

    @Override
    public void onPreviewOutOfMemory(OutOfMemoryError e) {
        e.printStackTrace();
        showToast(getString(R.string.out_of_memory));
        finish();
    }

    public void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    protected IOIOLooper createIOIOLooper() {
        return new Looper();
    }

    public void setUiEnabled(boolean bool) {
        startButton.setEnabled(!bool);

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



    public void onClickStop(View view) {
        setUiEnabled(false);
        serialPort.close();


    }

    class Looper extends BaseIOIOLooper {
        DigitalOutput D1A, D1B, D2A, D2B, D3A, D3B, D4A, D4B;
        PwmOutput PWM1, PWM2, PWM3, PWM4;

        protected void setup() throws ConnectionLostException {
            ioio_.openDigitalOutput(0, false);
            D1A = ioio_.openDigitalOutput(1, false);
            D1B = ioio_.openDigitalOutput(2, false);
            D2A = ioio_.openDigitalOutput(4, false);
            D2B = ioio_.openDigitalOutput(5, false);
            D3A = ioio_.openDigitalOutput(16, false);
            D3B = ioio_.openDigitalOutput(17, false);
            D4A = ioio_.openDigitalOutput(18, false);
            D4B = ioio_.openDigitalOutput(19, false);
            PWM1 = ioio_.openPwmOutput(3, 100);
            PWM1.setDutyCycle(0);
            PWM2 = ioio_.openPwmOutput(6, 100);
            PWM2.setDutyCycle(0);
            PWM3 = ioio_.openPwmOutput(13, 100);
            PWM3.setDutyCycle(0);
            PWM4 = ioio_.openPwmOutput(14, 100);
            PWM4.setDutyCycle(0);

            showToastFromIOIO(getString(R.string.connected));
        }

        public void loop() throws ConnectionLostException, InterruptedException {
            if (directionState == DirectionState.UP) {
                PWM1.setDutyCycle((float) movementSpeed / 100);
                PWM2.setDutyCycle((float) movementSpeed / 100);
                PWM3.setDutyCycle((float) movementSpeed / 100);
                PWM4.setDutyCycle((float) movementSpeed / 100);
                D1A.write(true);
                D1B.write(false);
                D2A.write(true);
                D2B.write(false);
                D3A.write(true);
                D3B.write(false);
                D4A.write(true);
                D4B.write(false);
            } else if (directionState == DirectionState.DOWN) {
                PWM1.setDutyCycle((float) movementSpeed / 100);
                PWM2.setDutyCycle((float) movementSpeed / 100);
                PWM3.setDutyCycle((float) movementSpeed / 100);
                PWM4.setDutyCycle((float) movementSpeed / 100);
                D1A.write(false);
                D1B.write(true);

                D2A.write(false);
                D2B.write(true);
                D3A.write(false);
                D3B.write(true);
                D4A.write(false);
                D4B.write(true);
            } else if (directionState == DirectionState.LEFT) {
                PWM1.setDutyCycle((float) movementSpeed / 100);
                PWM2.setDutyCycle((float) movementSpeed / 100);
                PWM3.setDutyCycle((float) movementSpeed / 100);
                PWM4.setDutyCycle((float) movementSpeed / 100);
                D1A.write(false);
                D1B.write(true);
                D2A.write(false);
                D2B.write(true);
                D3A.write(true);
                D3B.write(false);
                D4A.write(true);
                D4B.write(false);
            } else if (directionState == DirectionState.RIGHT) {
                PWM1.setDutyCycle((float) movementSpeed / 100);
                PWM2.setDutyCycle((float) movementSpeed / 100);
                PWM3.setDutyCycle((float) movementSpeed / 100);
                PWM4.setDutyCycle((float) movementSpeed / 100);
                D1A.write(true);
                D1B.write(false);
                D2A.write(true);
                D2B.write(false);
                D3A.write(false);
                D3B.write(true);
                D4A.write(false);
                D4B.write(true);
            } else if (directionState == DirectionState.UPRIGHT) {
                PWM1.setDutyCycle((((float) movementSpeed / (float) 1.5) + 20) / 100);
                PWM2.setDutyCycle((((float) movementSpeed / (float) 1.5) + 20) / 100);
                PWM3.setDutyCycle((((float) movementSpeed / (float) 1.5) - 20) / 100);
                PWM4.setDutyCycle((((float) movementSpeed / (float) 1.5) - 20) / 100);
                D1A.write(true);
                D1B.write(false);
                D2A.write(true);
                D2B.write(false);
                D3A.write(true);
                D3B.write(false);
                D4A.write(true);
                D4B.write(false);
            } else if (directionState == DirectionState.UPLEFT) {
                PWM1.setDutyCycle((((float) movementSpeed / (float) 1.5) - 20) / 100);
                PWM2.setDutyCycle((((float) movementSpeed / (float) 1.5) - 20) / 100);
                PWM3.setDutyCycle((((float) movementSpeed / (float) 1.5) + 20) / 100);
                PWM4.setDutyCycle((((float) movementSpeed / (float) 1.5) + 20) / 100);
                D1A.write(true);
                D1B.write(false);
                D2A.write(true);
                D2B.write(false);
                D3A.write(true);
                D3B.write(false);
                D4A.write(true);
                D4B.write(false);
            } else if (directionState == DirectionState.DOWNRIGHT) {
                PWM1.setDutyCycle((((float) movementSpeed / (float) 1.5) + 20) / 100);
                PWM2.setDutyCycle((((float) movementSpeed / (float) 1.5) + 20) / 100);
                PWM3.setDutyCycle((((float) movementSpeed / (float) 1.5) - 20) / 100);
                PWM4.setDutyCycle((((float) movementSpeed / (float) 1.5) - 20) / 100);
                D1A.write(false);
                D1B.write(true);
                D2A.write(false);
                D2B.write(true);
                D3A.write(false);
                D3B.write(true);
                D4A.write(false);
                D4B.write(true);
            } else if (directionState == DirectionState.DOWNLEFT) {
                PWM1.setDutyCycle((((float) movementSpeed / (float) 1.5) - 20) / 100);
                PWM2.setDutyCycle((((float) movementSpeed / (float) 1.5) - 20) / 100);
                PWM3.setDutyCycle((((float) movementSpeed / (float) 1.5) + 20) / 100);
                PWM4.setDutyCycle((((float) movementSpeed / (float) 1.5) + 20) / 100);
                D1A.write(false);
                D1B.write(true);
                D2A.write(false);
                D2B.write(true);
                D3A.write(false);
                D3B.write(true);
                D4A.write(false);
                D4B.write(true);
            } else if (directionState == DirectionState.STOP) {
                PWM1.setDutyCycle(0);
                PWM2.setDutyCycle(0);
                PWM3.setDutyCycle(0);
                PWM4.setDutyCycle(0);
                D1A.write(false);
                D1B.write(false);
                D2A.write(false);
                D2B.write(false);
                D3A.write(false);
                D3B.write(false);
                D4A.write(false);
                D4B.write(false);
            }

            Thread.sleep(20);
        }

        public void disconnected() {
            showToastFromIOIO(getString(R.string.disconnected));
        }

        public void incompatible() {
            showToastFromIOIO(getString(R.string.incompatible_firmware));
        }

        public void showToastFromIOIO(final String mesage) {
            runOnUiThread(new Runnable() {
                public void run() {
                    showToast(mesage);
                }
            });
        }
    }
















}


















