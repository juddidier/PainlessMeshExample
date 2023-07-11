package com.juddidier.painlessmeshandroid;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class PainlessMeshScheduler {
    private PainlessMesh caller;
    public NodeSync nodeSync;
    public TimeSync timeSync;

    public PainlessMeshScheduler(PainlessMesh _caller) {
        Log.d("PainlessMeshScheduler","create()");
        this.caller = _caller;
        nodeSync = new NodeSync();
        nodeSync.start();
        timeSync = new TimeSync();
    }

    public class SocketAlive {
        private PainlessMesh caller;

        public SocketAlive(PainlessMesh _caller) {
            this.caller = _caller;
        }
    }

    public class NodeSync {
        private Handler syncHandler;
        private HandlerThread syncThread;

        public void start() {
            Log.d("PainlessMeshScheduler.NodeSync","start()");

            syncThread = new HandlerThread("NodeSync");
            syncThread.start();
            syncHandler = new Handler(syncThread.getLooper());
            syncHandler.postDelayed(nodeSyncRunnable, 1000);
        }

        public void stop() {
            Log.d("PainlessMeshScheduler.NodeSync","stop()");
            try {
                syncHandler.removeCallbacks(nodeSyncRunnable);
                syncThread.quit();
                syncHandler = null;
                syncThread = null;
            } catch (Exception e) {
                Log.e("PainlessMeshScheduler.NodeSync.stop()",""+e.getMessage());
            }
        }

        private final Runnable nodeSyncRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d("PainlessMeshScheduler.NodeSync","run()");
                try {
                    sendNodeSync(false);
                } catch (JSONException e) {
                    Log.e("PainlessMeshScheduler.Nodesync.run()",""+e.getMessage());
                }
                syncHandler.postDelayed(this, 10000/* + new Random().nextInt(2000) - 1000*/);
            }
        };

        public void sendNodeSync(boolean response) throws JSONException {
            Log.d("PainlessMeshScheduler.NodeSync","sendNodeSync(): "+(response ? "response":"request"));
            JSONObject nodeMsg = new JSONObject();
            nodeMsg.put("dest", caller.apMeshNodeId);
            nodeMsg.put("from", caller.myMeshNodeId);
            nodeMsg.put("type", response ? 6 : 5);
//            Log.i("PainlessMesh.SendNodeSync",""+nodeMsg.toString());
            caller.sender.sendData(nodeMsg.toString().getBytes());
        }
    }

    public class TimeSync {
        public long sendTimeSync() throws JSONException {
            JSONObject nodeMsg = new JSONObject();
            nodeMsg.put("dest", caller.apMeshNodeId);
            nodeMsg.put("from", caller.myMeshNodeId);
            nodeMsg.put("type", 4);

            JSONObject msg = new JSONObject();
            msg.put("type", 1);
            long t0 = System.nanoTime() / 1000;
            msg.put("t0", t0);
            nodeMsg.put("msg", msg);
            caller.sender.sendData(nodeMsg.toString().getBytes());
            return t0;
        }
    }
}
