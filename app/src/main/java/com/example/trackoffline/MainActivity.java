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
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private TextView tvStatus, tvLocation, tvNavInfo;
    private Button btnStart, btnStop;
    private LocationManager locationManager;
    private boolean isTracking = false;
    private ArrayList<Location> trackPoints = new ArrayList<>();

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

        // Kiểm tra quyền vị trí khi khởi động ứng dụng
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            isTracking = true;
            trackPoints.clear();
            tvStatus.setText("Trạng thái: Đang ghi hành trình GPS...");
            // Cập nhật tọa độ mỗi 5 mét hoặc 3 giây
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 5, this);
            Toast.setItem(this, "Đã bắt đầu ghi lộ trình", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Chưa cấp quyền GPS!", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopTrackingAndSaveGPX() {
        if (!isTracking) {
            Toast.makeText(this, "Chưa bật ghi hành trình!", Toast.LENGTH_SHORT).show();
            return;
        }
        isTracking = false;
        locationManager.removeUpdates(this);
        tvStatus.setText("Trạng thái: Đã dừng ghi.");

        if (trackPoints.isEmpty()) {
            Toast.makeText(this, "Không có dữ liệu tọa độ để lưu!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Xuất file GPX lưu vào bộ nhớ máy
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(dir, "hox_hanh_trinh_" + timeStamp + ".gpx");

            FileWriter writer = new FileWriter(file);
            writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.append("<gpx version=\"1.1\" creator=\"OfflineTracker-J2\">\n");
            writer.append("  <trk>\n");
            writer.append("    <name>Hành trình " + timeStamp + "</name>\n");
            writer.append("    <trkseg>\n");

            for (Location loc : trackPoints) {
                writer.append("      <trkpt lat=\"" + loc.getLatitude() + "\" lon=\"" + loc.getLongitude() + "\">\n");
                writer.append("        <ele>" + loc.getAltitude() + "</ele>\n");
                writer.append("      </trkpt>\n");
            }

            writer.append("    </trkseg>\n");
            writer.append("  </trk>\n");
            writer.append("</gpx>\n");
            writer.flush();
            writer.close();

            Toast.makeText(this, "Đã lưu GPX vào Thư mục Download!", Toast.LENGTH_LONG).show();
            tvNavInfo.setText("Đã xuất file: hox_hanh_trinh_" + timeStamp + ".gpx");
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi khi lưu file GPX: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (isTracking) {
            trackPoints.add(location);
        }
        String info = String.format(Locale.getDefault(), "Lat: %.6f | Lon: %.6f\nĐộ cao: %.1fm | Tốc độ: %.1fm/s",
                location.getLatitude(),
                location.getLongitude(),
                location.getAltitude(),
                location.getSpeed());
        tvLocation.setText(info);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(@NonNull String provider) {}

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        Toast.makeText(this, "Hãy bật GPS trên thiết bị!", Toast.LENGTH_LONG).show();
    }
}
