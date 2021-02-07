//
// Created by 1403 on 2020-12-24.
//
#include "opencv2\opencv.hpp"
#include <iostream>
#include <opencv2/imgproc.hpp>
#include <String>
#include <algorithm>
#include <vector>

#ifndef MY_APPLICATION1224_1_MAPPING_FUNCTION_H
#define MY_APPLICATION1224_1_MAPPING_FUNCTION_H



using namespace cv;
using namespace std;

enum mappingError {
    OK,
    NO_PEAK,
    NOT_LINEAR,
    OVEREXPOSED
};

uchar getMax(Vec3b& value);
int findP0(const Mat& img);
void getMeasurePixelNumber(const vector<double>& wavelength, vector<double>& pixelNumber, int p0, double GRADIENT);
bool vertify_rgb(int& p3, int& p4);
bool vertify_roi(cv::RotatedRect mr);  // 스펙트럼의 주변영역 검출 조건, roi로 사용
mappingError mapWavelength(const std::vector<cv::Vec3b>& input, double& alpha, double& beta,
                           bool detectOverexposed = false, const std::vector<double>& cmf = { 445, 545, 605 });
bool linearFitting(const vector<double>& y, vector<double>& x, double& alpha, double& beta);
double distance(const Point& p1, const Point& p2);
void find_rect(Mat img, vector<RotatedRect>& candidates);//입력이미지로부터 직사각형 부분 찾기
void return_roi(Mat &img, Mat &dst, RotatedRect mr,Rect &roi_rect); //(Mat에 저장한 img, 직사각형 벡터집합 ,선 색, 선 굵기)

#define B 0
#define G 1
#define R 2

#endif //MY_APPLICATION1224_1_MAPPING_FUNCTION_H
