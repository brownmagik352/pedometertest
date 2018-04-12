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
    private int _newStepCount = MainActivity._totalSteps;
    private int _lastStepCount = 0;

    // screen width initialization
    private final int SCREEN_WIDTH = getScreenWidth();
    private final float STEP_SIZE = SCREEN_WIDTH/10.0f;

    // setup paint and path objects ahead of time (stick figure)
    private Paint _paintStickFigure = new Paint();
    private Path _stickFigure = makeStickFigure();
    private float _stickFigurePositionFrontFoot = 400; // 300 + 100
    private static final float STICK_FIGURE_HEIGHT = 500f;
    private static final float STICK_FIGURE_WIDTH = 200f;
    private static final float STICK_FIGURE_START = 0f;

    // setup goal & ground paint objects
    private Paint _paintGoal = new Paint();
    private float _goalLocation = SCREEN_WIDTH - 300;
    private Paint _paintGround = new Paint();

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

        // draw stick figure
        _paintStickFigure.setStyle(Paint.Style.STROKE);
        canvas.drawPath(_stickFigure, _paintStickFigure);

        // try goal message
        canvas.drawText("Life Goals", _goalLocation,STICK_FIGURE_HEIGHT*0.4f, _paintGoal);

        // draw "ground" for stick figure
        canvas.drawLine(STICK_FIGURE_START, STICK_FIGURE_HEIGHT, SCREEN_WIDTH, STICK_FIGURE_HEIGHT, _paintGround);

        // poll for updates in steps
        new CountDownTimer(100, 100) {

            public void onFinish() {
                // update step counts
                _newStepCount = MainActivity._totalSteps;
                int stepDeltaSinceLastPoll = _newStepCount - _lastStepCount;
                _lastStepCount = _newStepCount;

                // update stick figure position
                if (_stickFigurePositionFrontFoot > SCREEN_WIDTH) {
                    // bring stick figure back to beginning
                    _stickFigurePositionFrontFoot -= SCREEN_WIDTH;
                    _stickFigure.offset((SCREEN_WIDTH * -1), 0);
                    _goalLocation = SCREEN_WIDTH - 100; // goal only shows at beginning
                } else {
                    // move stick figure forward
                    _stickFigure.offset(STEP_SIZE * stepDeltaSinceLastPoll, 0);
                    _stickFigurePositionFrontFoot += STEP_SIZE * stepDeltaSinceLastPoll;
                    _goalLocation =  SCREEN_WIDTH + 100; // offscreen
                }

                // redraw with new information
                invalidate();
            }

            public void onTick(long millisUntilFinished) {

            }
        }.start();
    }

    // make custom shape (stick figure)
    private Path makeStickFigure() {

        Path path = new Path();

        path.moveTo(STICK_FIGURE_START, STICK_FIGURE_HEIGHT); // back foot
        path.lineTo(STICK_FIGURE_START + STICK_FIGURE_WIDTH*0.5f,STICK_FIGURE_HEIGHT*0.7f); // hip
        path.lineTo(STICK_FIGURE_START + STICK_FIGURE_WIDTH,STICK_FIGURE_HEIGHT); // front foot
        path.moveTo(STICK_FIGURE_START + STICK_FIGURE_WIDTH*0.5f,STICK_FIGURE_HEIGHT*0.7f); // hip
        path.lineTo(STICK_FIGURE_START + STICK_FIGURE_WIDTH*0.5f,STICK_FIGURE_HEIGHT*0.4f); // chin
        path.moveTo(STICK_FIGURE_START + STICK_FIGURE_WIDTH*0.25f,STICK_FIGURE_HEIGHT*0.5f); // arm start
        path.lineTo(STICK_FIGURE_START + STICK_FIGURE_WIDTH*0.75f, STICK_FIGURE_HEIGHT*0.5f); // arm end
        path.addCircle(STICK_FIGURE_START + STICK_FIGURE_WIDTH*0.5f,STICK_FIGURE_HEIGHT*0.3f,STICK_FIGURE_HEIGHT*0.1f, Path.Direction.CW); // head

        return path;
    }

    // get screen width
    private static int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

}
