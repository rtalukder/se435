/**
 * Class: SE435 - MyListener
 * Author: Raquib Talukder
 **/

package edu.depaul.se435;
// Java I/O and networking libs
import java.io.*;
import java.net.*;


class ListenerWorker extends Thread {
    Socket socket;

    // constructor assigning argument 'socket' to local variable 'socket'
    ListenerWorker(Socket socket) {
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

            // read line from input
            String request;
            while (true){
                request = input.readLine();

                // keep reading from socket until null is received
                if (request != null){
                    System.out.println(request);
                }
                System.out.flush();
                socket.close();
            }
        }
        catch (IOException ioexception) {
            System.out.println(ioexception);
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

public class MyListener {
    public static void main(String[] args) throws IOException {
        int port = 2540;
        int queue_len = 6;
        Socket socket;

        // create socket listening on port#: 2540 + queue length of 6
        ServerSocket serverSocket = new ServerSocket(port, queue_len);
        System.out.println("Raquib Talukder's Webserver 1.8 starting up, listening on port 2540.\n" );

        // accept incoming coming connections and then pass them along to the worker class
        while (true) {
            socket = serverSocket.accept();
            new ListenerWorker(socket).run();
        }

    }
}

