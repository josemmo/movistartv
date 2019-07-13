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
import org.minidns.DnsClient;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsmessage.Question;
import org.minidns.record.A;
import org.minidns.record.Record;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class TvClient {
    private static final String LOGTAG = "TvClient";
    private static final String EPG_ENTRYPOINT_KEY = "epgEntrypoints";
    private static final int MAX_EPG_DAYS = 1; // TODO: increase number
    private static final String DNS_SERVER = "172.26.23.3";
    private static final String ENDPOINT = "http://172.26.22.23:2001/appserver/mvtv.do?action=";

    private SharedPreferences prefs;
    private RequestQueue requestQueue;
    private DnsClient dnsClient;

    private String dvbEntrypoint;
    private int demarcation;
    private String serviceProvider;
    private List<String> tvPackages;
    private String resBaseUri;
    private String tvChannelLogoPath;

    /**
     * TvClient constructor
     * @param ctx Context
     */
    public TvClient(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences("TvClientData", Context.MODE_PRIVATE);
        requestQueue = Volley.newRequestQueue(ctx);
        dnsClient = new DnsClient();
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
     * Resolve hostname
     * @param  input Input URL
     * @return       Resolved URL
     */
    private String resolveHostname(String input) {
        try {
            URL url = new URL(input);
            Question dnsQuestion = new Question(url.getHost(), Record.TYPE.A);
            DnsMessage res = dnsClient.query(dnsQuestion, InetAddress.getByName(DNS_SERVER));
            A record = (A) res.answerSection.get(0).payloadData;
            String ipAddress = record.toString();
            String newUrl = url.toString().replace(url.getHost(), ipAddress);
            return newUrl;
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to resolve hostname of " + input);
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
            demarcation = client.getJSONObject("resultData")
                    .getInt("demarcation");
            String[] tvPackages = client.getJSONObject("resultData")
                    .getString("tvPackages")
                    .split("\\|");
            this.tvPackages = Arrays.asList(tvPackages);
            dvbEntrypoint = platform.getJSONObject("resultData")
                    .getJSONObject("dvbConfig")
                    .getString("dvbipiEntryPoint");
            resBaseUri = resolveHostname(platform.getJSONObject("resultData")
                    .getString("RES_BASE_URI"));
            tvChannelLogoPath = config.getJSONObject("resultData")
                    .getString("tvChannelLogoPath");
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
    public ArrayList<JSONObject> getChannelsList(String[] testData) throws Exception {
        /*
        // Get data from service provider
        UdpClient socket = new UdpClient(serviceProvider);
        String[] res = socket.download();
        */
        String[] res = testData; // TODO: remove test data

        // Extract XML documents from downloaded data
        Document serviceList = null;
        Document packageDiscovery = null;
        Document epgDiscovery = null;
        for (int i=0; i<res.length; i++) {
            res[i] = res[i].split("</ServiceDiscovery>", 2)[0];
            res[i] += "</ServiceDiscovery>";
            res[i] = res[i].replaceAll("\n", "");
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
        saveEpgEntrypoints(epgDiscovery);

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
            int dial = serviceNames.keyAt(i);
            int serviceName = serviceNames.get(dial);
            Element service = services.get(serviceName);
            Element info = (Element) service.getElementsByTagName("SI").item(0);

            Element location = (Element) service.getElementsByTagName("ServiceLocation").item(0);
            location = (Element) location.getElementsByTagName("IPMulticastAddress").item(0);
            String address = location.getAttribute("Address") + ":" + location.getAttribute("Port");

            String name = info.getElementsByTagName("Name").item(0).getTextContent();
            String shortName = info.getElementsByTagName("ShortName").item(0).getTextContent();
            String desc = info.getElementsByTagName("Description").item(0).getTextContent();

            Element ti = (Element) service.getElementsByTagName("TextualIdentifier").item(0);
            String logoUri = resBaseUri + tvChannelLogoPath + ti.getAttribute("logoURI");

            JSONObject channel = new JSONObject();
            channel.put("dial", dial);
            channel.put("serviceName", serviceName);
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
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource inputSource = new InputSource(new StringReader(xmlString));
        Document doc = builder.parse(inputSource);
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
    @NonNull
    private void saveEpgEntrypoints(Document doc) {
        ArrayList<String> epgEntrypoints = new ArrayList<>();

        NodeList epgNodes = doc.getElementsByTagName("DVBBINSTP");
        for (int i=0; i<epgNodes.getLength(); i++) {
            Element elem = (Element) epgNodes.item(i);
            String ipAddress = elem.getAttribute("Address");
            String port = elem.getAttribute("Port");
            epgEntrypoints.add(ipAddress + ":" + port);
        }

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
                int serviceName = epgFile.getInt("serviceName");
                ArrayList<JSONObject> programs = res.get(serviceName, null);
                if (programs == null) {
                    programs = new ArrayList<>();
                    res.put(serviceName, programs);
                }
                for (int j=0; j<epgFile.getJSONArray("programs").length(); j++) {
                    JSONObject program = epgFile.getJSONArray("programs").getJSONObject(j);
                    programs.add(program);
                }
            }
        }

        return res;
    }

}
