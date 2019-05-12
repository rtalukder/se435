package edu.depaul.se435;

/**
 * Class: SE433 - MyWebserver
 * Author: Raquib Talukder
 **/

import java.io.*;
import java.net.*;
import java.util.Arrays;
/** !!!--- this package is needed in order for the program to work ---!!! **/
import org.apache.commons.io.FileUtils;


class Worker extends Thread {
    Socket socket;

    // constructor assigning argument 'socket' to local variable 'socket'
    Worker(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        // receive input/output from the opened socket
        BufferedReader input;
        PrintStream output;

        // Try and open streams with given socket
        try {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintStream((socket.getOutputStream()));

            // read line from input
            String request;
            while (true){
                request = input.readLine();

                // keep reading from socket until null is received
                if (request != null){
                    // read first line of the request from the client
                    // it will have information about the header, what to GET for the client, and HTTP version
                    System.out.println(request + "\n");
                    Logger.ServerLogger(request);
                    AnalyzeRequest(request, output);
                    // first line is the only thing i'm interested with, i'll break away from other lines in the request
                    break;
                }
                // flush the socket for any other output
                System.out.flush();
            }
        }
        catch (IOException ioexception) {
            System.out.println(ioexception);
        }
    }

    static void AnalyzeRequest(String request, PrintStream outputSocket) throws IOException {
        // splitting the request in order to get the file or filepath requested
        String[] splitRequest = request.split(" ");
        String filename = splitRequest[1];
        String HTTPversion = splitRequest[2];
        String contentType = GetContentType(filename);

        // pass the information along to create the information header
        // only creating 200 and 404 codes back to the client
        String header = CreateHeader(filename, HTTPversion, contentType);

        // there are three functions to deal with requests for files, looking at directories, or adding numbers
        // this will pass information gathered above to the respond to directory requests
        if (filename.endsWith("/")) ReplyToRequestDirectory(outputSocket, filename, header);

            // this will pass information gathered above to the respond to AddNum requests
        else if (filename.startsWith("/cgi/addnums.fake-cgi")) ReplyToRequestAddNums(outputSocket, filename, HTTPversion);

            // this will pass information gathered above to the respond to file requests
        else ReplyToRequest(outputSocket, filename, header);

        outputSocket.close();
    }

    static void ReplyToRequest(PrintStream outputSocket, String filename, String header) throws IOException {
        // get the full filepath of the file or directory
        // the pwd() function will return the working directory of where the server is running
        // filename is the file or directory requested
        String fullFilePath = pwd() + filename;
        try {
            // open up the static file to be sent over to the client
            InputStream targetFile = new FileInputStream(fullFilePath);

            try {

                // print to console
                System.out.println(header);
                System.out.println("Static file: " + filename + " sent to client.\n");

                // log response
                Logger.ServerLogger(header);
                Logger.ServerLogger("Static file: " + filename + " sent to client.\n");

                // send header over to the client
                outputSocket.println(header);

                // starting byte stream for the file to be sent over to the client
                byte[] buffer = new byte[1000];
                while (targetFile.available() > 0)
                    // sending
                    outputSocket.write(buffer, 0, targetFile.read(buffer));

                // end of transmission - then close socket
                outputSocket.write(("\r\n\r\n").getBytes());
                outputSocket.close();

            } catch (IOException exception) {
                exception.printStackTrace(); }

            // if the file isn't found - send the 404 header and simple text to the client
        } catch (FileNotFoundException exception) {
            // print to console
            System.out.println(header);
            System.out.println("404 File not found: " + filename + "\n");

            // log response
            Logger.ServerLogger(header);
            Logger.ServerLogger("404 File not found: " + filename);

            // send 404 error to client - then close socket
            outputSocket.println(header);
            outputSocket.println("404 Not Found " + filename + " not found on server.");
            outputSocket.write(("\r\n\r\n").getBytes());
            outputSocket.close();
        }
    }

