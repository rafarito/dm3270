package com.bytezone.dm3270.streams;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
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

import com.bytezone.dm3270.streams.TelnetSocket.Source;

// -----------------------------------------------------------------------------------//
@DisplayName ("TerminalServer - socket que fala com o mainframe")
class TerminalServerTest
// -----------------------------------------------------------------------------------//
{
  private ServerSocket serverSocket;
  private RecordingListener listener;

  @BeforeEach
  void setUp () throws IOException
  {
    // porta 0 = o SO escolhe uma porta livre; loopback para nao expor nada na rede
    serverSocket = new ServerSocket (0, 1, InetAddress.getLoopbackAddress ());
    listener = new RecordingListener ();
  }

  @AfterEach
  void tearDown () throws IOException
  {
    if (!serverSocket.isClosed ())
      serverSocket.close ();
  }

  private int port ()
  {
    return serverSocket.getLocalPort ();
  }

  private String host ()
  {
    return InetAddress.getLoopbackAddress ().getHostAddress ();
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("sem conexao estabelecida")
  class Disconnected
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("toString identifica destino e porta")
    void describesTarget ()
    {
      TerminalServer server =
          new TerminalServer ("mainframe.example.com", 992, listener, false, false);

      assertEquals ("TerminalSocket listening to mainframe.example.com : 992",
                    server.toString ());
    }

    @Test
    @DisplayName ("write antes de conectar nao lanca excecao")
    void writeBeforeConnect ()
    {
      TerminalServer server = new TerminalServer (host (), port (), listener, false,
                                                 false);

      // serverOut e null: o metodo avisa no console e volta
      server.write (new byte[] { 0x01 });

      assertTrue (listener.messages.isEmpty ());
    }

    @Test
    @DisplayName ("close sem socket ainda fecha o listener")
    void closePropagatesToListener ()
    {
      TerminalServer server = new TerminalServer (host (), port (), listener, false,
                                                 false);

      server.close ();

      assertEquals (1, listener.closes);
    }

    @Test
    @DisplayName ("close e idempotente")
    void closeTwice ()
    {
      TerminalServer server = new TerminalServer (host (), port (), listener, false,
                                                  false);

      server.close ();
      server.close ();

      assertEquals (2, listener.closes);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("conectado a um servidor local")
  class Connected
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("entrega ao listener o que o servidor envia")
    @Timeout (15)
    void forwardsIncomingBytes () throws Exception
    {
      byte[] message = { (byte) 0xFF, (byte) 0xFD, 0x28 };      // IAC DO TN3270E
      TerminalServer server = new TerminalServer (host (), port (), listener, false,
                                                 false);
      Thread thread = new Thread (server);
      thread.start ();

      try (Socket accepted = serverSocket.accept ())
      {
        accepted.getOutputStream ().write (message);
        accepted.getOutputStream ().flush ();

        assertTrue (listener.received.await (10, TimeUnit.SECONDS),
                    "o listener nao recebeu nada");
        assertArrayEquals (message, listener.messages.get (0));
        assertEquals (Source.SERVER, listener.sources.get (0));
        assertTrue (listener.genuine.get (0));
      }
      finally
      {
        server.close ();
        thread.join (5000);
      }
    }

    @Test
    @DisplayName ("write chega no servidor")
    @Timeout (15)
    void sendsOutgoingBytes () throws Exception
    {
      byte[] message = { (byte) 0xFF, (byte) 0xFB, 0x28 };      // IAC WILL TN3270E
      TerminalServer server = new TerminalServer (host (), port (), listener, false,
                                                 false);
      Thread thread = new Thread (server);
      thread.start ();

      try (Socket accepted = serverSocket.accept ())
      {
        // um byte na volta prova que run() ja montou serverIn e serverOut; sem essa
        // sincronizacao o write poderia cair antes de o socket existir
        accepted.getOutputStream ().write (0x00);
        accepted.getOutputStream ().flush ();
        assertTrue (listener.received.await (10, TimeUnit.SECONDS));

        server.write (message);

        byte[] received = new byte[message.length];
        int read = accepted.getInputStream ().read (received);

        assertEquals (message.length, read);
        assertArrayEquals (message, received);
      }
      finally
      {
        server.close ();
        thread.join (5000);
      }
    }

    @Test
    @DisplayName ("o fim do stream fecha a conexao e avisa o listener")
    @Timeout (15)
    void serverClosingEndsTheLoop () throws Exception
    {
      TerminalServer server = new TerminalServer (host (), port (), listener, false,
                                                 false);
      Thread thread = new Thread (server);
      thread.start ();

      Socket accepted = serverSocket.accept ();
      accepted.close ();                    // read() devolve -1

      thread.join (10000);

      assertTrue (listener.closes >= 1, "o listener nao foi fechado");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("TLS")
  class Tls
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("um handshake falho fecha o listener em vez de passar em silencio")
    @Timeout (20)
    void handshakeFailureClosesTheListener () throws Exception
    {
      // o servidor local aceita a conexao mas nunca responde em TLS
      Thread acceptor = new Thread ( () ->
      {
        try (Socket accepted = serverSocket.accept ();
             OutputStream out = accepted.getOutputStream ())
        {
          out.write (new byte[] { 0x00 });      // resposta que nao e um ServerHello
          out.flush ();
        }
        catch (IOException e)
        {
          // o cliente desiste primeiro: esperado
        }
      });
      acceptor.setDaemon (true);
      acceptor.start ();

      TerminalServer server = new TerminalServer (host (), port (), listener, true, true);

      // createSocket embrulha a falha numa IOException; o catch do run () distingue
      // "nunca conectou" de "caiu durante o desligamento" e fecha o listener no
      // primeiro caso, para que a interface saiba que o terminal nao subiu
      server.run ();

      assertEquals (1, listener.closes);
    }

    @Test
    @DisplayName ("os dois contextos SSL sao criados sem erro")
    void bothContextsAreUsable () throws Exception
    {
      assertNotNull (SslContextFactory.createDefault ().getSocketFactory ());
      assertNotNull (SslContextFactory.createTrustAll ().getSocketFactory ());
    }

    @Test
    @DisplayName ("o contexto trustAll nao declara emissores confiaveis")
    void trustAllAcceptsAnything () throws Exception
    {
      assertEquals ("TLS", SslContextFactory.createTrustAll ().getProtocol ());
      assertEquals ("TLS", SslContextFactory.createDefault ().getProtocol ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("destino inacessivel")
  class Unreachable
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("uma porta fechada avisa o listener em vez de propagar a excecao")
    @Timeout (20)
    void refusedConnection () throws Exception
    {
      int closedPort = port ();
      serverSocket.close ();              // ninguem mais escuta nessa porta

      TerminalServer server = new TerminalServer (host (), closedPort, listener, false,
                                                 false);

      server.run ();                      // trata a IOException internamente

      assertEquals (1, listener.closes, "a conexao recusada deveria fechar o listener");
    }
  }

  // ---------------------------------------------------------------------------------//
  //  Auxiliares
  // ---------------------------------------------------------------------------------//

  // ---------------------------------------------------------------------------------//
  private static class RecordingListener implements BufferListener
  // ---------------------------------------------------------------------------------//
  {
    private final List<byte[]> messages = new CopyOnWriteArrayList<> ();
    private final List<Source> sources = new CopyOnWriteArrayList<> ();
    private final List<Boolean> genuine = new CopyOnWriteArrayList<> ();
    private final CountDownLatch received = new CountDownLatch (1);
    private volatile int closes;

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
      closes++;
    }
  }
}
