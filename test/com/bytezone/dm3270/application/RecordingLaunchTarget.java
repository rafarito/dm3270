package com.bytezone.dm3270.application;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.session.Session;
import com.bytezone.dm3270.utilities.Site;

/*
 * O gravador da porta de lancamento: tudo o que o LaunchCoordinator manda fazer, na ordem em
 * que mandou.
 *
 * TODAS AS DOZE ENTRADAS ESCREVEM NA MESMA LISTA. E isso que faz cada caso afirmar tambem a
 * ORDEM entre operacoes diferentes, e nao so os argumentos de cada uma - e a ordem e metade do
 * que este passo precisa congelar, porque o switch original tinha cinco dependencias de
 * sequencia e nenhuma delas aparece em assinatura de metodo nenhuma. E o idioma dos tres
 * gravadores do ConsoleKeyPressTest e dos dois do caminho de lancamento do passo 11.
 *
 * ELE NAO CONSTROI NADA, e e por isso que existe. Os quatro caminhos felizes do lancamento
 * criam uma Screen concreta, abrem arquivo SQLite no diretorio corrente, mostram janela e
 * abrem socket para um host ou fazem bind de porta - nenhum deles cabe numa suite. O que este
 * gravador entrega e a DECISAO: qual ramo, com que argumentos, em que ordem, e o que acontece
 * quando um deles falha.
 *
 * O QUE ELE PROVA, entao, e o que NAO prova, e isso esta dito aqui em vez de escondido. Prova
 * que o coordenador decide e sequencia certo. NAO prova que o ConsolePane nasce, que a janela
 * aparece ou que o socket abre - isso continua esperando a Screen sair da assinatura de
 * createScreen, que e o passo seguinte. O que o passo 12 fez foi reduzir esse bloqueio a duas
 * instrucoes: "new ConsolePane (screen, ...)" e "new SpyPane (screen, ...)".
 *
 * As respostas sao campos publicos de pacote em vez de metodos de configuracao, porque cada
 * caso ajusta uma ou duas e le o resto. Um construtor de doze argumentos seria pior de ler do
 * que a atribuicao nomeada no proprio caso.
 */
// -----------------------------------------------------------------------------------//
class RecordingLaunchTarget implements LaunchTarget
// -----------------------------------------------------------------------------------//
{
  final List<String> calls = new ArrayList<> ();
  final List<String> alerts = new ArrayList<> ();

  // O que a porta responde. Cada caso ajusta o que precisa.
  LaunchRequest request;
  Optional<Site> serverSiteByName = Optional.empty ();
  boolean alertAnswer = true;

  // Para exercitar o catch do ramo do replay, que hoje nao tem prova nenhuma.
  RuntimeException failInShowConsole;
  RuntimeException failInShowReplay;

  // O que cada operacao recebeu. Identidade importa: o LaunchRequest carrega a referencia
  // VIVA ao Site, e o ramo TERMINAL tem de repassar a MESMA instancia.
  TerminalFunction consoleFunction;
  Site consoleSite;
  TerminalFunction spyFunction;
  Site spyServerSite;
  Site spyClientSite;
  Site modelSite;
  ScreenDimensions alternateScreenDimensions;
  Session replaySession;
  Path replayPath;
  int mainframePort = -1;
  String serverNameAsked;

  // ---------------------------------------------------------------------------------//
  @Override
  public void hideOptions ()
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("hideOptions");
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void showOptions ()
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("showOptions");
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public LaunchRequest launchRequest ()
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("launchRequest");

    return request;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public boolean alert (String message)
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("alert");
    alerts.add (message);

    return alertAnswer;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public Optional<Site> findServerSite (String siteName)
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("findServerSite");
    serverNameAsked = siteName;

    return serverSiteByName;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void applyModel (Site serverSite)
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("applyModel");
    modelSite = serverSite;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void useAlternateScreenDimensions (ScreenDimensions screenDimensions)
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("useAlternateScreenDimensions");
    alternateScreenDimensions = screenDimensions;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void showConsole (TerminalFunction function, Site serverSite)
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("showConsole");
    consoleFunction = function;
    consoleSite = serverSite;

    if (failInShowConsole != null)
      throw failInShowConsole;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void connectConsole ()
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("connectConsole");
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void showReplay (Session session, Path path)
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("showReplay");
    replaySession = session;
    replayPath = path;

    if (failInShowReplay != null)
      throw failInShowReplay;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void showSpy (TerminalFunction function, Site serverSite, Site clientSite)
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("showSpy");
    spyFunction = function;
    spyServerSite = serverSite;
    spyClientSite = clientSite;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void showMainframe (int port)
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("showMainframe");
    mainframePort = port;
  }
}
