package com.bytezone.dm3270.application;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.session.Session;
import com.bytezone.dm3270.utilities.Site;

// Gravador de LaunchTarget: registra operacoes em ordem numa lista unica sem efeitos reais.
// -----------------------------------------------------------------------------------//
class RecordingLaunchTarget implements LaunchTarget
// -----------------------------------------------------------------------------------//
{
  final List<String> calls = new ArrayList<> ();
  final List<String> alerts = new ArrayList<> ();

  LaunchRequest request;
  Optional<Site> serverSiteByName = Optional.empty ();
  boolean alertAnswer = true;

  RuntimeException failInShowConsole;
  RuntimeException failInShowReplay;

  // Argumentos capturados; o ramo terminal deve repassar a mesma instancia de Site.
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
