package com.testing.esp32_ia.utils;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import com.google.mlkit.vision.text.Text;
import com.testing.esp32_ia.StreamFragment;

import java.util.ArrayList;
import java.util.List;

public class utils{
    private final String TAG = "utils";
    public void vibrate(Context context) {
        Log.d(TAG, "vibrate() called with: context = [" + context + "]");

        Vibrator vibrator;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm =
                    (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vm.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
        Log.d(TAG, "hasVibrator: " + vibrator.hasVibrator());
        Log.d(TAG, "SDK: " + Build.VERSION.SDK_INT);
        if (vibrator == null || !vibrator.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                    VibrationEffect.createOneShot(1500, 255)
            );
        } else {
            vibrator.vibrate(1500);
        }
    }
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isCapturing = false;

    private final Runnable captureVibrationTask = new Runnable() {
        @Override
        public void run() {
            if (!isCapturing) return;

            vibrateShort(appContext); // vibración corta

            handler.postDelayed(this, 1000); // cada 1s
        }
    };

    private Context appContext;

    public void startCaptureVibration(Context context) {
        if (isCapturing) return;

        this.appContext = context.getApplicationContext();
        isCapturing = true;
        handler.post(captureVibrationTask);
    }

    public void stopCaptureVibration() {
        isCapturing = false;
        handler.removeCallbacks(captureVibrationTask);
    }
    public void vibrateShort(Context context) {

        Vibrator vibrator;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm =
                    (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vm.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }

        if (vibrator == null || !vibrator.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                    VibrationEffect.createOneShot(120, 180) // corto y suave
            );
        } else {
            vibrator.vibrate(120);
        }
    }
    /* utilidades para normalizar texto */
    private String normalize(String text){
        return text
                .toLowerCase()
                .replaceAll("[^a-z0-9áéíóúñ ]","")
                .replaceAll("\\s+"," ")
                .trim();
    }
    private boolean isValidText(String text){
        return text.contains("?") && text.length() > 20;
        //return text.length() > 15 && text.split(" ").length > 3;
    }
    public List<String> capturedTexts = new ArrayList<>();
    private void saveCapturedText(String text){
        Log.d(TAG, "saveCapturedText() called with: text = [" + text + "]");
        capturedTexts.add(text);

        Log.d(TAG,"Texto guardado: " + text);
    }
    private boolean isSameRegion(Rect a, Rect b){

        int dx = Math.abs(a.centerX() - b.centerX());
        int dy = Math.abs(a.centerY() - b.centerY());
        Log.d(TAG, "isSameRegion: dx:["+dx+"] 40 dy:["+dy+"] COND:["+(dx < 40 && dy < 40)+"]");
        return dx < 40 && dy < 40;
    }
}
