package com.tfkj.meeting.meeting;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.tfkj.meeting.meeting.Test.TestActivity;
import com.tfkj.meeting.meeting.device.DeviceListActivity;
import com.tfkj.meeting.meeting.service.CService;
import com.tfkj.meeting.meeting.service.SService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int REQUEST_LOCATION_PERMISSION_CODE = 4;
    private BluetoothAdapter mBluetoothAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initBluetooth();
    }

    private void initBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
        }
        CService.getInstance();
        SService.getInstance();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (CService.getInstance().getState() == Constants.STATE_NONE) {
            CService.getInstance().start();
        }
        if (SService.getInstance().getState() == Constants.STATE_NONE) {
            SService.getInstance().start();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        CService.getInstance().stop();
        SService.getInstance().stop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secure_connect_scan:
                CService.getInstance().start();
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivity(serverIntent);

                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    initBluetooth();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Bluetooth was not enabled. Leaving ", Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION_PERMISSION_CODE) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "定位权限被拒绝，请手动开启！", Toast.LENGTH_SHORT).show();

                //打开系统的应用信息页面
                Intent intent = new Intent();
                intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + this.getPackageName()));
                startActivity(intent);
            }
        }
    }


    public void onClickSend(View view) {

        String message = "MAIN";
        CService.getInstance().writeToAllService(message.getBytes());
        SService.getInstance().writeToAllClient(message.getBytes());
    }

    public void onClickToTest(View view) {
        startActivity(new Intent(this, TestActivity.class));
    }
}