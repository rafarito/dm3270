package com.bytezone.dm3270.application;

import java.nio.file.Path;
import java.util.Optional;
import java.util.prefs.Preferences;

import com.bytezone.dm3270.datasets.DatasetStore;
import com.bytezone.dm3270.database.QueuedDatasetStore;
import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.runtime.TerminalModel;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.plugins.PluginsStage;
import com.bytezone.dm3270.session.Session;
import com.bytezone.dm3270.streams.TelnetState;
import com.bytezone.dm3270.utilities.Dm3270Utility;
import com.bytezone.dm3270.utilities.Site;
import com.bytezone.dm3270.utilities.WindowSaver;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Console extends Application
{
  private static final Logger logger = LoggerFactory.getLogger (Console.class);

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

  private final LaunchTarget stages = new Stages ();
  private LaunchCoordinator coordinator;

  // JavaFX exige construtor publico sem argumentos; init() preenche prefs.
  public Console ()
  {
  }

  // Injeta prefs nos testes sem passar por init() / Application.launch().
  Console (Preferences prefs)
  {
    this.prefs = prefs;
  }

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

    pluginsStage = createPluginsStage ();
    optionStage = createOptionStage (pluginsStage.getEditMenuItem ());

    primaryScreenBounds = javafx.stage.Screen.getPrimary ().getVisualBounds ();

    coordinator = new LaunchCoordinator (stages, telnetState, Platform::runLater);
    optionStage.setOnConnect (coordinator::launch);
    optionStage.show ();
  }

  // Fabricas de pacote para os testes substituirem Stages que acessam disco ou abrem GUI.
  // ---------------------------------------------------------------------------------//
  PluginsStage createPluginsStage ()
  // ---------------------------------------------------------------------------------//
  {
    return new PluginsStage (prefs);
  }

  // ---------------------------------------------------------------------------------//
  OptionStage createOptionStage (MenuItem pluginsEditMenuItem)
  // ---------------------------------------------------------------------------------//
  {
    return new OptionStage (prefs, pluginsEditMenuItem);
  }

  // Hook de pacote para testes evitarem Dm3270Utility.showAlert (showAndWait bloqueante).
  // ---------------------------------------------------------------------------------//
  boolean showAlert (String message)
  // ---------------------------------------------------------------------------------//
  {
    return Dm3270Utility.showAlert (message);
  }

  // Comportamento legado preservado: modelo 5 configura 27x132 mas ainda loga aviso;
  // modelos invalidos mantem o alternateScreenDimensions do lancamento anterior.
  void setModel (Site serverSite)
  {
    int model = serverSite.getModel ();
    logger.debug ("model: {}", model);

    Optional<TerminalModel> terminalModel = TerminalModel.forNumber (model);

    if (terminalModel.isPresent ())
    {
      TerminalModel found = terminalModel.get ();
      alternateScreenDimensions = new ScreenDimensions (found.rows (), found.columns ());
      telnetState.setDoDeviceType (model);
    }

    if (terminalModel.isEmpty () || model == 5)
      logger.warn ("Invalid model number: {}", model);
  }

  // Acessores de pacote expostos para os testes de setModel.
  // ---------------------------------------------------------------------------------//
  TelnetState telnetState ()
  // ---------------------------------------------------------------------------------//
  {
    return telnetState;
  }

  // ---------------------------------------------------------------------------------//
  ScreenDimensions alternateScreenDimensions ()
  // ---------------------------------------------------------------------------------//
  {
    return alternateScreenDimensions;
  }

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

  // stop() pode rodar mesmo se start() nao concluiu a criacao de optionStage.
  private void savePreferences ()
  {
    if (optionStage != null)
      optionStage.savePreferences ();

    if (screen != null)
    {
      prefs.put ("FontName", screen.getFontManager ().getFontName ());
      prefs.put ("FontSize", "" + screen.getFontManager ().getFontSize ());
    }
  }

  private Screen createScreen (TerminalFunction function, Site site)
  {
    DatasetStore datasetStore = site == null ? DatasetStore.NONE
        : new QueuedDatasetStore (site.getName () + ".db");

    screen = new Screen (screenDimensions, alternateScreenDimensions, prefs, function,
        pluginsStage, site, telnetState, datasetStore);
    return screen;
  }

  // Classe interna para implementar LaunchTarget sem expor seus 12 metodos como publicos.
  // ---------------------------------------------------------------------------------//
  private final class Stages implements LaunchTarget
  // ---------------------------------------------------------------------------------//
  {
    @Override
    public void hideOptions ()
    {
      optionStage.hide ();
    }

    @Override
    public void showOptions ()
    {
      optionStage.show ();
    }

    @Override
    public LaunchRequest launchRequest ()
    {
      return optionStage.getLaunchRequest ();
    }

    @Override
    public boolean alert (String message)
    {
      return showAlert (message);
    }

    @Override
    public Optional<Site> findServerSite (String siteName)
    {
      return optionStage.findServerSite (siteName);
    }

    @Override
    public void applyModel (Site serverSite)
    {
      setModel (serverSite);
    }

    @Override
    public void useAlternateScreenDimensions (ScreenDimensions screenDimensions)
    {
      alternateScreenDimensions = screenDimensions;
    }

    @Override
    public void showConsole (TerminalFunction function, Site serverSite)
    {
      setConsolePane (createScreen (function, serverSite), serverSite);
    }

    @Override
    public void connectConsole ()
    {
      consolePane.connect ();
    }

    // Depende de showConsole() ter atribuido o campo screen via createScreen().
    @Override
    public void showReplay (Session session, Path path)
    {
      replayStage = new ReplayStage (session, path, prefs, screen);
      replayStage.show ();
    }

    @Override
    public void showSpy (TerminalFunction function, Site serverSite, Site clientSite)
    {
      setSpyPane (createScreen (function, null), serverSite, clientSite);
    }

    @Override
    public void showMainframe (int port)
    {
      mainframeStage = new MainframeStage (telnetState, port);
      mainframeStage.show ();
      mainframeStage.startServer ();
    }
  }

  public static void main (final String[] arguments)
  {
    Application.launch (arguments);
  }
}