package nl.tue.id.oocsi.client.behavior;

import java.util.Collections;

import nl.tue.id.oocsi.client.OOCSIClient;
import nl.tue.id.oocsi.client.protocol.Handler;
import nl.tue.id.oocsi.client.protocol.OOCSIMessage;

public class OOCSISystemCommunicator<T> {

	protected static final String HANDLE = "handle";

	protected OOCSIClient client;
	protected String channelName;
	private Handler handler;

	public OOCSISystemCommunicator(OOCSIClient client, String channelName) {
		this(client, channelName, null);
	}

	public OOCSISystemCommunicator(OOCSIClient client, String channelName, Handler handler) {
		this.client = client;
		this.channelName = channelName;
		this.handler = handler;
	}

	protected void message(String command, T data) {
		if (client != null) {
			new OOCSIMessage(client, channelName).data(command, data).data(HANDLE, getHandle()).send();
		}
	}

	protected void message(String command) {
		if (client != null) {
			new OOCSIMessage(client, channelName).data(command, "").data(HANDLE, getHandle()).send();
		}
	}

	/**
	 * returns the unique handle for this object
	 * 
	 * @return
	 */
	protected String getHandle() {
		return client.getName() + "_" + OOCSISystemCommunicator.this.hashCode();
	}

	protected void triggerHandler() {
		if (handler != null) {
			handler.receive(channelName, Collections.<String, Object> emptyMap(), System.currentTimeMillis(),
					channelName, client.getName());
		}
	}
}
