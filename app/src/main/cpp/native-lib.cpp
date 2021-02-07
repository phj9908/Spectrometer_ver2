#include <jni.h>
#include "opencv2\opencv.hpp"
#include <iostream>
#include <opencv2/imgproc.hpp>
#include <String>
#include <algorithm>
#include <android/log.h>
#include "mapping_function.h"

using namespace cv;
using namespace std;


bool vertify_rgb(int& p3, int& p4)
{
    // 스펙트럼 내 rgb객체 각각 검출 조건

    float size = p3*p4;
    //float aspect = (float)p4/p3;
    //if(aspect<1) aspect =1/ aspect;

    bool ch1= size>2000 && size<30000;

    __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ", "rgb_size ::: %f", size);

    return ch1;
}


mappingError mapWavelength(const vector<Vec3b>& input, double& alpha, double& beta, bool detectOverexposed, const vector<double>& cmf)
{//mappingError클래스 만들어서 mapWavelength() 만듦, return값은 4가지 경우에 따라 다름

    vector<uchar> peakValue{ 0, 0, 0 }; // R, G, B 최댓값넣을 배열 초기화
    vector<double> peakPn{ -1,-1,-1 }; // R, G, B 최댓값 pixel number넣을 배열 초기화


    for (int pn = 0; pn < input.size(); pn++)// 최댓값 탐색  (input이 픽셀값 벡터>>일차원 벡터니까 size(), 색공간이 3c이라서 vec3b)
    {
        for (int c = 0; c < 3; c++)
        {
            Vec3b value = input[pn];
            if (peakValue[c] < value[c])                       //한 픽셀마다 채널바꿔가며 픽셀값 탐색
            {
                peakValue[c] = value[c];
                peakPn[c] = pn;
            }

//            if (detectOverexposed && peakValue[c] == 255 && value[c] == 255)
//                return mappingError::OVEREXPOSED; //주석 해제시 매핑 잘 안됨
        }
    }


    for (int c = 0; c < 3; c++)
    {
        if (peakPn[c] < 0)
            return mappingError::NO_PEAK;// 최댓값 발견 못 한 경우 오류 코드 반환
    }

    // 스펙트럼 판단
    // if (!((peakPn[B] < peakPn[G]) && (peakPn[G] < peakPn[R]) && (peakPn[B] < peakPn[R])))
    //   return mappingError::NOT_LINEAR;

    // Liear Fitting
    linearFitting(cmf, peakPn, alpha, beta);
    return mappingError::OK;
}

bool linearFitting(const vector<double>& y, vector<double>& x, double& alpha, double& beta)
{
    // x, y 데이터 개수가 다르면 작은 것 반환
    size_t ny= y.size();
    size_t nx= x.size();

    int n=0; // 피팅할 데이터 개수
    if(ny>nx) n=nx;
    else n= ny;

    // Linear fitting
    double xsum = 0, x2sum = 0, ysum = 0, xysum = 0;
    for (int i = 0; i < n; i++)
    {
        xsum += x[i];
        ysum += y[i];
        x2sum += pow(x[i], 2); // x[i] ^ 2
        xysum += x[i] * y[i];
    }
    alpha = (n * xysum - xsum * ysum) / (n * x2sum - xsum * xsum); // 기울기
    beta = (x2sum*ysum - xsum * xysum) / (x2sum * n - xsum * xsum); // y절편
    return true;
}

uchar getMax(Vec3b& value)
{
    uchar max = 0;
    int maxi = 0;
    for (int i = 0; i < 3; i++)
    {
        if (max < value[i])
        {
            max = value[i];
            maxi = i;
        }
    }
    return max;
}

