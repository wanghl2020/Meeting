/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tfkj.meeting.meeting.device;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.tfkj.meeting.meeting.R;
import com.tfkj.meeting.meeting.service.CService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DeviceListActivity extends Activity {

    private static final String TAG = "DeviceListActivity";
    private BluetoothAdapter mBtAdapter;
    private ArrayAdapter<String> mNewDevicesArrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_device_list);

        setResult(Activity.RESULT_CANCELED);

        Button scanButton = (Button) findViewById(R.id.button_scan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doDiscovery();
                v.setVisibility(View.GONE);
            }
        });

        final SelectDeviceArrayAdapter pairedDevicesArrayAdapter =
                new SelectDeviceArrayAdapter(this, R.layout.device_name, new ArrayList<SelectDeviceBean>());
        mNewDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);

        mNewDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);

        ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
        pairedListView.setAdapter(pairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(mDeviceClickListener);

        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices) {

                SelectDeviceBean selectDeviceBean = new SelectDeviceBean();
                selectDeviceBean.setDeviceName(device.getName());
                selectDeviceBean.setDeviceAddress(device.getAddress());
                pairedDevicesArrayAdapter.add(selectDeviceBean);
            }
        } else {
            String noDevices = getResources().getText(R.string.none_paired).toString();
            pairedDevicesArrayAdapter.clear();
        }

        Button selectAllButton = (Button) findViewById(R.id.btn_select_all);
        selectAllButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pairedDevicesArrayAdapter.selectAll();
            }
        });

        Button startConnectButton = (Button) findViewById(R.id.btn_start_connect);
        startConnectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mBtAdapter.cancelDiscovery();

                // Get the device MAC address, which is the last 17 chars in the View

                List<SelectDeviceBean> data = pairedDevicesArrayAdapter.getData();

                ArrayList<String> macAddresses = new ArrayList<>();
                for (int i = 0; i < data.size(); i++) {
                    if(data.get(i).isSelected()) macAddresses.add(data.get(i).getDeviceAddress());
                }
                CService.getInstance().connectDevices(macAddresses);
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBtAdapter != null) {
            mBtAdapter.cancelDiscovery();
        }
        this.unregisterReceiver(mReceiver);
    }

    private void doDiscovery() {
        Log.d(TAG, "doDiscovery()");

        setProgressBarIndeterminateVisibility(true);
        setTitle(R.string.scanning);

        findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }

        mBtAdapter.startDiscovery();
    }

    private AdapterView.OnItemClickListener mDeviceClickListener
            = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // Cancel discovery because it's costly and we're about to connect
            mBtAdapter.cancelDiscovery();

            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            BluetoothDevice device = mBtAdapter.getRemoteDevice(address);
            CService.getInstance().connect(device, true);
            finish();
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceInfo = device.getName() + "\n" + device.getAddress();
                Log.i(TAG, deviceInfo);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    boolean isExists = false;
                    for (int i = 0; i < mNewDevicesArrayAdapter.getCount(); i++) {
                        if (deviceInfo.equals(mNewDevicesArrayAdapter.getItem(i))) {
                            isExists = true;
                            break;
                        }
                    }
                    if (!isExists) {
                        mNewDevicesArrayAdapter.add(deviceInfo);
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setProgressBarIndeterminateVisibility(false);
                setTitle(R.string.select_device);
                if (mNewDevicesArrayAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.none_found).toString();
                    mNewDevicesArrayAdapter.add(noDevices);
                }
            }
        }
    };

}
