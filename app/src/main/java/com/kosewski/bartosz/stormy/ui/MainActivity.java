package com.kosewski.bartosz.stormy.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.kosewski.bartosz.stormy.weather.Day;
import com.kosewski.bartosz.stormy.weather.Forecast;
import com.kosewski.bartosz.stormy.weather.Hour;
import com.kosewski.bartosz.stormy.weather.LocationProvider;
import com.kosewski.bartosz.stormy.R;
import com.kosewski.bartosz.stormy.weather.Current;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements LocationProvider.LocationCallback {

    public static final String TAG = MainActivity.class.getSimpleName();
    public static final String DAILY_FORECAST ="DAILY_FORECAST";
    public static final String LOCATION ="LOCATION";
    public static final String HOURLY_FORECAST = "HOURLY_FORECAST";
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;

    private Forecast mForecast;
    private LocationProvider mLocationProvider;
    private double mLatitude;
    private double mLongitude;
    private String mLocationName;

    @Bind(R.id.timeText) TextView mTimeLabel;
    @Bind(R.id.temperatureLabel) TextView mTemperatureLabel;
    @Bind(R.id.humidityValue) TextView mHumidityValue;
    @Bind(R.id.precipValue) TextView mPrecipValue;
    @Bind(R.id.summaryLabel) TextView mSummaryLabel;
    @Bind(R.id.iconImageView) ImageView mIconImageView;
    @Bind(R.id.refreshButton) ImageButton mRefreshButton;
    @Bind(R.id.progressBar) ProgressBar mProgressBar;
    @Bind(R.id.locationLabel) TextView mLocationLabel;
    @Bind(R.id.permisonLabel) TextView mPermisionLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GetLocationPermision();
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mProgressBar.setVisibility(View.INVISIBLE);
        mProgressBar.getIndeterminateDrawable().setColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);

        mLocationProvider = new LocationProvider(this, this);

        Log.i(TAG, "Latitude: " + mLatitude + " Longitude: " + mLongitude);


        mRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getForecast(mLatitude, mLongitude);
            }
        });

    }


    @Override
    protected void onStart() {
        super.onStart();


    }

    @Override
    protected void onPause() {
        super.onPause();
        mLocationProvider.disconnect();
    }


    private void getForecast(double latitude, double longitude) {
        String apiKey = "c3958c6e9969288ccb4f1ad44cd81804";

        String forecastUrl = "https://api.forecast.io/forecast/" + apiKey + "/" + latitude + "," + longitude;

        if(isNetworkAvailable()) {
            OkHttpClient client = new OkHttpClient();

            toggleRefresh();

            Request request = new Request.Builder()
                    .url(forecastUrl)
                    .build();

            Call call = client.newCall(request);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toggleRefresh();
                        }
                    });
                    alertUserAboutError();
                }

                @Override
                public void onResponse(Response response) throws IOException {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toggleRefresh();
                        }
                    });
                    try {
                        String jsonData = response.body().string();
                        Log.v(TAG, jsonData);
                        if (response.isSuccessful()) {
                            mForecast = parseForecastDetails(jsonData);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    displayUpdate();
                                }
                            });
                        } else {
                            alertUserAboutError();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Exception caught: ", e);

                    } catch (JSONException e) {
                        Log.e(TAG, "Exception caught: ", e);
                    }
                }
            });

        } else {
            Toast.makeText(MainActivity.this, "Network is unavailable", Toast.LENGTH_LONG).show();
        }
    }

    private void toggleRefresh(){
        if (mProgressBar.getVisibility() == View.INVISIBLE){
            mProgressBar.setVisibility(View.VISIBLE);
            mRefreshButton.setVisibility(View.INVISIBLE);
        } else {
            mProgressBar.setVisibility(View.INVISIBLE);
            mRefreshButton.setVisibility(View.VISIBLE);
        }
    }

    private void displayUpdate() {
        Current current = mForecast.getCurrent();

        mTemperatureLabel.setText(current.getTemperature() + "");
        mTimeLabel.setText("At " + current.getFormattedTime() + " there will be");
        mHumidityValue.setText(current.getHumidity() + "");
        mPrecipValue.setText(current.getPrecipChance() + "%");
        mSummaryLabel.setText(current.getSummary());
        mLocationLabel.setText(current.getLocation());

        Drawable drawable = getResources().getDrawable(current.getIconId());
        mIconImageView.setImageDrawable(drawable);
    }

    private Forecast parseForecastDetails(String jsonData) throws JSONException, IOException {
        Forecast forecast = new Forecast();

        forecast.setCurrent(getCurrentDetails(jsonData));
        forecast.setHourlyForecast(getHourlyForecast(jsonData));
        forecast.setDailyForecast(getDailyForecast(jsonData));
        return forecast;
    }

    private Day[] getDailyForecast(String jsonData) throws JSONException  {
        JSONObject forecast = new JSONObject(jsonData);
        String timezone = forecast.getString("timezone");
        JSONObject daily = forecast.getJSONObject("daily");
        JSONArray data = daily.getJSONArray("data");

        Day[] days = new Day[data.length()];
        for(int i = 0; i<data.length(); i++){
            JSONObject jsonDay = data.getJSONObject(i);
            Day day = new Day();

            day.setTime(jsonDay.getLong("time"));
            day.setSummary(jsonDay.getString("summary"));
            day.setTemperatureMax(jsonDay.getDouble("temperatureMax"));
            day.setTimezone(timezone);
            day.setIcon(jsonDay.getString("icon"));

            days[i] = day;
        }

        return days;
    }

    private Hour[] getHourlyForecast(String jsonData) throws JSONException {
        JSONObject forecast = new JSONObject(jsonData);
        String timezone = forecast.getString("timezone");
        JSONObject hourly = forecast.getJSONObject("hourly");
        JSONArray data = hourly.getJSONArray("data");

        Hour[] hours = new Hour[data.length()];
        for(int i = 0; i<data.length(); i++){
            JSONObject jsonHour = data.getJSONObject(i);
            Hour hour = new Hour();

            hour.setTime(jsonHour.getLong("time"));
            hour.setSummary(jsonHour.getString("summary"));
            hour.setTemperature(jsonHour.getDouble("temperature"));
            hour.setTimezone(timezone);
            hour.setIcon(jsonHour.getString("icon"));

            hours[i] = hour;
        }

        return hours;
    }

    private Current getCurrentDetails(String jsonData) throws JSONException, IOException {
        JSONObject forecast = new JSONObject(jsonData);
        JSONObject currently = forecast.getJSONObject("currently");

        Current current = new Current();

        current.setHumidity(currently.getDouble("humidity"));
        current.setIcon(currently.getString("icon"));
        current.setTemperature(currently.getDouble("temperature"));
        current.setPrecipChance(currently.getDouble("precipProbability"));
        current.setSummary(currently.getString("summary"));
        current.setTime(currently.getLong("time"));
        current.setTimezone(forecast.getString("timezone"));



        // Reverse geocoding of location
        Geocoder geocoder = new Geocoder(this, Locale.ENGLISH);

        List<Address> addresses = geocoder.getFromLocation(mLatitude, mLongitude ,1);
        String city = "";
        if (addresses.get(0).getLocality() != null){
            city = addresses.get(0).getLocality();
            Log.i(TAG, "getlocality ");
        } else {
            city = addresses.get(0).getSubAdminArea();
            Log.i(TAG, "getSubAdminArea ");
        }


        String country = addresses.get(0).getCountryName();
        mLocationName = "" + city +", " + country;


        Log.i(TAG, "Location " + mLocationName);
        current.setLocation(mLocationName);

        Log.d(TAG, current.getFormattedTime());

        return current;

    }

    private boolean isNetworkAvailable() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        boolean isAvailable = false;
        if (networkInfo != null && networkInfo.isConnected()) {
            isAvailable = true;
        }

        return isAvailable;
    }

    private void alertUserAboutError() {
        AlertDialogFragment dialog = new AlertDialogFragment();
        dialog.show(getFragmentManager(), "error_dialog");
    }

    public void handleNewLocation(Location location) {
        Log.d(TAG, location.toString());

        mLatitude = location.getLatitude();
        mLongitude = location.getLongitude();
        getForecast(mLatitude, mLongitude);
    }

    @OnClick(R.id.dailyButton)
        public void startDailyActivity(View view) {
            if(mForecast != null) {
                Intent intent = new Intent(this, DailyForecastActivity.class);
                intent.putExtra(DAILY_FORECAST, mForecast.getDailyForecast());
                intent.putExtra(LOCATION, mLocationName);
                startActivity(intent);
            } else {
                Toast.makeText(MainActivity.this, "Just a second, weather data is being gathered", Toast.LENGTH_SHORT).show();
            }
        }

    @OnClick(R.id.hourlyButton)
        public void startHourlyActivity(View view) {
            if(mForecast != null) {
                Intent intent = new Intent(this, HourlyForecastActivity.class);
                intent.putExtra(HOURLY_FORECAST, mForecast.getHourlyForecast());
                startActivity(intent);
            } else {
                Toast.makeText(MainActivity.this, "Just a second, weather data is being gathered", Toast.LENGTH_SHORT).show();
            }
    }

    private void GetLocationPermision(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay!
                    mLocationProvider.connect();

                } else {

                    mPermisionLabel.setVisibility(View.VISIBLE);
                    mLocationLabel.setVisibility(View.INVISIBLE);
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }
}
