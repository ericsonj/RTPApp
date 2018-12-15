package net.ericsonj.rtpapp;

import android.Manifest;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.StrictMode;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = MainActivity.class.getSimpleName();

    private Handler mainHandler;

    private MainService service;
    protected LocalServiceConnection localServiceConnection;
    protected boolean isBound;

    private View start;
    private View login;
    private EditText editTextId;
    private EditText editTextName;
    private ImageButton buttonlogin;
    private TextView textViewStart;
    private ImageButton imageButtonMIc;
    private boolean micActive;
    private ImageButton imageButtonSpeaker;
    private boolean speakerActive;
    private CheckBox checkBoxCCO;
    StopMic stopMic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);


        start = findViewById(R.id.start);
        login = findViewById(R.id.login);
        speakerActive = true;

        editTextId = (EditText) findViewById(R.id.editTextId);
        editTextName = (EditText) findViewById(R.id.editTextName);
        editTextName.setVisibility(View.GONE);
        buttonlogin = (ImageButton) findViewById(R.id.buttonLogin);
        buttonlogin.setScaleType(ImageView.ScaleType.CENTER);
        textViewStart = (TextView) findViewById(R.id.textViewStart);
        checkBoxCCO = (CheckBox) findViewById(R.id.checkboxCCO);
        checkBoxCCO.setVisibility(View.GONE);

//        Random random = new Random(System.currentTimeMillis());
//        int id = 1001;
//        editTextId.setText(String.valueOf(id));
//        editTextName.setText("USER-" + String.valueOf(id));

        imageButtonMIc = (ImageButton) findViewById(R.id.imageButtonMic);
        imageButtonSpeaker = (ImageButton) findViewById(R.id.imageButtonSpeaker);
        mainHandler = new Handler(getMainLooper());
        stopMic = new StopMic();
        initButtons();
        getPermissions();
        bindService();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.manu_main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            // launch settings activity
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Binder Service
     */
    public void bindService() {
        Intent intentService = new Intent(this, MainService.class);
        startService(intentService);
        if (!isBound) {
            Log.d(TAG, "try service connect");
            localServiceConnection = new MainActivity.LocalServiceConnection();
            bindService(intentService, localServiceConnection, Service.BIND_AUTO_CREATE);

            isBound = true;
            Log.d(TAG, "service connect");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");
        if (isBound) {
            if (service != null && service.isRun()) {
                start.setVisibility(View.VISIBLE);
                login.setVisibility(View.GONE);
            } else {
                start.setVisibility(View.GONE);
                login.setVisibility(View.VISIBLE);
            }
        } else {
            start.setVisibility(View.GONE);
            login.setVisibility(View.VISIBLE);
        }
    }

    private void getPermissions() {
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO};
        ActivityCompat.requestPermissions(this, permissions, 123);
    }

    private void initButtons() {
        buttonlogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start.setVisibility(View.VISIBLE);
                login.setVisibility(View.GONE);
                String id = editTextId.getText().toString();
                String name = id + "@ericsonj.net";
                textViewStart.setText("Start RTPApp " + name);
                service.initAudio();
                boolean isCCO = checkBoxCCO.isChecked();
                service.startAudioManager(isCCO, name, id);
                service.setRun(true);
            }
        });

        imageButtonMIc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (micActive) {
                    service.activeMicMute(true);
                    imageButtonMIc.setImageResource(R.mipmap.mic_b_off);
                    imageButtonMIc.setBackgroundResource(R.drawable.round_button_mic);
                    micActive = false;
                    stopMic.setFinish(true);
                } else {
                    Thread th = new Thread(stopMic);
                    th.start();
                    service.activeMicMute(false);
                    imageButtonMIc.setImageResource(R.mipmap.mic_b_on);
                    imageButtonMIc.setBackgroundResource(R.drawable.round_button_start);
                    micActive = true;
                }
            }
        });

        imageButtonSpeaker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (speakerActive) {
                    service.activeSpeaker(false);
                    imageButtonSpeaker.setImageResource(R.drawable.speaker_b_off);
                    speakerActive = false;
                } else {
                    service.activeSpeaker(true);
                    imageButtonSpeaker.setImageResource(R.drawable.speaker_b_on);
                    speakerActive = true;
                }
            }
        });

    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "--- onDestroy ---");
        unBindService();
        super.onDestroy();
    }

    public void unBindService() {
        if (isBound) {
            unbindService(localServiceConnection);
            isBound = false;
            Log.d(TAG, "--- unBindService ---");
        }
    }


    public class LocalServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder) {
            Log.d(TAG, "=== onServiceConnected ===");
            service = ((MainService.MyServiceBinder) iBinder).getService();
            MainActivity.this.onStart();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }

    public class StopMic implements Runnable {

        private boolean finish;

        @Override
        public void run() {
            Log.d(TAG, "START STOP");
            int count = 0;
            finish = false;
            while (!finish) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                count++;
                Log.d(TAG, "STOP " + count);
                if (count == 30) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (micActive) {
                                service.activeMicMute(true);
                                imageButtonMIc.setImageResource(R.mipmap.mic_b_off);
                                imageButtonMIc.setBackgroundResource(R.drawable.round_button_mic);
                                micActive = false;
                            }

                        }
                    });
                    break;
                }
            }
        }

        public boolean isFinish() {
            return finish;
        }

        public void setFinish(boolean stop) {
            this.finish = stop;
        }
    }

}