int findP0(const Mat& img) // idea) p0도 여러 frame에 걸쳐 moving averaging 등 평균화 해주면 좋을 듯함
{
    /*
    B-channel에서 가장 밝은(=평균 B 값이 큰) 열(column)을 선정한다.
    p0 <= 선정된 열의 픽셀 넘버
    p0의 wavelength = 450 nm
    p0가 픽셀 넘버와 스펙트럼 간 매핑 기준점이 된다. (마치 바코드의 기준점 처럼)
    */
    int max = 0;
    int width = img.cols;
    int height = img.rows;
    int p0 = 0;
    for (int x = 0; x < width; x++)
    {
        LineIterator iter(img, Point(x, 0), Point(x, height - 1));
        int sum = 0;
        for (int y = 0; y < iter.count; y++, ++iter)
        {
            sum += *(const uchar*)*iter;
        }
        if (sum > max)
        {
            max = sum;
            p0 = x;
        }
    }
    return p0;
}

void getMeasurePixelNumber(const vector<double>& wavelength, vector<double>& pixelNumber, int p0, double GRADIENT)
{

//    int WVL_ZERO = 450; // B채널 피크 wv, pixel number 0되는 기준
//
//    int numMeasure = wavelength.size();
//    for (int i = 0; i < numMeasure; i++) {
//        pixelNumber[i] = (wavelength[i] - WVL_ZERO) * GRADIENT + p0;
//    }


    int WVL_ZERO= 620; // R채널 피크 wv, pixel number 0되는 기준, 620 보다 좀더 작아야 될 듯

    int numMeasure = wavelength.size();
    for (int i = 0; i < numMeasure; i++)
    {
        pixelNumber[i] = p0-(WVL_ZERO-wavelength[i])*GRADIENT;
    }

}

