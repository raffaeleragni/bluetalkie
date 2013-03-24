package ki.bluetalkie.activity;

import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListAdapter;
import android.widget.SimpleAdapter;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ki.bluetalkie.R;

/**
 * 
 * @author Raffaele Ragni <raffaele.ragni@gmail.com>
 */
public class JoinChannel extends ListActivity
{
	BluetoothAdapter bluetoothAdapter;
	
	private static final String KEY_ADDRESS = "address";
	
	private static final String KEY_NAME = "name";
	
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.join_channel);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null)
		{
			Toast.makeText(this, R.string.err_bluetoothNotAvailable, Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		
		getListView().setOnItemClickListener(new OnItemClickListener()
		{
			@SuppressWarnings("unchecked")
			public void onItemClick(AdapterView<?> a, View v, int position, long rowid)
			{
				Map<String, String> item = (Map<String, String>) getListView().getItemAtPosition(position);
				connectToChannel(item.get(KEY_ADDRESS));
			}
		});
    }
    
    @Override
    protected void onStart()
    {
    	super.onStart();
    	reloadChannels();
    }
    
    private void reloadChannels()
    {
    	Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
    	List<Map<String, String>> list = new ArrayList<Map<String, String>>();
    	for (BluetoothDevice d : pairedDevices)
    	{
    		// TODO verify if actually exists a channel
    		Map<String, String> item = new HashMap<String, String>();
    		item.put(KEY_ADDRESS, d.getAddress());
    		item.put(KEY_NAME, d.getName());
    		list.add(item);
    	}
        ListAdapter adapter = new SimpleAdapter(this, list, R.layout.join_channel_row,
            	new String[] {KEY_NAME, KEY_ADDRESS},
				new int[] {R.id.layout_join_channel_row_text1, R.id.layout_join_channel_row_text2});
        setListAdapter(adapter);
    }
    
    private void connectToChannel(String address)
    {
    	Intent i = new Intent(this, Channel.class);
		i.putExtra(Channel.PAR_MODE, Channel.MODE_JOIN);
		i.putExtra(Channel.PAR_ADDRESS, address);
		startActivity(i);
		finish();
    }
}
