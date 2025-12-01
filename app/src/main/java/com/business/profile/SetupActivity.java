package com.business.profile;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

public class SetupActivity extends AppCompatActivity {
	
	private SharedPreferences sharedPreferences;
	private static final String PREF_NAME = "BusinessProfilePrefs";
	private static final String KEY_URL = "saved_url";
	private static final String KEY_APP_NAME = "saved_app_name";
	private static final String KEY_FIRST_RUN = "first_run";
	private static final String KEY_CURRENT_ICON = "current_icon_index";
	
	private EditText etAppName, etUrl;
	private ImageView imgCurrentIcon;
	private Button btnSave;
	
	// ৫টি আইকনের রিসোর্স লিস্ট
	private final int[] iconResources = {
		R.mipmap.icon1, R.mipmap.icon2, R.mipmap.icon3, R.mipmap.icon4, R.mipmap.icon5
	};
	
	// ম্যানিফেস্টে ডিক্লেয়ার করা activity-alias এর নামগুলো
	private final String[] iconAliases = {
		".Icon1", ".Icon2", ".Icon3", ".Icon4", ".Icon5"
	};
	
	private static final int PERMISSION_REQ_CODE = 999;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
		
		// ১. চেক করা অ্যাপ আগে কনফিগার করা হয়েছে কিনা
		if (!sharedPreferences.getBoolean(KEY_FIRST_RUN, true)) {
			openMainActivity();
			return;
		}
		
		setContentView(R.layout.activity_setup);
		
		// ভিউ ইনিশিয়ালাইজেশন
		etAppName = findViewById(R.id.etAppName);
		etUrl = findViewById(R.id.etUrl);
		btnSave = findViewById(R.id.btnSave);
		imgCurrentIcon = findViewById(R.id.imgCurrentIcon);
		View layoutIconSelector = findViewById(R.id.layoutIconSelector);
		
		// ২. বর্তমান আইকন লোড করা (ডিফল্ট 0)
		int currentIconIndex = sharedPreferences.getInt(KEY_CURRENT_ICON, 0);
		if (currentIconIndex < iconResources.length) {
			imgCurrentIcon.setImageResource(iconResources[currentIconIndex]);
		}
		
		// ৩. আইকন চেঞ্জ ডায়ালগ ওপেন করা
		layoutIconSelector.setOnClickListener(v -> showIconPickerDialog());
		
