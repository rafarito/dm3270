package com.bytezone.dm3270.streams;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.bytezone.dm3270.streams.TelnetSocket.Source;
import com.bytezone.dm3270.utilities.Dm3270Utility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// -----------------------------------------------------------------------------------//
public class TerminalServer implements Runnable
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (TerminalServer.class);

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
        logger.error ("TerminalServer nao conectou: {}", e.getMessage (), e);
        close ();
      }
      else if (running)
      {
        logger.error ("TerminalServer erro de leitura", e);
        close ();
      }
    }
  }

  // ---------------------------------------------------------------------------------//
  private Socket createSocket () throws IOException
  // ---------------------------------------------------------------------------------//
  {
    if (!useTls)
    {
      Socket s = new Socket ();
      s.connect (new InetSocketAddress (serverURL, serverPort));
      return s;
    }

    try
    {
      SSLSocketFactory factory = trustAll
          ? (SSLSocketFactory) SslContextFactory.createTrustAll ().getSocketFactory ()
          : (SSLSocketFactory) SslContextFactory.createDefault ().getSocketFactory ();

      SSLSocket sslSocket = (SSLSocket) factory.createSocket (serverURL, serverPort);
      sslSocket.setEnabledProtocols (new String[] { "TLSv1.2", "TLSv1.3" });
      sslSocket.startHandshake ();
      return sslSocket;
    }
    catch (Exception e)
    {
      throw new IOException ("Falha no handshake TLS com " + serverURL
          + ":" + serverPort + " — " + e.getMessage (), e);
    }
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