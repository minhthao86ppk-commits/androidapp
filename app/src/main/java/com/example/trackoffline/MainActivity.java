package com.example.trackoffline;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private LocationManager locationManager;
    private TextView tvStatus, tvLocation, tvNavInfo;
    private Button btnStart, btnStop;

    private boolean isTracking = false;
    private final List<Location> trackPoints = new ArrayList<>();
    private static final int PERMISSION_REQUEST_CODE = 1001;

    @Override
    protected void组织(Bundle savedInstanceState) {
        // Reserved
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvLocation = findViewById(R.id.tvLocation);
        tvNavInfo = findViewById(R.id.tvNavInfo);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        btnStart.setOnClickListener(v -> startTracking());
        btnStop.setOnClickListener(v -> stopTrackingAndSave());

        checkLocationPermission();
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    private void startTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            checkLocationPermission();
            return;
        }

        trackPoints.clear();
        isTracking = true;
        tvStatus.setText("Trạng thái: Đang ghi hành trình...");
        
        // Nhận cập nhật GPS mỗi 2 giây hoặc di chuyển trên 3 mét
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 3.0f, this);
        Toast.makeText(this, "Bắt đầu ghi GPS", Toast.LENGTH_SHORT).show();
    }

    private void stopTrackingAndSave() {
        if (!isTracking) {
            Toast.makeText(this, "Chưa bật chế độ ghi!", Toast.LENGTH_SHORT).show();
            return;
        }

        isTracking = false;
        locationManager.removeUpdates(this);
        tvStatus.setText("Trạng thái: Đã dừng");

        saveTrackToGpx();
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        double alt = location.getAltitude();

        tvLocation.setText(String.format(Locale.getDefault(), 
                "Vĩ độ: %.6f\nKinh độ: %.6f\nĐộ cao: %.1f m\nTốc độ: %.1f km/h", 
                lat, lon, alt, (location.getSpeed() * 3.6)));

        if (isTracking) {
            trackPoints.add(location);
            tvNavInfo.setText("Số điểm đã lưu: " + trackPoints.size());
        }
    }

    private void saveTrackToGpx() {
        if (trackPoints.isEmpty()) {
            Toast.makeText(this, "Không có dữ liệu để lưu!", Toast.LENGTH_SHORT).show();
            return;
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "Track_" + timeStamp + ".gpx";

        File exportDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (exportDir == null) {
            exportDir = getFilesDir();
        }

        File file = new File(exportDir, fileName);

        try (FileWriter writer = new FileWriter(file)) {
            writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.append("<gpx version=\"1.1\" creator=\"OfflineTracker\">\n");
            writer.append("  <trk>\n    <name>Hanh Trinh ").append(timeStamp).append("</name>\n    <trkseg>\n");

            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

            for (Location pt : trackPoints) {
                writer.append(String.format(Locale.US,
                        "      <trkpt lat=\"%.6f\" lon=\"%.6f\">\n" +
                        "        <ele>%.1f</ele>\n" +
                        "        <time>%s</time>\n" +
                        "      </trkpt>\n",
                        pt.getLatitude(), pt.getLongitude(), pt.getAltitude(), isoFormat.format(new Date(pt.getTime()))));
            }

            writer.append("    </trkseg>\n  </trk>\n</gpx>");
            writer.flush();

            Toast.makeText(this, "Đã lưu: " + file.getName(), Toast.LENGTH_LONG).show();
            tvNavInfo.setText("Tệp đã lưu tại:\n" + file.getAbsolutePath());
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi lưu file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {}

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        tvStatus.setText("Cảnh báo: GPS đã bị tắt!");
    }
}
