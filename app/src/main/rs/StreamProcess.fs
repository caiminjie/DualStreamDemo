
#pragma version(1)
#pragma rs java_package_name(com.t2m.pan)
#pragma rs_fp_relaxed

rs_allocation src;
rs_allocation dst;
float ratio;
float mapOffsetX, mapOffsetY;
float srcWidth;
float srcHeight;
float srcSize;
float dstWidth;
float dstHeight;
float dstSize;

void initSize() {
    srcWidth = rsAllocationGetDimX(src);
    srcHeight = rsAllocationGetDimY(src);
    srcSize = srcWidth * srcHeight;
    dstWidth = rsAllocationGetDimX(dst);
    dstHeight = rsAllocationGetDimY(dst);
    dstSize = dstWidth * dstHeight;

    float rSrc = (float)srcWidth / srcHeight;
    float rDst = (float)dstWidth / dstHeight;

    if (rSrc > rDst) {
        ratio = (float)srcHeight / dstHeight;
        mapOffsetY = 0.0f;
        mapOffsetX = (srcWidth - (float)srcHeight * rDst) / 2.0f;
    } else {
        ratio = (float)srcWidth / dstWidth;
        mapOffsetX = 0.0f;
        mapOffsetY = (srcHeight - (float)srcWidth / rDst) / 2.0f;
    }
}

//void doScale() {
//    rsForEach(scale, dst, dst);
//}

uchar __attribute__((kernel)) scale(uint32_t x) {
    float mod;
    float s_x, s_y;
    if (x < dstSize) {
        s_x = fma(fmod(x, dstWidth), ratio, mapOffsetX);
        s_y = fma(trunc(x / dstWidth), ratio, mapOffsetY);
        return rsGetElementAt_uchar(src, fma(s_y, srcWidth, s_x));
    } else {
        x -= dstSize;
        mod = fmod(x, 2.0f);
        x *= 0.5f;
        s_x = fma(fmod(x, dstWidth * 0.5f), ratio, mapOffsetX * 0.5f);
        s_y = fma(trunc(x / dstWidth * 0.5f), ratio, mapOffsetY * 0.5f);
        return rsGetElementAt_uchar(src, fma(s_y, srcWidth, srcSize + (s_x * 2.0f) + mod));
    }
}

void __attribute__((kernel)) scale2(uchar in, uint32_t x, uint32_t y) {
    float s_x = fma(x, ratio, mapOffsetX);
    float s_y = fma(y, ratio, mapOffsetY);

    // copy Y
    rsSetElementAt_uchar(dst, rsGetElementAt_uchar(src, s_x, s_y), x, y);

    // copy UV
    if (x % 2 == 0 && y % 2 == 0) {
        s_y = fma(s_y, 0.5f, srcHeight);
        y = fma(y, 0.5f, dstHeight);
        rsSetElementAt_uchar(dst, rsGetElementAt_uchar(src, s_x, s_y), x, y);
        rsSetElementAt_uchar(dst, rsGetElementAt_uchar(src, s_x + 1, s_y), x + 1, y);
    }
}
