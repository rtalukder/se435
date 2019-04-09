/**
 * Class: SE435 - InetClient
 * Author: Raquib Talukder
 **/

package edu.depaul.se435;
// Java I/O and networking libs
import java.io.*;
import java.net.*;

public class InetClient {
    final static int port = 8022;

    public static void main(String[] args) {
        // hostname of the server
        String hostname;

        if (args.length < 1) {
            hostname = "localhost";
        }
        else{
            hostname = args[0];
        }

        System.out.println("Raquib Talukder's Inet Client v1.8 \n");
        System.out.println("Using server: " + hostname + ":8022");

        // accepting user input from command line
        BufferedReader input = new BufferedReader((new InputStreamReader(System.in)));

        try {
            String name;
            do {
                // take in user input
                System.out.print("Enter a hostname or an IP address, (quit) to end: ");
                System.out.flush();
                name = input.readLine();

                if (!name.contains("quit")){
                    getRemoteAddess(name, hostname);
                }
            }
            while (!name.contains("quit")); {
                System.out.println("Cancelled by user request.");
            }
        }
        catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    static void getRemoteAddess(String name, String hostname){
        Socket socket;
        BufferedReader fromServer;
        PrintStream toServer;
        String textFromServer;

        try {
            // open connection to server on port#: 8022
            socket = new Socket(hostname, port);

            // open input/output streams for socket to server
            fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            toServer = new PrintStream(socket.getOutputStream());

            // send hostname or IP address to server
            toServer.println(name);
            toServer.flush();

            // read response from server and block while waiting for other connections from clients
            // will only read 1-3 lines of output from the server at a time
            for (int i = 1; i <=3; i++){
                textFromServer = fromServer.readLine();
                if (textFromServer != null){
                    System.out.println(textFromServer);
                }
            }

            // close socket once communication has completed or user ends session
            socket.close();
        }
        catch (IOException exception) {
            System.out.println("Socket error");
            exception.printStackTrace();
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
