package nl.tue.id.oocsi.server;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.google.gson.Gson;

import nl.tue.id.oocsi.server.model.Channel;
import nl.tue.id.oocsi.server.model.Server;
import nl.tue.id.oocsi.server.protocol.Message;
import nl.tue.id.oocsi.server.services.AbstractService;
import nl.tue.id.oocsi.server.services.OSCService;
import nl.tue.id.oocsi.server.services.SocketService;

/**
 * main server component for running OOCSI
 * 
 * @author matsfunk
 * 
 */
public class OOCSIServer extends Server {

	// constants
	public static final String VERSION = "1.8";

	// defaults for different services
	public static int port = 4444;
	public static int maxClients = 30;
	public static boolean isLogging = false;
	public static String[] users = null;

	// default channels
	public static final String OOCSI_EVENTS = "OOCSI_events";
	public static final String OOCSI_CONNECTIONS = "OOCSI_connections";
	public static final String OOCSI_CHANNELS = "OOCSI_channels";
	public static final String OOCSI_CLIENTS = "OOCSI_clients";
	public static final String OOCSI_METRICS = "OOCSI_metrics";

	// metrics
	private static int messageCount = 0;
	private static int messageTotal = 0;
	private static final long serverStart = System.currentTimeMillis();

	// singleton
	private static OOCSIServer server;

	// services
	AbstractService[] services;

	/**
	 * initialize minimal server without any services running
	 * 
	 * @param args
	 * @throws IOException
	 */
	public OOCSIServer() {
		// singleton assignment
		server = this;
	}

	/**
	 * initialize the server and listen for client connects
	 * 
	 * @param args
	 * @throws IOException
	 */
	public OOCSIServer(String[] args) throws IOException {
		this();

		// parse arguments
		parseCommandlineArgs(args);

		// then initialize
		init();
	}

	/**
	 * initialize the server and listen for client connects
	 * 
	 * @param port
	 * @param clients
	 * @param logging
	 * @throws IOException
	 */
	public OOCSIServer(int port, int clients, boolean logging) throws IOException {
		this();

		// assign argument
		OOCSIServer.port = port;
		maxClients = clients;
		isLogging = logging;

		init();
	}

	/**
	 * initialize the server and listen for client connects
	 * 
	 * @param port
	 * @param clients
	 * @param logging
	 * @param userList
	 * @throws IOException
	 */
	public OOCSIServer(int port, int clients, boolean logging, String[] userList) throws IOException {
		this();

		// assign argument
		OOCSIServer.port = port;
		maxClients = clients;
		isLogging = logging;
		users = userList;

		init();
	}

	/**
	 * initialize the server and listen for client connects
	 * 
	 * @param args
	 * @throws IOException
	 */
	private void init() throws IOException {

		// add OOCSI channels that will deliver meta-data to potentially
		// connected clients
		Channel channel = new Channel(OOCSIServer.OOCSI_CONNECTIONS);
		addChannel(channel);
		channel = new Channel(OOCSIServer.OOCSI_EVENTS);
		addChannel(channel);

		// output status message
		OOCSIServer.log("Started OOCSI server v" + OOCSIServer.VERSION + " for max. " + maxClients + " parallel clients"
				+ (isLogging ? " and activated logging" : "") + ".");

		// TODO check command line options
		// start OSC server
		OSCService osc = new OSCService(this, port + 1, Math.max(2, maxClients / 3));

		// TODO check command line options
		// start TCP/socket server
		SocketService tcp = new SocketService(this, port, Math.max(2, 2 * maxClients / 3), users);

		// start services
		start(new AbstractService[] { tcp, osc });

		// start timer for posting channel and client information to the respective channels
		new Timer(true).schedule(new StatusTimeTask(), 1000, 5000);
	}

	/**
	 * start given services
	 * 
	 * @param services
	 */
	public void start(AbstractService[] services) {

		// first stop all running services
		stop();

		// start new services
		for (final AbstractService service : services) {
			new Thread(new Runnable() {
				public void run() {
					service.start();
				}
			}).start();
		}

		// keep record of newly started services
		this.services = services;
	}

	/**
	 * stop all running services
	 * 
	 */
	public void stop() {
		if (services == null) {
			return;
		}

		for (AbstractService service : services) {
			service.stop();
		}
	}

	@Override
	public Channel getChannel(String channelName) {
		Channel c = super.getChannel(channelName);

		// intercept for OSC
		if (c == null && channelName.startsWith("osc://")) {
			c = getChannel(OSCService.OSC);
		}

		return c;
	}

	/** STATIC METHODS *************************************************************/

