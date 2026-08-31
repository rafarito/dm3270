package com.bytezone.dm3270.streams;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

import com.bytezone.dm3270.runtime.Source;
import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.session.Session;
import com.bytezone.dm3270.session.SessionReader;

/*
 * Reconstroi uma sessao a partir de um arquivo gravado.
 *
 * Isto morava dentro da propria Session, em dois construtores e num init () privado, e era a
 * unica razao de o pacote session nomear streams: uma sessao gravada nao precisa de socket
 * nenhum, mas RECONSTRUIR uma precisa, porque os bytes tem de passar pelo mesmo TelnetListener
 * que os interpretaria se viessem do fio. O campo TelnetState da Session existia so para
 * chegar aqui, e nao era lido em mais lugar nenhum.
 *
 * Aqui, do lado de quem tem o listener, o carregamento e um driver de replay - o vizinho do
 * TerminalServer, que faz a mesma coisa com bytes que chegam de verdade. Com ele fora,
 * session -> streams chegou a zero e o ciclo mutuo session <-> streams caiu.
 *
 * O ALGORITMO E MOVIMENTO VERBATIM, e o intercalamento merece explicacao porque nao e obvio:
 * os dois SessionReader leem o MESMO arquivo, um filtrando as linhas do cliente e outro as do
 * servidor, e cada um sabe em que linha do arquivo esta. O laco entrega sempre o lado que
 * estiver atrasado, e assim as duas metades sao reproduzidas na ordem cronologica original.
 *
 * Os dois leitores sao construidos NA ORDEM ORIGINAL - servidor e depois cliente. O construtor
 * do SessionReader le o arquivo e pode lancar DateTimeParseException num cabecalho malformado,
 * entao quem lanca primeiro depende dessa ordem.
 *
 * O rotulo da tela so e recolhido quando o buffer do servidor termina em IAC/EOR - ou seja,
 * quando ele fecha um registro completo -, e continua sendo acrescentado a lista viva que a
 * MainframeStage tambem mexe.
 */
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
