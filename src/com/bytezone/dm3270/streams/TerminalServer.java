package com.bytezone.dm3270.streams;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.bytezone.dm3270.runtime.Source;
import com.bytezone.dm3270.utilities.Dm3270Utility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// -----------------------------------------------------------------------------------//
public class TerminalServer implements Runnable
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (TerminalServer.class);

  // Limita o connect a 10s para evitar a espera padrao do SO (~21s no Windows) sem retorno.
  private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

  private final int serverPort;
  private final String serverURL;
  private final boolean useTls;
  private final boolean trustAll;
  private Socket serverSocket;
  private InputStream serverIn;
  private OutputStream serverOut;

  private final byte[] buffer = new byte[4096];
  private int bytesRead;
  private volatile boolean running;
  private volatile boolean connected;

  private final BufferListener telnetListener;
  private ConnectionListener connectionListener;
  private final boolean debug = false;

  // ---------------------------------------------------------------------------------//
  public TerminalServer (String serverURL, int serverPort, BufferListener listener,
      boolean useTls, boolean trustAll)
  // ---------------------------------------------------------------------------------//
  {
    this.serverPort = serverPort;
    this.serverURL = serverURL;
    this.telnetListener = listener;
    this.useTls = useTls;
    this.trustAll = trustAll;
  }

  // ---------------------------------------------------------------------------------//
  public void setConnectionListener (ConnectionListener connectionListener)
  // ---------------------------------------------------------------------------------//
  {
    this.connectionListener = connectionListener;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void run ()
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      serverSocket = createSocket ();

      serverIn = serverSocket.getInputStream ();
      serverOut = serverSocket.getOutputStream ();

      connected = true;
      running = true;
      while (running)
      {
        if (Thread.interrupted ())
        {
          logger.info ("TerminalServer interrupted");
          break;
        }

        bytesRead = serverIn.read (buffer);
        if (bytesRead < 0)
        {
          close ();
          break;
        }

        if (Thread.currentThread ().isInterrupted ())
          logger.info ("TerminalServer was interrupted!");

        if (debug)
        {
          logger.debug ("{}\nreading:\n{}", toString (),
                        Dm3270Utility.toHex (buffer, 0, bytesRead));
        }

        byte[] message = new byte[bytesRead];
        System.arraycopy (buffer, 0, message, 0, bytesRead);
        telnetListener.listen (Source.SERVER, message, LocalDateTime.now (), true);
      }
    }
    catch (IOException e)
    {
      // Uma falha antes de a conexao subir (destino inacessivel, handshake TLS recusado)
      // precisa chegar a quem esta esperando: sem isso a interface fica achando que o
      // terminal conectou. Depois do close() a IOException de leitura e so o efeito do
      // desligamento e nao interessa a ninguem.
      if (!connected)
      {
        logger.error ("TerminalServer nao conectou a {}:{} - {}", serverURL, serverPort,
                      e.getMessage (), e);
        close ();

        // Apos close(), para que o erro fique depois do resumo do telnet na tela.
        if (connectionListener != null)
          connectionListener.connectionFailed (serverURL, serverPort, reasonFor (e));
      }
      else if (running)
      {
        logger.error ("TerminalServer erro de leitura", e);
        close ();
      }
    }
  }

  // Abre TCP com timeout antes do TLS para nao relatar falha de rede como erro de TLS.
  // ---------------------------------------------------------------------------------//
  private Socket createSocket () throws IOException
  // ---------------------------------------------------------------------------------//
  {
    Socket socket = new Socket ();
    socket.connect (new InetSocketAddress (serverURL, serverPort), CONNECT_TIMEOUT_MILLIS);

    if (!useTls)
      return socket;

    try
    {
      SSLSocketFactory factory = trustAll
          ? (SSLSocketFactory) SslContextFactory.createTrustAll ().getSocketFactory ()
          : (SSLSocketFactory) SslContextFactory.createDefault ().getSocketFactory ();

      SSLSocket sslSocket =
          (SSLSocket) factory.createSocket (socket, serverURL, serverPort, true);
      sslSocket.setEnabledProtocols (new String[] { "TLSv1.2", "TLSv1.3" });
      sslSocket.startHandshake ();
      return sslSocket;
    }
    catch (Exception e)
    {
      // o TCP ja subiu: se o TLS falha, o socket precisa ser fechado aqui, senao vaza
      closeQuietly (socket);

      throw new IOException ("Falha no handshake TLS com " + serverURL
          + ":" + serverPort + " — " + e.getMessage (), e);
    }
  }

  // ---------------------------------------------------------------------------------//
  private static void closeQuietly (Socket socket)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      socket.close ();
    }
    catch (IOException suppressed)
    {
      logger.debug ("Falha ao fechar o socket apos erro de TLS", suppressed);
    }
  }

  // ---------------------------------------------------------------------------------//
  private static String reasonFor (IOException e)
  // ---------------------------------------------------------------------------------//
  {
    if (e instanceof java.net.UnknownHostException)
      return "nome do servidor nao encontrado";

    if (e instanceof java.net.SocketTimeoutException)
      return "o servidor nao respondeu no tempo limite - verifique se esta ligado e "
          + "alcancavel pela rede";

    if (e instanceof java.net.ConnectException)
    {
      String message = e.getMessage () == null ? "" : e.getMessage ().toLowerCase ();

      if (message.contains ("refused") || message.contains ("recusad"))
        return "conexao recusada - o servidor esta alcancavel, mas nada escuta nessa porta";

      if (message.contains ("timed out") || message.contains ("esgotad"))
        return "o servidor nao respondeu - verifique se esta ligado e alcancavel pela rede";

      return "nao foi possivel abrir a conexao";
    }

    if (e instanceof java.net.NoRouteToHostException)
      return "sem rota para o servidor";

    return e.getMessage () == null ? e.getClass ().getSimpleName () : e.getMessage ();
  }

  // ---------------------------------------------------------------------------------//
  synchronized void write (byte[] buffer)
  // ---------------------------------------------------------------------------------//
  {
    if (serverOut == null)
    {
      // the no-op may come here if the program is not closed after disconnection
      logger.warn ("serverOut is null in TerminalServer");
      return;
    }

    try
    {
      serverOut.write (buffer);
      serverOut.flush ();
    }
    catch (IOException e)
    {
      logger.error ("Erro de escrita in TerminalServer", e);
    }

    if (debug)
    {
      logger.debug ("{}\nwriting:\n{}", toString (), Dm3270Utility.toHex (buffer));
    }
  }

  // ---------------------------------------------------------------------------------//
  public void close ()
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      running = false;

      serverIn = null;
      serverOut = null;

      if (serverSocket != null && !serverSocket.isClosed ())
        serverSocket.close ();

      if (telnetListener != null)
        telnetListener.close ();
    }
    catch (IOException e)
    {
      logger.error ("Erro ao fechar TerminalServer", e);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    return String.format ("TerminalSocket listening to %s : %d", serverURL, serverPort);
  }
}