package com.testing.esp32_ia;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.testing.esp32_ia.remote.API;
import com.testing.esp32_ia.remote.BlockAggregator;
import com.testing.esp32_ia.utils.utils;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StreamFragment extends Fragment {
    private final String TAG = "StreamFragment";
    private ImageView imgStream;
    private Button btnStream;
    private TextView txtOCR;
    private boolean streaming = false;

    public StreamFragment(){}
    private final BlockAggregator aggregator = new BlockAggregator();
/*
* variables de tipo de bloque
* */
private List<TextBlockData> detectedBlocks = new ArrayList<>();
    private String currentBlockBuffer = "";
    private long blockStartTime = 0;

    private static final long BLOCK_STABLE_TIME = 2500; // ms
    private static final float BLOCK_SIMILARITY = 0.8f;
    /*estabilizar texto*/
    private final List<String> textBuffer = new ArrayList<>();
    private String currentStableText = "";
    private String lastCapturedText = "";
    private long stableSince = 0;
    private static final int BUFFER_SIZE = 5;
    private static final float SIMILARITY_THRESHOLD = 0.85f;
    private static final long STABLE_TIME_MS = 2000; // 2 segundos
    private String lastDetectedText = "";
    private String stableText = "";
    private int stableCount = 0;

    private static final int STABILITY_THRESHOLD = 3;
    private List<TrackedText> trackedTexts = new ArrayList<>();
    private static final int HISTORY_SIZE = 5; // frames de historial

    private String candidateText = "";
    private String frozenText = "";

    private int stableFrames = 0;

    private static final int REQUIRED_STABLE_FRAMES = 4;
    /* fin de estabilizar texto */
/*preparar texto para enviar*/
    private String currentText = "";
    private String lastStableText = "";

    private long lastChangeTime = 0;


    private static final int MIN_TEXT_LENGTH = 10;

    private List<String> capturedTexts = new ArrayList<>();
    /*fin de preparacion (variables)*/
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState){

        View view = inflater.inflate(R.layout.fragment_stream, container, false);

        imgStream = view.findViewById(R.id.imgStream);
        txtOCR = view.findViewById(R.id.txtOCR);
        btnStream = view.findViewById(R.id.btnStream);

        btnStream.setOnClickListener(v -> {

            if(!streaming){

                streaming = true;
                startStream();
                btnStream.setText("Detener");

            }else{

                streaming = false;
                btnStream.setText("Iniciar");

            }

        });

        return view;
    }

    private void startStream(){

        new Thread(() -> {

            try{

                URL url = new URL("http://192.168.4.1/stream");

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                InputStream input = conn.getInputStream();

                ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();

                int prev = 0;
                int cur;

                boolean capturing = false;

                while(streaming && (cur = input.read()) != -1){

                    if(!capturing){

                        if(prev == 0xFF && cur == 0xD8){

                            capturing = true;

                            frameBuffer.reset();

                            frameBuffer.write(0xFF);
                            frameBuffer.write(0xD8);
                        }

                    }else{

                        frameBuffer.write(cur);

                        if(prev == 0xFF && cur == 0xD9){

                            byte[] jpeg = frameBuffer.toByteArray();

                            Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);

                            if(bitmap != null){

                                requireActivity().runOnUiThread(() ->
                                        imgStream.setImageBitmap(bitmap)
                                );
                            }
                            processOCR(bitmap);
                            capturing = false;
                        }
                    }

                    prev = cur;
                }

            }catch(Exception e){
                e.printStackTrace();
            }

        }).start();
    }

    private Bitmap drawTextBlocks(Bitmap bitmap, List<Text.TextBlock> blocks){

        Bitmap mutable = bitmap.copy(Bitmap.Config.ARGB_8888,true);

        Canvas canvas = new Canvas(mutable);

        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.STROKE);

        for(Text.TextBlock block : blocks){

            Rect rect = block.getBoundingBox();

            if(rect != null)
                canvas.drawRect(rect,paint);
        }

        return mutable;
    }

    private void processOCR_old(Bitmap bitmap){

        InputImage image = InputImage.fromBitmap(bitmap,0);

        TextRecognizer recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(text -> {

                    trackedTexts.clear(); // limpiar historial anterior

                    Bitmap result = drawTextBlocks(bitmap, text.getTextBlocks());

                    for(Text.TextBlock block : text.getTextBlocks()){
                        updateTrackedText(block);
                    }

                    StringBuilder stableResult = new StringBuilder();

                    for(TrackedText t : trackedTexts){
                        stableResult.append(getStableText(t)).append("\n");
                    }

                    String finalText = stableResult.toString().trim();
                    long now = System.currentTimeMillis();
                    String cleanNew = normalize(finalText);
                    String cleanCurrent = normalize(currentText);

                    float sim = similarity(cleanNew, cleanCurrent);
                    //textoEstable(finalText);

                    if(sim < SIMILARITY_THRESHOLD){

                        // cambio significativo
                        currentText = finalText;
                        lastChangeTime = now;
                        if (isValidText(finalText)) {
                            detectFinalBlock(finalText);
                        }

                    }else{

                        // texto "casi igual" → considerar estable
                        if((now - lastChangeTime) > STABLE_TIME_MS){

                            if(currentText.length() > 10){

                                if(!currentText.equals(lastStableText)){
                                    lastStableText = currentText;
                                    saveCapturedText(currentText);
                                    detectFinalBlock(currentStableText);

                                }
                            }
                        }
                    }

                    requireActivity().runOnUiThread(() -> {

                        imgStream.setImageBitmap(result);

                        if(finalText.isEmpty()){
                            txtOCR.setText("Sin texto detectado");
                        }else{
                            txtOCR.setText(finalText);
                        }

                    });

                })
                .addOnFailureListener(e ->
                        Log.e(TAG,"error",e));
    }
    private void processOCR(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(image)
                .addOnSuccessListener(text -> {
                    // Marcar todos como no actualizados
                    for (TrackedText t : trackedTexts) {
                        t.updated = false;
                    }

                    List<Text.TextBlock> blocks = text.getTextBlocks();
                    for (Text.TextBlock block : blocks) {
                        Rect box = block.getBoundingBox();
                        if (box == null) continue;
                        String blockText = block.getText();

                        boolean found = false;
                        for (TrackedText t : trackedTexts) {
                            if (isSameRegion(t.box, box)) {
                                // Actualizar historial
                                t.history.add(blockText);
                                if (t.history.size() > HISTORY_SIZE)
                                    t.history.remove(0);
                                t.box = box;
                                t.updated = true;
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            TrackedText newText = new TrackedText();
                            newText.box = box;
                            newText.history.add(blockText);
                            newText.updated = true;
                            trackedTexts.add(newText);
                        }
                    }

                    // Eliminar los que no se actualizaron en este frame
                    trackedTexts.removeIf(t -> !t.updated);

                    // Obtener el texto más frecuente de cada bloque
                    StringBuilder stableResult = new StringBuilder();
                    for (TrackedText t : trackedTexts) {
                        String best = getMostFrequent(t.history);
                        if (!best.isEmpty()) {
                            stableResult.append(best).append("\n");
                        }
                    }

                    String finalText = stableResult.toString().trim();

                    // Lógica de captura estable (como se describió arriba)

                    evaluateStability(finalText);
                    checkBlockTimeout();

                    // Actualizar UI con imagen anotada
                    Bitmap annotated = drawTextBlocks(bitmap, blocks);
                    requireActivity().runOnUiThread(() -> {
                        imgStream.setImageBitmap(annotated);
                        txtOCR.setText(finalText.isEmpty() ? "Sin texto detectado" : finalText);
                    });
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error", e));
    }

    private void evaluateStability(String finalText) {
        //Log.d(TAG, "evaluateStability() called with: finalText = [" + finalText + "]");
        long now = System.currentTimeMillis();
        String normalizedNew = normalize(finalText);
        String normalizedCurrent = normalize(currentStableText);
        float sim = similarity(normalizedNew, normalizedCurrent);

        if (sim < SIMILARITY_THRESHOLD) {
            //Log.d(TAG, "evaluateStability: sim < SIMILARITY_THRESHOLD was true");
            currentStableText = finalText;
            stableSince = now;
            if (isValidText(finalText)) {
                detectFinalBlock(finalText);
            }
        } else {
            //Log.d(TAG, "evaluateStability: sim < SIMILARITY_THRESHOLD was false");
            if (now - stableSince >= STABLE_TIME_MS) {
                if (isValidText(currentStableText) && !currentStableText.equals(lastCapturedText)) {
                    //(currentStableText);
                    //processCompleteBlock(currentStableText);
                    lastCapturedText = currentStableText;
                    detectFinalBlock(currentStableText);
                }

            }
        }
    }


    /*ciclo de vida de un bloque de texto*/
    private String activeBlock = "";
    private long lastUpdateTime = 0;
    private boolean blockLocked = false;

    private static final long BLOCK_TIMEOUT = 3000; // ms sin cambios = cerrar bloque
    private static final float CHANGE_THRESHOLD = 0.75f;
    private void detectFinalBlock(String stableText) {
        Log.d(TAG, "detectFinalBlock() called with: stableText = [" + stableText + "]");
        if (stableText == null || stableText.isEmpty()) return;

        long now = System.currentTimeMillis();

        String normalizedNew = normalize(stableText);
        String normalizedActive = normalize(activeBlock);

        float sim = similarity(normalizedNew, normalizedActive);

        // 1. Primer texto
        if (activeBlock.isEmpty()) {
            activeBlock = stableText;
            lastUpdateTime = now;
            blockLocked = false;
            Log.d(TAG, "CASE: FIRST BLOCK");

            return;
        }

        // 2. Texto sigue siendo el mismo (o parecido)
        if (sim > CHANGE_THRESHOLD) {

            // mejorar contenido (por si OCR mejora)
            if (stableText.length() > activeBlock.length()) {
                activeBlock = stableText;
            }
            Log.d(TAG, "CASE: SAME BLOCK");

            lastUpdateTime = now;
            return;
        }

        // 3. Cambio fuerte → posible nuevo bloque
        if (!blockLocked && isGoodBlock(activeBlock)) {
            saveFinalBlock(activeBlock);
            parseBlock(activeBlock);

            blockLocked = true;
        }

        // iniciar nuevo bloque
        activeBlock = stableText;
        lastUpdateTime = now;
        Log.d(TAG, "CASE: NEW BLOCK");
        blockLocked = false;
    }

    private boolean isGoodBlock(String text) {

        if (text.length() < 30) return false;

        int words = text.split(" ").length;

        if (words < 6) return false;

        // evitar basura tipo IPs o strings raros
        if (text.matches(".*\\d{3,}.*")) return false;

        return true;
    }

    private List<String> finalBlocks = new ArrayList<>();
    private List<StructuredBlock> structuredBlocks = new ArrayList<>();

    private void saveFinalBlock_old(String text) {
        Log.d(TAG, "saveFinalBlock() called with: text = [" + text + "]");
        String normalizedNew = normalize(text);

        for (String saved : finalBlocks) {
            float sim = similarity(normalize(saved), normalizedNew);
            if (sim > 0.9f) {
                return; // duplicado real
            }
        }
        finalBlocks.add(text);

        Log.d(TAG, "FINAL BLOCK: GUARDADO FINAL:\n" + text);
    }

    private void saveFinalBlock(String text) {

        String normalizedNew = normalize(text);

        for (StructuredBlock b : structuredBlocks) {
            float sim = similarity(normalize(b.rawText), normalizedNew);
            if (sim > 0.9f) return;
        }

        StructuredBlock parsed = parseBlock(text);

        handleStructuredBlock(parsed);
        //structuredBlocks.add(parsed);
        //mergeQuestionWithOptions();

        Log.d( TAG, "Tipo: " + parsed.type);
    }


    private boolean isValid(StructuredBlock b) {
        return b.question != null &&
                b.question.length() > 15 &&
                b.optionsMap != null &&
                b.optionsMap.size() >= 2;
    }

    utils util = new utils();
    API api = new API();
    private String lastSent = "";
    private void handleStructuredBlock(StructuredBlock block) {
        Log.d(TAG, "handleStructuredBlock() called with: block = [" + block + "]");
        StructuredBlock stable = aggregator.process(block);

        if (stable == null) {
            return; // aún no es confiable
        }

        block = stable; // ← reemplazar por el mejor candidato
        if (block.type == BlockType.OPTIONS) {
            if (!isDuplicate(block)) {
                if (!block.raw.equals(lastSent)) {
                    lastSent = block.raw;
                    detectedBlocks__.add(block);
                    JSONObject json = toJson(block);
                    util.vibrate(getContext());
                    Log.d(TAG, "BLOCK GUARDADO: " + block.question);
                    Log.d(TAG, "JSON: " + json.toString());
                    //api.sendToApi(block.raw); // ← aquí va lo importante
                }else{
                    Log.d(TAG,"Texto ya enviado previamente, no se vuelve a mandar a API.");
                }
            }
            Log.d(TAG, "QUESTION: " + block.question);

            for (Map.Entry<String, String> entry : block.optionsMap.entrySet()) {
                Log.d(TAG, entry.getKey() + ": " + entry.getValue());
            }

            // aquí después:
            // → guardar
            // → enviar API

        } else if (block.type == BlockType.QUESTION) {

            Log.d(TAG, "QUESTION ONLY: " + block.question);

        } else if (block.type == BlockType.PARAGRAPH) {

            Log.d(TAG, "PARAGRAPH: " + block.paragraph);
        }
    }

    private void checkBlockTimeout() {
        //Log.d(TAG, "checkBlockTimeout: cond[activeBlock.isEmpty() || blockLocked]:"+(activeBlock.isEmpty() || blockLocked));
        //Log.d(TAG, "activeBlock: [" + activeBlock + "]");
        //Log.d(TAG, "blockLocked: " + blockLocked);
        if (activeBlock.isEmpty() || blockLocked) return;

        long now = System.currentTimeMillis();
        //Log.d(TAG, "checkBlockTimeout: second cond[(now - lastUpdateTime) > BLOCK_TIMEOUT]:"+((now - lastUpdateTime) > BLOCK_TIMEOUT));
        if ((now - lastUpdateTime) > BLOCK_TIMEOUT) {

            if (isGoodBlock(activeBlock)) {
                saveFinalBlock(activeBlock);
                parseBlock(activeBlock);
                blockLocked = true;
            }
        }
    }
    /*fin ciclos de vida*/

    private void captureText(String text) {
        capturedTexts.add(text);
        Log.d(TAG, "Texto capturado: " + text);
        // Aquí se puede notificar a la UI, guardar en base de datos, etc.
    }

    private String getMostFrequent(List<String> history) {
        Map<String, Integer> freq = new HashMap<>();
        for (String s : history) {
            freq.put(s, freq.getOrDefault(s, 0) + 1);
        }
        String best = "";
        int max = 0;
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            if (e.getValue() > max) {
                best = e.getKey();
                max = e.getValue();
            }
        }
        return best;
    }
    private float similarity(String a, String b){
        //Log.d(TAG, "similarity() called with: a = [" + a + "], b = [" + b + "]");
        if(a.isEmpty() || b.isEmpty()) return 0f;

        int matches = 0;
        int minLength = Math.min(a.length(), b.length());

        for(int i = 0; i < minLength; i++){
            if(a.charAt(i) == b.charAt(i)){
                matches++;
            }
        }

        return (float) matches / Math.max(a.length(), b.length());
    }
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
    private void textoEstable(String finalText) {
        long now = System.currentTimeMillis();

        if(!finalText.equals(currentText)){

            currentText = finalText;
            lastChangeTime = now;

        }else{

            // si no cambia por cierto tiempo → texto estable
            if((now - lastChangeTime) > STABLE_TIME_MS){

                if(currentText.length() >= MIN_TEXT_LENGTH){

                    // evitar duplicados
                    if(!currentText.equals(lastStableText)){

                        lastStableText = currentText;

                        saveCapturedText(currentText);
                    }
                }
            }
        }
    }
    private void saveCapturedText(String text){
        Log.d(TAG, "saveCapturedText() called with: text = [" + text + "]");
        capturedTexts.add(text);

        Log.d(TAG,"Texto guardado: " + text);
    }
    private String stabilizeText(String detected){

        if(detected == null || detected.trim().isEmpty())
            return stableText;

        detected = detected.trim();

        if(detected.equals(lastDetectedText)){

            stableCount++;

        }else{

            stableCount = 0;
        }

        lastDetectedText = detected;

        if(stableCount >= STABILITY_THRESHOLD){

            stableText = detected;
        }

        return stableText;
    }
    private boolean isSameRegion(Rect a, Rect b){

        int dx = Math.abs(a.centerX() - b.centerX());
        int dy = Math.abs(a.centerY() - b.centerY());
        Log.d(TAG, "isSameRegion: dx:["+dx+"] 40 dy:["+dy+"] COND:["+(dx < 40 && dy < 40)+"]");
        return dx < 40 && dy < 40;
    }
    private void updateTrackedText(Text.TextBlock block){

        Rect box = block.getBoundingBox();
        String text = block.getText();

        if(box == null) return;

        for(TrackedText t : trackedTexts){

            if(isSameRegion(t.box, box)){

                t.history.add(text);

                if(t.history.size() > 5)
                    t.history.remove(0);

                t.box = box;

                return;
            }
        }
        TrackedText newText = new TrackedText();
        newText.box = box;
        newText.history.add(text);

        trackedTexts.add(newText);
    }
    private String getStableText(TrackedText t){

        Map<String,Integer> freq = new HashMap<>();

        for(String s : t.history){

            freq.put(s, freq.getOrDefault(s,0)+1);

        }

        String best = "";
        int max = 0;

        for(Map.Entry<String,Integer> e : freq.entrySet()){

            if(e.getValue() > max){

                best = e.getKey();
                max = e.getValue();
            }
        }

        return best;
    }
    class TrackedText {
        Rect box;
        List<String> history = new ArrayList<>();
        boolean updated = false; // para saber si se vio en este frame
    }
    private String freezeStableText(String detected){

        if(detected == null || detected.isEmpty())
            return frozenText;

        detected = detected.trim();

        if(detected.equals(candidateText)){

            stableFrames++;

        }else{

            candidateText = detected;
            stableFrames = 1;
        }

        if(stableFrames >= REQUIRED_STABLE_FRAMES){

            frozenText = candidateText;
        }

        return frozenText;
    }
    /*
    * Funciones de tipo de bloque
    * */
    private BlockType classifyText(String text) {

        text = text.trim();

        // Pregunta
        if (text.contains("?") || text.matches("^(¿).*")) {
            return BlockType.QUESTION;
        }

        // Opciones tipo A) B) C)
        if (text.matches("(?i).*(a\\)|b\\)|c\\)|d\\)).*")) {
            return BlockType.OPTIONS;
        }

        // Párrafo
        if (text.length() > 40 && text.split(" ").length > 8) {
            return BlockType.PARAGRAPH;
        }

        return BlockType.UNKNOWN;
    }
    private String cleanOCRText(String text) {

        return text
                .replaceAll("[|]", "I")
                .replaceAll("0", "O")
                .replaceAll("1", "I")
                .replaceAll("\\s+", " ")
                .trim();
    }
    private void processCompleteBlock(String stableText) {

        if (stableText == null || stableText.isEmpty()) return;

        String cleaned = cleanOCRText(stableText);

        long now = System.currentTimeMillis();

        if (currentBlockBuffer.isEmpty()) {
            currentBlockBuffer = cleaned;
            blockStartTime = now;
            return;
        }

        float sim = similarity(normalize(cleaned), normalize(currentBlockBuffer));

        if (sim > BLOCK_SIMILARITY) {
            // sigue siendo el mismo bloque → acumular
            currentBlockBuffer = mergeText(currentBlockBuffer, cleaned);
        } else {
            // cambio fuerte → posible nuevo bloque
            if (isBlockReady(currentBlockBuffer, now)) {
                saveBlock(currentBlockBuffer);
            }

            currentBlockBuffer = cleaned;
            blockStartTime = now;
        }

        // Si permanece estable suficiente tiempo → cerrar bloque
        if (isBlockReady(currentBlockBuffer, now)) {
            saveBlock(currentBlockBuffer);
            currentBlockBuffer = "";
        }
    }
    private boolean isBlockReady(String text, long now) {

        if (text.length() < 20) return false;

        boolean timeOk = (now - blockStartTime) > BLOCK_STABLE_TIME;

        BlockType type = classifyText(text);

        boolean structureOk = false;

        switch (type) {
            case QUESTION:
                structureOk = text.contains("?") && text.length() > 20;
                break;

            case OPTIONS:
                structureOk = text.contains("A)") && text.contains("B)");
                break;

            case PARAGRAPH:
                structureOk = text.split(" ").length > 10;
                break;
        }

        return timeOk && structureOk;
    }
    private String mergeText(String oldText, String newText) {

        if (newText.length() > oldText.length()) {
            return newText;
        }

        return oldText;
    }
    private void saveBlock(String text) {

        String normalizedNew = normalize(text);

        for (TextBlockData b : detectedBlocks) {
            float sim = similarity(normalize(b.content), normalizedNew);
            if (sim > 0.9f) {
                return; // duplicado
            }
        }

        BlockType type = classifyText(text);

        TextBlockData block = new TextBlockData(type, text);
        detectedBlocks.add(block);

        Log.d(TAG, "Guardado: " + type + " -> " + text);
    }
    private void postProcessBlocks() {

        for (int i = 0; i < detectedBlocks.size() - 1; i++) {

            TextBlockData current = detectedBlocks.get(i);
            TextBlockData next = detectedBlocks.get(i + 1);

            if (current.type == BlockType.QUESTION &&
                    next.type == BlockType.OPTIONS) {

                current.content = current.content + "\n" + next.content;
                detectedBlocks.remove(i + 1);
                i--;
            }
        }
    }
    private StructuredBlock parseBlock(String text) {

        StructuredBlock block = new StructuredBlock(text);

        String cleaned = cleanOCRText(text);
        block.raw = text;
        block.cleaned = cleaned;
        boolean hasOptions = false;

        String[] rawLines = cleaned.split("\n");
        List<String> lines = new ArrayList<>();

        // Normalizar líneas sin destruir texto (manejo de líneas con múltiples opciones)
        for (String line : rawLines) {

            String[] split = line.split("(?i)(?=[a-d][\\)\\.\\-:]\\s)");

            for (String s : split) {
                if (!s.trim().isEmpty()) {
                    lines.add(s.trim());
                }
            }
        }

        // Detectar si hay opciones
        for (String l : lines) {
            if (l.matches("(?i)^[a-z][\\)\\.\\-:]\\s+.*")) {
                hasOptions = true;
                break;
            }
        }

        if (hasOptions) {

            block.type = BlockType.OPTIONS;

            for (String l : lines) {

                if (l.matches("(?i)^[a-z][\\)\\.\\-:]\\s+.*")) {

                    String key = l.substring(0, 1).toUpperCase();

                    String value = l
                            .replaceFirst("(?i)^[a-z][\\)\\.\\-:]\\s*", "")
                            .trim();

                    // Filtrar ruido dentro de opciones
                    if (!isNoise(value) && value.length() > 4) {
                        if (!block.optionsMap.containsKey(key)) {
                            block.optionsMap.put(key, value);
                        }
                    }

                } else if (!l.isEmpty() && !isNoise(l) && !isUI(l)) {

                    // Evitar meter opciones como texto de pregunta
                    if (l.length() > 5 && !l.matches("(?i)^[a-z][\\)\\.\\-:].*")) {
                        block.question = (block.question == null ? "" : block.question + " ") + l;
                    }
                }
            }

            // Ordenar opciones (A, B, C, D)
            if (!block.optionsMap.isEmpty()) {
                Map<String, String> sorted = new TreeMap<>(block.optionsMap);
                block.optionsMap = new LinkedHashMap<>(sorted);
            }

            // Fallback si no se logró construir la pregunta
            if (block.question == null || block.question.trim().isEmpty()) {

                Matcher matcher = Pattern.compile("(?i)[a-z][\\)\\.\\-:]").matcher(cleaned);

                if (matcher.find()) {
                    int index = matcher.start();
                    block.question = cleaned.substring(0, index).trim();
                }
            }

            return block;
        }

        // Detectar pregunta simple
        if (cleaned.contains("?")) {
            block.type = BlockType.QUESTION;
            block.question = cleaned;
            return block;
        }

        // Párrafo
        block.type = BlockType.PARAGRAPH;
        block.paragraph = cleaned;

        return block;
    }

    private boolean isNoise(String line) {
        return line.matches("(?i).*(www|http|\\.com|sesion|login|herramientas|inicio).*");
    }
    private boolean isUI(String text) {
        String t = text.toLowerCase();
        return t.contains("revelar") ||
                t.contains("respuesta") ||
                t.contains("copiar") ||
                t.contains("herramient") ||
                t.contains("examen");
    }
    private void mergeQuestionWithOptions() {
        Log.d(TAG, "mergeQuestionWithOptions() called");
        for (int i = 0; i < structuredBlocks.size() - 1; i++) {

            StructuredBlock current = structuredBlocks.get(i);
            StructuredBlock next = structuredBlocks.get(i + 1);

            if (current.type == BlockType.QUESTION &&
                    next.type == BlockType.OPTIONS) {

                current.options.addAll(next.options);

                structuredBlocks.remove(i + 1);
                i--;
            }
        }
    }
    private void mergeQuestionWithOptions(String question, Map<String, String> options) {

        Log.d(TAG, "QUESTION: " + question);

        for (Map.Entry<String, String> entry : options.entrySet()) {
            Log.d(TAG, entry.getKey() + ": " + entry.getValue());
        }

        // aquí después irá:
        // → guardar en lista
        // → enviar a API
    }
    private String cleanOCRTextAdvanced(String text) {

        return text
                .replaceAll("0", "O")
                .replaceAll("1", "I")
                .replaceAll("l", "I")
                .replaceAll("ﬁ", "fi")
                .replaceAll("[^\\x00-\\x7FáéíóúñÁÉÍÓÚÑ()\\n ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private final List<StructuredBlock> detectedBlocks__ = new ArrayList<>();
    private boolean isDuplicate(StructuredBlock newBlock) {

        for (StructuredBlock b : detectedBlocks__) {

            if (b.type == newBlock.type) {

                if (b.question != null && newBlock.question != null) {

                    float sim = similarity(
                            normalize(b.question),
                            normalize(newBlock.question)
                    );

                    if (sim > 0.85f) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
    private JSONObject toJson(StructuredBlock block) {

        JSONObject json = new JSONObject();

        try {
            json.put("question", block.question);

            JSONObject options = new JSONObject();

            for (Map.Entry<String, String> entry : block.optionsMap.entrySet()) {
                options.put(entry.getKey(), entry.getValue());
            }

            json.put("options", options);
            json.put("raw",block.raw);
            json.put("raw_cleaned",block.cleaned);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return json;
    }
}