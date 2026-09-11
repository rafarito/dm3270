package com.bytezone.dm3270.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.prefs.Preferences;

import com.bytezone.dm3270.datasets.DatasetStore;
import com.bytezone.dm3270.database.QueuedDatasetStore;
import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.plugins.PluginsStage;
import com.bytezone.dm3270.session.Session;
import com.bytezone.dm3270.streams.SessionLoader;
import com.bytezone.dm3270.streams.TelnetState;
import com.bytezone.dm3270.utilities.Dm3270Utility;
import com.bytezone.dm3270.utilities.Site;
import com.bytezone.dm3270.utilities.SiteValue;
import com.bytezone.dm3270.utilities.WindowSaver;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Console extends Application
{
  private static final Logger logger = LoggerFactory.getLogger (Console.class);
  private static final int MAINFRAME_EMULATOR_PORT = 5555;
  private static final Site DEFAULT_MAINFRAME = new SiteValue ("mainframe",
      "localhost", MAINFRAME_EMULATOR_PORT, true, 2, false, false, false, "");

  private Stage primaryStage;
  private Rectangle2D primaryScreenBounds;
  private WindowSaver consoleWindowSaver;
  private WindowSaver spyWindowSaver;

  private Preferences prefs;
  private Screen screen;
  private final ScreenDimensions screenDimensions = new ScreenDimensions (24, 80);
  private ScreenDimensions alternateScreenDimensions;
  private final TelnetState telnetState = new TelnetState ();

  private OptionStage optionStage;
  private SpyPane spyPane;
  private ConsolePane consolePane;
  private ReplayStage replayStage;
  private MainframeStage mainframeStage;
  private PluginsStage pluginsStage;

  @Override
  public void init () throws Exception
  {
    super.init ();

    prefs = Preferences.userNodeForPackage (this.getClass ());
    for (String raw : getParameters ().getRaw ())
      if (raw.equalsIgnoreCase ("-reset"))
        prefs.clear ();
  }

  @Override
  public void start (Stage primaryStage) throws Exception
  {
    this.primaryStage = primaryStage;
    primaryStage.setOnCloseRequest (e -> Platform.exit ());
    primaryStage.setResizable (true);

    pluginsStage = new PluginsStage (prefs);
    optionStage = new OptionStage (prefs, pluginsStage.getEditMenuItem ());

    primaryScreenBounds = javafx.stage.Screen.getPrimary ().getVisualBounds ();

    optionStage.okButton.setOnAction (e -> startSelectedFunction ());
    optionStage.cancelButton.setOnAction (e -> optionStage.hide ());
    optionStage.show ();
  }

  private void startSelectedFunction ()
  {
    optionStage.hide ();
    String errorMessage = "";

    LaunchRequest request = optionStage.getLaunchRequest ();
    Optional<Site> optionalServerSite = request.serverSite ();
    Optional<Site> optionalClientSite = request.clientSite ();

    switch (request.function ())
    {
      case REPLAY:
        Path path = Paths.get (request.spyFolder () + "/" + request.replayFile ());
        if (!Files.exists (path))
          errorMessage = path + " does not exist";
        else
          try
          {
            // can throw Exception
            Session session =
                SessionLoader.replay (telnetState, path, Platform::runLater);
            alternateScreenDimensions = session.getScreenDimensions ();

            Optional<Site> serverSite =
                optionStage.findServerSite (session.getServerName ());
            if (serverSite.isPresent ())
            {
              Site site = serverSite.get ();
              setConsolePane (createScreen (TerminalFunction.REPLAY, site), site);
            }
            else
            {
              logger.warn ("Couldn't find the server site for {}",
                  session.getServerName ());
              setConsolePane (createScreen (TerminalFunction.REPLAY, null), null);
            }

            replayStage = new ReplayStage (session, path, prefs, screen);
            replayStage.show ();
          }
          catch (Exception e)
          {
            logger.error ("Error creating replay window", e);
            errorMessage = "Error creating replay window";
          }

        break;

      case TERMINAL:
        if (optionalServerSite.isPresent ())
        {
          Site serverSite = optionalServerSite.get ();
          setModel (serverSite);
          setConsolePane (createScreen (TerminalFunction.TERMINAL, serverSite), serverSite);
          consolePane.connect ();
        }
        else
          errorMessage = "No server selected";

        break;

      case SPY:
        if (!optionalServerSite.isPresent ())
          errorMessage = "No server selected";
        else if (!optionalClientSite.isPresent ())
          errorMessage = "No client selected";
        else
        {
          Site serverSite = optionalServerSite.get ();
          Site clientSite = optionalClientSite.get ();
          setSpyPane (createScreen (TerminalFunction.SPY, null), serverSite, clientSite);
        }

        break;

      case TEST:
        if (!optionalClientSite.isPresent ())
          errorMessage = "No client selected";
        else
        {
          Site clientSite = optionalClientSite.get ();
          setSpyPane (createScreen (TerminalFunction.TEST, null), DEFAULT_MAINFRAME, clientSite);
          mainframeStage = new MainframeStage (telnetState, MAINFRAME_EMULATOR_PORT);
          mainframeStage.show ();
          mainframeStage.startServer ();
        }

        break;
    }

    if (!errorMessage.isEmpty () && Dm3270Utility.showAlert (errorMessage))
      optionStage.show ();
  }

  private void setModel (Site serverSite)
  {
    int model = serverSite.getModel ();
    logger.debug ("model: {}", model);
    switch (model)
    {
      case 2:
        alternateScreenDimensions = new ScreenDimensions (24, 80);
        telnetState.setDoDeviceType (2);
        break;
      case 3:
        alternateScreenDimensions = new ScreenDimensions (32, 80);
        telnetState.setDoDeviceType (3);
        break;
      case 4:
        alternateScreenDimensions = new ScreenDimensions (43, 80);
        telnetState.setDoDeviceType (4);
        break;
      case 5:
        alternateScreenDimensions = new ScreenDimensions (27, 132);
        telnetState.setDoDeviceType (5);
      default:
        logger.warn ("Invalid model number: {}", model);
    }
  }

  //  private Optional<Site> findSite (String serverName)
  //  {
  //    Optional<Site> optionalServerSite =
  //        optionStage.serverSitesListStage.getSelectedSite (serverName);
  //    return optionalServerSite;
  //  }

  private void setConsolePane (Screen screen, Site serverSite)
  {
    consolePane = new ConsolePane (screen, serverSite, pluginsStage);
    Scene scene = new Scene (consolePane);

    primaryStage.setScene (scene);
    primaryStage.setTitle ("dm3270");

    if (screen.getFunction () == TerminalFunction.TERMINAL)
    {
      consoleWindowSaver = new WindowSaver (prefs, primaryStage, "Terminal");
      if (!consoleWindowSaver.restoreWindow ())
        primaryStage.centerOnScreen ();
    }
    else
    {
      consoleWindowSaver = new WindowSaver (prefs, primaryStage, "Console");
      if (!consoleWindowSaver.restoreWindow ())
      {
        primaryStage.setX (0);
        primaryStage.setY (primaryScreenBounds.getMinY () + 100);
      }
    }

    scene.setOnKeyPressed (new ConsoleKeyPress (consolePane, screen));
    scene.setOnKeyTyped (new ConsoleKeyEvent (screen));

    primaryStage.sizeToScene ();
    primaryStage.setMinWidth (640);
    primaryStage.setMinHeight (480);

    scene.widthProperty ().addListener ((obs, oldVal, newVal) ->
        consolePane.handleResize (newVal.doubleValue (), scene.getHeight ()));
    scene.heightProperty ().addListener ((obs, oldVal, newVal) ->
        consolePane.handleResize (scene.getWidth (), newVal.doubleValue ()));

    primaryStage.show ();
  }

  private void setSpyPane (Screen screen, Site server, Site client)
  {
    spyPane = new SpyPane (screen, server, client, telnetState);

    primaryStage.setScene (new Scene (spyPane));
    primaryStage.setTitle ("Terminal Spy");

    spyWindowSaver = new WindowSaver (prefs, primaryStage, "Spy");
    if (!spyWindowSaver.restoreWindow ())
    {
      primaryStage.setX (0);
      primaryStage.setY (0);

      double height = primaryScreenBounds.getHeight () - 20;
      primaryStage.setHeight (Math.min (height, 1200));
    }

    primaryStage.show ();
    spyPane.startServer ();
  }

  @Override
  public void stop ()
  {
    if (mainframeStage != null)
      mainframeStage.disconnect ();

    if (spyPane != null)
      spyPane.disconnect ();

    if (consolePane != null)
      consolePane.disconnect ();

    if (replayStage != null)
      replayStage.disconnect ();

    savePreferences ();

    if (consoleWindowSaver != null)
      consoleWindowSaver.saveWindow ();

    if (spyWindowSaver != null)
      spyWindowSaver.saveWindow ();

    if (screen != null)
      screen.close ();

    if (pluginsStage != null)
      pluginsStage.closeClassLoader ();
  }

  private void savePreferences ()
  {
    optionStage.savePreferences ();

    if (screen != null)
    {
      prefs.put ("FontName", screen.getFontManager ().getFontName ());
      prefs.put ("FontSize", "" + screen.getFontManager ().getFontSize ());
    }
  }

  private Screen createScreen (TerminalFunction function, Site site)
  {
    // O ciclo de vida da persistencia e do composition root. Sem site nao ha banco - era o
    // que o  dentro do FieldManager decidia, tres camadas abaixo.
    DatasetStore datasetStore = site == null ? DatasetStore.NONE
        : new QueuedDatasetStore (site.getName () + ".db");

    screen = new Screen (screenDimensions, alternateScreenDimensions, prefs, function,
        pluginsStage, site, telnetState, datasetStore);
    return screen;
  }

  public static void main (final String[] arguments)
  {
    Application.launch (arguments);
  }
}