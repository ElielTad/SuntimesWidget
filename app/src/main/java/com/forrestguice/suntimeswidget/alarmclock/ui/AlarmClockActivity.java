/**
    Copyright (C) 2018-2020 Forrest Guice
    This file is part of SuntimesWidget.

    SuntimesWidget is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SuntimesWidget is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with SuntimesWidget.  If not, see <http://www.gnu.org/licenses/>.
*/ 

package com.forrestguice.suntimeswidget.alarmclock.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.AlarmClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.forrestguice.suntimeswidget.AboutActivity;
import com.forrestguice.suntimeswidget.R;
import com.forrestguice.suntimeswidget.SuntimesActivity;
import com.forrestguice.suntimeswidget.SuntimesSettingsActivity;
import com.forrestguice.suntimeswidget.SuntimesUtils;
import com.forrestguice.suntimeswidget.SuntimesWarning;
import com.forrestguice.suntimeswidget.alarmclock.AlarmClockItem;
import com.forrestguice.suntimeswidget.alarmclock.AlarmDatabaseAdapter;
import com.forrestguice.suntimeswidget.alarmclock.AlarmNotifications;
import com.forrestguice.suntimeswidget.alarmclock.AlarmSettings;
import com.forrestguice.suntimeswidget.settings.AppSettings;
import com.forrestguice.suntimeswidget.settings.SolarEvents;
import com.forrestguice.suntimeswidget.settings.WidgetSettings;
import com.forrestguice.suntimeswidget.settings.WidgetThemes;
import com.forrestguice.suntimeswidget.themes.SuntimesTheme;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

/**
 * AlarmClockActivity
 */
public class AlarmClockActivity extends AppCompatActivity
{
    public static final String TAG = "AlarmReceiverList";

    public static final String ACTION_ADD_ALARM = "com.forrestguice.suntimeswidget.alarmclock.ALARM";
    public static final String ACTION_ADD_NOTIFICATION = "com.forrestguice.suntimeswidget.alarmclock.ADD_NOTIFICATION";

    public static final String EXTRA_SHOWBACK = "showBack";
    public static final String EXTRA_SOLAREVENT = "solarevent";
    public static final String EXTRA_SELECTED_ALARM = "selectedAlarm";

    public static final int REQUEST_EDITALARM = 1;
    public static final int REQUEST_ADDALARM = 10;
    public static final int REQUEST_SETTINGS = 20;

    public static final String WARNINGID_NOTIFICATIONS = "NotificationsWarning";

    private AlarmListDialog list;

    private FloatingActionButton addButton;
    private BottomSheetBehavior sheetBehavior;

    private SuntimesWarning notificationWarning;
    private List<SuntimesWarning> warnings;

    private AppSettings.LocaleInfo localeInfo;

    private int colorAlarmEnabled, colorOn, colorOff, colorEnabled, colorDisabled, colorPressed;
    private int resAddIcon, resCloseIcon;

    public AlarmClockActivity()
    {
        super();
    }

    @Override
    protected void attachBaseContext(Context newBase)
    {
        Context context = AppSettings.initLocale(newBase, localeInfo = new AppSettings.LocaleInfo());
        super.attachBaseContext(context);
    }

    @Override
    public void onCreate(Bundle savedState)
    {
        initTheme();
        super.onCreate(savedState);
        initLocale(this);
        setContentView(R.layout.layout_activity_alarmclock1);
        initViews(this);
        initWarnings(this, savedState);
        handleIntent(getIntent());
    }

    @Override
    public void onStart()
    {
        super.onStart();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        restoreDialogs();
        checkWarnings();
    }

    @Override
    public void onPause()
    {
        super.onPause();
    }

