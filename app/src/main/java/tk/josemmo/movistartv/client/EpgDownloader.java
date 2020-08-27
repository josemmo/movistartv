package tk.josemmo.movistartv.client;

import androidx.annotation.Nullable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;

public class EpgDownloader extends Thread {
    private static int instanceCount;

    private String LOGTAG;
    private final String entrypoint;
    private ArrayList<JSONObject> epgFiles;

    /**
     * EpgDownloader constructor
     * @param entrypoint EPG entrypoint address
     */
    public EpgDownloader(String entrypoint) {
        this.entrypoint = entrypoint;
        LOGTAG = "EpgWorker#" + instanceCount;
        instanceCount++;
    }


    @Override
    public void run() {
        try {
            // Download data from socket
            UdpClient socket = new UdpClient(entrypoint);
            TreeMap<String,byte[]> rawFiles = socket.downloadRaw();

            // Parse EPG files
            epgFiles = new ArrayList<>();
            for (byte[] data : rawFiles.values()) {
                JSONObject file = parseEpgFile(data);
                if (file != null) {
                    epgFiles.add(file);
                }
            }

            Log.d(LOGTAG, "Finished work!");
        } catch (Exception e) {
            Log.e(LOGTAG, "Exception raised inside EPG Downloader thread");
            e.printStackTrace();
        }
    }


    /**
     * Get EPG files
     * @return EPG files
     */
    public ArrayList<JSONObject> getEpgFiles() {
        return epgFiles;
    }


    /**
     * Parse EPG file
     * @param b Data
     */
    @Nullable
    private JSONObject parseEpgFile(byte[] b) throws Exception {
        // TODO: for testing
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte elem : b) sb.append(String.format("%02x", elem));

        byte[] stover = "default|STOVER".getBytes();

        // Parse header
        int serviceVersion = b[5] & 0xff;
        int urlLength = b[6] & 0xff;
        String serviceUrl = new String(Arrays.copyOfRange(b, 7, 7+urlLength));
        if (!serviceUrl.contains(".imagenio.es")) {
            Log.d(LOGTAG, "Invalid service URL for EPG file, ignoring data");
            return null;
        }
        int epgServiceName = Integer.parseInt(serviceUrl.split("\\.", 2)[0]);

        // Parse body
        JSONArray programs = new JSONArray();
        int i = urlLength + 7;
        while (i < b.length) {
            try {
                int pId = (b[i + 3] & 0xff) | ((b[i + 2] & 0xff) << 8) |
                        ((b[i + 1] & 0xff) << 16) | ((b[i] & 0xff) << 24);
                int start = (b[i + 7] & 0xff) | ((b[i + 6] & 0xff) << 8) |
                        ((b[i + 5] & 0xff) << 16) | ((b[i + 4] & 0xff) << 24);
                int duration = ((b[i + 8] & 0xff) << 8) | (b[i + 9] & 0xff);
                int genre = b[i + 20] & 0xff;
                int ageRating = b[i + 24] & 0xff;
                int titleLength = b[i + 31] & 0xff;
                String title = parseEpgString(Arrays.copyOfRange(b, i+32, i+32+titleLength));

                int offset = 32 + titleLength;
                int tvShowId = ((b[i + offset + 5] & 0xff) << 8) | (b[i + offset + 6] & 0xff);
                int episode = b[i + offset + 8] & 0xff;
                int year = ((b[i + offset + 9] & 0xff) << 8) | (b[i + offset + 10] & 0xff);
                int season = b[i + offset + 11] & 0xff;
                int tvShowNameLength = b[i + offset + 12] & 0xff;
                String tvShowName = parseEpgString(Arrays.copyOfRange(b, i+offset+13,
                        i+offset+13+tvShowNameLength));

                if (pId < 0 || start < 0 || duration <= 0 || year > 3000) {
                    throw new IllegalArgumentException("Corrupted bytes for program data");
                }

                JSONObject programData = new JSONObject();
                programData.put("pId", pId);
                programData.put("start", start);
                programData.put("end", start + duration);
                programData.put("genre", genre);
                programData.put("ageRating", ageRating);
                programData.put("title", title);
                programData.put("year", year);
                programData.put("tvShowId", tvShowId);
                programData.put("season", season);
                programData.put("episode", episode);
                programData.put("tvShowName", tvShowName);
                programs.put(programData);

                // Find next program first byte
                i += offset + tvShowNameLength;
                while (i < b.length-stover.length) {
                    boolean foundStover = true;
                    for (int j=0; j<stover.length; j++) {
                        if (b[i+j] != stover[j]) {
                            foundStover = false;
                            break;
                        }
                    }
                    if (foundStover) break;
                    i++;
                }
                i += stover.length;
            } catch (Exception e) {
                // The file has ended abruptly or is corrupted
                break;
            }
        }

        // Create response
        JSONObject file = new JSONObject();
        file.put("epgServiceName", epgServiceName);
        file.put("serviceVersion", serviceVersion);
        file.put("serviceUrl", serviceUrl);
        file.put("programs", programs);
        return file;
    }


    /**
     * Parse EPG string
     * @param  b Raw bytes
     * @return   EPG string
     */
    private String parseEpgString(byte[] b) {
        for (int i=0; i<b.length; i++) {
            b[i] ^= 0x15;
        }
        return new String(b);
    }

}
