package com.example.brownmagik352.pedometertest;

/*

Step activity detection based on Mladenov & Mock 2009.
This implementation makes the following assumptions:
Position of device: left hand, shoulder height, in front of left shoulder
Walking: forward direction, continuous, deliberate pace (no more than 1 step per 100ms)

This heavily borrows and combines pieces from the sample code posted at https://github.com/jonfroehlich/CSE590Sp2018.
 */

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // accelerometer stuff
    private SensorManager _sensorManager;
    private Sensor _accelSensor;
    private Sensor _stepSensor;
    private float _rawAccelValues[] = new float[3];

    // graphview stuff
    private LineGraphSeries<DataPoint> _rawMagnitude;
    private LineGraphSeries<DataPoint> _smoothMagnitude;
    private double graphLastXValue = 0.0;
    private static int GRAPH_MIN_X = 0;
    private static int GRAPH_MAX_X = 50;
        // setting these based on observation
    private static int GRAPH_MIN_Y = 5;
    private static int GRAPH_MAX_Y = 15;

    // smoothing accelerometer signal stuff
    private static int MAX_ACCEL_VALUE = 30;
    private static int SMOOTHING_WINDOW_SIZE = 20;
    private float _accelValueHistory[][] = new float[3][SMOOTHING_WINDOW_SIZE];
    private float _runningAccelTotal[] = new float[3];
    private float _curAccelAvg[] = new float[3];
    private int _curReadIndex = 0;

    // mladenovStepDetectionAlgorithm
    public static int _totalSteps = 0;
    private static float CONSTANT_C = 0.8f;
    private static float CONSTANT_K = 10.1f;
        // reduce noise in early steps
    private static int EARLY_STEPS = 2;
    private static float CONSTANT_K_early = 10.3f;
    private static int CHUNKING_SIZE = 10;
    private int _currentChunkPosition = 0;
    private float _smoothMagnitudeValues[] = new float[CHUNKING_SIZE];

    // internal steps
    private float _internalStepsInitial = -1;


/*
// Variables for requiesting permissions, API 25+ (test only)
private int requestCode;
private int grantResults[];
*/

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
        _rawMagnitude = new LineGraphSeries<>(new DataPoint[] {
                new DataPoint(0.0,0.0)
        });
        graphRaw.addSeries(_rawMagnitude);
        graphRaw.getViewport().setXAxisBoundsManual(true);
        graphRaw.getViewport().setYAxisBoundsManual(true);
        graphRaw.getViewport().setMinX(GRAPH_MIN_X);
        graphRaw.getViewport().setMaxX(GRAPH_MAX_X);
        graphRaw.getViewport().setMinY(GRAPH_MIN_Y);
        graphRaw.getViewport().setMaxY(GRAPH_MAX_Y);

        // smooth graph initialization
        GraphView graphSmooth = (GraphView) findViewById(R.id.graphSmooth);
        _smoothMagnitude = new LineGraphSeries<>(new DataPoint[] {
                new DataPoint(0.0,0.0)
        });
        graphSmooth.addSeries(_smoothMagnitude);
        graphSmooth.getViewport().setXAxisBoundsManual(true);
        graphSmooth.getViewport().setYAxisBoundsManual(true);
        graphSmooth.getViewport().setMinX(GRAPH_MIN_X);
        graphSmooth.getViewport().setMaxX(GRAPH_MAX_X);
        graphSmooth.getViewport().setMinY(GRAPH_MIN_Y);
        graphSmooth.getViewport().setMaxY(GRAPH_MAX_Y);

        final Button debugToggle = (Button)findViewById(R.id.debug_toggle);
        debugToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDebug();
            }
        });
/*

// request permissions for logging data (test only)
if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED ) {
//if you dont have required permissions ask for it (only required for API 23+)
ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);


onRequestPermissionsResult(requestCode, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, grantResults);
}
*/
    }

