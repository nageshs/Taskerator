package com.taskerator;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

public class ProcessListAdapter extends ArrayAdapter<ProcessActivity.ProcessData> {

  private static final CompoundButton.OnCheckedChangeListener EMPTY_LISTENER = new CompoundButton.OnCheckedChangeListener() {
    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
      // no op intentional
    }
  };

  private ProcessActivity context;
  private List<ProcessActivity.ProcessData> items;
  private final ActivityManager activityManager;
  private final int maxMemoryPerApp;

  private static final CharSequence[] ITEMS = new CharSequence[] {
      "Kill Process",
      "Uninstall App",
      "App Info",
      "Cancel"
  };

  public ProcessListAdapter(ProcessActivity context, int textViewResourceId, List<ProcessActivity.ProcessData> items) {
    super(context, textViewResourceId, items);
    this.context = context;
    this.items = items;
    this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    this.maxMemoryPerApp = activityManager.getMemoryClass();
  }

  public static class ViewHolder {
    ImageView icon;
    TextView label1;
    TextView label2;
    CheckBox checkBox;
    public ProcessActivity.ProcessData data;
  }


  @Override
  public View getView(final int pos, final View convertView, ViewGroup viewGroup) {
    View rowView = convertView;
    ViewHolder holder;
    if (rowView == null) {
      LayoutInflater inflater = (LayoutInflater) context
          .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      rowView = inflater.inflate(R.layout.rowlayout2, viewGroup, false);
      holder = new ViewHolder();

      holder.icon = (ImageView) rowView.findViewById(R.id.icon);
      holder.label1 = (TextView) rowView.findViewById(R.id.label);
      holder.label2 = (TextView) rowView.findViewById(R.id.label2);
      holder.checkBox = (CheckBox) rowView.findViewById(R.id.checkBox);
      rowView.setTag(holder);
    } else {
      holder = (ViewHolder) rowView.getTag();
    }
    final ProcessActivity.ProcessData data = getItem(pos);
    holder.data = data;

    final ViewHolder theHolder = holder;

    theHolder.icon.setImageDrawable(data.applicationInfo.loadIcon(context.getPackageManager()));
    holder.label1.setText(data.processName);
    data.fetchMemoryInfo(activityManager, holder);

    if (Constants.API_LEVEL >= 14) {
      final SparseBooleanArray checkedItemPositions = context.listView.getCheckedItemPositions();
      holder.checkBox.setOnCheckedChangeListener(EMPTY_LISTENER);
      holder.checkBox.setChecked(checkedItemPositions.get(pos));

      theHolder.checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean selected) {
          ((ListView) compoundButton.getParent().getParent().getParent()).setItemChecked(pos, selected);
        }
      });
      rowView.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          theHolder.checkBox.toggle();
        }
      });
    } else {
      holder.checkBox.setVisibility(View.GONE);
      rowView.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          Bundle bundle = new Bundle();
          bundle.putString("name", data.processName);
          bundle.putString("packageName", data.pkgName);

          AlertDialog.Builder builder = new AlertDialog.Builder(context);
          builder.setTitle(data.processName);
          builder.setItems(ITEMS, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
              if (item == 0) {
                context.killProcess(data);
              } else if (item == 1) {
                Uri packageUri = Uri.parse("package:" + data.pkgName);
                Intent uninstallIntent = null;
                if (Constants.API_LEVEL >= 14) {
                  uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri);
                } else {
                  uninstallIntent = new Intent(Intent.ACTION_DELETE, packageUri);
                }
                context.startActivity(uninstallIntent);
              } else if (item == 2) {
                ProcessActivity.showInstalledAppDetails(context, data.pkgName);
              } else {
                //dialog.dismiss();
              }
            }
          });
          builder.show();
        }
      });

    }
    return rowView;
  }
}