//extern "C"
//JNIEXPORT jdoubleArray JNICALL
//Java_org_techtown_capture_myapplication1224_11_MainActivity_pixelaveraging(JNIEnv *env,
//                                                                            jobject thiz,
//                                                                            jlong mat_addr_input, jdouble sum_alpha, jdouble sum_beta, jdouble sum_p0) {
//    Mat &matInput1 = *(Mat *)mat_addr_input;
//
//    Mat labels[3], stats[3], centroids[3];
//    Mat img[3];
//    Mat img_gray[3];
//    Mat img_thresh[3];
//    Point start(0, 0), end(0, 0);
//
//    static const vector<double> wavelength{468, 535, 620};
//    int numMeasure = wavelength.size();
//    vector<double> pixelNumber(numMeasure);
//
//    static const int MEASURE_HEIGHT=30; // 측정영역 세로 높이
//    static const int MEASURE_WIDTH=30; // 측정영역 가로 높이
//
//    double result[3]={0.0,0.0,0.0}; // 채널 별 픽셀 누적값, 입력 하기위해 초기화 해주기
//
//    int morph_size = 1;
//    int wvli;
//    Mat element = getStructuringElement(MORPH_RECT, Size(2 * morph_size + 1, 2 * morph_size + 1), Point(-1, -1));
//
//    static int height =matInput1.rows;
//    static int width = matInput1.cols;
//
//
//    Mat src=matInput1.clone(); // 직선접근자 입력용
//    //cvtColor(matInput1,src,COLOR_BGR2RGBA); // s10,직선 접근자 입력영상
//
//    cvtColor(matInput1,matInput1,COLOR_BGR2RGBA);  // s6,직선 접근자 입력영상은 원본영상, 프리뷰에 보여질 영상은 RGBA영상
//    // Mat::convertTo(matInput1, -1, 1.0, -100.0); // 과노출 막기위함
//
////    split(matInput1, img);
////
////    Mat zFillMatrix = Mat::zeros(matInput1.size(), CV_8UC1);
////    Mat Blue[] = { img[0], zFillMatrix, zFillMatrix };// {c1,c2,c3} : r빼고 다른 채널은 0으로 채움
////    Mat Green[] = { zFillMatrix,img[1], zFillMatrix };
////    Mat Red[] = { zFillMatrix, zFillMatrix, img[2] };
////    merge(Red, 3, img[2]); // n개의 싱글채널을 멀티채널 1개로 합친다
////    merge(Green, 3, img[1]);
////    merge(Blue, 3, img[0]);
////
////    for(int i= 0; i<3; i++) {
////
////        cvtColor(img[i], img_gray[i], COLOR_RGB2GRAY);
////        threshold(img_gray[i], img_thresh[i], 0, 0xFF, THRESH_BINARY|THRESH_OTSU);
////        morphologyEx(img_thresh[i], img_thresh[i], MORPH_OPEN, element);
////
////        int cnt = connectedComponentsWithStats(img_thresh[i], labels[i], stats[i], centroids[i]);  // 객체의 정보담은 행렬 추출
////
////        for(int j=1; j<cnt; j++){
////            int* p = stats[i].ptr<int>(j);
////            float * centroid_p = centroids[i].ptr<float>(j);
////
////            if (vertify_rgb(p[2], p[3])) {
////
////                rectangle(matInput1, Rect(p[0], p[1], p[2], p[3]), Scalar(255, 255, 255),1);
////                // if(i==1) continue;
////
////                if (i == 2) start = Point(p[0], p[1] + (p[3] / 2));// blue의 스펙트럼 시작점 검출
////                if (i == 0) end = Point(p[0] + p[2], p[1] + (p[3] / 2)); // red의 스펙트럼 끝점 검출
////
////            }
////        }}
////
////    static int length = (end.x) - (start.x);
////
////    vector<vector<double>> pv_graph(3, vector<double>(length));//n line의 픽셀값 읽어들여 2차원 배열에 누적 합, 3행 구성, 1차원 double자료형 길이는 length만큼
////    vector<Vec3b> pv(length);
////
////    LineIterator iter(src, Point(start.x , start.y), Point(end.x, end.y)); // 직선 접근자
////    for (int i = 0; i < length; i++, ++iter)
////    {
////        Vec3b value = *(const Vec3b*)*iter; // 0에서 length 까지 iter가 접근
////        for (int c = 0; c < 3; c++)
////            pv_graph[c][i] += value[c];  // 좌표마다 채널별로 값 저장
////        pv[i] = *(const Vec3b*)*iter;
////    }
////    vector<double> wvl(length); // wavelength를 저장할 벡터(wavelength = f(pixel number) --> wvl[pixel number])
////    double alpha = 0, beta = 0;
////
////    mappingError err = mapWavelength(pv, alpha, beta, true); // wavelength 매핑, 반환값은 오류 코드
////
////    string errMsg = ""; // 오류 메시지
////    switch (err)
////    {
////        case NO_PEAK: // 매핑 과정에서 R, G, B 채널 중 하나라도 peak 값을 찾지 못한 경우
////            errMsg = "NO PEAK";
////            break;
////        case NOT_LINEAR: // 스펙트럼 영상이 아니라고 판단되는 경우
////            errMsg = "NOT SPECTRUM";
////            break;
////        case OVEREXPOSED: // 과노출된 영상이라고 판단되는 경우
////            errMsg = "OVEREXPOSED";
////            break;
////    }
////
////    line(matInput1, Point(start.x, start.y), Point(end.x, start.y), Scalar(255, 0, 0), 2);
////
////    int p0=findP0(img[0]); // 기준점 탐색
//    getMeasurePixelNumber(wavelength, pixelNumber,sum_p0,sum_alpha); // 피크 픽셀넘버 도출
//
//    int halfWidth = MEASURE_WIDTH / 2;
//    int halfHeight = MEASURE_HEIGHT / 2;
//    for (int i = 0; i < numMeasure; i++)
//    {
//        int pn = pixelNumber[i];
//        double sum = 0.0;
//        int x0 = pn - halfWidth;
//        int xend = pn + halfHeight;
//        int y0= start.y-20;
//        int yend=(start.y-20)+MEASURE_HEIGHT;
//
//        for (int y =y0; y < yend; y++)
//        {
//            LineIterator iter(src, Point(x0, y), Point(xend, y));
//            for (int x = 0; x < iter.count; x++, ++iter)
//            {
//                Vec3b value = *(const Vec3b*)* iter;
//                sum += getMax(value);
//            }
//        }
//        result[i] = sum;
//
//        __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ", "pixel sum ::: %f", result[i]);
//
//        int bb;
//        bb = static_cast<int>(wavelength[i]);
//
//        putText(matInput1, format("%dnm", bb), Point(pn-halfWidth, start.y-20), FONT_HERSHEY_COMPLEX, 1, Scalar(255, 255, 255),1);
//        rectangle(matInput1, Rect(pn-halfWidth , start.y-20, MEASURE_WIDTH, MEASURE_HEIGHT), Scalar(255, 255,
//                                                                                                    255), 2, LINE_AA);
//    }
//
//
//    // 자바파일로 데이터 전달하는 부분
//    jdoubleArray dataArray = (env)->NewDoubleArray(4); // 자바로 roi_rect값 전달 할 배열
//
//    if (dataArray == NULL) {
//        return NULL; /* out of memory error thrown */
//    }
//
//    jdouble *data_rectArray = env->GetDoubleArrayElements(dataArray,0);
//
//    for(int i=0; i<4; i++) {
//
//        if(i<3) data_rectArray[i] = result[i] / (MEASURE_HEIGHT*MEASURE_WIDTH); // result[3]
//        else data_rectArray[i] =1.0; // count_frame
//
//    }
//
//    env->ReleaseDoubleArrayElements(dataArray,data_rectArray,0);
//
//    return dataArray;
//}

