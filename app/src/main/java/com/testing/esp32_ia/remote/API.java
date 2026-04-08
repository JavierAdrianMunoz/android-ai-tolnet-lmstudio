package com.testing.esp32_ia.remote;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class API {
    // crear notificacion de prueba

    final String TAG ="API";
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    public void sendToApi(Context context,String pregunta) {
        Notification notification = new Notification(context);

        try {
            String finalMessage = "Limpia y solo obten pregunta e incisos, posteriormente Responde solo la letra de la respuesta correcta a:".concat(pregunta);
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("model", "llama-3.2-3b");
            bodyJson.put("input", finalMessage);

            RequestBody body = RequestBody.create(
                    bodyJson.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url("https://te8wpujraw.localto.net/v1/responses")
                    .addHeader("localtonet-skip-warning", "true")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();
            Log.d(TAG, "mensaje a enviar:"+request.toString());
            Log.d(TAG, finalMessage);
            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {

                    if (!response.isSuccessful()) {
                        Log.e(TAG, "HTTP ERROR: " + response.code());
                        return;
                    }

                    String resBody = response.body().string();
                    Log.d(TAG, "RAW RESPONSE: " + resBody);

                    try {
                        JSONObject json = new JSONObject(resBody);

                        JSONArray output = json.getJSONArray("output");

                        String finalText = "";

                        for (int i = 0; i < output.length(); i++) {

                            JSONObject item = output.getJSONObject(i);

                            if ("message".equals(item.getString("type"))) {

                                JSONArray content = item.getJSONArray("content");

                                for (int j = 0; j < content.length(); j++) {

                                    JSONObject c = content.getJSONObject(j);

                                    if ("output_text".equals(c.getString("type"))) {
                                        finalText = c.getString("text");
                                    }
                                }
                            }
                        }

                        Log.d(TAG, "RESPUESTA FINAL: " + finalText);
                        vibrarSegunRespuesta(context,finalText);
                        // aquí puedes:
                        // mostrar en UI
                        // guardar
                        // TTS, etc.
                        notification.createNotificationChannel();
                        notification.sendNotification(finalText);
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error: " + e.getMessage());
                        vibrarError(context);
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Build JSON error: " + e.getMessage());
            vibrarError(context);
        }
    }

    public static void vibrarSegunRespuesta(Context context, String finalText) {
        String TAG = "API";
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.w("VibradorHelper", "No hay vibrador disponible");
            return;
        }

        // vibracion inicial de atencion a respuesta:
        long[] atencionPattern = {0, 1000, 100, 1000}; // espera 0, vibra 1s, pausa 100ms, vibra 1s


        // Extraer la primera letra no vacía
        if (finalText == null || finalText.trim().isEmpty()) {
            return;
        }
        char letra = Character.toLowerCase(finalText.trim().charAt(0));

        // Patrones en milisegundos: [espera inicial, vibración, espera, vibración, ...]
        long[] pattern;
        switch (letra) {
            case 'a':
                pattern = new long[]{0, 200};
                break;
            case 'b':
                pattern = new long[]{0, 200, 100, 200};
                break;
            case 'c':
                pattern = new long[]{0, 200, 100, 200, 100, 200};
                break;
            case 'd':
                pattern = new long[]{0, 500};
                break;
            case 'e':
                pattern = new long[]{0, 500, 200, 500};
                break;
            case 'f':
                pattern = new long[]{0, 500, 200, 500, 200, 500};
                break;
            default:
                pattern = new long[]{0, 100}; // vibración corta por defecto
                break;
        }
        Log.d(TAG, "respuesta elegida:"+letra);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(atencionPattern, -1));
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            // Deprecated pero funciona en versiones antiguas
            vibrator.vibrate(VibrationEffect.createWaveform(atencionPattern, -1));
            vibrator.vibrate(pattern, -1);
        }
    }

    public static void vibrarError(Context context) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }

        long[] pattern = new long[]{0, 500, 200, 200, 200, 200}; // largo-corto-corto
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            vibrator.vibrate(pattern, -1);
        }
    }
}
