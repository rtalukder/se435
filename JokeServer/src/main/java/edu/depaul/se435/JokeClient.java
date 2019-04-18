package edu.depaul.se435;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.UUID;

/**
 * Class: SE435 - JokeClient
 * Author: Raquib Talukder
 **/

public class JokeClient {
    public static void main(String[] args) {
        // hostname of the server
        String hostname;
        // generate UUID for each new client that's connected
        UUID clientUUID = UUID.randomUUID();


        // if no arguments then use 'localhost' as server
        if (args.length < 1) {
            hostname = "localhost";
        }
        else{
            hostname = args[0];
        }

        System.out.println("Raquib Talukder's Inet Client v1.8 \n");
        System.out.println("Using server: " + hostname + ":1565");

        // accepting user input from command line
        BufferedReader input = new BufferedReader((new InputStreamReader(System.in)));

        try {
            String name;
            do {
                // take in user input
                System.out.print("\nPress 'enter' for a joke or proverb!\n");
                System.out.flush();

                // input from user
                name = input.readLine();

                // if input is 'quit' - leave loop - else call getRemoteAddress function
                if (!name.contains("quit")){
                    GetServerReturn(name, hostname);
                }
            } // if input is 'quit' - leave loop
            while (!name.contains("quit")); {
                System.out.println("Cancelled by user request.");
            }
        }
        catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    // function that will get remote address from the server connected to
    static void GetServerReturn(String name, String hostname){
        Socket socket;
        BufferedReader fromServer;
        PrintStream toServer;
        String textFromServer;

        try {
            // open connection to server on port#: 9999
            socket = new Socket(hostname, 9999);

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
}
