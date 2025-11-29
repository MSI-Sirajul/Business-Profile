package com.business.profile;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class InfoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        setupSocialLinks();
        populateDeviceInfo();
    }

    private void setupSocialLinks() {
        ImageView btnTelegram = findViewById(R.id.btnTelegram);
        ImageView btnYoutube = findViewById(R.id.btnYoutube);
        ImageView btnFacebook = findViewById(R.id.btnFacebook);
        TextView txtWebsite = findViewById(R.id.txtWebsite);

        // আপনার সোশ্যাল লিংকগুলো এখানে দিন
        btnTelegram.setOnClickListener(v -> openLink(getString(R.string.url_telegram)));
        btnYoutube.setOnClickListener(v -> openLink(getString(R.string.url_youtube)));
        btnFacebook.setOnClickListener(v -> openLink(getString(R.string.url_facebook)));
        
        // ওয়েবসাইট লিংকে ক্লিক করলে ওপেন হবে
        txtWebsite.setOnClickListener(v -> openLink(getString(R.string.dev_website)));
    }

    private void populateDeviceInfo() {
        // Helper method to set Label and Value
        setRowData(R.id.rowBrand, getString(R.string.lbl_brand), Build.BRAND.toUpperCase());
        setRowData(R.id.rowModel, getString(R.string.lbl_model), Build.MODEL);
        
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        setRowData(R.id.rowId, getString(R.string.lbl_device_id), deviceId);
        
        setRowData(R.id.rowVersion, getString(R.string.lbl_version), Build.VERSION.RELEASE);
        setRowData(R.id.rowOs, getString(R.string.lbl_os), "Android " + Build.VERSION.RELEASE);
        setRowData(R.id.rowSdk, getString(R.string.lbl_sdk), String.valueOf(Build.VERSION.SDK_INT));
        
        setRowData(R.id.rowBattery, getString(R.string.lbl_battery), getBatteryPercentage() + "%");
        setRowData(R.id.rowMemory, getString(R.string.lbl_memory), getTotalMemory());
    }

    private void setRowData(int includeId, String label, String value) {
        View row = findViewById(includeId);
        TextView txtLabel = row.findViewById(R.id.infoLabel);
        TextView txtValue = row.findViewById(R.id.infoValue);
        
        txtLabel.setText(label);
        txtValue.setText(value);
    }

    private int getBatteryPercentage() {
        if (Build.VERSION.SDK_INT >= 21) {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } else {
            // For older versions (fallback)
            return 0; 
        }
    }

    private String getTotalMemory() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        
        long totalMem = memoryInfo.totalMem;
        // Convert to GB
        double gb = (double) totalMem / (1024 * 1024 * 1024);
        return String.format("%.2f GB", gb);
    }

    private void openLink(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
        }
    }
}