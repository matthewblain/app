package who;

import com.google.api.Metric;
import com.google.api.MonitoredResource;
import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.modules.ModulesServiceFactory;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.monitoring.v3.CreateTimeSeriesRequest;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.monitoring.v3.TypedValue;
import com.google.protobuf.util.Timestamps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Redirects to appropriate app store.
 *
 * @author Bob Lee
 */
public class AppStoreServlet extends HttpServlet {

  static final String GOOGLE_PLAY_STORE_LINK =
    "https://play.google.com/store/apps/details?id=org.who.WHOMyHealth";
  static final String APPLE_APP_STORE_LINK =
    "https://apps.apple.com/app/id1503458183";

  private static final Logger logger = LoggerFactory.getLogger(
    AppStoreServlet.class
  );

  @Override
  protected void doGet(
    HttpServletRequest request,
    HttpServletResponse response
  ) throws IOException {
    if (request.getHeader("User-Agent").toLowerCase().contains("android")) {
      response.sendRedirect(GOOGLE_PLAY_STORE_LINK);
    } else {
      response.sendRedirect(APPLE_APP_STORE_LINK);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException {
    logger.info("LOL");
    DoSomeNonsense();
  }

  private void DoSomeNonsense() throws IOException {
    // Instantiates a client
    MetricServiceClient metricServiceClient = MetricServiceClient.create();

    // Prepares an individual data point
    TimeInterval interval = TimeInterval
      .newBuilder()
      .setEndTime(Timestamps.fromMillis(System.currentTimeMillis()))
      .build();
    TypedValue value = TypedValue.newBuilder().setDoubleValue(3.14).build();
    Point point = Point
      .newBuilder()
      .setInterval(interval)
      .setValue(value)
      .build();

    List<Point> pointList = new ArrayList<>();
    pointList.add(point);

    // SystemProperty.applicationId.get() does not work right in dev_appserver!
    String projectId = Environment.getApplicationId();
    ProjectName name = ProjectName.of(projectId);
    logger.info("Project id {} name {}", projectId, name.toString());

    Map<String, String> environ = System.getenv();
    Gson pprinter = new GsonBuilder().setPrettyPrinting().create();
    logger.info("ENVIRON {}", pprinter.toJson(environ));

    // Prepares the metric descriptor
    Map<String, String> metricLabels = new HashMap<String, String>();
    metricLabels.put("store_id", "Pittsburg");
    Metric metric = Metric
      .newBuilder()
      .setType("custom.googleapis.com/my_metric")
      .putAllLabels(metricLabels)
      .build();

    // Prepares the monitored resource descriptor
    /*
    https://cloud.google.com/monitoring/api/resources#tag_gae_instance
    Labels:

    project_id: The identifier of the GCP project associated with this resource, such as "my-project".
    module_id: The service/module name.
    version_id: The version name.
    instance_id: The instance id.
    location: App Engine's external notion of location.

     */
    Map<String, String> resourceLabels = new HashMap<String, String>();
    ModulesService modulesService = ModulesServiceFactory.getModulesService();
    resourceLabels.put("project_id", projectId);
    resourceLabels.put("module_id", modulesService.getCurrentModule());
    resourceLabels.put("version_id", modulesService.getCurrentVersion());
    resourceLabels.put("instance_id", modulesService.getCurrentInstanceId());
    resourceLabels.put("location", environ.get("DATACENTER"));

    MonitoredResource resource = MonitoredResource
      .newBuilder()
      .setType("gae_instance")
      .putAllLabels(resourceLabels)
      .build();

    // Prepares the time series request
    TimeSeries timeSeries = TimeSeries
      .newBuilder()
      .setMetric(metric)
      .setResource(resource)
      .addAllPoints(pointList)
      .build();
    List<TimeSeries> timeSeriesList = new ArrayList<>();
    timeSeriesList.add(timeSeries);

    CreateTimeSeriesRequest request = CreateTimeSeriesRequest
      .newBuilder()
      .setName(name.toString())
      .addAllTimeSeries(timeSeriesList)
      .build();

    // Writes time series data
    metricServiceClient.createTimeSeries(request);
    metricServiceClient.close();

    logger.info("Done writing time series data {}", request.toString());
  }
}
