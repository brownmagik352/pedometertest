package com.example.brownmagik352.pedometertest;

/*
This heavily borrows and combines pieces from the sample code posted at https://github.com/jonfroehlich/CSE590Sp2018
 */

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
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

    // PEAK Detection
    // zero-counting based on observed data
    private int _totalSteps = 0;
    private boolean _stepPeakActive = false;
    private static float PEAK_THRESHOLD = 10.1f;

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
                peakDetect(smoothMagnitudeValue);
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

    private void peakDetect(float magnitude) {
        /*
        if above threshold but not active
            set active
        if below threshold and active
            set inactive
            increment steps
            update view
        if above threshold and active
        if below threshold and not active
            do nothing
         */

        if (magnitude > PEAK_THRESHOLD && !_stepPeakActive) {
            _stepPeakActive = true;
        } else if (magnitude < PEAK_THRESHOLD && _stepPeakActive) {
            _stepPeakActive = false;
            _totalSteps++;
            updateAlgoStepsView(_totalSteps);
        }
    }

    private void updateAlgoStepsView(int totalSteps) {
        TextView algoCounterView = (TextView) findViewById(R.id.algo_counter_view);
        algoCounterView.setText(String.format("Algo Step Counter: %d steps", totalSteps));
    }

    private void updateInternalStepView(float currentInternalSteps) {
        TextView stepCounterView = (TextView) findViewById(R.id.internal_steps_view);
        stepCounterView.setText(String.format("Android Step Counter: %.0f steps", currentInternalSteps));
    }
}
