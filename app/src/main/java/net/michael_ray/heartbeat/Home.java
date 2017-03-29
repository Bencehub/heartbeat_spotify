package net.michael_ray.heartbeat;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Home extends AppCompatActivity implements SpotifyPlayer.NotificationCallback, ConnectionStateCallback {

    private static final String CLIENT_ID = "558a758c51fd4189993fe313f0e84eaa";
    private static final String REDIRECT_URI = "michael-ray.net://callback";
    public static final String baseBluetoothUuidPostfix = "0000-1000-8000-00805F9B34FB";

    private Player mPlayer;
    private static final int REQUEST_CODE = 1114;
    private String auth_token;
    private ArrayList<SpotifyTrack> tracks;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mGatt;
    private static final int REQUEST_ENABLE_BT = 1;

    private static final long SCAN_PERIOD = 10000;
    private Handler mHandler;

    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;

    private String spotify_user;

    private int last_heartbeat;
    private boolean was_playing;
    TextView txt_heartbeat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        last_heartbeat = 0;
        was_playing = false;
        setContentView(R.layout.activity_home);

        txt_heartbeat = (TextView)findViewById(R.id.txt_heartbeat);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mHandler = new Handler();

        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming", "user-read-private", "user-read-email", "user-library-modify", "user-library-read"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
                } else {
                    mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                    settings = new ScanSettings.Builder()
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                            .build();
                    filters = new ArrayList<ScanFilter>();
                }
            }
            scanLeDevice(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            scanLeDevice(false);
        }
    }

    @Override
    protected void onDestroy() {
        Spotify.destroyPlayer(this);
        if (mGatt != null) {
            mGatt.close();
        }
        mGatt = null;
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("MainActivity", "coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                finish();
                return;
            }
        }

        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, data);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                Config playerConfig = new Config(this, response.getAccessToken(), CLIENT_ID);
                auth_token = response.getAccessToken();
                Spotify.getPlayer(playerConfig, this, new SpotifyPlayer.InitializationObserver() {
                    @Override
                    public void onInitialized(SpotifyPlayer spotifyPlayer) {
                        mPlayer = spotifyPlayer;
                        mPlayer.addConnectionStateCallback(Home.this);
                        mPlayer.addNotificationCallback(Home.this);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e("MainActivity", "Could not initialize player: " + throwable.getMessage());
                    }
                });
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mLEScanner.stopScan(mScanCallback);
                }
            }, SCAN_PERIOD);
            mLEScanner.startScan(filters, settings, mScanCallback);
        } else {
            mLEScanner.stopScan(mScanCallback);
        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice btDevice = result.getDevice();
            if (result.getDevice().getName()!=null && result.getDevice().getName().contains("HRM1")) {
                Log.i("MainActivity", "BLE Result: " + result.toString());
                connectToDevice(btDevice);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) { }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    public void connectToDevice(final BluetoothDevice device) {
        if (mGatt == null) {
            mGatt = device.connectGatt(this, false, gattCallback);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView device_name = (TextView)findViewById(R.id.txt_device_name);
                    TextView device_uuid = (TextView)findViewById(R.id.txt_device_UUID);
                    device_name.setText("Name: " + device.getName());
                    device_uuid.setText("Address: " + device.getAddress());
                }
            });
            scanLeDevice(false);// will stop after first device detection
        }
    }

    private static UUID uuidFromShortCode16(String shortCode16) {
        return UUID.fromString("0000" + shortCode16 + "-" + baseBluetoothUuidPostfix);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange", "Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("gattCallback", "STATE_CONNECTED");
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("gattCallback", "STATE_DISCONNECTED");
                    break;
                default:
                    Log.e("gattCallback", "STATE_OTHER");
            }

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            gatt.readCharacteristic(characteristic);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
//            Log.i("onServicesDiscovered", services.toString());
            BluetoothGattCharacteristic characteristic = gatt.getService(uuidFromShortCode16("180D")).getCharacteristic(uuidFromShortCode16("2A37"));
            gatt.setCharacteristicNotification(characteristic, true);

            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            byte[] data = characteristic.getValue();
            last_heartbeat = ((data[0] & 0xFF) << 8) + (data[1] & 0xFF);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    txt_heartbeat.setText("Latest heartbeat: " + last_heartbeat);
                }
            });
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onLoggedIn() {
        Log.d("MainActivity", "User logged in");
        Initialization init = new Initialization();
        init.execute();
    }

    @Override
    public void onLoggedOut() {
        Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Error error) {
        Log.d("MainActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String s) {
        Log.d("MainActivity", "Received connection message: " + s);
    }

    @Override
    public void onPlaybackEvent(PlayerEvent playerEvent) {
//        Log.d("MainActivity", "Playback event received: " + playerEvent.name());
        if (playerEvent == PlayerEvent.kSpPlaybackNotifyBecameInactive) {

        }
    }

    @Override
    public void onPlaybackError(Error error) {
        Log.d("MainActivity", "Playback error received: " + error.name());
        switch (error) {
            default:
                break;
        }
    }

    public void nextSong(View view) {
        startSong();
    }

    public void previousSong(View view) {
        mPlayer.skipToPrevious(null);
    }

    public void playSong(View view) {
        if (mPlayer.getPlaybackState().isPlaying) {
            mPlayer.pause(null);
        } else {
            if (was_playing) {
                mPlayer.resume(null);
            } else {
                startSong();
            }
            was_playing = true;
        }
    }

    public void startSong() {
        SpotifyTrack track = findSong();
        TextView song_name = (TextView) findViewById(R.id.txt_song_name);
        TextView song_artist = (TextView) findViewById(R.id.txt_artist_name);
        TextView song_album = (TextView) findViewById(R.id.txt_album_name);
        song_name.setText("Name: " + track.getTrack_name());
        song_artist.setText("Artist: " + track.getTrack_artists().get(0).getArtist_name());
        song_album.setText("Album: " + track.getTrack_album().getAlbum_name());
        new DownloadImage((ImageView)findViewById(R.id.img_album_art)).execute(track.getTrack_album().getAlbum_art());
        mPlayer.playUri(null, track.getTrack_uri(), 0,0);
    }

    public SpotifyTrack findSong() {
        int index = 0;
        float bpm = last_heartbeat;
        double closest_bpm = Math.abs(tracks.get(0).getTrack_details().getTempo()-bpm);
        for (int i = 1; i<tracks.size(); i++) {
            double track_bpm = tracks.get(i).getTrack_details().getTempo();
            double difference = Math.abs(track_bpm-bpm);
            if (difference<closest_bpm) {
                index = i;
                closest_bpm = Math.abs(tracks.get(i).getTrack_details().getTempo()-bpm);
            }
        }
        return tracks.get(index);
    }

    private class Initialization extends AsyncTask<String, Integer, String> {
        protected String doInBackground(String... urls) {
            try {
                String track_results = httpGetRequest(auth_token, "https://api.spotify.com/v1/me/tracks?limit=50");
                tracks = new ArrayList<>();

                JSONObject result_json = new JSONObject(track_results);
                JSONArray song_list = result_json.getJSONArray("items");
                String track_ids = "";
                for (int i = 0; i < song_list.length(); i++) {
                    SpotifyTrack track = new SpotifyTrack(song_list.getJSONObject(i).getJSONObject("track"));
                    tracks.add(track);
                    track_ids += track.getTrack_id() + ",";
                }

                track_ids = track_ids.substring(0,track_ids.length()-1);
                String detailed_results = httpGetRequest(auth_token, "https://api.spotify.com/v1/audio-features?ids=" + track_ids);
                result_json = new JSONObject(detailed_results);
                JSONArray detail_list = result_json.getJSONArray("audio_features");
                for (int i=0; i < detail_list.length(); i++) {
                    tracks.get(i).setTrack_details(new SpotifyTrackDetails(detail_list.getJSONObject(i)));
                }

                String profile_result = httpGetRequest(auth_token, "https://api.spotify.com/v1/me");
                JSONObject profile_json = new JSONObject(profile_result);
                spotify_user = profile_json.getString("display_name");
                return profile_json.getJSONArray("images").getJSONObject(0).getString("url");

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        protected void onProgressUpdate(Integer... progress) { }

        protected void onPostExecute(String result) {
            new DownloadImage((ImageView)findViewById(R.id.img_spotify_profile)).execute(result);
            Log.d("MainActivity","Finished initialization");
            TextView completed = (TextView)findViewById(R.id.txt_spotify_connected);
            completed.setText("Status: Connected");
            TextView user = (TextView)findViewById(R.id.txt_spotify_details);
            user.setText("User: " + spotify_user);
        }
    }


    private String httpGetRequest(String auth_token, String endpoint) throws Exception{
        OkHttpClient client = new OkHttpClient();

        String auth_header = "Bearer " + auth_token;
        Request request = new Request.Builder().url(endpoint).get().addHeader("authorization", auth_header).build();

        Response response = client.newCall(request).execute();
        return response.body().string();
    }

    public class DownloadImage extends AsyncTask<String, Integer, Drawable> {
        ImageView imageView;
        public DownloadImage(ImageView imageView) {
            this.imageView = imageView;
        }
        @Override
        protected Drawable doInBackground(String... arg0) {
            return downloadImage(arg0[0]);
        }
        protected void onPostExecute(Drawable image) {
            imageView.setImageDrawable(image);
        }

        private Drawable downloadImage(String _url) {
            URL url;
            BufferedOutputStream out;
            InputStream in;
            BufferedInputStream buf;
            try {
                url = new URL(_url);
                in = url.openStream();
                buf = new BufferedInputStream(in);
                Bitmap bMap = BitmapFactory.decodeStream(buf);
                if (in != null) {
                    in.close();
                }
                if (buf != null) {
                    buf.close();
                }
                return new BitmapDrawable(bMap);
            } catch (Exception e) {
                Log.e("Error reading file", e.toString());
            }
            return null;
        }

    }
}
