//
// Multithreaded Java WebServer
// (C) 2001 Anders Gidenstam
// (based on a lab in Computer Networking: ..)
//

import java.io.*;
import java.net.*;
import java.util.*;
import java.net.InetAddress.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.text.*;

public final class WebServer
{
    public static void main(String argv[]) throws Exception
    {
    // Set port number
        int port = 0;

    // Establish the listening socket
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Port number is: "+serverSocket.getLocalPort());


    // Wait for and process HTTP service requests
        while (true) {
        // Wait for TCP connection
            Socket requestSocket = serverSocket.accept();
            requestSocket.setSoLinger(true, 5);

        // Create an object to handle the request
            HttpRequest request  = new HttpRequest(requestSocket);

        //request.run();

        // Create a new thread for the request
            Thread thread = new Thread(request);

        // Start the thread
            thread.start();
        }
    }
}

final class HttpRequest implements Runnable
{
    // Constants
    //   Recognized HTTP methods
    final static class HTTP_METHOD
    {
        final static String GET  = "GET";
        final static String HEAD = "HEAD";
        final static String POST = "POST";
    }

    final static String HTTPVERSION = "HTTP/1.0";
    final static String CRLF = "\r\n";
    Socket socket;

    // Constructor
    public HttpRequest(Socket socket) throws Exception
    {
        this.socket = socket;
    }

    // Implements the run() method of the Runnable interface
    public void run()
    {
        try {
            processRequest();
        } catch (Exception e) {
            System.out.println(e);
        }
    }


// ============== START OF OUR CODE ============
    
    // Process a HTTP request
	/**
	 * Processes an incoming http request.
	 * <p>
	 * This method processes requests of the GET and HEAD form. 
	 * It then writes a response to the appropriate output stream.
	 * It does not process POST requests nor conditional GET.
	 */
    private void processRequest() throws Exception 
    {
        // Get the input and output streams of the socket.
        InputStream ins       = socket.getInputStream();
        DataOutputStream outs = new DataOutputStream(socket.getOutputStream());

        // Set up input stream filters
        BufferedReader br = new BufferedReader(new InputStreamReader(ins));

        // Get the request line of the HTTP request
        String requestLine = br.readLine();
        String response;

        if (requestLine.startsWith("GET")) {
            response = processGET(requestLine);
        } else if (requestLine.startsWith("HEAD")) {
            response = processHEAD(requestLine);
        } else if (requestLine.startsWith("POST")) {
            response = generateFail(501);
        } else {
            response = generateFail(400);
        }

        outs.writeBytes(response);
        // Close streams and sockets
        outs.close();
        br.close();
        socket.close();
    }

	/**
	 * Processes a GET request and generates a response.
	 * <p>
	 * This method processes an http GET request and generates and returns a response string.
	 * It uses the @processHEAD method to generate the status and header lines and then adds the data to the response.
	 *
	 * @param rl	The request line to be processed
	 * @return 		An http response
	 */
    private String processGET(String rl) {
    	String head = processHEAD(rl);
    	// In case of error
    	if (head.contains("HTTP/1.0 4") || head.startsWith("HTTP/1.0 5")) {
    		return head;
    	}
    	return head + readFile(rl.split(" ")[1]) + CRLF;
    }

	/**
	 * Processes a HEAD request and generates a response.
	 * <p>
	 * This method processes an http HEAD request and generates and returns a response string.
	 * The return string contains a status line and header lines.
	 *
	 * @param rl	The request line to be processed
	 * @return		An http response containing the status line and header lines
	 */
    private String processHEAD(String rl) {
        String[] list = rl.split(" ");
        if (list.length != 3) return generateFail(400);
        if (!list[1].startsWith("/")) return generateFail(400);
        if ((list[1]).equals("/")) list[1] = "/index.html";

		File file;
        try {
        	file = new File(list[1]);
        } catch (NullPointerException e) {
        	return generateFail(400);
        }

        if (!file.canRead()) return generateFail(404);

        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.0 200 OK");                   sb.append(CRLF);
        sb.append(getDate(System.currentTimeMillis())); sb.append(CRLF);
        sb.append("Server: servername");                sb.append(CRLF);
        sb.append("Last-Modified: ");
        sb.append(getDate(file.lastModified()));        sb.append(CRLF);
        sb.append("Content-Length: ");
        sb.append(file.length());                       sb.append(CRLF);
        sb.append("Content-Type: ");
        sb.append(contentType(list[1]));                sb.append(CRLF);
       	sb.append(CRLF);

       	return sb.toString();

    }

    // generates a failure string
	/**
	 * Generates a failure string of standard form.
	 * 
	 * @param status	The error code
	 * @return			A string containing the error message
	 */
    private String generateFail(int status) {
        String message = HTTPVERSION + " ";
        switch (status) {
            case 400: message += "400 Bad Request "; break;
            case 404: message += "404 Not Found "; break;
            case 500: message += "500 Internal Server Error "; break; // TODO: Skicka med html
            case 501: message += "501 Not Implemented"; break;
            default: message += "-1 Error Not Found "; break;
        }
        return message + CRLF + getDate(System.currentTimeMillis());
    }

    // generates a date string from a long
	/**
	 * Generates a string with a given date on standard form.
	 * 
	 * @param time	The date
	 * @return		A string containing the formatted date
	 */
    private String getDate(long time) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd,yyyy HH:mm");
        Date resultdate = new Date(time);
        return "Date: " + sdf.format(resultdate);
    }

    // Reads file content from the filesystem
	/**
	 * Reads a file to a string.
	 * 
	 * @param path	The path to the file
	 * @return		A string with the data of the file written to it
	 */
	private String readFile(String path) {
		byte[] fileAsText;
		try {
		    fileAsText = Files.readAllBytes(Paths.get(path));
		} catch (IOException e) {
			return generateFail(404);
		}
		String string = new String(fileAsText, Charset.defaultCharset());
        return string;
    }

// ================ END OF OUR CODE ======================


    private static void sendBytes(FileInputStream  fins,
      OutputStream     outs) throws Exception
    {
    // Coopy buffer
        byte[] buffer = new byte[1024];
        int    bytes = 0;

        while ((bytes = fins.read(buffer)) != -1) {
            outs.write(buffer, 0, bytes);
        }
    }

    private static String contentType(String fileName)
    {
        if (fileName.toLowerCase().endsWith(".htm") ||
            fileName.toLowerCase().endsWith(".html")) {
            return "text/html";
    } else if (fileName.toLowerCase().endsWith(".gif")) {
        return "image/gif";
    } else if (fileName.toLowerCase().endsWith(".jpg")) {
        return "image/jpeg";
    } else {
        return "application/octet-stream";
    }
}
}