package com.taskerator;

import android.app.ActivityManager;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Debug;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProcessActivity extends ListActivity {
  public static final String TAG = "ProcessActivity";


  Button refreshButton;
  List<ProcessData> dataList = new ArrayList<ProcessData>();
  ArrayAdapter<ProcessData> adapter;
  ListView listView;

  private static final DecimalFormat FORMAT = new DecimalFormat("0.00");
  private static final String SCHEME = "package";

  private ActionMode actionMode;

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);

    adapter = new ProcessListAdapter(this, android.R.layout.simple_dropdown_item_1line, dataList);
    setListAdapter(adapter);

    setContentView(R.layout.main);

    refreshButton = (Button) findViewById(R.id.refreshButton);
    if (Constants.API_LEVEL >= 14) {
      refreshButton.setVisibility(View.GONE);
    } else {
      refreshButton.setVisibility(View.VISIBLE);
      refreshButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          loadData();
        }
      });
    }


    listView = getListView();
    if (Constants.API_LEVEL >= 14) {
      contextualActionBarSupport();
    }
  }

  private void contextualActionBarSupport() {
    listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
    listView.setItemsCanFocus(false);

    listView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {

      @Override
      public void onItemCheckedStateChanged(ActionMode mode, int position,
                                            long id, boolean checked) {
        // Here you can do something when items are selected/de-selected,
        // such as update the title in the CAB
        int count = listView.getCheckedItemCount();
        if (count > 1) {
          mode.invalidate();
        } else if (count == 1) {
          mode.invalidate();
        }
        mode.setTitle(count + " Selected ");
      }

      @Override
      public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        // Respond to clicks on the actions in the CAB
        switch (item.getItemId()) {
          case R.id.cab_action_kill:
          {
            SparseBooleanArray items = listView.getCheckedItemPositions();
            StringBuilder sb = new StringBuilder();
            List<ProcessData> toKill = new ArrayList<ProcessData>(items.size());
            for (int i = 0; i < items.size(); i++) {
              final int key = items.keyAt(i);
              if (items.get(key)) {
                ProcessData data = (ProcessData) listView.getItemAtPosition(key);
                toKill.add(data);
              }
            }
            for (ProcessData data : toKill) {
              sb.append(data.pkgName).append(" killed\n");
            }

            killProcesses(toKill);
            Toast.makeText(ProcessActivity.this, sb.toString(), Toast.LENGTH_LONG).show();
            mode.finish(); // Action picked, so close the CAB
            return true;
          }
          case R.id.cab_action_appinfo:
          {
            int pos = -1;
            pos = getItemPos();
            if (pos == -1) return true;
            ProcessData data = (ProcessData) listView.getItemAtPosition(pos);
            ProcessActivity.showInstalledAppDetails(ProcessActivity.this, data.pkgName);
            return true;
          }
          case R.id.cab_action_uninstall:
          {
            int pos = getItemPos();
            if (pos == -1) return true;
            ProcessData data = (ProcessData) listView.getItemAtPosition(pos);
            Uri packageUri = Uri.parse("package:" + data.pkgName);
            Intent uninstallIntent = null;
            if (Constants.API_LEVEL >= 14) {
              uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri);
            } else {
              uninstallIntent = new Intent(Intent.ACTION_DELETE, packageUri);
            }
            startActivity(uninstallIntent);
            return true;
          }
          case R.id.cab_action_launch:
          {
            int pos = getItemPos();
            if (pos == -1) return true;
            ProcessData data = (ProcessData) listView.getItemAtPosition(pos);
            Intent intent = getPackageManager().getLaunchIntentForPackage(data.pkgName);
            mode.finish();
            if (intent == null) {
              Toast.makeText(ProcessActivity.this, getString(R.string.service_only, data.pkgName), Toast.LENGTH_LONG).show();
              return true;
            }
            startActivity(intent);
            return true;
          }
          default:
            return false;
        }
      }

      @Override
      public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        // Inflate the menu for the CAB
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.context_menu, menu);
        return true;
      }

      @Override
      public void onDestroyActionMode(ActionMode mode) {
        // Here you can make any necessary updates to the activity when
        // the CAB is removed. By default, selected items are deselected/unchecked.
      }

      @Override
      public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        // Here you can perform updates to the CAB due to
        // an invalidate() request
        final boolean show = listView.getCheckedItemCount() == 1;
        menu.findItem(R.id.cab_action_appinfo).setVisible(show);
        menu.findItem(R.id.cab_action_uninstall).setVisible(show);
        menu.findItem(R.id.cab_action_launch).setVisible(show);
        return true;
      }
    });
  }

  private int getItemPos() {
    SparseBooleanArray arr = listView.getCheckedItemPositions();
    for (int i = 0; i < arr.size(); i++) {
      int key = arr.keyAt(i);
      if (arr.get(key)) return key;
    }
    return -1;
  }

  @Override
  public void onResume() {
    super.onResume();
    loadData();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
      switch (item.getItemId()) {
          case R.id.menu_refresh:
              loadData();
              return true;
          default:
              return super.onOptionsItemSelected(item);
      }
  }

  public void loadData() {
    Log.d(TAG, "loaddata ----");
    final ProgressDialog dialog = ProgressDialog.show(this, "", "Loading info...");
    AsyncTask<Void, Void, List<ProcessData>> task = new AsyncTask<Void, Void, List<ProcessData>>() {
      @Override
      protected List<ProcessData> doInBackground(Void... voids) {

        Map<String,ProcessData> pkg2Data = new HashMap<String, ProcessData>();
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        PackageManager pm = ProcessActivity.this.getPackageManager();
        List<ActivityManager.RunningAppProcessInfo> list = activityManager.getRunningAppProcesses();

        List<ProcessData> procs = new ArrayList<ProcessData>(list.size());

        for (ActivityManager.RunningAppProcessInfo info : list) {
          // Skip the android native ones
          if (isSystemProcessName(info)) continue;

          String name;
          ApplicationInfo applicationInfo;
          try {
            applicationInfo = pm.getApplicationInfo(info.processName, PackageManager.GET_META_DATA);
            name = new StringBuilder().append(pm.getApplicationLabel(applicationInfo)).toString();
          } catch (PackageManager.NameNotFoundException e) {
            // ignore this one
            continue;
          } catch (Throwable ex) {
            ex.printStackTrace();
            continue;
          }
          ProcessData data = new ProcessData(name, info.processName, applicationInfo);
          data.pkglist = info.pkgList;
          data.pid = info.pid;
          data.mem = 0;
          pkg2Data.put(info.processName, data);
          procs.add(data);
        }

        List<ActivityManager.RunningServiceInfo> services = activityManager.getRunningServices(20);

        for (ActivityManager.RunningServiceInfo service: services) {
//          Log.d(TAG, "service -> " + service.process + " name " + service.clientPackage + " " + service.activeSince + " tostr " + service.toString()
//           + " x " + service.clientCount);
          ProcessData data = pkg2Data.get(service.process);
          if (data != null) {
            data.noOfServices++;
          }
        }

        return procs;
      }

      @Override
      public void onPostExecute(List<ProcessData> items) {
        adapter.clear();
        adapter.notifyDataSetInvalidated();
        for (ProcessData item : items) {
          adapter.add(item);
        }
        adapter.notifyDataSetChanged();
        dialog.dismiss();
      }
    };

    task.execute((Void) null);
  }

  private static final Set<String> excludedPackages = new HashSet<String>(
      Arrays.asList(
          "com.android.systemui", // system ui
          "com.android.defcontainer",  // package access helper
          "com.android.phone", // phone
          "com.android.launcher",
          "com.android.keychain",
          "com.android.nfc",
          "com.google.android.partnersetup",
          "com.android.voicedialer",
          "com.android.musicfx",
          "com.android.vending",
          "com.google.android.gsf.login",
          "com.android.packageinstaller",
          "com.android.providers.calendar",
          "com.android.bluetooth",
          "com.google.android.voicesearch",
          "com.android.settings",
          "system"
          )
  );

  private boolean isSystemProcessName(ActivityManager.RunningAppProcessInfo info) {
    final String processName = info.processName;
    if (excludedPackages.contains(processName)) return true;

    return
        processName.startsWith("com.google.android.inputmethod.") ||
        processName.startsWith("com.google.process.gapps") ||
        processName.startsWith("android.process.core");
  }

  public ActionMode getActionMode() {
    return actionMode;
  }