//    // (test only)
//    @Override // android recommended class to handle permissions
//    public void onRequestPermissionsResult(int requestCode,
//                                           String permissions[], int[] grantResults) {
//        switch (requestCode) {
//            case 1: {
//
//                // If request is cancelled, the result arrays are empty.
//                if (grantResults.length > 0
//                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//
//                    Log.d("permission", "granted");
//                } else {
//
//                    // permission denied, boo! Disable the
//                    // functionality that depends on this permission.uujm
//                    Toast.makeText(MainActivity.this, "Permission denied to read your External storage", Toast.LENGTH_SHORT).show();
//
//                    //app cannot function without this permission for now so close it...
//                    onDestroy();
//                }
//                return;
//            }
//
//            // other 'case' line to check fosr other
//            // permissions this app might request
//        }
//    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch(sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:

                // pull raw values
                _rawAccelValues[0] = sensorEvent.values[0];
                _rawAccelValues[1] = sensorEvent.values[1];
                _rawAccelValues[2] = sensorEvent.values[2];

                smoothSignal();
                float rawMagnitudeValue = findMagnitude(_rawAccelValues[0], _rawAccelValues[1], _rawAccelValues[2]);
                float smoothMagnitudeValue = findMagnitude(_curAccelAvg[0], _curAccelAvg[1], _curAccelAvg[2]);
                updateDebugViz(rawMagnitudeValue, smoothMagnitudeValue);
                updateRunningMagnitudesValues(smoothMagnitudeValue);
                updateAlgoStepsView();
                break;
            case Sensor.TYPE_STEP_COUNTER:

                if (_internalStepsInitial < 0) {
                    _internalStepsInitial = sensorEvent.values[0];
                }

                updateInternalStepView(sensorEvent.values[0] - _internalStepsInitial);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private float findMagnitude(float x, float y, float z) {
        return (float) Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));
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

    private void updateDebugViz(float raw, float smooth) {
        // graph updates
        graphLastXValue += 1d;
        _rawMagnitude.appendData(new DataPoint(graphLastXValue, raw), true, GRAPH_MAX_X);
        _smoothMagnitude.appendData(new DataPoint(graphLastXValue, smooth), true, GRAPH_MAX_X);

        // debug text information
        TextView debugText = (TextView) findViewById(R.id.debugText);
        debugText.setText(String.format("Raw Magnitude: %f\nSmooth Magnitude: %f", raw, smooth));

/*
// logging data (test only)
File root = new File(Environment.getExternalStorageDirectory().toString() + "/pedometerTestFiles/");
File csvFile = new File(root, "test.csv");
try {
FileWriter writer = new FileWriter(csvFile, true);
writer.append(Float.toString(smoothMagnitudeValue) + ",");
writer.flush();
writer.close();
} catch (Exception e) {
System.out.println(e);
}
*/
    }

    private void updateRunningMagnitudesValues(float recentMagnitudeValue) {
        _smoothMagnitudeValues[_currentChunkPosition] = recentMagnitudeValue;
        if (_currentChunkPosition == CHUNKING_SIZE - 1) {
            mladenovStepDetectionAlgorithm(_smoothMagnitudeValues);
            _currentChunkPosition = 0;
        } else {
            _currentChunkPosition++;
        }
    }

    private void mladenovStepDetectionAlgorithm(float magnitudes[]) {

        // Part 1: peak detection & setting threshold
        int peakCount = 0;
        float peakAccumulate = 0f;
        // loop safety variables (1 and CHUNKING_SIZE - 1) given +1 and -1 uses with indexes
        for (int i = 1; i < CHUNKING_SIZE - 1; i++) {
            float forwardSlope = magnitudes[i + 1] - magnitudes[i];
            float backwardSlope = magnitudes[i] - magnitudes[i - 1];
            if (forwardSlope < 0 && backwardSlope > 0) {
                peakCount += 1;
                peakAccumulate += magnitudes[i];
            }
        }
        float peakMean = peakAccumulate / peakCount;

        // Part 2: same peaks with thresholds applied
        int stepCount = 0;
        for (int i = 1; i < CHUNKING_SIZE - 1; i++) {
            float forwardSlope = magnitudes[i + 1] - magnitudes[i];
            float backwardSlope = magnitudes[i] - magnitudes[i - 1];
            if (forwardSlope < 0 && backwardSlope > 0
                    && magnitudes[i] > CONSTANT_C * peakMean ) {
                if ((_totalSteps <= EARLY_STEPS && magnitudes[i] > CONSTANT_K_early) ||
                        (_totalSteps > EARLY_STEPS && magnitudes[i] > CONSTANT_K )) {
                    stepCount += 1;
                }
            }
        }

        // update total steps (across chunks)
        _totalSteps += stepCount;
    }

    private void updateAlgoStepsView() {
        // debug
        TextView algoCounterView = (TextView) findViewById(R.id.algo_counter_view);
        algoCounterView.setText(String.format("Algo Step Counter: %d steps", _totalSteps));

        // user-facing
        TextView totalStepsView = (TextView) findViewById(R.id.total_steps_view);
        totalStepsView.setText(String.format("Total Steps: %d steps", _totalSteps));
    }

    private void updateInternalStepView(float currentInternalSteps) {
        TextView stepCounterView = (TextView) findViewById(R.id.internal_steps_view);
        stepCounterView.setText(String.format("Android Step Counter: %.0f steps", currentInternalSteps));
    }

    private void toggleDebug() {
        TextView debugText = (TextView) findViewById(R.id.debugText);
        TextView internalStepsView = (TextView) findViewById(R.id.internal_steps_view);
        TextView algoStepsView = (TextView) findViewById(R.id.algo_counter_view);
        GraphView graphRaw = (GraphView) findViewById(R.id.graphRaw);
        GraphView graphSmooth = (GraphView) findViewById(R.id.graphSmooth);

        // assuming if one if visible, all are
        if (debugText.getVisibility() == View.VISIBLE) {
            debugText.setVisibility(View.INVISIBLE);
            internalStepsView.setVisibility(View.INVISIBLE);
            algoStepsView.setVisibility(View.INVISIBLE);
            graphRaw.setVisibility(View.INVISIBLE);
            graphSmooth.setVisibility(View.INVISIBLE);
        } else {
            debugText.setVisibility(View.VISIBLE);
            internalStepsView.setVisibility(View.VISIBLE);
            algoStepsView.setVisibility(View.VISIBLE);
            graphRaw.setVisibility(View.VISIBLE);
            graphSmooth.setVisibility(View.VISIBLE);
        }
    }
}