		// ৪. সেভ বাটন - প্রথমে পারমিশন চেক, তারপর সেভ
		btnSave.setOnClickListener(v -> checkPermissionsAndSave());
	}
	
	// --- NEW HORIZONTAL ICON PICKER DIALOG ---
	private void showIconPickerDialog() {
		// কাস্টম লেআউট ইনফ্লেট করা
		View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_icon_picker, null);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setView(dialogView);
		AlertDialog dialog = builder.create();
		
		// ব্যাকগ্রাউন্ড ট্রান্সপারেন্ট করা (রাউন্ডেড কর্নারের জন্য)
		if (dialog.getWindow() != null) {
			dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		}
		
		LinearLayout iconContainer = dialogView.findViewById(R.id.iconContainer);
		Button btnCancel = dialogView.findViewById(R.id.btnCancelIcon);
		
		// ডায়নামিকালি আইকনগুলো হরাইজন্টাল স্ক্রল ভিউতে যোগ করা
		for (int i = 0; i < iconResources.length; i++) {
			final int index = i;
			
			ImageView imageView = new ImageView(this);
			
			// আইকনের সাইজ নির্ধারণ (70dp x 70dp)
			int size = dpToPx(70);
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
			
			// মার্জিন দেওয়া
			int margin = dpToPx(8);
			params.setMargins(margin, 0, margin, 0);
			imageView.setLayoutParams(params);
			
			imageView.setImageResource(iconResources[i]);
			imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
			
			// ক্লিক ইফেক্ট এবং লিসেনার
			imageView.setBackgroundResource(R.drawable.bg_popup_rounded);
			imageView.setClickable(true);
			imageView.setFocusable(true);
			imageView.setPadding(dpToPx(5), dpToPx(5), dpToPx(5), dpToPx(5));
			
			imageView.setOnClickListener(v -> {
				changeAppIcon(index);
				dialog.dismiss();
			});
			
			iconContainer.addView(imageView);
		}
		
		btnCancel.setOnClickListener(v -> dialog.dismiss());
		dialog.show();
	}
	
	// --- APP ICON CHANGE LOGIC ---
	private void changeAppIcon(int newIconIndex) {
		int currentIndex = sharedPreferences.getInt(KEY_CURRENT_ICON, 0);
		if (newIconIndex == currentIndex) return;
		
		PackageManager pm = getPackageManager();
		String packageName = getPackageName();
		
		try {
			// ১. আগের আইকন ডিসেবল করা
			pm.setComponentEnabledSetting(
			new ComponentName(packageName, packageName + iconAliases[currentIndex]),
			PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
			PackageManager.DONT_KILL_APP
			);
			
			// ২. নতুন আইকন এনাবল করা
			pm.setComponentEnabledSetting(
			new ComponentName(packageName, packageName + iconAliases[newIconIndex]),
			PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
			PackageManager.DONT_KILL_APP
			);
			
			// ৩. প্রেফারেন্সে সেভ করা
			sharedPreferences.edit().putInt(KEY_CURRENT_ICON, newIconIndex).apply();
			
			Toast.makeText(this, "Updating Icon... App will restart.", Toast.LENGTH_LONG).show();
			
			// ৪. ফোর্স রিস্টার্ট (দেরি করিয়ে যাতে টোস্ট দেখা যায়)
			new Handler().postDelayed(() -> {
				Intent i = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
				if (i != null) {
					i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(i);
				}
				System.exit(0); // Kill Process
			}, 1500);
			
			} catch (Exception e) {
			Toast.makeText(this, "Error changing icon: " + e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}
	
	// --- PERMISSION & SAVE LOGIC ---
	private void checkPermissionsAndSave() {
		String name = etAppName.getText().toString().trim();
		String url = etUrl.getText().toString().trim();
		
		if (TextUtils.isEmpty(url)) {
			etUrl.setError(getString(R.string.msg_enter_url_error));
			etUrl.requestFocus();
			return;
		}
		
		// Android 13+ (API 33) Notification Permission Check
		if (Build.VERSION.SDK_INT >= 33) {
			String permissionPostNotif = "android.permission.POST_NOTIFICATIONS";
			if (ContextCompat.checkSelfPermission(this, permissionPostNotif) != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this, new String[]{permissionPostNotif}, PERMISSION_REQ_CODE);
				return; // রেজাল্টের জন্য অপেক্ষা করুন
			}
		}
		
		// Android 6 - 9 Storage Permission Check
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this,
				new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
				PERMISSION_REQ_CODE);
				return; // রেজাল্টের জন্য অপেক্ষা করুন
			}
		}
		
		// সব ঠিক থাকলে সেভ করুন
		saveData(name, url);
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == PERMISSION_REQ_CODE) {
			// পারমিশন দিক বা না দিক, আমরা সেভ করে আগাবো
			String name = etAppName.getText().toString().trim();
			String url = etUrl.getText().toString().trim();
			saveData(name, url);
		}
	}
	
	private void saveData(String name, String url) {
		if (TextUtils.isEmpty(name)) name = getString(R.string.app_name);
		if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
		
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(KEY_APP_NAME, name);
		editor.putString(KEY_URL, url);
		editor.putBoolean(KEY_FIRST_RUN, false);
		editor.apply();
		
		Toast.makeText(this, "Setup Complete!", Toast.LENGTH_SHORT).show();
		openMainActivity();
	}
	
	private void openMainActivity() {
		Intent intent = new Intent(SetupActivity.this, MainActivity.class);
		startActivity(intent);
		finish();
	}
	
	// Helper: Convert DP to Pixels
	private int dpToPx(int dp) {
		float density = getResources().getDisplayMetrics().density;
		return Math.round(dp * density);
	}
}