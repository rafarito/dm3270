package com.bytezone.dm3270.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.streams.TelnetState;
import com.bytezone.dm3270.utilities.Site;
import com.bytezone.dm3270.utilities.SiteValue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/*
 * OS QUATRO CAMINHOS FELIZES DO LANCAMENTO, pela primeira vez.
 *
 * Ate o passo 12 a rede do lancamento cobria so os ramos que RECUSAM - as quatro mensagens de
 * erro e a janela que reaparece -, e o cabecalho do ConsoleLaunchErrorsTest dizia por que: os
 * caminhos felizes criam uma Screen concreta, abrem arquivo SQLite no diretorio corrente,
 * mostram janela e abrem socket. Com a decisao fora do Console e atras de uma porta que nao
 * nomeia widget nenhum, eles cabem.
 *
 * NAO HA @ExtendWith (JavaFxToolkit.class), e isso e o ponto do passo inteiro. Esta e a
 * segunda classe do caminho de lancamento a nao precisar de toolkit - a primeira foi o
 * ConsoleModelTest - e a unica que exercita o switch de quatro ramos. Nada aqui abre janela.
 *
 * O QUE ELE PROVA, e o que NAO prova. Prova a DECISAO e a SEQUENCIA: qual ramo, com que
 * argumentos, em que ordem, e o que acontece quando um deles falha. Nao prova EFEITO - que o
 * ConsolePane nasceu, que a janela apareceu, que o socket abriu. Isso continua esperando a
 * Screen concreta sair da assinatura de Console.createScreen, e o que o passo 12 fez foi
 * reduzir o bloqueio a duas instrucoes: "new ConsolePane (screen, ...)" e
 * "new SpyPane (screen, ...)".
 *
 * O REPLAY CARREGA UMA SESSAO DE VERDADE, e nao um duble. Foi medido que isso e fiel: o
 * SessionLoader.load entrega null como SessionDisplay, e o TelnetListener so chama
 * uiThread.execute quando a funcao e TERMINAL - num replay o Executor nunca e invocado durante
 * a carga. Entao "Runnable::run" aqui roda exatamente o mesmo caminho que "Platform::runLater"
 * roda na aplicacao. O arquivo e o mf.txt, o mesmo do golden master, copiado para um @TempDir
 * porque o SessionReader le por Path e porque e assim que a concatenacao crua
 * "pasta + barra + arquivo" do ramo do replay fica exercitada de verdade.
 *
 * E O QUE ESSA SESSAO DEVOLVE FOI MEDIDO, nao suposto: getServerName () e "FanDeZhi" e
 * getScreenDimensions () e NULL - o mf.txt nao tem negociacao telnet nenhuma, so registros de
 * dados, entao nao ha Query Reply de onde tirar geometria. O caso que afirma o null esta
 * abaixo e e deliberado: ele congela que um replay ZERA a dimensao alternativa do lancamento
 * anterior, em vez de herda-la.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("LaunchCoordinator - a decisao do lancamento, sem toolkit")
class LaunchCoordinatorPathsTest
// -----------------------------------------------------------------------------------//
{
  private static final String SESSION_RESOURCE = "com/bytezone/dm3270/application/mf.txt";
  private static final String RECORDED_SERVER = "FanDeZhi";

  @TempDir
  private Path spyFolder;

  private RecordingLaunchTarget target;
  private LaunchCoordinator coordinator;
  private Logger consoleLogger;
  private ListAppender<ILoggingEvent> logged;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void createCoordinator ()
  // ---------------------------------------------------------------------------------//
  {
    target = new RecordingLaunchTarget ();
    coordinator = new LaunchCoordinator (target, new TelnetState (), Runnable::run);

    logged = new ListAppender<> ();
    logged.start ();

    // O coordenador loga sob o logger do Console de proposito: o logback.xml imprime
    // %logger{36}, entao o nome do logger e saida observavel.
    consoleLogger = (Logger) LoggerFactory.getLogger (Console.class);
    consoleLogger.addAppender (logged);
  }

  // ---------------------------------------------------------------------------------//
  @AfterEach
  void detachAppender ()
  // ---------------------------------------------------------------------------------//
  {
    consoleLogger.detachAppender (logged);
    logged.stop ();

    // O construtor de ScreenDimensions chama BufferAddress.setScreenWidth (columns), que e
    // estado estatico GLOBAL. O Surefire roda as classes numa JVM so, em ordem nao
    // especificada.
    new ScreenDimensions (24, 80);
  }

  // ---------------------------------------------------------------------------------//
  private static Site site (String name)
  // ---------------------------------------------------------------------------------//
  {
    return new SiteValue (name, "localhost", 23, true, 2, false, false, false, "");
  }

  // ---------------------------------------------------------------------------------//
  private void ask (TerminalFunction function, Site server, Site client, String replayFile)
  // ---------------------------------------------------------------------------------//
  {
    target.request = new LaunchRequest (function, Optional.ofNullable (server),
        Optional.ofNullable (client), spyFolder.toString (), replayFile);
  }

  // ---------------------------------------------------------------------------------//
  private Path recordedSession (String fileName) throws Exception
  // ---------------------------------------------------------------------------------//
  {
    Path path = spyFolder.resolve (fileName);
    try (InputStream in =
        getClass ().getClassLoader ().getResourceAsStream (SESSION_RESOURCE))
    {
      assertNotNull (in, SESSION_RESOURCE + " tem de estar no classpath de teste");
      Files.copy (in, path);
    }

    return path;
  }

  // ---------------------------------------------------------------------------------//
  private String loggedMessages ()
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder text = new StringBuilder ();
    for (ILoggingEvent event : logged.list)
      text.append (event.getLevel ()).append (" ").append (event.getFormattedMessage ())
          .append (System.lineSeparator ());

    return text.toString ();
  }

  // =================================================================================//
  @Nested
  @DisplayName ("os quatro caminhos felizes")
  class TheHappyPaths
  // =================================================================================//
  {
    /*
     * A ordem aqui e comportamento: o modelo e negociado ANTES de a tela nascer, porque o
     * setModel escreve as dimensoes alternativas e o createScreen as le. E o connect vem
     * DEPOIS do painel, porque e o painel que conecta.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("TERMINAL: negocia o modelo, monta o console e SO ENTAO conecta")
    void terminal ()
    // -------------------------------------------------------------------------------//
    {
      ask (TerminalFunction.TERMINAL, site ("prod"), null, "");

      coordinator.launch ();

      assertEquals (List.of ("hideOptions", "launchRequest", "applyModel", "showConsole",
          "connectConsole"), target.calls);
      assertEquals (TerminalFunction.TERMINAL, target.consoleFunction);
      assertEquals (List.of (), target.alerts);
    }

    /*
     * O LaunchRequest carrega a REFERENCIA VIVA ao Site, nunca uma copia - os acessores do
     * SiteForm corrigem o widget quando acham valor invalido, e e o valor corrigido que vai
     * para o disco. Uma copia em qualquer ponto do caminho mudaria o que fica gravado.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("TERMINAL entrega a MESMA instancia de Site aos dois destinatarios")
    void terminalPassesTheLiveSiteReference ()
    // -------------------------------------------------------------------------------//
    {
      Site server = site ("prod");
      ask (TerminalFunction.TERMINAL, server, null, "");

      coordinator.launch ();

      assertSame (server, target.modelSite);
      assertSame (server, target.consoleSite);
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("SPY: monta o painel das duas pontas, e nada alem")
    void spy ()
    // -------------------------------------------------------------------------------//
    {
      Site server = site ("prod");
      Site client = site ("cliente");
      ask (TerminalFunction.SPY, server, client, "");

      coordinator.launch ();

      assertEquals (List.of ("hideOptions", "launchRequest", "showSpy"), target.calls);
      assertEquals (TerminalFunction.SPY, target.spyFunction);
      assertSame (server, target.spyServerSite);
      assertSame (client, target.spyClientSite);
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("TEST: monta o painel e SO ENTAO sobe o emulador de mainframe")
    void test ()
    // -------------------------------------------------------------------------------//
    {
      Site client = site ("cliente");
      ask (TerminalFunction.TEST, null, client, "");

      coordinator.launch ();

      assertEquals (List.of ("hideOptions", "launchRequest", "showSpy", "showMainframe"),
          target.calls);
      assertEquals (TerminalFunction.TEST, target.spyFunction);
      assertSame (client, target.spyClientSite);
    }

    /*
     * A ponta servidora do modo TEST e o emulador embutido, e nao um site do usuario. O par
     * "site padrao" e "porta do emulador" e acoplado pelo literal 5555 - a porta da constante
     * E a porta em que o emulador escuta -, e ate o passo 12 as duas constantes eram privadas
     * do Console e esse acoplamento nao tinha prova nenhuma.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("TEST aponta o painel para o emulador embutido, na porta em que ele sobe")
    void theBuiltInMainframeAndItsPortAreThePair ()
    // -------------------------------------------------------------------------------//
    {
      ask (TerminalFunction.TEST, null, site ("cliente"), "");

      coordinator.launch ();

      assertEquals ("mainframe", target.spyServerSite.getName ());
      assertEquals ("localhost", target.spyServerSite.getURL ());
      assertEquals (target.spyServerSite.getPort (), target.mainframePort);
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("REPLAY: carrega a sessao, acha o servidor, monta o console, abre a janela")
    void replay () throws Exception
    // -------------------------------------------------------------------------------//
    {
      Path path = recordedSession ("spy0001.txt");
      Site recorded = site (RECORDED_SERVER);
      target.serverSiteByName = Optional.of (recorded);
      ask (TerminalFunction.REPLAY, null, null, "spy0001.txt");

      coordinator.launch ();

      assertEquals (List.of ("hideOptions", "launchRequest", "useAlternateScreenDimensions",
          "findServerSite", "showConsole", "showReplay"), target.calls);
      assertEquals (RECORDED_SERVER, target.serverNameAsked);
      assertEquals (TerminalFunction.REPLAY, target.consoleFunction);
      assertSame (recorded, target.consoleSite);
      assertEquals (path, target.replayPath);
      assertNotNull (target.replaySession);
      assertEquals (List.of (), target.alerts);
    }

    /*
     * A dependencia de ordem que nao aparece em assinatura nenhuma: a janela de replay recebe
     * a tela que o showConsole acabou de construir, lendo o campo screen do Console. Inverter
     * as duas entregaria a tela do lancamento ANTERIOR, ou nula.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("REPLAY monta o console ANTES de abrir a janela de replay")
    void replayBuildsTheConsoleBeforeTheReplayWindow () throws Exception
    // -------------------------------------------------------------------------------//
    {
      recordedSession ("spy0001.txt");
      target.serverSiteByName = Optional.of (site (RECORDED_SERVER));
      ask (TerminalFunction.REPLAY, null, null, "spy0001.txt");

      coordinator.launch ();

      assertTrue (target.calls.indexOf ("showConsole") < target.calls.indexOf ("showReplay"),
          "showConsole tem de vir antes de showReplay: " + target.calls);
    }

    /*
     * O ramo que nenhum documento registrava como exercitavel: a sessao gravada nomeia um
     * servidor que nao esta na lista de sites do usuario. A tela e montada SEM site - e por
     * isso sem banco de dados - e o aviso sai sob o logger do Console.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("REPLAY sem site correspondente monta a tela sem site, e avisa")
    void replayWithoutAMatchingSite () throws Exception
    // -------------------------------------------------------------------------------//
    {
      recordedSession ("spy0001.txt");
      target.serverSiteByName = Optional.empty ();
      ask (TerminalFunction.REPLAY, null, null, "spy0001.txt");

      coordinator.launch ();

      assertNull (target.consoleSite);
      assertEquals (TerminalFunction.REPLAY, target.consoleFunction);
      assertTrue (
          loggedMessages ().contains ("WARN Couldn't find the server site for "
              + RECORDED_SERVER),
          loggedMessages ());
    }

    /*
     * MEDIDO, e surpreende: o mf.txt nao tem negociacao telnet nenhuma, so registros de dados,
     * entao nao ha Query Reply de onde tirar geometria e getScreenDimensions () devolve NULL.
     * O coordenador repassa o null assim mesmo.
     *
     * O efeito e util e vale congelar: um replay ZERA a dimensao alternativa em vez de herdar
     * a do lancamento anterior. E o oposto do que acontece com um modelo invalido no ramo
     * TERMINAL, que e a segunda metade do item 1 do BACKLOG-DEFEITOS.md.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("REPLAY repassa a geometria da sessao, mesmo quando ela nao tem nenhuma")
    void replayPassesOnTheSessionGeometryEvenWhenItIsAbsent () throws Exception
    // -------------------------------------------------------------------------------//
    {
      recordedSession ("spy0001.txt");
      target.alternateScreenDimensions = new ScreenDimensions (27, 132);
      target.serverSiteByName = Optional.of (site (RECORDED_SERVER));
      ask (TerminalFunction.REPLAY, null, null, "spy0001.txt");

      coordinator.launch ();

      assertNull (target.alternateScreenDimensions);
    }
  }

  // =================================================================================//
  @Nested
  @DisplayName ("os ramos que recusam")
  class TheRefusals
  // =================================================================================//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("TERMINAL sem servidor")
    void terminalWithoutServer ()
    // -------------------------------------------------------------------------------//
    {
      ask (TerminalFunction.TERMINAL, null, null, "");

      coordinator.launch ();

      assertEquals (List.of ("No server selected"), target.alerts);
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("SPY sem servidor")
    void spyWithoutServer ()
    // -------------------------------------------------------------------------------//
    {
      ask (TerminalFunction.SPY, null, site ("cliente"), "");

      coordinator.launch ();

      assertEquals (List.of ("No server selected"), target.alerts);
    }

    /*
     * A ordem das duas guardas do SPY e comportamento: com o servidor presente e o cliente
     * ausente, a mensagem e a do cliente.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("SPY com servidor e sem cliente reclama do CLIENTE")
    void spyWithServerButNoClient ()
    // -------------------------------------------------------------------------------//
    {
      ask (TerminalFunction.SPY, site ("prod"), null, "");

      coordinator.launch ();

      assertEquals (List.of ("No client selected"), target.alerts);
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("TEST sem cliente")
    void testWithoutClient ()
    // -------------------------------------------------------------------------------//
    {
      ask (TerminalFunction.TEST, site ("prod"), null, "");

      coordinator.launch ();

      assertEquals (List.of ("No client selected"), target.alerts);
    }

    /*
     * O caminho e montado por CONCATENACAO crua, pasta mais barra mais arquivo, e nao por um
     * Paths.get de dois segmentos.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("REPLAY com arquivo inexistente mostra o caminho inteiro")
    void replayWithMissingFile ()
    // -------------------------------------------------------------------------------//
    {
      ask (TerminalFunction.REPLAY, null, null, "naoexiste.txt");

      coordinator.launch ();

      assertEquals (List.of (Path.of (spyFolder + "/naoexiste.txt") + " does not exist"),
          target.alerts);
    }

    /*
     * A asserticao mais valiosa deste grupo, e a razao de ser de lista COMPLETA: um lancamento
     * recusado nao constroi NADA. Qualquer operacao que escapasse para antes da validacao
     * apareceria aqui e derrubaria o caso.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("um lancamento recusado nao constroi nada")
    void aRefusedLaunchBuildsNothing ()
    // -------------------------------------------------------------------------------//
    {
      ask (TerminalFunction.TERMINAL, null, null, "");

      coordinator.launch ();

      assertEquals (List.of ("hideOptions", "launchRequest", "alert", "showOptions"),
          target.calls);
    }
  }

  // =================================================================================//
  @Nested
  @DisplayName ("o epilogo")
  class TheEpilogue
  // =================================================================================//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("o alerta confirmado reabre a janela de opcoes")
    void theAlertReopensTheOptions ()
    // -------------------------------------------------------------------------------//
    {
      target.alertAnswer = true;
      ask (TerminalFunction.TERMINAL, null, null, "");

      coordinator.launch ();

      assertTrue (target.calls.contains ("showOptions"), target.calls.toString ());
    }

    /*
     * O curto-circuito do E logico: quando o alerta e dispensado sem confirmar, a janela NAO
     * volta.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("o alerta dispensado NAO reabre a janela de opcoes")
    void theDismissedAlertDoesNotReopenTheOptions ()
    // -------------------------------------------------------------------------------//
    {
      target.alertAnswer = false;
      ask (TerminalFunction.TERMINAL, null, null, "");

      coordinator.launch ();

      assertEquals (List.of ("hideOptions", "launchRequest", "alert"), target.calls);
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("um lancamento aceito nao alerta nem reabre a janela")
    void anAcceptedLaunchIsSilent ()
    // -------------------------------------------------------------------------------//
    {
      ask (TerminalFunction.SPY, site ("prod"), site ("cliente"), "");

      coordinator.launch ();

      assertEquals (List.of (), target.alerts);
      assertFalse (target.calls.contains ("showOptions"), target.calls.toString ());
    }
  }

  // =================================================================================//
  @Nested
  @DisplayName ("o catch do replay, que cobre mais do que a carga da sessao")
  class TheReplayCatch
  // =================================================================================//
  {
    /*
     * O try do ramo do replay vai da carga da sessao ate a janela aparecer, e a mensagem
     * "Error creating replay window" cobre o caminho inteiro. Estes dois casos sao o que
     * impede alguem de estreitar esse escopo sem perceber - e ate o passo 12 nao havia nenhum.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("uma falha ao montar o console vira o alerta do replay")
    void aFailureBuildingTheConsole () throws Exception
    // -------------------------------------------------------------------------------//
    {
      recordedSession ("spy0001.txt");
      target.serverSiteByName = Optional.of (site (RECORDED_SERVER));
      target.failInShowConsole = new IllegalStateException ("sem toolkit");
      ask (TerminalFunction.REPLAY, null, null, "spy0001.txt");

      coordinator.launch ();

      assertEquals (List.of ("Error creating replay window"), target.alerts);
      assertTrue (target.calls.contains ("showOptions"), target.calls.toString ());
      assertTrue (loggedMessages ().contains ("ERROR Error creating replay window"),
          loggedMessages ());
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("uma falha ao abrir a janela de replay vira o mesmo alerta")
    void aFailureOpeningTheReplayWindow () throws Exception
    // -------------------------------------------------------------------------------//
    {
      recordedSession ("spy0001.txt");
      target.serverSiteByName = Optional.of (site (RECORDED_SERVER));
      target.failInShowReplay = new IllegalStateException ("sem toolkit");
      ask (TerminalFunction.REPLAY, null, null, "spy0001.txt");

      coordinator.launch ();

      assertEquals (List.of ("Error creating replay window"), target.alerts);
      assertTrue (target.calls.contains ("showOptions"), target.calls.toString ());
    }

    /*
     * DEFEITO PRESERVADO, e a rede o achou no primeiro mvn test. E o item 24 do
     * BACKLOG-DEFEITOS.md.
     *
     * Este caso foi escrito afirmando que um arquivo que nao e uma sessao gravada cairia no
     * catch e viraria "Error creating replay window". ELE FALHOU: nao ha alerta nenhum. O
     * SessionReader nao valida formato - o readFile so captura IOException, e o laco de parse
     * simplesmente nao encontra linha que sirva. O resultado e uma sessao VAZIA, com nome de
     * servidor "Unknown", que o coordenador lanca como qualquer outra: a janela de replay abre,
     * sem dados e sem aviso.
     *
     * Congelado, nao corrigido (Regra 1). O que se afirma aqui e o comportamento REAL, medido:
     * o lancamento vai ate o fim, em silencio.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("um arquivo que nao e uma sessao gravada abre um replay VAZIO, em silencio")
    void aFileThatIsNotARecordedSession () throws Exception
    // -------------------------------------------------------------------------------//
    {
      Files.writeString (spyFolder.resolve ("lixo.txt"), "isto nao e uma sessao");
      ask (TerminalFunction.REPLAY, null, null, "lixo.txt");

      coordinator.launch ();

      assertEquals (List.of (), target.alerts, "nenhum alerta - e esse e o defeito");
      assertEquals (List.of ("hideOptions", "launchRequest", "useAlternateScreenDimensions",
          "findServerSite", "showConsole", "showReplay"), target.calls);
      assertEquals ("Unknown", target.serverNameAsked);
      assertNotNull (target.replaySession);
      assertFalse (target.calls.contains ("showOptions"), target.calls.toString ());
    }
  }

  /*
   * A janela de opcoes some no clique, antes de qualquer validacao, e isso vale para os quatro
   * ramos. Nenhuma asserticao anterior a este passo pegava uma inversao aqui.
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("a janela de opcoes some ANTES de o pedido ser lido, nos quatro ramos")
  void theOptionsWindowAlwaysHidesFirst ()
  // ---------------------------------------------------------------------------------//
  {
    for (TerminalFunction function : TerminalFunction.values ())
    {
      target = new RecordingLaunchTarget ();
      coordinator = new LaunchCoordinator (target, new TelnetState (), Runnable::run);
      ask (function, null, null, "");

      coordinator.launch ();

      assertEquals ("hideOptions", target.calls.get (0), "ramo " + function);
      assertEquals ("launchRequest", target.calls.get (1), "ramo " + function);
    }
  }
}