    static void ReplyToRequestDirectory(PrintStream outputSocket, String filename, String header) throws IOException {
        // initial HTML string
        // additional HTML code is added recursively added below
        String headerHTML = "<pre><h1> Index of " + filename + "</h1>";

        // if the directory is not root directory - figure out the parent directory to one requested
        if (!filename.equals("/")){
            String[] splitRequest = filename.split("/");
            String[] parentDirList = Arrays.copyOfRange(splitRequest, 0, (splitRequest.length - 1));
            String parentDir = String.join("/", parentDirList) + "/";

            headerHTML += "<a href=\"" + parentDir +"\">Parent Directory</a> <br>";
        }
        // directory requested is root directory
        else headerHTML += "<a href=\"" + filename +"\">Parent Directory</a> <br>";

        // look through directory and add HTML code to string above
        // HTML code is different depending on if a directory or file is found
        try {
            File directory = new File ("." + filename);
            System.out.println("Directory path: " + directory.getCanonicalPath());

            // borrowed from code linked on assignment
            // Get all the files and directory under your directory
            File[] filesDirsList = directory.listFiles();

            for ( File file : filesDirsList ) {
                if ( file.isDirectory ()){
                    // directory found
                    String dirName = file.getName() + "/";
                    headerHTML += "<a href=\"" + filename + dirName +"\">" + dirName + "</a> <br>";
                }
                else if ( file.isFile()){
                    // found found
                    String fileName = file.getName();
                    headerHTML += "<a href=\"" + fileName +"\">" + fileName + "</a> <br>";
                }
            }
            // print to console
            System.out.println(header);
            System.out.println("HTML sent to client: " + headerHTML + "\n");

            // log response
            Logger.ServerLogger(header);
            Logger.ServerLogger("HTML sent to client: " + headerHTML);

            // send header and HTML code response to client - then close socket
            outputSocket.println(header);
            outputSocket.write(headerHTML.getBytes());
            outputSocket.write(("\r\n\r\n").getBytes());
            outputSocket.close();

            // if the directory isn't found - send the 404 header, simple text to the client, and logged
        } catch (NullPointerException exception) {
            // print to console
            System.out.println("404 File not found: " + filename + "\n");

            // log response
            Logger.ServerLogger(header);
            Logger.ServerLogger(headerHTML);

            // send 404 error to client - then close socket
            outputSocket.println(header);
            outputSocket.println("404 Not Found " + filename + " not found on server.");
            outputSocket.write(("\r\n\r\n").getBytes());
            outputSocket.close();

        }
    }

    static void ReplyToRequestAddNums(PrintStream outputSocket, String filename, String HTTPversion) throws IOException {
        // parsing through cgi string to get variables
        String[] splitRequest = filename.split("\\?");
        String varsString = splitRequest[1];
        String[] splitVars = varsString.split("&");
        String person = (splitVars[0].replace("person=", "")).replace("+", " ");
        String num1 = splitVars[1].replace("num1=", "");
        String num2 = splitVars[2].replace("num2=", "");

        // create return string for client
        String returnString = AddNumsReturnString(person, num1, num2);

        // create HTTP header to sent to client
        String header = CreateHeaderAddNums(returnString, HTTPversion);

        try {
            // print to console
            System.out.println(header);
            System.out.println("AddNums return statement: " + returnString);

            // log response
            Logger.ServerLogger(header);
            Logger.ServerLogger("AddNums return statement: " + returnString);

            // send header and sum to client - then close socket
            outputSocket.println(header);
            outputSocket.write(returnString.getBytes());
            outputSocket.write(("\r\n\r\n").getBytes());
            outputSocket.close();

            // if bad request, returnString will be null
            // caught by try/catch and reponse 400 Bad Request sent to client and logged
        } catch (NullPointerException exception) {
            // print to console
            System.out.println(header);

            // log response
            Logger.ServerLogger(header);

            // send 400 error to client - then close socket
            outputSocket.println(header);
            outputSocket.println("400 Bad Request");
            outputSocket.write(("\r\n\r\n").getBytes());
            outputSocket.close();
        }
    }

