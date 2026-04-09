/*
 * Author: Jonatan Schroeder
 * Updated: October 2022
 *
 * This code may not be used without written consent of the authors.
 */

package ca.yorku.rtsp.client.net;

import ca.yorku.rtsp.client.exception.RTSPException;
import ca.yorku.rtsp.client.model.Frame;
import ca.yorku.rtsp.client.model.Session;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * This class represents a connection with an RTSP server.
 */
public class RTSPConnection {

    private static final int BUFFER_LENGTH = 0x10000;
    private final Session session;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private DatagramSocket rtpSocket;
    private int cseq;
    private String sessionId;
    private String videoFile;
    private RTPReceivingThread recvThread;

    /**
     * Establishes a new connection with an RTSP server. No message is
     * sent at this point, and no stream is set up.
     *
     * @param session The Session object to be used for connectivity with the UI.
     * @param server  The hostname or IP address of the server.
     * @param port    The TCP port number where the server is listening to.
     * @throws RTSPException If the connection couldn't be accepted,
     *                       such as if the host name or port number
     *                       are invalid or there is no connectivity.
     */
    public RTSPConnection(Session session, String server, int port) throws RTSPException {

        this.session = session;
        this.cseq = 1;

        try {
            socket = new Socket(server, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        } catch (IOException e) {
            throw new RTSPException("Failed to connect to server", e);
        }
    }

    /**
     * Sets up a new video stream with the server. This method is
     * responsible for sending the SETUP request, receiving the
     * response and retrieving the session identification to be used
     * in future messages. It is also responsible for establishing an
     * RTP datagram socket to be used for data transmission by the
     * server. The datagram socket should be created with a random
     * available UDP port number, and the port number used in that
     * connection has to be sent to the RTSP server for setup. This
     * datagram socket should also be defined to timeout after 2
     * seconds if no packet is received.
     *
     * @param videoName The name of the video to be setup.
     * @throws RTSPException If there was an error sending or
     *                       receiving the RTSP data, or if the RTP
     *                       socket could not be created, or if the
     *                       server did not return a successful
     *                       response.
     */
    public synchronized void setup(String videoName) throws RTSPException {

        try {
            rtpSocket = new DatagramSocket();
            rtpSocket.setSoTimeout(2000);
            int port = rtpSocket.getLocalPort();

            this.videoFile = videoName;

            out.println("SETUP " + videoName + " RTSP/1.0");
            out.println("CSeq: " + cseq);
            out.println("Transport: RTP/UDP; client_port= " + port);
            out.println();
            out.flush();

            RTSPResponse resp = readRTSPResponse();
            cseq++;

            if (resp.getResponseCode() != 200) {
                throw new RTSPException("SETUP failed: " + resp.getResponseMessage());
            }

            sessionId = resp.getHeaderValue("Session");

        } catch (IOException e) {
            throw new RTSPException("Error during SETUP", e);
        }
    }

    /**
     * Starts (or resumes) the playback of a set up stream. This
     * method is responsible for sending the request, receiving the
     * response and, in case of a successful response, starting a
     * separate thread responsible for receiving RTP packets with
     * frames (achieved by calling start() on a new object of type
     * RTPReceivingThread).
     *
     * @throws RTSPException If there was an error sending or
     *                       receiving the RTSP data, or if the server
     *                       did not return a successful response.
     */
    public synchronized void play() throws RTSPException {

        try {
            out.println("PLAY " + videoFile + " RTSP/1.0");
            out.println("CSeq: " + cseq);
            out.println("Session: " + sessionId);
            out.println();
            out.flush();

            RTSPResponse resp = readRTSPResponse();
            cseq++;

            if (resp.getResponseCode() != 200) {
                throw new RTSPException("PLAY failed: " + resp.getResponseMessage());
            }

            if (recvThread == null || !recvThread.isAlive()) {
                recvThread = new RTPReceivingThread();
                recvThread.start();
            }

        } catch (IOException e) {
            throw new RTSPException("Error during PLAY", e);
        }
    }

    private class RTPReceivingThread extends Thread {
        /**
         * Continuously receives RTP packets until the thread is
         * cancelled or until an RTP packet is received with a
         * zero-length payload. Each packet received from the datagram
         * socket is assumed to be no larger than BUFFER_LENGTH
         * bytes. This data is then parsed into a Frame object (using
         * the parseRTPPacket() method) and the method
         * session.processReceivedFrame() is called with the resulting
         * packet. The receiving process should be configured to
         * timeout if no RTP packet is received after two seconds. If
         * a frame with zero-length payload is received, indicating
         * the end of the stream, the method session.videoEnded() is
         * called, and the thread is terminated.
         */
        @Override
        public void run() {

            byte[] buf = new byte[BUFFER_LENGTH];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);

            try {
                while (!Thread.interrupted()) {
                    try {
                        rtpSocket.receive(pkt);
                        Frame frame = parseRTPPacket(pkt);

                        if (frame.getPayloadLength() == 0) {
                            session.videoEnded(frame.getSequenceNumber());
                            break;
                        }

                        session.processReceivedFrame(frame);

                    } catch (SocketTimeoutException e) {
                    }
                }
            } catch (IOException e) {
            }
        }

    }

