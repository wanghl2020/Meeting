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

package com.tfkj.meeting.meeting.service;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;


import com.tfkj.meeting.meeting.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CService {
    // Debugging
    private static final String TAG = "BluetoothChatClient";

    private final BluetoothAdapter mAdapter;
    private ConnectThread mConnectThread;
    private int mState;
    private int mNewState;
    private volatile static CService instance = null;

    //所有接入客服端的连接线程---》 一个客服端连接多个服务端
    private HashMap<String, ConnectedThread> serviceConnectedThread = new HashMap<>();

    private ArrayList<String> macAddresses;
    private int connectDeviceIndex;

    private CService() {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = Constants.STATE_NONE;
        mNewState = mState;
    }

    public static CService getInstance() {

        if (instance == null) {
            synchronized (CService.class) {
                if (instance == null) {
                    instance = new CService();
                }
            }
        }
        return instance;
    }


    public synchronized int getState() {
        return mState;
    }

    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        mState = getState();
        Log.d(TAG, "updateUserInterfaceTitle() " + mNewState + " -> " + mState);
        mNewState = mState;
    }

    public synchronized void connect(BluetoothDevice device, boolean secure) {
        Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == Constants.STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();
        // Update UI title
        mState = getState();
        Log.d(TAG, "updateUserInterfaceTitle() " + mNewState + " -> " + mState);
        mNewState = mState;
    }

    /**
     * 同时连接多台蓝牙设备
     * @param macAddresses 蓝牙MAC地址
     */
    public void connectDevices(ArrayList<String> macAddresses) {
        this.macAddresses = macAddresses;
        if(macAddresses!=null && macAddresses.size()>0) {
            connectDeviceIndex = 0;
            connectNextDevice();
        }
    }

    /**
     * 连接下一台蓝牙设备
     */
    private void connectNextDevice(){
        if(macAddresses.size()>connectDeviceIndex){
            BluetoothDevice device = mAdapter.getRemoteDevice(macAddresses.get(connectDeviceIndex));
            connect(device, true);
            connectDeviceIndex ++;
        }
    }

    /**
     * 蓝牙设备连接成功
     *
     * @param socket
     * @param device
     * @param socketType
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        ConnectedThread mConnectedThread = new ConnectedThread(socket, device.getAddress(), socketType);
        mConnectedThread.start();

        serviceConnectedThread.put(device.getAddress(), mConnectedThread);

        Log.e(TAG,"Connected to " + device.getName());

        mState = getState();
        mNewState = mState;

        connectNextDevice();
    }

    /**
     * 停止所有的线程
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");

        //关闭所有服务端线程
        for (ConnectedThread connectedThread : serviceConnectedThread.values()) {
            connectedThread.cancel();
        }
        serviceConnectedThread.clear();

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        mState = Constants.STATE_NONE;
        // Update UI title
        mState = getState();
        Log.d(TAG, "updateUserInterfaceTitle() " + mNewState + " -> " + mState);
        mNewState = mState;
    }

    public void writeToAllService(byte[] out) {
        for (Map.Entry<String, ConnectedThread> item : serviceConnectedThread.entrySet()) {
            item.getValue().write(out);
        }
    }

    /**
     * 蓝色设备连接失败，多台设备时连接下一台
     */
    private void connectionFailed() {

        Log.e(TAG, "Unable to connect device");

        mState = Constants.STATE_NONE;
        // Update UI title

        mState = getState();
        mNewState = mState;

        // Start the service over to restart listening mode
        CService.this.start();

        connectNextDevice();
    }

    /**
     * 断开连接
     */
    private void connectionLost() {

        Log.e(TAG, "Device connection was lost");

        mState = Constants.STATE_NONE;
        // Update UI title

        mState = getState();
        mNewState = mState;

        // Start the service over to restart listening mode
        CService.this.start();
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     *
     * 连接线程，直到连接成功或者失败
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(
                            Constants.MY_UUID_SECURE);
                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(
                            Constants.MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
            mState = Constants.STATE_CONNECTING;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() " + mSocketType +
                            " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (CService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     *
     * 已经连接成功。此线程为与远程蓝牙设备通信线程。
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private String macAddress;

        public ConnectedThread(BluetoothSocket socket, String macAddress, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            this.macAddress = macAddress;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            mState = Constants.STATE_CONNECTED;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (mState == Constants.STATE_CONNECTED) {
                try {
                    bytes = mmInStream.read(buffer);
                    String message = new String(buffer, 0, bytes);
                    Log.e(TAG, "read message:" + message);

                    Log.e(TAG, "from SService to CService:" +message);

                    for (Map.Entry<String, ConnectedThread> item : serviceConnectedThread.entrySet()) {
                        if (!item.getKey().equals(macAddress)) {
                            item.getValue().write(buffer);
                        }
                    }
                    SService.getInstance().writeToAllClient(message.getBytes());
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
                Log.e(TAG, "write message:" + new String(buffer));
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
