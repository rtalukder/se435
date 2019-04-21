package edu.depaul.se435;

/**
 * Class: SE435 - JokeClient
 * Author: Raquib Talukder
 **/

// Java I/O and networking libs

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.UUID;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class JokeClient {
    public static void main(String[] args) throws IOException {
        // hostname of the server
        String hostname;
        // generate UUID for each new client that's connected
        // it will be added to the end of the username
        String clientUUID = (UUID.randomUUID()).toString();


        // if no arguments then use 'localhost' as server
        if (args.length < 1) {
            hostname = "localhost";
        }
        else{
            hostname = args[0];
        }

        System.out.println("JokeClient v1.8 \n");
        System.out.println("Using server: " + hostname + ":4545");
        JokeClient.Logger("JokeClient v1.8");
        JokeClient.Logger("Using server: " + hostname + ":4545");

        // accepting user input from command line
        BufferedReader input = new BufferedReader((new InputStreamReader(System.in)));

        try {
            String usernameWithUUID = null;
            boolean userNotEntered = true;
            String userinput;
            do {
                // get username once client is opened
                // username will end up being 'username' + clientUUID
                if (userNotEntered) {
                    // take in user input
                    System.out.print("\nPlease enter a username to register and receive a joke or proverb - 'quit' to end: ");
                    System.out.flush();

                    // receive input - username
                    String username = input.readLine();
                    usernameWithUUID = username + clientUUID;
                    userNotEntered = false;

                    // if any empty user name is entered - used 'default_user-' + clientUUID
                    if (username.equals("")) usernameWithUUID="default_user-" + clientUUID;

                    // if not quit - then go get a joke or proverb from the JokeServer
                    if (!username.contains("quit")){
                        GetJokeOrProverb(usernameWithUUID, hostname);
                    }
                }
                System.out.print("\nPress 'enter' to receive a joke or proverb - 'quit' to end: ");
                System.out.flush();

                // read in enter or 'quit'
                userinput = input.readLine();

                // if input is 'quit' - leave loop - else call getRemoteAddress function
                if (!userinput.contains("quit")){
                    GetJokeOrProverb(usernameWithUUID, hostname);
                }
            } // if input is 'quit' - leave loop
            while (!userinput.contains("quit")); {
                System.out.println("Client service stopped by user request.");
            }
            // catch IOE
        } catch (IOException exception) {
            System.out.println("BufferedReader error - trouble opening");
            exception.printStackTrace();
        }
    }

    // function that will get remote address from the server connected to
    static void GetJokeOrProverb(String name, String hostname){
        Socket socket;
        BufferedReader fromServer;
        PrintStream toServer;
        String textFromServer;

        try {
            // open connection to server on port#: 4545
            socket = new Socket(hostname, 4545);

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
                    System.out.println("\nServer response: " + textFromServer);
                    JokeClient.Logger("Server response: " + textFromServer);
                }
            }
            // close socket once communication has completed or user ends session
            socket.close();

            // catch IOE
        } catch (IOException exception) {
            System.out.println("Socket error - JokeServer may not have been started");
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
