package edu.depaul.se435;

/**
 * Class: SE435 - JokeClientAdmin
 * Author: Raquib Talukder
 **/

// Java I/O and networking libs

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class JokeClientAdmin {
    public static void main(String[] args) throws IOException {
        // hostname of the JokeServer
        String hostname;

        // if no arguments then use 'localhost' as server
        if (args.length < 1) {
            hostname = "localhost";
        }
        else{
            hostname = args[0];
        }

        System.out.println("Welcome to the JokeServer Admin Client");
        System.out.println("Administering server: " + hostname + ":5050");
        JokeClientAdmin.Logger("Welcome to the JokeServer Admin Client");
        JokeClientAdmin.Logger("Administering server: " + hostname + ":5050");

        // accepting user input from command line
        BufferedReader inputSocket = new BufferedReader((new InputStreamReader(System.in)));

        try {
            String serverMode;
            do {
                // take in user input
                System.out.print("\nSelect server mode: \n - Joke\n - Proverb \n - On\n - Off\nadmin@~ $ ");
                System.out.flush();

                // input from user
                serverMode = inputSocket.readLine();
                System.out.println(serverMode);

                // if input is 'quit' - leave loop - else call ChangeServerMode function
                if (!serverMode.contains("quit")){
                    ChangeServerMode(serverMode, hostname);
                }
            } // if input is 'quit' - leave loop
            while (!serverMode.contains("quit")); {
                System.out.println("Client admin service stopped by user request.");
            }
        }
        catch (IOException exception) {
            System.out.println("Admin client error - BufferedReader couldn't be opened.");
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
            // open connection to JokeServer admin client on port#: 5050
            // this is the admin port on the server
            socket = new Socket(hostname, 5050);

            // open input/output streams for socket to server
            fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            toServer = new PrintStream(socket.getOutputStream());

            // send the server mode and log it
            JokeClientAdmin.Logger(serverMode);
            toServer.println(serverMode.toLowerCase());
            toServer.flush();

            // read response from server and block while waiting for other connections from clients
            // will only read 1-3 lines of output from the server at a time
            for (int i = 1; i <=3; i++){
                textFromServer = fromServer.readLine();
                if (textFromServer != null){
                    System.out.println("\nServer response: "+ textFromServer);
                    JokeClientAdmin.Logger("Server response: "+ textFromServer);
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

    // writes logs to "JokeLog.txt" - all logs are appended and nothing is deleted
    static void Logger(String writeToFile) throws IOException {
        // setting up file, handler, and formatter - all logs will be appended
        FileHandler fileHandler = new FileHandler("JokeLog.txt", true);
        SimpleFormatter fileFormatter = new SimpleFormatter();
        fileHandler.setFormatter(fileFormatter);

        // open logger
        Logger logger = Logger.getLogger("JokeServer");
        logger.addHandler(fileHandler);

        // write to log and close handler after
        logger.info(writeToFile);
        fileHandler.close();
    }
}

