package com.juddidier.painlessmeshandroid;

import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

public class PainlessMeshReceiver {
    private PainlessMesh caller;
    private Thread receiveThread = null;

    private long t0;

    public PainlessMeshReceiver(PainlessMesh _caller) {
        this.caller = _caller;
    }

    protected void startReceiver() {
        Log.d("PainlessMesh", "startReceiver()");
        this.receiveThread = new Thread(new ReceiveRunnable());
        this.receiveThread.start();
    }

    protected void stopReceiver() {
        Log.d("PainlessMesh", "stopReceiver()");
        this.receiveThread.interrupt();
    }

    public boolean isAlive() {
        return this.receiveThread.isAlive();
    }

    private class ReceiveRunnable implements Runnable {
        private InputStream inStream = null;
        private int bufferElmt = 0;
        private boolean parsing = false;
        private int nested = 0;
        private long rcvTime = 0;
        private String rcvData="";

        @Override
        public void run() {
            Log.d("PainlessMeshReceiver","run()");
            try {
                this.inStream = caller.connSocket.getInputStream();
                while (true) {
                    bufferElmt = inStream.read();
                    if (bufferElmt != -1) {
                        bufferElmt = bufferElmt & 0xFF;
                        if (!parsing) {
                            rcvTime = System.nanoTime() / 1000;
                        }
                        if (bufferElmt == '{') {
                            parsing = true;
                            nested++;
                        } else if (bufferElmt == '}') {
                            nested--;
                        }
                        if (parsing) {
                            rcvData += (char) bufferElmt;
                            if (nested == 0) {
                                Log.d("PainlessMesh.received", rcvData);
                                decode(rcvData, rcvTime);
                                parsing = false;
                                rcvData = "";
                            }
                        }
                    } else {
                        Log.e("PainlessMeshReceiver.run()", "empty");
                        if (!Thread.currentThread().isInterrupted()) {
                            throw new IOException();
                        } else {
                            throw new InterruptedException();
                        }
                    }
                }
            } catch (InterruptedException e) {
//                Log.e("PainlessMeshReceiver.run()", "interrupted");
            } catch (Exception e) {
                Log.e("PainlessMeshReceiver.run()", "" + e.getMessage());
                if (caller.connTerminate.tryAcquire()) {
                    caller.mainActivity.runOnUiThread(() -> {
                        caller.reconnectSocket();
                    });
                }
            }
            Log.d("PainlessMesh", "...receiver stopped...");
        }  // run()
    }  // class ReceiveRunnable

    private void decode(String rcvData, long rcvTime) {
        try {
            JSONObject rcvJson = new JSONObject(rcvData);
            int msgType = rcvJson.getInt("type");
            long fromNode = rcvJson.getLong("from");

            switch (msgType) {
                case 4: /* NODE-TIME-SYNC */
                    JSONObject rcvMsg = new JSONObject(rcvJson.getString("msg"));
                    switch (rcvMsg.optInt("type")) {
                        case 0:
                            t0 = caller.scheduler.timeSync.sendTimeSync();
                            break;
//                        case 1:
//                            caller.scheduler.timeSync.respondTimeSync(rcvTime, rcvMsg.getLong("t0"));
//                            break;
                        case 2:
//                            long t1 = rcvMsg.getLong("t1") - timeOffset;
//                            long t2 = rcvMsg.getLong("t2") - timeOffset;
//                            long offset = (t1 - t0) / 2 + (t2 - rcvTime) / 2;
////                            System.out.println("t0:"+t0+" t1:"+t1+" t2:"+t2+" t3:"+rcvTime+"  offset:"+offset);
//                            timeOffset += offset;
//                            if (Math.abs(offset) > 200000) {
//                                caller.scheduler.timeSync.sendTimeSync();
//                            }
//                            break;
                    }
                    break;
                case 5: /* NODE-SYNC-REQUEST */
                    caller.scheduler.nodeSync.sendNodeSync(true);
                    break;
                case 6: /* NODE-SYNC-REPLY */
                    ArrayList<Long> oldNodesList = new ArrayList<>(caller.nodesList);
                    Collections.sort(oldNodesList);
                    caller.nodesList.clear();
                    caller.nodesList.add(fromNode);
                    if (rcvJson.has("subs")) {
                        getSubsNodesId(rcvJson.optJSONArray("subs"));
                        Collections.sort(caller.nodesList);
                    }
                    Log.d("Painlessmesh"," changed Nodes: old "+oldNodesList.size()+" new "+caller.nodesList.size());
                    ArrayList<Long> addedItems = getAdditionalItems(oldNodesList, caller.nodesList);
                    ArrayList<Long> removedItems = getAdditionalItems(caller.nodesList, oldNodesList);
                    if (!addedItems.isEmpty() || !removedItems.isEmpty()) {
                        if (caller.meshListener!=null) {
                            caller.mainActivity.runOnUiThread(() -> {
                                caller.meshListener.onNodesChanged(addedItems, removedItems);
                            });
                        }
                    }
                    break;
                case 8: /* BROADCAST */
                    break;
                case 9: /* SINGLE */
                    String isoMsg = null;
                    try {
                        isoMsg = new String(rcvJson.getString("msg").getBytes("ISO-8859-1"), "UTF-8");
                    } catch (Exception e) {
                        Log.e("Receiver.decode()-9",""+e.getMessage());
                    }
                    String finalIsoMsg = isoMsg;
                    if (caller.meshListener!=null) {
                        caller.mainActivity.runOnUiThread(() -> {
                            try {
                                caller.meshListener.onReceivedMessage(fromNode, new JSONObject(finalIsoMsg));
                            } catch (JSONException e) {
                                Log.e("Receiver.decode()-9",""+e.getMessage());
                            }
                        });
                    }
                    break;
            }
        } catch (Exception e) {
            Log.e("PainlessMeshScheduler.decode()",""+e.getMessage());
        }
    }

    private void getSubsNodesId(@Nullable JSONArray msg) {
        if ((msg != null) && (msg.length() > 0)) {
            for (int i=0; i < msg.length(); i++) {
                try {
                    JSONObject subMsg = msg.getJSONObject(i);
                    caller.nodesList.add(subMsg.getLong("nodeId"));
                    getSubsNodesId(subMsg.optJSONArray("subs"));
                } catch (Exception e) {
                    Log.e("PainlessMesh.getSubsNodesId()",""+e.getMessage());
                }
            }
        }
    }

    private ArrayList<Long> getAdditionalItems(ArrayList<Long> origArray, ArrayList<Long> deltaArray) {
        ArrayList<Long> addItems = new ArrayList<>();

        Iterator deltaItr = deltaArray.iterator();
        Long itm;
        while (deltaItr.hasNext()) {
            itm = (Long) deltaItr.next();
            if (!origArray.contains(itm)) {
                addItems.add(itm);
            }
        }
        return addItems;
    }

}
