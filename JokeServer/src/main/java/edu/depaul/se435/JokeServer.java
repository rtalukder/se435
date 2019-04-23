package edu.depaul.se435;

/**
 * Class: SE435 - JokeServer
 * Author: Raquib Talukder
 **/


/*--------------------------------------------------------

1. Raquib Talukder /  4/22/19

2. Java version used, if not the official version for the class:

(Windows)
java version "1.8.0_71"
Java(TM) SE Runtime Environment (build 1.8.0_71-b15)
Java HotSpot(TM) 64-Bit Server VM (build 25.71-b15, mixed mode)

(MacOS)
java version "10.0.2" 2018-07-17
Java(TM) SE Runtime Environment 18.3 (build 10.0.2+13)
Java HotSpot(TM) 64-Bit Server VM 18.3 (build 10.0.2+13, mixed mode)

3. Precise command-line compilation examples / instructions:

> javac JokeServer.java
> javac JokeClient.java
> javac JokeClientAdmin.java

4. Precise examples / instructions to run this program:

In separate shell windows, start the JokeServer, JokeClient and JokeClientAdmin

> java JokeServer
> java JokeClient
> java JokeClientAdmin

All acceptable commands are displayed on the various consoles.

The program only currently runs through localhost

> java JokeClient
> java JokeClientAdmin
> java JokeClient localhost
> java JokeClientAdmin localhost

5. List of files needed for running the program.

 a. JokeServer.java
 b. JokeClient.java
 c. JokeClientAdmin.java

5. Notes:

- Username has UUID appended to the end of the username string.
- No secondary JokeServer was implemented
- JokeClientAdmin has the ability to shut the server down


----------------------------------------------------------*/

// Java I/O and networking libs

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.logging.Logger;

class Worker extends Thread {
    Socket socket;

    // Worker constructor assigning argument 'socket' to local variable 'socket'
    Worker(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        // receive input/output from the opened socket - non-admin
        BufferedReader inputSocket;
        PrintStream outputSocket;

        // Try and open streams with given socket
        try {
            inputSocket = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            outputSocket = new PrintStream((socket.getOutputStream()));

            try {
                // check and see if server is powered on
                if (ServerStatus.GetServerPowerState()){
                    // receive username from client
                    String username = inputSocket.readLine();

                    // check if username exists in hashmap
                    // if it does - return a joke or proverb depending on the server mode
                    if (ServerStatus.UserExists(username)) {
                        ServerStatus.Logger("User found: " + username);
                        ServerStatus.GetReturnStatement(outputSocket, ServerStatus.GetJokeMode(), username);

                    }
                    // if the user doesn't exist - register the user in the hashmap
                    // then return a joke or proverb depending on the server mode
                    else {
                        ServerStatus.Logger("New user: " + username + " registered");
                        ServerStatus.RegisterUser(username);
                        ServerStatus.GetReturnStatement(outputSocket, ServerStatus.GetJokeMode(), username);
                    }
                }
                // server is currently powered off
                else {
                    outputSocket.println("Server is currently offline. Please visit later.");
                    ServerStatus.Logger("Server is currently offline. Please visit later.");
                }
                // catch IOE
            } catch (IOException exception) {
                System.out.println("Server read error");
                exception.printStackTrace();
            }
            // close socket after returning joke, proverb, or catching an exception
            socket.close();

            // catch IOE
        } catch (IOException exception) {
            System.out.println("Socket error");
            exception.printStackTrace();
        }
    }
}

// create listener for admin client
class AdminListener implements Runnable {
    public void run() {
        final int port = 5050;
        final int queue_len = 6;
        Socket socket;

        try {
            // admin client listening on port#: 5050
            ServerSocket serverSocket = new ServerSocket(port, queue_len);

            while (true) {
                // accept admin client sessions
                socket = serverSocket.accept();
                new AdminWorker(socket).run();
            }
            // catch IOE
        } catch (IOException exception) {
            System.out.println("Server error - can't open socket.");
            exception.printStackTrace();
        }
    }
}

// class to allow modification of server behavior such as switching modes
class AdminWorker extends Thread {
    Socket socket;

    // AdminWorker constructor assigning argument 'socket' to local variable 'socket'
    AdminWorker (Socket socket) { this.socket = socket; }

    public void run() {
        // receive input/output from the opened socket - admin
        BufferedReader inputSocket;
        PrintStream outputSocket;

        // Try and open streams with given socket
        try {
            inputSocket = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            outputSocket = new PrintStream((socket.getOutputStream()));

            try {
                String serverMode;

                // receive line from admin client and update the server mode
                serverMode = inputSocket.readLine();
                UpdateServerMode(serverMode, outputSocket);

                // catch IOE
            } catch (IOException exception) {
                System.out.println("Server error - couldn't open socket");
                exception.printStackTrace();
            }
            // close connection after changing server mode or catching exception
            socket.close();

            // catch IOE
        } catch (IOException exception) {
            System.out.println("Server error - couldn't open up streams");
            exception.printStackTrace();
        }
    }

