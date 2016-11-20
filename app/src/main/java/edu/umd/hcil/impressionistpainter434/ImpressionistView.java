package edu.umd.hcil.impressionistpainter434;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.view.VelocityTrackerCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import java.text.MessageFormat;
import java.util.Random;

/**
 * Created by jon on 3/20/2016.
 */
public class ImpressionistView extends View {

    private ImageView _imageView;

    private Canvas _offScreenCanvas = null;
    private Bitmap _offScreenBitmap = null;
    private Paint _paint = new Paint();

    private int _alpha = 150;
    private int _defaultRadius = 25;
    private Point _lastPoint = null;
    private long _lastPointTime = -1;
    private boolean _useMotionSpeedForBrushStrokeSize = true;
    private Paint _paintBorder = new Paint();
    private BrushType _brushType = BrushType.Square;
    private float _minBrushRadius = 5;
    public boolean _useSaturatedColors = false;
    public boolean _useDynamicWidth = false;
    public boolean _useFill = true;
    private VelocityTracker _velocityTracker = null;

    public ImpressionistView(Context context) {
        super(context);
        init(null, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    /**
     * Because we have more than one constructor (i.e., overloaded constructors), we use
     * a separate initialization method
     * @param attrs
     * @param defStyle
     */
    private void init(AttributeSet attrs, int defStyle){

        // Set setDrawingCacheEnabled to true to support generating a bitmap copy of the view (for saving)
        // See: http://developer.android.com/reference/android/view/View.html#setDrawingCacheEnabled(boolean)
        //      http://developer.android.com/reference/android/view/View.html#getDrawingCache()
        this.setDrawingCacheEnabled(true);

        _paint.setColor(Color.RED);
        _paint.setAlpha(_alpha);
        _paint.setAntiAlias(true);
        _paint.setStyle(Paint.Style.FILL);
        _paint.setStrokeWidth(4);

        _paintBorder.setColor(Color.BLACK);
        _paintBorder.setStrokeWidth(3);
        _paintBorder.setStyle(Paint.Style.STROKE);
        _paintBorder.setAlpha(50);

        //_paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh){

        Bitmap bitmap = getDrawingCache();
        Log.v("onSizeChanged", MessageFormat.format("bitmap={0}, w={1}, h={2}, oldw={3}, oldh={4}", bitmap, w, h, oldw, oldh));
        if(bitmap != null) {
            _offScreenBitmap = getDrawingCache().copy(Bitmap.Config.ARGB_8888, true);
            _offScreenCanvas = new Canvas(_offScreenBitmap);
        }
    }

    /**
     * Sets the ImageView, which hosts the image that we will paint in this view
     * @param imageView
     */
    public void setImageView(ImageView imageView){
        _imageView = imageView;
    }

    /**
     * Sets the brush type. Feel free to make your own and completely change my BrushType enum
     * @param brushType
     */
    public void setBrushType(BrushType brushType){
        _brushType = brushType;
    }

    //Used by MainActivity to toggle whether or not to use saturated colors
    public void setSaturatedColors() {
        if(_useSaturatedColors) {
            _useSaturatedColors = false;
        } else {
            _useSaturatedColors = true;
        }
    }

    //Used by MainActivity to toggle whether or not to use the velocitytracker to determine brush width
    public void setDynamicWidth() {
        if(_useDynamicWidth) {
            _useDynamicWidth = false;
        } else {
            _useDynamicWidth = true;
        }
    }

    //Used by MainActivity to toggle whether or not to use draw filled in shapes or outlines (does not affect line)
    public void setStrokeOrFill() {
        if(_useFill) {
            _useFill = false;
            _paint.setStyle(Paint.Style.STROKE);
        } else {
            _useFill = true;
            _paint.setStyle(Paint.Style.FILL);
        }
    }

    //Returns the bitmap for the save image button
    public Bitmap getBitmap(){
        return _offScreenBitmap;
    }

    /**
     * Clears the painting
     */
    public void clearPainting(){
        _offScreenBitmap = getDrawingCache().copy(Bitmap.Config.ARGB_8888, true);
        _offScreenCanvas = new Canvas(_offScreenBitmap);
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(_offScreenBitmap != null) {
            canvas.drawBitmap(_offScreenBitmap, 0, 0, _paint);
        }

        // Draw the border. Helpful to see the size of the bitmap in the ImageView
        canvas.drawRect(getBitmapPositionInsideImageView(_imageView), _paintBorder);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent){
        //Gets a list of X and Y coordinates
        int historySize = motionEvent.getHistorySize();
        float touchX = motionEvent.getX();
        float touchY = motionEvent.getY();
        int color;

        //Used by the velocity tracker
        int index = motionEvent.getActionIndex();
        int pointerId = motionEvent.getPointerId(index);

        switch(motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                //Creates a velocity tracker if it does not already exist
                if(_velocityTracker == null) {
                    _velocityTracker = VelocityTracker.obtain();
                } else {
                    //resets the velocity tracker for each swipe on the screen
                    _velocityTracker.clear();
                }
                _velocityTracker.addMovement(motionEvent);

                //Finds the corresponding pixel color
                color = getColor(touchX, touchY);
                //If -1 returns then it is out of bounds or the image is not yet loaded. In either case do nothing
                if(color == -1) {
                    break;
                }
                //If the toggle to use saturated colors is on then saturate the color before drawing
                if(_useSaturatedColors) {
                    color = saturateColors(color);
                }
                _paint.setColor(color);
                _paint.setAlpha(_alpha);

                //The first touch is always default radius in width because there is no velocity yet
                paintShape(touchX, touchY, _defaultRadius);
                invalidate();
                break;

            case MotionEvent.ACTION_MOVE:
                //Calculates velocity of finger movement if dynamic width is on
                _velocityTracker.addMovement(motionEvent);
                float velocity = _defaultRadius;
                if(_useDynamicWidth) {
                    _velocityTracker.computeCurrentVelocity(20);
                    velocity = (float) Math.sqrt(Math.pow(_velocityTracker.getXVelocity(pointerId), 2) + Math.pow(_velocityTracker.getYVelocity(pointerId), 2));
                }

                //Goes through all historical X and Y coordinates to draw shapes there too (otherwise it looks laggy)
                for(int i = 0; i < historySize; i++) {
                    float historicalX = motionEvent.getHistoricalX(i);
                    float historicalY = motionEvent.getHistoricalY(i);


                    color = getColor(historicalX, historicalY);
                    if(color == -1) {
                        continue;
                    }
                    if(_useSaturatedColors) {
                        color = saturateColors(color);
                    }

                    _paint.setColor(color);
                    _paint.setAlpha(_alpha);

                    paintShape(historicalX, historicalY, velocity);
                }

                color = getColor(touchX, touchY);
                if(color == -1) {
                    break;
                }
                if(_useSaturatedColors) {
                    color = saturateColors(color);
                }
                _paint.setColor(color);
                _paint.setAlpha(_alpha);

                paintShape(touchX, touchY, velocity);
                invalidate();
                break;
        }

        return true;
    }

    //Finds the corresponding pixel color in the source image
    private int getColor(float x, float y) {
        //If the image is not loaded, then return -1 to indicate that you should not draw
        if(_imageView == null || _imageView.getDrawable() == null) {
            return -1;
        }

        //Returns -1 if finger strays off of the canvas
        Rect bitmapPosition = getBitmapPositionInsideImageView(_imageView);
        if(x > bitmapPosition.right || x < bitmapPosition.left || y > bitmapPosition.bottom || y < bitmapPosition.top) {
            return -1;
        }

        Bitmap sourceBitmap = ((BitmapDrawable)_imageView.getDrawable()).getBitmap();

        //Scales the image to match the size of the bitmap so that it does not "zoom in" while drawing
        //Also shifts the pixel to match the bitmap position inside the image view
        float imageWidth = bitmapPosition.width();
        float imageHeight = bitmapPosition.height();
        float bitmapWidth = sourceBitmap.getWidth();
        float bitmapHeight = sourceBitmap.getHeight();

        int actualX = (int)((bitmapWidth/imageWidth)*(x-bitmapPosition.left));
        int actualY = (int)((bitmapHeight/imageHeight)*(y-bitmapPosition.top));

        return(sourceBitmap.getPixel(Math.round(actualX), Math.round(actualY)));
    }

    //Fully saturates a color to make it look more vibrant
    private int saturateColors(int color) {
        //Converts color into HSV form and sets saturation to maximum (1)
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = 1;
        return(Color.HSVToColor(hsv));
    }

    //Paints different shapes depending on the brush type
    private void paintShape (float touchX, float touchY, float velocity) {
        switch(_brushType) {
            case Circle:
                _offScreenCanvas.drawCircle(touchX, touchY, velocity, _paint);
                break;
            case Square:
                _offScreenCanvas.drawRect(touchX - velocity, touchY - velocity, touchX + velocity, touchY + velocity, _paint);
                break;
            case Line:
                _offScreenCanvas.drawLine(touchX - velocity, touchY - velocity, touchX + velocity, touchY + velocity, _paint);
                break;
        }
    }


    /**
     * This method is useful to determine the bitmap position within the Image View. It's not needed for anything else
     * Modified from:
     *  - http://stackoverflow.com/a/15538856
     *  - http://stackoverflow.com/a/26930938
     * @param imageView
     * @return
     */
    private static Rect getBitmapPositionInsideImageView(ImageView imageView){
        Rect rect = new Rect();

        if (imageView == null || imageView.getDrawable() == null) {
            return rect;
        }

        // Get image dimensions
        // Get image matrix values and place them in an array
        float[] f = new float[9];
        imageView.getImageMatrix().getValues(f);

        // Extract the scale values using the constants (if aspect ratio maintained, scaleX == scaleY)
        final float scaleX = f[Matrix.MSCALE_X];
        final float scaleY = f[Matrix.MSCALE_Y];

        // Get the drawable (could also get the bitmap behind the drawable and getWidth/getHeight)
        final Drawable d = imageView.getDrawable();
        final int origW = d.getIntrinsicWidth();
        final int origH = d.getIntrinsicHeight();

        // Calculate the actual dimensions
        final int widthActual = Math.round(origW * scaleX);
        final int heightActual = Math.round(origH * scaleY);

        // Get image position
        // We assume that the image is centered into ImageView
        int imgViewW = imageView.getWidth();
        int imgViewH = imageView.getHeight();

        int top = (int) (imgViewH - heightActual)/2;
        int left = (int) (imgViewW - widthActual)/2;

        rect.set(left, top, left + widthActual, top + heightActual);

        return rect;
    }
}

