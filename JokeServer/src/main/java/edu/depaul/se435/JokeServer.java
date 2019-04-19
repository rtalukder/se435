package edu.depaul.se435;

/**
 * Class: SE435 - JokeServer
 * Author: Raquib Talukder
 **/

// Java I/O and networking libs
import java.io.*;
import java.net.*;
import java.util.*;

class Worker extends Thread {
    Socket socket;

    // Worker constructor assigning argument 'socket' to local variable 'socket'
    Worker(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        // receive input/output from the opened socket - non-admin
        BufferedReader inputSocket = null;
        PrintStream outputSocket = null;

        // Try and open streams with given socket
        try {
            inputSocket = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            outputSocket = new PrintStream((socket.getOutputStream()));

            try {
                String client;

                // receive line from client
                client = inputSocket.readLine();
                System.out.println("Joke Mode = " + ServerStatus.jokeMode);
                if(ServerStatus.getJokeMode()){
                    outputSocket.println(ServerStatus.jokeStack.pop());
                }
                else{
                    outputSocket.println(ServerStatus.proverbStack.pop());
                }


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
}

// create listener for admin client
class AdminListener implements Runnable {
    public void run() {
        final int port = 21460;
        final int queue_len = 6;
        Socket socket;

        try {
            // admin client listening on port#: 21460
            ServerSocket serverSocket = new ServerSocket(port, queue_len);

            while (true) {
                socket = serverSocket.accept();
                new AdminWorker(socket).run();
            }
        } catch (IOException exception) {
            System.out.println("Server error - can't open socket.");
            exception.printStackTrace();
        }
    }
}

class AdminWorker extends Thread {
    Socket socket;

    // AdminWorker constructor assigning argument 'socket' to local variable 'socket'
    AdminWorker(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        // receive input/output from the opened socket - admin
        BufferedReader input = null;
        PrintStream output = null;

        // Try and open streams with given socket
        try {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintStream((socket.getOutputStream()));

            try {
                String serverMode;

                // receive line from admin client - pass it to the ServerStatus function
                serverMode = input.readLine();
                System.out.println(serverMode);
                System.out.println(ServerStatus.getJokeMode());
                ServerMode(serverMode, output);

            } catch (IOException exception) {
                System.out.println("Server error - couldn't open socket");
                exception.printStackTrace();
            }
            // close connection
            socket.close();
        } catch (IOException exception) {
            System.out.println("Server error - couldn't open up streams");
            exception.printStackTrace();
        }
    }

    // updates the JokeServer mode to 'joke' or' proverb'
    private void ServerMode(String serverMode, PrintStream outputSocket) {
        switch (serverMode) {
            case "joke":
                outputSocket.println("Changing mode to joke");
                ServerStatus.jokeMode = true;
                break;
            case "proverb":
                outputSocket.println("Changing mode to proverb");
                ServerStatus.jokeMode = false;
                break;
            default:
                outputSocket.println("Not an option. Please select one from the list given.");
                break;
        }
    }
}

// stores details about what mode the server is currently in
// by default it starts in joke mode
class ServerStatus {
    static boolean jokeMode = true;

    private static String[] jokes = {"A: <joke A>",
                                     "B: <joke B>",
                                     "C: <joke C>",
                                     "D: <joke D>"};

    private static String[] proverbs = {"A: <proverb A>",
                                        "B: <proverb B>",
                                        "C: <proverb C>",
                                        "D: <proverb D>"};

    static Stack<String> jokeStack = new Stack<>();
    static Stack<String> proverbStack = new Stack<>();

    ServerStatus() {}

    static boolean getJokeMode(){
        return jokeMode;
    }

    static void InitializeJokeStack(){
        for(String joke : jokes){
            jokeStack.push(joke);
        }
    }

    static void InitializeProverbStack(){
        for(String proverb : proverbs){
            proverbStack.push(proverb);
        }
    }

}

// main class to start listener for admin client + listener for client connections
public class JokeServer {
    public static void main(String[] args) throws IOException {
        final int port = 9999;
        final int queue_len = 6;
        Socket socket;

        // create socket listening on port#: 21460 + queue length of 6
        // this socket is for admin clients
        // must be ran on its own thread otherwise client code will never start
        System.out.println("Starting the AdminClient, listening on port 24160 for administrators.\n");
        Thread adminClientThread = new Thread(new AdminListener());
        adminClientThread.start();

        // create socket listening on port#: 9999 + queue length of 6
        // this socket is for regular clients
        ServerSocket serverSocket = new ServerSocket(port, queue_len);
        System.out.println("The JokeServer v1.8 starting up, listening on port 9999 for clients.\n" );

        // accept incoming coming connections and then pass them along to the worker class
        while (true) {
            socket = serverSocket.accept();
            ServerStatus.InitializeJokeStack();
            ServerStatus.InitializeProverbStack();
            new Worker(socket).run();
        }

    }
}

