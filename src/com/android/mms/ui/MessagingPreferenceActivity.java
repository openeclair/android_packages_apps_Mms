/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import com.android.mms.MmsConfig;
import com.android.mms.R;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IHardwareService;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;

import com.android.mms.util.Recycler;

/**
 * With this activity, users can set preferences for MMS and SMS and
 * can access and manipulate SMS messages stored on the SIM.
 */
public class MessagingPreferenceActivity extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "Mms";
    
    // Symbolic names for the keys used for preference lookup
    public static final String MMS_DELIVERY_REPORT_MODE = "pref_key_mms_delivery_reports";
    public static final String EXPIRY_TIME              = "pref_key_mms_expiry";
    public static final String PRIORITY                 = "pref_key_mms_priority";
    public static final String READ_REPORT_MODE         = "pref_key_mms_read_reports";
    public static final String SMS_DELIVERY_REPORT_MODE = "pref_key_sms_delivery_reports";
    public static final String NOTIFICATION_ENABLED     = "pref_key_enable_notifications";
    public static final String NOTIFICATION_VIBRATE     = "pref_key_vibrate";
    public static final String NOTIFICATION_RINGTONE    = "pref_key_ringtone";
    public static final String AUTO_RETRIEVAL           = "pref_key_mms_auto_retrieval";
    public static final String RETRIEVAL_DURING_ROAMING = "pref_key_mms_retrieval_during_roaming";
    public static final String AUTO_DELETE              = "pref_key_auto_delete";
    public static final String SEND_ON_ENTER            = "pref_key_mms_send_on_enter";
    public static final String NOTIFICATION_LED         = "pref_key_mms_notification_led";
    public static final String NOTIFICATION_LED_COLOR   = "pref_key_mms_notification_led_color";
    public static final String NOTIFICATION_LED_BLINK_RATE = "pref_key_mms_notification_led_blink_rate";
    public static final String NOTIFICATION_VIBRATE_PATTERN = "pref_key_mms_notification_vibrate_pattern";
    public static final String BLACK_BACKGROUND      = "pref_key_mms_black_background";
    public static final String CONVERSATION_FONT_SIZE      = "pref_key_mms_conversation_font_size";
    public static final String CONVERSATION_HIDE_NAMES = "pref_key_conversation_hide_names";
    public static final String BACK_TO_ALL_THREADS     = "pref_key_mms_back_to_all_threads";
    public static final String USER_AGENT                   = "pref_key_mms_user_agent";
    public static final String USER_AGENT_CUSTOM            = "pref_key_mms_user_agent_custom";

    // Menu entries
    private static final int MENU_RESTORE_DEFAULTS    = 1;

    private Preference mSmsLimitPref;
    private Preference mMmsLimitPref;
    private Preference mManageSimPref;
    private Preference mConversationFontSize;
    private ListPreference mLEDColor;
    private Recycler mSmsRecycler;
    private Recycler mMmsRecycler;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.preferences);

        mManageSimPref = findPreference("pref_key_manage_sim_messages");
        mSmsLimitPref = findPreference("pref_key_sms_delete_limit");
        mMmsLimitPref = findPreference("pref_key_mms_delete_limit");
        mConversationFontSize = findPreference("pref_key_mms_conversation_font_size");

        if (!TelephonyManager.getDefault().hasIccCard()) {
            // No SIM card, remove the SIM-related prefs
            PreferenceCategory smsCategory =
                (PreferenceCategory)findPreference("pref_key_sms_settings");
            smsCategory.removePreference(mManageSimPref);
        }
        if (!MmsConfig.getMmsEnabled()) {
            // No Mms, remove all the mms-related preferences
            PreferenceCategory mmsOptions =
                (PreferenceCategory)findPreference("pref_key_mms_settings");
            getPreferenceScreen().removePreference(mmsOptions);

            PreferenceCategory storageOptions =
                (PreferenceCategory)findPreference("pref_key_storage_settings");
            storageOptions.removePreference(findPreference("pref_key_mms_delete_limit"));
        }

        mSmsRecycler = Recycler.getSmsRecycler();
        mMmsRecycler = Recycler.getMmsRecycler();

        // Fix up the recycler's summary with the correct values
        setSmsDisplayLimit();
        setMmsDisplayLimit();

        setFontSizeDisplay();

        mLEDColor = (ListPreference) findPreference("pref_key_mms_notification_led_color");
        mLEDColor.setOnPreferenceChangeListener(this);
    }

    private void setSmsDisplayLimit() {
        mSmsLimitPref.setSummary(
                getString(R.string.pref_summary_delete_limit,
                        mSmsRecycler.getMessageLimit(this)));
    }

    private void setMmsDisplayLimit() {
        mMmsLimitPref.setSummary(
                getString(R.string.pref_summary_delete_limit,
                        mMmsRecycler.getMessageLimit(this)));
    }

    private int getFontSize() {
        SharedPreferences mPrefs = mConversationFontSize.getSharedPreferences();
        return mPrefs.getInt(MessagingPreferenceActivity.CONVERSATION_FONT_SIZE, 18);
    }

    private void setFontSizeDisplay() {
        mConversationFontSize.setSummary(
                getString(R.string.pref_summary_mms_conversation_font_size,
                        getFontSize()));
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.clear();
        menu.add(0, MENU_RESTORE_DEFAULTS, 0, R.string.restore_default);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESTORE_DEFAULTS:
                restoreDefaultPreferences();
                return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (preference == mSmsLimitPref) {
            new NumberPickerDialog(this,
                    mSmsLimitListener,
                    mSmsRecycler.getMessageLimit(this),
                    mSmsRecycler.getMessageMinLimit(),
                    mSmsRecycler.getMessageMaxLimit(),
                    R.string.pref_title_sms_delete,
                    R.string.pref_messages_to_save).show();
        } else if (preference == mMmsLimitPref) {
            new NumberPickerDialog(this,
                    mMmsLimitListener,
                    mMmsRecycler.getMessageLimit(this),
                    mMmsRecycler.getMessageMinLimit(),
                    mMmsRecycler.getMessageMaxLimit(),
                    R.string.pref_title_mms_delete,
                    R.string.pref_messages_to_save).show();
        } else if (preference == mConversationFontSize) {
            new NumberPickerDialog(this,
                    mConversationFontSizeListener,
                    getFontSize(),
                    1,
                    30,
                    R.string.pref_title_mms_conversation_font_size,
                    R.string.pref_summary_mms_set_conversation_font_size).show();
        } else if (preference == mManageSimPref) {
            startActivity(new Intent(this, ManageSimMessages.class));
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }


    private void restoreDefaultPreferences() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit().clear().commit();
        setPreferenceScreen(null);
        addPreferencesFromResource(R.xml.preferences);
    }

    NumberPickerDialog.OnNumberSetListener mSmsLimitListener =
        new NumberPickerDialog.OnNumberSetListener() {
            public void onNumberSet(int limit) {
                mSmsRecycler.setMessageLimit(MessagingPreferenceActivity.this, limit);
                setSmsDisplayLimit();
            }
    };

    NumberPickerDialog.OnNumberSetListener mMmsLimitListener =
        new NumberPickerDialog.OnNumberSetListener() {
            public void onNumberSet(int limit) {
                mMmsRecycler.setMessageLimit(MessagingPreferenceActivity.this, limit);
                setMmsDisplayLimit();
            }
    };

    NumberPickerDialog.OnNumberSetListener mConversationFontSizeListener =
        new NumberPickerDialog.OnNumberSetListener() {
            public void onNumberSet(int limit) {
                SharedPreferences.Editor editor = mConversationFontSize.getEditor();
                editor.putInt(MessagingPreferenceActivity.CONVERSATION_FONT_SIZE, limit);
                editor.commit();
                setFontSizeDisplay();
            }
    };
    
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        IHardwareService hardware = IHardwareService.Stub.asInterface(
                ServiceManager.getService("hardware"));
        if (NOTIFICATION_LED_COLOR.equals(key)) {
            int value = Color.parseColor((String) objValue);
            try {
                hardware.pulseBreathingLightColor(value);
            } catch (RemoteException re) {
                Log.e(TAG, "could not preview LED color", re);
            }
        }
        return true;
    };
}