//여기서 부턴 Rect roi 전달 함수
bool vertify_roi(RotatedRect mr)  // 스펙트럼의 주변영역 검출 조건, roi로 사용
{
    float size = mr.size.area();
    float aspect = (float)mr.size.height / mr.size.width;        //종횡비 계산
    //if (aspect < 1) aspect = 1 / aspect;                         //종횡비 1보다 작으면 역수로(종횡비가 1보다 작으면 세로로 긴 영역임)

    bool ch1 = size < 50000&& size >10000;                      //직사각형 넓이 조건
    bool ch2 = aspect > 1.8 && aspect <4.0;                     //종횡비 조건

    return ch1&&ch2;                                            //조건 반환
}


void find_rect(Mat img, vector<RotatedRect>& candidates) //입력이미지로부터 직사각형 부분 찾기
{
    cvtColor(img,img,COLOR_BGR2GRAY);
    threshold(img,img,5,255,THRESH_BINARY);

    int morph_size = 5;
    Mat element = getStructuringElement(MORPH_RECT, Size(2 * morph_size + 1, 2 * morph_size + 1), Point(-1, -1));
    morphologyEx(img,img,MORPH_OPEN,element);

    vector< vector<Point>> contours;                                                //외곽선 벡터 선언
    findContours(img.clone(), contours, RETR_TREE, CHAIN_APPROX_SIMPLE);            //입력영상의 외곽선을 벡터 'contours'로 반환
    //윤곽검색모드,윤곽근사메소드값

    for (int i = 0; i < (int)contours.size(); i++)                                  //벡터 배열의 객체 갯수=for문 횟수, 검출외곽선 조회
    {
        RotatedRect rot_rect = minAreaRect(contours[i]);                            //RotatedRect 객체 rot_rect 선언
        //minAreaRect():검출된 외곽선 벡터들의 최소영역을 회전사각형'rot_rect'으로 반환

        if (vertify_roi(rot_rect))                                                 //직사각형 조건에 해당하면
            candidates.push_back(rot_rect);                                          //직사각형모양 벡터 rot_rect를 candidate라는 벡터로 저장해둠
    }
}

void return_roi(Mat &img, Mat &dst, RotatedRect mr,Rect &roi_rect) //(Mat에 저장한 img, 직사각형 벡터집합 ,선 색, 선 굵기)
{                                                                           //검출된 직사각형 벡터를 영상에 그림

    Point2f pts[4];                                         //점 배열객체 4개 선언
    mr.points(pts);                                         //검출된 직사각형 벡터를 points()을 이용하여 좌표(사각형 꼭짓점)로 변환후 pts배열에 반환

//    Mat roi(img, Rect(pts[2].x, pts[2].y, mr.size.height, mr.size.width));         //img에다가  mr직사각형모양 관심영역 생성,그 관심영역을 'roi'라는 이름으로 Mat 객체 생성

//    for (int i = 0; i < 4; ++i)
//    {
//        line(img, pts[i], pts[(i + 1) % 4],Scalar(255,255,255), 2);      //line(img,점1,점1과 연결할 점2,색, 굵기)
//    }

    roi_rect.x=(int)pts[2].x;
    roi_rect.y=(int)pts[2].y;
    roi_rect.width= (int)mr.size.height;
    roi_rect.height= (int)mr.size.width;

//    dst = roi; // dst 리턴하기
}



