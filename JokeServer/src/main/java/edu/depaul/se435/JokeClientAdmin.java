package edu.depaul.se435;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;

/**
 * Class: SE435 - JokeClientAdmin
 * Author: Raquib Talukder
 **/


public class JokeClientAdmin {
    public static void main(String[] args) {
        // hostname of the server
        String hostname;

        // if no arguments then use 'localhost' as server
        if (args.length < 1) {
            hostname = "localhost";
        }
        else{
            hostname = args[0];
        }

        System.out.println("Welcome to the JokeServer Admin Client");
        System.out.println("Administering server: " + hostname + ":21460");

        // accepting user input from command line
        BufferedReader input = new BufferedReader((new InputStreamReader(System.in)));

        try {
            String serverMode;
            do {
                // take in user input
                System.out.print("\nSelect server mode: \n - Joke\n - Proverb \nadmin@~ $ ");
                System.out.flush();

                // input from user
                serverMode = input.readLine();

                // if input is 'quit' - leave loop - else call ChangeServerMode function
                if (!serverMode.contains("quit")){
                    ChangeServerMode(serverMode, hostname);
                }
            } // if input is 'quit' - leave loop
            while (!serverMode.contains("quit")); {
                System.out.println("Cancelled by user request.");
            }
        }
        catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    // function that will get remote address from the server connected to
    static void ChangeServerMode(String serverMode, String hostname){
        Socket socket;
        BufferedReader fromServer;
        PrintStream toServer;
        String textFromServer;

        try {
            // open connection to server on port#: 21460
            // this is the admin port on the server
            socket = new Socket(hostname, 21460);

            // open input/output streams for socket to server
            fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            toServer = new PrintStream(socket.getOutputStream());

            // send the server mode
            toServer.println(serverMode.toLowerCase());
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

