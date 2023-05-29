/*
Simple Web Server in Java which allows you to call 
localhost:9000/ and show you the root.html webpage from the www/root.html folder
You can also do some other simple GET requests:
1) /random shows you a random picture (well random from the set defined)
2) json shows you the response as JSON for /random instead the html page
3) /file/filename shows you the raw file (not as HTML)
4) /multiply?num1=3&num2=4 multiplies the two inputs and responses with the result
5) /github?query=users/amehlhase316/repos (or other GitHub repo owners) will lead to receiving
   JSON which will for now only be printed in the console. See the todo below

The reading of the request is done "manually", meaning no library that helps making things a 
little easier is used. This is done so you see exactly how to pars the request and 
write a response back
 */

package funHttpServer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONObject;

class WebServer {
	public static void main(String args[]) {
		WebServer server = new WebServer(9000);
	}

	/**
	 * Main thread
	 * @param port to listen on
	 */
	public WebServer(int port) {
		ServerSocket server = null;
		Socket sock = null;
		InputStream in = null;
		OutputStream out = null;

		try {
			server = new ServerSocket(port);
			while (true) {
				sock = server.accept();
				out = sock.getOutputStream();
				in = sock.getInputStream();
				byte[] response = createResponse(in);
				out.write(response);
				out.flush();
				in.close();
				out.close();
				sock.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (sock != null) {
				try {
					server.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Used in the "/random" endpoint
	 */
	private final static HashMap<String, String> _images = new HashMap<>() {
		{
			put("streets", "https://iili.io/JV1pSV.jpg");
			put("bread", "https://iili.io/Jj9MWG.jpg");
		}
	};

	private Random random = new Random();

	/**
	 * Reads in socket stream and generates a response
	 * @param inStream HTTP input stream from socket
	 * @return the byte encoded HTTP response
	 */
	public byte[] createResponse(InputStream inStream) {

		byte[] response = null;
		BufferedReader in = null;

		try {

			// Read from socket's input stream. Must use an
			// InputStreamReader to bridge from streams to a reader
			in = new BufferedReader(new InputStreamReader(inStream, "UTF-8"));

			// Get header and save the request from the GET line:
			// example GET format: GET /index.html HTTP/1.1

			String request = null;

			boolean done = false;
			while (!done) {
				String line = in.readLine();

				System.out.println("Received: " + line);

				// find end of header("\n\n")
				if (line == null || line.equals(""))
					done = true;
				// parse GET format ("GET <path> HTTP/1.1")
				else if (line.startsWith("GET")) {
					int firstSpace = line.indexOf(" ");
					int secondSpace = line.indexOf(" ", firstSpace + 1);

					// extract the request, basically everything after the GET up to HTTP/1.1
					request = line.substring(firstSpace + 2, secondSpace);
				}

			}
			System.out.println("FINISHED PARSING HEADER\n");

			// Generate an appropriate response to the user
			if (request == null) {
				response = "<html>Illegal request: no GET</html>".getBytes();
			} else {
				// create output buffer
				StringBuilder builder = new StringBuilder();
				// NOTE: output from buffer is at the end

				if (request.length() == 0) {
					// shows the default directory page

					// opens the root.html file
					String page = new String(readFileInBytes(new File("www/root.html")));
					// performs a template replacement in the page
					page = page.replace("${links}", buildFileList());

					// Generate response
					builder.append("HTTP/1.1 200 OK\n");
					builder.append("Content-Type: text/html; charset=utf-8\n");
					builder.append("\n");
					builder.append(page);

				} else if (request.equalsIgnoreCase("json")) {
					// shows the JSON of a random image and sets the header name for that image

					// pick a index from the map
					int index = random.nextInt(_images.size());

					// pull out the information
					String header = (String) _images.keySet().toArray()[index];
					String url = _images.get(header);

					// Generate response
					builder.append("HTTP/1.1 200 OK\n");
					builder.append("Content-Type: application/json; charset=utf-8\n");
					builder.append("\n");
					builder.append("{");
					builder.append("\"header\":\"").append(header).append("\",");
					builder.append("\"image\":\"").append(url).append("\"");
					builder.append("}");

				} else if (request.equalsIgnoreCase("random")) {
					// opens the random image page

					// open the index.html
					File file = new File("www/index.html");

					// Generate response
					builder.append("HTTP/1.1 200 OK\n");
					builder.append("Content-Type: text/html; charset=utf-8\n");
					builder.append("\n");
					builder.append(new String(readFileInBytes(file)));

				} else if (request.contains("file/")) {
					// tries to find the specified file and shows it or shows an error

					// take the path and clean it. try to open the file
					File file = new File(request.replace("file/", ""));

					// Generate response
					if (file.exists()) { // success
						builder.append("HTTP/1.1 200 OK\n");
						builder.append("Content-Type: text/html; charset=utf-8\n");
						builder.append("\n");
						builder.append(new String(readFileInBytes(file)));
					} else { // failure
						builder.append("HTTP/1.1 404 Not Found\n");
						builder.append("Content-Type: text/html; charset=utf-8\n");
						builder.append("\n");
						builder.append("File not found: " + file);
					}
				} else if (request.contains("multiply?")) {
					// This multiplies two numbers, there is NO error handling, so when
					// wrong data is given this just crashes

					Map<String, String> query_pairs = new LinkedHashMap<String, String>();
					// extract path parameters
					try {
						query_pairs = splitQuery(request.replace("multiply?", ""));
						
						// extract required fields from parameters
						Integer num1 = Integer.parseInt(query_pairs.get("num1"));
						Integer num2 = Integer.parseInt(query_pairs.get("num2"));

						// do math
						Integer result = num1 * num2;

						// Generate response
						builder.append("HTTP/1.1 200 OK\n");
						builder.append("Content-Type: text/html; charset=utf-8\n");
						builder.append("\n");
						builder.append("Result is: " + result);
					} catch (Exception e) {
						
						// Generate error
						builder.append("HTTP/1.1 400 Bad request\n");
						builder.append("Content-Type: text/html; charset=utf-8\n");
						builder.append("\n");
						builder.append("Malformed request.");
						builder.append("<br>");
						builder.append("Please include numeric parameters num1 and num2:");
						builder.append("<br>");
						builder.append("multiply?num1=value&num2=value");
					}

				} else if (request.contains("github?")) {
					// pulls the query from the request and runs it with GitHub's REST API
					// check out https://docs.github.com/rest/reference/
					//
					// HINT: REST is organized by nesting topics. Figure out the biggest one first,
					//     then drill down to what you care about
					// "Owner's repo is named RepoName. Example: find RepoName's contributors" translates to
					//     "/repos/OWNERNAME/REPONAME/contributors"

					Map<String, String> query_pairs = new LinkedHashMap<String, String>();
					try {
						query_pairs = splitQuery(request.replace("github?", ""));
						// check if parameter name is correct
						if (!query_pairs.containsKey("query"))
							throw new Exception();
						
						// send request to github
						String jsonStr = fetchURL("https://api.github.com/" + query_pairs.get("query"));
						
						// try to parse the json
						if (jsonStr.startsWith("[")) {
							// is an array
							JSONArray json = new JSONArray(jsonStr);
							builder.append("HTTP/1.1 200 OK\n");
							builder.append("Content-Type: text/html; charset=utf-8\n");
							builder.append("\n");
							builder.append("Repositories returned:");
							builder.append("<p>");

							// loop through all elements
							for (int i = 0; i < json.length(); i++) {
								builder.append("Full name: " + json.getJSONObject(i).getString("full_name"));
								builder.append("<br>");
								builder.append("Id: " + json.getJSONObject(i).getInt("id"));
								builder.append("<br>");
								builder.append("Owner: " + json.getJSONObject(i).getJSONObject("owner").getString("login"));
								builder.append("<p>");
							}
						} else {
							// if it's a single record
							// check if request returned Not Found from github
							if (jsonStr.isBlank()) {
								throw new NoSuchElementException();
							}
							
							// else process
							JSONObject json = new JSONObject(jsonStr);
							builder.append("Full name: " + json.getString("full_name"));
							builder.append("<br>");
							builder.append("Id: " + json.getInt("id"));
							builder.append("<br>");
							builder.append("Owner: " + json.getJSONObject("owner").getString("login"));
							builder.append("<p>");
						}
						
					} catch (NoSuchElementException e) {
						builder.append("HTTP/1.1 404 Not found\n");
						builder.append("Content-Type: text/html; charset=utf-8\n");
						builder.append("\n");
						builder.append("Record not found. Verify if query format or parameters are correct.");
						e.printStackTrace();
					} catch (Exception e) {
						builder.append("HTTP/1.1 400 Bad request\n");
						builder.append("Content-Type: text/html; charset=utf-8\n");
						builder.append("\n");
						builder.append("Malformed query. Please verify if the format or parameters are correct.");
						e.printStackTrace();
					}
					
				} else if (request.contains("decipher?")) {
					
					try {
						Map<String, String> parameters = new LinkedHashMap<String, String>();
						parameters = splitQuery(request.replace("decipher?", ""));

						// check if we have both parameters
						if (!parameters.containsKey("string") || !parameters.containsKey("seed"))
							throw new IllegalStateException();

						// capture parameters
						String cipher = parameters.get("string");
						int seed = Integer.parseInt(parameters.get("seed"));

						// check if parameters are valid
						if (cipher.isBlank() || seed == 0)
							throw new IllegalArgumentException();

						// decipher string
						System.out.println("To decode: " + cipher);
						String urlDecoded = URLDecoder.decode(cipher, "UTF-8");
						StringBuilder cipherBuilder = new StringBuilder();
						for (char c : urlDecoded.toCharArray()) {
							int tmp = c - 32;
							tmp = (tmp + 94 - (seed % 94)) % 94;
							tmp = tmp + 32;
							cipherBuilder.append((char)tmp);
						}

						// generate response
						builder.append("HTTP/1.1 200 OK\n");
						builder.append("Content-Type: text/html; charset=utf-8\n");
						builder.append("\n");
						builder.append("Deciphered string: " + cipherBuilder.toString());

					} catch (IllegalStateException e) {
						builder.append("HTTP/1.1 400 Bad request\n");
						builder.append("Content-Type: text/html; charset=utf-8\n");
						builder.append("\n");
						builder.append("Missing parameters. Please verify the format of the query.");
						e.printStackTrace();
					} catch (IllegalArgumentException e) {
						builder.append("HTTP/1.1 400 Bad request\n");
						builder.append("Content-Type: text/html; charset=utf-8\n");
						builder.append("\n");
						builder.append("Invalid parameters. Please verify the values being passed.");
						e.printStackTrace();
					} catch (Exception e) {
						builder.append("HTTP/1.1 400 Bad request\n");
						builder.append("Content-Type: text/html; charset=utf-8\n");
						builder.append("\n");
						builder.append("Malformed query. Please verify the format of the query.");
						e.printStackTrace();
					}
					
				} else if (request.contains("cipher?")) {
					
					try {
						Map<String, String> parameters = new LinkedHashMap<String, String>();
						parameters = splitQuery(request.replace("cipher?", ""));

						// check if we have both parameters
						if (!parameters.containsKey("string") || !parameters.containsKey("seed"))
							throw new IllegalStateException();

						// capture parameters
						String cipher = parameters.get("string");
						int seed = Integer.parseInt(parameters.get("seed"));

						// check if parameters are valid
						if (cipher.isBlank() || seed == 0)
							throw new IllegalArgumentException();

						// apply cipher
						StringBuilder cipherBuilder = new StringBuilder();
						for (char c : cipher.toCharArray()) {
							int tmp = c - 32;
							tmp = (tmp + seed) % 94;
							tmp = tmp + 32;
							cipherBuilder.append((char)tmp);
						}
						
						//String htmlEncoded = StringEscapeUtils.escapeHtml4(cipherBuilder.toString());
						String urlEncoded = URLEncoder.encode(cipherBuilder.toString(), "UTF-8");
						
						// generate response
						builder.append("HTTP/1.1 200 OK\n");
						builder.append("Content-Type: text/html; charset=utf-8\n");
						builder.append("\n");
						builder.append("ciphered string: " + cipherBuilder.toString());
						builder.append("<br>");
						builder.append("HTML encoded (use this for the decipher query): " + urlEncoded);

					} catch (IllegalStateException e) {
						builder.append("HTTP/1.1 400 Bad request\n");
						builder.append("Content-Type: text/html; charset=utf-8\n");
						builder.append("\n");
						builder.append("Missing parameters. Please verify the format of the query.");
						e.printStackTrace();
					} catch (IllegalArgumentException e) {
						builder.append("HTTP/1.1 400 Bad request\n");
						builder.append("Content-Type: text/html; charset=utf-8\n");
						builder.append("\n");
						builder.append("Invalid parameters. Please verify the values being passed.");
						e.printStackTrace();
					} catch (Exception e) {
						builder.append("HTTP/1.1 400 Bad request\n");
						builder.append("Content-Type: text/html; charset=utf-8\n");
						builder.append("\n");
						builder.append("Malformed query. Please verify the format of the query.");
						e.printStackTrace();
					}
					
				} else {
					// if the request is not recognized at all

					builder.append("HTTP/1.1 400 Bad Request\n");
					builder.append("Content-Type: text/html; charset=utf-8\n");
					builder.append("\n");
					builder.append("I am not sure what you want me to do...");
				}

				// Output
				response = builder.toString().getBytes();
			}
		} catch (IOException e) {
			e.printStackTrace();
			response = ("<html>ERROR: " + e.getMessage() + "</html>").getBytes();
		}

		return response;
	}

	/**
	 * Method to read in a query and split it up correctly
	 * @param query parameters on path
	 * @return Map of all parameters and their specific values
	 * @throws UnsupportedEncodingException If the URLs aren't encoded with UTF-8
	 */
	public static Map<String, String> splitQuery(String query) throws UnsupportedEncodingException {
		Map<String, String> query_pairs = new LinkedHashMap<String, String>();
		// "q=hello+world%2Fme&bob=5"
		String[] pairs = query.split("&");
		// ["q=hello+world%2Fme", "bob=5"]
		for (String pair : pairs) {
			int idx = pair.indexOf("=");
			query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
					URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
		}
		// {{"q", "hello world/me"}, {"bob","5"}}
		return query_pairs;
	}

	/**
	 * Builds an HTML file list from the www directory
	 * @return HTML string output of file list
	 */
	public static String buildFileList() {
		ArrayList<String> filenames = new ArrayList<>();

		// Creating a File object for directory
		File directoryPath = new File("www/");
		filenames.addAll(Arrays.asList(directoryPath.list()));

		if (filenames.size() > 0) {
			StringBuilder builder = new StringBuilder();
			builder.append("<ul>\n");
			for (var filename : filenames) {
				builder.append("<li>" + filename + "</li>");
			}
			builder.append("</ul>\n");
			return builder.toString();
		} else {
			return "No files in directory";
		}
	}

	/**
	 * Read bytes from a file and return them in the byte array. We read in blocks
	 * of 512 bytes for efficiency.
	 */
	public static byte[] readFileInBytes(File f) throws IOException {

		FileInputStream file = new FileInputStream(f);
		ByteArrayOutputStream data = new ByteArrayOutputStream(file.available());

		byte buffer[] = new byte[512];
		int numRead = file.read(buffer);
		while (numRead > 0) {
			data.write(buffer, 0, numRead);
			numRead = file.read(buffer);
		}
		file.close();

		byte[] result = data.toByteArray();
		data.close();

		return result;
	}

	/**
	 *
	 * a method to make a web request. Note that this method will block execution
	 * for up to 20 seconds while the request is being satisfied. Better to use a
	 * non-blocking request.
	 * 
	 * @param aUrl the String indicating the query url for the OMDb api search
	 * @return the String result of the http request.
	 *
	 **/
	public String fetchURL(String aUrl) {
		StringBuilder sb = new StringBuilder();
		URLConnection conn = null;
		InputStreamReader in = null;
		try {
			URL url = new URL(aUrl);
			conn = url.openConnection();
			if (conn != null)
				conn.setReadTimeout(20 * 1000); // timeout in 20 seconds
			if (conn != null && conn.getInputStream() != null) {
				in = new InputStreamReader(conn.getInputStream(), Charset.defaultCharset());
				BufferedReader br = new BufferedReader(in);
				if (br != null) {
					int ch;
					// read the next character until end of reader
					while ((ch = br.read()) != -1) {
						sb.append((char) ch);
					}
					br.close();
				}
			}
			in.close();
		} catch (Exception ex) {
			System.out.println("Exception in url request:" + ex.getMessage());
		}
		return sb.toString();
	}
}
