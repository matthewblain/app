package who;

import io.opencensus.stats.Aggregation;
import io.opencensus.stats.Measure.MeasureLong;
import io.opencensus.stats.MeasureMap;
import io.opencensus.stats.Stats;
import io.opencensus.stats.StatsRecorder;
import io.opencensus.stats.View;
import io.opencensus.stats.ViewData;
import io.opencensus.stats.ViewManager;
import io.opencensus.tags.TagKey;
import io.opencensus.tags.Tagger;
import io.opencensus.tags.Tags;
import java.io.IOException;
import java.util.Collections;
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

  private static final Logger logger = LoggerFactory.getLogger(
    AppStoreServlet.class
  );

  static final String GOOGLE_PLAY_STORE_LINK =
    "https://play.google.com/store/apps/details?id=org.who.WHOMyHealth";
  static final String APPLE_APP_STORE_LINK =
    "https://apps.apple.com/app/id1503458183";

  private static final Tagger tagger = Tags.getTagger();
  private static final ViewManager viewManager = Stats.getViewManager();
  private static final StatsRecorder statsRecorder = Stats.getStatsRecorder();

  // frontendKey allows us to break down the recorded data
  private static final TagKey FRONTEND_KEY = TagKey.create("myorg_my_key");

  // videoSize will measure the size of processed videos.
  private static final MeasureLong SILLY_COUNT = MeasureLong.create(
    "my.org/measure/silly_count",
    "count of sillies",
    "1"
  );

  @Override
  protected void doGet(
    HttpServletRequest request,
    HttpServletResponse response
  ) throws IOException {
    String q = request.getQueryString();
    logger.info("Querystring {}", q);
    if (q != null && q.contains("logme")) {
      View.Name view_name = View.Name.create("silly_count");
      View view = View.create(
        view_name,
        "Count of test requests.",
        SILLY_COUNT,
        Aggregation.Count.create(),
        Collections.emptyList()
      );

      ViewManager viewManager = Stats.getViewManager();
      viewManager.registerView(view);

      MeasureMap measureMap = statsRecorder.newMeasureMap();
      measureMap.put(SILLY_COUNT, 1);
      measureMap.record();
      logger.info("Recorded!");
      ViewData viewData = viewManager.getView(view_name);
      logger.info(
        "Recorded stats for {}:\n {}}",
        view_name.asString(),
        viewData
      );
    }
    if (request.getHeader("User-Agent").toLowerCase().contains("android")) {
      response.sendRedirect(GOOGLE_PLAY_STORE_LINK);
    } else {
      response.sendRedirect(APPLE_APP_STORE_LINK);
    }
  }
}
