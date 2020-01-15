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
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.tfkj.meeting.meeting.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class SService {
    // Debugging
    private static final String TAG = "BluetoothChatService";

    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "BluetoothChatSecure";
    private static final String NAME_INSECURE = "BluetoothChatInsecure";

    // Member fields
    private final BluetoothAdapter mAdapter;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private int mState;

    //所有接入的客户端连接线程
    private HashMap<String, ConnectedThread> clientConnectedThread = new HashMap<>();

    private volatile static SService instance = null;

    private SService() {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = Constants.STATE_NONE;
    }

    public static SService getInstance() {
        if (instance == null) {
            synchronized (SService.class) {
                if (instance == null) {
                    instance = new SService();
                }
            }
        }
        return instance;
    }

    public synchronized int getState() {
        return mState;
    }

    /**
     * 启动接受线程等待蓝牙客户端连接本设备
     */
    public synchronized void start() {
        Log.d(TAG, "start");

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread(true);
            mSecureAcceptThread.start();
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread(false);
            mInsecureAcceptThread.start();
        }

        mState = getState();
    }

    /**
     * 客服端连接成功后，创建用于通信的线程
     * @param socket
     * @param device
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "connected, device address:" + device.getAddress());

        ConnectedThread mConnectedThread = new ConnectedThread(socket, device);
        mConnectedThread.start();
        clientConnectedThread.put(device.getAddress(), mConnectedThread);

        Log.e(TAG, "Connected to " + device.getName());

        mState = getState();
    }

    /**
     * 停止所有线程
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");

        //关闭所有客户端线程
        for (ConnectedThread connectedThread : clientConnectedThread.values()) {
            connectedThread.cancel();
        }
        clientConnectedThread.clear();
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }
        mState = Constants.STATE_NONE;

        mState = getState();

    }

    /**
     * 向所有客服端发送消息
     * @param out
     */
    public void writeToAllClient(byte[] out) {
        for (Map.Entry<String, ConnectedThread> item : clientConnectedThread.entrySet()) {
            item.getValue().write(out, true);
        }
    }

    /**
     * 创建接受线程，等待客服端连接，新建服务端ServerSocket
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
                            Constants.MY_UUID_SECURE);
                } else {
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(
                            NAME_INSECURE, Constants.MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
            mState = Constants.STATE_LISTEN;
        }

        public void run() {
            Log.d(TAG, "Socket Type: " + mSocketType +
                    "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket = null;

            // Listen to the server socket if we're not connected
            while (true) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    connected(socket, socket.getRemoteDevice());
                }
            }
            Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);
        }

        public void cancel() {
            Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
            }
        }
    }

    /**
     * 通信线程  连接成功后，通过此线程与客服端进行通信
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        //连接的蓝牙设备，此处为客户端
        private final BluetoothDevice mmBluetoothDevice;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket, BluetoothDevice bluetoothDevice) {
            Log.d(TAG, "create ConnectedThread: " + bluetoothDevice.getAddress());
            mmSocket = socket;
            mmBluetoothDevice = bluetoothDevice;
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
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    //转发消息给其他客户端
                    for (Map.Entry<String, ConnectedThread> item : clientConnectedThread.entrySet()) {
                        if (!item.getKey().equals(mmBluetoothDevice.getAddress())) {
                            item.getValue().write((new String(buffer, 0, bytes)).getBytes(), false);
                        }
                    }

                    String message = new String(buffer, 0, bytes);
                    Log.e(TAG, "read message:" + message);

                    Log.e(TAG, "from CService to SService:" +message);
                    CService.getInstance().writeToAllService(message.getBytes());
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    clientConnectedThread.remove(mmBluetoothDevice.getAddress());
                    break;
                }
            }
        }

        public void write(byte[] buffer, boolean isUpdateUI) {
            try {
                mmOutStream.write(buffer);

                if (isUpdateUI) {
                    Log.e(TAG, "write message:"  + new String(buffer));
                }
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
