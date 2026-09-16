package com.bytezone.dm3270.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import javafx.scene.control.MenuItem;
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

    pluginsStage = createPluginsStage ();
    optionStage = createOptionStage (pluginsStage.getEditMenuItem ());

    primaryScreenBounds = javafx.stage.Screen.getPrimary ().getVisualBounds ();

    optionStage.setOnConnect (this::startSelectedFunction);
    optionStage.show ();
  }

  /*
   * As duas fabricas abaixo sao DELEGACAO PURA, e existem para que a rede do caminho de
   * lancamento possa substituir os dois colaboradores que o start constroi.
   *
   * O PluginsStage e o caso duro: o construtor publico dele delega a
   * "this (prefs, Paths.get (PLUGINS_DIR).toAbsolutePath ())", ou seja, varre a pasta
   * plugins/ da maquina de quem roda a suite - que esta no .gitignore e portanto tem
   * conteudo diferente em cada checkout. Sem esta fabrica a rede seria dependente de
   * maquina. E o mesmo custo que o OptionStage recusou por escrito, em OptionStage:111-116.
   *
   * O OptionStage e o outro: ele e uma Stage, e o start termina em show (). Um teste que
   * dirigisse o start de verdade abriria janela.
   *
   * A delegacao preserva comportamento por inspecao: mesmos argumentos, mesma ordem, mesmo
   * ponto de avaliacao - pluginsStage.getEditMenuItem () continua sendo avaliado onde era, no
   * sitio de chamada. Os dois metodos somem quando o composition root assumir a fiacao: viram
   * argumentos do construtor do colaborador de montagem.
   */
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

    if (!errorMessage.isEmpty () && showAlert (errorMessage))
      optionStage.show ();
  }

  /*
   * O alerta de erro do lancamento, atras de um metodo de pacote pelo mesmo motivo das duas
   * fabricas - mas o motivo aqui e de outra natureza, e por isso e outro commit.
   *
   * Dm3270Utility.showAlert e ESTATICO e chama alert.showAndWait () (Dm3270Utility:286).
   * Numa suite isso nao falha: TRAVA, esperando um clique que nunca vem. E os tres ramos de
   * erro do startSelectedFunction - "No server selected", "No client selected" e
   * "<caminho> does not exist" - sao justamente a parte do switch que nao constroi nada, ou
   * seja, a mais barata e a mais valiosa de congelar. Ate aqui eles so tinham o roteiro de
   * validacao manual do passo 7.
   *
   * O curto-circuito esta preservado: isEmpty () continua sendo avaliado primeiro, e o
   * alerta so aparece quando ha mensagem.
   *
   * Sai quando o composition root assumir a fiacao: vira uma porta de notificacao, no
   * padrao das outras portas desta refatoracao.
   */
  // ---------------------------------------------------------------------------------//
  boolean showAlert (String message)
  // ---------------------------------------------------------------------------------//
  {
    return Dm3270Utility.showAlert (message);
  }

  /*
   * DOIS DEFEITOS PRESERVADOS DE PROPOSITO AQUI, e os dois sao o item 1 do
   * BACKLOG-DEFEITOS.md. O switch original nao tinha break no case 5, entao um modelo 5 - que
   * e valido, 27x132 - configurava-se corretamente e SO ENTAO caia no default e reclamava de
   * si mesmo. E o default nao atribui alternateScreenDimensions, que e campo de instancia
   * reaproveitado entre lancamentos na mesma JVM: um modelo invalido herda o valor do
   * lancamento anterior.
   *
   * A ordem importa e esta preservada: configura primeiro, reclama depois.
   */
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

    if (terminalModel.isEmpty () || model == 5)         // o case 5 sem break, item 1 do backlog
      logger.warn ("Invalid model number: {}", model);
  }

  /*
   * Os dois acessores abaixo, e o setModel de pacote logo acima, existem para que o
   * ConsoleModelTest possa observar o item 1 do BACKLOG-DEFEITOS.md - o modelo 5, que se
   * configura corretamente e SO ENTAO reclama de si mesmo, e o modelo invalido, que herda a
   * dimensao do lancamento anterior. O passo 8 mediu que esse defeito estava DESCONGELADO: o
   * backlog afirmava que a caracterizacao de setModel o cobria, e ela nao existia.
   *
   * Sao os tres unicos membros nao-private desta classe alem de init, start, stop e main, e
   * sao PASSIVO, na contabilidade do relatorio. A diferenca em relacao aos 21 da fase 2 e que
   * este e autoliquidavel: os tres morrem quando o composition root levar o setModel para
   * fora do Console e passar a injetar o TelnetState em vez de construi-lo aqui.
   */
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