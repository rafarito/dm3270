package com.bytezone.dm3270.streams;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

import com.bytezone.dm3270.runtime.Source;
import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.session.Session;
import com.bytezone.dm3270.session.SessionReader;

// Reconstroi uma sessao gravada intercalando os leitores de servidor e cliente por linha.
// Constroi server antes de client: SessionReader faz parse no construtor e pode lancar.
// -----------------------------------------------------------------------------------//
public class SessionLoader
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  private SessionLoader ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // chamado pelo Console.startSelectedFunction ()
  // ---------------------------------------------------------------------------------//
  public static Session replay (TelnetState telnetState, Path path, Executor uiThread)
      throws Exception
  // ---------------------------------------------------------------------------------//
  {
    SessionReader server = new SessionReader (Source.SERVER, path);
    SessionReader client = new SessionReader (Source.CLIENT, path);

    return load (telnetState, TerminalFunction.REPLAY, client, server, uiThread);
  }

  // chamado pela MainframeStage.prepareButtons ()
  // ---------------------------------------------------------------------------------//
  public static Session test (TelnetState telnetState, List<String> lines,
      Executor uiThread) throws Exception
  // ---------------------------------------------------------------------------------//
  {
    SessionReader server = new SessionReader (Source.SERVER, lines);
    SessionReader client = new SessionReader (Source.CLIENT, lines);

    return load (telnetState, TerminalFunction.TEST, client, server, uiThread);
  }

  // ---------------------------------------------------------------------------------//
  private static Session load (TelnetState telnetState, TerminalFunction function,
      SessionReader client, SessionReader server, Executor uiThread) throws Exception
  // ---------------------------------------------------------------------------------//
  {
    Session session = new Session (function);

    TelnetListener clientTelnetListener =
        new TelnetListener (Source.CLIENT, session, function, null, telnetState,
            uiThread);
    TelnetListener serverTelnetListener =
        new TelnetListener (Source.SERVER, session, function, null, telnetState,
            uiThread);

    while (client.nextLineNo () != server.nextLineNo ())
      if (client.nextLineNo () < server.nextLineNo ())
        while (client.nextLineNo () < server.nextLineNo ())
          clientTelnetListener.listen (Source.CLIENT, client.nextBuffer (),
              client.getDateTime (), client.isGenuine ());
      else
        while (client.nextLineNo () > server.nextLineNo ())
        {
          byte[] buffer = server.nextBuffer ();
          serverTelnetListener.listen (Source.SERVER, buffer, server.getDateTime (),
              server.isGenuine ());
          if (buffer[buffer.length - 2] == (byte) 0xFF
              && buffer[buffer.length - 1] == (byte) 0xEF)
            session.getLabels ().add (server.getLabel ());
        }

    return session;
  }
}