    /**
     * Pauses the playback of a set up stream. This method is
     * responsible for sending the request, receiving the response
     * and, in case of a successful response, stopping the thread
     * responsible for receiving RTP packets with frames.
     *
     * @throws RTSPException If there was an error sending or
     *                       receiving the RTSP data, or if the server
     *                       did not return a successful response.
     */
    public synchronized void pause() throws RTSPException {

        try {
            out.println("PAUSE " + videoFile + " RTSP/1.0");
            out.println("CSeq: " + cseq);
            out.println("Session: " + sessionId);
            out.println();
            out.flush();

            RTSPResponse resp = readRTSPResponse();
            cseq++;

            if (resp.getResponseCode() != 200) {
                throw new RTSPException("PAUSE failed: " + resp.getResponseMessage());
            }

            if (recvThread != null && recvThread.isAlive()) {
                recvThread.interrupt();
            }

        } catch (IOException e) {
            throw new RTSPException("Error during PAUSE", e);
        }
    }

    /**
     * Terminates a set up stream. This method is responsible for
     * sending the request, receiving the response and, in case of a
     * successful response, closing the RTP socket. This method does
     * not close the RTSP connection, and a further SETUP in the same
     * connection should be accepted. Also, this method can be called
     * both for a paused and for a playing stream, so the thread
     * responsible for receiving RTP packets will also be cancelled,
     * if active.
     *
     * @throws RTSPException If there was an error sending or
     *                       receiving the RTSP data, or if the server
     *                       did not return a successful response.
     */
    public synchronized void teardown() throws RTSPException {

        try {
            out.println("TEARDOWN " + videoFile + " RTSP/1.0");
            out.println("CSeq: " + cseq);
            out.println("Session: " + sessionId);
            out.println();
            out.flush();

            RTSPResponse resp = readRTSPResponse();
            cseq++;

            if (resp.getResponseCode() != 200) {
                throw new RTSPException("TEARDOWN failed: " + resp.getResponseMessage());
            }

            if (recvThread != null && recvThread.isAlive()) {
                recvThread.interrupt();
            }

            if (rtpSocket != null && !rtpSocket.isClosed()) {
                rtpSocket.close();
            }

        } catch (IOException e) {
            throw new RTSPException("Error during TEARDOWN", e);
        }
    }

    /**
     * Closes the connection with the RTSP server. This method should
     * also close any open resource associated to this connection,
     * such as the RTP connection and thread, if it is still open.
     */
    public synchronized void closeConnection() {

        try {
            if (recvThread != null && recvThread.isAlive()) {
                recvThread.interrupt();
            }

            if (rtpSocket != null && !rtpSocket.isClosed()) {
                rtpSocket.close();
            }

            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();

        } catch (IOException e) {
        }
    }

    /**
     * Parses an RTP packet into a Frame object. This method is
     * intended to be a helper method in this class, but it is made
     * public to facilitate testing.
     *
     * @param packet the byte representation of a frame, corresponding to the RTP
     *               packet.
     * @return A Frame object.
     */
    public static Frame parseRTPPacket(DatagramPacket packet) {

        byte[] data = packet.getData();
        int len = packet.getLength();

        byte b0 = data[0];
        byte b1 = data[1];

        byte payloadType = (byte) (b1 & 0x7F);
        boolean marker = (b1 & 0x80) != 0;

        ByteBuffer bb = ByteBuffer.wrap(data, 2, 2);
        bb.order(ByteOrder.BIG_ENDIAN);
        short seqNum = bb.getShort();

        bb = ByteBuffer.wrap(data, 4, 4);
        bb.order(ByteOrder.BIG_ENDIAN);
        int timestamp = bb.getInt();

        int payloadLen = len - 12;

        return new Frame(payloadType, marker, seqNum, timestamp, data, 12, payloadLen);
    }


    /**
     * Reads and parses an RTSP response from the socket's input. This
     * method is intended to be a helper method in this class, but it
     * is made public to facilitate testing.
     *
     * @return An RTSPResponse object if the response was read
     *         completely, or null if the end of the stream was reached.
     * @throws IOException   In case of an I/O error, such as loss of connectivity.
     * @throws RTSPException If the response doesn't match the expected format.
     */
    public RTSPResponse readRTSPResponse() throws IOException, RTSPException {

        String line = in.readLine();
        if (line == null) {
            return null;
        }

        String[] parts = line.split(" ", 3);
        if (parts.length < 3) {
            throw new RTSPException("Invalid response line");
        }

        String version = parts[0];
        int code = Integer.parseInt(parts[1]);
        String msg = parts[2];

        RTSPResponse resp = new RTSPResponse(version, code, msg);

        while ((line = in.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                resp.addHeaderValue(name, value);
            }
        }

        return resp;
    }

}
