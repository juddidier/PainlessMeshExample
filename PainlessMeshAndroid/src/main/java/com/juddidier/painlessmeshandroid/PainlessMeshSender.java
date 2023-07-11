package com.juddidier.painlessmeshandroid;

import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedList;

public class PainlessMeshSender {
    private PainlessMesh caller;
    private Thread sendThread = null;

    private LinkedList<DataSet> sendQueue;
    private Object sync = null;
    class DataSet {
        byte[] data;
        DataSet(byte[] data) {
            this.data = data;
        }
    }

    public PainlessMeshSender(PainlessMesh _caller) {
        this.caller = _caller;
    }

    public void startSender() {
        Log.d("PainlessMesh", "startSender()");
        try {
            if (sync == null) {
                sendQueue = new LinkedList<>();
                sync = new Object();
            }
            this.sendThread = new Thread(new sendRunnable());
            this.sendThread.start();
        } catch (Exception e) {
            Log.e("PainlessMesh.startSender()", ""+e.getMessage());
        }
    }

    public void stopSender() {
        Log.d("PainlessMesh", "stopSender()");
        this.sendThread.interrupt();
    }

    public boolean isAlive() {
        return this.sendThread.isAlive();
    }

    public void sendData(long toNode, JSONObject msg) {
        Log.d("PainlessMesh", "sendData()");
        try {
            JSONObject nodeMsg = new JSONObject();
            nodeMsg.put("dest", toNode);
            nodeMsg.put("from", caller.myMeshNodeId);
            nodeMsg.put("type", (toNode != -1) ? 9 : 8);
            nodeMsg.put("msg", msg);
            sendData(nodeMsg.toString().getBytes());
        } catch (Exception e) {
            System.out.println("Sender.sendData() error: "+e.getMessage());
        }
    }

    protected void sendData(byte[] data) {
        Log.d("PainlessMesh", "sendData()");
        try {
            sendQueue.add(new DataSet(data));
            synchronized (sync) {
                sync.notify();
            }
        } catch (Exception e) {
            Log.e("PainlessMesh.sendData()",""+e.getMessage());
        }
    }

    private class sendRunnable implements Runnable {
        private OutputStream outStream = null;

        @Override
        public void run() {
            try {
                outStream = caller.connSocket.getOutputStream();
                sendQueue.clear();
                while (true) {
                    while (sendQueue.size() == 0) {
                        synchronized (sync) {
                                sync.wait();
                        }
                    }
                    Iterator itr = sendQueue.iterator();
                    byte[] msg = ((DataSet) itr.next()).data;
                    itr.remove();
                    Log.d("PainlessMesh.sent", new String(msg, "UTF-8"));
                    outStream.write(msg, 0, msg.length);
                    outStream.write(0);
                    outStream.flush();
                }
            } catch (InterruptedException e) {
            } catch (Exception e) {
                Log.e("PainlessMeshSender.run()", "" + e.getMessage());
                if (caller.connTerminate.tryAcquire()) {
                    caller.mainActivity.runOnUiThread(() -> {
                        caller.reconnectSocket();
                    });
                }
            }
            Log.d("PainlessMesh", "...sender stopped...");
        }  // run()
    }  // class sendRunnable
}
