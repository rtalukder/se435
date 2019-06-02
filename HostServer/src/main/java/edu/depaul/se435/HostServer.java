package edu.depaul.se435;

/**
 * Class: CSC435 - HostServer Assignment
 * Author: Raquib Talukder
 **/

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

class AgentWorker extends Thread {
    Socket socket; // opened socket to the client
    AgentHolder parentAgentHolder; // maintains socket and state counter
    int port; // current port being used by the running thread

    // AgentWorker constructor
    AgentWorker (Socket socket, int port, AgentHolder agentHolder) {
        this.socket = socket;
        this.port = port;
        parentAgentHolder = agentHolder;
    }

    public void run() {
        //initialize variables
        PrintStream output;
        BufferedReader input;
        PrintStream toHostServer;
        BufferedReader fromHostServer;

        // code currently only runs on localhost:15650
        String NewHost = "localhost";
        int NewHostMainPort = 15650;

        String buffer = "";
        int newPort;
        Socket migratedSocket;

        try {

            // opening up input/output streams for communication with client
            output = new PrintStream(socket.getOutputStream());
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // read line sent from client
            String inLine = input.readLine();

            // string builder utilized to append lines of HTML code and to calculate accurate Content-Length size
            StringBuilder htmlString = new StringBuilder();

            // log to console
            System.out.println();
            System.out.println("Request line: " + inLine);

            // if client requests to migrate
            if(inLine.indexOf("migrate") > -1) {

                // create a new instance of the socket on the localhost listening on 15650
                migratedSocket = new Socket(NewHost, NewHostMainPort);
                fromHostServer = new BufferedReader(new InputStreamReader(migratedSocket.getInputStream()));

                // open an output stream to send to the newly migrated socket
                toHostServer = new PrintStream(migratedSocket.getOutputStream());
                toHostServer.println("Please host me. Send my port! [State=" + parentAgentHolder.agentState + "]");
                toHostServer.flush();

                // continuously loop until new port is returned
                for(;;) {
                    // once sub-string of "[Port=" is received, break out of loop
                    buffer = fromHostServer.readLine();
                    if(buffer.indexOf("[Port=") > -1) {
                        break;
                    }
                }

                // parse through the response in order to extract the new port number
                String tempbuf = buffer.substring( buffer.indexOf("[Port=")+6, buffer.indexOf("]", buffer.indexOf("[Port=")) );
                // convert extracted port number string into an integer
                newPort = Integer.parseInt(tempbuf);
                // log to console
                System.out.println("newPort is: " + newPort);

                // use a utility function to prepare HTML string and headers
                htmlString.append(AgentListener.sendHTMLheader(newPort, NewHost, inLine));
                // return HTML string will include message saying that they have been migrated to a new port
                htmlString.append("<h3>We are migrating to host " + newPort + "</h3> \n");
                htmlString.append("<h3>View the source of this page to see how the client is informed of the new location.</h3> \n");

                // add final line needed for the HTML code
                htmlString.append(AgentListener.sendHTMLsubmit());

                // log to console that socket is being migrated and old port will be closed
                System.out.println("Killing parent listening loop.");
                // close the listening socket
                parentAgentHolder.socket.close();

            }

            // if substring includes 'person'
            else if(inLine.indexOf("person") > -1) {
                // increment the conversation counter
                parentAgentHolder.agentState++;

                // build HTML reply with updated counter and what was included in the client submitted form
                htmlString.append(AgentListener.sendHTMLheader(port, NewHost, inLine));
                htmlString.append("<h3>We are having a conversation with state   " + parentAgentHolder.agentState + "</h3>\n");
                htmlString.append(AgentListener.sendHTMLsubmit());
            }

            // invalid request received
            // send a properly formatted string
            // favicon.ico requests frequently enter this statement
            else {
                htmlString.append(AgentListener.sendHTMLheader(port, NewHost, inLine));
                htmlString.append("You have not entered a valid request!\n");
                htmlString.append(AgentListener.sendHTMLsubmit());
            }

            // send built HTML string to the client and close the socket
            AgentListener.sendHTMLtoStream(htmlString.toString(), output);
            socket.close();

        } catch (IOException exception) {
            System.out.println("Exception caught");
        }
    }

}
// object that holds details about the running thread
class AgentHolder {
    ServerSocket socket;
    int agentState;

    // AgentHolder constructor
    AgentHolder(ServerSocket socket) { this.socket = socket;}
}

// object that keeps track of conversations and what port the thread is running on
class AgentListener extends Thread {
    Socket socket;
    int port;

    // AgentListener constructor
    AgentListener(Socket socket, int port) {
        this.socket = socket;
        this.port = port;
    }

    // initial state begins at 0
    int agentState = 0;

