package com.example.sensoralarmapplication;

import android.hardware.SensorEvent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sensoralarmapplication.R;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpResponse;


import tech.gusavila92.websocketclient.WebSocketClient;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import okhttp3.*;

public class MainActivity extends AppCompatActivity {

    private final String SERVER_URL = "http://82.193.209.203/mc";
    private WebSocket webSocket;
    private Handler handler = new Handler();
    private Runnable heartbeatRunnable;
    private HashMap<Integer, SensorEvent> sensorEvents = new HashMap<>();
    private MediaPlayer mediaPlayer;
    private int userId = 1;
//private WebSocketClient webSocketClient;
//    private WebSocketClient webTrainClient;
//    private boolean isRunning = false;
//    Thread thread;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        for (int i = 1; i <= 5; i++) {
            int resId = getResources().getIdentifier("sensor" + i, "id", getPackageName());
            TextView sensorView = findViewById(resId);
            sensorEvents.put(i, new SensorEvent(sensorView));
            final int position = i;
            sensorView.setOnClickListener(v -> acknowledgeAlarm(position));
        }
//
        mediaPlayer = MediaPlayer.create(this, R.raw.alarm_sound);
        initiateWebSocketConnection();
    }

    private void initiateWebSocketConnection() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(SERVER_URL).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connected to server", Toast.LENGTH_SHORT).show());
                startHeartbeat();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                runOnUiThread(() -> handleServerMessage(text));
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connection failed", Toast.LENGTH_SHORT).show());
                stopHeartbeat();
                handler.postDelayed(() -> initiateWebSocketConnection(), 9000);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connection closed", Toast.LENGTH_SHORT).show());
                stopHeartbeat();
            }
        });
    }

    private void handleServerMessage(String message) {
        if (message.equals("0000000")) {
            // Heartbeat message
            return;
        }

        int position = Character.getNumericValue(message.charAt(0));
        int status = Character.getNumericValue(message.charAt(1));
        String sensorId = message.substring(2, 6);
        String description = message.substring(6);

        SensorEvent event = sensorEvents.get(position);
        if (event != null) {
            event.update(status, sensorId, description);
        }

        boolean alarmActive = sensorEvents.values().stream().anyMatch(e -> e.status == 1);
        if (alarmActive) {
            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
            }
        } else {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                try {
                    mediaPlayer.prepare();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void acknowledgeAlarm(int position) {
        SensorEvent event = sensorEvents.get(position);
        if (event != null && event.status == 1) {
            String ackMessage = event.sensorId + Integer.toHexString(userId).toUpperCase();
            webSocket.send(ackMessage);
        }
    }

    private void startHeartbeat() {
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                webSocket.send("0000000");
                handler.postDelayed(this, 60000);    }
        };
        handler.postDelayed(heartbeatRunnable, 60000);
    }

    private void stopHeartbeat() {
        if (heartbeatRunnable != null) {
            handler.removeCallbacks(heartbeatRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webSocket != null) {
            webSocket.close(1000, null);
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        stopHeartbeat();
    }

    // Inner class to represent a sensor event
    private class SensorEvent {
        TextView view;
        int status;
        String sensorId;
        String description;

        SensorEvent(TextView view) {
            this.view = view;
        }

        void update(int status, String sensorId, String description) {
            this.status = status;
            this.sensorId = sensorId;
            this.description = description;
            view.setText(description);
            switch (status) {
                case 1:
                    view.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
                    break;
                case 2:
                    view.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_dark));
                    break;
                case 3:
                    view.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
                    break;
            }
        }
    }
}
//        createWebSocketClient();
//    }
//        private void createWebSocketClient () {
//            URI uri;
//            try {
//                // Connect to local host
//                uri = new URI("ws://82.193.209.203/mc");
//            } catch (URISyntaxException e) {
//                e.printStackTrace();
//                return;
//            }
//
//            webSocketClient = new WebSocketClient(uri) {
//                @Override
//                public void onOpen() {
//                    Log.i("WebSocket", "Session is starting");
//                    webSocketClient.send("Hello World!");
//                }
//
//                @Override
//                public void onTextReceived(String s) {
//                    Log.i("WebSocket", "Message received");
//                    final String message = s;
//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            try {
//                                Log.i("WebSocket", message);
//
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    });
//                }
//
//                @Override
//                public void onBinaryReceived(byte[] data) {
//                }
//
//                @Override
//                public void onPingReceived(byte[] data) {
//                }
//
//                @Override
//                public void onPongReceived(byte[] data) {
//                }
//
//                @Override
//                public void onException(Exception e) {
//                    System.out.println(e.getMessage());
//                }
//
//                @Override
//                public void onCloseReceived() {
//                    Log.i("WebSocket", "Closed ");
//                    System.out.println("onCloseReceived");
//                }
//            };
//
//            webSocketClient.setConnectTimeout(10000);
//            webSocketClient.setReadTimeout(60000);
//            webSocketClient.enableAutomaticReconnection(5000);
//            webSocketClient.connect();
//        }
//    }
