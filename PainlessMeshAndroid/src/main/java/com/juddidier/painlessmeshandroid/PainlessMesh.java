package com.juddidier.painlessmeshandroid;


import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.Semaphore;

public class PainlessMesh {
    protected Activity mainActivity;

    public PainlessMeshReceiver receiver = null;
    public PainlessMeshSender sender = null;
    public PainlessMeshScheduler scheduler = null;

    private Thread MeshConnThread = null;

    protected Socket connSocket = null;
    protected Semaphore connTerminate;
    private Boolean connected = false;

    protected long myMeshNodeId = 0;
    protected long apMeshNodeId = 0;
    protected int meshPort = 0;

    protected String myMacAddress;

    public ArrayList<Long> nodesList;

    protected MeshListener meshListener;
    public interface MeshListener {
        void onConnected();
        void onDisconnected();
        void onNodesChanged(ArrayList<Long> addedItems, ArrayList<Long> removedItems);
        void onReceivedMessage(long fromNode, JSONObject message);
    }

    public void setMeshListener(MeshListener listener) {
        this.meshListener = listener;
    }

    public PainlessMesh(Activity _mainActivity, int _meshPort) {
        Log.d("PainlessMesh", "PainlessMesh()");
        mainActivity = _mainActivity;
        meshPort = _meshPort;
        nodesList = new ArrayList<Long>();
    }

    private long createMeshId(String macAddress) {
        Log.d("PainlessMesh", "createMeshId()");
        long calcNodeId = 0;
        String[] macAddrParts = macAddress.split(":");
        if (macAddrParts.length == 6) {
            try {
                long number = Long.valueOf(macAddrParts[2], 16);
                if (number < 0) {
                    number = number * -1;
                }
                calcNodeId = number * 256 * 256 * 256;
                number = Long.valueOf(macAddrParts[3], 16);
                if (number < 0) {
                    number = number * -1;
                }
                calcNodeId += number * 256 * 256;
                number = Long.valueOf(macAddrParts[4], 16);
                if (number < 0) {
                    number = number * -1;
                }
                calcNodeId += number * 256;
                number = Long.valueOf(macAddrParts[5], 16);
                if (number < 0) {
                    number = number * -1;
                }
                calcNodeId += number;
            } catch (NullPointerException e) {
                calcNodeId = -1;
                Log.e("PainlessMesh.createMeshId()", ""+e.getMessage());
            }
        }
        return calcNodeId;
    }

    private String askWifiMacAddress() throws SocketException {
        Log.d("PainlessMesh","askWifiMacAddress()");
        try {
            Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
            while (nifs.hasMoreElements()) {
                NetworkInterface nif = nifs.nextElement();
                if (nif.getName().equals("wlan0")) {
                    byte[] macBytes = nif.getHardwareAddress();
                    myMacAddress = String.format("%02x:%02x:%02x:%02x:%02x:%02x", macBytes[0], macBytes[1], macBytes[2], macBytes[3], macBytes[4], macBytes[5]);
                }
            }
        } catch (Exception e) {
            Log.e("PainlessMesh.askWifiMacAddress()", ""+e.getMessage());
            myMacAddress = "";
            throw e;
        }
        return myMacAddress;
    }

