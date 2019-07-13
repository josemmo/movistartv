package tk.josemmo.movistartv.client;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class UdpClient {
    private static final String LOGTAG = "MCAST-SOCKET";
    private static final int INITIAL_EXTRA_ROUNDS = 5;

    private final String host;
    private final int port;
    private final HashMap<String, ArrayList<byte[]>> files;
    private int downloadedChunks = 0;
    private int totalNumOfChunks = 0;
    private int remainingExtraRounds = INITIAL_EXTRA_ROUNDS;

    /**
     * UdpClient constructor
     * @param target Target formatted in host:port
     */
    public UdpClient(String target) {
        String[] parts = target.split(":");
        this.host = parts[0];
        this.port = Integer.parseInt(parts[1]);
        this.files = new HashMap<>();
    }


    /**
     * Download data from socket
     * @return Downloaded files
     */
    public String[] download() throws Exception {
        startSocket();
        return getFilesAsStrings();
    }


    /**
     * Download raw data from socket
     * @return Downloaded files in raw data
     */
    public HashMap<String, byte[]> downloadRaw() throws Exception {
        startSocket();
        return getRawFiles();
    }


    /**
     * Start socket
     */
    private void startSocket() throws Exception {
        byte[] buffer = new byte[1500];
        DatagramPacket dgram = new DatagramPacket(buffer, buffer.length);
        MulticastSocket socket = new MulticastSocket(port);
        Log.d(LOGTAG, "Default buffer size is " + socket.getReceiveBufferSize());
        socket.setReceiveBufferSize(buffer.length * 300);
        Log.d(LOGTAG, "New buffer size is " + socket.getReceiveBufferSize());
        InetAddress mcastAddr = InetAddress.getByName(host);
        socket.joinGroup(mcastAddr);

        boolean finished = false;
        while (!finished) {
            socket.receive(dgram);
            finished = parseChunk(dgram.getData());
            dgram.setLength(buffer.length);
        }

        socket.leaveGroup(mcastAddr);
        socket.close();
    }


    /**
     * Parse chunk of data
     * @param  b Chunk bytes
     * @return   Finished downloading all chunks
     */
    private boolean parseChunk(byte[] b) {
        // TODO: clean comments
        //StringBuilder sb = new StringBuilder(b.length * 2);
        //for (byte elem : b) sb.append(String.format("%02x", elem));
        //Log.d(LOGTAG, sb.toString());

        //int end = b[0] & 0xff;
        //int size = (b[3] & 0xff) | ((b[2] & 0xff) << 8) | ((b[1] & 0x0f) << 16);
        int fileType = b[4] & 0xff;
        int fileId = ((b[5] & 0xff) << 8) | (b[6] & 0xff);
        int chunkIndex = (((b[8] & 0xff) << 8) | (b[9] & 0xff)) / 0x10;
        int numOfChunks = (((b[9] & 0x0f) << 8) | (b[10] & 0xff)) + 1;
        if (chunkIndex >= numOfChunks) {
            Log.e(LOGTAG, "Ignoring bad chunk (" + chunkIndex + " out of " + numOfChunks +")");
            return false;
        }
        //Log.d(LOGTAG, "Chunk #" + chunkIndex + " out of " + numOfChunks);

        // Extract payload
        int endOfPayload = b.length - 1;
        while (b[endOfPayload] == 0) {
            --endOfPayload;
        }
        if (endOfPayload <= 12) {
            Log.d(LOGTAG, "Ignoring bad chunk (corrupted data)");
            return false;
        }
        byte[] payload = Arrays.copyOfRange(b, 12, endOfPayload+1);
        //Log.d(LOGTAG, "Expected size of " + size + " bytes, received " + (endOfPayload-12) + " bytes");

        // Save to memory
        String key = fileType + "-" + fileId;
        ArrayList<byte[]> fileChunks;
        if (files.containsKey(key)) {
            fileChunks = files.get(key);
        } else {
            fileChunks = new ArrayList<>();
            for (int i=0; i<numOfChunks; ++i) fileChunks.add(new byte[] {});
            files.put(key, fileChunks);
            totalNumOfChunks += numOfChunks;
            remainingExtraRounds = INITIAL_EXTRA_ROUNDS;
        }
        if (fileChunks.get(chunkIndex).length == 0) {
            ++downloadedChunks;
            Log.d(LOGTAG, "Downloaded " + downloadedChunks +
                    " out of " + totalNumOfChunks + " chunks");
        }
        fileChunks.set(chunkIndex, payload);

        // Have we finished downloading all files?
        if (downloadedChunks == totalNumOfChunks) {
            if (--remainingExtraRounds > 0) return false;
            Log.d(LOGTAG, "Finished downloading chunks for all files");
            return true;
        }

        return false;
    }


    /**
     * Get downloaded files as Strings
     * @return Downloaded files as Strings
     */
    private String[] getFilesAsStrings() throws IOException {
        String[] res = new String[files.size()];

        int i = 0;
        for (ArrayList<byte[]> chunks : files.values()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (byte[] chunk : chunks) {
                output.write(chunk);
            }
            res[i] = output.toString();
            i++;
        }

        return res;
    }

    /**
     * Get downloaded raw files
     * @return Download files as raw bytes
     */
    private HashMap<String,byte[]> getRawFiles() throws IOException {
        HashMap<String,byte[]> res = new HashMap<>();

        for (Map.Entry<String,ArrayList<byte[]>> entry : files.entrySet()) {
            String filename = entry.getKey();
            ArrayList<byte[]> chunks = entry.getValue();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (byte[] chunk : chunks) {
                output.write(chunk);
            }
            res.put(filename, output.toByteArray());
        }

        return res;
    }

}