    // updates the JokeServer mode to 'joke' or' proverb' and power state from 'on' or 'off'
    // all changes and mistakes are logged
    private void UpdateServerMode(String serverMode, PrintStream outputSocket) throws IOException {
        switch (serverMode) {
            case "joke":
                outputSocket.println("Changing server mode to joke");
                ServerStatus.Logger("Changing server mode to joke.");
                ServerStatus.jokeMode = true;
                break;
            case "proverb":
                outputSocket.println("Changing server mode to proverb");
                ServerStatus.Logger("Changing server mode to proverb.");
                ServerStatus.jokeMode = false;
                break;
            case "on":
                outputSocket.println("Turning server on.");
                ServerStatus.Logger("Turning server on.");
                ServerStatus.serverOn = true;
                break;
            case "off":
                outputSocket.println("Turning server off.");
                ServerStatus.Logger("Turning server off.");
                ServerStatus.serverOn = false;
                break;
            case "get status":
                outputSocket.println("JokeServer status || Joke Mode: " + ServerStatus.GetJokeMode() + " || Server Powered On: " + ServerStatus.GetServerPowerState());
                ServerStatus.Logger("Turning server off.");
                break;
            default:
                outputSocket.println("Not an option. Please select one from the list given.");
                ServerStatus.Logger("Not an option. Please select one from the list given.");
                break;
        }
    }
}

// stores details about what mode the server is currently in and handles to send jokes or proverbs
// by default it starts in joke mode and powered on
class ServerStatus {
    static boolean jokeMode = true;
    static boolean serverOn = true;

    // joke array
    private static final String[] jokes = {"JD: <username> <joke D>",
                                           "JC: <username> <joke C>",
                                           "JB: <username> <joke B>",
                                           "JA: <username> <joke A>"};

    // proverb array
    private static final String[] proverbs = {"PD: <username> <proverb D>",
                                              "PC: <username> <proverb C>",
                                              "PB: <username> <proverb B>",
                                              "PA: <username> <proverb A>"};

    // hold information about users and their respective joke and proverb stacks
    private static HashMap<String, ArrayList<Stack<String>>> userStatus = new HashMap<>();

    // default constuctor
    ServerStatus() {}

    // return if server is in joke or proverb mode
    static boolean GetJokeMode(){
        return jokeMode;
    }

    // return if server is on or off
    static boolean GetServerPowerState() { return serverOn; }

    // add jokes in order to stack for the first cycle
    static Stack InitializeJokeStack(){
        Stack<String> jokeStack = new Stack<>();

        for(String joke : jokes){
            jokeStack.push(joke);
        }

        return jokeStack;
    }

    // once all jokes have been seen in order - create a randomized stack of jokes
    static void RandomizeJokeStack(String username){
        // create a list of jokes that can be randomized
        List<String> randomizedJokeList = Arrays.asList(jokes);
        Collections.shuffle(randomizedJokeList);
        // new joke stack with random order
        Stack<String> jokeStack = new Stack<>();
        // get current proverb stack - we will not be changing it
        Stack<String> proverbStack = ServerStatus.GetProverbStack(username);

        // push randomized jokes onto stack
        for(String joke : randomizedJokeList){
            jokeStack.push(joke);
        }

        // add randomized joke stack and current proverb stack to the ArrayList
        ArrayList<Stack<String>> newJokeStack = new ArrayList<>();
        newJokeStack.add(jokeStack);
        newJokeStack.add(proverbStack);

        // update the users value in the hashmap
        // joke stack will be randomized and proverb stack will stay the same
        userStatus.put(username, newJokeStack);
    }

    // add proverbs in order to the stack for the first cycle
    static Stack InitializeProverbStack(){
        Stack<String> proverbStack = new Stack<>();

        for(String proverb : proverbs){
            proverbStack.push(proverb);
        }

        return proverbStack;
    }

    // once all proverbs have been seen in order - create a randomized stack of proverbs
    static void RandomizeProverbStack(String username){
        // create a list of jokes that can be randomized
        List<String> randomizedProverbList = Arrays.asList(proverbs);
        Collections.shuffle(randomizedProverbList);
        // new proverb stack with random order
        Stack<String> proverbStack = new Stack<>();
        // get current joke stack - we will not be changing it
        Stack<String> jokeStack = ServerStatus.GetJokeStack(username);

        // push randomized proverbs onto stack
        for(String proverb : randomizedProverbList){
            proverbStack.push(proverb);
        }

        // add randomized proverb stack and current joke stack to a list
        ArrayList<Stack<String>> newProverbStack = new ArrayList<>();
        newProverbStack.add(jokeStack);
        newProverbStack.add(proverbStack);

        // update the users value in the hashmap
        // proverb stack will be randomized and joke stack will stay the same
        userStatus.put(username, newProverbStack);
    }