//  private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
//
//      // Called when the action mode is created; startActionMode() was called
//      @Override
//      public boolean onCreateActionMode(ActionMode mode, Menu menu) {
//          // Inflate a menu resource providing context menu items
//          MenuInflater inflater = mode.getMenuInflater();
//          inflater.inflate(R.menu.context_menu, menu);
//          return true;
//      }
//
//      // Called each time the action mode is shown. Always called after onCreateActionMode, but
//      // may be called multiple times if the mode is invalidated.
//    int count = 0;
//      @Override
//      public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
//        count++;
//        mode.setTitle("called " + count);
//          return false; // Return false if nothing is done
//      }
//
//      // Called when the user selects a contextual menu item
//      @Override
//      public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
//          switch (item.getItemId()) {
//              case R.id.cab_action_delete:
//                  Toast.makeText(ProcessActivity.this, "delete item clicked ", Toast.LENGTH_SHORT).show();
//                  mode.finish(); // Action picked, so close the CAB
//                  return true;
//              default:
//                  return false;
//          }
//      }
//
//      // Called when the user exits the action mode
//      @Override
//      public void onDestroyActionMode(ActionMode mode) {
//          actionMode = null;
//          count= 0;
//      }
//  };
//  public void beginActionMode() {
//    actionMode = startActionMode(mActionModeCallback);
//  }

