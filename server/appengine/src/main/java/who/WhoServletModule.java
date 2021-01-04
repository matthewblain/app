package who;

import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.modules.ModulesServiceFactory;
import com.google.inject.Provides;
import com.google.inject.servlet.ServletModule;
import com.googlecode.objectify.ObjectifyFilter;
import com.googlecode.objectify.ObjectifyService;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsConfiguration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.metrics.LabelKey;
import io.opencensus.metrics.LabelValue;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guice and web configuration. Use this instead of web.xml.
 */
public class WhoServletModule extends ServletModule {

  private static final Logger logger = LoggerFactory.getLogger(
    WhoServletModule.class
  );

  Map<LabelKey, LabelValue> getOpenCensusDefaultLabels() {
    Map<LabelKey, LabelValue> resourceLabels = new HashMap<LabelKey, LabelValue>();
    ModulesService modulesService = ModulesServiceFactory.getModulesService();
    resourceLabels.put(
      LabelKey.create("module_id", "App Engine Module ID"),
      LabelValue.create(modulesService.getCurrentModule())
    );
    resourceLabels.put(
      LabelKey.create("version_id", "App Engine Version ID"),
      LabelValue.create(modulesService.getCurrentVersion())
    );
    resourceLabels.put(
      LabelKey.create("instance_id", "App Engine Instance ID"),
      LabelValue.create(modulesService.getCurrentInstanceId())
    );

    // This may not be necessary. The opencensus_task appears to exist so that if
    // no other labels exist, there's a chance of unique labels-per-process.
    final LabelKey OPENCENSUS_TASK_KEY = LabelKey.create(
      "opencensus_task",
      "Opencensus task identifier"
    );
    resourceLabels.put(
      OPENCENSUS_TASK_KEY,
      LabelValue.create("java@" + modulesService.getCurrentInstanceId())
    );
    return resourceLabels;
  }

  @Provides
  Environment provideEnvironment() {
    return Environment.current();
  }

  @Override
  protected void configureServlets() {
    install(new FirebaseModule());

    serve("/app").with(new AppStoreServlet());

    // Set up Objectify
    filter("/*").through(ObjectifyFilter.class);
    bind(ObjectifyFilter.class).in(Singleton.class);

    // Register Objectify entities
    ObjectifyService.register(Client.class);
    ObjectifyService.register(StoredCaseStats.class);

    bind(NotificationsManager.class).asEagerSingleton();

    // Internal cron jobs using Objectify but not requiring Clients.
    serve("/internal/cron/refreshCaseStats")
      .with(new RefreshCaseStatsServlet());

    // Set up Present RPC
    filter("/*").through(WhoRpcFilter.class);

    // Set up stackdriver Google Cloud Monitoring

    try {
      StackdriverStatsConfiguration.Builder config = StackdriverStatsConfiguration.builder();
      //config.setExportInterval(Duration.fromMillis(500)); // How do we ensure a push before shutdown?
      config.setConstantLabels(getOpenCensusDefaultLabels());

      StackdriverStatsExporter.createAndRegister(config.build());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
