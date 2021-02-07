package org.techtown.capture.myapplication1224_1;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.SensorAdditionalInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static android.Manifest.permission.CAMERA;
import static org.techtown.capture.myapplication1224_1.MainActivity.count_frame;
import static org.techtown.capture.myapplication1224_1.MainActivity.sum_alpha;
import static org.techtown.capture.myapplication1224_1.MainActivity.sum_beta;
import static org.techtown.capture.myapplication1224_1.MainActivity.sum_p0;


public class SensingActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "opencv";
    private Mat img_input;
    private Mat img_output;
    private Mat matInput1, matInput2;
    private Mat matMask = null, matNotMask = null;

    private CameraBridgeViewBase mOpenCvCameraView2;

    public native double[] pixelaveraging(long matAddrInput, double sum1, double sum2, double sum_p0, int y); // result[3] 도출(>>sensing값 도출)

    double alpha = 0, beta = 0, p0 = 0;

    static {
        System.loadLibrary("opencv_java4");
        System.loadLibrary("native-lib");
    }

    private boolean frame = false; // 영상처리 성공 프레임일 때
    private boolean sensing = false; // pixel_averaging 할 때

    TimerTask timerTask;
    Timer timer = new Timer();

    static double sum_B = 0;
    static double sum_G = 0;
    static double sum_R = 0;

    Rect roi = new Rect();
    int y=0; // y= roi.y+(roi.height/2)

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    mOpenCvCameraView2.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensing);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Intent intent = getIntent();
        double sensing_data[] = intent.getExtras().getDoubleArray("sensing_data"); // alpha,beta,p0,count_frame 배열

        alpha = sensing_data[0];
        beta = sensing_data[1];
        p0 = sensing_data[2];

        // sensing_data[3]은 count_frmae

        roi.x =(int)sensing_data[4];
        roi.y = (int)sensing_data[5];
        roi.width = (int)sensing_data[6];
        roi.height = (int)sensing_data[7];

        y= roi.y+(roi.height/2); // pixelaveraging 영역 y좌표

            Log.d(TAG, "first_roi_x " +  sensing_data[3] + " found");
            Log.d(TAG, "first_roi_y " +  sensing_data[4] + " found");
            Log.d(TAG, "first_roi_width " +  sensing_data[5] + " found");
            Log.d(TAG, "first_roi_height " + sensing_data[6] + " found");


        Button sensing_button = (Button) findViewById(R.id.sensing_bt);
        sensing_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(sensing_data!=null){
                     if(sum_B!=0)
                {
                    sum_B=0;
                    sum_G=0;
                    sum_R=0;
                }
                sensing=true;

                //startTimerTask();
                }
                else  Toast.makeText(SensingActivity.this, "non data", Toast.LENGTH_SHORT).show();
            }
        });

        Intent intent_result = new Intent(SensingActivity.this, ResultActivity.class);

        Button result_button = (Button) findViewById(R.id.result_bt);
        result_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                  // 데이터 액티비티로부터 평균값 인덴트 null여부로 나중에 수정하던지

                double[] result_sum_array = {0.0, 0.0, 0.0};

                result_sum_array[0] = sum_B;
                result_sum_array[1] = sum_G;
                result_sum_array[2] = sum_R;

                intent_result.putExtra("result", result_sum_array);
                startActivity(intent_result);

            }
        });

        Button remapping_button = (Button) findViewById(R.id.remapping_button);
        remapping_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                sum_alpha=0;
                sum_beta=0;
                sum_p0=0;
                count_frame = 0;

                Intent re_intent = new Intent(SensingActivity.this, MainActivity.class);
                startActivity(re_intent);
            }
        });

        mOpenCvCameraView2 = (CameraBridgeViewBase) findViewById(R.id.activity_surface_view2);
        mOpenCvCameraView2.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView2.setCvCameraViewListener(this);
        mOpenCvCameraView2.setCameraIndex(0); // front-camera(1),  back-camera(0)

    }

    public void toggle_sensing() {
        if (sensing) sensing = false;
        else sensing = true;
    }

    private void startTimerTask() {
        ScheduledJob job = new ScheduledJob();

        timerTask = new TimerTask() {
            @Override
            public void run() {
                job.run();
            }
        };
        timer.scheduleAtFixedRate(timerTask, 0, 5); // 10ns마다 작업이 실행되게 한다.
    }

    private void stopTimerTask() {
        sensing = false;
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    class ScheduledJob extends TimerTask {
        public void run() {
            if (count_frame < 100) toggle_sensing();
            else sensing = false;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView2 != null)
            mOpenCvCameraView2.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "onResume :: Internal OpenCV library not found.");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "onResum :: OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }


    public void onDestroy() {
        timer.cancel();
        super.onDestroy();

        if (mOpenCvCameraView2 != null)
            mOpenCvCameraView2.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        img_input = inputFrame.rgba();

        if (sensing) {

            if (img_output == null)
                img_output = new Mat(img_input.rows(), img_input.cols(), img_input.type());
            if (matInput1 == null)
                matInput1 = new Mat(img_input.rows(), img_input.cols(), img_input.type());
            if (matInput2 == null)
                matInput2 = new Mat(img_input.rows(), img_input.cols(), img_input.type());
            if (matNotMask == null)
                matNotMask = new Mat(img_input.rows(), img_input.cols(), img_input.type());

                matMask = Mat.zeros(img_input.size(), img_input.type());
                matMask.submat(roi).setTo(Scalar.all(255)); //  roi영역의 위치, 크기만 픽셀값 255할당

                if (matMask != null) {

                    Core.bitwise_and(img_input, matMask, matInput1); // bitwise_and()로 roi영역 원본에 입히기

                    double[] result_array = new double[6];
                    result_array = pixelaveraging(matInput1.getNativeObjAddr(), alpha, beta, p0, y);

                    sum_B += result_array[0];
                    sum_G += result_array[1];
                    sum_R += result_array[2];

//                    if (frame) { // count_frame이 필요할 때 ver
//                        Log.d(TAG, "frame[" + count_frame + "]: static_result_array[0] :::" + sum_B);
//                        Log.d(TAG, "frame[" + count_frame + "]: static_result_array[1] :::" + sum_G);
//                        Log.d(TAG, "frame[" + count_frame + "]: static_result_array[2] :::" + sum_R);
//
//                    }
                    Imgproc.rectangle(img_input, roi, new Scalar(255,255,255), 2); // roi 표시

                        Log.d(TAG, "static_result_array[0] :::" + sum_B);
                        Log.d(TAG, "static_result_array[1] :::" + sum_G);
                        Log.d(TAG, "static_result_array[2] :::" + sum_R);

                    if (matMask == null || matNotMask == null) {
                        return img_input;
                    }

                    Core.bitwise_not(matMask, matNotMask);
                    Core.bitwise_and(img_input, matNotMask, matInput2);
                    Core.bitwise_or(matInput1, matInput2, img_output);

                    sensing=false; // 영상처리 후 다시 false
            }

            return img_output;
        } else return img_input;
    }

    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView2);
    }


    //여기서부턴 퍼미션 관련 메소드
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;


    protected void onCameraPermissionGranted() {
        List<? extends CameraBridgeViewBase> cameraViews = getCameraViewList();
        if (cameraViews == null) {
            return;
        }
        for (CameraBridgeViewBase cameraBridgeViewBase: cameraViews) {
            if (cameraBridgeViewBase != null) {
                cameraBridgeViewBase.setCameraPermissionGranted();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        boolean havePermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
                havePermission = false;
            }
        }
        if (havePermission) {
            onCameraPermissionGranted();
        }
    }}