    static String CreateHeader(String filename, String HTTPversion, String contentType) {
        final String response200 = "200 OK";
        final String response404 = "404 Not Found";

        // prepare header response
        String firstLine = HTTPversion + " ";
        String contentLengthLine = "Content-Length: " + GetContentLength(filename);
        String contentTypeLine = "Content-Type: " + contentType;

        // if file exists - return 200 OK response with Content-Length and Content-Type
        if (DoesFileExist(filename)){
            String header = firstLine + response200 + "\r\n" +
                    contentLengthLine + "\r\n" +
                    contentTypeLine + "\r\n";
            return header;
        }
        // if file doesn't exists - return 404 response
        else {
            String header = firstLine + response404 + "\r\n";
            return header;
        }
    }

    static String CreateHeaderAddNums(String returnString, String HTTPversion) {
        final String response200 = "200 OK";
        final String response400 = "400 Bad Request";

        // prepare header response
        String firstLine = HTTPversion + " ";
        //String contentLengthLine = "Content-Length: " + returnString.length();
        String contentTypeLine = "Content-Type: text/html" ;

        // if file exists - return 200 OK response with Content-Length and Content-Type
        if (returnString != null && !returnString.isEmpty()){

            String contentLengthLine = "Content-Length: " + returnString.length();

            String header = firstLine + response200 + "\r\n" +
                    contentLengthLine + "\r\n" +
                    contentTypeLine + "\r\n";
            return header;
        }
        // null if variables can't be added - return 400 Bad Request error
        else {
            String header = firstLine + response400 + "\r\n";
            return header;
        }
    }

    static String AddNumsReturnString (String name, String number1, String number2){
        try {
            int num1 = Integer.parseInt(number1);
            int num2 = Integer.parseInt(number2);

            String sum = Integer.toString(num1 + num2);
            String returnString = "Hello, " + name + " the sum of " + number1 + " and " + number2 + " is " + sum + ".";
            return returnString;
        } catch (NumberFormatException exception){
            System.out.println("Variable given not an integer.");
        }

        return null;
    }

    static String GetContentLength(String filename) throws IllegalArgumentException {
        String fullFilePath = pwd() + filename;
        File targetFile = new File(fullFilePath);

        // if filepath is a directory - return size of the directory
        if (fullFilePath.endsWith("/")) {
            return Long.toString(FileUtils.sizeOfDirectory(targetFile));
        }
        // if filepath is a file - return size of the file
        else {
            long fileLength = targetFile.length();
            String fileLengthString = Long.toString(fileLength);
            return fileLengthString;
        }
    }

    static String GetContentType (String file) {
        // if file requested is .html or directory - return text/html Content-Type
        if (file.endsWith(".html") || file.endsWith("/")) return "text/html";
            // if file requested is text file - return text/plain Content-Type
        else if (file.endsWith(".txt")) return "text/plain";

            // if neither - return text-plain Content-Type to send back potentially for 404 response
        else return "text/plain";
    }

    static Boolean DoesFileExist(String filename) {
        // returns if file or directory exists
        String fullFilePath = pwd() + filename;
        File targetFile = new File(fullFilePath);

        Boolean fileExists = targetFile.exists();

        return fileExists;
    }

    static String pwd() {
        // get filepath of the working directory where the server is running
        File directory = new File(".");
        String directoryPath = "";
        try {
            directoryPath = directory.getCanonicalPath();
        } catch (IOException excpetion){
            System.out.println("Unable to determine filepath.");
        }
        return directoryPath;
    }
}

class Logger {
    static void ServerLogger(String writeToFile) throws IOException {
        // opening file for server logging
        BufferedWriter writer = new BufferedWriter(new FileWriter("server-logs.txt", true));

        // logging server actions
        writer.newLine();
        writer.append(writeToFile);
        writer.newLine();
        writer.close();
    }
}

public class MyWebServer {
    public static void main(String[] args) throws IOException {
        int port = 2540;
        int queue_len = 10;
        Socket socket;

        // create socket listening on port#: 2540 + queue length of 6
        ServerSocket serverSocket = new ServerSocket(port, queue_len);
        System.out.println("Raquib Talukder's Webserver 1.8 starting up, listening on port 2540.\n" );
        Logger.ServerLogger("Raquib Talukder's Webserver 1.8 starting up, listening on port 2540.\n" );

        // accept incoming coming connections and then pass them along to the worker class
        while (true) {
            socket = serverSocket.accept();
            new Worker(socket).run();
        }
    }
}

