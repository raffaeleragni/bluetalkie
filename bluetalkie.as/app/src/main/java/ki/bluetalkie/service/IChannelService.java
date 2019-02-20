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
package ki.bluetalkie.service;

import android.os.Handler;
import java.util.ArrayList;

/**
 * Service interface
 * 
 * @author Raffaele Ragni <raffaele.ragni@gmail.com>
 */
public interface IChannelService
{

	public static final String SERVICE_NAME = "bluetal.ChannelService";

	/**
	 * Event fired when the creation of a channel is completed
	 */
	public static final int MSG_ERROR = 1;

	/**
	 * Got channel details
	 */
	public static final int MSG_CHANNEL_DETAILS = MSG_ERROR + 1;

	/**
	 * Event fired when the creation of a channel is completed
	 */
	public static final int MSG_CHANNEL_CREATED = MSG_CHANNEL_DETAILS + 1;

	/**
	 * Event fired when a client connects to this channel (may be not invited though)
	 * 
	 * @param 1 (address) the address of the device connected
	 * @param 2 (name) the name of the device that has just been connected
	 */
	public static final int MSG_USER_CONNECTED = MSG_CHANNEL_CREATED + 1;

	/**
	 * Event fired when a device disconnects from the channel, used to update users list
	 * 
	 * @param 1 (address) the blue tooth address of the disconnected client 
	 */
	public static final int MSG_USER_DISCONNECTED = MSG_USER_CONNECTED + 1;
	
	/**
	 * User list updated
	 */
	public static final int MSG_USER_LIST_UPDATE = MSG_USER_DISCONNECTED + 1;

	/**
	 * Event fired when audio starts to income
	 */
	public static final int MSG_AUDIO_INCOMING_START = MSG_USER_LIST_UPDATE + 1;

	/**
	 * Event fired when audio from input stops coming
	 */
	public static final int MSG_AUDIO_INCOMING_STOP = MSG_AUDIO_INCOMING_START + 1;

	/**
	 * Event fired when audio starts to exit
	 */
	public static final int MSG_AUDIO_OUTGOING_START = MSG_AUDIO_INCOMING_STOP + 1;

	/**
	 * Event fired when audio from output stops exiting
	 */
	public static final int MSG_AUDIO_OUTGOING_STOP = MSG_AUDIO_OUTGOING_START + 1;

	/** channel has been closed by the other part */
	public static final int MSG_CHANNEL_CLOSED = MSG_AUDIO_OUTGOING_STOP + 1;
	
	/** when the service was just created */
	public static final int STATUS_NULL = 0;

	/** when the service was just created */
	public static final int STATUS_INITIALIZED = STATUS_NULL + 0;

	/** the channel has just been created and is waiting for joins */
	public static final int STATUS_CREATED = STATUS_INITIALIZED + 1;

	/** joined a channel but awaiting for invite to talk/listen */
	public static final int STATUS_AWAITING_INVITATION = STATUS_CREATED + 1;

	/** joined and able to talk/listen on a channel */
	public static final int STATUS_JOINED = STATUS_AWAITING_INVITATION + 1;
	
	/**
	 * Sets the handler that will be waiting for events in the activity layer
	 * @param h the handler in the activity layer
	 */
	public void setActivityHandler(Handler h);

	/**
	 * Creates a new channel.
	 * This auto-closes any existing or joined channels.
	 */
	public void createChannel(String channelName);

	/**
	 * Joins an existing channel.
	 * Invitation should be already been handled, we assume everything is OK here.
	 * If not, exception thrown
	 */
	public void joinChannel(String address);

	/**
	 * Called when the talk button is pressed (on press)
	 * Service will gather audio until it's stopped and stream it to the other endpoint.
	 */
	public void startTalking();

	/**
	 * Called when the talk button is released (on release)
	 * Stops gathering audio and sending it
	 */
	public void stopTalking();
	
	/**
	 * Tell if the service is currently in talk mode
	 */
	public boolean isTalking();
    
    /**
     * If talking by using the PTT button
     */
    public boolean isPTT();

	/**
	 * Closes the channel and frees the services resources.
	 * Also communicate to the other endpoint that we're abandoning the channel (if joined) or closing it (if created)
	 */
	public void closeChannel();

	/**
	 * Retrieves the status of the service, and thus also of the channel
	 * @return the channel status
	 */
	public int getStatus();
	
	/**
	 * Gets the users names in the channel
	 * @return
	 */
	public ArrayList<String> getUserList();
    
    /**
     * Mute input setter
     */
    public void setMuteInput(boolean x);
    
    /**
     * Mute input setter
     */
    public void setMuteOutput(boolean x);
    
    /**
     * Mute output getter
     */
    public boolean isMuteInput();
    
    /**
     * Mute output getter
     */
    public boolean isMuteOutput();
}
