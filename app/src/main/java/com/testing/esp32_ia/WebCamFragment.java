package com.testing.esp32_ia;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.testing.esp32_ia.remote.API;
import com.testing.esp32_ia.utils.OCRGraphicOverlay;
import com.testing.esp32_ia.utils.utils;

import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WebCamFragment extends Fragment {

    private static final String TAG = "WebCamFragment";
    private OCRGraphicOverlay overlay;
    // UI
    private TextureView textureView;
    private TextView txtOCR;

    // Camera2
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewBuilder;
    private ImageReader imageReader;
    private String cameraId;

    // Threads
    private HandlerThread bgThread;
    private Handler bgHandler;

    // OCR
    private TextRecognizer recognizer;
    private boolean isProcessingFrame = false;

    // Control
    private long lastFrameTime = 0;
    private static final long FRAME_INTERVAL_MS = 800;

    // Estabilización simple
    private String lastText = "";
    private int stableCount = 0;
    private static final int STABLE_THRESHOLD = 3;

    // Control de estado
    private boolean isCameraOpen = false;
    /*buffer temporal */
    private final List<String> buffer = new ArrayList<>();
    private final int MAX_BUFFER = 10;
    API api = new API();
    utils util = new utils();
    // ==============================
    // Lifecycle
    // ==============================

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_web_cam, container, false);

        textureView = view.findViewById(R.id.textureView);
        txtOCR = view.findViewById(R.id.txtOCR);

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        overlay = view.findViewById(R.id.overlay);
        textureView.setSurfaceTextureListener(surfaceListener);

        Log.d(TAG, "Fragment creado");

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        startBackgroundThread();

        if (textureView.isAvailable()) {
            openCamera();
        }

        Log.d(TAG, "onResume()");
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();

        Log.d(TAG, "onPause()");
    }

    // ==============================
    // Camera setup
    // ==============================

    private final TextureView.SurfaceTextureListener surfaceListener =
            new TextureView.SurfaceTextureListener() {

                @Override
                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int w, int h) {
                    Log.d(TAG, "Surface disponible");
                    openCamera();
                }

                @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture s, int w, int h) {}
                @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture s) { return false; }
                @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture s) {}
            };

    private void openCamera() {
        try {
            if (isCameraOpen) {
                Log.w(TAG, "Camera ya abierta, evitando duplicado");
                return;
            }
            isCameraOpen = true;
            Log.d(TAG, "Abriendo camara...");
            CameraManager manager = requireContext().getSystemService(CameraManager.class);

            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                Integer lens = c.get(CameraCharacteristics.LENS_FACING);

                if (lens != null && lens == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                    cameraId = id;
                    Log.d(TAG, "Usando cámara EXTERNA: " + id);
                    break;
                }
            }

            if (cameraId == null) {
                cameraId = manager.getCameraIdList()[0];
                Log.d(TAG, "Fallback cámara: " + cameraId);
            }

            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
                return;
            }

            manager.openCamera(cameraId, stateCallback, bgHandler);

        } catch (Exception e) {
            Log.e(TAG, "Error openCamera", e);
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera abierta");
            cameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.w(TAG, "Camera desconectada");

            camera.close();
            cameraDevice = null;
            isCameraOpen = false;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "Error camera: " + error);
            camera.close();
        }
    };

    private void startPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(640, 480);

            Surface previewSurface = new Surface(texture);

            imageReader = ImageReader.newInstance(
                    640, 480,
                    android.graphics.ImageFormat.YUV_420_888,
                    2
            );

            imageReader.setOnImageAvailableListener(imageListener, bgHandler);

            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(previewSurface);
            previewBuilder.addTarget(imageReader.getSurface());

            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                session.setRepeatingRequest(previewBuilder.build(), null, bgHandler);
                                Log.d(TAG, "Preview iniciado");
                            } catch (Exception e) {
                                Log.e(TAG, "Error preview", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Fallo configuración preview");
                        }
                    },
                    bgHandler
            );

        } catch (Exception e) {
            Log.e(TAG, "Error startPreview", e);
        }
    }

    // ==============================
    // Frame processing
    // ==============================

    private final ImageReader.OnImageAvailableListener imageListener = reader -> {

        long now = System.currentTimeMillis();

        if (isProcessingFrame || (now - lastFrameTime < FRAME_INTERVAL_MS)) {
            Image imgSkip = reader.acquireLatestImage();
            if (imgSkip != null) imgSkip.close();
            return;
        }

        Image image = reader.acquireLatestImage();
        if (image == null) return;

        isProcessingFrame = true;
        lastFrameTime = now;

        int rotation = getRotationCompensation(cameraId);

        Log.d(TAG, "Frame capturado → OCR");

        InputImage inputImage = InputImage.fromMediaImage(image, rotation);

        processOCR(inputImage, image);
    };

    private int getRotationCompensation(String cameraId) {
        CameraManager cameraManager =
                (CameraManager) requireContext().getSystemService(getContext().CAMERA_SERVICE);

        try {
            CameraCharacteristics characteristics =
                    cameraManager.getCameraCharacteristics(cameraId);

            int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            int deviceRotation = requireActivity()
                    .getWindowManager()
                    .getDefaultDisplay()
                    .getRotation();

            int rotationCompensation;

            switch (deviceRotation) {
                case Surface.ROTATION_0: rotationCompensation = 0; break;
                case Surface.ROTATION_90: rotationCompensation = 90; break;
                case Surface.ROTATION_180: rotationCompensation = 180; break;
                case Surface.ROTATION_270: rotationCompensation = 270; break;
                default: rotationCompensation = 0;
            }

            return (sensorOrientation - rotationCompensation + 360) % 360;

        } catch (CameraAccessException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private void processOCR(InputImage image, Image mediaImage) {

        long start = System.currentTimeMillis();

        recognizer.process(image)
                .addOnSuccessListener(text -> {
                    List<Text.TextBlock> blocks = text.getTextBlocks();

                    String detectedText = normalize(text.getText());
                    handleText(text.getText());
                    Log.w(TAG, "Detected text is:"+detectedText);
                    if (!detectedText.isEmpty()) {
                        addToBuffer(detectedText);
                    }

                    requireActivity().runOnUiThread(() -> {
                        overlay.setBlocks(blocks);
                    });
                    long time = System.currentTimeMillis() - start;

                    Log.d(TAG, "OCR OK | bloques=" + text.getTextBlocks().size()
                            + " | tiempo=" + time + "ms");

                    String result = text.getText();

                    if (result.isEmpty()) {
                        Log.d(TAG, "OCR sin texto");
                    } else {
                        Log.d(TAG, "OCR texto:\n" + result);
                    }

                    requireActivity().runOnUiThread(() ->
                            txtOCR.setText(result.isEmpty() ? "Sin texto" : result)
                    );

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "OCR error", e);
                })
                .addOnCompleteListener(task -> {

                    // ← AQUÍ se cierra correctamente
                    try {
                        mediaImage.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Error cerrando imagen", e);
                    }

                    isProcessingFrame = false;

                    Log.d(TAG, "Frame liberado");
                });
    }

    private void addToBuffer(String text) {

        buffer.add(text);

        if (buffer.size() > MAX_BUFFER) {
            buffer.remove(0);
        }

        String stable = getMostFrequent();

        if (isStable(stable)) {
            onStableTextDetected(stable);
            buffer.clear(); // evita reenvíos
        }
    }
    private String getMostFrequent() {
        Map<String, Integer> freq = new HashMap<>();

        for (String s : buffer) {
            freq.put(s, freq.getOrDefault(s, 0) + 1);
        }

        return Collections.max(freq.entrySet(), Map.Entry.comparingByValue()).getKey();
    }
    private boolean isStable(String text) {
        int count = 0;

        for (String s : buffer) {
            if (similarity(s, text) > 0.8) { // 80% similar
                count++;
            }
        }

        Log.i(TAG, "isStable: count=" + count);

        return count >= 5;
    }
    private void onStableTextDetected(String text) {
        Log.d(TAG, "onStableTextDetected() called with: text = [" + text + "]");
        try {
            JSONObject json = new JSONObject();
            json.put("text", text);
            json.put("timestamp", System.currentTimeMillis());

            Log.w(TAG,"texto enviado:"+text);
            util.vibrate(getContext());
            api.sendToApi(getContext(),text);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void sendToApi(JSONObject json) {

        OkHttpClient client = new OkHttpClient();

        RequestBody body = RequestBody.create(
                json.toString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url("https://tu-api.com/ocr")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) {
                // manejar respuesta
            }
        });
    }
    // ==============================
    // Estabilización simple
    // ==============================

    private void handleText(String text) {
        Log.d(TAG, "handleText() called with: text = [" + text + "]");
        if (text.isEmpty()) return;

        double sim = similarity(normalize(text), normalize(lastText));


        if (sim > 0.85f) {
            stableCount++;
        } else {
            stableCount = 0;
            lastText = text;
        }

        Log.d(TAG, "Similitud: " + sim + " | stableCount: " + stableCount);

        if (stableCount >= STABLE_THRESHOLD) {
            Log.d(TAG, "TEXTO ESTABLE DETECTADO");

            requireActivity().runOnUiThread(() ->
                    txtOCR.setText(text)
            );
        }
    }

    // ==============================
    // Utils
    // ==============================

    private int getRotation() {
        int rotation = requireActivity().getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0: return 0;
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
        }
        return 0;
    }

    private String normalize(String text) {
        return text.toLowerCase()
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private double similarity(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;

        int distance = levenshtein(a, b);
        return 1.0 - (double) distance / maxLen;
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;

                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[a.length()][b.length()];
    }

    // ==============================
    // Thread control
    // ==============================

    private void startBackgroundThread() {
        bgThread = new HandlerThread("CameraBG");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (bgThread != null) {
            bgThread.quitSafely();
            try {
                bgThread.join();
            } catch (InterruptedException ignored) {}
            bgThread = null;
            bgHandler = null;
        }
    }

    private void closeCamera() {
        try {
            if (!isCameraOpen) return;

            isCameraOpen = false;
            Log.d(TAG, "Cerrando cámara");

            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }

            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }

            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error closeCamera", e);
        }
    }


}