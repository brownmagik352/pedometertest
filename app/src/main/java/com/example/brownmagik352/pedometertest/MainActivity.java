package com.example.brownmagik352.pedometertest;

/*
This heavily borrows and combines pieces from the sample code posted at https://github.com/jonfroehlich/CSE590Sp2018
 */

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // accelerometer stuff
    private SensorManager _sensorManager;
    private Sensor _accelSensor;
    private Sensor _stepSensor;
    private float _rawAccelValues[] = new float[3];

    // graphview stuff
    private LineGraphSeries<DataPoint> _rawX;
    private LineGraphSeries<DataPoint> _rawY;
    private LineGraphSeries<DataPoint> _rawZ;
    private LineGraphSeries<DataPoint> _smoothX;
    private LineGraphSeries<DataPoint> _smoothY;
    private LineGraphSeries<DataPoint> _smoothZ;
    private double graphLastXValue = 0.0;
    private static int GRAPH_MAX_X = 50;

    // smoothing accelerometer signal stuff
    private static int MAX_ACCEL_VALUE = 30;
    private static int SMOOTHING_WINDOW_SIZE = 20;
    private float _accelValueHistory[][] = new float[3][SMOOTHING_WINDOW_SIZE];
    private float _runningAccelTotal[] = new float[3];
    private float _curAccelAvg[] = new float[3];
    private int _curReadIndex = 0;

    // step counting states (algo: x is negative when left is up, positive when right is up)
    private int _totalLeft = 0;
    private int _totalRight = 0;
    private boolean _leftInProgress = false;
    private boolean _rightInProgress = false; 
    private float LEFT_PEAK = -0.5f;
    private float RIGHT_PEAK = 0.5f;

    // internal steps
    private float internalStepsInitial = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // See https://developer.android.com/guide/topics/sensors/sensors_motion.html
        _sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        _accelSensor = _sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        _stepSensor = _sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        // The official Google accelerometer example code found here:
        //   https://github.com/android/platform_development/blob/master/samples/AccelerometerPlay/src/com/example/android/accelerometerplay/AccelerometerPlayActivity.java
        // explains that it is not necessary to get accelerometer events at a very high rate, by using a slower rate (SENSOR_DELAY_UI), we get an
        // automatic low-pass filter, which "extracts" the gravity component of the acceleration. As an added benefit, we use less power and
        // CPU resources. I haven't experimented with this, so can't be sure.
        // See also: https://developer.android.com/reference/android/hardware/SensorManager.html#SENSOR_DELAY_UI
        _sensorManager.registerListener(this, _accelSensor, SensorManager.SENSOR_DELAY_GAME);
        _sensorManager.registerListener(this, _stepSensor, SensorManager.SENSOR_DELAY_GAME);

        // raw graph initialization
        GraphView graphRaw = (GraphView) findViewById(R.id.graphRaw);
        _rawX = new LineGraphSeries<>(new DataPoint[] {
                new DataPoint(0.0,0.0)
        });
        _rawY = new LineGraphSeries<>(new DataPoint[] {
                new DataPoint(0.0,0.0)
        });
        _rawZ = new LineGraphSeries<>(new DataPoint[] {
                new DataPoint(0.0,0.0)
        });
        graphRaw.addSeries(_rawX);
        graphRaw.addSeries(_rawY);
        graphRaw.addSeries(_rawZ);
        graphRaw.getViewport().setXAxisBoundsManual(true);
        graphRaw.getViewport().setMinX(0);
        graphRaw.getViewport().setMaxX(GRAPH_MAX_X);

        // smooth graph initialization
        GraphView graphSmooth = (GraphView) findViewById(R.id.graphSmooth);
        _smoothX = new LineGraphSeries<>(new DataPoint[] {
                new DataPoint(0.0,0.0)
        });
        _smoothY = new LineGraphSeries<>(new DataPoint[] {
                new DataPoint(0.0,0.0)
        });
        _smoothZ = new LineGraphSeries<>(new DataPoint[] {
                new DataPoint(0.0,0.0)
        });
        graphSmooth.addSeries(_smoothX);
        graphSmooth.addSeries(_smoothY);
        graphSmooth.addSeries(_smoothZ);
        graphSmooth.getViewport().setXAxisBoundsManual(true);
        graphSmooth.getViewport().setMinX(0);
        graphSmooth.getViewport().setMaxX(GRAPH_MAX_X);

