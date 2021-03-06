package app.kimi.camerarobot.menu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;



import app.kimi.camerarobot.R;
import app.kimi.camerarobot.controller.ControllerSetupActivity;
import app.kimi.camerarobot.ioio.IOIOSetupActivity;

public class MenuActivity extends Activity {
	private Button btnController;
	private Button btnIoio;
	
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
		setContentView(R.layout.activity_main);

		btnController = (Button)findViewById(R.id.btn_controller);
        btnController.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(MenuActivity.this, ControllerSetupActivity.class);
				startActivity(intent);
			}
		});

        btnIoio = (Button)findViewById(R.id.btn_ioio);
        btnIoio.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(MenuActivity.this, IOIOSetupActivity.class);
				startActivity(intent);
			}
		});
    }
}
