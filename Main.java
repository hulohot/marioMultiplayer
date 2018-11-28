import java.net.*;
import java.io.*;
import java.util.Date;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

class Main {

	static int clientCounter = 0;
	static int marioX = 100;
	static int marioY = 100;
	static int yoshiX = 200;
	static int yoshiY = 100;
	static boolean createYoshi = false;

	static String getServerTime() {
		Calendar calendar = Calendar.getInstance();
		SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		return dateFormat.format(calendar.getTime());
	}

	static void sendLine(PrintWriter out, String line) {
		out.print(line); // Send over the socket
		out.print("\r\n");
		System.out.println(line); // Print it to the console too, just to make debugging easier
	}

	static void onGet(OutputStream os, String url) throws Exception {
		PrintWriter out = new PrintWriter(os, true);
		String filename = url.substring(1); // cut off the initial "/"
		File f = new File(filename);
		Path path = Paths.get(filename);
		String dateString = getServerTime();
		System.out.println("----------The server replied: ----------");
		if (f.exists() && !f.isDirectory()) {
			// Read the file from disk
			byte[] fileContents = Files.readAllBytes(path);

			// Send the headers
			sendLine(out, "HTTP/1.1 200 OK");
			sendLine(out, "Content-Type: " + Files.probeContentType(path));
			sendLine(out, "Content-Length: " + Integer.toString(fileContents.length));
			sendLine(out, "Date: " + dateString);
			sendLine(out, "Last-Modified: " + dateString);
			sendLine(out, "Connection: close");
			sendLine(out, "");
			out.flush();

			// Send the payload
			os.write(fileContents);
			String blobHead = fileContents.length < 60 ? new String(fileContents)
					: new String(fileContents, 0, 60) + "...";

			System.out.println(blobHead);
		} else {
			// Make an error message
			String payload = "404 - File not found: " + filename;

			// Send HTTP headers
			sendLine(out, "HTTP/1.1 200 OK");
			sendLine(out, "Content-Type: text/html");
			sendLine(out, "Content-Length: " + Integer.toString(payload.length()));
			sendLine(out, "Date: " + dateString);
			sendLine(out, "Last-Modified: " + dateString);
			sendLine(out, "Connection: close");
			sendLine(out, "");

			// Send the payload
			sendLine(out, payload);
		}
	}

	static void onPost(OutputStream os, String url, char[] incomingPayload) {
		// Parse the incoming payload
		System.out.println("----------------------------------------");
		String payload = String.valueOf(incomingPayload);
		System.out.println("Received the following payload: " + payload);
		Json incoming = Json.parse(payload);

		// Make a response
		Json outgoing = Json.newObject();

		int clientID = (int) incoming.getLong("id");

		if (clientID < 0) {
			outgoing.add("id", clientCounter);
			if (clientCounter == 1) {
				createYoshi = true;
			}
			clientCounter++;
		} else {
			outgoing.add("id", clientID);
		}

		if (clientID == 0) {
			marioX = (int) incoming.getLong("marioX");
			marioY = (int) incoming.getLong("marioY");
		} else if (clientID == 1) {
			yoshiX = (int) incoming.getLong("yoshiX");
			yoshiY = (int) incoming.getLong("yoshiY");
		}

		outgoing.add("createYoshi", createYoshi);
		outgoing.add("marioX", marioX);
		outgoing.add("marioY", marioY);
		if (clientCounter > 1) {
			outgoing.add("yoshiX", yoshiX);
			outgoing.add("yoshiY", yoshiY);
		}

		String response = outgoing.toString();

		// Send HTTP headers
		System.out.println("----------The server replied: ----------");
		String dateString = getServerTime();
		PrintWriter out = new PrintWriter(os, true);
		sendLine(out, "HTTP/1.1 200 OK");
		sendLine(out, "Content-Type: application/json");
		sendLine(out, "Content-Length: " + Integer.toString(response.length()));
		sendLine(out, "Date: " + dateString);
		sendLine(out, "Last-Modified: " + dateString);
		sendLine(out, "Connection: close");
		sendLine(out, "");

		// Send the response
		sendLine(out, response);
		out.flush();
	}

	public static void main(String[] args) throws Exception {
		// Make a socket to listen for clients
		int port = 3000;
		String urlName = "/page.html";
		String ServerURL = "http://localhost:" + Integer.toString(port) + urlName;
		ServerSocket serverSocket = new ServerSocket(port);

		// Start the web browser
		if (Desktop.isDesktopSupported()) {
			Desktop.getDesktop().browse(new URI(ServerURL));
			Desktop.getDesktop().browse(new URI(ServerURL));
		} else
			System.out.println("Please direct your browser to " + ServerURL);

		// Handle requests from clients
		while (true) {
			Socket clientSocket = serverSocket.accept(); // This call blocks until a client connects
			BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			OutputStream os = clientSocket.getOutputStream();

			// Read the HTTP headers
			String headerLine;
			int requestType = 0;
			int contentLength = 0;
			String url = "";
			System.out.println("----------A client said: ----------");
			while ((headerLine = in.readLine()) != null) {
				System.out.println(headerLine);
				if (headerLine.length() > 3 && headerLine.substring(0, 4).equals("GET ")) {
					requestType = 1;
					url = headerLine.substring(4, headerLine.indexOf(" ", 4));
				} else if (headerLine.length() > 4 && headerLine.substring(0, 5).equals("POST ")) {
					requestType = 2;
					url = headerLine.substring(5, headerLine.indexOf(" ", 5));
				} else if (headerLine.length() > 15 && headerLine.substring(0, 16).equals("Content-Length: "))
					contentLength = Integer.parseInt(headerLine.substring(16));
				if (headerLine.length() < 2) // Headers are terminated by a "\r\n" line
					break;
			}

			// Send a response
			if (requestType == 1) {
				onGet(os, url);
			} else if (requestType == 2) {
				// Read the incoming payload
				char[] incomingPayload = new char[contentLength];
				in.read(incomingPayload, 0, contentLength);
				String blobHead = incomingPayload.length < 60 ? new String(incomingPayload)
						: new String(incomingPayload, 0, 60) + "...";
				System.out.println(blobHead);
				onPost(os, url, incomingPayload);
			} else
				System.out.println("Received bad headers. Ignoring.");

			// Hang up
			os.flush();
			// clientSocket.close();
		}
	}
}
