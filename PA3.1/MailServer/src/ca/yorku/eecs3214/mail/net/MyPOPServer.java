package ca.yorku.eecs3214.mail.net;

import ca.yorku.eecs3214.mail.mailbox.MailMessage;
import ca.yorku.eecs3214.mail.mailbox.Mailbox;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class MyPOPServer extends Thread {

    private final Socket socket;
    private final BufferedReader socketIn;
    private final PrintWriter socketOut;

    private String username = null;
    private Mailbox mailbox = null;
    private boolean authenticated = false;

    /**
     * Initializes an object responsible for a connection to an individual client.
     *
     * @param socket The socket associated to the accepted connection.
     * @throws IOException If there is an error attempting to retrieve the socket's
     *                     information.
     */
    public MyPOPServer(Socket socket) throws IOException {
        this.socket = socket;
        this.socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.socketOut = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    /**
     * Handles the communication with an individual client. Must send the
     * initial welcome message, and then repeatedly read requests, process the
     * individual operation, and return a response, according to the POP3
     * protocol. Empty request lines should be ignored. Only returns if the
     * connection is terminated or if the QUIT command is issued. Must close the
     * socket connection before returning.
     */
    @Override
    public void run() {
        try (this.socket) {
            socketOut.println("+OK POP3 server ready");
            
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
            case "USER":
                handleUser(arg);
                break;
            case "PASS":
                handlePass(arg);
                break;
            case "STAT":
                handleStat();
                break;
            case "LIST":
                handleList(arg);
                break;
            case "RETR":
                handleRetr(arg);
                break;
            case "DELE":
                handleDele(arg);
                break;
            case "RSET":
                handleRset();
                break;
            case "NOOP":
                handleNoop();
                break;
            case "QUIT":
                handleQuit();
                break;
            default:
                socketOut.println("-ERR Unknown command");
                break;
        }
    }
    
    private void handleUser(String arg) {
        if (arg.isEmpty()) {
            socketOut.println("-ERR Missing username");
            return;
        }
        username = arg;
        socketOut.println("+OK User accepted");
    }
    
    private void handlePass(String arg) {
        if (username == null) {
            socketOut.println("-ERR No username given");
            return;
        }
        
        if (arg.isEmpty()) {
            socketOut.println("-ERR Missing password");
            return;
        }
        
        try {
            mailbox = new Mailbox(username);
            mailbox.loadMessages(arg);
            authenticated = true;
            socketOut.println("+OK Mailbox open, " + mailbox.size(true) + " messages");
        } catch (Mailbox.InvalidUserException e) {
            socketOut.println("-ERR Invalid user or password");
            username = null;
        } catch (Mailbox.MailboxNotAuthenticatedException e) {
            socketOut.println("-ERR Invalid user or password");
            username = null;
        }
    }
    
    private void handleStat() {
        if (!authenticated) {
            socketOut.println("-ERR Not authenticated");
            return;
        }
        
        int count = mailbox.size(false);
        long size = mailbox.getTotalUndeletedFileSize(false);
        socketOut.println("+OK " + count + " " + size);
    }
    
    private void handleList(String arg) {
        if (!authenticated) {
            socketOut.println("-ERR Not authenticated");
            return;
        }
        
        if (arg.isEmpty()) {
            int count = mailbox.size(false);
            long size = mailbox.getTotalUndeletedFileSize(false);
            socketOut.println("+OK " + count + " messages (" + size + " octets)");
            
            int index = 1;
            for (MailMessage msg : mailbox) {
                if (!msg.isDeleted()) {
                    socketOut.println(index + " " + msg.getFileSize());
                }
                index++;
            }
            socketOut.println(".");
        } else {
            try {
                int msgNum = Integer.parseInt(arg);
                MailMessage msg = mailbox.getMailMessage(msgNum);
                
                if (msg.isDeleted()) {
                    socketOut.println("-ERR Message " + msgNum + " already deleted");
                } else {
                    socketOut.println("+OK " + msgNum + " " + msg.getFileSize());
                }
            } catch (NumberFormatException e) {
                socketOut.println("-ERR Invalid message number");
            } catch (IndexOutOfBoundsException e) {
                socketOut.println("-ERR No such message");
            }
        }
    }
    
    private void handleRetr(String arg) {
        if (!authenticated) {
            socketOut.println("-ERR Not authenticated");
            return;
        }
        
        if (arg.isEmpty()) {
            socketOut.println("-ERR Missing message number");
            return;
        }
        
        try {
            int msgNum = Integer.parseInt(arg);
            MailMessage msg = mailbox.getMailMessage(msgNum);
            
            if (msg.isDeleted()) {
                socketOut.println("-ERR Message " + msgNum + " already deleted");
                return;
            }
            
            socketOut.println("+OK " + msg.getFileSize() + " octets");
            
            try (BufferedReader reader = new BufferedReader(new FileReader(msg.getFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    socketOut.println(line);
                }
            }
            socketOut.println(".");
            
        } catch (NumberFormatException e) {
            socketOut.println("-ERR Invalid message number");
        } catch (IndexOutOfBoundsException e) {
            socketOut.println("-ERR No such message");
        } catch (IOException e) {
            socketOut.println("-ERR Error reading message");
        }
    }
    
    private void handleDele(String arg) {
        if (!authenticated) {
            socketOut.println("-ERR Not authenticated");
            return;
        }
        
        if (arg.isEmpty()) {
            socketOut.println("-ERR Missing message number");
            return;
        }
        
        try {
            int msgNum = Integer.parseInt(arg);
            MailMessage msg = mailbox.getMailMessage(msgNum);
            
            if (msg.isDeleted()) {
                socketOut.println("-ERR Message " + msgNum + " already deleted");
            } else {
                msg.tagForDeletion();
                socketOut.println("+OK Message " + msgNum + " deleted");
            }
        } catch (NumberFormatException e) {
            socketOut.println("-ERR Invalid message number");
        } catch (IndexOutOfBoundsException e) {
            socketOut.println("-ERR No such message");
        }
    }
    
    private void handleRset() {
        if (!authenticated) {
            socketOut.println("-ERR Not authenticated");
            return;
        }
        
        for (MailMessage msg : mailbox) {
            msg.undelete();
        }
        
        int count = mailbox.size(false);
        socketOut.println("+OK " + count + " messages");
    }
    
    private void handleNoop() {
        if (!authenticated) {
            socketOut.println("-ERR Not authenticated");
            return;
        }
        socketOut.println("+OK");
    }
    
    private void handleQuit() throws IOException {
        if (authenticated) {
            mailbox.deleteMessagesTaggedForDeletion();
            socketOut.println("+OK POP3 server signing off");
        } else {
            socketOut.println("+OK POP3 server signing off");
        }
        socket.close();
    }

    /**
     * Main process for the POP3 server. Handles the argument parsing and
     * creates a listening server socket. Repeatedly accepts new connections
     * from individual clients, creating a new server instance that handles
     * communication with that client in a separate thread.
     *
     * @param args The command-line arguments.
     * @throws IOException In case of an exception creating the server socket or
     *                     accepting new connections.
     */
    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            throw new RuntimeException(
                    "This application must be executed with exactly one argument, the listening port.");
        }

        try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]))) {
            serverSocket.setReuseAddress(true);

            System.out.println("Waiting for connections on port " + serverSocket.getLocalPort() + "...");
            // noinspection InfiniteLoopStatement
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Accepted a connection from " + socket.getRemoteSocketAddress());
                try {
                    MyPOPServer handler = new MyPOPServer(socket);
                    handler.start();
                } catch (IOException e) {
                    System.err.println("Error setting up an individual client's handler.");
                    e.printStackTrace();
                }
            }
        }
    }
}