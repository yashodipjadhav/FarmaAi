package com.example.banana_desies_detection;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// ✅ OKHTTP Imports
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class DashboardActivity extends AppCompatActivity {

    private static final int IMAGE_PICK_CODE = 1000;
    private static final int CAMERA_REQUEST_CODE = 1001;
    private static final int PERMISSION_CODE = 1002;

    ImageView imageView;
    TextView resultText;
    Bitmap selectedImage;

    Interpreter interpreter;
    ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final String MODEL_NAME = "disease_model.tflite";
    private final String LABEL_FILE = "labels.txt";

    private int IMAGE_SIZE = 128;
    private int IMAGE_CHANNELS = 3;
    private DataType inputDataType = DataType.FLOAT32;

    private List<String> LABELS = new ArrayList<>();

    // 🔑 Replace with YOUR Gemini API key
    private final String GEMINI_API_KEY = "AIzaSyBRyP-LvnhD3mqvhfr4f4wEKPo-Xgjo3F8";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        imageView = findViewById(R.id.previewImage);
        resultText = findViewById(R.id.resultText);
        Button selectBtn = findViewById(R.id.uploadPhotoBtn);
        Button cameraBtn = findViewById(R.id.takePhotoBtn);

        loadLabelFile();

        try {
            interpreter = new Interpreter(loadModelFile());
            Tensor inTensor = interpreter.getInputTensor(0);
            int[] shape = inTensor.shape();

            inputDataType = inTensor.dataType();
            IMAGE_SIZE = shape[1];
            IMAGE_CHANNELS = shape[3];

        } catch (Exception e) {
            Toast.makeText(this, "Model Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        selectBtn.setOnClickListener(v -> pickImageFromGallery());
        cameraBtn.setOnClickListener(v -> checkCameraPermission());
    }


    private MappedByteBuffer loadModelFile() throws IOException {
        FileInputStream inputStream =
                new FileInputStream(getAssets().openFd(MODEL_NAME).getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = getAssets().openFd(MODEL_NAME).getStartOffset();
        long declaredLength = getAssets().openFd(MODEL_NAME).getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }


    private void loadLabelFile() {
        LABELS.clear();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(getAssets().open(LABEL_FILE))
            );
            String line;
            while ((line = reader.readLine()) != null)
                LABELS.add(line.trim());
            reader.close();
        } catch (Exception e) {
            Toast.makeText(this, "labels.txt missing!", Toast.LENGTH_LONG).show();
        }
    }


    private void pickImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, IMAGE_PICK_CODE);
    }


    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    PERMISSION_CODE
            );
        } else openCamera();
    }


    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {

            if (requestCode == IMAGE_PICK_CODE) {
                Uri uri = data.getData();
                try {
                    selectedImage = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                    imageView.setImageBitmap(selectedImage);
                    runInference(selectedImage);
                } catch (IOException e) { e.printStackTrace(); }

            } else if (requestCode == CAMERA_REQUEST_CODE) {
                selectedImage = (Bitmap) data.getExtras().get("data");
                imageView.setImageBitmap(selectedImage);
                runInference(selectedImage);
            }
        }
    }


    // ✅ FIXED: bitmap is effectively final
    private void runInference(Bitmap bitmap) {

        final Bitmap inputBitmap = bitmap;

        executorService.execute(() -> {

            Bitmap resized = Bitmap.createScaledBitmap(inputBitmap, IMAGE_SIZE, IMAGE_SIZE, true);

            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * IMAGE_SIZE * IMAGE_SIZE * IMAGE_CHANNELS);
            inputBuffer.order(ByteOrder.nativeOrder());

            int[] pixels = new int[IMAGE_SIZE * IMAGE_SIZE];
            resized.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE);

            int pixelIndex = 0;
            for (int i = 0; i < pixels.length; i++) {
                int val = pixels[pixelIndex++];
                inputBuffer.putFloat(((val >> 16) & 0xFF) / 255f);
                inputBuffer.putFloat(((val >> 8) & 0xFF) / 255f);
                inputBuffer.putFloat((val & 0xFF) / 255f);
            }

            float[][] output = new float[1][LABELS.size()];
            interpreter.run(inputBuffer, output);

            presentResult(output);
        });
    }


    // ✅ FIXED: predictedDisease and confidence made final
    private void presentResult(float[][] output) {

        int maxIndex = 0;
        float maxConfidence = 0;

        for (int i = 0; i < output[0].length; i++) {
            if (output[0][i] > maxConfidence) {
                maxConfidence = output[0][i];
                maxIndex = i;
            }
        }

        final String finalDisease = LABELS.get(maxIndex);
        final float confidence = maxConfidence;

        runOnUiThread(() -> resultText.setText(
                finalDisease + "\nConfidence: " +
                        new DecimalFormat("0.00").format(confidence * 100) + "%"
        ));

        callGeminiForSolution(finalDisease);
    }


    private void callGeminiForSolution(final String diseaseName) {
        executorService.execute(() -> {
            try {
                OkHttpClient client = new OkHttpClient();

                // 👇 Marathi + concise, pesticide-focused prompt
                String prompt =
                        "तू एक अनुभवी कृषी तज्ञ आहेस. केळीच्या '" + diseaseName + "' या रोगासाठी " +
                                "खालील माहिती मराठीत दे:\n" +
                                "1️⃣ योग्य कीटकनाशक/बुरशीनाशकांची नावे आणि प्रमाण (मि.ली./ग्रॅम प्रति लिटर पाणी)\n" +
                                "2️⃣ फवारणीची वेळ आणि वारंवारता (एका वाक्यात)\n" +
                                "3️⃣ प्रतिबंधक उपाय (२ मुद्दे)\n" +
                                "उत्तर ७० शब्दांच्या आत आणि स्पष्ट मराठीत लिहा.";

                String jsonBody = "{\n" +
                        "  \"contents\": [{\n" +
                        "    \"role\": \"user\",\n" +
                        "    \"parts\": [{\"text\": \"" + prompt.replace("\"", "\\\"") + "\"}]\n" +
                        "  }]\n" +
                        "}";

                RequestBody body = RequestBody.create(
                        jsonBody,
                        MediaType.parse("application/json")
                );

                // ✅ Keep your working endpoint
                Request request = new Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + GEMINI_API_KEY)
                        .post(body)
                        .build();

                Response response = client.newCall(request).execute();
                String reply = response.body().string();

                String solution = "No response";
                try {
                    org.json.JSONObject json = new org.json.JSONObject(reply);

                    if (json.has("candidates")) {
                        org.json.JSONArray candidates = json.getJSONArray("candidates");
                        if (candidates.length() > 0) {
                            org.json.JSONObject firstCandidate = candidates.getJSONObject(0);
                            if (firstCandidate.has("content")) {
                                org.json.JSONObject content = firstCandidate.getJSONObject("content");
                                if (content.has("parts")) {
                                    org.json.JSONArray parts = content.getJSONArray("parts");
                                    if (parts.length() > 0 && parts.getJSONObject(0).has("text")) {
                                        solution = parts.getJSONObject(0).getString("text");
                                    }
                                }
                            }
                        }
                    } else if (json.has("error")) {
                        solution = "Error: " + json.getJSONObject("error").toString();
                    } else {
                        solution = reply;
                    }
                } catch (Exception parseErr) {
                    solution = "Parsing error: " + parseErr.getMessage() + "\nRaw:\n" + reply;
                }

                final String finalSolution = solution;

                runOnUiThread(() -> {
                    resultText.append("\n\n🌿 उपाय (मराठीत):\n" + finalSolution);
                });

            } catch (Exception e) {
                runOnUiThread(() -> resultText.append("\n\n⚠️ LLM Error: " + e.getMessage()));
            }
        });
    }


    @Override
    protected void onDestroy() {
        if (interpreter != null) interpreter.close();
        executorService.shutdownNow();
        super.onDestroy();
    }
}
