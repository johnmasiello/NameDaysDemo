package com.example.john.namedays;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.JsonReader;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

interface DownloadCallback {
    void handleResponse(@NonNull String jsonString);

    /**
     * Logs the error to both logcat and the UI
     */
    void logError(String tag, String message);

    Activity getActivity();
}

interface CountryCallback {
    /**
     *
     * @param countryCode  An ISO 3166 alpha-2 country code
     */
    void setCountry(String countryCode);
}

interface DayAndMonthCallback {
    /**
     * Make the request with NameDays API, using day and month
     */
    void makeRequest(int day, int month);
}

/**
 * A class
 *
 */
public class MainActivity extends AppCompatActivity implements DownloadCallback, CountryCallback,
    DayAndMonthCallback {

    private static class MyConnectionThread extends Thread {
        DownloadCallback downloadCallback;
        HttpURLConnection httpURLConnection;

        MyConnectionThread(DownloadCallback downloadCallback, HttpURLConnection httpURLConnection) {
            super("Connection Thread");
            this.downloadCallback = downloadCallback;
            this.httpURLConnection = httpURLConnection;
        }

        @Override
        public void run() {
            // Open communications link (network traffic occurs here).
            try {
                httpURLConnection.connect();
                final String response = read(new BufferedInputStream(httpURLConnection.getInputStream()));

                if (response != null && downloadCallback != null) {
                    downloadCallback.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            downloadCallback.handleResponse(response);
                        }
                    });
                }

            } catch (final IOException e) {
                if (downloadCallback != null) {
                    downloadCallback.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            downloadCallback.logError("Connection",
                                    "Unable to read response from connection: " + e.getMessage());
                        }
                    });
                } else {
                    Log.e("Connection", "Unable to read response from connection: " + e.getMessage());
                }
            } finally {
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            }
        }

        private String read(InputStream inputStream) {

            StringBuilder body = null;
            try {
                InputStreamReader reader = new InputStreamReader(inputStream, "UTF-8");
                final int READ_SIZE = 128;
                char[] buffer = new char[READ_SIZE];
                body = new StringBuilder();

                int bytesRead;

                bytesRead = reader.read(buffer);
                while (bytesRead != -1) {
                    body.append(buffer, 0, bytesRead);

                    bytesRead = reader.read(buffer);
                }
            } catch (IOException ignore) {
            } finally {
                if (inputStream != null ) {
                    try {
                        inputStream.close();
                    } catch (IOException ignore) {
                    }
                }
            }
            return body != null ? body.toString() : null;
        }
    }

    public static class CountryDialogFragment extends DialogFragment
            implements DialogInterface.OnClickListener {

        static CountryDialogFragment newInstance(String nameDays_country) {
            CountryDialogFragment f = new CountryDialogFragment();

            Bundle bundle = new Bundle();


            bundle.putString("country", nameDays_country);
            f.setArguments(bundle);

            return f;
        }

        private CountryCallback callback;

        private static final String[] COUNTRY_CODES =
                {"US", "CZ", "SK", "PL", "FR", "HU", "HR", "SE", "AT", "IT", "ES"};

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Theme_AppCompat_Light_Dialog_Alert);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity(), getTheme())
                    .setTitle(R.string.country_dialog_text)
                    .setSingleChoiceItems(fetchCountryDisplayNames(), fetchCheckedItem(), this)
                    .create();
        }

        private String[] fetchCountryDisplayNames() {
            Locale locale = Locale.getDefault();
            String language = locale.getLanguage();

            String[] displayNames = new String[COUNTRY_CODES.length];


            for (int i = 0; i < displayNames.length; i++) {
                displayNames[i] = new Locale(language, COUNTRY_CODES[i]).getDisplayCountry(locale);
            }

            return displayNames;
        }

        private int fetchCheckedItem() {
            String code = getArguments().getString("country");

            // Fallback on the US, if the country code is not found
            int found = 0;

            if (code != null) {
                code = code.substring("name_".length());

                int index = 0;
                for (String s : COUNTRY_CODES) {
                    if (code.equalsIgnoreCase(s)) {
                        found = index;
                        break;
                    }
                    index++;
                }
            }
            return found;
        }

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);

            if (getActivity() instanceof CountryCallback) {
                callback = ((CountryCallback) getActivity());
            }
        }

        @Override
        public void onDetach() {
            super.onDetach();
            callback = null;
        }

        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            if (callback != null) {
                callback.setCountry(COUNTRY_CODES[i]);
            }
            dialogInterface.dismiss();
            dismiss();
        }
    }

    public static class DayAndMonthDialogFragment extends DialogFragment {
        private DayAndMonthCallback callback;

        static DayAndMonthDialogFragment newInstance(int day, int month) {
            DayAndMonthDialogFragment dialog = new DayAndMonthDialogFragment();

            Bundle b = new Bundle();

            b.putInt("day", day);
            b.putInt("month", month);

            dialog.setArguments(b);

            return dialog;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_AppCompat_Light_Dialog_Alert);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            View view = getActivity().getLayoutInflater().inflate(R.layout.day_month_dialog_layout,
                    null, false);

            final TextView dayField, monthField;

            dayField = view.findViewById(R.id.dayField);
            monthField = view.findViewById(R.id.monthField);

            dayField.setText(String.valueOf(getArguments().getInt("day")));
            monthField.setText(String.valueOf(getArguments().getInt("month")));

            monthField.setOnKeyListener(
                    new View.OnKeyListener() {
                        @Override
                        public boolean onKey(View keyedView, int i, KeyEvent keyEvent) {
                            if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN) && (i == KeyEvent.KEYCODE_ENTER)) {

                                if (callback != null) {
                                    int day = Integer.parseInt(dayField.getText().toString());
                                    int month = Integer.parseInt(monthField.getText().toString());

                                    callback.makeRequest(day, month);
                                }
                                dismiss();
                                return true;
                            }
                            return false;
                        }
                    }
            );

            return new AlertDialog.Builder(getActivity(), getTheme())
                    .setTitle(R.string.day_and_month_dialog_text)
                    .setView(view)
                    .create();
        }

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);

            if (getActivity() instanceof CountryCallback) {
                callback = ((DayAndMonthCallback) getActivity());
            }
        }

        @Override
        public void onDetach() {
            super.onDetach();
            callback = null;
        }
    }

    // State variables
    private String country;
    private int day = 1, month = 1;

    // Helper variables
    private MyConnectionThread connectionThread;
    private int apiFunction;
    private String[] displayMonths;

    // UI
    private TextView responseView;

    private static final String COUNTRY_KEY = "Country";
    private static final String RESPONSE_KEY = "Response";
    private static final String DAY_KEY = "Day";
    private static final String MONTH_KEY = "Month";
    private static final String COUNTRY_DIALOG_TAG = "COUNTRY";
    private static final String DAY_MONTH_DIALOG_TAG = "DAY AND MONTH";
    private static final String API_HOST_NAME = "https://api.abalin.net/get/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        responseView = findViewById(R.id.responseView);
        if (responseView == null) {
            Log.e("App", "No view to show responses to requests");
        }

        // Set up state, such as country and month names, according to locale
        findDisplayMonths();

        if (savedInstanceState != null) {
            country = savedInstanceState.getString(COUNTRY_KEY);
            responseView.setText(savedInstanceState.getString(RESPONSE_KEY));
            day = savedInstanceState.getInt(DAY_KEY);
            month = savedInstanceState.getInt(MONTH_KEY);
        }

        if (country == null) {
            country = getCountryNameForAPI();
        }
    }

    /**
     * @see #getCountryNameForAPI(String)
     * @return getCountryNameForAPI(null)
     */
    private String getCountryNameForAPI() {
        return getCountryNameForAPI(null);
    }

    /**
     *
     * @param countryCode An ISO 3166 alpha-2 country code or a UN M.49 numeric-3 area code or null
     *
     * @return A string that denotes country to the namedays API: name_{country code, in lowercase},
     * for some accepted set of country codes = {CZ, SK, PL, FR, HU, HR, SE, AT, IT, ES, US}
     */
    private String getCountryNameForAPI(String countryCode) {
        if (countryCode == null) {
            Locale locale = Locale.getDefault();

            if (locale != null) {
                // Country code is either 'ISO 3166 alpha-2 country code or UN M.49 numeric-3 area code'
                countryCode = locale.getCountry();
            }
            if (countryCode == null) {
                countryCode = "US";
            }
        }

        /*
         <https://www.iso.org/obp/ui/#iso:pub:PUB500001:en>
         <https://en.wikipedia.org/wiki/ISO_3166-1_numeric>
         */
        switch (countryCode) {
            case "CZ":
            case "203":
                return "name_cz";

            case "SK":
            case "703":
                return "name_sk";

            case "PL":
            case "616":
                return "name_pl";

            case "FR":
            case "250":
                return "name_fr";

            case "HU":
            case "348":
                return "name_hu";

            case "HR":
            case "191":
                return "name_hr";

            case "SE":
            case "752":
                return "name_se";

            case "AT":
            case "040":
                return "name_at";

            case "IT":
            case "380":
                return "name_it";

            case "ES":
            case "724":
                return "name_es";

            case "US":
            case "840":
            default:
                return "name_us";
        }
    }

    private void findDisplayMonths() {
        Locale locale = Locale.getDefault();
        Calendar calendar = Calendar.getInstance(locale);
        Set<Map.Entry<String, Integer>> months = calendar.getDisplayNames(Calendar.MONTH, Calendar.LONG, locale).entrySet();

        if (displayMonths == null || displayMonths.length != months.size()) {
            displayMonths = new String[months.size()];
        }

        for (Map.Entry<String, Integer> entry : months) {
            displayMonths[entry.getValue()] = entry.getKey();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(COUNTRY_KEY, country);
        outState.putString(RESPONSE_KEY, responseView.getText().toString());
        outState.putInt(DAY_KEY, day);
        outState.putInt(MONTH_KEY, month);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.today:
                try {
                    apiFunction = id;
                    makeRequest(new URL(API_HOST_NAME + "today"));
                } catch (MalformedURLException e) {
                    logError("Request", e.getMessage());
                }
                break;

            case R.id.yesterday:
                try {
                    apiFunction = id;
                    makeRequest(new URL(API_HOST_NAME + "yesterday"));
                } catch (MalformedURLException e) {
                    logError("Request", e.getMessage());
                }
                break;

            case R.id.tomorrow:
                try {
                    apiFunction = id;
                    makeRequest(new URL(API_HOST_NAME + "tomorrow"));
                } catch (MalformedURLException e) {
                    logError("Request", e.getMessage());
                }
                break;

            case R.id.dayAndMonth:
                apiFunction = id;
                DayAndMonthDialogFragment.newInstance(day, month).show(getFragmentManager(), DAY_MONTH_DIALOG_TAG);
                break;

            case R.id.country:
                apiFunction = id;
                CountryDialogFragment.newInstance(country).show(getFragmentManager(), COUNTRY_DIALOG_TAG);
                break;

            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    void makeRequest(@NonNull URL url) {
        HttpURLConnection connection = null;
        try {
            connection = ((HttpURLConnection) url.openConnection());
            // For this use case, set HTTP method to GET.
            connection.setRequestMethod("GET");
            // Already true by default but setting just in case; needs to be true since this request
            // is carrying an input (response) body.
            connection.setDoInput(true);

            connectionThread = new MyConnectionThread(this, connection);
            // Make the request on a background thread
            connectionThread.start();

        } catch (IOException e) {
            logError("Connection", "Unable to open connection: "+e.getMessage());

            if (connection != null)
                connection.disconnect();
        }
    }

    @Override
    public void handleResponse(@NonNull String jsonString) {
        Log.d("Response", "response: "+jsonString);

        try {
            JSONObject jObj = new JSONObject(jsonString);
            JSONObject jDataObj = jObj.optJSONObject("data");

            if (jDataObj != null) {
                String nameDays = jDataObj.getString(country);

                try {
                    int day = jDataObj.getInt("day");
                    int month = jDataObj.getInt("month");

                    if (nameDays != null) {
                        responseView.setText(printNameDaysMessage(nameDays, day, month));
                    }
                } catch (JSONException e) {
                    logError("JSON", "Unable to read days or months fields");
                }

            } else {
                logError("JSON", "Value corresponding to key: 'data' is not a JSON object");
            }
        } catch (JSONException e) {
            logError("JSON", "Unable to make json object");
        }
    }

    @Override
    public void logError(String tag, String message) {
        Log.e(tag, message);
        responseView.setText(message);
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public void setCountry(String countryCode) {
        country = getCountryNameForAPI(countryCode);
    }


    @SuppressLint("DefaultLocale")
    @Override
    public void makeRequest(int day, int month) {
        this.day = day;
        this.month = month;

        try {
            makeRequest(new URL(String.format(API_HOST_NAME + "namedays?day=%d&month=%d", day, month)));
        } catch (MalformedURLException e) {
            logError("Request", e.getMessage());
        }
    }

    @SuppressLint("DefaultLocale")
    private String printNameDaysMessage(String nameDays, Integer day, Integer month) {
        switch (apiFunction) {
            case R.id.today:
                return String.format("Name day(s) for today : \n\n%s", nameDays);
            case R.id.yesterday:
                return String.format("Name day(s) for yesterday : \n\n%s", nameDays);
            case R.id.tomorrow:
                return String.format("Name day(s) for tomorrow : \n\n%s", nameDays);
            case R.id.dayAndMonth:
                return String.format("Name day(s) for %s %d : \n\n%s",
                        displayMonths[month - 1],
                        day,
                        nameDays);
            default:
                return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (connectionThread != null) {
            // Prevent the thread from calling back to the main thread
            connectionThread.downloadCallback = null;
        }
    }
}
