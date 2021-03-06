/**
 * Class: SE435 - InetServer
 * Author: Raquib Talukder
 **/

package edu.depaul.se435;
// Java I/O and networking libs
import java.io.*;
import java.net.*;


class Worker extends Thread {
    Socket socket;

    // constructor assigning argument 'socket' to local variable 'socket'
    Worker(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        // receive input/output from the opened socket
        BufferedReader input = null;
        PrintStream output = null;

        // Try and open streams with given socket
        try {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintStream((socket.getOutputStream()));

            try {
                // read line from input
                String name;
                name = input.readLine();
                System.out.println("Looking up: " + name);
                // pass name received from input to printRemoteAddress() function
                printRemoteAddress(name, output);

            } catch (IOException exception) {
                System.out.println("Server read error");
                exception.printStackTrace();
            }
            // close socket after returning remote address or catching an exception
            socket.close();
        } catch (IOException ioexception) {
            System.out.println(ioexception);
        }
    }

    static void printRemoteAddress(String name, PrintStream output) {
        try {
            output.println("Looking up: " + name + "...");
            // check if we can resolve the name to get details about the hostname and IP address
            // if it doesn't exist, an exception will be thrown back at the client
            InetAddress host = InetAddress.getByName(name);
            output.println("Hostname: " + host.getHostName());
            // return IP address - uses toText() function to convert to a string
            output.println("Host IP: " + toText(host.getAddress()));
        } catch (UnknownHostException exception) {
            output.println("Failed to find hostname: " + name);
        }
    }

    // IP address to text conversion
    static String toText(byte ip[]) { /* Make portable for 128 bit format */
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < ip.length; ++i) {
            if (i > 0) result.append(".");
            result.append(0xff & ip[i]);
        }
        return result.toString();
    }
}

public class InetServer {
    public static void main(String[] args) throws IOException {
        int port = 9999;
        int queue_len = 6;
        Socket socket;

        // create socket listening on port#: 9999 + queue length of 6
        ServerSocket serverSocket = new ServerSocket(port, queue_len);
        System.out.println("Raquib Talukder's Inet server 1.8 starting up, listening on port 9999.\n" );

        // accept incoming coming connections and then pass them along to the worker class
        while (true) {
            socket = serverSocket.accept();
            new Worker(socket).run();
        }

    }
}
