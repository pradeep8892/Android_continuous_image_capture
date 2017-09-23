package com.pradeep.calibration.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

//import com.pradeep.calibration.managers.CameraCalibrator;
import com.pradeep.calibration.managers.CameraManager;
import io.rpng.calibration.R;
import com.pradeep.calibration.utils.ImageUtils;
import com.pradeep.calibration.views.AutoFitTextureView;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import android.hardware.SensorEventListener;
import android.widget.Toast;

/*************************************************************************************************************
 * this is the main class to capture the images
 */

public class MainActivity extends AppCompatActivity {

    private static String TAG = "MainActivity";
    //private static final int RESULT_SETTINGS = 1;
    //private static final int RESULT_RESULT = 2;

    private static Intent intentSettings;
    //private static Intent intentResults;

    private static ImageView camera2View;
    private static TextView camera2Captured;
    private TextureView mTextureView;

    public static CameraManager mCameraManager;
    //public static CameraCalibrator mCameraCalibrator;

    private static SharedPreferences sharedPreferences;
    public static File file_frame;
    public static File acc_sensor;
    public static FileWriter writer;
    private Sensor Acc_sensor;
    private SensorManager Sensor_manager;

    static Date date;
    private ImageButton takePictureButton;
    protected static Boolean _recording = false;
    double _gyro_head = 0.;
    double _gyro_pitch = 0.;
    double _gyro_roll = 0.;

    static double _accel_x = 0.;
    static double _accel_y = 0.;
    static double _accel_z = 0.;
   static long MillisecondTime, StartTime, TimeBuff, UpdateTime = 0L ;

    //Handler handler;

   static int Seconds, Minutes, MilliSeconds ;
  //  ListView listView ;

  //  String[] ListElements = new String[] {  };

    //List<String> ListElementsArrayList ;

    //ArrayAdapter<String> adapter ;
static     TextView stopwatch ;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
       date = new Date();

        // Check to see if opencv is enabled
        if (!OpenCVLoader.initDebug()) {
            Log.e(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), not working.");
        } else {
            Log.d(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), working.");
        }
      //  handler = new Handler() ;
      //  ListElementsArrayList = new ArrayList<String>(Arrays.asList(ListElements));

      //  adapter = new ArrayAdapter<String>(MainActivity.this,
        //        android.R.layout.simple_list_item_1,
          //      ListElementsArrayList);

        // Pass to super
        super.onCreate(savedInstanceState);


/*************************************************************************************************
//get the sensor service to access the IMU

 */

        SensorManager sm = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        sm.registerListener(sel,
                sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_UI);
        sm.registerListener(sel,
                sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI);

        // Create our layout
        setContentView(R.layout.activity_main);

