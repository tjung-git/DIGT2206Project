package ca.yorku.eecs3214.mail.net;

import ca.yorku.eecs3214.mail.mailbox.MailWriter;
import ca.yorku.eecs3214.mail.mailbox.Mailbox;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class MySMTPServer extends Thread {

    private final Socket socket;
    private final BufferedReader socketIn;
    private final PrintWriter socketOut;

    private boolean greeted = false;
    private String sender = null;
    private List<Mailbox> recipientList = null;

    /**
     * Initializes an object responsible for a connection to an individual client.
     *
     * @param socket The socket associated to the accepted connection.
     * @throws IOException If there is an error attempting to retrieve the socket's information.
     */
    public MySMTPServer(Socket socket) throws IOException {
        this.socket = socket;
        this.socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.socketOut = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    /**
     * Handles the communication with an individual client. Must send the initial welcome message, and then repeatedly
     * read requests, process the individual operation, and return a response, according to the SMTP protocol. Empty
     * request lines should be ignored. Only returns if the connection is terminated or if the QUIT command is issued.
     * Must close the socket connection before returning.
     */
    @Override
    public void run() {
        try (this.socket) {
            socketOut.println("220 " + getHostName() + " SMTP Service Ready");
            
            String line;
            while ((line = socketIn.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                processCommand(line.trim());
            }
            
        } catch (IOException e) {
            System.err.println("Error in client's connection handling.");
            e.printStackTrace();
        }
    }
    
    private void processCommand(String line) throws IOException {
        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0].toUpperCase();
        String arg = parts.length > 1 ? parts[1] : "";
        
        switch (cmd) {
            case "HELO":
                handleHelo(arg);
                break;
            case "EHLO":
                handleEhlo(arg);
                break;
            case "MAIL":
                handleMail(arg);
                break;
            case "RCPT":
                handleRcpt(arg);
                break;
            case "DATA":
                handleData();
                break;
            case "RSET":
                handleRset();
                break;
            case "VRFY":
                handleVrfy(arg);
                break;
            case "NOOP":
                handleNoop();
                break;
            case "QUIT":
                handleQuit();
                break;
            case "EXPN":
            case "HELP":
                socketOut.println("502 Command not implemented");
                break;
            default:
                socketOut.println("500 Syntax error, command unrecognized");
                break;
        }
    }
    
    private void handleHelo(String arg) {
        if (arg.isEmpty()) {
            socketOut.println("501 Syntax error in parameters or arguments");
            return;
        }
        greeted = true;
        socketOut.println("250 " + getHostName());
    }
    
    private void handleEhlo(String arg) {
        if (arg.isEmpty()) {
            socketOut.println("501 Syntax error in parameters or arguments");
            return;
        }
        greeted = true;
        socketOut.println("250 " + getHostName());
    }
    
    private void handleMail(String arg) {
        if (!greeted) {
            socketOut.println("503 Bad sequence of commands");
            return;
        }
        
        if (sender != null) {
            socketOut.println("503 Bad sequence of commands");
            return;
        }
        
        if (arg.isEmpty()) {
            socketOut.println("501 Syntax error in parameters or arguments");
            return;
        }
        
        String upper = arg.toUpperCase();
        if (!upper.startsWith("FROM:") && !upper.startsWith("FROM :")) {
            socketOut.println("501 Syntax error in parameters or arguments");
            return;
        }
        
        int colonPos = arg.indexOf(':');
        if (colonPos == -1) {
            socketOut.println("501 Syntax error in parameters or arguments");
            return;
        }
        
        String addr = arg.substring(colonPos + 1).trim();
        if (addr.isEmpty()) {
            socketOut.println("501 Syntax error in parameters or arguments");
            return;
        }
        
        if (addr.startsWith("<") && addr.endsWith(">")) {
            addr = addr.substring(1, addr.length() - 1);
        }
        
        sender = addr;
        recipientList = new ArrayList<>();
        socketOut.println("250 OK");
    }
    
    private void handleRcpt(String arg) {
        if (!greeted) {
            socketOut.println("503 Bad sequence of commands");
            return;
        }
        
        if (sender == null) {
            socketOut.println("503 Bad sequence of commands");
            return;
        }
        
        if (arg.isEmpty()) {
            socketOut.println("501 Syntax error in parameters or arguments");
            return;
        }
        
        String upper = arg.toUpperCase();
        if (!upper.startsWith("TO:") && !upper.startsWith("TO :")) {
            socketOut.println("501 Syntax error in parameters or arguments");
            return;
        }
        
        int colonPos = arg.indexOf(':');
        if (colonPos == -1) {
            socketOut.println("501 Syntax error in parameters or arguments");
            return;
        }
        
        String addr = arg.substring(colonPos + 1).trim();
        if (addr.isEmpty()) {
            socketOut.println("501 Syntax error in parameters or arguments");
            return;
        }
        
        if (addr.startsWith("<") && addr.endsWith(">")) {
            addr = addr.substring(1, addr.length() - 1);
        }
        
        if (!Mailbox.isValidUser(addr)) {
            socketOut.println("550 Requested action not taken: mailbox unavailable");
            return;
        }
        
        try {
            recipientList.add(new Mailbox(addr));
            socketOut.println("250 OK");
        } catch (Mailbox.InvalidUserException e) {
            socketOut.println("550 Requested action not taken: mailbox unavailable");
        }
    }
    
    private void handleData() throws IOException {
        if (!greeted) {
            socketOut.println("503 Bad sequence of commands");
            return;
        }
        
        if (sender == null) {
            socketOut.println("503 Bad sequence of commands");
            return;
        }
        
        if (recipientList == null || recipientList.isEmpty()) {
            socketOut.println("503 Bad sequence of commands");
            return;
        }
        
        socketOut.println("354 Start mail input; end with <CRLF>.<CRLF>");
        
        try (MailWriter writer = new MailWriter(recipientList)) {
            String line;
            while ((line = socketIn.readLine()) != null) {
                if (line.equals(".")) {
                    break;
                }
                if (line.startsWith("..")) {
                    line = line.substring(1);
                }
                writer.write(line + "\r\n");
            }
        }
        
        socketOut.println("250 OK");
        sender = null;
        recipientList = null;
    }
    
    private void handleRset() {
        sender = null;
        recipientList = null;
        socketOut.println("250 OK");
    }
    
    private void handleVrfy(String arg) {
        if (arg.isEmpty()) {
            socketOut.println("501 Syntax error in parameters or arguments");
            return;
        }
        
        String addr = arg.trim();
        if (addr.startsWith("<") && addr.endsWith(">")) {
            addr = addr.substring(1, addr.length() - 1);
        }
        
        if (Mailbox.isValidUser(addr)) {
            socketOut.println("250 " + addr);
        } else {
            socketOut.println("550 Requested action not taken: mailbox unavailable");
        }
    }
    
    private void handleNoop() {
        socketOut.println("250 OK");
    }
    
    private void handleQuit() throws IOException {
        socketOut.println("221 " + getHostName() + " Service closing transmission channel");
        socket.close();
    }

    /**
     * Retrieves the name of the current host. Used in the response of commands like HELO and EHLO.
     * @return A string corresponding to the name of the current host.
     */
    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            try (BufferedReader reader = Runtime.getRuntime().exec(new String[] {"hostname"}).inputReader()) {
                return reader.readLine();
            } catch (IOException ex) {
                return "unknown_host";
            }
        }
    }

    /**
     * Main process for the SMTP server. Handles the argument parsing and creates a listening server socket. Repeatedly
     * accepts new connections from individual clients, creating a new server instance that handles communication with
     * that client in a separate thread.
     *
     * @param args The command-line arguments.
     * @throws IOException In case of an exception creating the server socket or accepting new connections.
     */
    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            throw new RuntimeException("This application must be executed with exactly one argument, the listening port.");
        }

        try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]))) {
            serverSocket.setReuseAddress(true);
            System.out.println("Waiting for connections on port " + serverSocket.getLocalPort() + "...");
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Accepted a connection from " + socket.getRemoteSocketAddress());
                try {
                    MySMTPServer handler = new MySMTPServer(socket);
                    handler.start();
                } catch (IOException e) {
                    System.err.println("Error setting up an individual client's handler.");
                    e.printStackTrace();
                }
            }
        }
    }
}
