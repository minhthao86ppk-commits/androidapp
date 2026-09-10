package com.example.trackoffline;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private TextView tvStatus, tvLocation, tvNavInfo;
    private Button btnStart, btnStop;
    private LocationManager locationManager;
    private boolean isTracking = false;
    private final List<Location> trackPoints = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvLocation = findViewById(R.id.tvLocation);
        tvNavInfo = findViewById(R.id.tvNavInfo);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTracking();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTrackingAndSaveGPX();
            }
        });
    }

    private void startTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 101);
            return;
        }

        trackPoints.clear();
        isTracking = true;
        // Cập nhật vị trí mỗi 2 giây hoặc khi di chuyển từ 1 mét trở lên
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 1, this);
        tvStatus.setText("Trạng thái: Đang ghi hành trình GPS...");
        Toast.makeText(this, "Đã bắt đầu ghi lộ trình", Toast.LENGTH_SHORT).show();
    }

    private void stopTrackingAndSaveGPX() {
        if (!isTracking) {
            Toast.makeText(this, "Chưa bật ghi hành trình", Toast.LENGTH_SHORT).show();
            return;
        }

        isTracking = false;
        locationManager.removeUpdates(this);
        tvStatus.setText("Trạng thái: Đã dừng");

        if (trackPoints.isEmpty()) {
            Toast.makeText(this, "Chưa thu thập được điểm tọa độ nào!", Toast.LENGTH_SHORT).show();
            return;
        }

        saveToGPX();
    }

    private void saveToGPX() {
        String fileName = "Track_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".gpx";
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, fileName);

        StringBuilder gpx = new StringBuilder();
        gpx.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        gpx.append("<gpx version=\"1.1\" creator=\"OfflineTracker\">\n");
        gpx.append("  <trk>\n    <trkseg>\n");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        for (Location loc : trackPoints) {
            gpx.append(String.format(Locale.US, "      <trkpt lat=\"%.6f\" lon=\"%.6f\">\n", loc.getLatitude(), loc.getLongitude()));
            gpx.append(String.format(Locale.US, "        <ele>%.1f</ele>\n", loc.getAltitude()));
            gpx.append("        <time>").append(sdf.format(new Date(loc.getTime()))).append("</time>\n");
            gpx.append("      </trkpt>\n");
        }

        gpx.append("    </trkseg>\n  </trk>\n</gpx>");

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(gpx.toString());
            Toast.makeText(this, "Đã lưu GPX: " + file.getName(), Toast.LENGTH_LONG).show();
            tvNavInfo.setText("Tệp đã lưu tại: " + file.getAbsolutePath());
        } catch (IOException e) {
            Toast.makeText(this, "Lỗi khi lưu file GPX", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (!isTracking) return;

        trackPoints.add(location);
        tvLocation.setText(String.format(Locale.getDefault(), "Tọa độ: %.6f, %.6f | Điểm: %d",
                location.getLatitude(), location.getLongitude(), trackPoints.size()));

        // Hướng dẫn dẫn đường cơ bản theo vết (Backtrack)
        if (trackPoints.size() > 1) {
            Location prev = trackPoints.get(trackPoints.size() - 2);
            float dist = location.distanceTo(prev);
            float bearing = location.bearingTo(prev);
            tvNavInfo.setText(String.format(Locale.getDefault(), "Khoảng cách điểm trước: %.1fm | Góc: %.1f°", dist, bearing));
        }
    }

    @Override public void onProviderEnabled(@NonNull String provider) {}
    @Override public void onProviderDisabled(@NonNull String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
}
