package com.ivxin.killtheapp;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends Activity implements OnClickListener, OnCheckedChangeListener {
	// private static final String PACKAGE_NAME = "com.jingdong.app.mall";
	private static final String PACKAGE_NAME = "com.meowsbox.btgps";
	public static final String AUTO_FINISH = "AutoFinish";
	public static final String CONFIRM = "confirm";
	private static final int NOT_RUNNING = 0, CHECKOVER = 1, KILLED = 2, FINISH = 3;
	private String packageName;
	private boolean autoFinish;
	private boolean checkAgain = true;
	private SharedPreferences sp;
	private EditText et_package_name;
	private TextView tv_log;
	private Button btn_kill;
	private ToggleButton tb_confirm;
	private ScrollView sv;
	private CheckBox cb_auto_finish;
	private ActivityManager manager;
	private List<ProcessEntity> allProcessList = new ArrayList<ProcessEntity>();
	private List<ProcessEntity> processListToKill = new ArrayList<ProcessEntity>();
	private List<String> resultList = new ArrayList<String>();
	private Handler handler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case CHECKOVER:
				checkAgain = true;
				processListToKill = (List<ProcessEntity>) msg.obj;
				for (ProcessEntity entity : processListToKill) {
					appendLog("find pid:" + entity.getPID() + " " + entity.getNAME());
				}
				new Thread(new KillAPPTask(processListToKill)).start();
				break;
			case KILLED:
				ProcessEntity entity = (ProcessEntity) msg.obj;
				appendLog("Process:" + entity.getNAME() + " has killed.");
				break;
			case NOT_RUNNING:
				appendLog(packageName + " not running.");
			case FINISH:
				if (checkAgain) {
					checkAgain = false;
					new Thread(new KillAPPTask(processListToKill)).start();
				}
				if (cb_auto_finish.isChecked()) {
					appendLog("finish()...");
					finish();
				}
				break;
			default:
				break;
			}
		};
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		sp = getSharedPreferences("config", MODE_PRIVATE);
		manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		initView();
	}

	private void initView() {
		et_package_name = (EditText) findViewById(R.id.et_package_name);
		tv_log = (TextView) findViewById(R.id.tv_log);
		btn_kill = (Button) findViewById(R.id.btn_kill);
		tb_confirm = (ToggleButton) findViewById(R.id.tb_confirm);
		sv = (ScrollView) findViewById(R.id.sv);
		cb_auto_finish = (CheckBox) findViewById(R.id.cb_auto_finish);

		et_package_name.setText(sp.getString(PACKAGE_NAME, PACKAGE_NAME));
		cb_auto_finish.setChecked(sp.getBoolean(AUTO_FINISH, false));
		tb_confirm.setChecked(sp.getBoolean(CONFIRM, false));

		packageName = et_package_name.getText().toString();
		btn_kill.setText("KILL " + packageName);
		autoFinish = cb_auto_finish.isChecked();
		et_package_name.setEnabled(!tb_confirm.isChecked());
		et_package_name.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				AlertDialog.Builder builder = new Builder(MainActivity.this);
				TextView customTitleView = new TextView(MainActivity.this);
				customTitleView.setTextSize(8f);
				customTitleView.setMovementMethod(ScrollingMovementMethod.getInstance());
				for (String string : resultList) {
					customTitleView.append(string + "\n\n");
				}
				builder.setCustomTitle(customTitleView);
				builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
				builder.show();
				return true;
			}
		});
		cb_auto_finish.setOnCheckedChangeListener(this);
		tb_confirm.setOnCheckedChangeListener(this);
		btn_kill.setOnClickListener(this);
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		new Thread(new CheckTask()).start();
	}

	@Override
	public void onClick(View v) {
		tv_log.append("try killing　" + packageName + "\n");
		new Thread(new CheckTask()).start();
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		switch (buttonView.getId()) {
		case R.id.cb_auto_finish:
			autoFinish = isChecked;
			sp.edit().putBoolean(AUTO_FINISH, autoFinish).commit();
			break;
		case R.id.tb_confirm:
			et_package_name.setEnabled(!isChecked);
			packageName = et_package_name.getText().toString();
			btn_kill.setText("KILL " + packageName);
			sp.edit().putString(PACKAGE_NAME, packageName).putBoolean(CONFIRM, isChecked).commit();
			break;
		default:
			break;
		}

	}

	@Override
	public void finish() {
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				manager.killBackgroundProcesses(packageName);
				MainActivity.super.finish();
			}
		}, 1000);
	}

	private void appendLog(String log) {
		tv_log.append(log + "\n");
		sv.fullScroll(ScrollView.FOCUS_DOWN);
	}

	class CheckTask implements Runnable {

		@Override
		public void run() {
			resultList.clear();
			allProcessList.clear();
			List<ProcessEntity> processList = new ArrayList<ProcessEntity>();
			try {
				// Process process = Runtime.getRuntime().exec("ps grep " +
				// packageName);
				Process process = Runtime.getRuntime().exec("ps");
				process.waitFor();
				InputStream inputStream = process.getInputStream();
				BufferedReader read = new BufferedReader(new InputStreamReader(inputStream));
				String result = "";
				ArrayList<String> list;
				ProcessEntity entity;
				while ((result = read.readLine()) != null) {
					System.out.println(result);
					resultList.add(result);
					list = new ArrayList<String>();
					entity = new ProcessEntity();
					String[] colums = result.split(" ");
					for (int i = 0; i < colums.length; i++) {
						if (!TextUtils.isEmpty(colums[i])) {
							list.add(colums[i]);
						}
					}
					if (!"USER".equals(list.get(0)) && list.size() > 8) {
						entity.setUSER(list.get(0));
						entity.setPID(list.get(1));
						entity.setPPID(list.get(2));
						entity.setVSIZE(list.get(3));
						entity.setRSS(list.get(4));
						entity.setWCHAN(list.get(5));
						entity.setPC(list.get(6) + list.get(7));
						entity.setNAME(list.get(8));
						allProcessList.add(entity);
						if (list.get(8).contains(packageName)) {
							processList.add(entity);
							System.out.println("PID:" + entity.getPID());
						}
					}
				}
				if (processList.size() > 0) {
					Message msg = handler.obtainMessage();
					msg.what = CHECKOVER;
					msg.obj = processList;
					handler.sendMessage(msg);
				} else {
					handler.sendEmptyMessage(NOT_RUNNING);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	class KillAPPTask implements Runnable {
		List<ProcessEntity> processList;

		public KillAPPTask(List<ProcessEntity> processList) {
			this.processList = processList;
		}

		@Override
		public void run() {
			try {
				manager.killBackgroundProcesses(packageName);
				Thread.sleep(500);
				for (ProcessEntity entity : processList) {
					String exe = "su && kill -9 " + entity.getPID();
					System.out.println(exe);
					Process process = Runtime.getRuntime().exec(exe);
					process.waitFor();
					InputStream inputStream = process.getInputStream();
					BufferedReader read = new BufferedReader(new InputStreamReader(inputStream));
					String result = "";
					while ((result = read.readLine()) != null) {
						System.out.println(result);
					}
					Message msg = handler.obtainMessage();
					msg.what = KILLED;
					msg.obj = entity;
					handler.sendMessage(msg);
					Thread.sleep(500);
				}
				handler.sendEmptyMessage(FINISH);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}
}
