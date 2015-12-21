/*
* Copyright (C) 2015 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.systemui.omni;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.systemui.R;

import com.android.internal.util.omni.PackageUtils;

public class OmniJawsClient {
    private static final String TAG = "WeatherService:OmniJawsClient";
    private static final boolean DEBUG = false;
    public static final String SERVICE_PACKAGE = "org.omnirom.omnijaws";
    public static final Uri WEATHER_URI
            = Uri.parse("content://org.omnirom.omnijaws.provider/weather");
    public static final Uri SETTINGS_URI
            = Uri.parse("content://org.omnirom.omnijaws.provider/settings");

    private static final String ICON_PACKAGE_DEFAULT = "org.omnirom.omnijaws";
    private static final String ICON_PREFIX_DEFAULT = "weather";

    public static final String[] WEATHER_PROJECTION = new String[]{
            "city",
            "wind",
            "condition_code",
            "temperature",
            "humidity",
            "condition",
            "forecast_low",
            "forecast_high",
            "forecast_condition",
            "forecast_condition_code",
            "time_stamp",
            "forecast_date"
    };

    final String[] SETTINGS_PROJECTION = new String[] {
            "enabled"
    };

    public static class WeatherInfo {
        public String city;
        public String wind;
        public int conditionCode;
        public String temp;
        public String humidity;
        public String condition;
        public Long timeStamp;
        public List<DayForecast> forecasts;

        public String toString() {
            return city + ":" + new Date(timeStamp) + ": " + wind + ":" +conditionCode + ":" + temp + ":" + humidity + ":" + condition + ":" + forecasts;
        }
    }

    public static class DayForecast {
        public String low;
        public String high;
        public int conditionCode;
        public String condition;
        public String date;

        public String toString() {
            return "[" + low + ":" + high + ":" +conditionCode + ":" + condition + ":" + date + "]";
        }
    }

    private Context mContext;
    private Handler mHandler = new Handler();
    private WeatherInfo mCachedInfo;
    private boolean mEnabled;
    private Resources mRes;
    private String mPackageName;
    private String mIconPrefix;
    private String mSettingIconPackage;

    public OmniJawsClient(Context context) {
        mContext = context;
        mEnabled = isOmniJawsServiceInstalled();

        if (mEnabled) {
            settingsChanged();
        }
    }

    public void updateWeather(boolean force) {
        if (mEnabled) {
            Intent updateIntent = new Intent(Intent.ACTION_MAIN)
                    .setClassName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".WeatherService");
            updateIntent.setAction(SERVICE_PACKAGE + ".ACTION_UPDATE");
            updateIntent.putExtra("force", force);
            mContext.startService(updateIntent);
        }
    }

    public void startSettings() {
        if (mEnabled) {
            Intent settings = new Intent(Intent.ACTION_MAIN)
                    .setClassName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".SettingsActivity");
            mContext.startActivity(settings);
        }
    }

    public Intent getSettingsIntent() {
        if (mEnabled) {
            Intent settings = new Intent(Intent.ACTION_MAIN)
                    .setClassName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".SettingsActivity");
            return settings;
        }
        return null;
    }

    public WeatherInfo getWeatherInfo() {
        return mCachedInfo;
    }

    public void queryWeather() {
        if (!isOmniJawsEnabled()) {
            Log.w(TAG, "queryWeather while disabled");
            mCachedInfo = null;
            return;
        }
        Cursor c = mContext.getContentResolver().query(WEATHER_URI, WEATHER_PROJECTION,
                null, null, null);
        mCachedInfo = null;
        if (c != null) {
            try {
                int count = c.getCount();
                if (count > 0) {
                    mCachedInfo = new WeatherInfo();
                    List<DayForecast> forecastList = new ArrayList<DayForecast>();
                    int i = 0;
                    for (i = 0; i < count; i++) {
                        c.moveToPosition(i);
                        if (i == 0) {
                            mCachedInfo.city = c.getString(0);
                            mCachedInfo.wind = c.getString(1);
                            mCachedInfo.conditionCode = c.getInt(2);
                            mCachedInfo.temp = c.getString(3);
                            mCachedInfo.humidity = c.getString(4);
                            mCachedInfo.condition = c.getString(5);
                            mCachedInfo.timeStamp = Long.valueOf(c.getString(10));
                        } else {
                            DayForecast day = new DayForecast();
                            day.low = c.getString(6);
                            day.high = c.getString(7);
                            day.condition = c.getString(8);
                            day.conditionCode = c.getInt(9);
                            day.date = c.getString(11);
                            forecastList.add(day);
                        }
                    }
                    mCachedInfo.forecasts = forecastList;
                }
            } finally {
                c.close();
            }
        }
        if (DEBUG) Log.d(TAG, "queryWeather " + mCachedInfo);
    }

    private void loadDefaultIconsPackage() {
        mPackageName = ICON_PACKAGE_DEFAULT;
        mIconPrefix = ICON_PREFIX_DEFAULT;
        mSettingIconPackage = mPackageName + "." + mIconPrefix;
        if (DEBUG) Log.d(TAG, "Load default icon pack " + mSettingIconPackage + " " + mPackageName + " " + mIconPrefix);
        try {
            PackageManager packageManager = mContext.getPackageManager();
            mRes = packageManager.getResourcesForApplication(mPackageName);
        } catch (Exception e) {
            mRes = null;
        }
        if (mRes == null) {
            Log.w(TAG, "No default package found");
        }
    }

    private void loadCustomIconPackage() {
        int idx = mSettingIconPackage.lastIndexOf(".");
        mPackageName = mSettingIconPackage.substring(0, idx);
        mIconPrefix = mSettingIconPackage.substring(idx + 1);
        if (DEBUG) Log.d(TAG, "Load custom icon pack " + mSettingIconPackage + " " + mPackageName + " " + mIconPrefix);
        try {
            PackageManager packageManager = mContext.getPackageManager();
            mRes = packageManager.getResourcesForApplication(mPackageName);
        } catch (Exception e) {
            mRes = null;
        }
        if (mRes == null) {
            Log.w(TAG, "Icon pack loading failed - loading default");
            loadDefaultIconsPackage();
        }
    }

    private int getWeatherImageResource(int conditionCode) {
        if (mRes != null) {
            int resId = mRes.getIdentifier(mIconPrefix + "_" + conditionCode, "drawable", mPackageName);
            if (resId != 0) {
                return resId;
            }
            resId = mRes.getIdentifier("weather_na", "drawable", mPackageName);
            if (resId != 0) {
                return resId;
            }
        }
        // last resort
        return R.drawable.weather_na;
    }

    public Drawable getWeatherConditionImage(int conditionCode) {
        if (!isOmniJawsEnabled()) {
            Log.w(TAG, "Requesting condition image while disabled");
            return mContext.getResources().getDrawable(R.drawable.weather_na);
        }
        if (!PackageUtils.isAvailableApp(mPackageName, mContext)) {
            Log.w(TAG, "Icon pack no longer available - loading default " + mPackageName);
            loadDefaultIconsPackage();
        }
        try {
            int resId = getWeatherImageResource(conditionCode);
            return mRes.getDrawable(resId);
        } catch(Exception e) {
            Log.w(TAG, "Failed to get condition image for " + conditionCode);
            return mContext.getResources().getDrawable(R.drawable.weather_na);
        }
    }

    public Drawable getErrorWeatherConditionImage() {
        return mContext.getResources().getDrawable(R.drawable.weather_na);
    }

    private boolean isOmniJawsServiceInstalled() {
        return PackageUtils.isAvailableApp(SERVICE_PACKAGE, mContext);
    }

    public boolean isOmniJawsEnabled() {
        if (!mEnabled) {
            return false;
        }
        final Cursor c = mContext.getContentResolver().query(SETTINGS_URI, SETTINGS_PROJECTION,
                null, null, null);
        if (c != null) {
            int count = c.getCount();
            if (count == 1) {
                c.moveToPosition(0);
                boolean enabled = c.getInt(0) == 1;
                return enabled;
            }
        }
        return true;
    }

    public void settingsChanged() {
        if (mEnabled) {
            final String iconPack = Settings.System.getStringForUser(
                   mContext.getContentResolver(), Settings.System.STATUS_BAR_WEATHER_ICON_PACK, UserHandle.USER_CURRENT);
            if (iconPack == null) {
                loadDefaultIconsPackage();
            } else if (mSettingIconPackage == null || !iconPack.equals(mSettingIconPackage)) {
                mSettingIconPackage = iconPack;
                loadCustomIconPackage();
            }
        }
    }
}
