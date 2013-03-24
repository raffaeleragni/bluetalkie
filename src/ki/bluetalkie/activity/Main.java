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
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import ki.bluetalkie.R;

/**
 * 
 * @author Raffaele Ragni <raffaele.ragni@gmail.com>
 */
public class Main extends Activity
{
	BluetoothAdapter bluetoothAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		btnOpenChannel = (Button) findViewById(R.id.layout_main_btnOpenChannel);
		btnJoin = (Button) findViewById(R.id.layout_main_btnJoin);
		
		btnOpenChannel.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				createChannel();
			}
		});
		btnJoin.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				joinChannel();
			}
		});
		

		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null)
		{
			Toast.makeText(this, R.string.err_bluetoothNotAvailable, Toast.LENGTH_LONG).show();
			finish();
			return;
		}
	}

	@Override
	protected void onStart()
	{
		super.onStart();

		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (!bluetoothAdapter.isEnabled())
			startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BLUETOOTH);
	}
	
	/*                                    */
	/* ===== Intents and Activities ===== */
	/*                                    */
	
	private static final int REQUEST_ENABLE_BLUETOOTH = 1;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (requestCode)
		{
			case REQUEST_ENABLE_BLUETOOTH:
				if (resultCode != Activity.RESULT_OK)
				{
					Toast.makeText(this, R.string.err_bluetoothNotEnabled, Toast.LENGTH_LONG).show();
					finish();
				}
                break;
		}
	}

	/*                     */
	/* ===== BUTTONS ===== */
	/*                     */
	
	private void createChannel()
	{
		Intent i = new Intent(this, Channel.class);
		i.putExtra(Channel.PAR_MODE, Channel.MODE_HOST);
		startActivity(i);
	}
	
	private void joinChannel()
	{
		startActivity(new Intent(getBaseContext(), JoinChannel.class));
	}
	
	/*                  */
	/* ===== MENU ===== */
	/*                  */

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater mi = getMenuInflater();
		mi.inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menu_main_about:
				showDialog(DIALOG_ABOUT);
				break;
			case R.id.menu_main_pairing:
				// Use android's own bluetooth settings interface
                Intent launch = new Intent(Intent.ACTION_MAIN);
                launch.setClassName("com.android.settings", "com.android.settings.bluetooth.BluetoothSettings");
                startActivity(launch);
				break;
				// To reenable when the options will be implemented (private channel, etc.)
			case R.id.menu_main_settings:
				startActivity(new Intent(getBaseContext(), Preferences.class));
				break;
		}
		return true;
	}

	/*                     */
	/* ===== DIALOGS ===== */
	/*                     */

	private static final int DIALOG_ABOUT = 1;

	@Override
	protected Dialog onCreateDialog(int id)
	{
		switch (id)
		{
			case DIALOG_ABOUT:
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage(getResources().getText(R.string.dialog_about_text));
				return builder.create();
		}
		return super.onCreateDialog(id);
	}

	/*                          */
	/* ===== Layout views ===== */
	/*                          */
	
	Button btnOpenChannel;
	Button btnJoin;
}