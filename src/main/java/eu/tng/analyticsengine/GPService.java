/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.tng.analyticsengine;

import com.google.gson.Gson;
import eu.tng.analyticsengine.Messaging.LogsFormat;
import eu.tng.repository.dao.AnalyticResultRepository;
import eu.tng.repository.dao.AnalyticServiceRepository;
import eu.tng.repository.domain.AnalyticResult;
import eu.tng.repository.domain.AnalyticService;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.PushGateway;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestTemplate;

/**
 *
 * @author Eleni Fotopoulou <efotopoulou@ubitech.eu>
 */
@Service
public class GPService {

    @Value("${physiognomica.server.url}")
    private String physiognomicaServerURL;

    @Value("${prometheus.url}")
    private String prometheusURL;

    @Value("${prometheus.gateway}")
    String prometheusGateway;

    @Value("${monitoring.engine}")
    private String monitoringEngine;

    @Value("${repository.url}")
    private String repositoryURL;

    private static final Logger logger = Logger.getLogger(GPService.class.getName());
    public static Counter requests = Counter.build().name("analytic_requests_total").help("Total analytic requests.").register();

    @Autowired
    private AnalyticServiceRepository analyticServiceRepository;

    @Autowired
    private AnalyticResultRepository analyticResulteRepository;

    @Autowired
    LogsFormat logsFormat;

    public String getPrometheusMetrics() {

        String prometheusMetricsURL = prometheusURL + "/api/v1/label/__name__/values";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(prometheusMetricsURL, HttpMethod.GET, entity, String.class);

        String myresponse = response.getBody();

        return myresponse;
    }

    //TODO get them with concatenated keywords
    public List<String> getFilteredPrometheusMetrics(String keyword) {

        String prometheusMetricsURL = prometheusURL + "/api/v1/label/__name__/values";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(prometheusMetricsURL, HttpMethod.GET, entity, String.class);

        JSONObject myresponse = new JSONObject(response.getBody());

        JSONArray data = myresponse.getJSONArray("data");

        List<String> dataList = IntStream.range(0, data.length()).mapToObj(i -> data.getString(i)).collect(Collectors.toList());

        Stream<String> onlymetricsiwant = dataList.stream().filter(s -> s.contains(keyword));

        List<String> filteredData = onlymetricsiwant.collect(Collectors.toList());

        return filteredData;
    }