extern "C"
JNIEXPORT jintArray JNICALL
Java_org_techtown_capture_myapplication1224_11_MainActivity_Returnroi(JNIEnv *env, jobject thiz,
                                                                      jlong mat_addr_input) {
    Mat &img_input = *(Mat *)mat_addr_input;
    jintArray roiArray = (env)->NewIntArray(4); // 자바로 roi_rect값 전달 할 배열

    if (roiArray == NULL) {
        return NULL; /* out of memory error thrown */
    }

    Mat img_roi;
    Rect roi;

    vector<RotatedRect> candidates;
    find_rect(img_input, candidates);

    for(int i=0; i<(int)candidates.size(); i++){  // roi rect 반환
        return_roi(img_input, img_roi, candidates[i],roi);
    }

    jint *rectArray = env->GetIntArrayElements(roiArray,0);

    rectArray[0] = roi.x;
    rectArray[1] = roi.y;
    rectArray[2] = roi.width;
    rectArray[3] = roi.height;

    env->ReleaseIntArrayElements(roiArray,rectArray,0);

    return roiArray;
}

extern "C"
JNIEXPORT jdoubleArray JNICALL
Java_org_techtown_capture_myapplication1224_11_MainActivity_data_1averaging(JNIEnv *env,
                                                                            jobject thiz,
                                                                            jlong mat_addr_input) {
    Mat &matInput1 = *(Mat *)mat_addr_input;

    Mat labels[3], stats[3], centroids[3];
    Mat img[3];
    Mat img_gray[3];
    Mat img_thresh[3];
    Point start(0, 0), end(0, 0);

    int morph_size = 1;
    Mat element = getStructuringElement(MORPH_RECT, Size(2 * morph_size + 1, 2 * morph_size + 1), Point(-1, -1));

    static int height =matInput1.rows;
    static int width = matInput1.cols;


    Mat src=matInput1.clone(); // 직선접근자 입력용
//    cvtColor(matInput1,src,COLOR_BGR2RGBA); // s10,직선 접근자 입력영상

    cvtColor(matInput1,matInput1,COLOR_BGR2RGBA);  // s6,직선 접근자 입력영상은 원본영상, 프리뷰에 보여질 영상은 RGBA영상
//     Mat::convertTo(matInput1, -1, 1.0, -100.0); // 과노출 막기위함

    split(matInput1, img);

    Mat zFillMatrix = Mat::zeros(matInput1.size(), CV_8UC1);
    Mat Blue[] = { img[0], zFillMatrix, zFillMatrix };// {c1,c2,c3} : r빼고 다른 채널은 0으로 채움
    Mat Green[] = { zFillMatrix,img[1], zFillMatrix };
    Mat Red[] = { zFillMatrix, zFillMatrix, img[2] };
    merge(Red, 3, img[2]); // n개의 싱글채널을 멀티채널 1개로 합친다
    merge(Green, 3, img[1]);
    merge(Blue, 3, img[0]);

    for(int i= 0; i<3; i++) {

        cvtColor(img[i], img_gray[i], COLOR_RGB2GRAY);
        threshold(img_gray[i], img_thresh[i], 0, 0xFF, THRESH_BINARY|THRESH_OTSU);
        morphologyEx(img_thresh[i], img_thresh[i], MORPH_OPEN, element);

        int cnt = connectedComponentsWithStats(img_thresh[i], labels[i], stats[i], centroids[i]);  // 객체의 정보담은 행렬 추출

        for(int j=1; j<cnt; j++){
            int* p = stats[i].ptr<int>(j);
//            float * centroid_p = centroids[i].ptr<float>(j);

            if (vertify_rgb(p[2], p[3])) {

                rectangle(matInput1, Rect(p[0], p[1], p[2], p[3]), Scalar(255, 255, 255),1);
                // if(i==1) continue;

                if (i == 2) start = Point(p[0], p[1] + (p[3] / 2));// blue의 스펙트럼 시작점 검출
                if (i == 0) end = Point(p[0] + p[2], p[1] + (p[3] / 2)); // red의 스펙트럼 끝점 검출

            }
        }}

    static int length = (end.x) - (start.x);

    vector<vector<double>> pv_graph(3, vector<double>(length));//n line의 픽셀값 읽어들여 2차원 배열에 누적 합, 3행 구성, 1차원 double자료형 길이는 length만큼
    vector<Vec3b> pv(length);

    LineIterator iter(src, Point(start.x , start.y), Point(end.x, end.y)); // 직선 접근자
    for (int i = 0; i < length; i++, ++iter)
    {
        Vec3b value = *(const Vec3b*)*iter; // 0에서 length 까지 iter가 접근
        for (int c = 0; c < 3; c++)
            pv_graph[c][i] += value[c];  // 좌표마다 채널별로 값 저장
        pv[i] = *(const Vec3b*)*iter;
    }
    vector<double> wvl(length); // wavelength를 저장할 벡터(wavelength = f(pixel number) --> wvl[pixel number])
    double alpha = 0, beta = 0;

    mappingError err = mapWavelength(pv, alpha, beta, true); // wavelength 매핑, 반환값은 오류 코드

    string errMsg = ""; // 오류 메시지
    switch (err)
    {
        case NO_PEAK: // 매핑 과정에서 R, G, B 채널 중 하나라도 peak 값을 찾지 못한 경우
            errMsg = "NO PEAK";
            break;
        case NOT_LINEAR: // 스펙트럼 영상이 아니라고 판단되는 경우
            errMsg = "NOT SPECTRUM";
            break;
        case OVEREXPOSED: // 과노출된 영상이라고 판단되는 경우
            errMsg = "OVEREXPOSED";
            break;
    }

    line(matInput1, Point(start.x, start.y), Point(end.x, start.y), Scalar(255, 0, 0), 2);

    int p0=findP0(img[0]); // 기준점 탐색

    // 자바파일로 데이터 전달하는 부분
    jdoubleArray dataArray = (env)->NewDoubleArray(4);

    if (dataArray == NULL) {
        return NULL; /* out of memory error thrown */
    }

    jdouble *data_rectArray = env->GetDoubleArrayElements(dataArray,0);

    for(int i=0; i<4; i++) {

        switch(i){
            case 0: data_rectArray[i] = alpha; break;
            case 1: data_rectArray[i] = beta; break;
            case 2: data_rectArray[i] =(double)p0; break;
            case 3: data_rectArray[i] =1.0; break; // count_frame
        }
    }

    env->ReleaseDoubleArrayElements(dataArray,data_rectArray,0);

    return dataArray;


}

