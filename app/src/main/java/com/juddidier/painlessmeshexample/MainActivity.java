package com.juddidier.painlessmeshexample;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.juddidier.painlessmeshandroid.PainlessMesh;

import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    public PainlessMesh mesh;

    private Button btnConnect;
    private Button btnDisconnect;
    private EditText etInCommand;
    private Spinner spDestNode;
    private ArrayAdapter arrAdapter;
    private TextView tvResponse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.ACCESS_WIFI_STATE,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION},
                PackageManager.PERMISSION_GRANTED);

        btnConnect = (Button) findViewById(R.id.btn_connect);
        btnDisconnect = (Button) findViewById(R.id.btn_disconnect);
        btnDisconnect.setEnabled(false);
        etInCommand = (EditText) findViewById(R.id.et_inCommand);
        spDestNode = (Spinner) findViewById(R.id.sp_nodes);
        tvResponse = (TextView) findViewById(R.id.tv_response);

        mesh = new PainlessMesh(this, 5555);

        arrAdapter = new ArrayAdapter<Long>(this, android.R.layout.simple_spinner_dropdown_item, mesh.nodesList);

        mesh.setMeshListener(new PainlessMesh.MeshListener() {
            @Override
            public void onConnected() {
                btnConnect.setEnabled(false);
                btnDisconnect.setEnabled(true);
            }

            @Override
            public void onDisconnected() {
                btnConnect.setEnabled(true);
                btnDisconnect.setEnabled(false);
            }

            @Override
            public void onNodesChanged(ArrayList<Long> addedItems, ArrayList<Long> removedItems) {
                spDestNode.setAdapter(arrAdapter);
            }

            @Override
            public void onReceivedMessage(long fromNode, JSONObject message) {
                tvResponse.setText(String.valueOf(fromNode) +": "+ message.toString());
            }
        });

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mesh.connectMesh();
            }
        });

        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mesh.disconnectMesh();
            }
        });

        etInCommand.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    ceckAndSend();
                    return true;
                }
                return false;
            }
        });

    }

    @Override
    protected void onDestroy() {
        mesh.disconnectMesh();
        super.onDestroy();
    }

    private void ceckAndSend() {
        try {
            JSONObject sendMsg = new JSONObject(etInCommand.getText().toString());
            String val = spDestNode.getSelectedItem().toString();
            mesh.sender.sendData(Integer.parseInt(val), sendMsg);
        } catch (Exception e) {
            Log.e("PainlessMesh.checkAndSend()", ""+e.getMessage());
        }
    }


}