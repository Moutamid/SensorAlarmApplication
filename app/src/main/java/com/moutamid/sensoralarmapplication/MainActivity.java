package com.moutamid.sensoralarmapplication;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int STATUS_ALARM = 1;
    private static final int STATUS_ACK = 2;
    private static final int STATUS_RESOLVED = 3;
    private final Map<Integer, SensorEvent> sensorData = new HashMap<>();
    private TextView[] sensorViews = new TextView[5];
    private SharedPreferences prefs;
    private OkHttpClient client;
    private okhttp3.WebSocket webSocket;
    private Handler handler = new Handler();
    private Runnable reconnectRunnable;
    private SoundPool soundPool;
    private int alertSoundId;
    private int alertStreamId = 0;
    private long lastMessageTime = 0;

    public static void checkApp(Activity activity) {
        String appName = "alarmsensor";

        new Thread(() -> {
            URL google = null;
            try {
                google = new URL("https://raw.githubusercontent.com/Moutamid/Moutamid/main/apps.txt");
            } catch (final MalformedURLException e) {
                e.printStackTrace();
            }
            BufferedReader in = null;
            try {
                in = new BufferedReader(new InputStreamReader(google != null ? google.openStream() : null));
            } catch (final IOException e) {
                e.printStackTrace();
            }
            String input = null;
            StringBuffer stringBuffer = new StringBuffer();
            while (true) {
                try {
                    if ((input = in != null ? in.readLine() : null) == null) break;
                } catch (final IOException e) {
                    e.printStackTrace();
                }
                stringBuffer.append(input);
            }
            try {
                if (in != null) {
                    in.close();
                }
            } catch (final IOException e) {
                e.printStackTrace();
            }
            String htmlData = stringBuffer.toString();

            try {
                JSONObject myAppObject = new JSONObject(htmlData).getJSONObject(appName);

                boolean value = myAppObject.getBoolean("value");
                String msg = myAppObject.getString("msg");

                if (value) {
                    activity.runOnUiThread(() -> {
                        new AlertDialog.Builder(activity)
                                .setMessage(msg)
                                .setCancelable(false)
                                .show();
                    });
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

        }).start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkApp(MainActivity.this);
        sensorViews[0] = findViewById(R.id.sensor1);
        sensorViews[1] = findViewById(R.id.sensor2);
        sensorViews[2] = findViewById(R.id.sensor3);
        sensorViews[3] = findViewById(R.id.sensor4);
        sensorViews[4] = findViewById(R.id.sensor5);

        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String ip = prefs.getString("server_ip", null);
        String userIdStr = prefs.getString("user_id", null);

        if (ip == null || userIdStr == null) {
            Toast.makeText(this, "IP or User ID not set. Please setup app again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        int userId = Integer.parseInt(userIdStr);

        setupSound();

        // Set click listeners for acknowledge
        for (int i = 0; i < 5; i++) {
            final int pos = i + 1;
            sensorViews[i].setOnClickListener(v -> {
                SensorEvent event = sensorData.get(pos);
                if (event != null && event.status == STATUS_ALARM) {
                    sendAcknowledge(Integer.parseInt(event.sensorId), userId);
                }
            });
        }

        connectWebSocket(ip);
        startHeartbeatCheck();
    }

    private void setupSound() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes)
                .build();
        alertSoundId = soundPool.load(this, R.raw.alarm_sound, 1);
    }

//    private void handleServerMessage(String msg) {
//        if ("0000000".equals(msg)) return; // Heartbeat
//
//        try {
//            int sensorId = Integer.parseInt(msg.substring(0, 2));
//            int position = Integer.parseInt(msg.substring(2, 3));
//            int status = Integer.parseInt(msg.substring(3, 4));
//            String description = msg.substring(4);
//
//            SensorEvent event = new SensorEvent(sensorId, position, status, description);
//            sensorData.put(position, event);
//            updateSensorUI(event);
//        } catch (Exception e) {
//            Log.e(TAG, "Failed to parse message: " + msg);
//        }
//
//        updateSoundStatus();
//    }

    private void connectWebSocket(String ip) {
        if (client != null) {
            client.dispatcher().executorService().shutdown();
        }
        client = new OkHttpClient();

        String url = "ws://" + ip + ":1337";
        Request request = new Request.Builder().url(url).build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull okhttp3.WebSocket webSocket, @NonNull Response response) {
                Log.d(TAG, "WebSocket Opened");
                lastMessageTime = System.currentTimeMillis();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connected to server", Toast.LENGTH_SHORT).show());

                // HTTP request to /mc endpoint
                String httpUrl = "http://" + ip + "/mc";
                Request httpRequest = new Request.Builder().url(httpUrl).build();

                client.newCall(httpRequest).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e("HTTP /mc", "Failed to hit /mc: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (response.isSuccessful()) {
                            String body = response.body().string();
                            processMcResponse(body);
                        } else {
                            Log.e("HTTP /mc", "Server error: " + response.code());
                        }
                    }
                });
            }

            @Override
            public void onMessage(@NonNull okhttp3.WebSocket webSocket, @NonNull String text) {
                lastMessageTime = System.currentTimeMillis();
                Log.d(TAG, "Received: " + text);
            }

            @Override
            public void onMessage(@NonNull okhttp3.WebSocket webSocket, @NonNull ByteString bytes) {
                lastMessageTime = System.currentTimeMillis();
                Log.d(TAG, "Received binary message");
            }

            @Override
            public void onClosing(@NonNull okhttp3.WebSocket webSocket, int code, @NonNull String reason) {
                Log.d(TAG, "WebSocket Closing: " + reason);
            }

            @Override
            public void onClosed(@NonNull okhttp3.WebSocket webSocket, int code, @NonNull String reason) {
                Log.d(TAG, "WebSocket Closed: " + reason);
            }

            @Override
            public void onFailure(@NonNull okhttp3.WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                Log.e(TAG, "WebSocket Failure: " + t.getMessage());
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connection failed, retrying...", Toast.LENGTH_SHORT).show());
                reconnectWithDelay();
            }
        });
    }