    private String askApMacAddress() {
        Log.d("PainlessMesh","askApMacAddress()");
        String apSSID = "";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ConnectivityManager cm = (ConnectivityManager) mainActivity.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
                apSSID = ((WifiInfo) nc.getTransportInfo()).getBSSID();
            } else {
                WifiManager wifiMgr = (WifiManager) mainActivity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                apSSID = wifiMgr.getConnectionInfo().getBSSID();
            }
        } catch (Exception e) {
            Log.e("PainlessMesh.askApMacAddress()", ""+e.getMessage());
            throw e;
        }
        return apSSID;
    }

    private String askApIpAddress() {
        Log.d("PainlessMesh","askApIpAddress()");
        String apMeshIp = "";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ConnectivityManager cm = (ConnectivityManager) mainActivity.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                LinkProperties lp = cm.getLinkProperties(cm.getActiveNetwork());
//                byte[] ipBytes = lp.getRoutes().get(0).getGateway().getAddress();
//                apMeshIp = ipBytes[3]+"."+ipBytes[2]+"."+ipBytes[1]+"."+ipBytes[0];
                apMeshIp = lp.getRoutes().get(0).getGateway().getAddress().toString();
            } else {
                int ipAsNumber = ((WifiManager) mainActivity.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).getDhcpInfo().gateway;
                apMeshIp = ((ipAsNumber & 0xFF) + "." +
                        ((ipAsNumber >>>= 8) & 0xFF) + "." +
                        ((ipAsNumber >>>= 8) & 0xFF) + "." +
                        (ipAsNumber >>> 8 & 0xFF));
            }
        } catch (Exception e) {
            Log.e("PainlessMesh.askApIpAddress()", ""+e.getMessage());
            throw e;
        }
        return apMeshIp;
    }

    public Boolean isConnected() {
        return this.connected;
    }

    private void setConnected(Boolean _connect) {
        if (_connect) {
            this.connected = true;
            if (meshListener != null) {
                mainActivity.runOnUiThread(() -> {
                    meshListener.onConnected();
                });
            }
        } else {
            this.connected = false;
            if (meshListener != null) {
                mainActivity.runOnUiThread(() ->{
                    meshListener.onDisconnected();
                });
            }
        }
    }

    public void connectMesh() {
        Log.d("PainlessMesh", "connectMesh()");
        connTerminate = new Semaphore(1);
        if (scheduler == null) {
            scheduler = new PainlessMeshScheduler(this);
        }

        MeshConnThread = new Thread(new MeshConn(this));
        MeshConnThread.start();
    }

    class MeshConn implements Runnable {
        private PainlessMesh caller;

        public MeshConn(PainlessMesh _caller) {
            this.caller = _caller;
        }

        @Override
        public void run() {
            Log.d("PainlessMesh.MeshConn", "run()");

            try {
                boolean connected = false;
                do {
                    myMeshNodeId = createMeshId(askWifiMacAddress());
                    String serverAdr = askApIpAddress();

                    try {
                        connSocket = new Socket(serverAdr, meshPort);
                        connected = true;
                    } catch (Exception e) {
                        Log.e("PainlessMesh.MeshConn.run() connect",""+e.getMessage());
                    }
                } while (!connected);
                apMeshNodeId = createMeshId(askApMacAddress());
//                connSocket.setKeepAlive(true);
//                connSocket.setReceiveBufferSize(32768);
//                connSocket.setSendBufferSize(32768);
//                connSocket.setReuseAddress(true);
//                connSocket.setTrafficClass(0x04);
                Log.d("PainlessMesh", "...connected...");

                mainActivity.runOnUiThread(() -> {
                    receiver = new PainlessMeshReceiver(caller);
                    receiver.startReceiver();
                    sender = new PainlessMeshSender(caller);
                    sender.startSender();

                    setConnected(true);
//                    if (meshListener != null) { meshListener.onConnected(); }
                });
            } catch (Exception e) {
                Log.e("PainlessMesh.connectMesh()", "" + e.getMessage());
            }
        }
    }

    public void disconnectMesh() {
        Log.d("PainlessMesh", "disconnectMesh()");
        try {
            if (connTerminate.tryAcquire()) {
                scheduler.nodeSync.stop();
                receiver.stopReceiver();
                sender.stopSender();
                connSocket.close();
                while (receiver.isAlive() || sender.isAlive()) {
                    Thread.sleep(20);
                }
                nodesList.clear();
                Log.d("PainlessMesh", "...disconnected...");
            }
        } catch (Exception e) {
            Log.e("PainlessMesh.disconnectMesh()", "" + e.getMessage());
        }
        setConnected(false);
//        if (meshListener != null) {
//            mainActivity.runOnUiThread(() -> {
//                meshListener.onDisconnected();
//            });
//        }
    }
    private void intDisconnectMesh() {
        try {
            receiver.stopReceiver();
            sender.stopSender();
            while (receiver.isAlive() || sender.isAlive()) {
                Thread.sleep(20);
            }
            nodesList.clear();
            connSocket.close();
            Log.d("PainlessMesh", "...disconnected...");
        } catch (Exception e) {
            Log.e("PainlessMesh._disconnectMesh()", "" + e.getMessage());
        }
        setConnected(false);
//            if (meshListener != null) {
//                mainActivity.runOnUiThread(() -> {
//                    meshListener.onDisconnected();
//                });
//            }
    }

    public void reconnectSocket() {
        Log.d("PainlessMesh", "reconnectSocket");
        intDisconnectMesh();
        connectMesh();
    }

}
