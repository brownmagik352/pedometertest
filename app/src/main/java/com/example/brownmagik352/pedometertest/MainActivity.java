package com.example.brownmagik352.pedometertest;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // accelerometer stuff
    private SensorManager _sensorManager;
    private Sensor _accelSensor;
    private float _rawAccelValues[] = new float[3];

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
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch(sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                _rawAccelValues[0] = sensorEvent.values[0];
                _rawAccelValues[1] = sensorEvent.values[1];
                _rawAccelValues[2] = sensorEvent.values[2];


//                Log.i("RAW0", Float.toString(_rawAccelValues[0]));
//                Log.i("RAW1", Float.toString(_rawAccelValues[1]));
//                Log.i("RAW2", Float.toString(_rawAccelValues[2]));

                TextView rawX = (TextView) findViewById(R.id.rawX);
                TextView rawY = (TextView) findViewById(R.id.rawY);
                TextView rawZ = (TextView) findViewById(R.id.rawZ);
                rawX.setText(String.format("rawX: %f",  _rawAccelValues[0]));
                rawY.setText(String.format("rawY: %f",  _rawAccelValues[1]));
                rawZ.setText(String.format("rawZ: %f",  _rawAccelValues[2]));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