//    private void updateSensorUI(int position, int status, String sensorId, String description) {
//        SensorEvent event = new SensorEvent(position, status, sensorId, description);
//        sensorData.put(position, event);
//        updateSensorUI(event);
//    }

    private void updateSensorUI(SensorEvent event) {
        if (event.position < 1 || event.position > 5) return;

        TextView view = sensorViews[event.position - 1];
        view.setText(event.description + "    " + event.status);

        switch (event.status) {
            case STATUS_ALARM:
                view.setBackgroundColor(Color.RED);
                break;
            case STATUS_ACK:
                view.setBackgroundColor(Color.parseColor("#FFA500")); // Orange
                break;
            case STATUS_RESOLVED:
                view.setBackgroundColor(Color.GREEN);
                break;
        }
    }

    private void updateSoundStatus() {
        boolean anyAlarm = false;
        for (SensorEvent e : sensorData.values()) {
            if (e.status == STATUS_ALARM) {
                anyAlarm = true;
                break;
            }
        }

        if (anyAlarm) {
            if (alertStreamId == 0) {
                alertStreamId = soundPool.play(alertSoundId, 1, 1, 1, -1, 1);
            }
        } else {
            if (alertStreamId != 0) {
                soundPool.stop(alertStreamId);
                alertStreamId = 0;
            }
        }
    }

    private void sendAcknowledge(int sensorId, int userId) {
        String ackMsg = "ACK" + sensorId + "-" + userId;
        if (webSocket != null) {
            webSocket.send(ackMsg);
            Toast.makeText(this, "Acknowledged", Toast.LENGTH_SHORT).show();
        }
    }

    private void reconnectWithDelay() {
        handler.removeCallbacks(reconnectRunnable);
        reconnectRunnable = () -> {
            String ip = prefs.getString("server_ip", null);
            if (ip != null) {
                connectWebSocket(ip);
            }
        };
        handler.postDelayed(reconnectRunnable, 5000); // retry after 5 seconds
    }

    private void startHeartbeatCheck() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                if (now - lastMessageTime > 180000) {
                    Log.d(TAG, "No heartbeat received for 3 minutes, reconnecting...");
                    reconnectWithDelay();
                }
                handler.postDelayed(this, 60000);
            }
        }, 60000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webSocket != null) {
            webSocket.close(1000, "App closed");
        }
        if (soundPool != null) {
            soundPool.release();
        }
    }

    private void processMcResponse(String body) {
        Log.d("RAW_RESPONSE", "Body: " + body);

        String cleaned = body.replace("<br>", "\n").trim();
        String[] lines = cleaned.split("(\r\n|\r|\n)");

        for (String line : lines) {
            Log.d("LINE", "Line: " + line);
            if (line.length() < 6 || !Character.isDigit(line.charAt(0))) continue;

            try {
                int position = Character.getNumericValue(line.charAt(0)); // 1–5
                int status = Character.getNumericValue(line.charAt(1));   // 1 = ALARM, 2 = ACK, 3 = RESOLVED
                String sensorId = line.substring(2, 6);                   // 1234
                String description = line.length() > 6 ? line.substring(6).trim() : "";
                Log.d("PARSED", "Position=" + position + ", Status=" + status + ", ID=" + sensorId + ", Desc=" + description);
                SensorEvent event = new SensorEvent(position, status, sensorId, description);
                updateSensorUI(event);
            } catch (Exception e) {
                Log.e("Parse Error", "Line parse failed: " + line, e);
            }
        }
    }

    class SensorEvent {
        int position;
        int status;
        String sensorId;
        String description;

        SensorEvent(int position, int status, String sensorId, String description) {
            this.position = position;
            this.status = status;
            this.sensorId = sensorId;
            this.description = description;
        }
    }


}
