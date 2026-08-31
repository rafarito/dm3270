package com.bytezone.dm3270.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.commands.WriteCommand;
import com.bytezone.dm3270.extended.CommandHeader;
import com.bytezone.dm3270.extended.TN3270ExtendedCommand;
import com.bytezone.dm3270.orders.Order;
import com.bytezone.dm3270.session.SessionRecord.SessionRecordType;
import com.bytezone.dm3270.streams.TelnetSocket.Source;

/*
 * Caracterizacao do SessionRecord - uma mensagem lida do fio, com a origem, o instante e o
 * rotulo que a tabela de replay mostra.
 *
 * Esta classe faz duas coisas ao mesmo tempo: guarda a mensagem parseada (que e dominio) e
 * guarda cinco Property do JavaFX que existem so para o SessionTable ligar colunas por nome de
 * string. Os testes abaixo congelam o comportamento de HOJE, antes de as duas metades serem
 * separadas.
 *
 * O que precisa sobreviver, e nao e obvio:
 *
 *   - os cinco rotulos sao derivados UMA VEZ, no construtor, e nunca mais mudam. Nao ha
 *     recalculo: o que a tabela mostra e o que o construtor escreveu;
 *   - o CommandHeader fica SEM NOME de proposito - ele nao e um NamedBuffer, e por isso a
 *     coluna Command aparece vazia para ele. Esta dito no comentario do NamedBuffer;
 *   - quando o dateTime e nulo, a property time nunca chega a ser escrita, e a coluna mm:ss
 *     fica vazia. Um cabecalho curto num arquivo de replay produz exatamente isso;
 *   - getTime () NAO devolve a hora. Ver o grupo Defeitos, no fim.
 *
 * Nao ha @ExtendWith (JavaFxToolkit.class) porque SimpleStringProperty e um bean comum e nao
 * precisa de toolkit - o que precisa e o Label da Session, e por isso o SessionTest o tem.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("SessionRecord - a mensagem gravada, e o rotulo que a tabela mostra")
class SessionRecordTest
// -----------------------------------------------------------------------------------//
{
  private static final LocalDateTime WHEN = LocalDateTime.of (2024, 1, 15, 10, 30, 42, 0);

  // Um Write com uma order de texto so, para termos um NamedBuffer de verdade.
  // ---------------------------------------------------------------------------------//
  private static WriteCommand write ()
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = { Command.WRITE_F1, 0x00, (byte) 0xC1, (byte) 0xC2 };
    return new WriteCommand (buffer, 0, buffer.length);
  }

  // O unico ReplyBuffer do projeto que NAO e um NamedBuffer.
  // ---------------------------------------------------------------------------------//
  private static CommandHeader header ()
  // ---------------------------------------------------------------------------------//
  {
    return new CommandHeader (new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00 });
  }

  // ---------------------------------------------------------------------------------//
  private static SessionRecord record (SessionRecordType type,
      com.bytezone.dm3270.buffers.ReplyBuffer message, Source source, boolean genuine)
  // ---------------------------------------------------------------------------------//
  {
    return new SessionRecord (type, message, source, WHEN, genuine);
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("os rotulos derivados no construtor")
  class Labels
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("uma mensagem genuina se identifica pelo lado da conversa")
    void genuineSourceNames ()
    {
      assertEquals ("Client",
          record (SessionRecordType.TN3270, write (), Source.CLIENT, true).getSourceName ());
      assertEquals ("Server",
          record (SessionRecordType.TN3270, write (), Source.SERVER, true).getSourceName ());
    }

    @Test
    @DisplayName ("uma mensagem injetada ganha o prefixo MITM")
    void mitmSourceNames ()
    {
      assertEquals ("MITM-C",
          record (SessionRecordType.TN3270, write (), Source.CLIENT, false).getSourceName ());
      assertEquals ("MITM-S",
          record (SessionRecordType.TN3270, write (), Source.SERVER, false).getSourceName ());
    }

    @Test
    @DisplayName ("cada tipo de registro tem o seu rotulo de coluna")
    void commandTypeLabels ()
    {
      assertEquals ("Telnet", record (SessionRecordType.TELNET, write (), Source.SERVER,
                                      true).getCommandType ());
      assertEquals ("TN3270", record (SessionRecordType.TN3270, write (), Source.SERVER,
                                      true).getCommandType ());
      assertEquals ("Extended", record (SessionRecordType.TN3270E, write (), Source.SERVER,
                                        true).getCommandType ());
    }

    @Test
    @DisplayName ("o nome do comando vem do NamedBuffer")
    void commandNameComesFromTheBuffer ()
    {
      SessionRecord record =
          record (SessionRecordType.TN3270, write (), Source.SERVER, true);

      assertEquals ("Write", record.getCommandName ());
    }

    @Test
    @DisplayName ("um CommandHeader continua sem nome, de proposito")
    void commandHeaderHasNoName ()
    {
      SessionRecord record =
          record (SessionRecordType.TN3270E, header (), Source.SERVER, true);

      assertNull (record.getCommandName ());
    }

    @Test
    @DisplayName ("o tamanho e o da mensagem")
    void bufferSizeIsTheMessageSize ()
    {
      WriteCommand command = write ();
      SessionRecord record =
          record (SessionRecordType.TN3270, command, Source.SERVER, true);

      assertEquals (command.size (), record.getBufferSize ());
      assertEquals (4, record.getBufferSize ());
    }

    @Test
    @DisplayName ("a hora e mostrada como mm:ss")
    void timeIsMinutesAndSeconds ()
    {
      SessionRecord record =
          record (SessionRecordType.TN3270, write (), Source.SERVER, true);

      assertEquals ("30:42", record.timeProperty ().get ());
    }

    @Test
    @DisplayName ("sem instante nenhum a coluna da hora fica vazia")
    void noDateTimeLeavesTheTimeUnset ()
    {
      SessionRecord record = new SessionRecord (SessionRecordType.TN3270, write (),
          Source.SERVER, null, true);

      assertNull (record.timeProperty ().get ());
    }

    @Test
    @DisplayName ("os rotulos sao escritos uma vez e nao mudam depois")
    void labelsAreDerivedOnce ()
    {
      SessionRecord record =
          record (SessionRecordType.TN3270, write (), Source.SERVER, true);

      assertSame (record.sourceNameProperty (), record.sourceNameProperty ());
      assertEquals ("Server", record.getSourceName ());
      assertEquals ("Server", record.getSourceName ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o tipo do registro")
  class RecordType
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("cada registro reconhece apenas o proprio tipo")
    void eachTypeAnswersOnlyForItself ()
    {
      SessionRecord telnet =
          record (SessionRecordType.TELNET, write (), Source.SERVER, true);
      SessionRecord tn3270 =
          record (SessionRecordType.TN3270, write (), Source.SERVER, true);
      SessionRecord extended =
          record (SessionRecordType.TN3270E, write (), Source.SERVER, true);

      assertTrue (telnet.isTelnet ());
      assertFalse (telnet.isTN3270 ());
      assertFalse (telnet.isTN3270Extended ());

      assertFalse (tn3270.isTelnet ());
      assertTrue (tn3270.isTN3270 ());
      assertFalse (tn3270.isTN3270Extended ());

      assertFalse (extended.isTelnet ());
      assertFalse (extended.isTN3270 ());
      assertTrue (extended.isTN3270Extended ());
    }

    @Test
    @DisplayName ("o tipo tambem sai inteiro, para o filtro da tabela")
    void theEnumIsAvailable ()
    {
      assertEquals (SessionRecordType.TN3270,
          record (SessionRecordType.TN3270, write (), Source.SERVER,
                  true).getDataRecordType ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o comando que a mensagem carrega")
  class TheCommand
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("um Command e um comando, e sai como ele mesmo")
    void plainCommand ()
    {
      WriteCommand command = write ();
      SessionRecord record =
          record (SessionRecordType.TN3270, command, Source.SERVER, true);

      assertTrue (record.isCommand ());
      assertSame (command, record.getCommand ());
    }

    @Test
    @DisplayName ("um comando estendido e desembrulhado")
    void extendedCommandIsUnwrapped ()
    {
      WriteCommand command = write ();
      TN3270ExtendedCommand extended = new TN3270ExtendedCommand (header (), command);
      SessionRecord record =
          record (SessionRecordType.TN3270E, extended, Source.SERVER, true);

      assertTrue (record.isCommand ());
      assertSame (command, record.getCommand ());
    }

    @Test
    @DisplayName ("um CommandHeader sozinho nao e comando nenhum")
    void headerIsNotACommand ()
    {
      SessionRecord record =
          record (SessionRecordType.TELNET, header (), Source.SERVER, true);

      assertFalse (record.isCommand ());
      assertNull (record.getCommand ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("os dados que o registro entrega")
  class Payload
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("a mensagem, os bytes e o tamanho vem do buffer original")
    void payloadComesFromTheMessage ()
    {
      WriteCommand command = write ();
      SessionRecord record =
          record (SessionRecordType.TN3270, command, Source.SERVER, true);

      assertSame (command, record.getMessage ());
      assertEquals (command.getData ().length, record.getBuffer ().length);
      assertEquals (command.size (), record.size ());
    }

    @Test
    @DisplayName ("a origem, o instante e a autenticidade saem como entraram")
    void metadataIsKept ()
    {
      SessionRecord record =
          record (SessionRecordType.TN3270, write (), Source.CLIENT, false);

      assertEquals (Source.CLIENT, record.getSource ());
      assertEquals (WHEN, record.getDateTime ());
      assertFalse (record.isGenuine ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o texto do registro")
  class Text
  // ---------------------------------------------------------------------------------//
  {
    /*
     * O padrao "dd MMM uuuu" depende do Locale da maquina - "Jan" aqui, "jan." em pt-BR - e o
     * SessionRecord nao passa Locale nenhum ao DateTimeFormatter. A expectativa e montada com
     * o mesmo padrao, e nao com um literal, pelo mesmo motivo do §5.16.
     */
    @Test
    @DisplayName ("imprime a origem e o instante completo")
    void printsSourceAndInstant ()
    {
      SessionRecord record =
          record (SessionRecordType.TN3270, write (), Source.SERVER, true);

      String expected = String.format ("%s : %s", Source.SERVER,
          DateTimeFormatter.ofPattern ("dd MMM uuuu HH:mm:ss.S").format (WHEN));

      assertEquals (expected, record.toString ());
    }

    @Test
    @DisplayName ("sem instante nenhum, imprimir estoura")
    void printingWithoutAnInstantThrows ()
    {
      SessionRecord record = new SessionRecord (SessionRecordType.TN3270, write (),
          Source.SERVER, null, true);

      assertThrows (NullPointerException.class, () -> record.toString ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("defeitos, congelados como estao")
  class Defects
  // ---------------------------------------------------------------------------------//
  {
    /*
     * getTime () le a property errada - devolve o commandName em vez da hora. Hoje e latente:
     * o PropertyValueFactory resolve timeProperty () primeiro e nunca chega ao getter, e
     * nenhum arquivo de src/ ou test/ o chama. Este teste existe para que a separacao que vem
     * a seguir leve o defeito para o lado certo em vez de ativa-lo na coluna mm:ss.
     */
    @Test
    @DisplayName ("getTime () devolve o nome do comando, e nao a hora")
    void getTimeReturnsTheCommandName ()
    {
      SessionRecord record =
          record (SessionRecordType.TN3270, write (), Source.SERVER, true);

      assertEquals ("30:42", record.timeProperty ().get ());
      assertEquals ("Write", record.getTime ());
    }

    /*
     * Consequencia do mesmo defeito: sem NamedBuffer nao ha commandName, e getTime () devolve
     * nulo mesmo com a hora presente na property.
     */
    @Test
    @DisplayName ("sem nome de comando, getTime () devolve nulo com a hora preenchida")
    void getTimeIsNullWhenThereIsNoCommandName ()
    {
      SessionRecord record =
          record (SessionRecordType.TN3270E, header (), Source.SERVER, true);

      assertEquals ("30:42", record.timeProperty ().get ());
      assertNull (record.getTime ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("uma order de texto nao interfere no rotulo do comando")
  void textOrderDoesNotChangeTheLabel ()
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = { Command.ERASE_WRITE_F5, (byte) 0xC3, Order.START_FIELD, (byte) 0x60,
                      (byte) 0xC1 };
    WriteCommand command = new WriteCommand (buffer, 0, buffer.length);

    SessionRecord record =
        record (SessionRecordType.TN3270, command, Source.SERVER, true);

    assertEquals ("Erase Write", record.getCommandName ());
    assertEquals (5, record.getBufferSize ());
  }
}
