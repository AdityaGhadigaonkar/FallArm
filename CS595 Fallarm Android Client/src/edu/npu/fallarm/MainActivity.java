/** 
* Title : Android application to detect fall and communicate with server.
* @Author: Aditya Ghadigaonkar
**/
package edu.npu.fallarm;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorEventListener {
	private SensorManager sensorManager;
	private Sensor accelerometer;
	private Sensor gyroscope; // orientation: android emulator only works with
	// orientation instead
	// private Sensor magneticfield;
	private LocationManager locationManager; // for geo location

	private TextView textPatientId; // patienId textview generate from android
	// id
	private TextView textLatitude;
	private TextView textLongitude;
	private TextView textAccelX; // accelerometer x coord textview
	private TextView textAccelY; // y
	private TextView textAccelZ; // z
	private TextView textGyroX; // gyroscope x coord textview
	private TextView textGyroY; // y
	private TextView textGyroZ; // z
	private TextView textRiskRating;
	private float oldx = 0;
	private float oldy = 0;
	private float oldz = 0;
	private boolean acceChangeFlag = false;
	private boolean geoFlag = false;
	private int riskRate = 0;
	private String android_id = "";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		//for sdk API level 9 or higher
		if (android.os.Build.VERSION.SDK_INT > 9) {
			StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
			StrictMode.setThreadPolicy(policy);
		}
		android_id = Secure.getString(this.getContentResolver(), Secure.ANDROID_ID);
		textPatientId = (TextView) findViewById(R.id.patientId);
		textPatientId.setText("Patient Id: " + android_id);
		textLatitude = (TextView) findViewById(R.id.latitude);
		textLongitude = (TextView) findViewById(R.id.longitude);
		textLatitude.setText("Latitude: 0");
		textLongitude.setText("Longitude: 0");
		textAccelX = (TextView) findViewById(R.id.accel_x);
		textAccelY = (TextView) findViewById(R.id.accel_y);
		textAccelZ = (TextView) findViewById(R.id.accel_z);
		textGyroX = (TextView) findViewById(R.id.gyro_x);
		textGyroY = (TextView) findViewById(R.id.gyro_y);
		textGyroZ = (TextView) findViewById(R.id.gyro_z);
		textAccelX.setText("X: 0");
		textAccelY.setText("Y: 0");
		textAccelZ.setText("Z: 0");
		textGyroX.setText("X: 0");
		textGyroY.setText("Y: 0");
		textGyroZ.setText("Z: 0");
		textRiskRating = (TextView) findViewById(R.id.risk_rating);
		String provider = LocationManager.GPS_PROVIDER;
		String context = Context.LOCATION_SERVICE;
		LocationManager locationManager;
		locationManager = (LocationManager) getSystemService(context);
		Location location = locationManager.getLastKnownLocation(provider);
		// hold its lat, lng data
		updateWithNewLocation(location); // display location
		// register location listener to listen changes, update every 1 sec and
		// 1 meter change
		locationManager.requestLocationUpdates(provider, 1000, 1, new MyLocationListener());
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE); // sensor
		// manager
		accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION); // Sensor.TYPE_GYROSCOPE
		// register these sensors with sensor manager
		sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI); // .SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI);
	}

	@Override
	// SensorEventListener's method
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	@Override
	// SensorEventListener's method
	public void onSensorChanged(SensorEvent event) {
		int risk = 1;
		// check sensor type for accelerometer
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			// assign values for display
			float x = event.values[0];
			float y = event.values[1];
			float z = event.values[2];
			if (oldx != x || oldy != y || oldz != z) {
				oldx = x;
				oldy = y;
				oldz = z;
				acceChangeFlag = true;
			} 
			else {
				acceChangeFlag = false;
			}
			textAccelX.setText("X: " + x);
			textAccelY.setText("Y: " + y);
			textAccelZ.setText("Z: " + z);
			// rate the risk using accelerometer on its sensor data changed
			// get this calculated data from server instead
			risk = riskRating(x, y, z);
			//risk = 1;
			System.out.println("X: " + x + "  Y: " + y + "  Z: " + z + " risk=" + risk);
		}
		// check sensor type for orientation (gyroscope)
		if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) { // Sensor.TYPE_GYROSCOPE
			float x = event.values[0];
			float y = event.values[1];
			float z = event.values[2];

			textGyroX.setText("X: " + x); // set text for orientation gyroscope
			textGyroY.setText("Y: " + y);
			textGyroZ.setText("Z: " + z);
		}
		// now we can send high risk sensor data to pc server
		if (risk > 3 && acceChangeFlag && geoFlag) {
			Log.i("filter", "sending sensor data over network");
			sendSensorDataOverNetwork();
		}
	}
	// use socket to send sensor accelerometer and gyroscope data to the PC
	// server
	public void sendSensorDataOverNetwork() {
		Socket socket = null;
		DataOutputStream dataOutputStream = null; // for sending data over to server
		DataInputStream dataInputStream = null; // for receiving data from server
		try {
			socket = new Socket("172.19.9.230", 8081);
			dataOutputStream = new DataOutputStream(socket.getOutputStream());
			dataInputStream = new DataInputStream(socket.getInputStream());
			// build string, use this data text to carry over to server
			String acceleroData = "Accelerometer " + textAccelX.getText().toString() + " "
					+ textAccelY.getText().toString() + " " + textAccelZ.getText().toString();
			String gyroData = "Gyroscope " + textGyroX.getText().toString() + " " + textGyroY.getText().toString()
					+ " " + textGyroZ.getText().toString();
			String locationData = "Location " + textLatitude.getText().toString() + " "
					+ textLongitude.getText().toString();
			dataOutputStream.writeUTF(android_id);
			dataOutputStream.writeUTF(acceleroData); // write string to server
			dataOutputStream.writeUTF(gyroData);
			dataOutputStream.writeUTF(locationData); // also send geo data
			Log.i("filter", "Sensor Data is sent over the network!");
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally { // then close socket and input/output stream
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (dataOutputStream != null) {
				try {
					dataOutputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (dataInputStream != null) {
				try {
					dataInputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	// helper method for FallArm project, do risk rating based on accelerometer
	// data
	// 1 being highest risk, 5 is lowest risk
	public int riskRating(float x, float y, float z) {
		// gforce = sqrt of vector sum
		float gforce = Math.round(Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2)));
		Log.i("filter", "G-Force: " + gforce);
		int riskRate;
		if (gforce >= 9) // && vectorSum <= 10 )
			riskRate = 1;
		else if (gforce >= 7 && gforce <= 8)
			riskRate = 2;
		else if (gforce >= 4 && gforce <= 6)
			riskRate = 3;
		else if (gforce >= 1 && gforce <= 3)
			riskRate = 4;
		else
			// gforce 0
			riskRate = 5; // lowest risk
		textRiskRating.setText("Risk Rate: " + riskRate);
		return riskRate;
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.i("filter", "Application on resume");
		// register this class as a listener for sensors
		sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.i("filter", "Application on pause");
		// unregister listener for all sensors
		sensorManager.unregisterListener(this);
	}

	// my reusable location listener class
	private class MyLocationListener implements LocationListener {
		@Override
		public void onLocationChanged(Location location) {
			updateWithNewLocation(location);
		}

		@Override
		public void onProviderDisabled(String provider) {
			updateWithNewLocation(null);
		}

		@Override
		public void onProviderEnabled(String provider) {
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
		}
	};

	// helper method for displaying changed geo location
	private void updateWithNewLocation(Location location) {
		String latLongString;
		String addressString = "No address found";
		if (location != null) {
			double latitude = location.getLatitude();
			double longitude = location.getLongitude();
			geoFlag = true;
			textLatitude.setText("Latitude: " + latitude);
			textLongitude.setText("Longitude: " + longitude);
			Geocoder gc = new Geocoder(this, Locale.getDefault());
			try {
				// will do a network lookup on its location for address
				List<Address> addresses = gc.getFromLocation(latitude, longitude, 1);
				StringBuilder sb = new StringBuilder();
				if (addresses.size() > 0) {
					Address address = addresses.get(0);
					for (int i = 0; i < address.getMaxAddressLineIndex(); i++) {
						sb.append(address.getAddressLine(i)).append("\n");
					}

					sb.append(address.getLocality()).append("\n");
					sb.append(address.getPostalCode()).append("\n");
					sb.append(address.getCountryName());
				}
				addressString = sb.toString();
			} catch (IOException e) {
				// probably network is down
			}
		} else {
			latLongString = "No location found";
		}
		// update the text for display
	}
}