	public static void main(String[] args) {

		// check dependencies
		new Gson();

		// start socket server
		try {
			new OOCSIServer(args);
		} catch (IOException e) {
			// e.printStackTrace();
		} finally {
			// done
		}
	}

	/**
	 * logging of a general server event (can be switched off with startup parameter '-logging')
	 * 
	 * @param message
	 */
	public static void log(String message) {
		if (isLogging) {
			server.internalLog(message);
		}
	}

	/**
	 * internal logging function that can be overridden by a subclass
	 * 
	 * @param message
	 */
	protected void internalLog(String message) {
		System.out.println(new Date() + " " + message);
	}

	/**
	 * logging of event (can be switched off with startup parameter '-logging')
	 * 
	 * @param sender
	 * @param recipient
	 * @param data
	 * @param timestamp
	 */
	public static void logEvent(String sender, String recipient, Map<String, Object> data, Date timestamp) {

		if (!OOCSIServer.OOCSI_EVENTS.equals(sender) && !OOCSIServer.OOCSI_EVENTS.equals(recipient)
				&& !OOCSIServer.OOCSI_METRICS.equals(recipient) && !OOCSIServer.OOCSI_CONNECTIONS.equals(recipient)) {

			// log metrics
			messageCount++;
			messageTotal++;

			if (isLogging) {
				log(OOCSI_EVENTS + " " + sender + "->" + recipient);

				Channel logChannel = server.getChannel(OOCSI_EVENTS);
				if (logChannel != null) {
					Message message = new Message(sender, OOCSI_EVENTS, timestamp, data);
					message.addData("sender", sender);
					message.addData("recipient", recipient);

					logChannel.send(message);
				}
			}
		}
	}

	/**
	 * logging of connection/channel update (can be switched off with startup parameter '-logging')
	 * 
	 * @param message
	 */
	public static void logConnection(String client, String channel, String operation, Date timestamp) {
		if (isLogging && !OOCSIServer.OOCSI_EVENTS.equals(channel) && !OOCSIServer.OOCSI_CONNECTIONS.equals(channel)) {
			log(OOCSI_CONNECTIONS + " " + client + "->" + channel + " (" + operation + ")");

			Message message = new Message(client, OOCSI_CONNECTIONS, timestamp);
			message.addData("client", client);
			message.addData("channel", channel);
			message.addData("operation", operation);
			Channel logChannel = server.getChannel(OOCSI_CONNECTIONS);
			if (logChannel != null) {
				logChannel.send(message);
			}
		}
	}

	/**
	 * parses the command line arguments
	 */
	public static void parseCommandlineArgs(String[] args) {
		for (int i = 0; i < args.length; i++) {
			String argument = args[i];

			if (argument.equals("-port") && args.length >= i + 2) {
				port = Integer.parseInt(args[i + 1]);
			} else if (argument.equals("-clients") && args.length >= i + 2) {
				maxClients = Integer.parseInt(args[i + 1]);
			} else if (argument.equals("-logging")) {
				isLogging = true;
			} else if (argument.equals("-users") && args.length >= i + 2) {
				String userList = args[i + 1];
				if (userList.matches(
						"^([a-zA-Z0-9_\\-.]+:[a-zA-Z0-9_\\-.%$]+;)*([a-zA-Z0-9_\\-.]+:[a-zA-Z0-9_\\-.%$]+);*$")) {
					users = userList.split(";");
				}
			}
		}
	}

	class StatusTimeTask extends TimerTask {
		@Override
		public void run() {
			// check if we have a subscriber for public channel information
			Channel channels = server.getChannel(OOCSI_CHANNELS);
			if (channels != null) {
				Message message = new Message("SERVER", OOCSI_CHANNELS);
				message.addData("channels", server.getChannelList());
				channels.send(message);
			}

			// check if we have a subscriber for public client information
			Channel clients = server.getChannel(OOCSI_CLIENTS);
			if (clients != null) {
				Message message = new Message("SERVER", OOCSI_CLIENTS);
				message.addData("clients", server.getClientList());
				clients.send(message);
			}

			// check if we have a subscriber for public client information
			Channel metrics = server.getChannel(OOCSI_METRICS);
			if (metrics != null) {
				Message message = new Message("SERVER", OOCSI_METRICS);
				message.addData("messagesTotal", messageTotal);
				message.addData("messages", (int) Math.ceil(messageCount / 5.));
				message.addData("uptime", System.currentTimeMillis() - serverStart);
				message.addData("channels", server.subChannels.size());
				message.addData("clients", server.clients.size());
				metrics.send(message);
			}
			messageCount = 0;
		}
	}

}
