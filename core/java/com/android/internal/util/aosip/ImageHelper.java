/*
* Copyright (C) 2013 SlimRoms Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.internal.util.aosip;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.renderscript.Element;
import android.renderscript.Allocation;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.RenderScript;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.View;
import android.view.WindowManager;

public class ImageHelper {

    public static Bitmap screenshotSurface(Context context) {
        WindowManager mWindowManager;
        Display mDisplay;
        DisplayMetrics mDisplayMetrics;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mDisplay = mWindowManager.getDefaultDisplay();
        mDisplayMetrics = new DisplayMetrics();
        mDisplay.getRealMetrics(mDisplayMetrics);
        int displayHeight = mDisplayMetrics.heightPixels;
        int displayWidth = mDisplayMetrics.widthPixels;
        Rect displayRect = new Rect(0, 0, displayWidth, displayHeight);
        int rot = mDisplay.getRotation();
        Bitmap mScreenBitmap;
        try {
            mScreenBitmap = SurfaceControl.screenshot(displayRect, displayWidth, displayHeight, rot);
            // Crop the screenshot to selected region
            Bitmap swBitmap = mScreenBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Bitmap cropped = Bitmap.createBitmap(swBitmap, Math.max(0, displayRect.left), Math.max(0, displayRect.top),
                    displayRect.width(), displayRect.height());
            swBitmap.recycle();
            mScreenBitmap.recycle();
            mScreenBitmap = cropped;
            } catch (Exception e) {
                Log.d ("SynthOS", "Screenshot service: FB is protected, falling back to an empty Bitmap");
                mScreenBitmap = Bitmap.createBitmap(displayWidth, displayHeight, Bitmap.Config.ARGB_8888);
        }
        return mScreenBitmap;
    }


    public static Bitmap getColoredBitmap(Drawable d, int color) {
        if (d == null) {
            return null;
        }
        Bitmap colorBitmap = ((BitmapDrawable) d).getBitmap();
        Bitmap grayscaleBitmap = toGrayscale(colorBitmap);
        Paint pp = new Paint();
        pp.setAntiAlias(true);
        PorterDuffColorFilter frontFilter =
            new PorterDuffColorFilter(color, Mode.MULTIPLY);
        pp.setColorFilter(frontFilter);
        Canvas cc = new Canvas(grayscaleBitmap);
        final Rect rect = new Rect(0, 0, grayscaleBitmap.getWidth(), grayscaleBitmap.getHeight());
        cc.drawBitmap(grayscaleBitmap, rect, rect, pp);
        return grayscaleBitmap;
    }

    public static Bitmap toGrayscale(Bitmap bmpOriginal) {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();
        try {
            bmpOriginal = RGB565toARGB888(bmpOriginal);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        ColorMatrix cm = new ColorMatrix();
        final Rect rect = new Rect(0, 0, width, height);
        cm.setSaturation(0);

        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, rect, rect, paint);
        return bmpGrayscale;
    }

    public static Bitmap getGrayscaleBlurredImage(Context context, Bitmap image) {
        return getGrayscaleBlurredImage(context, image, 3.5f);
    }

    public static Bitmap getGrayscaleBlurredImage(Context context, Bitmap image, float radius) {
        Bitmap finalImage = Bitmap.createBitmap(
                image.getWidth(), image.getHeight(),
                Bitmap.Config.ARGB_8888);
        finalImage = toGrayscale(getBlurredImage(context, image, radius));
        return finalImage;
    }

    public static Drawable resize(Context context, Drawable image, int size) {
        if (image == null || context == null) {
            return null;
        }

        int newSize = Converter.dpToPx(context, size);
        Bitmap bitmap = ((BitmapDrawable) image).getBitmap();
        Bitmap scaledBitmap = Bitmap.createBitmap(newSize, newSize, Config.ARGB_8888);

        float ratioX = newSize / (float) bitmap.getWidth();
        float ratioY = newSize / (float) bitmap.getHeight();
        float middleX = newSize / 2.0f;
        float middleY = newSize / 2.0f;

        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setAntiAlias(true);

        Matrix scaleMatrix = new Matrix();
        scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);

        Canvas canvas = new Canvas(scaledBitmap);
        canvas.setMatrix(scaleMatrix);
        canvas.drawBitmap(bitmap, middleX - bitmap.getWidth() / 2,
                middleY - bitmap.getHeight() / 2, paint);
        return new BitmapDrawable(context.getResources(), scaledBitmap);
    }

    public static Bitmap resizeMaxDeviceSize(Context context, Drawable image) {
        Bitmap i2b = ((BitmapDrawable) image).getBitmap();
        return resizeMaxDeviceSize(context, i2b);
    }

    public static Bitmap resizeMaxDeviceSize(Context context, Bitmap image) {
        Bitmap imageToBitmap;
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = context.getSystemService(WindowManager.class);
        wm.getDefaultDisplay().getRealMetrics(metrics);
        int maxHeight = metrics.heightPixels;
        int maxWidth = metrics.widthPixels;
        try {
            imageToBitmap = RGB565toARGB888(image);
            if (maxHeight > 0 && maxWidth > 0) {
                int width = imageToBitmap.getWidth();
                int height = imageToBitmap.getHeight();
                float ratioBitmap = (float) width / (float) height;
                float ratioMax = (float) maxWidth / (float) maxHeight;

                int finalWidth = maxWidth;
                int finalHeight = maxHeight;
                if (ratioMax > ratioBitmap) {
                    finalWidth = (int) ((float)maxHeight * ratioBitmap);
                } else {
                    finalHeight = (int) ((float)maxWidth / ratioBitmap);
                }
                imageToBitmap = Bitmap.createScaledBitmap(imageToBitmap, finalWidth, finalHeight, true);
                return imageToBitmap;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return image;
    }

    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);
        final float roundPx = 24;
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }

    public static Bitmap getCircleBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        Bitmap output = Bitmap.createBitmap(width, height,
                Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        BitmapShader shader = new BitmapShader(bitmap,  TileMode.CLAMP, TileMode.CLAMP);
        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(shader);

        canvas.drawCircle(width/2, height/2, width/2, paint);

        return output;
    }

    public static Bitmap getBlurredImage(Context context, Bitmap image) {
        return getBlurredImage(context, image, 3.5f);
    }

    public static Bitmap getBlurredImage(Context context, Bitmap image, float radius) {
        try {
            image = RGB565toARGB888(image);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Bitmap bitmap = Bitmap.createBitmap(
                image.getWidth(), image.getHeight(),
                Bitmap.Config.ARGB_8888);
        RenderScript renderScript = RenderScript.create(context);
        Allocation blurInput = Allocation.createFromBitmap(renderScript, image);
        Allocation blurOutput = Allocation.createFromBitmap(renderScript, bitmap);

        ScriptIntrinsicBlur blur = ScriptIntrinsicBlur.create(renderScript,
                Element.U8_4(renderScript));
        blur.setInput(blurInput);
        blur.setRadius(radius); // radius must be 0 < r <= 25
        blur.forEach(blurOutput);
        blurOutput.copyTo(bitmap);
        renderScript.destroy();

        return bitmap;
   }

    private static Bitmap RGB565toARGB888(Bitmap img) throws Exception {
        int numPixels = img.getWidth() * img.getHeight();
        int[] pixels = new int[numPixels];

        //Get JPEG pixels.  Each int is the color values for one pixel.
        img.getPixels(pixels, 0, img.getWidth(), 0, 0, img.getWidth(), img.getHeight());

        //Create a Bitmap of the appropriate format.
        Bitmap result = Bitmap.createBitmap(img.getWidth(), img.getHeight(), Bitmap.Config.ARGB_8888);

        //Set RGB pixels.
        result.setPixels(pixels, 0, result.getWidth(), 0, 0, result.getWidth(), result.getHeight());
        return result;
    }

}