extern "C"
JNIEXPORT jintArray JNICALL
Java_org_techtown_capture_myapplication1224_11_SensingActivity_Returnroi(JNIEnv *env, jobject thiz,
                                                                         jlong mat_addr_input) {
    Mat &img_input = *(Mat *)mat_addr_input;
    jintArray roiArray = (env)->NewIntArray(4); // 자바로 roi_rect값 전달 할 배열

    if (roiArray == NULL) {
        return NULL; /* out of memory error thrown */
    }

    Mat img_roi;
    Rect roi;

    vector<RotatedRect> candidates;
    find_rect(img_input, candidates);

    for(int i=0; i<(int)candidates.size(); i++){  // roi rect 반환
        return_roi(img_input, img_roi, candidates[i],roi);
    }

    jint *rectArray = env->GetIntArrayElements(roiArray,0);

    rectArray[0] = roi.x;
    rectArray[1] = roi.y;
    rectArray[2] = roi.width;
    rectArray[3] = roi.height;

    env->ReleaseIntArrayElements(roiArray,rectArray,0);

    return roiArray;
}


extern "C"
JNIEXPORT jdoubleArray JNICALL
Java_org_techtown_capture_myapplication1224_11_SensingActivity_pixelaveraging(JNIEnv *env,
                                                                              jobject thiz,
                                                                              jlong mat_addr_input,
                                                                              jdouble sum_alpha,
                                                                              jdouble sum_beta,
                                                                              jdouble sum_p0,
                                                                              jint y) {
    Mat &matInput1 = *(Mat *)mat_addr_input;

    static const vector<double> wavelength{468, 535, 620}; // s10: wavelength{ 480,555,630}; S6:wavelength{468, 535, 620};
    int numMeasure = wavelength.size();
    vector<double> pixelNumber(numMeasure);

    static const int MEASURE_HEIGHT=30; // 측정영역 세로 높이
    static const int MEASURE_WIDTH=30; // 측정영역 가로 높이

    double result[3]={0.0,0.0,0.0}; // 채널 별 픽셀 누적값, 입력 하기위해 초기화 해주기

    int morph_size = 1;
    Mat element = getStructuringElement(MORPH_RECT, Size(2 * morph_size + 1, 2 * morph_size + 1), Point(-1, -1));

    Mat src=matInput1.clone(); // 직선접근자 입력용
//    cvtColor(matInput1,src,COLOR_BGR2RGBA); // s10,직선 접근자 입력영상

    cvtColor(matInput1,matInput1,COLOR_BGR2RGBA);  // s6,직선 접근자 입력영상은 원본영상, 프리뷰에 보여질 영상은 RGBA영상
    // Mat::convertTo(matInput1, -1, 1.0, -100.0); // 과노출 막기위함

    getMeasurePixelNumber(wavelength, pixelNumber,sum_p0,sum_alpha); // 피크 픽셀넘버 도출

    int halfWidth = MEASURE_WIDTH / 2;
    int halfHeight = MEASURE_HEIGHT / 2;
    for (int i = 0; i < numMeasure; i++)
    {
        int pn = pixelNumber[i];
        double sum = 0.0;
        int x0 = pn - halfWidth;
        int xend = pn + halfHeight;
        int y0= y+10;
        int yend=(y+10)+MEASURE_HEIGHT;

        for (int y =y0; y < yend; y++)
        {
            LineIterator iter(src, Point(x0, y), Point(xend, y));
            for (int x = 0; x < iter.count; x++, ++iter)
            {
                Vec3b value = *(const Vec3b*)* iter;
                sum += getMax(value);
            }
        }
        result[i] = sum;

        __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ", "pixel sum ::: %f", result[i]);

        int bb;
        bb = static_cast<int>(wavelength[i]);

        putText(matInput1, format("%dnm", bb), Point(pn-halfWidth, y-20), FONT_HERSHEY_COMPLEX, 0.5, Scalar(255, 255, 255),1);
        rectangle(matInput1, Rect(pn-halfWidth , y-20, MEASURE_WIDTH, MEASURE_HEIGHT), Scalar(255, 255,
                                                                                                    255), 2, LINE_AA);
    }

    // 자바파일로 데이터 전달하는 부분
    jdoubleArray dataArray = (env)->NewDoubleArray(4); // 자바로 roi_rect값 전달 할 배열

    if (dataArray == NULL) {
        return NULL; /* out of memory error thrown */
    }

    jdouble *data_rectArray = env->GetDoubleArrayElements(dataArray,0);

    for(int i=0; i<4; i++) {

        if(i<3) data_rectArray[i] = result[i] / (MEASURE_HEIGHT*MEASURE_WIDTH); // result[3]
        else data_rectArray[i] =1.0; // count_frame

    }

    env->ReleaseDoubleArrayElements(dataArray,data_rectArray,0);

    return dataArray;
}
