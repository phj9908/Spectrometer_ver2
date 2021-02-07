package org.opencv.android;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.ViewGroup.LayoutParams;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * This class is an implementation of the Bridge View between OpenCV and Java Camera.
 * This class relays on the functionality available in base class and only implements
 * required functions:
 * connectCamera - opens Java camera and sets the PreviewCallback to be delivered.
 * disconnectCamera - closes the camera and stops preview.
 * When frame is delivered via callback from Camera - it processed via OpenCV to be
 * converted to RGBA32 and then passed to the external callback for modifications if required.
 */

@TargetApi(21)
public class JavaCamera2View extends CameraBridgeViewBase {

    private static final String LOGTAG = "JavaCamera2View";

    private ImageReader mImageReader;
    private int mPreviewFormat = ImageFormat.YUV_420_888;

    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private String mCameraID;
    private android.util.Size mPreviewSize = new android.util.Size(-1, -1);

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    public JavaCamera2View(Context context, int cameraId) {
        super(context, cameraId);
    }

    public JavaCamera2View(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private void startBackgroundThread() {
        Log.i(LOGTAG, "startBackgroundThread");
        stopBackgroundThread();
        mBackgroundThread = new HandlerThread("OpenCVCameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        Log.i(LOGTAG, "stopBackgroundThread");
        if (mBackgroundThread == null)
            return;
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Log.e(LOGTAG, "stopBackgroundThread", e);
        }
    }

    protected boolean initializeCamera() {
        Log.i(LOGTAG, "initializeCamera");
        CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            String camList[] = manager.getCameraIdList();
            if (camList.length == 0) {
                Log.e(LOGTAG, "Error: camera isn't detected.");
                return false;
            }
            if (mCameraIndex == CameraBridgeViewBase.CAMERA_ID_ANY) {
                mCameraID = camList[0];
            } else {
                for (String cameraID : camList) {
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraID);
                    if ((mCameraIndex == CameraBridgeViewBase.CAMERA_ID_BACK &&
                            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) ||
                            (mCameraIndex == CameraBridgeViewBase.CAMERA_ID_FRONT &&
                                    characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
                    ) {
                        mCameraID = cameraID;
                        break;
                    }
                }
            }
            if (mCameraID != null) {
                Log.i(LOGTAG, "Opening camera: " + mCameraID);
                manager.openCamera(mCameraID, mStateCallback, mBackgroundHandler);
            } else { // make JavaCamera2View behaves in the same way as JavaCameraView
                Log.i(LOGTAG, "Trying to open camera with the value (" + mCameraIndex + ")");
                if (mCameraIndex < camList.length) {
                    mCameraID = camList[mCameraIndex];
                    manager.openCamera(mCameraID, mStateCallback, mBackgroundHandler);
                } else {
                    // CAMERA_DISCONNECTED is used when the camera id is no longer valid
                    throw new CameraAccessException(CameraAccessException.CAMERA_DISCONNECTED);
                }
            }
            return true;
        } catch (CameraAccessException e) {
            Log.e(LOGTAG, "OpenCamera - Camera Access Exception", e);
        } catch (IllegalArgumentException e) {
            Log.e(LOGTAG, "OpenCamera - Illegal Argument Exception", e);
        } catch (SecurityException e) {
            Log.e(LOGTAG, "OpenCamera - Security Exception", e);
        }
        return false;
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            mCameraDevice = null;
        }

    };

    private void createCameraPreviewSession() {
        final int w = mPreviewSize.getWidth(), h = mPreviewSize.getHeight();
        Log.i(LOGTAG, "createCameraPreviewSession(" + w + "x" + h + ")");
        if (w < 0 || h < 0)
            return;
        try {
            if (null == mCameraDevice) {
                Log.e(LOGTAG, "createCameraPreviewSession: camera isn't opened");
                return;
            }
            if (null != mCaptureSession) {
                Log.e(LOGTAG, "createCameraPreviewSession: mCaptureSession is already started");
                return;
            }

            mImageReader = ImageReader.newInstance(w, h, mPreviewFormat, 2);
            mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    if (image == null)
                        return;

                    // sanity checks - 3 planes
                    Image.Plane[] planes = image.getPlanes();
                    assert (planes.length == 3);
                    assert (image.getFormat() == mPreviewFormat);

                    JavaCamera2Frame tempFrame = new JavaCamera2Frame(image);

                    deliverAndDrawFrame(tempFrame);
                    tempFrame.release();
                    image.close();
                }
            }, mBackgroundHandler);
            Surface surface = mImageReader.getSurface();

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            Log.i(LOGTAG, "createCaptureSession::onConfigured");
                            if (null == mCameraDevice) {
                                return; // camera is already closed
                            }
                            mCaptureSession = cameraCaptureSession;
                            try {

                                //파라미터 설정, 플래시설정

                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_AE_MODE_OFF); //자동노출 끄기(자동 조절 비활성화)
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF); //자동모드 비활성화>>이거 비활성화 하면 밑에 3개는 수동제어 해야함
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE,CaptureRequest.CONTROL_AWB_MODE_OFF); // 자동 wb비활성화
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE,CaptureRequest.CONTROL_AWB_STATE_LOCKED);

                                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,Long.valueOf("100000")); // 노출시간 수동설정(ns) 최소 100us,최대 100ms
                                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 100); // 감도(ISO) 수동 제어 100~800
                                //mPreviewBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION,Long.valueOf("100000000")); // FPS수동제어(ns) 최소100ms
                                mPreviewRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE,CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_FAST);
                                mPreviewRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS,colorTemperature(5500));
                                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE,CaptureRequest.FLASH_MODE_TORCH); // flash 항상 on
                                mPreviewRequestBuilder.set(CaptureRequest.EDGE_MODE,CameraMetadata.EDGE_MODE_FAST);
                                //mPreviewRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
                                mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCAL_LENGTH, 0.002f);

                                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler);
                                Log.i(LOGTAG, "CameraPreviewSession has been started");
                            } catch (Exception e) {
                                Log.e(LOGTAG, "createCaptureSession failed", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Log.e(LOGTAG, "createCameraPreviewSession failed");
                        }
                    },
                    null
            );
        } catch (CameraAccessException e) {
            Log.e(LOGTAG, "createCameraPreviewSession", e);
        }
    }


    //WB 수동제어
    public static RggbChannelVector colorTemperature(int whiteBalance) {
        float temperature = whiteBalance / 100;
        float red;
        float green;
        float blue;

        //Calculate red
        if (temperature <= 66)
            red = 255;
        else {
            red = temperature - 60;
            red = (float) (329.698727446 * (Math.pow((double) red, -0.1332047592)));
            if (red < 0)
                red = 0;
            if (red > 255)
                red = 255;
        }


        //Calculate green
        if (temperature <= 66) {
            green = temperature;
            green = (float) (99.4708025861 * Math.log(green) - 161.1195681661);
            if (green < 0)
                green = 0;
            if (green > 255)
                green = 255;
        } else {
            green = temperature - 60;
            green = (float) (288.1221695283 * (Math.pow((double) green, -0.0755148492)));
            if (green < 0)
                green = 0;
            if (green > 255)
                green = 255;
        }

        //calculate blue
        if (temperature >= 66)
            blue = 255;
        else if (temperature <= 19)
            blue = 0;
        else {
            blue = temperature - 10;
            blue = (float) (138.5177312231 * Math.log(blue) - 305.0447927307);
            if (blue < 0)
                blue = 0;
            if (blue > 255)
                blue = 255;
        }

        Log.v("opencv::", "red=" + red + ", green=" + green + ", blue=" + blue);
        return new RggbChannelVector((red / 255) * 2, (green / 255), (green / 255), (blue / 255) * 2);
    }

    @Override
    protected void disconnectCamera() {
        Log.i(LOGTAG, "close camera");
        try {
            CameraDevice c = mCameraDevice;
            mCameraDevice = null;
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != c) {
                c.close();
            }
        } finally {
            stopBackgroundThread();
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        }
        Log.i(LOGTAG, "camera closed!");
    }

    public static class JavaCameraSizeAccessor implements ListItemAccessor {
        @Override
        public int getWidth(Object obj) {
            android.util.Size size = (android.util.Size)obj;
            return size.getWidth();
        }

        @Override
        public int getHeight(Object obj) {
            android.util.Size size = (android.util.Size)obj;
            return size.getHeight();
        }
    }

    boolean calcPreviewSize(final int width, final int height) {
        Log.i(LOGTAG, "calcPreviewSize: " + width + "x" + height);
        if (mCameraID == null) {
            Log.e(LOGTAG, "Camera isn't initialized!");
            return false;
        }
        CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraID);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            android.util.Size[] sizes = map.getOutputSizes(ImageReader.class);
            List<android.util.Size> sizes_list = Arrays.asList(sizes);
            Size frameSize = calculateCameraFrameSize(sizes_list, new JavaCameraSizeAccessor(), width, height);
            Log.i(LOGTAG, "Selected preview size to " + Integer.valueOf((int)frameSize.width) + "x" + Integer.valueOf((int)frameSize.height));
            assert(!(frameSize.width == 0 || frameSize.height == 0));
            if (mPreviewSize.getWidth() == frameSize.width && mPreviewSize.getHeight() == frameSize.height)
                return false;
            else {
                mPreviewSize = new android.util.Size((int)frameSize.width, (int)frameSize.height);
                return true;
            }
        } catch (CameraAccessException e) {
            Log.e(LOGTAG, "calcPreviewSize - Camera Access Exception", e);
        } catch (IllegalArgumentException e) {
            Log.e(LOGTAG, "calcPreviewSize - Illegal Argument Exception", e);
        } catch (SecurityException e) {
            Log.e(LOGTAG, "calcPreviewSize - Security Exception", e);
        }
        return false;
    }

    @Override
    protected boolean connectCamera(int width, int height) {
        Log.i(LOGTAG, "setCameraPreviewSize(" + width + "x" + height + ")");
        startBackgroundThread();
        initializeCamera();
        try {
            boolean needReconfig = calcPreviewSize(width, height);
            mFrameWidth = mPreviewSize.getWidth();
            mFrameHeight = mPreviewSize.getHeight();

            if ((getLayoutParams().width == LayoutParams.MATCH_PARENT) && (getLayoutParams().height == LayoutParams.MATCH_PARENT))
                mScale = Math.min(((float)height)/mFrameHeight, ((float)width)/mFrameWidth);
            else
                mScale = 0;

            AllocateCache();

            if (needReconfig) {
                if (null != mCaptureSession) {
                    Log.d(LOGTAG, "closing existing previewSession");
                    mCaptureSession.close();
                    mCaptureSession = null;
                }
                createCameraPreviewSession();
            }
        } catch (RuntimeException e) {
            throw new RuntimeException("Interrupted while setCameraPreviewSize.", e);
        }
        return true;
    }

    private class JavaCamera2Frame implements CvCameraViewFrame {
        @Override
        public Mat gray() {
            Image.Plane[] planes = mImage.getPlanes();
            int w = mImage.getWidth();
            int h = mImage.getHeight();
            assert(planes[0].getPixelStride() == 1);
            ByteBuffer y_plane = planes[0].getBuffer();
            int y_plane_step = planes[0].getRowStride();
            mGray = new Mat(h, w, CvType.CV_8UC1, y_plane, y_plane_step);
            return mGray;
        }

        @Override
        public Mat rgba() {
            Image.Plane[] planes = mImage.getPlanes();
            int w = mImage.getWidth();
            int h = mImage.getHeight();
            int chromaPixelStride = planes[1].getPixelStride();


            if (chromaPixelStride == 2) { // Chroma channels are interleaved
                assert(planes[0].getPixelStride() == 1);
                assert(planes[2].getPixelStride() == 2);
                ByteBuffer y_plane = planes[0].getBuffer();
                int y_plane_step = planes[0].getRowStride();
                ByteBuffer uv_plane1 = planes[1].getBuffer();
                int uv_plane1_step = planes[1].getRowStride();
                ByteBuffer uv_plane2 = planes[2].getBuffer();
                int uv_plane2_step = planes[2].getRowStride();
                Mat y_mat = new Mat(h, w, CvType.CV_8UC1, y_plane, y_plane_step);
                Mat uv_mat1 = new Mat(h / 2, w / 2, CvType.CV_8UC2, uv_plane1, uv_plane1_step);
                Mat uv_mat2 = new Mat(h / 2, w / 2, CvType.CV_8UC2, uv_plane2, uv_plane2_step);
                long addr_diff = uv_mat2.dataAddr() - uv_mat1.dataAddr();
                if (addr_diff > 0) {
                    assert(addr_diff == 1);
                    Imgproc.cvtColorTwoPlane(y_mat, uv_mat1, mRgba, Imgproc.COLOR_YUV2RGBA_NV12);
                } else {
                    assert(addr_diff == -1);
                    Imgproc.cvtColorTwoPlane(y_mat, uv_mat2, mRgba, Imgproc.COLOR_YUV2RGBA_NV21);
                }
                return mRgba;
            } else { // Chroma channels are not interleaved
                byte[] yuv_bytes = new byte[w*(h+h/2)];
                ByteBuffer y_plane = planes[0].getBuffer();
                ByteBuffer u_plane = planes[1].getBuffer();
                ByteBuffer v_plane = planes[2].getBuffer();

                int yuv_bytes_offset = 0;

                int y_plane_step = planes[0].getRowStride();
                if (y_plane_step == w) {
                    y_plane.get(yuv_bytes, 0, w*h);
                    yuv_bytes_offset = w*h;
                } else {
                    int padding = y_plane_step - w;
                    for (int i = 0; i < h; i++){
                        y_plane.get(yuv_bytes, yuv_bytes_offset, w);
                        yuv_bytes_offset += w;
                        if (i < h - 1) {
                            y_plane.position(y_plane.position() + padding);
                        }
                    }
                    assert(yuv_bytes_offset == w * h);
                }

                int chromaRowStride = planes[1].getRowStride();
                int chromaRowPadding = chromaRowStride - w/2;

                if (chromaRowPadding == 0){
                    // When the row stride of the chroma channels equals their width, we can copy
                    // the entire channels in one go
                    u_plane.get(yuv_bytes, yuv_bytes_offset, w*h/4);
                    yuv_bytes_offset += w*h/4;
                    v_plane.get(yuv_bytes, yuv_bytes_offset, w*h/4);
                } else {
                    // When not equal, we need to copy the channels row by row
                    for (int i = 0; i < h/2; i++){
                        u_plane.get(yuv_bytes, yuv_bytes_offset, w/2);
                        yuv_bytes_offset += w/2;
                        if (i < h/2-1){
                            u_plane.position(u_plane.position() + chromaRowPadding);
                        }
                    }
                    for (int i = 0; i < h/2; i++){
                        v_plane.get(yuv_bytes, yuv_bytes_offset, w/2);
                        yuv_bytes_offset += w/2;
                        if (i < h/2-1){
                            v_plane.position(v_plane.position() + chromaRowPadding);
                        }
                    }
                }

                Mat yuv_mat = new Mat(h+h/2, w, CvType.CV_8UC1);
                yuv_mat.put(0, 0, yuv_bytes);
                Imgproc.cvtColor(yuv_mat, mRgba, Imgproc.COLOR_YUV2RGBA_I420, 4);
                return mRgba;
            }
        }


        public JavaCamera2Frame(Image image) {
            super();
            mImage = image; // yuv이미지
            mRgba = new Mat();
            mGray = new Mat();
        }

        public void release() {
            mRgba.release();
            mGray.release();
        }

        private Image mImage;
        private Mat mRgba;
        private Mat mGray;
    };
}
