package ru.example.geekbrains.weatherapp;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String FONT_FILENAME = "fonts/weather.ttf";

    EditText mEditor_City, mEditor_Time, mEditor3, mEditor4, mEditor5;

    SharedPreferences sPref;

    final String CITY_NAME = "Город";
    final String WEATHER_TIME = "Обновление";
    final String WEATHER_ICON = "Виджет";
    final String TEMPERATURE_FIELD = "Температура";
    final String DETAILS_FIELD = "Детали";

    // Handler - это класс, позволяющий отправлять и обрабатывать сообщения и объекты runnable.
    // Он используется в двух случаях - когда нужно применить объект runnable когда-то в будущем,
    // и когда необходимо передать другому потоку выполнение какого-то метода. Второй случай наш.
    private final Handler handler = new Handler();

    // Реализация иконок погоды через шрифт (но можно и через картинки)
    private Typeface weatherFont;
    private TextView cityTextView;
    private TextView updatedTextView;
    private TextView detailsTextView;
    private TextView currentTemperatureTextView;
    private TextView weatherIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cityTextView = findViewById(R.id.city_field);
        updatedTextView = findViewById(R.id.updated_field);
        detailsTextView = findViewById(R.id.details_field);
        currentTemperatureTextView = findViewById(R.id.current_temperature_field);

        weatherFont = Typeface.createFromAsset(getAssets(), FONT_FILENAME);

        weatherIcon = findViewById(R.id.weather_icon);
        weatherIcon.setTypeface(weatherFont);

        loadText();
    }
    private void saveText(){
        sPref = getPreferences(MODE_PRIVATE);

        SharedPreferences.Editor mEditor = sPref.edit();

        mEditor.putString(CITY_NAME, cityTextView.getText().toString());
        mEditor.putString(WEATHER_TIME, updatedTextView.getText().toString());
        mEditor.putString(DETAILS_FIELD, detailsTextView.getText().toString());
        mEditor.putString(TEMPERATURE_FIELD, currentTemperatureTextView.getText().toString());
        mEditor.putString(WEATHER_ICON, weatherIcon.getText().toString());
        mEditor.apply();

        Toast.makeText(this, "Город сохранен!", Toast.LENGTH_LONG).show();

    }
    private void loadText() {
        sPref = getPreferences(MODE_PRIVATE);

        String saved_name = sPref.getString(CITY_NAME, "");
        String saved_time = sPref.getString(WEATHER_TIME, "");
        String saved_details = sPref.getString(DETAILS_FIELD, "");
        String saved_temper =sPref.getString(TEMPERATURE_FIELD, "");
        String saved_icon = sPref.getString(WEATHER_ICON, "");

        cityTextView.setText(saved_name);
        updatedTextView.setText(saved_time);
        detailsTextView.setText(saved_details);
        currentTemperatureTextView.setText(saved_temper);
        weatherIcon.setText(saved_icon);

        Toast.makeText(this, "Восстановлено из памяти!", Toast.LENGTH_LONG).show();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.weather, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.change_city) {
            showInputDialog();
            return true;
        }
        return false;
    }

    // Показываем диалоговое окно с выбором города
    private void showInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.change_city_dialog);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton(R.string.change_city_dialog_go, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                changeCity(input.getText().toString());
            }
        });
        builder.show();
    }

    // Обновление/загрузка погодных данных
    private void updateWeatherData(final String city) {
        new Thread() {  // Отдельный поток для получения новых данных в фоне
            @Override
            public void run() {
                final JSONObject json = WeatherDataLoader.getJSONData(getApplicationContext(), city);
                // Вызов методов напрямую может вызвать runtime error
                // Мы не можем напрямую обновить UI, поэтому используем handler,
                // чтобы обновить интерфейс в главном потоке.
                if (json == null)
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(),
                                    getString(R.string.place_not_found),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                else
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            renderWeather(json);
                        }
                    });
            }
        }.start();
    }

    // Обработка загруженных данных и обновление UI
    private void renderWeather(JSONObject json) {
        Log.d(TAG, "json " + json.toString());
        try {
            cityTextView.setText(json.getString("name").toUpperCase(Locale.US) + ", "
                    + json.getJSONObject("sys").getString("country"));

            JSONObject details = json.getJSONArray("weather").getJSONObject(0);
            JSONObject main = json.getJSONObject("main");
            detailsTextView.setText(details.getString("description").toUpperCase(Locale.US) +
                    "\n" + "Humidity: " + main.getString("humidity") + "%" + "\n" +
                    "Pressure: " + main.getString("pressure") + " hPa");

            currentTemperatureTextView.setText(String.format("%.2f",
                    main.getDouble("temp")) + " ℃");

            DateFormat df = DateFormat.getDateTimeInstance();
            String updatedOn = df.format(new Date(json.getLong("dt") * 1000));
            updatedTextView.setText("Last update: " + updatedOn);

            setWeatherIcon(details.getInt("id"), json.getJSONObject("sys").getLong(
                    "sunrise") * 1000, json.getJSONObject("sys").getLong(
                            "sunset") * 1000);
        }
        catch (Exception e) {
            Log.e(TAG, "One or more fields not found in the JSON data", e);
        }
    }

    // Подстановка нужной иконки
    // Парсим коды http://openweathermap.org/weather-conditions
    private void setWeatherIcon(int actualId, long sunrise, long sunset) {
        int id = actualId / 100; // Упрощение кодов (int оставляет только целочисленное значение)
        String icon = "";

        if (actualId == 800) {
            long currentTime = new Date().getTime();
            if (currentTime >= sunrise && currentTime < sunset)
                icon = getString(R.string.weather_sunny);
            else
                icon = getString(R.string.weather_clear_night);
        }
        else {
            Log.d(TAG, "id " + id);
            switch (id) {
                case 2:
                    icon = getString(R.string.weather_thunder);
                    break;
                case 3:
                    icon = getString(R.string.weather_drizzle);
                    break;
                case 5:
                    icon = getString(R.string.weather_rainy);
                    break;
                case 6:
                    icon = getString(R.string.weather_snowy);
                    break;
                case 7:
                    icon = getString(R.string.weather_foggy);
                    break;
                case 8:
                    icon = getString(R.string.weather_cloudy);
                    break;
                // Можете доработать приложение, найдя все иконки и распарсив все значения
                default:
                    break;
            }
        }
        weatherIcon.setText(icon);
    }

    // Метод для доступа кнопки меню к данным
    private void changeCity(String city) {
        updateWeatherData(city);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveText();
    }
}