    // when thread is started
    public void run() {
        BufferedReader input;
        PrintStream output;
        String NewHost = "localhost";
        // log to console
        System.out.println("In AgentListener Thread");

        try {
            String buffer;
            // open up input/output streams on the socket
            output = new PrintStream(socket.getOutputStream());
            input =  new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // read first like from the client
            buffer = input.readLine();

            // if not null and contains sub-string "[State="
            // will be parsing it to extract data
            if(buffer != null && buffer.indexOf("[State=") > -1) {
                // parse through the received string from the client and extract the state
                String tempbuf = buffer.substring(buffer.indexOf("[State=")+7, buffer.indexOf("]", buffer.indexOf("[State=")));
                // convert state to an integer
                agentState = Integer.parseInt(tempbuf);

                // log to server console
                System.out.println("agentState is: " + agentState);
            }

            // log to server console
            System.out.println(buffer);

            // string builder to start appending portions of the HTML response
            StringBuilder htmlResponse = new StringBuilder();

            // create HTML header using utility function
            htmlResponse.append(sendHTMLheader(port, NewHost, buffer));
            // appending details of the current running thread to be shown on the HTML code shipped to the client
            htmlResponse.append("Now in Agent Looper starting Agent Listening Loop\n<br />\n");
            htmlResponse.append("[Port="+port+"]<br/>\n");

            // add final boilerplate line for HTML code
            htmlResponse.append(sendHTMLsubmit());

            // create the MIME header and then send the HTML code created from above
            sendHTMLtoStream(htmlResponse.toString(), output);

            // now open a connection at the port
            ServerSocket servsock = new ServerSocket(port,2);

            // assign created port to the AgentHolder which keeps track of object details for the conversation
            AgentHolder agentHold = new AgentHolder(servsock);
            agentHold.agentState = agentState;

            // continuously loop waiting for clients
            while(true) {
                // accept the connection from the client
                socket = servsock.accept();
                // log to console that a connection has been received
                System.out.println("Got a connection to agent at port " + port);

                // create new worker thread and start it
                new AgentWorker(socket, port, agentHold).start();
            }

        } catch(IOException exception) {
            // exception caught
            // this happens when an error occurs OR when we switch port
            System.out.println("Either connection failed, or just killed listener loop for agent at port " + port);
            System.out.println(exception);
        }
    }

    // utility function to parse through the client request and build HTML string
    // takes in the client input from the form, current running port, and host
    static String sendHTMLheader(int localPort, String NewHost, String inLine) {
        StringBuilder htmlString = new StringBuilder();

        htmlString.append("<html><head> </head><body>\n");
        // local port is in the 30000s and on the local host
        htmlString.append("<h2>This is for submission to PORT " + localPort + " on " + NewHost + "</h2>\n");
        // text client entered in the form
        htmlString.append("<h3>You sent: "+ inLine + "</h3>");
        // send to localhost on current running port of the thread
        htmlString.append("\n<form method=\"GET\" action=\"http://" + NewHost +":" + localPort + "\">\n");
        // another text form for the client to enter additional text or 'migrate' to another port
        htmlString.append("Enter text or <i>migrate</i>:");
        // HTML variables to store the form input
        htmlString.append("\n<input type=\"text\" name=\"person\" size=\"20\" value=\"YourTextInput\" /> <p>\n");

        // return the built HTML string
        return htmlString.toString();
    }

    // complete the string from the function above
    static String sendHTMLsubmit() {
        return "<input type=\"submit\" value=\"Submit\"" + "</p>\n</form></body></html>\n";
    }

    // function to add MIME header and also calculate accurate content-length size
    static void sendHTMLtoStream(String html, PrintStream outputSocket) {
        // MIME header
        outputSocket.println("HTTP/1.1 200 OK");
        outputSocket.println("Content-Length: " + html.length());
        // Content-Type = html because we're sending HTML code in the next line
        outputSocket.println("Content-Type: text/html");
        outputSocket.println("");
        // HTML code to be sent to the browser
        outputSocket.println(html);
    }
}

// main function of the HostServer
// job is to open a socket on port 15650 and listen to incoming clients
// clients are then transferred to a port in the 30000's
public class HostServer {
    // next port begins at 30000 and incremented by one each time each time a new client connects
    public static int NextPort = 30000;

    public static void main(String[] a) throws IOException {
        // defaults for the HostServer
        // queue length = 6
        // listening port# = 15650
        int q_len = 6;
        int port = 15650;
        Socket sock;

        ServerSocket servsock = new ServerSocket(port, q_len);
        System.out.println("Raquib Talukder's HostServer started at port 15650.");
        System.out.println("Connect from 1 to 3 browsers using \"http:\\\\localhost:15650\"\n");
        // listen on port 15650 for clints and
        while(true) {
            // when a new client connects, increment next port by 1
            NextPort = NextPort + 1;
            // accept requests from a client
            sock = servsock.accept();

            // log to console
            System.out.println("Starting AgentListener at port " + NextPort);
            // accept request and start an individual thread for it
            new AgentListener(sock, NextPort).start();
        }
    }
}
