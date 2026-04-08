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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class CaptureFragment extends Fragment {

    private ImageView imgCapture;
    private Button btnCapture;
    private TextView txtOCR;
    public CaptureFragment(){}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState){

        View view = inflater.inflate(R.layout.fragment_capture, container, false);

        imgCapture = view.findViewById(R.id.imgCapture);
        btnCapture = view.findViewById(R.id.btnCapture);
        txtOCR = view.findViewById(R.id.txtOCR);
        btnCapture.setOnClickListener(v -> captureImage());

        return view;
    }

    private void captureImage(){

        new Thread(() -> {

            try{

                URL url = new URL("http://192.168.4.1/capture");

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                InputStream input = conn.getInputStream();

                Bitmap bitmap = BitmapFactory.decodeStream(input);

                if(bitmap != null){

                    requireActivity().runOnUiThread(() ->
                            imgCapture.setImageBitmap(bitmap)
                    );
                    processOCR(bitmap);
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

    private void processOCR(Bitmap bitmap){

        InputImage image = InputImage.fromBitmap(bitmap,0);

        TextRecognizer recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(text -> {
                    String detectedText = text.getText();
                    Bitmap result = drawTextBlocks(bitmap,text.getTextBlocks());

                    requireActivity().runOnUiThread(() ->
                            imgCapture.setImageBitmap(result)
                    );
                    if(detectedText.isEmpty())
                        txtOCR.setText("Sin texto detectado");
                    else
                        txtOCR.setText(detectedText);
                })
                .addOnFailureListener(e ->
                        Log.e("OCR","error",e));
    }
}