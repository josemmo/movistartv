package tk.josemmo.movistartv.client;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class TvClient {
    private static final String LOGTAG = "TvClient";
    private static final String EPG_ENTRYPOINT_KEY = "epgEntrypoints";
    private static final int MAX_EPG_DAYS = 2;
    private static final String RESOURCES_SERVER = "172.26.22.23";
    private static final String ENDPOINT = "http://172.26.22.23:2001/appserver/mvtv.do?action=";

    private SharedPreferences prefs;
    private RequestQueue requestQueue;

    private String dvbEntrypoint;
    private int demarcation;
    private String serviceProvider;
    private List<String> tvPackages;
    private String resBaseUri;
    private String tvChannelLogoPath;
    private String tvCoversPath;

    /**
     * TvClient constructor
     * @param ctx Context
     */
    public TvClient(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences("TvClientData", Context.MODE_PRIVATE);
        requestQueue = Volley.newRequestQueue(ctx);
        configureInstance();
    }


    /**
     * Get JSON response
     * @param  url Request URL
     * @return     Response
     */
    @Nullable
    private JSONObject getJsonResponse(String url) {
        RequestFuture<JSONObject> future = RequestFuture.newFuture();
        JsonObjectRequest request = new JsonObjectRequest(url, null, future, future);
        requestQueue.add(request);

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to get JSON response");
            e.printStackTrace();
        }
        return null;
    }


    /**
     * Resolve resources hostname
     * @param  input Input URL
     * @return       Resolved URL
     */
    private String resolveResourcesHostname(String input) {
        try {
            URL url = new URL(input);
            return url.toString().replace(url.getHost(), RESOURCES_SERVER);
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to parse URL " + input);
            e.printStackTrace();
            return input;
        }
    }


    /**
     * Configure this instance
     */
    private void configureInstance() {
        JSONObject client = getJsonResponse(ENDPOINT + "getClientProfile");
        JSONObject platform = getJsonResponse(ENDPOINT + "getPlatformProfile");
        JSONObject config = getJsonResponse(ENDPOINT + "getConfigurationParams");

        // Parse data from JSON responses
        try {
            client = client.getJSONObject("resultData");
            platform = platform.getJSONObject("resultData");
            config = config.getJSONObject("resultData");

            demarcation = client.getInt("demarcation");
            String[] tvPackages = client.getString("tvPackages").split("\\|");
            this.tvPackages = Arrays.asList(tvPackages);
            dvbEntrypoint = platform.getJSONObject("dvbConfig").getString("dvbipiEntryPoint");
            resBaseUri = resolveResourcesHostname(platform.getString("RES_BASE_URI"));
            tvChannelLogoPath = config.getString("tvChannelLogoPath");
            tvCoversPath = config.getString("tvCoversPath") +
                    config.getString("portraitSubPath") + "290x429/";
        } catch (Exception e) {
            Log.e(LOGTAG, "Exception when configuring instance");
            e.printStackTrace();
        }

        // Get service provider IP address
        try {
            UdpClient socket = new UdpClient(dvbEntrypoint);
            String res = socket.download()[0];
            res = res.split("DomainName=\"DEM_" + demarcation)[1];
            String address = res.split("Address=\"")[1].split("\"")[0];
            String port = res.split("Port=\"")[1].split("\"")[0];
            serviceProvider = address + ":" + port;
            Log.d(LOGTAG, "Found service provider at " + serviceProvider);
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to get service provider IP address");
            e.printStackTrace();
        }
    }


    /**
     * Get channels list
     * @return List of channels
     */
    public ArrayList<JSONObject> getChannelsList() throws Exception {
        // Get data from service provider
        UdpClient socket = new UdpClient(serviceProvider);
        String[] res = socket.download();

        // Extract XML documents from downloaded data
        Document serviceList = null;
        Document packageDiscovery = null;
        Document epgDiscovery = null;
        for (int i=0; i<res.length; i++) {
            res[i] = res[i].split("</ServiceDiscovery>", 2)[0];
            res[i] += "</ServiceDiscovery>";
            if (res[i].contains("</ServiceList>")) {
                serviceList = parseXmlString(res[i]);
            } else if (res[i].contains("</PackageDiscovery>")) {
                packageDiscovery = parseXmlString(res[i]);
            } else if (res[i].contains("</BCGDiscovery>")) {
                epgDiscovery = parseXmlString(res[i]);
            }
            res[i] = null;
        }

        // Extract EPG entrypoints (for later)
        if (epgDiscovery == null) {
            Log.w(LOGTAG, "Invalid EPG discovery response");
        } else {
            saveEpgEntrypoints(epgDiscovery);
        }

        // Generate array of services (channel data)
        SparseArray<Element> services = new SparseArray<>();
        NodeList serviceNodes = serviceList.getElementsByTagName("SingleService");
        for (int i=0; i<serviceNodes.getLength(); i++) {
            Element elem = (Element) serviceNodes.item(i);
            Element ti = (Element) elem.getElementsByTagName("TextualIdentifier").item(0);
            int serviceName = Integer.parseInt(ti.getAttribute("ServiceName"));
            services.put(serviceName, elem);
        }

        // Generate array of service names (list of dials)
        SparseIntArray serviceNames = new SparseIntArray();
        NodeList packageNodes = packageDiscovery.getElementsByTagName("Package");
        for (int i=0; i<packageNodes.getLength(); i++) {
            Element elem = (Element) packageNodes.item(i);
            String name = elem.getElementsByTagName("PackageName").item(0).getTextContent();
            if (!tvPackages.contains(name)) continue;

            NodeList childNodes = elem.getElementsByTagName("Service");
            for (int j=0; j<childNodes.getLength(); j++) {
                Element child = (Element) childNodes.item(j);
                Element textualID = (Element) child.getElementsByTagName("TextualID").item(0);
                int serviceName = Integer.parseInt(textualID.getAttribute("ServiceName"));
                int dial = Integer.parseInt(
                        child.getElementsByTagName("LogicalChannelNumber")
                                .item(0)
                                .getTextContent()
                );
                serviceNames.put(dial, serviceName);
            }
        }

        // Generate final channel list
        ArrayList<JSONObject> channels = new ArrayList<>();
        for (int i=0; i<serviceNames.size(); i++) {
            Log.d(LOGTAG, "Parsing channel #" + i + " out of " + serviceNames.size());
            int dial = serviceNames.keyAt(i);
            int serviceName = serviceNames.get(dial);
            Element service = services.get(serviceName);
            Element info = (Element) service.getElementsByTagName("SI").item(0);

            int epgServiceName = serviceName;
            NodeList replacements = service.getElementsByTagName("ReplacementService");
            if (replacements.getLength() > 0) {
                Element replacement = (Element) replacements.item(0);
                replacement = (Element) replacement.getElementsByTagName("TextualIdentifier").item(0);
                epgServiceName = Integer.parseInt(replacement.getAttribute("ServiceName"));
            }

            Element location = (Element) service.getElementsByTagName("ServiceLocation").item(0);
            location = (Element) location.getElementsByTagName("IPMulticastAddress").item(0);
            if (location == null) {
                Log.w(LOGTAG, "Found channel without IP Address at dial " + dial);
                continue;
            }
            String address = location.getAttribute("Address") + ":" + location.getAttribute("Port");

            String name = info.getElementsByTagName("Name").item(0).getTextContent();
            String shortName = info.getElementsByTagName("ShortName").item(0).getTextContent();
            String desc = info.getElementsByTagName("Description").item(0).getTextContent();

            Element ti = (Element) service.getElementsByTagName("TextualIdentifier").item(0);
            String logoUri = resBaseUri + tvChannelLogoPath + ti.getAttribute("logoURI");

            JSONObject channel = new JSONObject();
            channel.put("dial", dial);
            channel.put("serviceName", serviceName);
            channel.put("epgServiceName", epgServiceName);
            channel.put("mCastAddress", address);
            channel.put("name", name);
            channel.put("shortName", shortName);
            channel.put("description", desc);
            channel.put("logoUri", logoUri);
            channels.add(channel);
        }

        return channels;
    }


    /**
     * Parse XML string
     * @param  xmlString XML string
     * @return           DOM document
     */
    private Document parseXmlString(String xmlString) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        InputStream stream = new ByteArrayInputStream(xmlString.getBytes(StandardCharsets.UTF_8));
        Document doc = builder.parse(stream);
        doc.getDocumentElement().normalize();
        return doc;
    }


    /**
     * Get EPG entrypoints
     * @return EPG entrypoints
     */
    @NonNull
    private String[] getEpgEntrypoints() {
        return prefs.getString(EPG_ENTRYPOINT_KEY, "").split("\\|");
    }


    /**
     * Save EPG entrypoints
     * @param doc XML document
     */
    private void saveEpgEntrypoints(Document doc) {
        ArrayList<String> epgEntrypoints = new ArrayList<>();

        NodeList epgNodes = doc.getElementsByTagName("DVBBINSTP");
        for (int i=0; i<epgNodes.getLength(); i++) {
            Element elem = (Element) epgNodes.item(i);

            // Get EPG entrypoint address
            String ipAddress = elem.getAttribute("Address");
            String port = elem.getAttribute("Port");
            epgEntrypoints.add(ipAddress + ":" + port);
        }

        // Persist in SharedPreferences
        prefs.edit()
                .putString(EPG_ENTRYPOINT_KEY, TextUtils.join("|", epgEntrypoints))
                .apply();
    }


    /**
     * Get EPG data
     * @return EPG data
     */
    public SparseArray<ArrayList<JSONObject>> getEpgData() throws Exception {
        String[] entrypoints = getEpgEntrypoints();

        // Create and start EPG threads
        int numOfWorkers = Math.min(MAX_EPG_DAYS, entrypoints.length);
        EpgDownloader[] workers = new EpgDownloader[numOfWorkers];
        for (int i=0; i<workers.length; i++) {
            workers[i] = new EpgDownloader(entrypoints[i]);
            workers[i].start();
        }
        for (int i=0; i<workers.length; i++) {
            workers[i].join();
        }

        // Extract data from threads
        SparseArray<ArrayList<JSONObject>> res = new SparseArray<>();
        for (int i=0; i<workers.length; i++) {
            for (JSONObject epgFile : workers[i].getEpgFiles()) {
                int epgServiceName = epgFile.getInt("epgServiceName");
                ArrayList<JSONObject> programs = res.get(epgServiceName, null);
                if (programs == null) {
                    programs = new ArrayList<>();
                    res.put(epgServiceName, programs);
                }
                for (int j=0; j<epgFile.getJSONArray("programs").length(); j++) {
                    JSONObject program = epgFile.getJSONArray("programs").getJSONObject(j);
                    program.put("coverPath", getFullCoverPath(program.getInt("pId")));
                    programs.add(program);
                }
            }
        }

        return res;
    }


    /**
     * Get full cover path
     * @param  pId Program ID
     * @return     Full cover path
     */
    @NonNull
    private String getFullCoverPath(int pId) {
        String pIdStr = pId + "";
        return resBaseUri + tvCoversPath + pIdStr.substring(0, 4) + "/" + pIdStr + ".jpg";
    }

}
