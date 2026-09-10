package com.example.trackoffline;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.Marker;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {

    private MapView map;
    private TextView tvStatus;
    private Button btnToggle, btnSave;
    private LocationManager locationManager;
    private boolean isTracking = false;
    private final List<Location> trackPoints = new ArrayList<>();
    private Polyline trackLine;
    private Marker currentMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Khởi tạo cấu hình bộ nhớ đệm cho bản đồ OpenStreetMap
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // Thanh thông tin trạng thái
        tvStatus = new TextView(this);
        tvStatus.setText("Đang chờ tín hiệu GPS...");
        tvStatus.setPadding(20, 15, 20, 15);
        tvStatus.setBackgroundColor(Color.parseColor("#333333"));
        tvStatus.setTextColor(Color.WHITE);
        tvStatus.setTextSize(14);
        root.addView(tvStatus);

        // Lớp Bản đồ OpenStreetMap (hỗ trợ zoom cảm ứng và lưu offline)
        map = new MapView(this);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        map.setLayoutParams(mapParams);
        root.addView(map);

        // Khởi tạo đường vẽ lộ trình màu đỏ nổi bật trên bản đồ
        trackLine = new Polyline();
        trackLine.setColor(Color.RED);
        trackLine.setWidth(8.0f);
        map.getOverlays().add(trackLine);

        currentMarker = new Marker(map);
        currentMarker.setTitle("Vị trí của bạn");
        map.getOverlays().add(currentMarker);

        // Khung chứa các nút điều khiển
        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        btnLayout.setPadding(10, 10, 10, 10);

        btnToggle = new Button(this);
        btnToggle.setText("BẮT ĐẦU GHI VẾT");
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnToggle.setLayoutParams(p1);
        btnLayout.addView(btnToggle);

        btnSave = new Button(this);
        btnSave.setText("LƯU GPX");
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnSave.setLayoutParams(p2);
        btnLayout.addView(btnSave);

        root.addView(btnLayout);
        setContentView(root);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isTracking) {
                    startTracking();
                } else {
                    stopTracking();
                }
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveToGPX();
            }
        });
    }

    private void startTracking() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, 101);
                return;
            }
        }

        trackPoints.clear();
        trackLine.setPoints(new ArrayList<GeoPoint>());
        map.invalidate();

        isTracking = true;
        btnToggle.setText("DỪNG GHI");
        if (locationManager != null) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500, 1, this);
        }
        Toast.makeText(this, "Bắt đầu ghi hành trình trên bản đồ", Toast.LENGTH_SHORT).show();
    }

    private void stopTracking() {
        isTracking = false;
        btnToggle.setText("BẮT ĐẦU GHI VẾT");
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        Toast.makeText(this, "Đã tạm dừng", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!isTracking) return;

        trackPoints.add(location);
        GeoPoint currentPos = new GeoPoint(location.getLatitude(), location.getLongitude());

        // Cập nhật đường vẽ trực tiếp lên bản đồ
        trackLine.addPoint(currentPos);
        currentMarker.setPosition(currentPos);

        // Tự động căn giữa màn hình theo vị trí người dùng
        map.getController().animateTo(currentPos);
        if (map.getZoomLevelDouble() < 16.0) {
            map.getController().setZoom(17.0);
        }
        map.invalidate();

        tvStatus.setText(String.format(Locale.getDefault(),
                "Tọa độ: %.5f, %.5f | Điểm: %d | Độ cao: %.0fm",
                location.getLatitude(), location.getLongitude(), trackPoints.size(), location.getAltitude()));
    }

    private void saveToGPX() {
        if (trackPoints.isEmpty()) {
            Toast.makeText(this, "Chưa có dữ liệu lộ trình!", Toast.LENGTH_SHORT).show();
            return;
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "Track_" + timeStamp + ".gpx";

        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null || !dir.exists()) {
            dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        }
        File file = new File(dir, fileName);

        StringBuilder gpx = new StringBuilder();
        gpx.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        gpx.append("<gpx version=\"1.1\" creator=\"OfflineTracker\">\n  <trk>\n    <trkseg>\n");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        for (Location loc : trackPoints) {
            gpx.append(String.format(Locale.US, "      <trkpt lat=\"%.6f\" lon=\"%.6f\">\n        <ele>%.1f</ele>\n        <time>%s</time>\n      </trkpt>\n",
                    loc.getLatitude(), loc.getLongitude(), loc.getAltitude(), sdf.format(new Date(loc.getTime()))));
        }
        gpx.append("    </trkseg>\n  </trk>\n</gpx>");

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(gpx.toString());
            Toast.makeText(this, "Đã lưu vào Download:\n" + fileName, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Lỗi lưu file GPX", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }

    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
}
