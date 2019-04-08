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
                String name;
                name = input.readLine();
                System.out.println("Looking up: " + name);
                // printRemoteAddress(name, output);

            }
            catch (IOException exception){
                System.out.println("Server read error");
                exception.printStackTrace();
            }
            // close socket after returning remote address or catching an exception
            socket.close();
        }
        catch (IOException ioexception){
            System.out.println(ioexception);
        }

    }

}


public class InetServer {
}
