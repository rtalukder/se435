package edu.depaul.se435;

/**
 * Class: SE435 - JokeServer
 * Author: Raquib Talukder
 **/

// Java I/O and networking libs
import java.io.*;
import java.net.*;

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
                String name;

                // receive line from client
                name = inputSocket.readLine();
                System.out.println("Looking up: " + name);
                // pass name received from input to printRemoteAddress() function
                PrintRemoteAddress(name, outputSocket);

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

    static void PrintRemoteAddress(String name, PrintStream output) {
        try {
            output.println("this is a joke");
        } catch (Exception exception) {
            output.println("Something failed");
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

                // receive line from admin client - pass it to the ServerMode function
                serverMode = input.readLine();
                ServerMode(serverMode, output);

            } catch (IOException x) {
                System.out.println("Server read error");
                x.printStackTrace();
            }
            // close connection
            socket.close();
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
    }

    // updates the JokeServer mode to 'joke' or' proverb'
    private void ServerMode(String serverMode, PrintStream outputSocket) {
        switch (serverMode) {
            case "joke":
                outputSocket.println("Changing mode to joke");
//                AdminLooper._jokeMode = true;
//                AdminLooper._serverMode = true;
                break;
            case "proverb":
                outputSocket.println("Changing mode to proverb");
//                AdminLooper._jokeMode = false;
//                AdminLooper._serverMode = true;
                break;
            default:
                outputSocket.println("Not an option. Please select one from the list given. ");
                break;
        }
    }
}

public class JokeServer {
    public static void main(String[] args) throws IOException {
        int port = 9999;
        int queue_len = 6;
        Socket socket;

        // create socket listening on port#: 21460 + queue length of 6
        // this socket is for admin clients

        // create socket listening on port#: 9999 + queue length of 6
        // this socket is for regular clients
        ServerSocket serverSocket = new ServerSocket(port, queue_len);
        System.out.println("The JokeServer v1.8 starting up, listening on port 9999 for clients.\n" );

        // accept incoming coming connections and then pass them along to the worker class
        while (true) {
            socket = serverSocket.accept();
            new Worker(socket).run();
        }

    }
}

