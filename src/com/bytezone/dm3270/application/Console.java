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
   * sitio de chamada.
   *
   * OS DOIS CONTINUAM AQUI depois de a fiacao sair para Stages e a decisao para o
   * LaunchCoordinator, e vale dizer por que em vez de deixar a promessa antiga apodrecendo: o
   * start (Stage) e o ciclo de vida da Application do JavaFX, e os dois Stage que ele monta
   * sao anteriores a qualquer lancamento. Eles viram argumentos de construtor quando o
   * proprio Console deixar de ser quem monta a janela de abertura.
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

  /*
   * O alerta de erro do lancamento, atras de um metodo de pacote pelo mesmo motivo das duas
   * fabricas - mas o motivo aqui e de outra natureza, e por isso e outro commit.
   *
   * Dm3270Utility.showAlert e ESTATICO e chama alert.showAndWait (). Numa suite isso nao
   * falha: TRAVA, esperando um clique que nunca vem. E os tres ramos de erro do lancamento -
   * "No server selected", "No client selected" e "<caminho> does not exist" - sao justamente a
   * parte do switch que nao constroi nada, ou seja, a mais barata e a mais valiosa de
   * congelar.
   *
   * O curto-circuito esta preservado, e hoje mora no LaunchCoordinator: isEmpty () continua
   * sendo avaliado primeiro, e o alerta so aparece quando ha mensagem.
   *
   * Quem chama isto e Stages.alert (String), o metodo da porta. A indirecao existe porque o
   * TestConsole sobrescreve ESTE metodo, e sem isso a suite travaria no showAndWait.
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

  /*
   * A guarda do optionStage e a correcao do item 22 do BACKLOG-DEFEITOS.md, autorizada pelo
   * usuario. O stop () protege seis colaboradores contra nulo e nao protegia o setimo: um
   * Console que nunca chegou ao fim do start (Stage) estourava NullPointerException ao ser
   * parado, e Application.stop () e chamado pelo runtime do JavaFX no fechamento.
   *
   * A guarda do screen ja existia, e cobre o prefs junto: os dois so nascem depois do init (),
   * e sem tela nao ha fonte a gravar.
   */
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
    // O ciclo de vida da persistencia e do composition root. Sem site nao ha banco - era o
    // que o  dentro do FieldManager decidia, tres camadas abaixo.
    DatasetStore datasetStore = site == null ? DatasetStore.NONE
        : new QueuedDatasetStore (site.getName () + ".db");

    screen = new Screen (screenDimensions, alternateScreenDimensions, prefs, function,
        pluginsStage, site, telnetState, datasetStore);
    return screen;
  }

  /*
   * A FIACAO, atras da porta LaunchTarget.
   *
   * Nenhum corpo aqui e novo: cada um e um grupo de instrucoes que estava solto dentro do
   * switch de Console.startSelectedFunction. O que mudou e que a decisao - qual funcao, o que
   * falta, que mensagem dar, em que ordem - saiu para o LaunchCoordinator e fala com um
   * contrato em vez de com "new ConsolePane" e "primaryStage.show ()". O contrato nao nomeia
   * um widget sequer, e e isso que faz a decisao rodar sem toolkit grafico.
   *
   * POR QUE UMA CLASSE INTERNA NAO-ESTATICA, e nao "Console implements LaunchTarget": metodo
   * de interface e implicitamente publico, entao a segunda forma tornaria os doze metodos
   * PUBLICOS numa classe publica. Seriam doze membros de superficie nova, contra os seis de
   * pacote que o passo 11 abriu e contabilizou como passivo. Assim, ZERO alargamento: a classe
   * e privada, os doze metodos so sao alcancaveis por quem tem o tipo LaunchTarget, que e de
   * pacote, e o corpo le e escreve os campos privados do Console diretamente. Em Java 21 isso
   * usa nestmates - o javac nao emite ponte sintetica, entao a prova por javap fica legivel e
   * o JaCoCo nao inventa metodo.
   *
   * O alert () chama showAlert (String), o metodo de PACOTE, e nao Dm3270Utility.showAlert
   * direto. E deliberado: o TestConsole sobrescreve showAlert, e Dm3270Utility.showAlert chama
   * showAndWait (), que numa suite nao falha - TRAVA.
   *
   * As duas escritas de alternateScreenDimensions parecem redundantes e nao sao. applyModel
   * negocia o modelo e deriva as dimensoes; useAlternateScreenDimensions recebe as da sessao
   * gravada. Sao dois escritores de UM campo que sobrevive entre lancamentos na mesma JVM - e
   * e essa sobrevivencia que sustenta a segunda metade do item 1 do BACKLOG-DEFEITOS.md, o
   * modelo invalido que herda a geometria do lancamento anterior. Passar a dimensao como
   * parametro de showConsole seria mais limpo e corrigiria o defeito de carona, o que a Regra 1
   * proibe.
   */
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

    /*
     * Uma expressao so, de proposito: e o que garante que createScreen roda - e atribui o campo
     * screen - antes de setConsolePane ler qualquer coisa. Separar em duas instrucoes nao
     * mudaria nada hoje e tiraria a garantia do compilador.
     */
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

    /*
     * Le o campo screen, que showConsole acabou de atribuir. E a unica dependencia de ordem do
     * ramo do replay que nao aparece na assinatura de metodo nenhum.
     */
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