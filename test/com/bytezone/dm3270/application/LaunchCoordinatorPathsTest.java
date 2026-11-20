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

// Replay usa mf.txt real: SessionLoader.load nao aciona uiThread.execute na carga.
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

    // O coordenador usa o logger de Console porque logback.xml formata %logger{36}.
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

    // Reseta BufferAddress.screenWidth (estado estatico global alterado por ScreenDimensions).
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
    // Ordem obrigatoria: setModel grava as dimensoes lidas por createScreen; o painel conecta.
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

    // Passa a referencia viva do SiteForm (sem copia): seus getters corrigem widgets invalidos
    // antes de o estado ser gravado em disco.
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

    // Modo TEST acopla o site padrao "mainframe" a porta do emulador embutido (5555).
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

    // showReplay le o campo screen recem-atribuido por showConsole (dependencia implicita).
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

    // Sem site na lista, abre a tela sem banco de dados e apenas registra aviso.
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

    // mf.txt nao tem negociacao telnet, entao getScreenDimensions() devolve null.
    // Repassar null zera a dimensao alternativa anterior em vez de herda-la.
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

    // O codigo de producao concatena pasta + "/" + arquivo antes de criar o Path.
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
    // O try do replay cobre da carga ate showReplay; os dois testes preservam esse escopo.
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

    // Comportamento preservado: SessionReader ignora linhas invalidas sem lancar excecao,
    // produzindo uma sessao vazia ("Unknown") e abrindo o replay sem alerta.
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
