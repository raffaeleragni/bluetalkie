/*
This file is part of bluetalkie.

bluetalkie is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

bluetalkie is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with bluetalkie.  If not, see <http://www.gnu.org/licenses/>.
*/
package ki.bluetalkie.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import java.util.ArrayList;
import ki.bluetalkie.R;
import ki.bluetalkie.service.ChannelService;
import ki.bluetalkie.service.IChannelService;

/**
 * 
 * @author Raffaele Ragni <raffaele.ragni@gmail.com>
 */
public class Channel extends Activity
{
	public static final String PAR_MODE = "mode";

	public static final String PAR_ADDRESS = "address";
	
	public static final String PAR_RECONNECT_SERVICE = "reconnect_service";

	public static final int MODE_HOST = 1;

	public static final int MODE_JOIN = 2;

	int mode;

	String channelName;

	String address;

	Intent service;

	IChannelService serviceBinder;
	
	boolean reconnectService;
	
	ImageView imgRX, imgTX;

    // Messages incoming TO this activity
	Handler serviceListener = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			ArrayList<String> names;
			String name;
			switch (msg.what)
			{
				case IChannelService.MSG_USER_CONNECTED:
					name = msg.getData().getString("name");
					names = msg.getData().getStringArrayList("names");
					updateNames(names);
					Toast.makeText(Channel.this, getText(R.string.layout_channel_user_connected) + ": " + name, Toast.LENGTH_LONG).show();
					break;

				case IChannelService.MSG_USER_DISCONNECTED:
					name = msg.getData().getString("name");
					names = msg.getData().getStringArrayList("names");
					updateNames(names);
					Toast.makeText(Channel.this, getText(R.string.layout_channel_user_disconnected) + ": " + name, Toast.LENGTH_LONG).show();
					break;

				case IChannelService.MSG_USER_LIST_UPDATE:
					names = msg.getData().getStringArrayList("names");
					updateNames(names);
					break;
					
				case IChannelService.MSG_AUDIO_INCOMING_START:
					imgRX.setVisibility(View.VISIBLE);
					break;

				case IChannelService.MSG_AUDIO_INCOMING_STOP:
					imgRX.setVisibility(View.INVISIBLE);
					break;

				case IChannelService.MSG_AUDIO_OUTGOING_START:
					imgTX.setVisibility(View.VISIBLE);
					break;

				case IChannelService.MSG_AUDIO_OUTGOING_STOP:
					imgTX.setVisibility(View.INVISIBLE);
					break;

				case IChannelService.MSG_ERROR:
					Toast.makeText(Channel.this, "error: " + msg.getData().getString("error"), Toast.LENGTH_LONG).show();
					break;

				case IChannelService.MSG_CHANNEL_DETAILS:
					try {dismissDialog(DIALOG_CONNECTING);} catch (Exception e) {} // The dialog may not be there, that would throw an ex.
					Toast.makeText(Channel.this, getText(R.string.layout_channel_connected), Toast.LENGTH_LONG).show();
							/*"channelName: " + msg.getData().getString("channelName") + "\nvisible: "
									+ msg.getData().getBoolean("visible") + "\ninviteOnly: "
									+ msg.getData().getBoolean("inviteOnly")*/
					break;
					
				case IChannelService.MSG_CHANNEL_CLOSED:
					if (MODE_JOIN == mode)
					{
						stopService(service);
						unbindService(serviceConnection);
						finish();
					}
					break;
			}
		}
	};
	
	ServiceConnection serviceConnection = new ServiceConnection()
	{
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			if (service instanceof IChannelService)
			{
				serviceBinder = (IChannelService) service;

				// TODO: this would represent each time the app gets rotated, fix later
//				if (!reconnectService && mode == MODE_JOIN)
//					showDialog(DIALOG_CONNECTING);
				
				// Always rebind the handler
				serviceBinder.setActivityHandler(serviceListener);
				updateNames(serviceBinder.getUserList());
				// In case, reset the view to talking mode if the user was already talking
				if (serviceBinder.isTalking())
				{
                    if (serviceBinder.isPTT())
                    {
                        ToggleButton toggle = (ToggleButton) findViewById(R.id.layout_channel_btnToggleTalk);
                        Button l = (Button) findViewById(R.id.layout_channel_btnClickToTalk);
                        toggle.setChecked(serviceBinder.isTalking());
                        l.setEnabled(false);
                    }
					imgTX.setVisibility(View.VISIBLE);
				}				
				
				if (!reconnectService)
				{
					if (serviceBinder.getStatus() == IChannelService.STATUS_NULL)
					{
						switch (mode)
						{
							case MODE_HOST:
								serviceBinder.createChannel(channelName);
								break;
	
							case MODE_JOIN:
								serviceBinder.joinChannel(address);
								break;
						}
					}
				}
			}
		}

		public void onServiceDisconnected(ComponentName name)
		{
		}
	};
	
	private static final int DIALOG_CONNECTING = 1;
	
    @Override
	protected android.app.Dialog onCreateDialog(int id)
	{
		switch (id)
		{
			case DIALOG_CONNECTING:
				return ProgressDialog.show(this, "", getText(R.string.layout_channel_connecting), true, true);
		}
	
		return null;
	};

	private void updateNames(ArrayList<String> names)
	{
		StringBuilder sb = new StringBuilder();
		if (names != null && !names.isEmpty())
		{
			sb.append(names.get(0));
			for (int i = 1; i < names.size(); i++)
				sb.append(", ").append(names.get(i));
		}
		lblNames.setText(getText(R.string.layout_channel_userConnected) + ": " + sb.toString());
	}
	
	TextView lblNames;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		// INTERFACE
		super.onCreate(savedInstanceState);
		setContentView(R.layout.channel);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		lblNames = (TextView) findViewById(R.id.layout_channel_lblConnectedUsers);
		
		if (savedInstanceState != null)
			lblNames.setText(savedInstanceState.getString("lblNames"));
		
		imgRX = (ImageView) findViewById(R.id.layout_channel_imgRX);
		imgTX = (ImageView) findViewById(R.id.layout_channel_imgTX);
		Button closeChannel = (Button) findViewById(R.id.layout_channel_btnCloseChannel);
		closeChannel.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				stopService(service);
				unbindService(serviceConnection);
				finish();
			}
		});
		final Button l = (Button) findViewById(R.id.layout_channel_btnClickToTalk);
		l.setOnKeyListener(new OnKeyListener()
		{
			public boolean onKey(View v, int keyCode, KeyEvent event)
			{
				if (serviceBinder != null && keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.getAction() == KeyEvent.ACTION_DOWN)
					serviceBinder.startTalking();
				if (serviceBinder != null && keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.getAction() == KeyEvent.ACTION_UP)
					serviceBinder.stopTalking();
				return true;
			}
		});
		l.setOnTouchListener(new OnTouchListener()
		{
			public boolean onTouch(View v, MotionEvent event)
			{
				if (serviceBinder != null && event.getAction() == MotionEvent.ACTION_DOWN)
					serviceBinder.startTalking();
				if (serviceBinder != null && event.getAction() == MotionEvent.ACTION_UP)
					serviceBinder.stopTalking();
				
				return false;
			}
		});
		final ToggleButton toggle = (ToggleButton) findViewById(R.id.layout_channel_btnToggleTalk);
		toggle.setOnCheckedChangeListener(new OnCheckedChangeListener()
		{
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				if (serviceBinder == null)
					return;
				
				if (isChecked)
				{
					l.setEnabled(false);
					serviceBinder.startTalking();
				}
				else
				{
					serviceBinder.stopTalking();
					l.setEnabled(true);
				}
			}
		});
		
		// VARS
		reconnectService = savedInstanceState != null && savedInstanceState.containsKey(Channel.PAR_RECONNECT_SERVICE) ?
				savedInstanceState.getBoolean(Channel.PAR_RECONNECT_SERVICE) :
				getIntent().getExtras().getBoolean(Channel.PAR_RECONNECT_SERVICE);
		mode = savedInstanceState != null && savedInstanceState.containsKey(Channel.PAR_MODE) ?
				savedInstanceState.getInt(Channel.PAR_MODE) :
				getIntent().getExtras().getInt(Channel.PAR_MODE);
		address = savedInstanceState != null && savedInstanceState.containsKey(Channel.PAR_ADDRESS) ?
				savedInstanceState.getString(Channel.PAR_ADDRESS) :
				getIntent().getExtras().getString(Channel.PAR_ADDRESS);

		// SERVICE
		service = new Intent();
		service.setClass(this, ChannelService.class);
		if (!reconnectService)
		{
			service.putExtra(Channel.PAR_MODE, mode);
			service.putExtra(Channel.PAR_ADDRESS, address);
			service.putExtra(Channel.PAR_RECONNECT_SERVICE, false);
			bindService(service, serviceConnection, Context.BIND_AUTO_CREATE);
			startService(service);
		}
		else
		{
			service.putExtra(Channel.PAR_MODE, mode);
			service.putExtra(Channel.PAR_ADDRESS, address);
			service.putExtra(Channel.PAR_RECONNECT_SERVICE, true);
			bindService(service, serviceConnection, Context.BIND_AUTO_CREATE);
		}
	}
	
	@Override
	protected void onDestroy()
	{
		try
		{
			unbindService(serviceConnection);
		}
		catch (Exception e)
		{
		}
		super.onDestroy();
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		// If the activity is going down, means the service would still be keeping up.
		// Next time we restore just reconnect to the service
		outState.putBoolean(Channel.PAR_RECONNECT_SERVICE, true);
		outState.putInt(Channel.PAR_MODE, mode);
		outState.putString(Channel.PAR_ADDRESS, address);
		outState.putString("lblNames", lblNames.getText().toString());
		super.onSaveInstanceState(outState);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater mi = getMenuInflater();
		mi.inflate(R.menu.channel, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menu_channel_quit:
				stopService(service);
				unbindService(serviceConnection);
				finish();
				break;
		}

		return true;
	}
}
