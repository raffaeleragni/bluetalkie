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