    // return joke stack - used to determine if stack is empty
    static Stack<String> GetJokeStack(String username){
        ArrayList <Stack<String>> userStacks = ServerStatus.GetUserStacks((username));
        Stack<String> jokeStack = userStacks.get(0);

        return jokeStack;
    }

    // return proverb stack - used to determine if stack is empty
    static Stack<String> GetProverbStack(String username){
        ArrayList <Stack<String>> userStacks = ServerStatus.GetUserStacks((username));
        Stack<String> proverbStack = userStacks.get(1);

        return proverbStack;
    }

    // register new users into the hashmap which is keeping track of users respective joke and proverb stacks
    static void RegisterUser(String username) {
        ArrayList<Stack<String>> jokeProverbList = new ArrayList<>();

        jokeProverbList.add(InitializeJokeStack());
        jokeProverbList.add(InitializeProverbStack());

        userStatus.put(username, jokeProverbList);
    }

    // check if the user exists
    static boolean UserExists(String username){
        return userStatus.containsKey(username);
    }

    // return user's stacks - will aid in checking if the stacks are empty
    static ArrayList<Stack<String>> GetUserStacks(String username){
        return userStatus.get(username);
    }

    // return a joke or proverb for a particular user
    static void GetReturnStatement (PrintStream outputSocket, Boolean jokeMode, String username) throws IOException {
        if (jokeMode) {
            // check if joke stack is empty - if it's not it will pop a joke off the respective users joke stack
            if((ServerStatus.GetJokeStack(username).empty())) {
                outputSocket.println("Joke cycle has been completed. Will begin to send randomized jokes.");
                ServerStatus.Logger("Joke cycle has been completed. Will begin to send randomized jokes.");
                ServerStatus.RandomizeJokeStack(username);
            }

            // pop a joke off the stack and replace the <username> portion of the string with the actual username
            Stack<String> jokeStack = ServerStatus.GetJokeStack(username);
            String unformattedString = jokeStack.pop();
            outputSocket.println(unformattedString.replace("<username>", username));
            ServerStatus.Logger(unformattedString.replace("<username>", username));
        }
        else {
            // check if proverb stack is empty - if it's not it will pop a joke off the respective users proverb stack
            if((ServerStatus.GetProverbStack(username).empty())) {
                outputSocket.println("Proverb cycle has been completed. Will begin to send randomized proverbs.");
                ServerStatus.Logger("Proverb cycle has been completed. Will begin to send randomized proverbs.");
                ServerStatus.RandomizeProverbStack(username);
            }

            // pop a proverb off the stack and replace the <username> portion of the string with the actual username
            Stack<String> proverbStack = ServerStatus.GetProverbStack(username);
            String unformattedString = proverbStack.pop();
            outputSocket.println(unformattedString.replace("<username>", username));
            ServerStatus.Logger(unformattedString.replace("<username>", username));
        }
    }

    // writes logs to "JokeLog.txt" - all logs are appended and nothing is deleted
    static void Logger(String writeToFile) throws IOException {
        // setting up file, handler, and formatter - all logs will be appended
        FileHandler fileHandler = new FileHandler("JokeLogger.txt", true);
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

// main class to start listener for admin client + listener for client connections
public class JokeServer {
    public static void main(String[] args) throws IOException {
        final int port = 4545;
        final int queue_len = 6;
        Socket socket;

        // create socket listening on port#: 5050 + queue length of 6
        // this socket is for admin clients
        // must be ran on its own thread otherwise client code will never start
        System.out.println("Starting the AdminClient, listening on port 5050 for administrators.\n");
        ServerStatus.Logger("Starting the AdminClient, listening on port 5050 for administrators.");
        Thread adminClientThread = new Thread(new AdminListener());
        adminClientThread.start();

        // create socket listening on port#: 4545 + queue length of 6
        // this socket is for regular clients
        ServerSocket serverSocket = new ServerSocket(port, queue_len);
        System.out.println("The JokeServer v1.8 starting up, listening on port 4545 for clients.\n" );
        ServerStatus.Logger("The JokeServer v1.8 starting up, listening on port 4545 for clients.");

        // accept incoming client connections and then pass them along to the worker class
        while (true) {
            socket = serverSocket.accept();
            new Worker(socket).run();
        }

    }
}