    //TODO get attributes that may miss certain category dimensions
    public List<String> getPrometheousMetricDimensions(String metric) {
        List<String> metricDimensions = new ArrayList();
        try {
            System.out.println("getPrometheousMetricDimensions is triggered for metric" + metric);
            String prometheusMetricsURL = prometheusURL + "/api/v1/series?match[]=" + metric;

            System.out.println("prometheusMetricsURL" + prometheusMetricsURL);

            URL obj = new URL(prometheusMetricsURL);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            // optional default is GET
            con.setRequestMethod("GET");

            int responseCode = con.getResponseCode();
            System.out.println("\nSending 'GET' request to URL : " + prometheusMetricsURL);
            System.out.println("Response Code : " + responseCode);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            //print result
            System.out.println(response.toString());
            JSONObject myresponse = new JSONObject(response.toString());

            JSONArray data = myresponse.getJSONArray("data");

            List<JSONObject> dataList = IntStream.range(0, data.length()).mapToObj(i -> data.getJSONObject(i)).collect(Collectors.toList());

            metricDimensions = dataList.stream().map(x -> (x.getString("__name__")
                    + "{"
                    + "chart='" + (x.has("chart") ? x.getString("chart") : "") + "',"
                    + "dimension='" + (x.has("dimension") ? x.getString("dimension") : "") + "',"
                    + "family='" + (x.has("family") ? x.getString("family") : "") + "',"
                    + "instance='" + (x.has("instance") ? x.getString("instance") : "") + "',"
                    + "job='" + (x.has("job") ? x.getString("job") : "")) + "'}"
            ).collect(Collectors.toList());

        } catch (ProtocolException ex) {
            Logger.getLogger(GPService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (MalformedURLException ex) {
            Logger.getLogger(GPService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(GPService.class.getName()).log(Level.SEVERE, null, ex);
        }
        return metricDimensions;

    }

    public List<String> getFilteredPrometheusMetricswithAllDimensions(String keyword) {

        List<String> filteredData = this.getFilteredPrometheusMetrics(keyword);
        List<String> metricswithAllDimensions = new ArrayList();

        filteredData.forEach(metric -> {

            this.getPrometheousMetricDimensions(metric).stream().sequential().collect(Collectors.toCollection(() -> metricswithAllDimensions));

        });

        return metricswithAllDimensions;

    }

    public List<String> get5gtangoVnVNetworkServiceMetrics(String nsr_id) {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        List<String> metricswithDimensions = new ArrayList();

        String monitoringEngineURL = monitoringEngine + "/api/v2/services/" + nsr_id + "/metrics";
        logsFormat.createLogInfo("I", timestamp.toString(), "Connection to monitoring manager", monitoringEngineURL, "200");

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(monitoringEngineURL, HttpMethod.GET, entity, String.class);

        JSONObject myresponse = new JSONObject(response.getBody());

        if (!myresponse.getString("status").equalsIgnoreCase("Success")) {
            logsFormat.createLogWarn("W", timestamp.toString(), "No monitoring metrics available", "No monitoring metrics available for nsr_id " + nsr_id, "200");
            return metricswithDimensions;
        }

        JSONArray vnfs = myresponse.getJSONArray("vnfs");

        for (int i = 0; i < vnfs.length(); i++) {
            JSONObject vnf = vnfs.getJSONObject(i);
            JSONArray vdus = vnf.getJSONArray("vdus");
            for (int k = 0; k < vdus.length(); k++) {
                JSONObject vdu = vdus.getJSONObject(k);

                JSONArray metrics = vdu.getJSONArray("metrics");

                for (int n = 0; n < metrics.length(); n++) {
                    JSONObject metric = metrics.getJSONObject(n);

                    String metricwithDimensions = metric.getString("__name__") + "{"
                            + "name='" + (metric.has("name") ? metric.getString("name") : "") + "'}";

                    metricswithDimensions.add(metricwithDimensions);
                }

            }
        }

        return metricswithDimensions;
    }

    public JSONObject get5gtangoVnVTestMetadata(String testr_uuid) {

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(repositoryURL + "/trr/test-suite-results/" + testr_uuid, HttpMethod.GET, entity, String.class);
        JSONObject myresponse = new JSONObject(response.getBody());

        String nsr_id = myresponse.getString("instance_uuid");
        String start = myresponse.getString("started_at");
        String end = myresponse.getString("ended_at");

        JSONObject responseObject = new JSONObject();
        responseObject.put("nsr_id", nsr_id);
        responseObject.put("start", start);
        responseObject.put("end", end);

        return responseObject;
    }

    public List<String> get5gtangoSPNetworkServiceMetrics(String nsr_id) {
        List<String> metricswithDimensions = new ArrayList();

        String monitoringEngineURL = monitoringEngine + "/api/v2/services/" + nsr_id + "/metrics";
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(monitoringEngineURL, HttpMethod.GET, entity, String.class);

        JSONObject myresponse = new JSONObject(response.getBody());

        JSONArray vnfs = myresponse.getJSONArray("vnfs");

        for (int i = 0; i < vnfs.length(); i++) {
            JSONObject vnf = vnfs.getJSONObject(i);
            JSONArray vdus = vnf.getJSONArray("vdus");
            for (int k = 0; k < vdus.length(); k++) {
                JSONObject vdu = vdus.getJSONObject(k);

                JSONArray metrics = vdu.getJSONArray("metrics");

                for (int n = 0; n < metrics.length(); n++) {
                    JSONObject metric = metrics.getJSONObject(n);

                    String metricwithDimensions = metric.getString("__name__") + "{"
                            + "project_id='" + (metric.has("project_id") ? metric.getString("project_id") : "") + "',"
                            + "resource_id='" + (metric.has("resource_id") ? metric.getString("resource_id") : "") + "'}";

                    metricswithDimensions.add(metricwithDimensions);
                }

            }
        }

        return metricswithDimensions;
    }

    @Async
    public void consumeAnalyticService(String analytic_service_info) throws IOException {

        CollectorRegistry registry = new CollectorRegistry();
        Gauge duration = Gauge.build().name("analytic_service_duration_seconds").help("Duration of analytic process execution in seconds.").register(registry);
        Gauge.Timer durationTimer = duration.startTimer();

        requests.inc();
        requests.register(registry);

        try {

            Gson gson = new Gson();
            JSONObject analytic_service = new JSONObject(analytic_service_info);
            JSONArray periods = new JSONArray();
            if (analytic_service.has("periods")) {
                periods = analytic_service.getJSONArray("periods");
            }
            String step = analytic_service.getString("step");//"'3m'"
            String name = analytic_service.getString("name");
            String vendor = analytic_service.getString("vendor");

            RestTemplate restTemplate = new RestTemplate();

            JSONArray metrics = null;
            if (vendor.equalsIgnoreCase("5gtango.vnv")) {
                if (analytic_service.has("testr_uuid")) {

                    String testr_uuid = analytic_service.getString("testr_uuid");
                    JSONObject test_metadata = this.get5gtangoVnVTestMetadata(testr_uuid);
                    String nsr_id = test_metadata.getString("nsr_id");

                    if (analytic_service.has("metrics")) {
                        metrics = analytic_service.getJSONArray("metrics");
                    } else {
                        List<String> metricslist = this.get5gtangoVnVNetworkServiceMetrics(nsr_id);
                        metrics = new JSONArray(metricslist);
                    }
                    System.out.println("metrics--> " + metrics);
                    JSONObject periodOne = new JSONObject();
                    periodOne.put("start", test_metadata.getString("start"));
                    periodOne.put("end", test_metadata.getString("end"));
                    periods.put(periodOne);
                    //System.out.println("periods------------" + periods.toString());
                }

            } else if (analytic_service.has("metrics")) {
                metrics = analytic_service.getJSONArray("metrics");
                System.out.println("metrics--> " + metrics);
            } else {
                //TODO log no metrics requested
            }

            logger.info("connect to prometheusURL" + prometheusURL);
            //System.out.println("metrics.toString()" + metrics.toString());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> map3 = new LinkedMultiValueMap<String, String>();
            map3.add("prometheus_url", "'" + prometheusURL + "'");
            map3.add("metrics", metrics.toString());
            map3.add("step", "'" + step + "'");
            map3.add("periods", periods.toString());
            map3.add("enriched", "true");

            HttpEntity<MultiValueMap<String, String>> physiognomicaRequest3 = new HttpEntity<>(map3, headers);

            AnalyticService as = analyticServiceRepository.findByName(name);

            String analytic_service_url = physiognomicaServerURL + as.getUrl();
            System.out.println("analytic_service_url" + analytic_service_url);

            ResponseEntity<String> response3 = restTemplate.postForEntity(analytic_service_url, physiognomicaRequest3, String.class);

            String myresponse = "";
            if (null != response3 && null != response3.getStatusCode() && response3
                    .getStatusCode()
                    .is2xxSuccessful()) {

                myresponse = response3.getBody();
                myresponse = myresponse.replace("/ocpu/tmp/", physiognomicaServerURL + "/ocpu/tmp/");

                String lines[] = myresponse.split("\\r?\\n");
                JSONArray response = new JSONArray();
                List<String> resultslist = as.getResults();
                for (String line : lines) {

                    if (resultslist.stream().anyMatch(s -> line.contains(s))) {
                        if (line.contains("html")) {
                            JSONObject result = new JSONObject();
                            result.put("type", "html");
                            result.put("result", line);
                            response.put(result);
                        } else if (line.contains("csv")) {
                            JSONObject result = new JSONObject();
                            result.put("type", "csv");
                            result.put("result", line);
                            response.put(result);
                        } else if (line.contains("json")) {
                            JSONObject result = new JSONObject();
                            result.put("type", "json");
                            result.put("result", line);
                            response.put(result);
                        } else if (line.contains("jpg") || line.contains("png")) {
                            JSONObject result = new JSONObject();
                            result.put("type", "img");
                            result.put("result", line);
                            response.put(result);
                        } else {
                            JSONObject result = new JSONObject();
                            result.put("type", "txt");
                            result.put("result", line);
                            response.put(result);
                        }
                    }

                }
                logger.info(response.toString());

                //save the analytic result
                AnalyticResult analyticresult = new AnalyticResult();
                analyticresult.setStatus("SUCCESS");
                analyticresult.setExecutionMessage("The analytic service has succesfully completed.");
                analyticresult.setAnalyticServiceName(name);
                analyticresult.setExecutionDate(new Date());
                analyticresult.setResults(response.toList());
                AnalyticResult savedanalyticresult = analyticResulteRepository.save(analyticresult);

                //update the callback url if any
                if (analytic_service.has("callback")) {
                    String callback_url = analytic_service.getString("callback");

                    HttpHeaders callbackHeaders = new HttpHeaders();
                    callbackHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

                    logger.info("analyticresult  " + gson.toJson(analyticresult));
                    logger.info("callback_url  " + callback_url);

                    //ResponseEntity<String> callback_response = restTemplate.postForEntity(callback_url, gson.toJson(analyticresult), String.class);
                    String payload = gson.toJson(analyticresult);
                    StringEntity entity = new StringEntity(payload, ContentType.APPLICATION_JSON);

                    HttpClient httpClient = HttpClientBuilder.create().build();
                    HttpPost request = new HttpPost(callback_url);
                    request.setEntity(entity);

                    HttpResponse testresponse = httpClient.execute(request);
                    int statuscode = testresponse.getStatusLine().getStatusCode();

                    if (statuscode == 200) {
                        org.apache.http.HttpEntity entity1 = testresponse.getEntity();
                        // Read the contents of an entity and return it as a String.
                        String content = EntityUtils.toString((org.apache.http.HttpEntity) entity1);
                        String callback_uuid = content;

                        Optional<AnalyticResult> existing_as = analyticResulteRepository.findByCallbackid(callback_uuid);
                        if (existing_as.isPresent()) {
                            logger.severe("duplicate callback_uuid. Analytic service is not saved");
                            analyticResulteRepository.delete(savedanalyticresult);
                            return;
                        } else {
                            savedanalyticresult.setCallbackid(callback_uuid);
                            analyticResulteRepository.save(savedanalyticresult);
                        }

                    } else {
                        savedanalyticresult.setStatus("ERROR");
                        savedanalyticresult.setExecutionMessage("callback url returned HTTP error " + statuscode);
                        analyticResulteRepository.save(savedanalyticresult);
                    }
                }

            }
            //calculate amount of data
            String start = periods.getJSONObject(0).getString("start");
            String end = periods.getJSONObject(0).getString("end");

            Instant startInstant = Instant.parse(start);
            Instant endInstant = Instant.parse(end);

            Duration between = Duration.between(startInstant, endInstant);

            long period_duration = between.getSeconds();

            int step_in_seconds = 1;
            if (step.contains("m")) {
                step_in_seconds = Integer.parseInt(step.replace("m", "")) * 60;
            } else if (step.contains("s")) {
                step_in_seconds = Integer.parseInt(step.replace("s", ""));
            } else if (step.contains("h")) {
                step_in_seconds = Integer.parseInt(step.replace("h", "")) * 3600;
            }

            if (analytic_service.has("metrics")) {
                period_duration = Math.round(period_duration * analytic_service.getJSONArray("metrics").length() / step_in_seconds);
                System.out.println("num of metrics " + period_duration);
            }

            // Temporal start = periods.getJSONObject(0).getString("start");
            //Duration duration = Duration.between(previous, now);
            // This is only added to the registry after success,
            // so that a previous success in the Pushgateway isn't overwritten on failure.
            Gauge num_of_metrics = Gauge.build().name("analytic_service_num_of_metrics").help("analytic_service_num_of_metrics").register(registry);
            num_of_metrics.set(period_duration);
        } finally {
            durationTimer.setDuration();
            PushGateway pg = new PushGateway(prometheusGateway);
            pg.pushAdd(registry, "analytic_service_job");

        }
    }

    @ExceptionHandler({IOException.class})
    public String handleError(java.io.IOException e) {

        JSONObject response = new JSONObject();

        response.put("code", "503");
        response.put("message", e.getMessage());
        return response.toString();
    }

}
