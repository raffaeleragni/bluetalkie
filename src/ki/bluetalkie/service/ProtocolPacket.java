package ki.bluetalkie.service;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * A single packet of the protocol
 * 
 * @author Raffaele Ragni <raffaele.ragni@gmail.com>
 */
public class ProtocolPacket implements Serializable
{
	private static final long serialVersionUID = 1L;
	
	/* PACKET TYPES */

	public static final int PPTYPE_ASK_CONNECTION_INFO = 1;
	
	public static final int PPTYPE_REPLY_CONNECTION_INFO = PPTYPE_ASK_CONNECTION_INFO + 1;
	
	public static final int PPTYPE_ASK_INVITATION = PPTYPE_REPLY_CONNECTION_INFO + 1;
	
	public static final int PPTYPE_GIVE_INVITATION = PPTYPE_ASK_INVITATION + 1;
	
	public static final int PPTYPE_DENY_INVITATION = PPTYPE_GIVE_INVITATION + 1;
	
	public static final int PPTYPE_AUDIO_DATA = PPTYPE_DENY_INVITATION + 1;
	
	public static final int PPTYPE_AUDIO_DATA_STOP = PPTYPE_AUDIO_DATA + 1;
	
	public static final int PPTYPE_CHANNEL_CLOSE = PPTYPE_AUDIO_DATA_STOP + 1;
	
	public static final int PPTYPE_USER_LIST = PPTYPE_CHANNEL_CLOSE + 1;
	
	/* DATA */

	public int type;
	
	public String channelName;
	
	public ArrayList<String> userList;

	public boolean visible;

	public boolean inviteOnly;

	public short[] audioData;
}