package ca.yorku.eecs3214.dict.net;

import ca.yorku.eecs3214.dict.model.Database;
import ca.yorku.eecs3214.dict.model.Definition;
import ca.yorku.eecs3214.dict.model.MatchingStrategy;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    /**
     * Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages. This constructor does not send any request for additional data.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the welcome
     *                                 messages are not successful.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            
            Status status = Status.readStatus(in);
            if (status.getStatusCode() != 220) {
                throw new DictConnectionException("Invalid welcome message: " + status.getStatusCode());
            }
        } catch (IOException e) {
            throw new DictConnectionException("Failed to connect to server", e);
        }
    }

    /**
     * Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the welcome
     *                                 messages are not successful.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /**
     * Sends the final QUIT message, waits for its reply, and closes the connection with the server. This function
     * ignores any exception that may happen while sending the message, receiving its reply, or closing the connection.
     */
    public synchronized void close() {
        try {
            if (out != null) {
                out.println("QUIT");
                out.flush();
            }
            
            if (in != null) {
                Status.readStatus(in);
            }
        } catch (Exception e) {
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
            }
        }
    }

    /**
     * Requests and retrieves a map of database name to an equivalent database object for all valid databases used in
     * the server.
     *
     * @return A map linking database names to Database objects for all databases supported by the server, or an empty
     * map if no databases are available.
     * @throws DictConnectionException If the connection is interrupted or the messages don't match their expected
     *                                 value.
     */
    public synchronized Map<String, Database> getDatabaseList() throws DictConnectionException {
        Map<String, Database> databaseMap = new HashMap<>();

        try {
            out.println("SHOW DB");
            out.flush();
            
            Status status = Status.readStatus(in);
            
            if (status.getStatusCode() == 554) {
                return databaseMap;
            }
            
            if (status.getStatusCode() != 110) {
                throw new DictConnectionException("Unexpected status code: " + status.getStatusCode());
            }
            
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals(".")) {
                    break;
                }
                
                List<String> tokens = DictStringParser.splitAtoms(line);
                if (tokens.size() >= 2) {
                    String name = tokens.get(0);
                    String desc = tokens.get(1);
                    databaseMap.put(name, new Database(name, desc));
                }
            }
            
            Status finalStatus = Status.readStatus(in);
            if (finalStatus.getStatusCode() != 250) {
                throw new DictConnectionException("Expected status 250");
            }
            
        } catch (IOException e) {
            throw new DictConnectionException("Error reading database list", e);
        }

        return databaseMap;
    }

    /**
     * Requests and retrieves a list of all valid matching strategies supported by the server. Matching strategies are
     * used in getMatchList() to identify how to suggest words that match a specific pattern. For example, the "prefix"
     * strategy suggests words that start with a specific pattern.
     *
     * @return A set of MatchingStrategy objects supported by the server, or an empty set if no strategies are
     * supported.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected
     *                                 value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        try {
            out.println("SHOW STRAT");
            out.flush();
            
            Status status = Status.readStatus(in);
            
            if (status.getStatusCode() == 555) {
                return set;
            }
            
            if (status.getStatusCode() != 111) {
                throw new DictConnectionException("Unexpected status code: " + status.getStatusCode());
            }
            
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals(".")) {
                    break;
                }
                
                List<String> tokens = DictStringParser.splitAtoms(line);
                if (tokens.size() >= 2) {
                    String name = tokens.get(0);
                    String desc = tokens.get(1);
                    set.add(new MatchingStrategy(name, desc));
                }
            }
            
            Status finalStatus = Status.readStatus(in);
            if (finalStatus.getStatusCode() != 250) {
                throw new DictConnectionException("Expected status 250");
            }
            
        } catch (IOException e) {
            throw new DictConnectionException("Error reading strategy list", e);
        }

        return set;
    }

    /**
     * Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param pattern  The pattern to use to identify word matches.
     * @param strategy The strategy to be used to compare the list of matches.
     * @param database The database where matches are to be found. Special databases like Database.DATABASE_ANY or
     *                 Database.DATABASE_FIRST_MATCH are supported.
     * @return A set of word matches returned by the server based on the word pattern, or an empty set if no matches
     * were found.
     * @throws DictConnectionException If the connection was interrupted, the messages don't match their expected value,
     *                                 or the database or strategy are not supported by the server.
     */
    public synchronized Set<String> getMatchList(String pattern, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();

        try {
            String cmd = String.format("MATCH %s %s \"%s\"", 
                database.getName(), 
                strategy.getName(), 
                pattern);
            
            out.println(cmd);
            out.flush();
            
            Status status = Status.readStatus(in);
            
            if (status.getStatusCode() == 552) {
                return set;
            }
            
            if (status.getStatusCode() != 152) {
                throw new DictConnectionException("Unexpected status code: " + status.getStatusCode());
            }
            
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals(".")) {
                    break;
                }
                
                List<String> tokens = DictStringParser.splitAtoms(line);
                if (tokens.size() >= 2) {
                    String word = tokens.get(1);
                    set.add(word);
                }
            }
            
            Status finalStatus = Status.readStatus(in);
            if (finalStatus.getStatusCode() != 250) {
                throw new DictConnectionException("Expected status 250");
            }
            
        } catch (IOException e) {
            throw new DictConnectionException("Error reading match list", e);
        }

        return set;
    }

    /**
     * Requests and retrieves all definitions for a specific word.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. Special databases like Database.DATABASE_ANY
     *                 or Database.DATABASE_FIRST_MATCH are supported.
     * @return A collection of Definition objects containing all definitions returned by the server, or an empty
     * collection if no definitions were available.
     * @throws DictConnectionException If the connection was interrupted, the messages don't match their expected value,
     *                                 or the database is not supported by the server.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();

        try {
            String cmd = String.format("DEFINE %s \"%s\"", database.getName(), word);
            
            out.println(cmd);
            out.flush();
            
            Status status = Status.readStatus(in);
            
            if (status.getStatusCode() == 552) {
                return set;
            }
            
            if (status.getStatusCode() == 550) {
                throw new DictConnectionException("Invalid database");
            }
            
            if (status.getStatusCode() != 150) {
                throw new DictConnectionException("Unexpected status code: " + status.getStatusCode());
            }
            
            while (true) {
                Status defStatus = Status.readStatus(in);
                
                if (defStatus.getStatusCode() == 151) {
                    List<String> tokens = DictStringParser.splitAtoms(defStatus.getDetails());
                    if (tokens.size() >= 2) {
                        String w = tokens.get(0);
                        String db = tokens.get(1);
                        
                        Definition def = new Definition(w, db);
                        
                        StringBuilder text = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) {
                            if (line.equals(".")) {
                                break;
                            }
                            if (text.length() > 0) {
                                text.append("\n");
                            }
                            text.append(line);
                        }
                        
                        def.setDefinition(text.toString());
                        set.add(def);
                    }
                } else if (defStatus.getStatusCode() == 250) {
                    break;
                } else {
                    throw new DictConnectionException("Unexpected status code: " + defStatus.getStatusCode());
                }
            }
            
        } catch (IOException e) {
            throw new DictConnectionException("Error reading definitions", e);
        }

        return set;
    }

}