//  @Override
//  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//    // Notice how the ListView api is lame
//    // You can use mListView.getCheckedItemIds() if the adapter
//    // has stable ids, e.g you're using a CursorAdaptor
//    SparseBooleanArray checked = listView.getCheckedItemPositions();
//    boolean hasCheckedElement = false;
//    for (int i = 0; i < checked.size() && !hasCheckedElement; i++) {
//      hasCheckedElement = checked.valueAt(i);
//    }
//
//    if (hasCheckedElement) {
//      if (actionMode == null) {
//        actionMode = startActionMode(new ModeCallback());
//      }
//    } else {
//      if (actionMode != null) {
//        actionMode.finish();
//      }
//    }
//  }


  private final class ModeCallback implements ActionMode.Callback {

      @Override
      public boolean onCreateActionMode(ActionMode mode, Menu menu) {
          // Create the menu from the xml file
          MenuInflater inflater = getMenuInflater();
          inflater.inflate(R.menu.context_menu, menu);
          return true;
      }

      @Override
      public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
          // Here, you can checked selected items to adapt available actions
          return false;
      }

      @Override
      public void onDestroyActionMode(ActionMode mode) {
          // Destroying action mode, let's unselect all items
          for (int i = 0; i < listView.getAdapter().getCount(); i++)
              listView.setItemChecked(i, false);

          if (mode == actionMode) {
              actionMode = null;
          }
      }

      @Override
      public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
          long[] selected = listView.getCheckedItemIds();
          if (selected.length > 0) {
              for (long id: selected) {
                  // Do something with the selected item
              }
          }
          mode.finish();
          return true;
      }
  };



  public static class ProcessData {
    public String processName;
    public int mem;
    private Drawable icon;
    public String[] pkglist;
    public int pid;
    public final String pkgName;
    public final ApplicationInfo applicationInfo;
    private MemInfo memInfo;
    private int noOfServices;

    public ProcessData(String procName, String packageName, ApplicationInfo packageInfo) {
      Log.d(TAG, "procName " + procName + " packageName " + packageName);
      this.processName = procName;
      this.pkgName = packageName;
      this.applicationInfo = packageInfo;
    }

    public static class MemInfo {
      public String memPretty;
      public float mem; // in mb
      private long time;

      public MemInfo(String memPretty, int mem) {
        this.memPretty = memPretty;
        this.mem = mem;
        this.time = System.currentTimeMillis();
      }

      public boolean isStale() {
        return System.currentTimeMillis() - time > 30000;
      }
    }

    public void fetchMemoryInfo(final ActivityManager activityManager, final ProcessListAdapter.ViewHolder holder) {
      if (memInfo != null && !memInfo.isStale()) {
        holder.label2.setText(memInfo.memPretty);
        return;
      }
      final ProcessData data = this;
      (new AsyncTask<Void,Void,ProcessData.MemInfo>() {

        @Override
        protected void onPreExecute() {
          holder.label2.setText("Loading...");
        }
        @Override
        protected ProcessData.MemInfo doInBackground(Void... voids) {
          return fetchMemoryInfo(activityManager);
        }

        @Override
        protected void onPostExecute(ProcessData.MemInfo memInfo) {
          data.memInfo = memInfo;
          if (holder.data.equals(data)) {

//          int percentWidth = (int) (memInfo.mem/maxMemoryPerApp * width);
            holder.label2.setText(memInfo.memPretty);
          }
        }
      }).execute((Void)null);
    }

    public MemInfo fetchMemoryInfo(ActivityManager activityManager) {
      Debug.MemoryInfo[] minfo = activityManager.getProcessMemoryInfo(new int[]{pid});
      if (minfo != null && minfo.length == 1) {
        final int totalPss = minfo[0].getTotalPss();
        StringBuilder extra = new StringBuilder();
        if (noOfServices > 0) {
          if (noOfServices > 1) {
            extra.append("(").append(noOfServices).append(" services)");
          } else {
            extra.append("(1 service)");
          }
        }
        if (totalPss < 1000) {
          return new MemInfo(String.valueOf(totalPss) + "KB " + extra, totalPss/1024);
        } else {
          float value = (totalPss *1.00f)/1024;
          return new MemInfo(FORMAT.format(value) + "MB " + extra, totalPss);
        }
      } else {
        return new MemInfo("NA", 1);
      }
    }
  }

  public void killProcessOnly(ProcessData data) {
    if ("Taskerator".equals(data.processName) && "com.taskerator".equals(data.pkgName)) {
      System.exit(0);
      return;
    }
    ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
    final String[] pkglist = data.pkglist;
    if (pkglist != null && pkglist.length > 0) {
      for (String pkg : pkglist) {
        activityManager.killBackgroundProcesses(pkg);
      }
    }
    if (data != null && data.icon != null) {
      data.icon.setCallback(null);
      final Bitmap bitmap = ((BitmapDrawable) data.icon).getBitmap();
      if (bitmap != null) {
        if (!bitmap.isRecycled()) {
          bitmap.recycle();
        }
      }
      data.icon = null;
    }
  }

  public void killProcess(ProcessData data) {
    killProcessOnly(data);
    // finally refresh the data
    loadData();
  }

  public void killProcesses(List<ProcessData> list) {
    try {
      for (ProcessData data : list) {
        killProcessOnly(data);
      }
      loadData();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public static void showInstalledAppDetails(Context context, String packageName) {
    Intent intent = new Intent();

    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    Uri uri = Uri.fromParts(SCHEME, packageName, null);
    intent.setData(uri);
    context.startActivity(intent);
  }
}
