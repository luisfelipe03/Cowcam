package com.coworking.cowcam;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class CamActivity extends AppCompatActivity {
    ImageButton capture, toggleFlash, flipCamera;
    private PreviewView previewView;
    int cameraFacing = CameraSelector.LENS_FACING_BACK;

    private SensorManager mSensorManager;

    private SensorEventListenerImpl sensorEventListenerImpl;
    private Sensor sensor;
    private final ActivityResultLauncher<String> activityResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
        @Override
        public void onActivityResult(Boolean result) {
            if (result) {
                startCamera(cameraFacing);
            }
        }
    });
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cam);

        previewView = findViewById(R.id.cameraPreview);
        capture = findViewById(R.id.capture);
        toggleFlash = findViewById(R.id.toggleFlash);
        flipCamera = findViewById(R.id.flipCamera);
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        sensorEventListenerImpl = new SensorEventListenerImpl();
        mSensorManager.registerListener(sensorEventListenerImpl, sensor,  9_999_999);

        if (ContextCompat.checkSelfPermission(CamActivity.this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(android.Manifest.permission.CAMERA);
        } else {
            startCamera(cameraFacing);
        }

        flipCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
                    cameraFacing = CameraSelector.LENS_FACING_FRONT;
                } else {
                    cameraFacing = CameraSelector.LENS_FACING_BACK;
                }
                startCamera(cameraFacing);
            }
        });
    }

    public void startCamera(int cameraFacing) {
        int aspectRatio = aspectRatio(previewView.getWidth(), previewView.getHeight());
        ListenableFuture<ProcessCameraProvider> listenableFuture = ProcessCameraProvider.getInstance(this);


        listenableFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = (ProcessCameraProvider) listenableFuture.get();

                Preview preview = new Preview.Builder().setTargetAspectRatio(aspectRatio).build();

                ImageCapture imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation()).build();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(cameraFacing).build();

                cameraProvider.unbindAll();

                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

                capture.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (ContextCompat.checkSelfPermission(CamActivity.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            activityResultLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                        }
                        takePicture(imageCapture);
                    }
                });

                toggleFlash.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        setFlashIcon(camera);
                    }
                });

                preview.setSurfaceProvider(previewView.getSurfaceProvider());
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    public void takePicture(ImageCapture imageCapture) {
        final File refFile = new File("/storage/emulated/0/DCIM/Cowcam/");
        refFile.mkdir();
        final File file = new File(refFile, System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
        imageCapture.takePicture(outputFileOptions, Executors.newCachedThreadPool(), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(CamActivity.this, "Image saved at: " + file.getPath(), Toast.LENGTH_SHORT).show();
                    }
                });
                startCamera(cameraFacing);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(CamActivity.this, "Failed to save: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
                startCamera(cameraFacing);
            }
        });
    }

    private void setFlashIcon(Camera camera) {
        if (camera.getCameraInfo().hasFlashUnit()) {
            if (camera.getCameraInfo().getTorchState().getValue() == 0) {
                camera.getCameraControl().enableTorch(true);
                toggleFlash.setImageResource(R.drawable.baseline_flash_off_24);
            } else {
                camera.getCameraControl().enableTorch(false);
                toggleFlash.setImageResource(R.drawable.baseline_flash_on_24);
            }
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(CamActivity.this, "Flash is not available currently", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private int aspectRatio(int width, int height) {
        double previewRatio = (double) Math.max(width, height) / Math.min(width, height);
        if (Math.abs(previewRatio - 4.0 / 3.0) <= Math.abs(previewRatio - 16.0 / 9.0)) {
            return AspectRatio.RATIO_4_3;
        }
        return AspectRatio.RATIO_16_9;
    }

    public class SensorEventListenerImpl implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                float[] mRotationMatrix = new float[9];
                SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);

                float[] orientationValues = new float[3];
                SensorManager.getOrientation(mRotationMatrix, orientationValues);

                float X = (float) Math.toDegrees(orientationValues[1]);
                float Y = (float) Math.toDegrees(orientationValues[2]);
                float Z = (float) Math.toDegrees(orientationValues[0]);

                if ((Math.abs(X + 90) < 10) && (Math.abs(Y) < 25)) {
                    updateOverlayImage(R.drawable.mask_green);
                } else {
                    updateOverlayImage(R.drawable.mask_red);
                }

                Log.i("", "Inclinação Eixo X ---------" + String.valueOf(X));
                Log.i("","Inclinação Eixo Y ----------" + String.valueOf(Y));
                Log.i("", "Azimute -------------------" + String.valueOf(Z));


            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }

    private void updateOverlayImage(int drawableResId) {
        ImageView overlayImage = findViewById(R.id.imageMask);
        overlayImage.setImageResource(drawableResId);
    }
}