    @Override
    public void onStop()
    {
        super.onStop();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode)
        {
            case REQUEST_ADDALARM:
                onEditAlarmResult(resultCode, data, true);
                break;

            case REQUEST_EDITALARM:
                onEditAlarmResult(resultCode, data, false);
                break;

            case REQUEST_SETTINGS:
                onSettingsResult(resultCode, data);
                break;
        }
    }


    private String appTheme;
    private int appThemeResID;
    private SuntimesTheme appThemeOverride = null;

    private void initTheme()
    {
        appTheme = AppSettings.loadThemePref(this);
        setTheme(appThemeResID = AppSettings.themePrefToStyleId(this, appTheme, null));

        String themeName = AppSettings.getThemeOverride(this, appThemeResID);
        if (themeName != null) {
            Log.i("initTheme", "Overriding \"" + appTheme + "\" using: " + themeName);
            appThemeOverride = WidgetThemes.loadTheme(this, themeName);
        }
    }



    @Override
    public void onNewIntent( Intent intent )
    {
        super.onNewIntent(intent);
        Log.d("DEBUG", "new intent: " + intent);
        handleIntent(intent);
    }

    protected void handleIntent(Intent intent)
    {
        String param_action = intent.getAction();
        intent.setAction(null);

        Uri param_data = intent.getData();
        intent.setData(null);

        boolean selectItem = true;

        if (param_action != null)
        {
            if (param_action.equals(AlarmClock.ACTION_SET_ALARM))
            {
                String param_label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE);
                int param_hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1);
                int param_minute = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1);

                ArrayList<Integer> param_days = AlarmRepeatDialog.PREF_DEF_ALARM_REPEATDAYS;
                boolean param_vibrate = AlarmSettings.loadPrefVibrateDefault(this);
                Uri param_ringtoneUri = AlarmSettings.getDefaultRingtoneUri(this, AlarmClockItem.AlarmType.ALARM);
                if (Build.VERSION.SDK_INT >= 19)
                {
                    param_vibrate = intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, param_vibrate);

                    String param_ringtoneUriString = intent.getStringExtra(AlarmClock.EXTRA_RINGTONE);
                    if (param_ringtoneUriString != null) {
                        param_ringtoneUri = (param_ringtoneUriString.equals(AlarmClock.VALUE_RINGTONE_SILENT) ? null : Uri.parse(param_ringtoneUriString));
                    }

                    ArrayList<Integer> repeatOnDays = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS);
                    if (repeatOnDays != null) {
                        param_days = repeatOnDays;
                    }
                }

                SolarEvents param_event = SolarEvents.valueOf(intent.getStringExtra(AlarmClockActivity.EXTRA_SOLAREVENT), null);
                list.createAlarm(this, AlarmClockItem.AlarmType.ALARM, param_label, param_event, null, param_hour, param_minute, null, param_vibrate, param_ringtoneUri, param_days, true);

            } else if (param_action.equals(ACTION_ADD_ALARM)) {
                showAddDialog(AlarmClockItem.AlarmType.ALARM);

            } else if (param_action.equals(ACTION_ADD_NOTIFICATION)) {
                showAddDialog(AlarmClockItem.AlarmType.NOTIFICATION);

            } else if (param_action.equals(AlarmNotifications.ACTION_DELETE)) {
                if (param_data != null) {
                    list.notifyAlarmDeleted(ContentUris.parseId(param_data));
                } else {
                    list.notifyAlarmsCleared();
                    selectItem = false;
                }
            }
        } else {
            if (param_data != null) {
                list.notifyAlarmUpdated(ContentUris.parseId(param_data));
            }
        }

        long selectedID = intent.getLongExtra(EXTRA_SELECTED_ALARM, -1);
        if (selectItem && selectedID != -1)
        {
            Log.d(TAG, "handleIntent: selected id: " + selectedID);
            list.setSelectedRowID(selectedID);
        }
    }

    @SuppressLint("ResourceType")
    private void initLocale(Context context)
    {
        WidgetSettings.initDefaults(context);
        WidgetSettings.initDisplayStrings(context);
        SuntimesUtils.initDisplayStrings(context);
        SolarEvents.initDisplayStrings(context);
        AlarmClockItem.AlarmType.initDisplayStrings(context);
        AlarmClockItem.AlarmTimeZone.initDisplayStrings(context);

        int[] attrs = { R.attr.alarmColorEnabled, android.R.attr.textColorPrimary, R.attr.text_disabledColor, R.attr.buttonPressColor, android.R.attr.textColor, R.attr.icActionNew, R.attr.icActionClose };
        TypedArray a = context.obtainStyledAttributes(attrs);
        colorAlarmEnabled = colorOn = ContextCompat.getColor(context, a.getResourceId(0, R.color.alarm_enabled_dark));
        colorEnabled = ContextCompat.getColor(context, a.getResourceId(1, android.R.color.primary_text_dark));
        colorDisabled = ContextCompat.getColor(context, a.getResourceId(2, R.color.text_disabled_dark));
        colorPressed = ContextCompat.getColor(context, a.getResourceId(3, R.color.sunIcon_color_setting_dark));
        colorOff = ContextCompat.getColor(context, a.getResourceId(4, R.color.grey_600));
        resAddIcon = a.getResourceId(5, R.drawable.ic_action_new);
        resCloseIcon = a.getResourceId(6, R.drawable.ic_action_close);
        a.recycle();

        if (appThemeOverride != null) {
            colorAlarmEnabled = colorOn = appThemeOverride.getAccentColor();
            colorPressed = appThemeOverride.getActionColor();
        }
    }

    @Override
    public void onSaveInstanceState( Bundle outState )
    {
        super.onSaveInstanceState(outState);
        saveWarnings(outState);
        outState.putInt("bottomsheet", sheetBehavior.getState());
    }

    @Override
    public void onRestoreInstanceState(@NonNull Bundle savedState)
    {
        super.onRestoreInstanceState(savedState);
        restoreWarnings(savedState);

        int sheetState = savedState.getInt("bottomsheet", BottomSheetBehavior.STATE_HIDDEN);
        sheetBehavior.setState(sheetState);
        if (sheetState != BottomSheetBehavior.STATE_HIDDEN) {
            addButton.setScaleX(0);
            addButton.setScaleY(0);
        }
    }

    /**
     * initialize ui/views
     * @param context a context used to access resources
     */
    protected void initViews(Context context)
    {
        SuntimesUtils.initDisplayStrings(context);

        Toolbar menuBar = (Toolbar) findViewById(R.id.app_menubar);
        setSupportActionBar(menuBar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
        {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            boolean showBack = getIntent().getBooleanExtra(EXTRA_SHOWBACK, false);
            if (!showBack) {
                actionBar.setHomeAsUpIndicator(R.drawable.ic_action_suntimes);
            }
        }

        addButton = (FloatingActionButton) findViewById(R.id.btn_add);
        addButton.setBackgroundTintList(SuntimesUtils.colorStateList(colorAlarmEnabled, colorDisabled, colorPressed));
        addButton.setRippleColor(Color.TRANSPARENT);
        addButton.setOnClickListener(onFabMenuClick);

        list = (AlarmListDialog) getSupportFragmentManager().findFragmentById(R.id.listFragment);
        list.setOnEmptyViewClick(onEmptyViewClick);
        list.setAdapterListener(listAdapter);

        View bottomSheet = findViewById(R.id.app_bottomsheet);
        sheetBehavior = BottomSheetBehavior.from(bottomSheet);
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        sheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback()
        {
            @Override
            public void onStateChanged(@NonNull View view, int newState)
            {
                /* switch (newState) {
                    case BottomSheetBehavior.STATE_HIDDEN: break;
                    case BottomSheetBehavior.STATE_EXPANDED: break;
                    case BottomSheetBehavior.STATE_COLLAPSED: break;
                    case BottomSheetBehavior.STATE_DRAGGING: break;
                    case BottomSheetBehavior.STATE_SETTLING: break;
                } */
            }

            @Override
            public void onSlide(@NonNull View view, float v) {
                addButton.animate().scaleX(1 - v).scaleY(1 - v).setDuration(0).start();
            }
        });
    }

    private boolean isAddDialogShowing() {
        return sheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED || sheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED;
    }

    private AlarmListDialog.AdapterListener listAdapter = new AlarmListDialog.AdapterListener() {
        @Override
        public void onItemClicked(AlarmClockItem item, AlarmListDialog.AlarmListDialogItem view)
        {
            if (isAddDialogShowing()) {
                Log.d("DEBUG", "onItemClicked: dismiss add: " + sheetBehavior.getState());
                dismissAddDialog();

            } else if (list.getSelectedRowID() == item.rowID) {
                Log.d("DEBUG", "onItemClicked: show edit");
                showAlarmEditActivity(item, view.text_datetime, REQUEST_EDITALARM, false);
            }
        }

        @Override
        public boolean onItemLongClicked(AlarmClockItem item) {
            return true;
        }

        @Override
        public void onAlarmToggled(AlarmClockItem item, boolean enabled) {
            if (enabled) {
                AlarmNotifications.showTimeUntilToast(AlarmClockActivity.this, list.getView(), item);
            }
        }

        @Override
        public void onAlarmAdded(AlarmClockItem item) {
        }

        @Override
        public void onAlarmDeleted(long rowID) {}

        @Override
        public void onAlarmsCleared() {
            Toast.makeText(AlarmClockActivity.this, getString(R.string.clearalarms_toast_success), Toast.LENGTH_LONG).show();
        }
    };

    private View.OnClickListener onEmptyViewClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            showHelp();
        }
    };

    private DialogInterface.OnClickListener onAddAlarmAccepted = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface d, int which)
        {
            FragmentManager fragments = getSupportFragmentManager();
            AlarmCreateDialog dialog = (AlarmCreateDialog) fragments.findFragmentById(R.id.createAlarmFragment);
            AlarmClockItem item = AlarmCreateDialog.createAlarm(dialog, dialog.getAlarmType());
            ViewCompat.setTransitionName(dialog.text_time, "transition_" + item.rowID);
            showAlarmEditActivity(item, dialog.text_time, REQUEST_ADDALARM, true);
        }
    };
    private DialogInterface.OnClickListener onAddAlarmCanceled = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface d, int which) {
            dismissAddDialog();
        }
    };

    protected void showAlarmEditActivity(@NonNull AlarmClockItem item, @Nullable View sharedView, int requestCode, boolean isNewAlarm)
    {
        Intent intent = new Intent(this, AlarmEditActivity.class);
        intent.putExtra(AlarmEditActivity.EXTRA_ITEM, item);
        intent.putExtra(AlarmEditActivity.EXTRA_ISNEW, isNewAlarm);

        if (sharedView != null) {
            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(this, sharedView, ViewCompat.getTransitionName(sharedView));
            startActivityForResult(intent, requestCode, options.toBundle());
        } else {
            startActivityForResult(intent, requestCode);
        }
    }
    private AlarmDatabaseAdapter.AlarmItemTaskListener onUpdateItem = new AlarmDatabaseAdapter.AlarmItemTaskListener()
    {
        @Override
        public void onFinished(Boolean result, AlarmClockItem item)
        {
            if (result)
            {
                if (item.enabled) {
                    sendBroadcast( AlarmNotifications.getAlarmIntent(AlarmClockActivity.this, AlarmNotifications.ACTION_SCHEDULE, item.getUri()) );
                    listAdapter.onAlarmToggled(item, true);
                }

                if (list != null) {
                    list.reloadAdapter(item.rowID);
                    list.setSelectedRowID(item.rowID);
                }
            }
        }
    };

    protected void showAddDialog(AlarmClockItem.AlarmType type)
    {
        list.clearSelection();

        FragmentManager fragments = getSupportFragmentManager();
        AlarmCreateDialog dialog = (AlarmCreateDialog) fragments.findFragmentById(R.id.createAlarmFragment);
        if (dialog != null) {
            dialog.setAlarmType(type);
            dialog.setOnAcceptedListener(onAddAlarmAccepted);
            dialog.setOnCanceledListener(onAddAlarmCanceled);
        }
        sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    protected void dismissAddDialog() {
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    protected void updateViews(Context context) {
    }

    protected void restoreDialogs()
    {
        FragmentManager fragments = getSupportFragmentManager();
        AlarmCreateDialog alarmCreateDialog = (AlarmCreateDialog) fragments.findFragmentById(R.id.createAlarmFragment);
        if (alarmCreateDialog != null) {
            alarmCreateDialog.setOnAcceptedListener(onAddAlarmAccepted);
            alarmCreateDialog.setOnCanceledListener(onAddAlarmCanceled);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private void initWarnings(Context context, Bundle savedState)
    {
        notificationWarning = new SuntimesWarning(WARNINGID_NOTIFICATIONS);
        warnings = new ArrayList<SuntimesWarning>();
        warnings.add(notificationWarning);
        restoreWarnings(savedState);
    }
    private SuntimesWarning.SuntimesWarningListener warningListener = new SuntimesWarning.SuntimesWarningListener() {
        @Override
        public void onShowNextWarning() {
            showWarnings();
        }
    };
    private void saveWarnings( Bundle outState )
    {
        for (SuntimesWarning warning : warnings) {
            warning.save(outState);
        }
    }
    private void restoreWarnings(Bundle savedState)
    {
        for (SuntimesWarning warning : warnings) {
            warning.restore(savedState);
            warning.setWarningListener(warningListener);
        }
    }
    private void showWarnings()
    {
        boolean showWarnings = AppSettings.loadShowWarningsPref(this);
        if (showWarnings && notificationWarning.shouldShow() && !notificationWarning.wasDismissed())
        {
            View view = notificationWarning.getSnackbar().getView();
            float iconSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
            notificationWarning.initWarning(this, view, getString(R.string.notificationsWarning), iconSize);
            notificationWarning.getSnackbar().setAction(getString(R.string.configLabel_alarms_notifications), new View.OnClickListener()
            {
                @Override
                public void onClick(View view) {
                    SuntimesSettingsActivity.openNotificationSettings(AlarmClockActivity.this);
                }
            });
            notificationWarning.show();
            return;
        }

        // no warnings shown; clear previous (stale) messages
        notificationWarning.dismiss();
    }
    private void checkWarnings()
    {
        notificationWarning.setShouldShow(!NotificationManagerCompat.from(this).areNotificationsEnabled());
        showWarnings();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.alarmclock, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.action_settings:
                showSettings();
                return true;

            case R.id.action_help:
                showHelp();
                return true;

            case R.id.action_about:
                showAbout();
                return true;

            case android.R.id.home:
                boolean showBack = getIntent().getBooleanExtra(EXTRA_SHOWBACK, false);
                if (showBack) {
                    onBackPressed();
                } else {
                    onHomePressed();
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected void onHomePressed()
    {
        Intent intent = new Intent(this, SuntimesActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        overridePendingTransition(R.anim.transition_swap_in, R.anim.transition_swap_out);
    }

    @SuppressWarnings("RestrictedApi")
    @Override
    protected boolean onPrepareOptionsPanel(View view, Menu menu)
    {
        SuntimesUtils.forceActionBarIcons(menu);
        return super.onPrepareOptionsPanel(view, menu);
    }

    private View.OnClickListener onFabMenuClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            showAddDialog(AlarmClockItem.AlarmType.ALARM);
        }
    };

    /**
     * showSettings
     */
    protected void showSettings()
    {
        Intent settingsIntent = new Intent(this, SuntimesSettingsActivity.class);
        startActivityForResult(settingsIntent, REQUEST_SETTINGS);
        overridePendingTransition(R.anim.transition_next_in, R.anim.transition_next_out);
    }

    /**
     * showHelp
     */
    protected void showHelp()
    {
        /**HelpDialog helpDialog = new HelpDialog();
         helpDialog.setContent(getString(R.string.help_alarmclock));
         helpDialog.show(getSupportFragmentManager(), DIALOGTAG_HELP);**/
    }

    /**
     * showAbout
     */
    protected void showAbout()
    {
        Intent about = new Intent(this, AboutActivity.class);
        about.putExtra(AboutActivity.EXTRA_ICONID, R.mipmap.ic_launcher_alarms_round);
        startActivity(about);
        overridePendingTransition(R.anim.transition_next_in, R.anim.transition_next_out);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    protected void onEditAlarmResult(int resultCode, Intent data, boolean isNewAlarm)
    {
        dismissAddDialog();
        if (resultCode == RESULT_OK)
        {
            if (data != null)
            {
                AlarmClockItem item = data.getParcelableExtra(AlarmEditActivity.EXTRA_ITEM);
                AlarmDatabaseAdapter.AlarmUpdateTask task = new AlarmDatabaseAdapter.AlarmUpdateTask(AlarmClockActivity.this, isNewAlarm, false);
                task.setTaskListener(onUpdateItem);
                task.execute(item);
            }
        }
    }

    protected void onSettingsResult(int resultCode, Intent data)
    {
        boolean recreateActivity = ((!AppSettings.loadThemePref(AlarmClockActivity.this).equals(appTheme))                           // theme mode changed
                //       || (appThemeOverride != null && !appThemeOverride.themeName().equals(getThemeOverride()))                       // or theme override changed
                || (localeInfo.localeMode != AppSettings.loadLocaleModePref(AlarmClockActivity.this))                             // or localeMode changed
                || ((localeInfo.localeMode == AppSettings.LocaleMode.CUSTOM_LOCALE                                              // or customLocale changed
                && !AppSettings.loadLocalePref(AlarmClockActivity.this).equals(localeInfo.customLocale)))
        );
        if (recreateActivity) {
            Handler handler = new Handler();
            handler.postDelayed(recreateRunnable, 0);    // post to end of execution queue (onResume must be allowed to finish before calling recreate)
        }
    }
    private Runnable recreateRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                recreate();

            } else {
                finish();
                startActivity(getIntent());
            }
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static void scheduleAlarm(Activity context, String label, SolarEvents event, com.forrestguice.suntimeswidget.calculator.core.Location location)
    {
        // TODO:
        Calendar alarm = new GregorianCalendar(TimeZone.getDefault());
        //alarm.setTimeInMillis(calendar.getTimeInMillis());
        int hour = alarm.get(Calendar.HOUR_OF_DAY);
        int minutes = alarm.get(Calendar.MINUTE);
        // TODO: local mean, apparent solar

        scheduleAlarm(context, label, event, location, hour, minutes, null);
    }

    public static void scheduleAlarm(Activity context, String label, SolarEvents event, com.forrestguice.suntimeswidget.calculator.core.Location location, int hour, int minutes, String timezone)
    {
        TimeZone tz = (timezone == null ? TimeZone.getDefault() : AlarmClockItem.AlarmTimeZone.getTimeZone(timezone, location));
        Calendar calendar = Calendar.getInstance(tz);

        Intent alarmIntent = new Intent(AlarmClock.ACTION_SET_ALARM);
        alarmIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        alarmIntent.putExtra(AlarmClock.EXTRA_MESSAGE, label);
        alarmIntent.putExtra(AlarmClock.EXTRA_HOUR, ((timezone == null) ? hour : calendar.get(Calendar.HOUR_OF_DAY)));
        alarmIntent.putExtra(AlarmClock.EXTRA_MINUTES, ((timezone == null) ? minutes : calendar.get(Calendar.MINUTE)));
        alarmIntent.putExtra(AlarmClockActivity.EXTRA_TIMEZONE, timezone);
        alarmIntent.putExtra(AlarmClockActivity.EXTRA_LOCATION, location);
        alarmIntent.putExtra(AlarmClockActivity.EXTRA_SOLAREVENT, (event != null ? event.name() : (String) null));

        if (alarmIntent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(alarmIntent);
        }
                                /*String alarmErrorTxt = getString(R.string.schedalarm_dialog_error) + "\n" + getString(R.string.schedalarm_dialog_note2, event.getLongDisplayString());
                        Toast alarmError = Toast.makeText(context, alarmErrorTxt, Toast.LENGTH_LONG);
                        alarmError.show();*/
    }


}