//        // labels & titles
//        _smoothX.setTitle("X");
//        _smoothY.setTitle("Y");
//        _smoothZ.setTitle("Z");
//        graphSmooth.getLegendRenderer().setVisible(true);
//        graphSmooth.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch(sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:

                // pull raw values
                _rawAccelValues[0] = sensorEvent.values[0];
                _rawAccelValues[1] = sensorEvent.values[1];
                _rawAccelValues[2] = sensorEvent.values[2];

                smoothSignal();
                updateGraphs();
                updateDebugViz();
                peakDetect();
                updateAlgoSteps();
                break;
            case Sensor.TYPE_STEP_COUNTER:

                if (internalStepsInitial < 0) {
                    internalStepsInitial = sensorEvent.values[0];
                }

                updateInternalStepView(sensorEvent.values[0] - internalStepsInitial);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void smoothSignal() {
        // Smoothing algorithm adapted from: https://www.arduino.cc/en/Tutorial/Smoothing
        for (int i = 0; i < 3; i++) {
            _runningAccelTotal[i] = _runningAccelTotal[i] - _accelValueHistory[i][_curReadIndex];
            _accelValueHistory[i][_curReadIndex] = _rawAccelValues[i];
            _runningAccelTotal[i] = _runningAccelTotal[i] + _accelValueHistory[i][_curReadIndex];
            _curAccelAvg[i] = _runningAccelTotal[i] / SMOOTHING_WINDOW_SIZE;
        }

        _curReadIndex++;
        if(_curReadIndex >= SMOOTHING_WINDOW_SIZE){
            _curReadIndex = 0;
        }
    }

    private void updateGraphs() {
        // graph updates
        graphLastXValue += 1d;
        _rawX.appendData(new DataPoint(graphLastXValue, _rawAccelValues[0]), true, GRAPH_MAX_X);
        _rawY.appendData(new DataPoint(graphLastXValue, _rawAccelValues[1]), true, GRAPH_MAX_X);
        _rawZ.appendData(new DataPoint(graphLastXValue, _rawAccelValues[2]), true, GRAPH_MAX_X);
        _smoothX.appendData(new DataPoint(graphLastXValue, _curAccelAvg[0]), true, GRAPH_MAX_X);
        _smoothY.appendData(new DataPoint(graphLastXValue, _curAccelAvg[1]), true, GRAPH_MAX_X);
        _smoothZ.appendData(new DataPoint(graphLastXValue, _curAccelAvg[2]), true, GRAPH_MAX_X);
    }

    private void updateDebugViz() {
        // debug visualization
        TextView debugX = (TextView) findViewById(R.id.debugX);
        TextView debugY = (TextView) findViewById(R.id.debugY);
        TextView debugZ = (TextView) findViewById(R.id.debugZ);
        debugX.setText(String.format("rawX: %f\t\t\tsmoothX: %f",  _rawAccelValues[0], _curAccelAvg[0]));
        debugY.setText(String.format("rawY: %f\t\t\tsmoothY: %f",  _rawAccelValues[1], _curAccelAvg[1]));
        debugZ.setText(String.format("rawZ: %f\t\t\tsmoothZ: %f",  _rawAccelValues[2], _curAccelAvg[2]));
    }

    private void peakDetect() {
        float xVal = _curAccelAvg[0];
        if (xVal < LEFT_PEAK && !_leftInProgress) {
            _totalLeft += 1;
            _leftInProgress = true;
        } else if (xVal < LEFT_PEAK && _leftInProgress) {
            _leftInProgress = true;
        } else if (xVal > RIGHT_PEAK && !_rightInProgress) {
            _totalRight += 1;
            _rightInProgress = true;
            // make active
        } else if (xVal > RIGHT_PEAK && _rightInProgress){
            _rightInProgress = true;
        } else {
            _leftInProgress = false;
            _rightInProgress = false;
        }
    }

    private void updateAlgoSteps() {
        TextView algoCounterView = (TextView) findViewById(R.id.algo_counter_view);
        algoCounterView.setText(String.format("Algo Step Counter: %d steps", _totalLeft + _totalRight));
    }

    private void updateInternalStepView(float currentInternalSteps) {
        TextView stepCounterView = (TextView) findViewById(R.id.internal_steps_view);
        stepCounterView.setText(String.format("Android Step Counter: %.0f steps", currentInternalSteps));
    }
}
