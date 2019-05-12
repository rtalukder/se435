package edu.depaul.se435;

/**
 * Class: SE<xxx> - <descrption>
 * Author: Raquib Talukder
 **/

import java.io.*;
import java.net.*;
import java.util.Arrays;


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

            // read line from input
            String request;
            while (true){
                request = input.readLine();

                // keep reading from socket until null is received
                if (request != null){
                    System.out.println(request);
                    AnalyzeRequest(request, output);
                    break;
                }
                System.out.flush();
            }
            socket.close();
        }
        catch (IOException ioexception) {
            System.out.println(ioexception);
        }
    }

    static void AnalyzeRequest(String request, PrintStream outputSocket) throws IOException {
        String[] splitRequest = request.split(" ");
        String filename = splitRequest[1];
        String HTTPversion = splitRequest[2];
        String contentType = GetContentType(filename);

        String header = CreateHeader(filename, HTTPversion, contentType);

        if (filename.endsWith("/.xyz") || filename.endsWith("/")) ReplyToRequestDirectory(outputSocket, filename, header);

        else ReplyToRequest(outputSocket, filename, header);
    }

    static void ReplyToRequest(PrintStream outputSocket, String filename, String header) {
        String fullFilePath = pwd() + filename;
        try {
            InputStream targetFile = new FileInputStream(fullFilePath);
            System.out.println("File found: " + filename);

            try {
                System.out.println(header);
                outputSocket.println(header + "\r\n\r\n");

                byte[] buffer = new byte[1000];
                while (targetFile.available() > 0)
                    outputSocket.write(buffer, 0, targetFile.read(buffer));

            } catch (IOException exception) {
                exception.printStackTrace(); }

        } catch (FileNotFoundException exception) {
            outputSocket.println(header + "\r\n\r\n");
            outputSocket.println("404 Not Found\r\n" + filename + " not found on server.\r\n");
            outputSocket.close();
            System.out.println("404 File not found: " + filename);
        }
    }

    static void ReplyToRequestDirectory(PrintStream outputSocket, String filename, String header) throws IOException {
        String headerHTML = "<pre><h1> Index of " + filename + "</h1>";

        if (!filename.equals("/")){
            String[] splitRequest = filename.split("/");
            String[] parentDirList = Arrays.copyOfRange(splitRequest, 0, (splitRequest.length - 1));
            String parentDir = String.join("/", parentDirList) + "/";

            headerHTML += "<a href=\"" + parentDir +"\">Parent Directory</a> <br>";
        }
        else headerHTML += "<a href=\"" + filename +"\">Parent Directory</a> <br>";

        try {
            File directory = new File ("." + filename);
            System.out.println("Directory path: " + directory.getCanonicalPath());

            // borrowed from code linked on assignment
            // Get all the files and directory under your directory
            File[] filesDirsList = directory.listFiles();

            for ( File file : filesDirsList ) {
                if ( file.isDirectory ()){
                    String dirName = file.getName() + "/";
                    headerHTML += "<a href=\"" + filename + dirName +"\">" + dirName + "</a> <br>";
                }
                else if ( file.isFile()){
                    String fileName = file.getName();
                    headerHTML += "<a href=\"" + fileName +"\">" + fileName + "</a> <br>";
                }
            }
            System.out.println(header);
            System.out.println(headerHTML);
            outputSocket.println(header);
            outputSocket.write(headerHTML.getBytes());
            outputSocket.write("\r\n\r\n".getBytes());

        } catch (NullPointerException exception) {
            outputSocket.println(header + "\r\n\r\n");
            outputSocket.println("404 Not Found\r\n" + filename + " not found on server.\r\n");
            outputSocket.close();
            System.out.println("404 File not found: " + filename);
        }
    }

    static String GetContentLength(String filename) {
        String fullFilePath = pwd() + filename;
        File targetFile = new File(fullFilePath);

        long fileLength = targetFile.length();
        String fileLengthString = Long.toString(fileLength);

        return fileLengthString;
    }

    static String GetContentType (String file) {
        if (file.endsWith(".html") || file.endsWith(".xyz") || file.endsWith("/")) return "text/html";
        else if (file.endsWith(".txt")) return "text/plain";
        else return "text/plain";
    }

    static String CreateHeader(String filename, String HTTPversion, String contentType){
        final String response200 = "200 OK";
        final String response404 = "404 Not Found";

        String firstLine = HTTPversion + " ";
        String contentLengthLine = "Content-Length: " + GetContentLength(filename);
        String contentTypeLine = "Content-Type: " + contentType;

        if (DoesFileExist(filename)){
            String header = firstLine + response200 + "\r\n" +
                                contentLengthLine + "\r\n" +
                                contentTypeLine + "\r\n";
            return header;
        }
        else {
            String header = firstLine + response404 + "\r\n";
            return header;
        }
    }

    static Boolean DoesFileExist(String filename) {
        String fullFilePath = pwd() + filename;
        File targetFile = new File(fullFilePath);

        Boolean fileExists = targetFile.exists();

        return fileExists;
    }

    static String pwd(){
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

public class MyWebserver {
    public static void main(String[] args) throws IOException {
        int port = 2540;
        int queue_len = 10;
        Socket socket;

        // create socket listening on port#: 2540 + queue length of 6
        ServerSocket serverSocket = new ServerSocket(port, queue_len);
        System.out.println("Raquib Talukder's Webserver 1.8 starting up, listening on port 2540.\n" );

        // accept incoming coming connections and then pass them along to the worker class
        while (true) {
            socket = serverSocket.accept();
            new Worker(socket).run();
        }

    }
}
