package com.example.brownmagik352.pedometertest;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.Random;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // accelerometer stuff
    private SensorManager _sensorManager;
    private Sensor _accelSensor;
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // See https://developer.android.com/guide/topics/sensors/sensors_motion.html
        _sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        _accelSensor = _sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // The official Google accelerometer example code found here:
        //   https://github.com/android/platform_development/blob/master/samples/AccelerometerPlay/src/com/example/android/accelerometerplay/AccelerometerPlayActivity.java
        // explains that it is not necessary to get accelerometer events at a very high rate, by using a slower rate (SENSOR_DELAY_UI), we get an
        // automatic low-pass filter, which "extracts" the gravity component of the acceleration. As an added benefit, we use less power and
        // CPU resources. I haven't experimented with this, so can't be sure.
        // See also: https://developer.android.com/reference/android/hardware/SensorManager.html#SENSOR_DELAY_UI
        _sensorManager.registerListener(this, _accelSensor, SensorManager.SENSOR_DELAY_GAME);

        // raw graph
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

        // smooth graph
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
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch(sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:

                // pull raw values
                _rawAccelValues[0] = sensorEvent.values[0];
                _rawAccelValues[1] = sensorEvent.values[1];
                _rawAccelValues[2] = sensorEvent.values[2];

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

                // graph updates
                graphLastXValue += 1d;
                _rawX.appendData(new DataPoint(graphLastXValue, _rawAccelValues[0]), true, GRAPH_MAX_X);
                _rawY.appendData(new DataPoint(graphLastXValue, _rawAccelValues[1]), true, GRAPH_MAX_X);
                _rawZ.appendData(new DataPoint(graphLastXValue, _rawAccelValues[2]), true, GRAPH_MAX_X);
                _smoothX.appendData(new DataPoint(graphLastXValue, _curAccelAvg[0]), true, GRAPH_MAX_X);
                _smoothY.appendData(new DataPoint(graphLastXValue, _curAccelAvg[1]), true, GRAPH_MAX_X);
                _smoothZ.appendData(new DataPoint(graphLastXValue, _curAccelAvg[2]), true, GRAPH_MAX_X);

                // debug visualization
                TextView rawX = (TextView) findViewById(R.id.rawX);
                TextView rawY = (TextView) findViewById(R.id.rawY);
                TextView rawZ = (TextView) findViewById(R.id.rawZ);
                rawX.setText(String.format("rawX: %f\t\t\tsmoothX: %f",  _rawAccelValues[0], _curAccelAvg[0]));
                rawY.setText(String.format("rawY: %f\t\t\tsmoothY: %f",  _rawAccelValues[1], _curAccelAvg[1]));
                rawZ.setText(String.format("rawZ: %f\t\t\tsmoothZ: %f",  _rawAccelValues[2], _curAccelAvg[2]));


        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
