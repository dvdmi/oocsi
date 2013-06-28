package nl.tue.id.oocsi.client.socket;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map;

import nl.tue.id.oocsi.client.protocol.Handler;

public class SocketClient {

	private String name;
	private Map<String, Handler> channels;

	private Socket socket;
	private BufferedReader input;
	private PrintWriter output;

	public SocketClient(String name, Map<String, Handler> channels) {
		this.name = name;
		this.channels = channels;
	}

	public boolean connect(String hostname, int port) {

		// connect
		try {
			socket = new Socket(hostname, port);
			output = new PrintWriter(socket.getOutputStream(), true);

			// send name
			output.println(name);

			input = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			new Thread(new Runnable() {
				public void run() {
					try {
						String fromServer;
						while (!socket.isClosed()
								&& (fromServer = input.readLine()) != null) {
							if (fromServer.startsWith("send")) {
								// parse server output
								String[] tokens = fromServer.split(" ");
								if (tokens.length == 5) {
									String channel = tokens[1];
									String data = tokens[2];
									String timestamp = tokens[3];
									String sender = tokens[4];

									Handler c = channels.get(channel);
									if (c != null) {
										c.send(sender, data, timestamp);
									} else if (channel.equals(name)) {
										c = channels.get("SELF");
										if (c != null) {
											c.send(sender, data, timestamp);
										}
									}
								}
							}
						}

						output.close();
						input.close();
						socket.close();
					} catch (IOException e) {
						// e.printStackTrace();
					}
				}
			}).start();

		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}

	public boolean isConnected() {
		return !socket.isClosed() && socket.isConnected();
	}

	public void disconnect() {
		// disconnect from server
		output.println("quit");

		try {
			output.close();
			input.close();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void subscribe(String channelName, Handler handler) {

		// register at server
		send("subscribe " + channelName);

		// add handler
		channels.put(channelName, handler);
	}

	public void subscribe(Handler handler) {

		// register at server
		send("subscribe " + name);

		// add handler
		channels.put("SELF", handler);
	}

	public void unsubscribe(String channelName) {

		// unregister at server
		send("unsubscribe " + channelName);

		// remove handler
		channels.remove(channelName);
	}

	public void send(String channelName, String message) {
		// send message
		send("sendraw " + channelName + " " + message);
	}

	public void send(String channelName, Map<String, Object> data) {
		// send message with raw data
		send("send " + channelName + " " + serialize(data));
	}

	private void send(String string) {
		output.println(string);
	}

	private String serialize(Map<String, Object> data) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
		try {
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(data);
			byte[] rawData = baos.toByteArray();
			return new String(Base64Coder.encode(rawData));
		} catch (IOException e) {
			return "";
		}
	}

}