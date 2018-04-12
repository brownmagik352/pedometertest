package com.example.brownmagik352.pedometertest;

import android.view.View;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.CountDownTimer;
import android.support.annotation.Nullable;
import android.util.AttributeSet;

public class FeedbackView extends View {

    // private variable for XPOS on screen
    private float _xPos = MainActivity._totalSteps;

    // screen width initialization
    private final int SCREEN_WIDTH = getScreenWidth();

    // setup paint and path ahead of time (person)
    private Paint _testPaint = new Paint();
    private Path _testPath = makeCustomShape();
    private float _testPathMaxX = 400; // 300 + 100

    // goal
    private Paint _testPaint2 = new Paint();
    private float _goalTextMaxX = SCREEN_WIDTH - 300;
    private Paint _testPaint3 = new Paint();

    public FeedbackView(Context context) {
        super(context);
    }

    public FeedbackView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FeedbackView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
//        canvas.drawCircle(_xPos, 100, 50, _testPaint);

        _testPaint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(_testPath, _testPaint);

        // goal circle
        canvas.drawText("Life Goals", _goalTextMaxX,200, _testPaint2);

        canvas.drawLine(0, 500, SCREEN_WIDTH, 500, _testPaint3);

        new CountDownTimer(2000, 1000) {
            public void onFinish() {

                if (_testPathMaxX > SCREEN_WIDTH) {
                    _testPathMaxX -= SCREEN_WIDTH;
                    _testPath.offset((SCREEN_WIDTH * -1), 0);
                    _goalTextMaxX = SCREEN_WIDTH - 100;
                } else {
                    _testPath.offset(300, 0);
                    _testPathMaxX += 300;
                    _goalTextMaxX =  SCREEN_WIDTH + 100; // offscreen
                }
                invalidate();
            }

            public void onTick(long millisUntilFinished) {

            }
        }.start();
    }

    // make custome shape
    private Path makeCustomShape() {

        Path path = new Path();

        path.moveTo(100, 500);
        path.lineTo(200,350);
        path.lineTo(300,500);
        path.moveTo(200,350);
        path.lineTo(200,200);
        path.moveTo(150,250);
        path.lineTo(250, 250);
        path.addCircle(200,150,50, Path.Direction.CW);

        return path;
    }

    // get screen width
    private static int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

}
