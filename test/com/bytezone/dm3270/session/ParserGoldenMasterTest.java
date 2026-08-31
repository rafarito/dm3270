package com.bytezone.dm3270.session;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bytezone.dm3270.buffers.Buffer;
import com.bytezone.dm3270.buffers.ReplyBuffer;
import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.extended.BindCommand;
import com.bytezone.dm3270.extended.CommandHeader;
import com.bytezone.dm3270.extended.CommandHeader.DataType;
import com.bytezone.dm3270.extended.ResponseCommand;
import com.bytezone.dm3270.extended.TN3270ExtendedCommand;
import com.bytezone.dm3270.extended.UnbindCommand;
import com.bytezone.dm3270.runtime.Source;
import com.bytezone.dm3270.streams.TelnetState;
import com.bytezone.dm3270.telnet.TN3270ExtendedSubcommand;
import com.bytezone.dm3270.telnet.TelnetCommand;
import com.bytezone.dm3270.telnet.TelnetCommandProcessor;
import com.bytezone.dm3270.telnet.TelnetProcessor;
import com.bytezone.dm3270.telnet.TelnetSubcommand;
import com.bytezone.dm3270.telnet.TerminalTypeSubcommand;

/*
 * Golden master do parser.
 *
 * Reprocessa mf.txt - uma sessao TN3270 real gravada, ja publicada no classpath e usada em
 * producao pelo MainframeStage - e congela num snapshot a estrutura de tudo que o parser
 * produz: os registros que o TelnetProcessor separa, os comandos e orders que o
 * Command.getCommand monta, e as respostas telnet que a negociacao gera.
 *
 * Cobre de uma vez telnet, buffers, commands, orders, extended, structuredfields e
 * replyfield - exatamente os pacotes que a refatoracao de desacoplamento vai mexer.
 * Enquanto o snapshot nao mudar, o parser nao mudou.
 *
 * Nao ha JavaFX aqui de proposito: o unico process() invocado e o do TelnetCommand, que
 * ignora o parametro screen e mexe apenas no TelnetState. O process() dos comandos 3270,
 * que precisa de uma tela de verdade, e coberto pelo golden master do processamento.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("Golden master - parse de uma sessao TN3270 gravada")
class ParserGoldenMasterTest
// -----------------------------------------------------------------------------------//
{
  private static final String SESSION_RESOURCE = "com/bytezone/dm3270/application/mf.txt";
  private static final Path GOLDEN = Paths.get ("test", "golden", "mf-parse.txt");
  private static final Path ACTUAL = Paths.get ("target", "golden", "mf-parse.actual.txt");

  private static final HexFormat HEX = HexFormat.of ().withUpperCase ();

  // Valores de fio, fixados pelas RFCs 854/1091/2355 e pela especificacao TN3270E.
  // Repetidos aqui de proposito em vez de importados de TelnetProcessor: o golden master
  // congela o protocolo, nao as constantes do codigo de producao.
  private static final byte IAC = (byte) 0xFF;
  private static final byte SE = (byte) 0xF0;
  private static final byte SB = (byte) 0xFA;
  private static final byte DO = (byte) 0xFD;
  private static final byte EOR = (byte) 0xEF;

  private static final byte SB_BINARY = 0x00;
  private static final byte SB_TERMINAL_TYPE = 0x18;
  private static final byte SB_EOR = 0x19;
  private static final byte SB_TN3270E = 0x28;

  private static final byte EXT_CONNECT = 1;
  private static final byte EXT_DEVICE_TYPE = 2;
  private static final byte EXT_FUNCTIONS = 3;
  private static final byte EXT_IS = 4;
  private static final byte EXT_SEND = 8;

  private static final byte FN_BIND_IMAGE = 0;
  private static final byte FN_RESPONSES = 2;

  private static final byte TT_SEND = 1;
  private static final byte TN3270_DATA = 0x00;

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o parse de mf.txt continua identico ao snapshot aprovado")
  void parseIsUnchanged () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder text = new StringBuilder ();

    renderRecordedSession (text);
    renderNegotiation (text);

    String actual = normalise (text.toString ());

    if (!Files.exists (GOLDEN))
    {
      write (GOLDEN, actual);
      fail ("snapshot inexistente - foi gravado em " + GOLDEN
          + ". Revise o conteudo e versione o arquivo.");
    }

    String expected = normalise (Files.readString (GOLDEN, StandardCharsets.UTF_8));

    if (!expected.equals (actual))
    {
      write (ACTUAL, actual);
      fail (firstDifference (expected, actual) + System.lineSeparator ()
          + "snapshot esperado: " + GOLDEN + System.lineSeparator ()
          + "snapshot obtido:   " + ACTUAL);
    }
  }

  /*
   * Secao 1 - dados 3270 de verdade.
   *
   * mf.txt nao contem negociacao telnet nenhuma, so registros de dados. Por isso
   * does3270Extended() fica falso durante toda esta secao e o parse segue o caminho basico
   * (offset 0, sem cabecalho TN3270E). O caminho estendido e coberto pela secao 2.
   *
   * O buffer 02 nao produz saida sozinho: ele termina sem IAC EOR e o TelnetProcessor o
   * guarda ate o buffer 03 completar o registro. Esse remonte tambem esta congelado aqui.
   */
  // ---------------------------------------------------------------------------------//
  private static void renderRecordedSession (StringBuilder text) throws Exception
  // ---------------------------------------------------------------------------------//
  {
    TelnetState telnetState = newTelnetState ();
    TelnetProcessor processor =
        new TelnetProcessor (new ParseRecorder (telnetState, text));

    heading (text, "SESSAO GRAVADA (mf.txt)");

    SessionReader reader = new SessionReader (Source.SERVER, readSessionLines ());

    int bufferNo = 0;
    while (true)
    {
      byte[] buffer = reader.nextBuffer ();
      if (buffer.length == 0)
        break;

      text.append (
          String.format ("%n--- buffer %02d (%d bytes) ---%n", ++bufferNo, buffer.length));
      processor.listen (buffer);
    }

    summarise (text, telnetState);
  }

  /*
   * Secao 2 - negociacao TN3270E.
   *
   * Sequencia que um host manda de verdade ao abrir a sessao. Exercita o que mf.txt nao
   * alcanca: processTelnetCommand, processTelnetSubcommand, a geracao das respostas, e o
   * caminho estendido de processRecord - depois do DO TN3270E o does3270Extended() vira
   * verdadeiro e o ultimo registro ja carrega o cabecalho de 5 bytes.
   */
  // ---------------------------------------------------------------------------------//
  private static void renderNegotiation (StringBuilder text)
  // ---------------------------------------------------------------------------------//
  {
    TelnetState telnetState = newTelnetState ();
    TelnetProcessor processor =
        new TelnetProcessor (new ParseRecorder (telnetState, text));

    heading (text, "NEGOCIACAO TN3270E");

    feed (text, processor, "DO TN3270E", IAC, DO, SB_TN3270E);
    feed (text, processor, "SB TN3270E SEND DEVICE-TYPE", IAC, SB, SB_TN3270E, EXT_SEND,
        EXT_DEVICE_TYPE, IAC, SE);
    feed (text, processor, "SB TN3270E DEVICE-TYPE IS + CONNECT",
        concat (new byte[] { IAC, SB, SB_TN3270E, EXT_DEVICE_TYPE, EXT_IS },
            ascii ("IBM-3278-2-E"), new byte[] { EXT_CONNECT }, ascii ("LU1"),
            new byte[] { IAC, SE }));
    feed (text, processor, "SB TN3270E FUNCTIONS IS", IAC, SB, SB_TN3270E, EXT_FUNCTIONS,
        EXT_IS, FN_BIND_IMAGE, FN_RESPONSES, IAC, SE);
    feed (text, processor, "DO TERMINAL-TYPE", IAC, DO, SB_TERMINAL_TYPE);
    feed (text, processor, "SB TERMINAL-TYPE SEND", IAC, SB, SB_TERMINAL_TYPE, TT_SEND, IAC,
        SE);
    feed (text, processor, "DO EOR", IAC, DO, SB_EOR);
    feed (text, processor, "DO BINARY", IAC, DO, SB_BINARY);

    // agora que a sessao e estendida, um registro carrega o cabecalho TN3270E de 5 bytes
    feed (text, processor, "registro TN3270E (cabecalho + Write/WCC)", TN3270_DATA, 0x00,
        0x00, 0x00, 0x01, 0xF1, 0xC2, IAC, EOR);

    summarise (text, telnetState);
  }

  // ---------------------------------------------------------------------------------//
  private static TelnetState newTelnetState ()
  // ---------------------------------------------------------------------------------//
  {
    // as preferencias que o ConsolePane monta a partir do Site
    TelnetState telnetState = new TelnetState ();
    telnetState.setDo3270Extended (true);
    telnetState.setDoTerminalType (true);
    telnetState.setDoDeviceType (2);
    return telnetState;
  }

  // ---------------------------------------------------------------------------------//
  private static void heading (StringBuilder text, String title)
  // ---------------------------------------------------------------------------------//
  {
    text.append ("================================================================\n");
    text.append (title).append ('\n');
    text.append ("================================================================\n");
  }

  // ---------------------------------------------------------------------------------//
  private static void summarise (StringBuilder text, TelnetState telnetState)
  // ---------------------------------------------------------------------------------//
  {
    text.append (String.format ("%n--- estado final do telnet ---%n"));
    text.append (String.format ("does3270Extended : %s%n", telnetState.does3270Extended ()));
    text.append (String.format ("doesTerminalType : %s%n", telnetState.doesTerminalType ()));
    text.append (String.format ("doesEOR          : %s%n", telnetState.doesEOR ()));
    text.append (String.format ("doesBinary       : %s%n", telnetState.doesBinary ()));
    text.append (String.format ("%s%n", telnetState.getSummary ()));
  }

  // ---------------------------------------------------------------------------------//
  private static void feed (StringBuilder text, TelnetProcessor processor, String label,
      int... values)
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = new byte[values.length];
    for (int i = 0; i < values.length; i++)
      buffer[i] = (byte) values[i];

    feed (text, processor, label, buffer);
  }

  // ---------------------------------------------------------------------------------//
  private static void feed (StringBuilder text, TelnetProcessor processor, String label,
      byte[] buffer)
  // ---------------------------------------------------------------------------------//
  {
    text.append (String.format ("%n--- %s ---%n", label));
    processor.listen (buffer);
  }

  // ---------------------------------------------------------------------------------//
  private static byte[] ascii (String value)
  // ---------------------------------------------------------------------------------//
  {
    return value.getBytes (StandardCharsets.US_ASCII);
  }

  // ---------------------------------------------------------------------------------//
  private static byte[] concat (byte[]... parts)
  // ---------------------------------------------------------------------------------//
  {
    int size = 0;
    for (byte[] part : parts)
      size += part.length;

    byte[] joined = new byte[size];
    int offset = 0;
    for (byte[] part : parts)
    {
      System.arraycopy (part, 0, joined, offset, part.length);
      offset += part.length;
    }
    return joined;
  }

  // Le mf.txt em ISO-8859-1 de proposito: so as colunas de digitos hexadecimais
  // interessam, e um charset de um byte por caractere garante que as posicoes de coluna
  // que o SessionReader usa nunca deslizem por causa do texto EBCDIC decodificado no fim
  // de cada linha.
  // ---------------------------------------------------------------------------------//
  private static List<String> readSessionLines () throws IOException
  // ---------------------------------------------------------------------------------//
  {
    try (InputStream in = ParserGoldenMasterTest.class.getClassLoader ()
        .getResourceAsStream (SESSION_RESOURCE))
    {
      if (in == null)
        throw new IOException ("recurso nao encontrado no classpath: " + SESSION_RESOURCE);

      BufferedReader reader =
          new BufferedReader (new InputStreamReader (in, StandardCharsets.ISO_8859_1));

      List<String> lines = new ArrayList<> ();
      String line;
      while ((line = reader.readLine ()) != null)
        lines.add (line);

      return lines;
    }
  }

  /*
   * O snapshot nao pode depender de quebra de linha.
   *
   * A geracao mistura String.format ("%n"), que no Windows produz CRLF, com "\n" literal;
   * e o git converte a quebra de linha do arquivo versionado conforme core.autocrlf. Sem
   * normalizar os dois lados, o teste passaria na maquina que gerou o snapshot e falharia
   * num checkout novo ou no CI - apontando uma diferenca que nao existe no parser.
   */
  // ---------------------------------------------------------------------------------//
  private static String normalise (String content)
  // ---------------------------------------------------------------------------------//
  {
    return content.replace ("\r\n", "\n");
  }

  // ---------------------------------------------------------------------------------//
  private static void write (Path path, String content) throws IOException
  // ---------------------------------------------------------------------------------//
  {
    Files.createDirectories (path.getParent ());
    Files.writeString (path, content, StandardCharsets.UTF_8);
  }

  // ---------------------------------------------------------------------------------//
  private static String firstDifference (String expected, String actual)
  // ---------------------------------------------------------------------------------//
  {
    String[] expectedLines = expected.split ("\n", -1);
    String[] actualLines = actual.split ("\n", -1);

    int max = Math.min (expectedLines.length, actualLines.length);
    for (int i = 0; i < max; i++)
      if (!expectedLines[i].equals (actualLines[i]))
        return String.format ("o parse mudou na linha %d:%n  esperado: %s%n  obtido:   %s",
            i + 1, expectedLines[i], actualLines[i]);

    return String.format ("o parse mudou de tamanho: esperado %d linhas, obtido %d",
        expectedLines.length, actualLines.length);
  }

  /*
   * Espelha o TelnetListener sem a GUI: monta os mesmos objetos, na mesma ordem, com a
   * mesma logica de offset. A unica diferenca deliberada e nao chamar process() nos
   * comandos 3270 - esses precisam de uma tela.
   */
  // ---------------------------------------------------------------------------------//
  private static final class ParseRecorder implements TelnetCommandProcessor
  // ---------------------------------------------------------------------------------//
  {
    private final TelnetState telnetState;
    private final StringBuilder text;

    ParseRecorder (TelnetState telnetState, StringBuilder text)
    {
      this.telnetState = telnetState;
      this.text = text;
    }

    // -------------------------------------------------------------------------------//
    @Override
    public void processData (byte[] buffer, int length)
    // -------------------------------------------------------------------------------//
    {
      text.append (String.format ("DATA %s%n", hex (buffer, 0, length)));
    }

    // -------------------------------------------------------------------------------//
    @Override
    public void processRecord (byte[] data, int dataPtr)
    // -------------------------------------------------------------------------------//
    {
      int offset;
      int length;
      DataType dataType;
      CommandHeader commandHeader;

      if (telnetState.does3270Extended ())
      {
        offset = 5;
        length = dataPtr - 7;
        commandHeader = new CommandHeader (data, 0, 5);
        dataType = commandHeader.getDataType ();
      }
      else
      {
        offset = 0;
        length = dataPtr - 2;
        commandHeader = null;
        dataType = DataType.TN3270_DATA;
      }

      text.append (String.format ("REC  %s%n", hex (data, 0, dataPtr)));
      if (commandHeader != null)
        text.append (String.format ("     header: %s%n", commandHeader));

      ReplyBuffer message = build (dataType, commandHeader, data, offset, length);

      if (message == null)
        text.append (String.format ("     tipo nao tratado: %s%n", dataType));
      else
        describe (message);
    }

    // -------------------------------------------------------------------------------//
    private ReplyBuffer build (DataType dataType, CommandHeader commandHeader, byte[] data,
        int offset, int length)
    // -------------------------------------------------------------------------------//
    {
      switch (dataType)
      {
        case TN3270_DATA:
          ReplyBuffer command = Command.getCommand (data, offset, length);
          return commandHeader == null ? command
              : new TN3270ExtendedCommand (commandHeader, (Command) command);

        case BIND_IMAGE:
          return new BindCommand (commandHeader, data, offset, length);

        case UNBIND:
          return new UnbindCommand (commandHeader, data, offset, length);

        case RESPONSE:
          return new ResponseCommand (commandHeader, data, offset, length);

        default:
          return null;
      }
    }

    // -------------------------------------------------------------------------------//
    @Override
    public void processTelnetCommand (byte[] data, int dataPtr)
    // -------------------------------------------------------------------------------//
    {
      TelnetCommand telnetCommand = new TelnetCommand (telnetState, data, dataPtr);
      text.append (String.format ("CMD  %s%n", hex (data, 0, dataPtr)));

      // seguro: TelnetCommand.process ignora o parametro e so avanca o TelnetState, que e
      // o que faz a negociacao seguir igual a producao
      telnetCommand.process (null);
      describe (telnetCommand);
    }

    // -------------------------------------------------------------------------------//
    @Override
    public void processTelnetSubcommand (byte[] data, int dataPtr)
    // -------------------------------------------------------------------------------//
    {
      text.append (String.format ("SUB  %s%n", hex (data, 0, dataPtr)));

      TelnetSubcommand subcommand = null;
      if (data[2] == TelnetSubcommand.TERMINAL_TYPE)
        subcommand = new TerminalTypeSubcommand (data, 0, dataPtr, telnetState);
      else if (data[2] == TelnetSubcommand.TN3270E)
        subcommand = new TN3270ExtendedSubcommand (data, 0, dataPtr, telnetState);

      if (subcommand == null)
        text.append (String.format ("     subcomando desconhecido: %02X%n", data[2]));
      else
        describe (subcommand);
    }

    // -------------------------------------------------------------------------------//
    private void describe (ReplyBuffer message)
    // -------------------------------------------------------------------------------//
    {
      text.append (String.format ("     %s%n", message.getClass ().getSimpleName ()));
      text.append (indent (String.valueOf (message)));

      Optional<Buffer> reply = message.getReply ();
      if (reply.isPresent ())
        text.append (String.format ("     reply: %s%n", hex (reply.get ().getTelnetData ())));
    }

    // -------------------------------------------------------------------------------//
    private static String indent (String value)
    // -------------------------------------------------------------------------------//
    {
      StringBuilder builder = new StringBuilder ();
      for (String line : value.split ("\n", -1))
        builder.append ("       ").append (line).append ('\n');
      return builder.toString ();
    }

    // -------------------------------------------------------------------------------//
    private static String hex (byte[] data)
    // -------------------------------------------------------------------------------//
    {
      return hex (data, 0, data.length);
    }

    // -------------------------------------------------------------------------------//
    private static String hex (byte[] data, int offset, int length)
    // -------------------------------------------------------------------------------//
    {
      return HEX.formatHex (data, offset, offset + length);
    }
  }
}
