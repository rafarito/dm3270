package com.bytezone.dm3270.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.commands.ReadStructuredFieldCommand;
import com.bytezone.dm3270.commands.WriteCommand;
import com.bytezone.dm3270.extended.CommandHeader;
import com.bytezone.dm3270.orders.Order;
import com.bytezone.dm3270.replyfield.QueryReplyField;
import com.bytezone.dm3270.session.SessionRecord.SessionRecordType;
import com.bytezone.dm3270.runtime.Source;
import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.structuredfields.StructuredField;
import com.bytezone.dm3270.utilities.Dm3270Utility;

/*
 * Caracterizacao da Session - o acumulado de uma conversa TN3270, gravada ou espionada.
 *
 * A classe guarda tres coisas que nao sao a mesma: os registros da conversa, a identificacao
 * de quem esta nas duas pontas, e os widgets que mostram isso - um ObservableList e um Label,
 * com um Platform.runLater no meio. Os testes abaixo congelam o comportamento de HOJE, antes
 * de as tres serem separadas.
 *
 * O que precisa sobreviver, e nao e obvio:
 *
 *   - a identificacao acontece DENTRO de add (), uma vez por lado. Assim que o nome do
 *     servidor e conhecido, nenhuma mensagem posterior e examinada de novo - trocar isso
 *     mudaria o que a barra de titulo mostra numa sessao longa;
 *   - o nome do cliente e as dimensoes de tela sao lidos so de mensagens do CLIENTE, e o nome
 *     do servidor so de mensagens do SERVIDOR;
 *   - antes de qualquer identificacao os dois nomes leem "Unknown", mas o Label nasce VAZIO -
 *     o texto so e escrito quando um dos dois e reconhecido. Um Label preenchido com
 *     "Unknown : Unknown" desde o inicio seria mudanca visivel no modo Spy;
 *   - save () grava o que o SessionReader sabe reler: e um ciclo fechado, e e assim que os
 *     arquivos de replay sao produzidos e consumidos. Por isso os testes de persistencia sao
 *     de ida e volta, e nao de texto literal;
 *   - safeSave () embaralha o texto digitado pelo usuario para 0x7B antes de gravar, e volta
 *     a gravar normalmente depois. O flag e de instancia, nao de chamada.
 *
 * REPARE NO QUE ESTA CLASSE NAO TEM MAIS: nao ha @ExtendWith (JavaFxToolkit.class). Ate o
 * commit que levou o Label para a borda, a Session construia um Control no proprio campo, e
 * nenhum destes testes podia rodar sem toolkit grafico. Hoje a identificacao das duas pontas, o
 * acumulado e a persistencia sao exercitados headless, e essa ausencia e o teste principal.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("Session - a conversa acumulada, e quem esta nas duas pontas")
class SessionTest
// -----------------------------------------------------------------------------------//
{
  private static final Charset CP1047 = Charset.forName ("CP1047");
  private static final LocalDateTime WHEN = LocalDateTime.of (2024, 1, 15, 10, 30, 42);

  // ---------------------------------------------------------------------------------//
  private static Session session ()
  // ---------------------------------------------------------------------------------//
  {
    return new Session (TerminalFunction.SPY);
  }

  // ---------------------------------------------------------------------------------//
  private static WriteCommand write (byte... trailing)
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = new byte[trailing.length + 2];
    buffer[0] = Command.WRITE_F1;
    System.arraycopy (trailing, 0, buffer, 2, trailing.length);
    return new WriteCommand (buffer, 0, buffer.length);
  }

  // Um Write cuja unica order e o texto dado, em EBCDIC.
  // ---------------------------------------------------------------------------------//
  private static WriteCommand serverText (String text)
  // ---------------------------------------------------------------------------------//
  {
    return write (text.getBytes (CP1047));
  }

  // Monta um RSF: o AID de campo estruturado seguido de campos com prefixo de tamanho.
  // ---------------------------------------------------------------------------------//
  private static byte[] rsf (byte[]... fields)
  // ---------------------------------------------------------------------------------//
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream ();
    out.write (AIDCommand.AID_STRUCTURED_FIELD);

    for (byte[] field : fields)
    {
      int length = field.length + 2;
      out.write ((length >> 8) & 0xFF);
      out.write (length & 0xFF);
      out.write (field, 0, field.length);
    }

    return out.toByteArray ();
  }

  // ---------------------------------------------------------------------------------//
  private static byte[] queryReply (byte type, int... rest)
  // ---------------------------------------------------------------------------------//
  {
    byte[] field = new byte[rest.length + 2];
    field[0] = StructuredField.QUERY_REPLY;
    field[1] = type;
    for (int i = 0; i < rest.length; i++)
      field[i + 2] = (byte) rest[i];

    return field;
  }

  // Um RSF com area utilizavel de 24x80, que e o que carrega as dimensoes de tela.
  // ---------------------------------------------------------------------------------//
  private static ReadStructuredFieldCommand clientReply ()
  // ---------------------------------------------------------------------------------//
  {
    byte[] usableArea = queryReply (QueryReplyField.USABLE_AREA_REPLY, //
                                    0x01, 0x00, 0x00, 0x50, 0x00, 0x18, 0x00, //
                                    0x00, 0x0A, 0x00, 0x02, 0x00, 0x04, 0x00, 0x03, //
                                    0x09, 0x0C, 0x07, 0x80);

    return new ReadStructuredFieldCommand (rsf (usableArea));
  }

  // ---------------------------------------------------------------------------------//
  private static SessionRecord record (com.bytezone.dm3270.buffers.ReplyBuffer message,
      Source source)
  // ---------------------------------------------------------------------------------//
  {
    return new SessionRecord (SessionRecordType.TN3270, message, source, WHEN, true);
  }

  // Conta os avisos de cabecalho, que e o que o Label ouvia.
  // ---------------------------------------------------------------------------------//
  private static class HeaderSpy implements SessionHeaderListener
  // ---------------------------------------------------------------------------------//
  {
    private int calls;

    @Override
    public void headerChanged ()
    {
      calls++;
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o acumulado da conversa")
  class Accumulation
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("uma sessao nova esta vazia")
    void startsEmpty ()
    {
      Session session = session ();

      assertEquals (0, session.size ());
      assertEquals ("Empty session", session.toString ());
    }

    @Test
    @DisplayName ("os registros entram na ordem em que chegam")
    void keepsInsertionOrder ()
    {
      Session session = session ();
      SessionRecord first = record (write ((byte) 0xC1), Source.SERVER);
      SessionRecord second = record (write ((byte) 0xC1, (byte) 0xC2), Source.SERVER);

      session.add (first);
      session.add (second);

      List<SessionRecord> seen = new ArrayList<> ();
      for (SessionRecord next : session)
        seen.add (next);

      assertEquals (2, session.size ());
      assertSame (first, seen.get (0));
      assertSame (second, seen.get (1));
    }

    @Test
    @DisplayName ("um registro nulo e recusado")
    void rejectsNull ()
    {
      Session session = session ();

      assertThrows (IllegalArgumentException.class, () -> session.add (null));
    }

    @Test
    @DisplayName ("getNext devolve o primeiro registro do tipo pedido")
    void findsTheFirstOfAType ()
    {
      Session session = session ();
      SessionRecord telnet = new SessionRecord (SessionRecordType.TELNET,
          new CommandHeader (new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00 }), Source.SERVER,
          WHEN, true);
      SessionRecord tn3270 = record (write ((byte) 0xC1), Source.SERVER);

      session.add (telnet);
      session.add (tn3270);

      assertSame (tn3270, session.getNext (SessionRecordType.TN3270));
      assertSame (telnet, session.getNext (SessionRecordType.TELNET));
      assertNull (session.getNext (SessionRecordType.TN3270E));
    }

    @Test
    @DisplayName ("getBySize devolve o primeiro registro do tamanho pedido")
    void findsTheFirstOfASize ()
    {
      Session session = session ();
      SessionRecord small = record (write ((byte) 0xC1), Source.SERVER);
      SessionRecord large = record (write ((byte) 0xC1, (byte) 0xC2), Source.SERVER);

      session.add (small);
      session.add (large);

      assertSame (small, session.getBySize (3));
      assertSame (large, session.getBySize (4));
      assertNull (session.getBySize (99));
    }

    @Test
    @DisplayName ("os rotulos das telas sao uma lista viva, mutada de fora")
    void labelsAreMutableFromOutside ()
    {
      Session session = session ();

      session.getLabels ().add ("Test");

      assertEquals (List.of ("Test"), session.getLabels ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("quem esta do lado do cliente")
  class ClientSide
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("antes de qualquer identificacao os dois lados sao Unknown")
    void bothUnknownAtFirst ()
    {
      Session session = session ();

      assertEquals ("Unknown", session.getClientName ());
      assertEquals ("Unknown", session.getServerName ());
      assertNull (session.getScreenDimensions ());
    }

    @Test
    @DisplayName ("uma resposta de capacidades do cliente da nome e dimensoes")
    void identifiesTheClient ()
    {
      Session session = session ();

      session.add (record (clientReply (), Source.CLIENT));

      assertEquals ("Unknown", session.getClientName (),
          "a assinatura MD5 deste RSF nao esta na tabela, e o proprio comando devolve Unknown");
      assertNotNull (session.getScreenDimensions ());
      assertEquals (24, session.getScreenDimensions ().rows);
      assertEquals (80, session.getScreenDimensions ().columns);
    }

    @Test
    @DisplayName ("a mesma resposta vinda do servidor nao identifica cliente nenhum")
    void ignoresTheWrongSide ()
    {
      Session session = session ();

      session.add (record (clientReply (), Source.SERVER));

      assertNull (session.getScreenDimensions ());
    }

    @Test
    @DisplayName ("uma mensagem que nao e comando nao identifica nada")
    void ignoresNonCommands ()
    {
      Session session = session ();
      SessionRecord header = new SessionRecord (SessionRecordType.TELNET,
          new CommandHeader (new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00 }), Source.CLIENT,
          WHEN, true);

      session.add (header);

      assertNull (session.getScreenDimensions ());
      assertEquals (1, session.size ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("quem esta do lado do servidor")
  class ServerSide
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("reconhece o FanDeZhi pela saudacao")
    void recognisesFanDeZhi ()
    {
      Session session = session ();

      session.add (record (serverText ("Welcome to Fan DeZhi Mainframe System!"),
                           Source.SERVER));

      assertEquals ("FanDeZhi", session.getServerName ());
    }

    @Test
    @DisplayName ("reconhece o Hercules pelo cabecalho de versao")
    void recognisesHercules ()
    {
      Session session = session ();

      session.add (record (serverText ("Hercules Version  : 4.0"), Source.SERVER));

      assertEquals ("Hercules", session.getServerName ());
    }

    @Test
    @DisplayName ("reconhece o Nissan pelo telefone exato")
    void recognisesNissan ()
    {
      Session session = session ();

      session.add (record (serverText ("[(03) 97974300 ]"), Source.SERVER));

      assertEquals ("Nissan", session.getServerName ());
    }

    @Test
    @DisplayName ("reconhece o InterSession pelo prefixo")
    void recognisesInterSession ()
    {
      Session session = session ();

      session.add (record (serverText ("[InterSession --- 4.3"), Source.SERVER));

      assertEquals ("InterSession", session.getServerName ());
    }

    @Test
    @DisplayName ("um texto desconhecido deixa o servidor sem nome")
    void unknownServerStaysUnknown ()
    {
      Session session = session ();

      session.add (record (serverText ("READY"), Source.SERVER));

      assertEquals ("Unknown", session.getServerName ());
    }

    @Test
    @DisplayName ("depois de identificado o servidor nao e reexaminado")
    void theFirstMatchWins ()
    {
      Session session = session ();

      session.add (record (serverText ("Hercules Version  : 4.0"), Source.SERVER));
      session.add (record (serverText ("Welcome to Fan DeZhi Mainframe System!"),
                           Source.SERVER));

      assertEquals ("Hercules", session.getServerName ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o rotulo do cabecalho")
  class Header
  // ---------------------------------------------------------------------------------//
  {
    /*
     * O Label nascia vazio, e nao escrito "Unknown : Unknown". O que garantia isso era nao
     * haver aviso nenhum antes da primeira identificacao - e e isto que o teste afirma.
     */
    @Test
    @DisplayName ("nao avisa nada antes de identificar alguem")
    void staysQuietUntilSomeoneIsIdentified ()
    {
      Session session = session ();
      HeaderSpy spy = new HeaderSpy ();

      session.addHeaderListener (spy);
      session.add (record (serverText ("READY"), Source.SERVER));

      assertEquals (0, spy.calls);
      assertEquals ("Unknown : Unknown", session.getHeaderText ());
    }

    @Test
    @DisplayName ("identificar um lado avisa, e o texto traz os dois")
    void showsBothSides ()
    {
      Session session = session ();
      HeaderSpy spy = new HeaderSpy ();
      session.addHeaderListener (spy);

      session.add (record (serverText ("Hercules Version  : 4.0"), Source.SERVER));

      assertEquals (1, spy.calls);
      assertEquals ("Hercules : Unknown", session.getHeaderText ());
    }

    @Test
    @DisplayName ("com os dois lados vistos o texto traz servidor e cliente")
    void showsServerAndClient ()
    {
      Session session = session ();

      session.add (record (serverText ("Hercules Version  : 4.0"), Source.SERVER));
      session.add (record (clientReply (), Source.CLIENT));

      assertEquals ("Hercules : Unknown", session.getHeaderText ());
      assertEquals ("Unknown", session.getClientName ());
    }

    /*
     * O caso do modo Replay: a sessao e carregada inteira antes de a janela existir, e quem se
     * inscreve depois tem de receber o aviso mesmo assim - senao o cabecalho ficaria em branco
     * numa sessao ja identificada.
     */
    @Test
    @DisplayName ("quem se inscreve depois da identificacao e avisado na hora")
    void aLateSubscriberIsToldImmediately ()
    {
      Session session = session ();
      session.add (record (serverText ("Hercules Version  : 4.0"), Source.SERVER));

      HeaderSpy spy = new HeaderSpy ();
      session.addHeaderListener (spy);

      assertEquals (1, spy.calls);
    }

    @Test
    @DisplayName ("quem se inscreve numa sessao sem nome nenhum nao e avisado")
    void aLateSubscriberOnAnEmptySessionIsNotTold ()
    {
      Session session = session ();
      HeaderSpy spy = new HeaderSpy ();

      session.addHeaderListener (spy);

      assertEquals (0, spy.calls);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("gravar e reler")
  class Persistence
  // ---------------------------------------------------------------------------------//
  {
    /*
     * De ida e volta de proposito: o formato de save () so importa porque o SessionReader o
     * le, e e assim que os arquivos de replay circulam.
     */
    @Test
    @DisplayName ("o que save grava o SessionReader rele igual")
    void savedSessionReadsBack (@TempDir Path folder) throws Exception
    {
      Session session = session ();
      WriteCommand fromServer = write ((byte) 0xC1, (byte) 0xC2);
      session.add (record (fromServer, Source.SERVER));

      File file = folder.resolve ("session.txt").toFile ();
      session.save (file);

      SessionReader reader = new SessionReader (Source.SERVER, file.toPath ());

      assertArrayEquals (fromServer.getTelnetData (), reader.nextBuffer ());
      assertEquals (WHEN, reader.getDateTime ());
      assertTrue (reader.isGenuine ());
    }

    @Test
    @DisplayName ("cada lado da conversa e relido pelo leitor do seu lado")
    void bothSidesAreSeparable (@TempDir Path folder) throws Exception
    {
      Session session = session ();
      WriteCommand fromServer = write ((byte) 0xC1);
      WriteCommand fromClient = write ((byte) 0xC2, (byte) 0xC3);
      session.add (record (fromServer, Source.SERVER));
      session.add (record (fromClient, Source.CLIENT));

      File file = folder.resolve ("session.txt").toFile ();
      session.save (file);

      assertArrayEquals (fromServer.getTelnetData (),
          new SessionReader (Source.SERVER, file.toPath ()).nextBuffer ());
      assertArrayEquals (fromClient.getTelnetData (),
          new SessionReader (Source.CLIENT, file.toPath ()).nextBuffer ());
    }

    @Test
    @DisplayName ("safeSave embaralha o texto digitado pelo usuario")
    void safeSaveScramblesUserInput (@TempDir Path folder) throws Exception
    {
      Session session = session ();
      byte[] aid = { AIDCommand.AID_ENTER, (byte) 0x5D, (byte) 0x7F, //
                     Order.SET_BUFFER_ADDRESS, 0x40, 0x40, //
                     (byte) 0xC1, (byte) 0xC2 };
      AIDCommand command = new AIDCommand (aid, 0, aid.length);
      session.add (record (command, Source.CLIENT));

      File file = folder.resolve ("session.txt").toFile ();
      session.safeSave (file);

      String saved = Files.readString (file.toPath ());

      assertTrue (saved.contains ("7B 7B"), saved);
      assertTrue (!saved.contains ("C1 C2"), saved);
    }

    @Test
    @DisplayName ("save comum nao embaralha, e safeSave nao contamina o proximo save")
    void plainSaveKeepsTheText (@TempDir Path folder) throws Exception
    {
      Session session = session ();
      byte[] aid = { AIDCommand.AID_ENTER, (byte) 0x5D, (byte) 0x7F, //
                     Order.SET_BUFFER_ADDRESS, 0x40, 0x40, //
                     (byte) 0xC1, (byte) 0xC2 };
      session.add (record (new AIDCommand (aid, 0, aid.length), Source.CLIENT));

      File plain = folder.resolve ("plain.txt").toFile ();
      session.save (plain);

      assertTrue (Files.readString (plain.toPath ()).contains ("C1 C2"));

      File safe = folder.resolve ("safe.txt").toFile ();
      session.safeSave (safe);

      File again = folder.resolve ("again.txt").toFile ();
      session.save (again);

      assertTrue (Files.readString (again.toPath ()).contains ("7B 7B"),
          "o embaralhamento mudou o buffer, entao o save seguinte ja sai embaralhado");
    }

    @Test
    @DisplayName ("um destino invalido e registrado no log, e nao lancado")
    void anInvalidTargetIsLoggedAndSwallowed (@TempDir Path folder)
    {
      Session session = session ();
      session.add (record (write ((byte) 0xC1), Source.SERVER));

      session.save (folder.resolve ("sem-pasta").resolve ("x.txt").toFile ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o texto da sessao e uma linha por registro")
  void printsOneLinePerRecord ()
  // ---------------------------------------------------------------------------------//
  {
    Session session = session ();
    SessionRecord first = record (write ((byte) 0xC1), Source.SERVER);
    SessionRecord second = record (write ((byte) 0xC2), Source.CLIENT);

    session.add (first);
    session.add (second);

    assertEquals (first + "\n" + second, session.toString ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o texto EBCDIC de teste chega inteiro ate a order")
  void theTestFixtureRoundTrips ()
  // ---------------------------------------------------------------------------------//
  {
    WriteCommand command = serverText ("Hercules Version  : 4.0");

    assertEquals (1, command.getOrdersList ().size ());
    assertEquals ("Hercules Version  : 4.0",
        Dm3270Utility.getString (command.getOrdersList ().get (0).getBuffer ()));
  }
}