       // this.addButtonListeners();


/***************************************************************************************************
 * create the SOlar labs directory and Acceleration file in internal storage
 *
 *
 */
             String folder_main = "SolarLabs";
        File f1 = new File(Environment.getExternalStorageDirectory() + "/" + folder_main, "rgb");
        if (!f1.exists()) {
            f1.mkdirs();
        }
        File directory = new File (Environment.getExternalStorageDirectory().getAbsolutePath() +"/SolarLabs");
        file_frame = new File(directory, "frame.txt");
        if (!file_frame.exists())
        {
            try {
                file_frame.createNewFile();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
        acc_sensor = new File(directory, "Accelerometer.txt");
        if (!acc_sensor.exists())
        {
            try {
                acc_sensor.createNewFile();
            }catch (IOException e){
                e.printStackTrace();
            }
        }


/**********************************************************************************
        // Get our surfaces to view the camera preview and images
 */
        camera2View = (ImageView) findViewById(R.id.camera2_preview);
        camera2Captured = (TextView) findViewById(R.id.camera2_captures);
        mTextureView = (TextureView) findViewById(R.id.camera2_texture);
        takePictureButton = (ImageButton) findViewById(R.id.btn_takepicture);
        stopwatch = (TextView)findViewById(R.id.watch);
        assert takePictureButton != null;

        // Update the textview with starting values
       // camera2Captured.setText("Capture Success: 0\nCapture Tries: 0");


        // Create the camera manager
        mCameraManager = new CameraManager(this, mTextureView, camera2View);
        //mCameraCalibrator = new CameraCalibrator(this);

        // Set our shared preferences
       // sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Build the result activities for later
        //intentSettings = new Intent(this, SettingsActivity.class);
        //intentResults = new Intent(this, ResultsActivity.class);

        // Lets by default launch into the settings view
        //startActivityForResult(intentSettings, RESULT_SETTINGS);
        //listView.setAdapter(adapter);


        /****************************************************************************************************************
         * condition to check whether button is pressed or not to capture images
         */

        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(_recording){
                    _recording = false;
                    MillisecondTime = 0L ;
                    StartTime = 0L ;
                    TimeBuff = 0L ;
                    UpdateTime = 0L ;
                    Seconds = 0 ;
                    Minutes = 0 ;
                    MilliSeconds = 0 ;

        //            stopwatch.setText("00:00:00");

            //        ListElementsArrayList.clear();

//                    adapter.notifyDataSetChanged();
                    Toast.makeText(getApplicationContext(), "Stopped", Toast.LENGTH_LONG).show();

                }
                else{
                    _recording = true;
                    StartTime = SystemClock.uptimeMillis();
          //          handler.postDelayed(runnable, 0);
                    //reset.setEnabled(false);
                    Toast.makeText(getApplicationContext(), "Started", Toast.LENGTH_LONG).show();
                }

            }
        });




    }

    public Runnable runnable = new Runnable() {

        public void run() {


         //   handler.postDelayed(this, 0);
        }

    };


    /****************************************************************************************************************
     * methode to fetch the gyroscope and accelerometer value
     */
    private final SensorEventListener sel = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                updateOrientation(event.values[0], event.values[1], event.values[2]);
            }
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                updateAccels(event.values[0], event.values[1], event.values[2]);
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    /*****************************************************************************************************************
     * update the currrent value of the gyroscope
     */


    private void updateOrientation(float heading, float pitch, float roll) {
        _gyro_head = heading;
        _gyro_pitch = pitch;
        _gyro_roll = roll;
    }

    /*****************************************************************************************************************
     * update the currrent value of the Accelerometer
     */
    private void updateAccels(float x, float y, float z){
        _accel_x = x;
        _accel_y = y;
        _accel_z = z;
    }


    //private void addButtonListeners() {

        // When the done button is pressed, we want to calibrate
        // This should start the calibration activity, and then start the calibration
        /*Button button_done = (Button) findViewById(R.id.button_done);
        button_done.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(intentResults, RESULT_RESULT);
            }
        });*/

        // We we want to "capture" the current grid, we should record the current corners
       // Button button_record = (Button) findViewById(R.id.button_record);
        //button_record.setOnClickListener( new View.OnClickListener() {
            //@Override
           // public void onClick(View v) {
                // Add the corners
                //mCameraCalibrator.addCorners();
                // Update the text view
         //       camera2Captured.setText("Capture Success: ");// + mCameraCalibrator.getCornersBufferSize()
                       // + "\nCapture Tries: " + mCameraCalibrator.getCaptureTries());
       //     }
        //});
    //}

    /*@Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            linear_acc_x = event.values[0];
           linear_acc_y = event.values[1];
           linear_acc_z = event.values[2];
            String acc = linear_acc.toString();

           // Date d = new Date();
            CharSequence s = DateFormat.format("MM-dd-yy hh:mm:ss", date.getTime());
            Long tsLong = System.nanoTime();// System.currentTimeMillis()/1000;
            String ts = tsLong.toString();
            //File image = new File(imagesFolder, s.toString() + ".jpg"); //this line doesn't work
            String name1 = "/SolarLabs/" + s.toString() +ts+"   "+linear_acc_x+" "+linear_acc_y+" "+linear_acc_z;

            try {
                BufferedWriter outStream1= new BufferedWriter(new FileWriter(acc_sensor,true));
                outStream1.newLine();
                outStream1.write(name1);
                outStream1.close();

                // writer = new FileWriter(file_frame);
                //writer.append("\n"+name);
                //writer.flush();
                //writer.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }*/
    /*@Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }*/

    @Override
    public void onResume() {
        // Pass to our super
        super.onResume();
        // Start the background thread
        mCameraManager.startBackgroundThread();
        // Open the camera
        // This should take care of the permissions requests
        if (mTextureView.isAvailable()) {
            mCameraManager.openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mCameraManager.mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        // Stop background thread
        mCameraManager.stopBackgroundThread();
        // Close our camera, note we will get permission errors if we try to reopen
        // And we have not closed the current active camera
        mCameraManager.closeCamera();
        // Call the super
        super.onPause();
    }


    /********************************************************************************************************
     * Methode to capture and save the images when the images are available
     *
     */

    // Taken from OpenCamera project
    // URL: https://github.com/almalence/OpenCamera/blob/master/src/com/almalence/opencam/cameracontroller/Camera2Controller.java#L3455
    public final static ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader ir) {

            // Contrary to what is written in Aptina presentation acquireLatestImage is not working as described
            // Google: Also, not working as described in android docs (should work the same as acquireNextImage in
            // our case, but it is not)
            // Image im = ir.acquireLatestImage();

            // Get the next image from the queue
            Image image = ir.acquireNextImage();

            // Convert from yuv to correct format
            Mat mYuvMat = ImageUtils.imageToMat(image);
            //Mat mat_out_gray = new Mat();
            Mat mat_out_rgb = new Mat();

            // See if we should gray scale
           // if(sharedPreferences.getBoolean("preGrayScaled", true)) {
             //   Imgproc.cvtColor(mYuvMat, mat_out_gray, Imgproc.COLOR_YUV2GRAY_I420);
              //  Imgproc.cvtColor(mat_out_gray, mat_out_rgb, Imgproc.COLOR_GRAY2RGB);
            //} else {
                //Imgproc.cvtColor(mYuvMat, mat_out_gray, Imgproc.COLOR_YUV2GRAY_I420);
                Imgproc.cvtColor(mYuvMat, mat_out_rgb, Imgproc.COLOR_YUV2RGB_I420);
            //}

            // Get image size from prefs
            //String prefSizeResize = sharedPreferences.getString("prefSizeResize", "0x0");
            int width = 640;//Integer.parseInt(prefSizeResize.substring(0,prefSizeResize.lastIndexOf("x")));
            int height = 480;// Integer.parseInt(prefSizeResize.substring(prefSizeResize.lastIndexOf("x")+1));

            // We we want to resize, do so
            if(width != 0 && height != 0) {

                // Create matrix for the resized image
                Size sz = new Size(width, height);

                // Resize the images
                //Imgproc.resize(mat_out_gray, mat_out_gray, sz);
                Imgproc.resize(mat_out_rgb, mat_out_rgb, sz);
            }


            // Send the images to the image calibrator
            //mCameraCalibrator.processFrame(mat_out_gray, mat_out_rgb);



            final Bitmap bitmap = Bitmap.createBitmap(mat_out_rgb.cols(), mat_out_rgb.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat_out_rgb, bitmap);
            MainActivity.camera2View.setImageBitmap(bitmap);



            //File image = new File(imagesFolder, s.toString() + ".jpg"); //this line doesn't work
           // String name = "/SolarLabs/rgb/" +ts+ ".png";
         //   String file_image = "/SolarLabs/frame.txt";


            /***********************************************************************************************************
             *
             * when button is pressed start capturing the images
             */

            if(_recording){
                Long tsLong = System.nanoTime();// System.currentTimeMillis();
                String ts = tsLong.toString();
                String asd = new String();

                for (int k =0;k<=ts.length()-1;k++) {
                    char first = ts.charAt(k);
                    if (k==5)
                    {
                        asd = asd+".";
                    }
                    asd = asd+first;
                }
                String fname= "";
                fname = ""+ts + ".jpg";

                String fname1 = " rgb/"+ts+ ".jpg";

                String f1 = Environment.getExternalStorageDirectory() + "/" + "SolarLabs"+ "/rgb";
                //String _path = (Environment.getExternalStorageDirectory() +"SolarLabs","rgb");
                File file = new File(Environment.getExternalStorageDirectory() + "/" + "SolarLabs"+"/rgb", fname);

                String name1 = ""  +asd+",   "+_accel_x+", "+_accel_y+", "+_accel_z;

                try {
                    Imgcodecs.imwrite(file.getAbsolutePath(),mat_out_rgb);
                    BufferedWriter outStream= new BufferedWriter(new FileWriter(file_frame,true));
                    outStream.newLine();
                    outStream.write(asd+fname1);
                    outStream.close();

                    // writer = new FileWriter(file_frame);
                    //writer.append("\n"+name);
                    //writer.flush();
                    //writer.close();
                }catch (IOException e){
                    e.printStackTrace();
                }

                try {
                    BufferedWriter outStream1= new BufferedWriter(new FileWriter(acc_sensor,true));
                    outStream1.newLine();
                    outStream1.write(name1);
                    outStream1.close();

                    // writer = new FileWriter(file_frame);
                    //writer.append("\n"+name);
                    //writer.flush();
                    //writer.close();
                }catch (IOException e){
                    e.printStackTrace();
                }

                /************************************************************************************************
                 * time to see the duration of capturing frames
                 */

                MillisecondTime = SystemClock.uptimeMillis() - StartTime;

                UpdateTime = TimeBuff + MillisecondTime;

                Seconds = (int) (UpdateTime / 1000);

                Minutes = Seconds / 60;

                Seconds = Seconds % 60;

                MilliSeconds = (int) (UpdateTime % 1000);

                stopwatch.setText("" + Minutes + ":"
                        + String.format("%02d", Seconds) + ":"
                        + String.format("%03d", MilliSeconds));




            }






           //

            //OutputStream  output = null;

            /*finally {
                if (null != output) {
                    output.close();
                }
            }*/









        // Update image


   /*         AndroidBmpUtil bmpUtil = new AndroidBmpUtil();
            boolean isSaveResult = bmpUtil.save(bitmap, file.getPath());

/*
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] byteArray = stream.toByteArray();

            try {
                save(byteArray,file);

            }catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }*/



            // Make sure we close the image
            image.close();
        }
    };
    public static void save(byte[] bytes,File file) throws IOException {
        OutputStream output = null;
        try {
            output = new FileOutputStream(file);
            output.write(bytes);
        } finally {
            if (null != output) {
                output.close();
            }
        }
    }





    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement


        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);



    }
}
