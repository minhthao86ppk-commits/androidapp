package com.example.trackoffline;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {

    private TextView tvDashboard, tvNavGuide;
    private Button btnToggle, btnSave;
    private TrackCanvasView trackView;
    private LocationManager locationManager;
    private boolean isTracking = false;
    private final List<Location> trackPoints = new ArrayList<>();
    private float totalDistanceMeters = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(20, 20, 20, 20);

        // Bảng đồng hồ thông số chiến thuật
        tvDashboard = new TextView(this);
        tvDashboard.setText("TỌA ĐỘ: Đang đợi tín hiệu GPS...\nTỐC ĐỘ: 0.0 km/h | QUÃNG ĐƯỜNG: 0 m");
        tvDashboard.setTextColor(Color.parseColor("#00FF66"));
        tvDashboard.setTextSize(13);
        tvDashboard.setPadding(10, 10, 10, 10);
        root.addView(tvDashboard);

        // Màn hình đồ họa vẽ vết hành trình & mũi tên chỉ đường
        trackView = new TrackCanvasView(this);
        LinearLayout.LayoutParams viewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        viewParams.setMargins(0, 10, 0, 10);
        trackView.setLayoutParams(viewParams);
        root.addView(trackView);

        // Thanh chỉ dẫn điều hướng
        tvNavGuide = new TextView(this);
        tvNavGuide.setText("HƯỚNG DẪN ĐƯỜNG: Chưa kích hoạt");
        tvNavGuide.setTextColor(Color.YELLOW);
        tvNavGuide.setTextSize(13);
        tvNavGuide.setPadding(10, 10, 10, 15);
        root.addView(tvNavGuide);

        // Nút điều khiển
        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);

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
        totalDistanceMeters = 0;
        isTracking = true;
        btnToggle.setText("DỪNG GHI");
        if (locationManager != null) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500, 1, this);
        }
        Toast.makeText(this, "Bắt đầu thu thập dữ liệu hành trình", Toast.LENGTH_SHORT).show();
    }

    private void stopTracking() {
        isTracking = false;
        btnToggle.setText("BẮT ĐẦU GHI VẾT");
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        Toast.makeText(this, "Đã tạm dừng ghi vết", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!isTracking) return;

        if (!trackPoints.isEmpty()) {
            Location lastLoc = trackPoints.get(trackPoints.size() - 1);
            totalDistanceMeters += lastLoc.distanceTo(location);
        }
        trackPoints.add(location);

        // Cập nhật thông số bảng đồng hồ
        float speedKmh = location.hasSpeed() ? (location.getSpeed() * 3.6f) : 0.0f;
        tvDashboard.setText(String.format(Locale.getDefault(),
                "TỌA ĐỘ: %.6f, %.6f | CAO: %.1fm\nTỐC ĐỘ: %.1f km/h | ĐÃ ĐI: %.0f m (%d điểm)",
                location.getLatitude(), location.getLongitude(), location.getAltitude(),
                speedKmh, totalDistanceMeters, trackPoints.size()));

        // Tính góc và cự ly quay ngược về điểm xuất phát (Backtrack)
        if (trackPoints.size() > 1) {
            Location startLoc = trackPoints.get(0);
            float distToStart = location.distanceTo(startLoc);
            float bearingToStart = location.bearingTo(startLoc);
            if (bearingToStart < 0) bearingToStart += 360;

            tvNavGuide.setText(String.format(Locale.getDefault(),
                    "VỀ ĐIỂM ĐẦU: Cự ly %.0fm | Hướng đi: %.0f°", distToStart, bearingToStart));
            trackView.setGuideData(bearingToStart, distToStart);
        }

        // Vẽ lại đồ họa vết di chuyển
        trackView.updateTrack(trackPoints);
    }

    private void saveToGPX() {
        if (trackPoints.isEmpty()) {
            Toast.makeText(this, "Không có dữ liệu vết để lưu", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Lỗi khi lưu tệp GPX", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}

    // View đồ họa vẽ trực tiếp lộ trình và mũi tên la bàn
    public static class TrackCanvasView extends View {
        private final Paint gridPaint = new Paint();
        private final Paint trackPaint = new Paint();
        private final Paint currentPosPaint = new Paint();
        private final Paint arrowPaint = new Paint();
        private final List<Location> points = new ArrayList<>();
        private float navBearing = 0;
        private boolean hasGuide = false;

        public TrackCanvasView(Context context) {
            super(context);
            gridPaint.setColor(Color.parseColor("#252525"));
            gridPaint.setStrokeWidth(2);

            trackPaint.setColor(Color.parseColor("#00E5FF")); // Vết di chuyển màu xanh ngọc neon
            trackPaint.setStrokeWidth(5);
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setAntiAlias(true);

            currentPosPaint.setColor(Color.RED);
            currentPosPaint.setStyle(Paint.Style.FILL);
            currentPosPaint.setAntiAlias(true);

            arrowPaint.setColor(Color.YELLOW);
            arrowPaint.setStrokeWidth(6);
            arrowPaint.setStyle(Paint.Style.STROKE);
            arrowPaint.setAntiAlias(true);
        }

        public void updateTrack(List<Location> newPoints) {
            points.clear();
            points.addAll(newPoints);
            invalidate();
        }

        public void setGuideData(float bearing, float distance) {
            this.navBearing = bearing;
            this.hasGuide = true;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;

            // 1. Vẽ lưới radar tọa độ
            canvas.drawColor(Color.parseColor("#1A1A1A"));
            canvas.drawLine(cx, 0, cx, h, gridPaint);
            canvas.drawLine(0, cy, w, cy, gridPaint);
            canvas.drawCircle(cx, cy, Math.min(w, h) * 0.25f, gridPaint);
            canvas.drawCircle(cx, cy, Math.min(w, h) * 0.45f, gridPaint);

            if (points.isEmpty()) return;

            // 2. Tự động tính tỷ lệ co giãn để vết đường luôn vừa vặn trong màn hình
            double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
            double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
            for (Location p : points) {
                if (p.getLatitude() < minLat) minLat = p.getLatitude();
                if (p.getLatitude() > maxLat) maxLat = p.getLatitude();
                if (p.getLongitude() < minLon) minLon = p.getLongitude();
                if (p.getLongitude() > maxLon) maxLon = p.getLongitude();
            }

            double dLat = Math.max(maxLat - minLat, 0.0001);
            double dLon = Math.max(maxLon - minLon, 0.0001);
            float scale = (float) (Math.min(w, h) * 0.7f / Math.max(dLat, dLon));

            // 3. Vẽ vết lộ trình
            Path path = new Path();
            float lastX = cx, lastY = cy;
            for (int i = 0; i < points.size(); i++) {
                Location p = points.get(i);
                float px = (float) (cx + (p.getLongitude() - (minLon + maxLon) / 2) * scale);
                float py = (float) (cy - (p.getLatitude() - (minLat + maxLat) / 2) * scale);

                if (i == 0) path.moveTo(px, py);
                else path.lineTo(px, py);

                lastX = px;
                lastY = py;
            }
            canvas.drawPath(path, trackPaint);

            // 4. Đánh dấu vị trí tức thời (Chấm đỏ)
            canvas.drawCircle(lastX, lastY, 12, currentPosPaint);

            // 5. Vẽ mũi tên chỉ đường nếu có dữ liệu dẫn đường
            if (hasGuide) {
                canvas.save();
                canvas.rotate(navBearing, cx, cy);
                canvas.drawLine(cx, cy, cx, cy - 80, arrowPaint);
                canvas.drawLine(cx, cy - 80, cx - 20, cy - 60, arrowPaint);
                canvas.drawLine(cx, cy - 80, cx + 20, cy - 60, arrowPaint);
                canvas.restore();
            }
        }
    }
}
