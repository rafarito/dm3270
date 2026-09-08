package com.bytezone.dm3270.streams;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Executor;

import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.telnet.TelnetCommand;
import com.bytezone.dm3270.telnet.TelnetSubcommand;
import com.bytezone.dm3270.telnet.TerminalTypeSubcommand;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// -----------------------------------------------------------------------------------//
public class MainframeServer implements Runnable
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (MainframeServer.class);

  private final int port;
  private final byte[] buffer = new byte[4096];
  private byte[] tempBuffer;
  private volatile boolean running;

  private InputStream clientIn;
  private OutputStream clientOut;
  private ServerSocket clientServerSocket;
  private Socket clientSocket;

  private Mainframe mainframe;

  /*
   * Para onde mandar o que so pode acontecer na thread da interface.
   *
   * Eram chamadas diretas a Platform.runLater, e por causa delas o pacote streams - que le
   * bytes de um socket - nomeava o toolkit grafico. O composition root passa
   * Platform::runLater, que ja satisfaz Executor; um teste passa Runnable::run e o caminho
   * roda headless.
   *
   * E um tipo do JDK de proposito, e nao uma interface nova: a porta teria um metodo so, com
   * a assinatura exata do Executor, e inventa-la seria cerimonia sem ganho nenhum.
   */
  private final Executor uiThread;

  // ---------------------------------------------------------------------------------//
  public MainframeServer (int port, Executor uiThread)
  // ---------------------------------------------------------------------------------//
  {
    this.port = port;
    this.uiThread = uiThread;
  }

  // ---------------------------------------------------------------------------------//
  public void setStage (Mainframe mainframe)
  // ---------------------------------------------------------------------------------//
  {
    this.mainframe = mainframe;     // MainframeStage
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void run ()
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      clientServerSocket = new ServerSocket (port);     // usually 5555
      clientSocket = clientServerSocket.accept ();      // blocks

      clientIn = clientSocket.getInputStream ();
      clientOut = clientSocket.getOutputStream ();

      writeAll (TelnetCommand.IAC, TelnetCommand.DO, TelnetSubcommand.TERMINAL_TYPE);
      readAtLeast (1);

      writeAll (TelnetCommand.IAC, TelnetCommand.SB, TelnetSubcommand.TERMINAL_TYPE,
          TerminalTypeSubcommand.OPTION_SEND, TelnetCommand.IAC, TelnetCommand.SE);
      readAtLeast (1);

      writeAll (TelnetCommand.IAC, TelnetCommand.DO, TelnetSubcommand.EOR);
      writeAll (TelnetCommand.IAC, TelnetCommand.WILL, TelnetSubcommand.EOR);
      readAtLeast (6);

      writeAll (TelnetCommand.IAC, TelnetCommand.DO, TelnetSubcommand.BINARY);
      writeAll (TelnetCommand.IAC, TelnetCommand.WILL, TelnetSubcommand.BINARY);
      readAtLeast (6);

      // send Query to find out what the terminal supports
      byte[] cmd = { (byte) 0xF3, 0x00, 0x06, 0x40, 0x00,    //
                     (byte) 0xF1, (byte) 0xC0, 0x00, 0x05,   // note WCC = 0xC0
                     0x01, (byte) 0xFF, (byte) 0xFF,         // note double FF
                     0x02, (byte) 0xFF, (byte) 0xEF };

      write (cmd);

      running = true;
      while (running)
      {
        if (Thread.interrupted ())
        {
          logger.info ("MainframeServer interrupted");
          break;
        }

        int bytesRead = clientIn.read (buffer);     // assumes all in one buffer !!
        if (mainframe != null && buffer[0] != TelnetCommand.IAC)
        {
          bytesRead = sanitise (buffer, bytesRead);       // remove 0xFF bytes
          Command command = Command.getReply (buffer, 0, bytesRead);
          uiThread.execute ( () -> mainframe.receiveCommand (command));
        }
      }
    }
    catch (SocketException e)     // caused by closing the clientServerSocket
    {
      logger.info ("Connection attempt cancelled", e);
    }
    catch (IOException e)
    {
      logger.error ("Error in MainframeServer", e);
      close ();
    }

    logger.info ("Mainframe Server closed");
  }

  // ---------------------------------------------------------------------------------//
  private int sanitise (byte[] buffer, int bytesRead)
  // ---------------------------------------------------------------------------------//
  {
    if (tempBuffer != null)
    {
      // prepend it to buffer
      byte[] newBuffer = new byte[tempBuffer.length + bytesRead];
      System.arraycopy (tempBuffer, 0, newBuffer, 0, tempBuffer.length);
      System.arraycopy (buffer, 0, newBuffer, tempBuffer.length, bytesRead);

      tempBuffer = null;
      buffer = newBuffer;
      bytesRead = newBuffer.length;
    }

    if (buffer[bytesRead - 1] != (byte) 0xEF && buffer[bytesRead - 2] != (byte) 0xFF)
    {
      logger.warn ("Unfinished buffer");
      tempBuffer = new byte[bytesRead];
      System.arraycopy (buffer, 0, tempBuffer, 0, bytesRead);
      return 0;
    }

    bytesRead -= 2;                               // ignore the 0xFF 0xEF at the end
    byte lastByte = 0;
    byte IAC = (byte) 0xFF;
    int ptr = 0;

    for (int i = 0; i < bytesRead; i++)
    {
      if (ptr != i)
        buffer[ptr] = buffer[i];

      if (buffer[i] == IAC & lastByte == IAC)     // doubled-up 0xFF
        lastByte = 0;                             // don't flag it again
      else
      {
        ptr++;
        lastByte = buffer[i];
      }
    }

    return ptr;
  }

  // ---------------------------------------------------------------------------------//
  private void readAtLeast (int bytesToRead) throws IOException
  // ---------------------------------------------------------------------------------//
  {
    while (bytesToRead > 0)
      bytesToRead -= clientIn.read (buffer);      // blocks
  }

  // ---------------------------------------------------------------------------------//
  private void writeAll (byte... buffer) throws IOException
  // ---------------------------------------------------------------------------------//
  {
    write (buffer);
  }

  // ---------------------------------------------------------------------------------//
  public void write (byte[] buffer)
  // ---------------------------------------------------------------------------------//
  {
    if (clientOut != null)
    {
      try
      {
        clientOut.write (buffer);
        clientOut.flush ();
      }
      catch (IOException e)
      {
        logger.error ("Error writing to client", e);
      }
    }
  }

  // ---------------------------------------------------------------------------------//
  public void sendCommand (Command command)
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = command.getTelnetData ();
    write (buffer);
  }

  // ---------------------------------------------------------------------------------//
  public void close ()
  // ---------------------------------------------------------------------------------//
  {
    running = false;

    if (clientSocket != null)
      try
      {
        clientSocket.close ();
        clientSocket = null;
      }
      catch (IOException e)
      {
        logger.error ("Error closing client socket", e);
      }

    if (clientServerSocket != null)
      try
      {
        clientServerSocket.close ();
        clientServerSocket = null;
      }
      catch (IOException e)
      {
        logger.error ("Error closing client server socket", e);
      }
  }
}