package ki.bluetalkie.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import ki.bluetalkie.R;
import ki.bluetalkie.activity.Channel;

/**
 * 
 * @author Raffaele Ragni <raffaele.ragni@gmail.com>
 */
public class ChannelService extends Service
{
	private static final int NOTIFICATION_ELEMENT = 100;
    
    // How much to poll wait when we detect there are no packets to read. (when available() == 0), in msecs
    private static final int PROTOCOL_WAIT_BETWEEN_PACKETS = 10;
    
    private static final short AUDIO_TRESHOLD = 500; // TODO define
    
    private static final short AUDIO_AUTOTALK_TIMEOUT = 2500; // TODO define

	private IChannelServiceImpl binder = new IChannelServiceImpl();
	
	// This handler will communicate message back to the activity, for the User to view
	private Handler activityHandler = new Handler()
	{
	};

	@Override
	public IBinder onBind(Intent intent)
	{
		return binder;
	}

	@Override
	public void onStart(Intent intent, int startId)
	{
		super.onStart(intent, startId);

		boolean reconnectService = intent == null || intent.getExtras() == null ? false : intent.getExtras().getBoolean(Channel.PAR_RECONNECT_SERVICE);
		
		if (!reconnectService && intent != null && intent.getExtras() != null)
		{
			Log.d(IChannelService.SERVICE_NAME, "starting service");
	
			// The user can always return to channel screen if service remains active
			NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			Notification n = new Notification(R.drawable.icon, getText(R.string.service_active_channel), System.currentTimeMillis());
			n.flags |= Notification.FLAG_ONGOING_EVENT;
			Context context = getApplicationContext();
			Intent notificationIntent = new Intent(this, Channel.class);
			notificationIntent.putExtra(Channel.PAR_MODE, intent.getExtras().getInt(Channel.PAR_MODE));
			notificationIntent.putExtra(Channel.PAR_ADDRESS, intent.getExtras().getString(Channel.PAR_ADDRESS));
			notificationIntent.putExtra(Channel.PAR_RECONNECT_SERVICE, true);
			PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
			n.setLatestEventInfo(context, getText(R.string.service_active_channel), "", contentIntent);
			nm.notify(NOTIFICATION_ELEMENT, n);
		}
	}
	
	@Override
	public void onDestroy()
	{
		Log.d(IChannelService.SERVICE_NAME, "destroying service");

		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel(NOTIFICATION_ELEMENT);
		
		closeChannel();
		
		super.onDestroy();
	}

	/*                       */
	/* ===== Internals ===== */
	/*                       */

	private static final UUID MY_UUID = new UUID(0xAE01BC10, 0xCAE57020);

	private static final int AUDIO_FREQ = 8000;

	private int status = IChannelService.STATUS_NULL;

	private boolean connected = false;

	private boolean isReceiving = false;

	private String channelName;

	private boolean visible = true;

	private boolean inviteOnly = false;
    
    private boolean autoTransmit = false;

	private AudioTrack audioTrack;

	private AudioRecord audioRecorder;

	private AudioRecorderThread audioRecorderThread;

	private BluetoothAdapter bluetoothAdapter;

	private ServerListener serverListener;

	private List<SocketReader> socketReaders;

	private SocketReader socketReader;
	
	private int beepRES = R.raw.click;
	
	private volatile boolean isTalking = false;
    
    private volatile boolean isPTT = false;
    
    private volatile boolean muteInput = false;
    
    private volatile boolean muteOutput = false;
	
	private final Object PLAYER_LOCK = new Object();
    
    private final Object SOCKET_WRITE_LOCK = new Object();
    
    private MediaPlayer beepPlayer;
	
