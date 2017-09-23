package com.pradeep.calibration.managers;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
//import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.List;

import io.rpng.calibration.R;
import com.pradeep.calibration.activities.MainActivity;
import com.pradeep.calibration.dialogs.ErrorDialog;
import com.pradeep.calibration.utils.CameraUtil;
import com.pradeep.calibration.views.AutoFitTextureView;
import android.hardware.camera2.CameraCaptureSession;

import static android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_OFF;


/*******************************************************************************************************
 * manage all the camera settings
 */
public class CameraManager {

    // Our permissions we need to function
    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
    };

    private static final String TAG = "CameraManager";
    private static final String FRAGMENT_DIALOG = "dialog";
    private Activity activity;
    private PermissionManager permissionManager;
    private TextureView mTextureView;
    private ImageView camera2View;

    private ImageReader mImageReader;
    private CameraDevice mCameraDevice;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private Size mPreviewSize;
    private Size mVideoSize;
    private Integer mSensorOrientation;

    private CaptureRequest.Builder mPreviewBuilder;
    private static CameraCaptureSession mPreviewSession;

    private float[] intrinsic = new float[5];
    private float[] distortion = new float[4];
    public static Range<Integer>[] fpsRanges;

    //private CaptureRequest.Builder mPreviewBuilder;
    List<CaptureRequest> mPreviewBuilder2;
    private CameraConstrainedHighSpeedCaptureSession mPreviewSession2;


    /**
     * A reference to the current {@link android.hardware.camera2.CameraCaptureSession} for
     * preview.
     */
    //private CameraCaptureSession mPreviewSession;


    /**
     * Default constructor
     * Sets our activity, and texture view we should be updating
     */
    public CameraManager(Activity act, TextureView txt, ImageView camera2View) {
        this.activity = act;
        this.mTextureView = txt;
        this.camera2View = camera2View;
        this.permissionManager = new PermissionManager(activity, VIDEO_PERMISSIONS);
    }

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a {@link TextureView}.
     */
    public final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            // TODO: Handle screen resizing/rotations
            //configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
            //mCameraOpenCloseLock.release();
            //if (null != mTextureView) {
            //    configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            //}
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            //mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            //mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

    };

    /**
     * Tries to open a {@link CameraDevice}.
     * The result is listened by `mStateCallback`.
     * We should already have permissions, so just try to open it
     */
    public void openCamera(int width, int height) {

        // Make sure we have permissions
        if(permissionManager.handle_permissions())
            return;

        // Note this is bad naming on my part...
        // There is an android class with the same name as this class
        // Thus we import this explicit instead of an import statement
        android.hardware.camera2.CameraManager manager = (android.hardware.camera2.CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            //Log.d(TAG, "tryAcquire");
            //if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
            //    throw new RuntimeException("Time out waiting to lock camera opening.");
            //}
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
            String cameraId = sharedPreferences.getString("prefCamera", "0");

            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);







            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            mVideoSize = CameraUtil.chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            mPreviewSize = CameraUtil.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, mVideoSize);

            // If we can get the intrinsics, great!
            // https://developer.android.com/reference/android/hardware/camera2/CaptureResult.html#LENS_INTRINSIC_CALIBRATION
            // https://developer.android.com/reference/android/hardware/camera2/CaptureResult.html#LENS_RADIAL_DISTORTION
            if(android.os.Build.VERSION.SDK_INT >= 23) {
                intrinsic = characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION);
                distortion = characteristics.get(CameraCharacteristics.LENS_RADIAL_DISTORTION);
            }

            int orientation = activity.getResources().getConfiguration().orientation;
            //if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
             //  mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            //} else {
            //    mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            //}

            // Get image size from prefs
            String imageSize = "640x480";
            int widthRaw = 640;// Integer.parseInt(imageSize.substring(0,imageSize.lastIndexOf("x")));
            int heightRaw =480;// Integer.parseInt(imageSize.substring(imageSize.lastIndexOf("x")+1));

            // Create the image reader which will be called back to, to get image frames
            mImageReader = ImageReader.newInstance(widthRaw, heightRaw, ImageFormat.YUV_420_888, 3);
            mImageReader.setOnImageAvailableListener(MainActivity.imageAvailableListener, null);


            // The orientation is a multiple of 90 value that rotates into the native orientation
            // This should fix cameras that are rotated in landscape
            camera2View.setRotation(mSensorOrientation);

            // Finally actually open the camera
            manager.openCamera(cameraId, mStateCallback, null);


        } catch (CameraAccessException e) {
            e.printStackTrace();
            Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            activity.finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the device this code runs.
            ErrorDialog.newInstance(activity.getString(R.string.camera_error)).show(activity.getFragmentManager(), FRAGMENT_DIALOG);
        } catch (SecurityException e) {
            e.printStackTrace();
            Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            activity.finish();
        }
        //catch (InterruptedException e) {
        //    throw new RuntimeException("Interrupted while trying to lock camera opening.");
        //}
    }


    private void startPreview() {
        if (mCameraDevice == null ||!mTextureView.isAvailable() || mPreviewSize == null || mImageReader == null) {
            return;
        }
        try {
            //setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            //List<Surface> surfaces = new ArrayList<Surface>();





            // Set control elements, we want auto exposure and white balance
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            //mPreviewBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,Long.valueOf(exposure_time));


            mPreviewBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CONTROL_AF_MODE_OFF);

            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(30,240));//(fpsRanges[0]));//
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
             //int a = 5;
           // mPreviewBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, Key<int>5);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);

            mPreviewBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 100);

           // mPreviewBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, (inf));
           // mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CONTROL_AF_MODE_OFF);

            //captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, _wb_value);

