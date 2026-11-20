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

// Coordena a decisao de lancamento sem depender de JavaFX.
// -----------------------------------------------------------------------------------//
class LaunchCoordinator
// -----------------------------------------------------------------------------------//
{
  // Mantem o nome de logger de Console para compatibilidade dos logs.
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

  // Esconde a janela de opcoes antes de ler os widgets para feedback imediato no clique.
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
