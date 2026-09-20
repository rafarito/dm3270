package com.bytezone.dm3270.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.session.Session;
import com.bytezone.dm3270.streams.SessionLoader;
import com.bytezone.dm3270.streams.TelnetState;
import com.bytezone.dm3270.utilities.Site;
import com.bytezone.dm3270.utilities.SiteValue;

/*
 * A DECISAO do lancamento: qual funcao o usuario pediu, o que falta preencher, que mensagem de
 * erro dar, e em que ordem mandar montar as coisas.
 *
 * Era o Console.startSelectedFunction, 92 linhas em que a decisao e a construcao estavam
 * trancadas juntas. O commit anterior pos a construcao atras da porta LaunchTarget; este tira
 * a decisao de dentro da classe Application do JavaFX. O switch abaixo e o de la, verbatim -
 * mesmos ramos, mesmas guardas, mesmas quatro mensagens, mesma ordem, mesmo escopo de try.
 *
 * ESTA CLASSE NAO TEM UMA LINHA DE JAVAFX, e e isso que o passo compra. Ela fala com a porta,
 * com o LaunchRequest, com utilities.Site (interface, cuja implementacao headless e o
 * SiteValue), com session.Session e com streams.SessionLoader - todos sem toolkit. O que
 * precisaria do JavaFX e a thread da interface, e ela chega como java.util.concurrent.Executor,
 * que o Console preenche com Platform::runLater. E a mesma inversao que o passo 5 fez em
 * streams: depender de uma abstracao do JDK em vez de declarar uma porta so para isso.
 *
 * Ha uma regra de camada nua vigiando esse "sem JavaFX", e nao um baseline congelado, pelo
 * motivo do passo 5: baseline pode ser refrozen, regra nua nao.
 *
 * O LOGGER E O DO Console, DE PROPOSITO. O logback.xml imprime %logger{36}, entao o nome do
 * logger e saida observavel: as duas linhas que vieram junto com o switch -
 * "Couldn't find the server site for {}" e "Error creating replay window" - tem de continuar
 * saindo sob com.bytezone.dm3270.application.Console. E o precedente do DatasetDetails, que
 * usa o logger do ScreenWatcher, e das seis classes novas de database, que usam o do
 * DatabaseThread.
 *
 * POR QUE O SessionLoader FICA AQUI, e nao atras da porta: o try do ramo do replay abrange
 * tudo, da carga da sessao ate a janela de replay aparecer, e a mensagem
 * "Error creating replay window" cobre o caminho inteiro. Se a carga fosse para a porta e a
 * construcao tambem, o escopo do catch teria de ser remontado a mao - e ele e o detalhe mais
 * facil de quebrar deste ramo, sem nenhum teste ate agora.
 *
 * E carregar a sessao gravada num teste e barato e fiel, o que foi medido: SessionLoader.load
 * entrega null como SessionDisplay, e TelnetListener so chama uiThread.execute quando a funcao
 * e TERMINAL. Num replay o Executor nunca e invocado durante a carga, entao um teste pode
 * passar Runnable::run e estar rodando exatamente o mesmo caminho que a aplicacao roda com
 * Platform::runLater.
 *
 * A porta TCP do emulador e o site padrao dele andam JUNTOS aqui. Sao um par acoplado pelo
 * literal 5555 - a porta da constante E a porta do emulador -, e reparti-los entre duas
 * classes seria defeito latente esperando para acontecer.
 */
// -----------------------------------------------------------------------------------//
class LaunchCoordinator
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (Console.class);

  private static final int MAINFRAME_EMULATOR_PORT = 5555;
  private static final Site DEFAULT_MAINFRAME = new SiteValue ("mainframe",
      "localhost", MAINFRAME_EMULATOR_PORT, true, 2, false, false, false, "");

  private final LaunchTarget target;
  private final TelnetState telnetState;
  private final Executor uiThread;

  // ---------------------------------------------------------------------------------//
  LaunchCoordinator (LaunchTarget target, TelnetState telnetState, Executor uiThread)
  // ---------------------------------------------------------------------------------//
  {
    this.target = target;
    this.telnetState = telnetState;
    this.uiThread = uiThread;
  }

  /*
   * O gatilho do botao Connect. O Console o injeta no OptionStage como referencia de metodo,
   * exatamente como injetava o startSelectedFunction.
   *
   * A janela de opcoes e escondida ANTES de o pedido ser lido, e essa ordem e do codigo
   * original: o usuario ve a janela sumir no clique, nao depois da validacao.
   */
  // ---------------------------------------------------------------------------------//
  void launch ()
  // ---------------------------------------------------------------------------------//
  {
    target.hideOptions ();
    String errorMessage = "";

    LaunchRequest request = target.launchRequest ();
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
            Session session = SessionLoader.replay (telnetState, path, uiThread);
            target.useAlternateScreenDimensions (session.getScreenDimensions ());

            Optional<Site> serverSite = target.findServerSite (session.getServerName ());
            if (serverSite.isPresent ())
            {
              Site site = serverSite.get ();
              target.showConsole (TerminalFunction.REPLAY, site);
            }
            else
            {
              logger.warn ("Couldn't find the server site for {}",
                  session.getServerName ());
              target.showConsole (TerminalFunction.REPLAY, null);
            }

            target.showReplay (session, path);
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
          target.applyModel (serverSite);
          target.showConsole (TerminalFunction.TERMINAL, serverSite);
          target.connectConsole ();
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
          target.showSpy (TerminalFunction.SPY, serverSite, clientSite);
        }

        break;

      case TEST:
        if (!optionalClientSite.isPresent ())
          errorMessage = "No client selected";
        else
        {
          Site clientSite = optionalClientSite.get ();
          target.showSpy (TerminalFunction.TEST, DEFAULT_MAINFRAME, clientSite);
          target.showMainframe (MAINFRAME_EMULATOR_PORT);
        }

        break;
    }

    if (!errorMessage.isEmpty () && target.alert (errorMessage))
      target.showOptions ();
  }
}