// https://stackoverflow.com/questions/29265126/android-camera2-capture-burst-is-too-slow
            mPreviewBuilder.set(CaptureRequest.EDGE_MODE,CaptureRequest.EDGE_MODE_OFF);
            mPreviewBuilder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF);
            mPreviewBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);


            // Get the focal length
            //SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
            String focus = "0.0";// sharedPreferences.getString("prefFocusLength", "5.0");
            mPreviewBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, Float.parseFloat(focus));



            // Create the surface we want to render to (this preview surface is required)
            List<Surface> surfaces = new ArrayList<Surface>();

           // List surfaces = new ArrayList<>();
            //Surface previewSurface = new Surface(texture);
            //surfaces.add(previewSurface);
            //mPreviewBuilder.addTarget(previewSurface);
            Surface readerSurface = mImageReader.getSurface();
            surfaces.add(readerSurface);
            mPreviewBuilder.addTarget(readerSurface);

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //Activity activity = getActivity();
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        /*
        try{
            //CameraCaptureSession cameraCaptureSession;
            List<CaptureRequest> captureRequests = mPreviewSession.createHighSpeedRequestList(mPreviewBuilder.build());
            cameraCaptureSession.setRepeatingBurst(captureRequests, new CameraCaptureSession.CaptureCallback(){
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result){
                    super.onCaptureCompleted(session, request, result);
                    Log.i("Completed","fps : "+ result.getFrameNumber());
                }
            },mBackgroundHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
        */
      /*  mPreviewBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);
        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CONTROL_AF_MODE_OFF);

        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(30,240));//(fpsRanges[0]));//
        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
        mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
        //int a = 5;
        // mPreviewBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, Key<int>5);
        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);

        mPreviewBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 100);

        // mPreviewBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, (inf));
        // mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CONTROL_AF_MODE_OFF);

        //captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, _wb_value);

// https://stackoverflow.com/questions/29265126/android-camera2-capture-burst-is-too-slow
        mPreviewBuilder.set(CaptureRequest.EDGE_MODE,CaptureRequest.EDGE_MODE_OFF);
        mPreviewBuilder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF);
        mPreviewBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
           */


        try {
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
           // mPreviewSession.setRepeatingBurst(mPreviewBuilder, null, mBackgroundHandler);
            setUpCaptureRequestBuilder(mPreviewBuilder);
            //List<CaptureRequest> mPreviewBuilderBurst = mPreviewSessionHighSpeed.createHighSpeedRequestList(mPreviewBuilder.build());
            //mPreviewSessionHighSpeed.setRepeatingBurst(mPreviewBuilderBurst, null, mBackgroundHandler);

            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            //mPreviewBuilder2 =  mPreviewSession2.createHighSpeedRequestList(mPreviewBuilder.build());
            //mPreviewSession.setRepeatingBurst(mPreviewBuilder2, null, mBackgroundHandler);


            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        Range<Integer> fpsRange = Range.create(120, 120);
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);

    }

    public void closeCamera() {
        //try {
        //mCameraOpenCloseLock.acquire();

        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }

        //}
        //catch (InterruptedException e) {
        //    throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        //} finally {
        //    mCameraOpenCloseLock.release();
        //}
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    public void stopBackgroundThread() {
        try {
            mBackgroundThread.quitSafely();
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public float[] getIntrinsic() {
        if(intrinsic != null)
            return intrinsic;
        return new float[5];
    }

    public float[] getDistortion() {
        if(distortion != null)
            return distortion;
        return new float[4];
    }

}