	private void reInitPlayer()
	{
		synchronized (PLAYER_LOCK)
		{
			if (audioTrack != null)
				audioTrack.release();
			
			audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, AUDIO_FREQ, AudioFormat.CHANNEL_CONFIGURATION_MONO,
					AudioFormat.ENCODING_PCM_16BIT, AudioTrack.getMinBufferSize(AUDIO_FREQ, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT),
					AudioTrack.MODE_STREAM);
		}
	}
	
	private void initAudio()
	{
		reInitPlayer();
		audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, AUDIO_FREQ, AudioFormat.CHANNEL_CONFIGURATION_MONO,
				AudioFormat.ENCODING_PCM_16BIT, AudioRecord.getMinBufferSize(AUDIO_FREQ, AudioFormat.CHANNEL_CONFIGURATION_MONO,
						AudioFormat.ENCODING_PCM_16BIT));
	}

	private void createChannel(String channelName, boolean visible, boolean inviteOnly)
	{
		status = IChannelService.STATUS_INITIALIZED;

		this.channelName = channelName;
		this.visible = visible;
		this.inviteOnly = inviteOnly;

        // Register the broadcast receiver to detect if a phone call is incoming.
        registerReceiver(phoneCallDetector, new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED));

        // Obtain preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		String myName = prefs.getString("myName", "<anonymous>");
        this.autoTransmit = prefs.getBoolean("voiceActivation", this.autoTransmit);
		String soundType = prefs.getString("beepSound", "click");
		beepRES = "beep".equals(soundType) ? R.raw.beep : R.raw.click;
		
        beepPlayer = MediaPlayer.create(getBaseContext(), beepRES);
        
		userList = new ArrayList<String>();
		userConnectedEvent(myName);

		Log.d(IChannelService.SERVICE_NAME, "creating channel: " + channelName + ", visible: " + visible + ", inviteOnly: " + inviteOnly);

		initAudio();
		
		serverListener = new ServerListener();
		serverListener.start();
        
        // Start the recorder, this will be used for audio auto detection
        audioRecorderThread = new AudioRecorderThread();
        audioRecorderThread.start();
	}

	private void joinChannel(String address)
	{
		status = IChannelService.STATUS_INITIALIZED;
        
        // Register the broadcast receiver to detect if a phone call is incoming.
        registerReceiver(phoneCallDetector, new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED));

		Log.d(IChannelService.SERVICE_NAME, "joining address: " + address);

        // Obtain preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		String myName = prefs.getString("myName", "<anonymous>");
        this.autoTransmit = prefs.getBoolean("voiceActivation", this.autoTransmit);
		String soundType = prefs.getString("beepSound", "click");
		beepRES = "beep".equals(soundType) ? R.raw.beep : R.raw.click;
        
        beepPlayer = MediaPlayer.create(getBaseContext(), beepRES);
		
        // Check if bluetooth is ready and available
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null)
		{
			Message m = activityHandler.obtainMessage(IChannelService.MSG_ERROR);
			Bundle b = new Bundle();
			b.putString("error", getText(R.string.err_bluetoothNotAvailable).toString());
			m.setData(b);
			activityHandler.sendMessage(m);
			return;
		}

		BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);

        // Connect to remote server
		try
		{
			BluetoothSocket socket = device.createRfcommSocketToServiceRecord(MY_UUID);
			socket.connect();
			connected = true;
			status = IChannelService.STATUS_JOINED;
			initAudio();
			ObjectOutputStream os = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
			// Make the first attempt of communication: ask for details
			ProtocolPacket pp = new ProtocolPacket();
			pp.type = ProtocolPacket.PPTYPE_ASK_CONNECTION_INFO;
			pp.channelName = myName;
			os.writeObject(pp);
			os.flush();
			// Input stream will wait until a response come
			InputStream sis = socket.getInputStream();
			BufferedInputStream bis = new BufferedInputStream(sis);
			socketReader = new SocketReader(socket.getRemoteDevice().getAddress(), socket, os, bis);
			socketReader.start();
            // Handshaking and connection finished: start the recorder, this will be used for audio auto detection
            audioRecorderThread = new AudioRecorderThread();
            audioRecorderThread.start();
		}
		catch (IOException e)
		{
			Log.e(IChannelService.SERVICE_NAME, e.getMessage(), e);
			Message m = activityHandler.obtainMessage(IChannelService.MSG_ERROR);
			Bundle b = new Bundle();
			b.putString("error", getText(R.string.err_bluetoothNotEnabled).toString() + ": " + e.getMessage());
			m.setData(b);
			activityHandler.sendMessage(m);
		}
	}

	private void startTalking()
	{
		Log.d(IChannelService.SERVICE_NAME, "startTalking()");
		
		// Only if connected
		if (!connected)
			return;

		// Only send to correct status
		if (status != IChannelService.STATUS_CREATED && status != IChannelService.STATUS_JOINED)
			return;
        
        isTalking = true;
        isPTT = true;
	}

	private void stopTalking()
	{
		Log.d(IChannelService.SERVICE_NAME, "stopTalking()");
		
        if (audioRecorderThread != null)
            audioRecorderThread.finishTalk();
        
		isTalking = false;
        isPTT = false;
	}

	/**
	 * Reset the service to its default state
	 */
	private void closeChannel()
	{
		Log.d(IChannelService.SERVICE_NAME, "closing channel");
		
        if (beepPlayer != null)
        {
            beepPlayer.release();
            beepPlayer = null;
        }
        
        if (audioRecorderThread != null)
        {
            audioRecorderThread.interrupt();
            audioRecorderThread = null;
        }
        
		if (socketReader != null)
		{
			socketReader.interrupt();
			socketReader = null;
		}

		if (socketReaders != null)
		{
			for (SocketReader reader : socketReaders)
			{
				reader.interrupt();
			} 
			socketReaders.clear();
		}

		if (serverListener != null)
		{
			serverListener.interrupt();
			serverListener = null;
		}
		
		status = IChannelService.STATUS_NULL;

		if (audioTrack != null)
		{
			audioTrack.stop();
			audioTrack.release();
			audioTrack = null;
		}
		
		if (audioRecorder != null)
		{
			audioRecorder.stop();
			audioRecorder.release();
			audioRecorder = null;
		}
        
        // Unregister the phone call detector.
        unregisterReceiver(phoneCallDetector);
	}
	
	private ArrayList<String> userList = new ArrayList<String>();
	
	private void userConnectedEvent(String name)
	{
		userList.add(name);
		
		Message m = activityHandler.obtainMessage(IChannelService.MSG_USER_CONNECTED);
		Bundle b = new Bundle();
		b.putString("name", name);
		b.putStringArrayList("names", userList);
		m.setData(b);
		activityHandler.sendMessage(m);
		
		// Send finish signal of the communication to all sockets
		if (socketReaders != null)
		{
			for (SocketReader reader : socketReaders)
			{
				ObjectOutputStream os = reader.getOutput();
				ProtocolPacket pp = new ProtocolPacket();
				pp.type = ProtocolPacket.PPTYPE_USER_LIST;
				pp.userList = userList;
				try
				{
					os.writeObject(pp);
					os.flush();
				}
				catch (IOException e)
				{
                    if (e != null && e.getMessage() != null && e.getMessage().length() > 0)
                        Log.w(IChannelService.SERVICE_NAME, e.getMessage());
				}
			}
		}
	}
	
	private void userDisconnectedEvent(String name)
	{
		userList.remove(name);
		
		Message m = activityHandler.obtainMessage(IChannelService.MSG_USER_DISCONNECTED);
		Bundle b = new Bundle();
		b.putString("name", name);
		b.putStringArrayList("names", userList);
		m.setData(b);
		activityHandler.sendMessage(m);
		
		// Send finish signal of the communication to all sockets
		if (socketReaders != null)
		{
			for (SocketReader reader : socketReaders)
			{
				ObjectOutputStream os = reader.getOutput();
				ProtocolPacket pp = new ProtocolPacket();
				pp.type = ProtocolPacket.PPTYPE_USER_LIST;
				pp.userList = userList;
				try
				{
					os.writeObject(pp);
					os.flush();
				}
				catch (IOException e)
				{
					Log.e(IChannelService.SERVICE_NAME, e.getMessage(), e);
				}
			}
		}
	}

	private void userListUpdate()
	{
		Message m = activityHandler.obtainMessage(IChannelService.MSG_USER_LIST_UPDATE);
		Bundle b = new Bundle();
		b.putStringArrayList("names", userList);
		m.setData(b);
		activityHandler.sendMessage(m);
	}

    // Handle an incoming packet TO this endpoint coming from a remote of any kind.
	private void handleInputPacket(BluetoothSocket socketFrom, ProtocolPacket _pp, ObjectOutputStream os)
	{
		Bundle b;
		Message m;
		switch (_pp.type)
		{
			case ProtocolPacket.PPTYPE_USER_LIST:
				userList = _pp.userList;
				userListUpdate();
				break;
			
			case ProtocolPacket.PPTYPE_ASK_CONNECTION_INFO:
				userConnectedEvent(_pp.channelName);
				// Send channel info
				ProtocolPacket pp = new ProtocolPacket();
				pp.type = ProtocolPacket.PPTYPE_REPLY_CONNECTION_INFO;
				pp.channelName = channelName;
				pp.visible = visible;
				pp.inviteOnly = inviteOnly;
				try
				{
					os.writeObject(pp);
					os.flush();
				}
				catch (IOException e)
				{
					Log.e(IChannelService.SERVICE_NAME, e.getMessage(), e);
				}
				break;

			case ProtocolPacket.PPTYPE_REPLY_CONNECTION_INFO:
				b = new Bundle();
				b.putString("channelName", _pp.channelName);
				b.putBoolean("visible", _pp.visible);
				b.putBoolean("inviteOnly", _pp.inviteOnly);
				m = activityHandler.obtainMessage(IChannelService.MSG_CHANNEL_DETAILS);
				m.setData(b);
				activityHandler.sendMessage(m);
				break;

			case ProtocolPacket.PPTYPE_AUDIO_DATA:
				if (!isReceiving)
					Log.d(IChannelService.SERVICE_NAME, "Start receiving");
				isReceiving = true;
				activityHandler.sendEmptyMessage(IChannelService.MSG_AUDIO_INCOMING_START);
				// forward audio data if this is the server, to anyone except the sender
				if (socketReaders != null && !socketReaders.isEmpty())
					for (SocketReader s : socketReaders)
					{
						if (s.socket == socketFrom)
							continue;
						
						try
						{
							s.os.writeObject(_pp);
							s.os.flush();
						}
                        catch (StreamCorruptedException e)
                        {
                            // TODO what to do?
                        }
						catch (IOException e)
						{
							Log.i(IChannelService.SERVICE_NAME, e.getMessage());
						}
					}
				// Play audio now
                writeAudio(_pp.audioData);
				break;
				
			case ProtocolPacket.PPTYPE_AUDIO_DATA_STOP:
				isReceiving = false;
				writeAudio(null); // this empties the audio and gives a play trigger
				// forward audio data if this is the server, to anyone except the sender
				if (socketReaders != null && !socketReaders.isEmpty())
					for (SocketReader s : socketReaders)
					{
						if (s.socket == socketFrom)
							continue;
						
						try
						{
							s.os.writeObject(_pp);
							s.os.flush();
						}
                        catch (StreamCorruptedException e)
                        {
                            // TODO: ???
                        }
						catch (IOException e)
						{
							Log.e(IChannelService.SERVICE_NAME, e.getMessage(), e);
						}
					}
				Log.d(IChannelService.SERVICE_NAME, "Stop receiving");
				playBeep();
				activityHandler.sendEmptyMessage(IChannelService.MSG_AUDIO_INCOMING_STOP);
				break;
				
			case ProtocolPacket.PPTYPE_CHANNEL_CLOSE:
				activityHandler.sendEmptyMessage(IChannelService.MSG_CHANNEL_CLOSED);
				break;
		}
	}
	
	private void playBeep()
	{	
        if (!muteOutput)
            beepPlayer.start();
	}

	/*                                     */
	/* ===== Bluetooth/Audio threads ===== */
	/*                                     */

	/**
	 * This thread remains listening and queues clients connections to other threads.
	 */
	private class ServerListener extends Thread
	{
		private BluetoothServerSocket serverSocket;

		@Override
		public void run()
		{
			BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (bluetoothAdapter == null)
			{
				Message m = activityHandler.obtainMessage(IChannelService.MSG_ERROR);
				Bundle b = new Bundle();
				b.putString("error", getText(R.string.err_bluetoothNotAvailable).toString());
				m.setData(b);
				activityHandler.sendMessage(m);
				return;
			}

			try
			{
				socketReaders = new ArrayList<SocketReader>();
				serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("bluetalkie", MY_UUID);
				status = IChannelService.STATUS_CREATED;
				connected = true;

				Log.d(IChannelService.SERVICE_NAME, "ServerListener started, accepting connections");

				try
				{
					while (true)
					{
						if (Thread.interrupted())
							throw new InterruptedException();

						BluetoothSocket socket = serverSocket.accept();

						if (socket != null)
						{
							String address = socket.getRemoteDevice().getAddress();
							
							ObjectOutputStream os = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
							SocketReader socketReader = new SocketReader(address, socket, os, new BufferedInputStream(socket.getInputStream()));
							socketReaders.add(socketReader);
							socketReader.start();
						}
					}
				}
				finally
				{
					Log.d(IChannelService.SERVICE_NAME, "Stopping ServerListener");
					
					if (serverSocket != null)
					{
						serverSocket.close();
						serverSocket = null;
					}
				}
			}
			catch (InterruptedException e)
			{
			}
			catch (IOException e)
			{
				Log.e(IChannelService.SERVICE_NAME, e.getMessage(), e);
			}
		}
	}

	/**
	 * This thread continues to read from a socket and handles input data
	 */
	private class SocketReader extends Thread
	{
		private String name;
		
		private final BluetoothSocket socket;

		private ObjectOutputStream os;
		
		private InputStream is;

		public SocketReader(String name, BluetoothSocket socket, ObjectOutputStream os, InputStream is)
		{
			this.name = name;
			this.socket = socket;
			this.os = os;
			this.is = is;
		}

		public ObjectOutputStream getOutput()
		{
			return os;
		}

		@Override
		public void run()
		{
			try
			{
				try
				{
					ObjectInputStream ois = new ObjectInputStream(is); // THIS IS BLOCKING UNLESS RECEIVED A PACKET
					Log.d(IChannelService.SERVICE_NAME, "SocketReader started, reading data");
					
					while (true)
					{
						try
						{
							if (Thread.interrupted())
								throw new InterruptedException();
                            
                            if (ois.available() == 0)
                                Thread.sleep(PROTOCOL_WAIT_BETWEEN_PACKETS);
							
							ProtocolPacket pp = null;
							try { pp = (ProtocolPacket) ois.readObject(); } catch (ClassCastException e) {}
							
							if (pp == null)
								continue;
							
							// Exception: if the message is closing socket, handle it correctly and disconnect too
							if (pp.type == ProtocolPacket.PPTYPE_CHANNEL_CLOSE)
								userDisconnectedEvent(pp.channelName);
							
							handleInputPacket(socket, pp, os);
							
							// ... disconnect too
							if (pp.type == ProtocolPacket.PPTYPE_CHANNEL_CLOSE)
								break;
						}
						catch (ClassNotFoundException e)
						{
							Log.e(IChannelService.SERVICE_NAME, e.getMessage(), e);
						}
						catch (IOException e)
						{
							// Connection closed from the other side
							Log.e(IChannelService.SERVICE_NAME, e.getMessage(), e);
							break;
                            // TODO: decide if it's the case to quit and detect which IOException is in detail
						}
					}
				}
				finally
				{	
					if (is != null)
						is.close();
					
					if (os != null)
						os.close();
					
					if (socket != null)
						socket.close();
				}
			}
			catch (InterruptedException e)
			{
			}
			catch (IOException e)
			{
				Log.e(IChannelService.SERVICE_NAME, e.getMessage(), e);
			}
		}
		
		@Override
		public void interrupt()
		{
			Log.d(IChannelService.SERVICE_NAME, "Stopping SocketReader");
			
			userDisconnectedEvent(name);
            
			ProtocolPacket pp = new ProtocolPacket();
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
			String myName = prefs.getString("myName", "<anonymous>");
			pp.type = ProtocolPacket.PPTYPE_CHANNEL_CLOSE;
			pp.channelName = myName;
			try
			{
				os.writeObject(pp);
				os.flush();
			}
			catch (IOException e)
			{
			}
			
			if (is != null)
				try
				{
					is.close();
				}
				catch (IOException e)
				{
					Log.e(IChannelService.SERVICE_NAME, e.getMessage(), e);
				}
			
			if (os != null)
				try
				{
					os.close();
				}
				catch (IOException e)
				{
					Log.e(IChannelService.SERVICE_NAME, e.getMessage(), e);
				}
			
			if (socket != null)
				try
				{
					socket.close();
				}
				catch (IOException e)
				{
					Log.e(IChannelService.SERVICE_NAME, e.getMessage(), e);
				}
			
			if (!Thread.interrupted())
				super.interrupt();
		}
	}
    
	/**
	 * Records audio and streams it trough sockets
	 *
	 */
	private class AudioRecorderThread extends Thread
	{
        // Task used to shut down automatically the recording after a delay.
        // used when talk autodetected
        private Runnable triggerStopRecordingRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    isTalking = true;
                    
                    Thread.sleep(AUDIO_AUTOTALK_TIMEOUT);

                    finishTalk();

                    isTalking = false;
                }
                catch (InterruptedException ex)
                {
                }
            }
        };
        private Thread triggerStopRecording = null;
        
		private boolean running = false;

        private short[] buffer;
        
        @Override
		public void run()
		{
			int minBuffer = AudioRecord.getMinBufferSize(AUDIO_FREQ, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
			buffer = new short[minBuffer];

			try
			{
				// Wait until the system is initialized
                final AudioRecord audioRec = audioRecorder;
				while (audioRec.getState() != AudioRecord.STATE_INITIALIZED)
                    Thread.sleep(5);

				running = true;
				audioRec.startRecording();

				Log.d(IChannelService.SERVICE_NAME, "AudioRecorderThread: starting recording");
                
				while (running && audioRec != null)
				{
					if (Thread.interrupted())
					{
						running = false;
						break;
					}
					
					int count = audioRec.read(buffer, 0, minBuffer);
					if (count < 0)
					{
						Log.e(IChannelService.SERVICE_NAME, "AudioRecorderThread: error " + count);
						running = false;
						break;
					}					

                    // Detect if one of the bytes are beyond the treshold.
                    if (count > 0)
                    {
                        boolean toTransmit = isTalking;
                        boolean below = false;
                        
                        // Not transmitting with PTT: check if doing so with audio auto-detection.
                        // But only if autoTransmit is enabled as preference.
                        if (!toTransmit && autoTransmit)
                            for (int i = 0; i < count; i++)
                                toTransmit = buffer[i] > AUDIO_TRESHOLD;
                        
                        // If we are transmitting using the auto detection, check if audio has been below treshold
                        if (!isPTT && toTransmit && autoTransmit)
                            for (int i = 0; i < count; i++)
                                below = buffer[i] < AUDIO_TRESHOLD;
                        
                        // Ensure to clear the delay thread that stops conversation. Even with PTT.
                        // Because the user may have pushed the PTT just after finished talking with audio detection.
                        // Hint: move this to the 'talk' procedure
                        // If it's below treshold though, don't stop thread.
                        if (!below && toTransmit && triggerStopRecording != null && triggerStopRecording.isAlive())
                            triggerStopRecording.interrupt();
                        
                        // This means we're triggering auto-audio detection and not using PTT.
                        // Use a timeout for closing transmission if this is the case.
                        if (!below && toTransmit && !isPTT)
                        {
                            if (triggerStopRecording != null)
                            {
                                if (triggerStopRecording.isAlive())
                                    triggerStopRecording.interrupt();
                                triggerStopRecording = null;
                            }
                            
                            triggerStopRecording = new Thread(triggerStopRecordingRunnable);
                            triggerStopRecording.start();
                        }
                                                
                        if (toTransmit)
                        {
                            activityHandler.sendEmptyMessage(IChannelService.MSG_AUDIO_OUTGOING_START);
                            transmit(buffer, count);
                        }
                    }
                    
                    Thread.sleep(1);
				}
			}
			catch (InterruptedException e)
			{
			}
			finally
			{
				if (audioRecorder != null && audioRecorder.getState() == AudioRecord.RECORDSTATE_RECORDING)
					audioRecorder.stop();

				Log.d(IChannelService.SERVICE_NAME, "AudioRecorderThread: stopping recording");
			}
		}

		private void transmit(short[] buffer, int size)
		{
			if (buffer == null || size == 0)
				return;
            
            if (muteInput)
                return;

			// Use a tight array, saves connection speed
			short[] bf = new short[size];
			System.arraycopy(buffer, 0, bf, 0, size);
			
			// Prepare packet for sending
			ProtocolPacket pp = new ProtocolPacket();
			pp.type = ProtocolPacket.PPTYPE_AUDIO_DATA;
			pp.audioData = bf;
			
			sendPacket(pp);
		}
		
		public void finishTalk()
		{
            if (triggerStopRecording != null && triggerStopRecording.isAlive())
            {
                triggerStopRecording.interrupt();
                triggerStopRecording = null;
            }
            
            // Empty any read amount in the buffer
            int count = audioRecorder.read(buffer, 0, AudioRecord.getMinBufferSize(AUDIO_FREQ,
                AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT));
            if (count > 0)
                transmit(buffer, count);

            ProtocolPacket pp = new ProtocolPacket();
            pp.type = ProtocolPacket.PPTYPE_AUDIO_DATA_STOP;
            
			sendPacket(pp);
            
            activityHandler.sendEmptyMessage(IChannelService.MSG_AUDIO_OUTGOING_STOP);
		}
	}
	
	/*                                      */
	/* ===== Internal utulity methods ===== */
	/*                                      */
    
    private void sendPacket(ProtocolPacket pp)
    {
        synchronized (SOCKET_WRITE_LOCK)
        {
            // Either use the list of clients or the single socket
            List<SocketReader> readers = socketReaders;
            if (socketReader != null)
                readers = Arrays.asList(socketReader);

            // Send finish signal of the communication to all sockets
            if (readers != null)
            {
                for (SocketReader reader : readers)
                {
                    ObjectOutputStream os = reader.getOutput();
                    try
                    {
                        os.writeObject(pp);
                        os.flush();
                    }
                    catch (StreamCorruptedException e)
                    {
                        // TODO: ???
                        // A stream corrupted could be a problem?
                        // Generally happend when closing a connection.
                    }
                    catch (IOException e)
                    {
                        Log.e(IChannelService.SERVICE_NAME, e.getMessage(), e);
                    }
                }
            }
        }
    }
    
    private void writeAudio(short[] data)
    {
        if (!muteOutput)
            synchronized (PLAYER_LOCK)
            {
                if (audioTrack == null || audioTrack.getState() != AudioTrack.STATE_INITIALIZED)
                    reInitPlayer();
                if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED)
                {
                    if (data != null && data.length > 0)
                        audioTrack.write(data, 0, data.length);
                    audioTrack.flush();
                    audioTrack.play();
                }
            }
    }
	
    private BroadcastReceiver phoneCallDetector = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context cntxt, Intent intent)
        {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state))
            {
                muteInput = true;
                muteOutput = true;
                Log.d(IChannelService.SERVICE_NAME, "Phone ringing: muting channel");
            }
            else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state))
            {
                muteInput = true;
                muteOutput = true;
                Log.d(IChannelService.SERVICE_NAME, "Phone off hook: muting channel");
            }
            else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state))
            {
                muteInput = false;
                muteOutput = false;
                Log.d(IChannelService.SERVICE_NAME, "Phone idle: reactivating channel");
            }
        }
    };
    
	/*                                               */
	/* ===== External interface implementation ===== */
	/*                                               */
	
	// Every call to functions will be passed to a message queue and then catched by the service thread
	// In this way the UI doesn't get locked and everything gets queued.

	private class IChannelServiceImpl extends Binder implements IChannelService
	{
		public void setActivityHandler(Handler h)
		{
			activityHandler = h;
		}

		public void createChannel(String channelName)
		{
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
			boolean visible = prefs.getBoolean("channelIsPublic", true);
			boolean inviteOnly = prefs.getBoolean("channelInviteRequired", false);
			Message m = interfaceHandler.obtainMessage(MSG_IF_CREATE_CHANNEL);
			Bundle data = new Bundle();
			data.putString("channelName", channelName);
			data.putBoolean("visible", visible);
			data.putBoolean("inviteOnly", inviteOnly);
			m.setData(data);
			interfaceHandler.sendMessage(m);
		}

		public void joinChannel(String address)
		{
			Message m = interfaceHandler.obtainMessage(MSG_IF_JOIN_CHANNEL);
			Bundle data = new Bundle();
			data.putString("address", address);
			m.setData(data);
			interfaceHandler.sendMessage(m);
		}

		public void startTalking()
		{
			interfaceHandler.sendEmptyMessage(MSG_IF_START_TALKING);
		}

		public void stopTalking()
		{
			interfaceHandler.sendEmptyMessage(MSG_IF_STOP_TALKING);
		}

		public void closeChannel()
		{
			interfaceHandler.sendEmptyMessage(MSG_IF_DISCONNECT);
		}

		public int getStatus()
		{
			return status;
		}

		public ArrayList<String> getUserList()
		{
			return userList;
		}

		public boolean isTalking()
		{
			return isTalking;
		}
        
		public boolean isPTT()
		{
			return isPTT;
		}

        public void setMuteInput(boolean x)
        {
            muteInput = x;
        }

        public void setMuteOutput(boolean x)
        {
            muteOutput = x;
        }

        public boolean isMuteInput()
        {
            return muteInput;
        }

        public boolean isMuteOutput()
        {
            return muteOutput;
        }
	}

	private static final int MSG_IF_CREATE_CHANNEL = 1;

	private static final int MSG_IF_JOIN_CHANNEL = MSG_IF_CREATE_CHANNEL + 1;

	private static final int MSG_IF_DISCONNECT = MSG_IF_JOIN_CHANNEL + 1;

	private static final int MSG_IF_START_TALKING = MSG_IF_DISCONNECT + 1;

	private static final int MSG_IF_STOP_TALKING = MSG_IF_START_TALKING + 1;

	// Pass all calls as messages so the service will use its own thread
    // Incoming messages TO this service, incoming bt the ServiceBinder.
	private Handler interfaceHandler = new Handler()
	{
        @Override
		public void handleMessage(android.os.Message msg)
		{
			switch (msg.what)
			{
				case MSG_IF_CREATE_CHANNEL:
					ChannelService.this.createChannel(msg.getData().getString("channelName"),
							msg.getData().getBoolean("visible"), msg.getData().getBoolean("inviteOnly"));
					break;

				case MSG_IF_JOIN_CHANNEL:
					ChannelService.this.joinChannel(msg.getData().getString("address"));
					break;

				case MSG_IF_DISCONNECT:
					ChannelService.this.closeChannel();
					break;

				case MSG_IF_START_TALKING:
					ChannelService.this.startTalking();
					break;

				case MSG_IF_STOP_TALKING:
					ChannelService.this.stopTalking();
					break;
			}
		};
	};
}
