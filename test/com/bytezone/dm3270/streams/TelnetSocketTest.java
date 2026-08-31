package com.bytezone.dm3270.streams;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.bytezone.dm3270.runtime.Source;

// -----------------------------------------------------------------------------------//
@DisplayName ("TelnetSocket - as duas pontas espelhadas do SpyServer")
class TelnetSocketTest
// -----------------------------------------------------------------------------------//
{
  private ServerSocket serverSocket;
  private final List<Socket> open = new CopyOnWriteArrayList<> ();
  private final List<Thread> threads = new CopyOnWriteArrayList<> ();

  @BeforeEach
  void setUp () throws IOException
  {
    serverSocket = new ServerSocket (0, 2, InetAddress.getLoopbackAddress ());
  }

  @AfterEach
  void tearDown () throws Exception
  {
    for (Thread thread : threads)
    {
      thread.interrupt ();
      thread.join (2000);
    }
    for (Socket socket : open)
      if (!socket.isClosed ())
        socket.close ();
    if (!serverSocket.isClosed ())
      serverSocket.close ();
  }

  // Um par de sockets conectado em loopback: [0] e a ponta do teste, [1] a do codigo.
  private Socket[] pair () throws IOException
  {
    Socket client = new Socket (InetAddress.getLoopbackAddress (),
                                serverSocket.getLocalPort ());
    Socket accepted = serverSocket.accept ();

    open.add (client);
    open.add (accepted);

    return new Socket[] { client, accepted };
  }

  private void start (TelnetSocket telnetSocket)
  {
    Thread thread = new Thread (telnetSocket);
    thread.setDaemon (true);
    threads.add (thread);
    thread.start ();
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("validacao do construtor")
  class Construction
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("recusa source nulo")
    void rejectsNullSource () throws IOException
    {
      Socket socket = pair ()[1];

      IllegalArgumentException e =
          assertThrows (IllegalArgumentException.class,
                        () -> new TelnetSocket (null, socket, new RecordingListener ()));

      assertEquals ("Source cannot be null", e.getMessage ());
    }

    @Test
    @DisplayName ("recusa socket nulo")
    void rejectsNullSocket ()
    {
      IllegalArgumentException e = assertThrows (IllegalArgumentException.class,
          () -> new TelnetSocket (Source.CLIENT, null, new RecordingListener ()));

      assertEquals ("Socket cannot be null", e.getMessage ());
    }

    @Test
    @DisplayName ("recusa listener nulo")
    void rejectsNullListener () throws IOException
    {
      Socket socket = pair ()[1];

      IllegalArgumentException e =
          assertThrows (IllegalArgumentException.class,
                        () -> new TelnetSocket (Source.SERVER, socket, null));

      assertEquals ("Listener cannot be null", e.getMessage ());
    }

    @Test
    @DisplayName ("toString identifica a origem")
    void describesSource () throws IOException
    {
      Socket[] sockets = pair ();

      assertEquals ("TelnetSocket: Source=CLIENT, name=Client",
                    new TelnetSocket (Source.CLIENT, sockets[1],
                                      new RecordingListener ()).toString ());
    }

    @Test
    @DisplayName ("o lado servidor se chama Server")
    void describesServerSource () throws IOException
    {
      Socket[] sockets = pair ();

      assertEquals ("TelnetSocket: Source=SERVER, name=Server",
                    new TelnetSocket (Source.SERVER, sockets[1],
                                      new RecordingListener ()).toString ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("prevent3270E")
  class Prevent3270E
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("so o lado servidor pode bloquear o TN3270E")
    void onlyServerCanPrevent () throws IOException
    {
      TelnetSocket client = new TelnetSocket (Source.CLIENT, pair ()[1],
                                              new RecordingListener ());

      IllegalStateException e =
          assertThrows (IllegalStateException.class, () -> client.prevent3270E (true));

      assertEquals ("Only a SERVER listener can do that", e.getMessage ());
    }

    @Test
    @DisplayName ("o lado servidor aceita ligar e desligar")
    void serverAccepts () throws IOException
    {
      TelnetSocket server = new TelnetSocket (Source.SERVER, pair ()[1],
                                              new RecordingListener ());

      server.prevent3270E (true);
      server.prevent3270E (false);
    }

    @Test
    @DisplayName ("responde WONT no lugar de repassar o DO TN3270E")
    @Timeout (15)
    void answersWontInsteadOfForwarding () throws Exception
    {
      Socket[] serverSide = pair ();
      Socket[] clientSide = pair ();

      RecordingListener serverListener = new RecordingListener ();
      RecordingListener clientListener = new RecordingListener ();

      TelnetSocket server = new TelnetSocket (Source.SERVER, serverSide[1],
                                              serverListener);
      TelnetSocket client = new TelnetSocket (Source.CLIENT, clientSide[1],
                                              clientListener);
      server.link (client);
      server.prevent3270E (true);
      start (server);

      // o mainframe pede DO TN3270E
      serverSide[0].getOutputStream ().write (new byte[] { (byte) 0xFF, (byte) 0xFD,
                                                          0x28 });
      serverSide[0].getOutputStream ().flush ();

      // a resposta WONT volta pelo mesmo socket, sem passar pelo cliente
      byte[] reply = new byte[3];
      assertEquals (3, serverSide[0].getInputStream ().read (reply));
      assertArrayEquals (new byte[] { (byte) 0xFF, (byte) 0xFC, 0x28 }, reply);

      // o cliente recebe a notificacao de que a resposta foi fabricada (MITM)
      assertTrue (clientListener.received.await (10, TimeUnit.SECONDS));
      assertEquals (Source.CLIENT, clientListener.sources.get (0));
      assertFalse (clientListener.genuine.get (0), "deveria estar marcada como MITM");

      // e o pedido original ainda foi reportado como genuino no lado servidor
      assertTrue (serverListener.genuine.get (0));
    }

    @Test
    @DisplayName ("com o bloqueio desligado o pedido segue para o parceiro")
    @Timeout (15)
    void forwardsWhenNotPrevented () throws Exception
    {
      Socket[] serverSide = pair ();
      Socket[] clientSide = pair ();

      TelnetSocket server = new TelnetSocket (Source.SERVER, serverSide[1],
                                              new RecordingListener ());
      TelnetSocket client = new TelnetSocket (Source.CLIENT, clientSide[1],
                                              new RecordingListener ());
      server.link (client);
      start (server);

      byte[] request = { (byte) 0xFF, (byte) 0xFD, 0x28 };
      serverSide[0].getOutputStream ().write (request);
      serverSide[0].getOutputStream ().flush ();

      byte[] forwarded = new byte[3];
      assertEquals (3, clientSide[0].getInputStream ().read (forwarded));
      assertArrayEquals (request, forwarded);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("laco de leitura")
  class ReadLoop
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("repassa ao listener o que chega no socket")
    @Timeout (15)
    void forwardsToListener () throws Exception
    {
      Socket[] sockets = pair ();
      RecordingListener listener = new RecordingListener ();
      TelnetSocket telnetSocket = new TelnetSocket (Source.SERVER, sockets[1], listener);
      start (telnetSocket);

      byte[] message = { 0x01, 0x02, 0x03 };
      sockets[0].getOutputStream ().write (message);
      sockets[0].getOutputStream ().flush ();

      assertTrue (listener.received.await (10, TimeUnit.SECONDS));
      assertArrayEquals (message, listener.messages.get (0));
      assertEquals (Source.SERVER, listener.sources.get (0));
    }

    @Test
    @DisplayName ("sem parceiro os dados apenas sao reportados")
    @Timeout (15)
    void worksWithoutPartner () throws Exception
    {
      Socket[] sockets = pair ();
      RecordingListener listener = new RecordingListener ();
      start (new TelnetSocket (Source.CLIENT, sockets[1], listener));

      sockets[0].getOutputStream ().write (new byte[] { 0x7F });
      sockets[0].getOutputStream ().flush ();

      assertTrue (listener.received.await (10, TimeUnit.SECONDS));
      assertEquals (1, listener.messages.get (0).length);
    }

    @Test
    @DisplayName ("a outra ponta fechando encerra o laco")
    @Timeout (15)
    void remoteCloseEndsLoop () throws Exception
    {
      Socket[] sockets = pair ();
      TelnetSocket telnetSocket = new TelnetSocket (Source.SERVER, sockets[1],
                                                   new RecordingListener ());
      Thread thread = new Thread (telnetSocket);
      thread.setDaemon (true);
      thread.start ();

      sockets[0].close ();                // read() devolve -1 ou lanca IOException

      thread.join (10000);

      assertFalse (thread.isAlive ());
    }

    @Test
    @DisplayName ("close libera o socket e e idempotente")
    void closeIsIdempotent () throws Exception
    {
      Socket[] sockets = pair ();
      TelnetSocket telnetSocket = new TelnetSocket (Source.SERVER, sockets[1],
                                                    new RecordingListener ());

      telnetSocket.close ();
      telnetSocket.close ();

      assertTrue (sockets[1].isClosed ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("link")
  class Link
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("a ligacao e feita nos dois sentidos de uma vez")
    @Timeout (15)
    void isBidirectional () throws Exception
    {
      Socket[] serverSide = pair ();
      Socket[] clientSide = pair ();

      TelnetSocket server = new TelnetSocket (Source.SERVER, serverSide[1],
                                              new RecordingListener ());
      TelnetSocket client = new TelnetSocket (Source.CLIENT, clientSide[1],
                                              new RecordingListener ());

      // link() sai do lado servidor, mas o cliente tambem passa a conhecer o parceiro
      server.link (client);
      start (client);

      byte[] message = { 0x11, 0x22 };
      clientSide[0].getOutputStream ().write (message);
      clientSide[0].getOutputStream ().flush ();

      byte[] forwarded = new byte[2];
      assertEquals (2, serverSide[0].getInputStream ().read (forwarded));
      assertArrayEquals (message, forwarded);
    }
  }

  // ---------------------------------------------------------------------------------//
  private static class RecordingListener implements BufferListener
  // ---------------------------------------------------------------------------------//
  {
    private final List<byte[]> messages = new CopyOnWriteArrayList<> ();
    private final List<Source> sources = new CopyOnWriteArrayList<> ();
    private final List<Boolean> genuine = new CopyOnWriteArrayList<> ();
    private final CountDownLatch received = new CountDownLatch (1);

    @Override
    public void listen (Source targetRole, byte[] message, LocalDateTime dateTime,
        boolean genuine)
    {
      sources.add (targetRole);
      messages.add (message);
      this.genuine.add (genuine);
      received.countDown ();
    }

    @Override
    public void close ()
    {
    }
  }
}
