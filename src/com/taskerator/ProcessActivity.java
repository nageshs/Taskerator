package com.taskerator;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.clover.sdk.Clover;
import com.clover.sdk.CloverOrder;
import com.clover.sdk.CloverOrderListener;
import com.clover.sdk.CloverOrderRequest;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProcessActivity extends ListActivity {
  public static final String TAG = "ProcessActivity";

  Clover cloverSDK;

  Button refreshButton;
  List<ProcessData> dataList = new ArrayList<ProcessData>();
  ArrayAdapter<ProcessData> adapter;
  private static final int DONATE = 1;

  private static final DecimalFormat FORMAT = new DecimalFormat("0.00");
  private static final String SCHEME = "package";

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);

    cloverSDK = Clover.init(this, "560140f3-37d6-49dc-8a47-1881ec69b5fe");

    adapter = new ProcessListAdapter(this, android.R.layout.simple_dropdown_item_1line, dataList);
    setListAdapter(adapter);

    setContentView(R.layout.main);

    refreshButton = (Button) findViewById(R.id.refreshButton);
    if (ProcessListAdapter.apiLevel >= 14) {
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
    Button donateButton = (Button) findViewById(R.id.donateButton);
//    donateButton.setVisibility(View.GONE);
//    if (false) {
      
      donateButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          showDialog(DONATE);
        }
      });
//    }
  }

  @Override
  public void onResume() {
    super.onResume();
    loadData();
  }

  public Dialog onCreateDialog(int which, Bundle bundle) {
    switch (which) {
      case DONATE:
      {
        LayoutInflater factory = LayoutInflater.from(this);
        final View textEntryView = factory.inflate(R.layout.alert_dialog_text_entry, null);
        return new AlertDialog.Builder(this)
            //.setIconAttribute(android.R.attr.alertDialog)
            .setTitle("Donate")
            .setView(textEntryView)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int whichButton) {
                EditText value = (EditText) textEntryView.findViewById(R.id.value);
                Number data = null;
                try {
                  data = FORMAT.parse(value.getText().toString());
                } catch (ParseException e) {
                  Toast.makeText(ProcessActivity.this, "Invalid amount", Toast.LENGTH_LONG).show();
                  showDialog(DONATE);
                  return;
                }
                final CloverOrderRequest order = cloverSDK.createOrderRequestBuilder()
                        .setAmount(FORMAT.format(data))
                        .setTitle("Donation to Taskerator")
                        .setPermissions(new String[]{"full_name", "email_address"})
                        .setClientOrderId("donation") // Specify an ID that identifies this item in your application. (such as an item id)
                        .build();
                cloverSDK.authorizeOrder(ProcessActivity.this, order, new CloverOrderListener() {
                  @Override
                  public void onOrderAuthorized(CloverOrder order) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(ProcessActivity.this);
                    builder.setTitle("Thank you!");
                    builder.setMessage("Thank you," + order.permissions.fullName + "! We really appreciate your donation.");
                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                      @Override
                      public void onClick(DialogInterface dialogInterface, int i) {
                        // no op
                      }
                    });
                    builder.show();
                  }

                  @Override
                  public void onCancel() {
                    //Toast.makeText(ProcessActivity.this, "Thanks for taking the interest to try out the app.", Toast.LENGTH_LONG).show();
                  }

                  @Override
                  public void onFailure(Throwable th) {
                    Toast.makeText(ProcessActivity.this, "Sorry, there was an error processing the request.", Toast.LENGTH_LONG).show();
                  }
                });
                
              }
            })
            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int whichButton) {

              }
            })
            .create();
      }
    }
    return null;
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

  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    cloverSDK.onResult(requestCode, resultCode, data);
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

  public void killProcess(ProcessData data) {
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
    // finally refresh the data
    loadData();
  }

  public static void showInstalledAppDetails(Context context, String packageName) {
    Intent intent = new Intent();

    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    Uri uri = Uri.fromParts(SCHEME, packageName, null);
    intent.setData(uri);
    context.startActivity(intent);
  }
}
