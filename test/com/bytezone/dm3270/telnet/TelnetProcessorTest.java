package com.bytezone.dm3270.telnet;

import static com.bytezone.dm3270.telnet.TelnetProcessor.DO;
import static com.bytezone.dm3270.telnet.TelnetProcessor.EOR;
import static com.bytezone.dm3270.telnet.TelnetProcessor.IAC;
import static com.bytezone.dm3270.telnet.TelnetProcessor.NOP;
import static com.bytezone.dm3270.telnet.TelnetProcessor.SB;
import static com.bytezone.dm3270.telnet.TelnetProcessor.SB_TERMINAL_TYPE;
import static com.bytezone.dm3270.telnet.TelnetProcessor.SB_TN3270E;
import static com.bytezone.dm3270.telnet.TelnetProcessor.SE;
import static com.bytezone.dm3270.telnet.TelnetProcessor.WILL;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// -----------------------------------------------------------------------------------//
@DisplayName ("TelnetProcessor - separacao do stream telnet em registros e comandos")
class TelnetProcessorTest
// -----------------------------------------------------------------------------------//
{
  private Recorder recorder;
  private TelnetProcessor processor;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void setUp ()
  // ---------------------------------------------------------------------------------//
  {
    recorder = new Recorder ();
    processor = new TelnetProcessor (recorder);
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("registros 3270 (IAC EOR)")
  class Records
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("emite um registro ao encontrar IAC EOR")
    void emitsRecord ()
    {
      processor.listen ((byte) 0x05, (byte) 0xC1, IAC, EOR);

      assertEquals (1, recorder.records.size ());
      assertEquals (0, recorder.commands.size ());
      assertArrayEquals (new byte[] { 0x05, (byte) 0xC1, IAC, EOR },
          recorder.records.get (0));
    }

    @Test
    @DisplayName ("colapsa IAC IAC de volta para um unico byte de dados 0xFF")
    void unescapesDoubledIac ()
    {
      processor.listen ((byte) 0x05, IAC, IAC, (byte) 0xC1, IAC, EOR);

      assertEquals (1, recorder.records.size ());
      assertArrayEquals (new byte[] { 0x05, IAC, (byte) 0xC1, IAC, EOR },
          recorder.records.get (0),
          "o 0xFF duplicado deve virar um unico byte de dados");
    }

    @Test
    @DisplayName ("emite varios registros num unico bloco recebido")
    void emitsMultipleRecords ()
    {
      processor.listen ((byte) 0x01, IAC, EOR, (byte) 0x02, IAC, EOR);

      assertEquals (2, recorder.records.size ());
      assertArrayEquals (new byte[] { 0x01, IAC, EOR }, recorder.records.get (0));
      assertArrayEquals (new byte[] { 0x02, IAC, EOR }, recorder.records.get (1));
    }

    @Test
    @DisplayName ("remonta um registro partido entre duas leituras do socket")
    void reassemblesSplitRecord ()
    {
      processor.listen ((byte) 0x05, (byte) 0xC1);
      assertEquals (0, recorder.records.size (), "ainda nao ha registro completo");

      processor.listen ((byte) 0xC2, IAC, EOR);

      assertEquals (1, recorder.records.size ());
      assertArrayEquals (new byte[] { 0x05, (byte) 0xC1, (byte) 0xC2, IAC, EOR },
          recorder.records.get (0));
    }

    @Test
    @DisplayName ("remonta um registro partido exatamente entre IAC e EOR")
    void reassemblesSplitOnIac ()
    {
      processor.listen ((byte) 0x05, IAC);
      assertEquals (0, recorder.records.size ());

      processor.listen (EOR);

      assertEquals (1, recorder.records.size ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("comandos telnet")
  class Commands
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("reconhece comandos de tres bytes (DO/DONT/WILL/WONT)")
    void threeByteCommand ()
    {
      processor.listen (IAC, DO, TelnetProcessor.SB_EOR);

      assertEquals (1, recorder.commands.size ());
      assertArrayEquals (new byte[] { IAC, DO, TelnetProcessor.SB_EOR },
          recorder.commands.get (0));
    }

    @Test
    @DisplayName ("reconhece varios comandos de tres bytes seguidos")
    void consecutiveThreeByteCommands ()
    {
      processor.listen (IAC, DO, SB_TERMINAL_TYPE, IAC, WILL, SB_TERMINAL_TYPE);

      assertEquals (2, recorder.commands.size ());
      assertArrayEquals (new byte[] { IAC, DO, SB_TERMINAL_TYPE },
          recorder.commands.get (0));
      assertArrayEquals (new byte[] { IAC, WILL, SB_TERMINAL_TYPE },
          recorder.commands.get (1));
    }

    @Test
    @DisplayName ("reconhece comandos de dois bytes (NOP)")
    void twoByteCommand ()
    {
      processor.listen (IAC, NOP);

      assertEquals (1, recorder.commands.size ());
      assertArrayEquals (new byte[] { IAC, NOP }, recorder.commands.get (0));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("subcomandos telnet (IAC SB ... IAC SE)")
  class Subcommands
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("emite o subcomando completo, incluindo IAC SB e IAC SE")
    void emitsSubcommand ()
    {
      processor.listen (IAC, SB, SB_TERMINAL_TYPE, (byte) 0x01, IAC, SE);

      assertEquals (1, recorder.subcommands.size ());
      assertArrayEquals (new byte[] { IAC, SB, SB_TERMINAL_TYPE, (byte) 0x01, IAC, SE },
          recorder.subcommands.get (0));
    }

    @Test
    @DisplayName ("nao confunde o payload do subcomando com comandos telnet")
    void payloadIsNotInterpreted ()
    {
      // 0xFD (DO) e 0xFA (SB) aparecem como dados dentro do subcomando
      processor.listen (IAC, SB, SB_TN3270E, (byte) 0x02, (byte) 0x07, IAC, SE);

      assertEquals (1, recorder.subcommands.size ());
      assertEquals (0, recorder.commands.size ());
      assertEquals (7, recorder.subcommands.get (0).length);
    }

    @Test
    @DisplayName ("processa a negociacao completa de um terminal 3278")
    void fullTerminalTypeNegotiation ()
    {
      byte[] stream = { IAC, DO, SB_TERMINAL_TYPE,                  // comando
                        IAC, WILL, SB_TERMINAL_TYPE,                // comando
                        IAC, SB, SB_TERMINAL_TYPE, 0x01, IAC, SE,   // subcomando
                        IAC, SB, SB_TERMINAL_TYPE, 0x00,            // subcomando
                        0x49, 0x42, 0x4D, 0x2D, 0x33, 0x32, 0x37, 0x38,    // IBM-3278
                        IAC, SE };

      processor.listen (stream);

      assertEquals (2, recorder.commands.size ());
      assertEquals (2, recorder.subcommands.size ());
      assertEquals (0, recorder.records.size ());
      assertEquals (0, recorder.data.size ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("dados nao-telnet no stream")
  class NonTelnetData
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("descarrega os dados pendentes antes de tratar o comando telnet")
    void flushesDataBeforeCommand ()
    {
      processor.listen ((byte) 0x41, (byte) 0x42, IAC, DO, SB_TERMINAL_TYPE);

      assertEquals (1, recorder.data.size ());
      assertArrayEquals (new byte[] { 0x41, 0x42 }, recorder.data.get (0));

      assertEquals (1, recorder.commands.size ());
      assertArrayEquals (new byte[] { IAC, DO, SB_TERMINAL_TYPE },
          recorder.commands.get (0));
    }

    @Test
    @DisplayName ("stream que comeca com IAC IAC e tratado como dado 0xFF")
    void streamStartingWithDoubledIac ()
    {
      // esse e o caso que a flag weirdData existe para cobrir
      processor.listen (IAC, IAC, IAC, DO, SB_TERMINAL_TYPE);

      assertEquals (1, recorder.data.size ());
      assertArrayEquals (new byte[] { IAC }, recorder.data.get (0));

      assertEquals (1, recorder.commands.size ());
      assertArrayEquals (new byte[] { IAC, DO, SB_TERMINAL_TYPE },
          recorder.commands.get (0));
    }

    @Test
    @DisplayName ("dados sem nenhum IAC nao sao entregues (ficam no buffer)")
    void plainDataIsBuffered ()
    {
      processor.listen ((byte) 0x41, (byte) 0x42, (byte) 0x43);

      assertTrue (recorder.isEmpty (),
          "sem um IAC o processador nao tem como saber que o bloco terminou");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("sessao realista")
  class RealisticSession
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("negociacao TN3270E seguida de dois registros de dados")
    void negotiationThenRecords ()
    {
      byte[] stream = { IAC, DO, SB_TN3270E, IAC, WILL, SB_TN3270E, //
                        IAC, SB, SB_TN3270E, 0x08, 0x02, IAC, SE, //
                        0x03, 0x00, 0x00, 0x00, 0x00, 0x31, 0x01, 0x03, IAC, EOR, //
                        IAC, NOP, //
                        0x03, 0x00, 0x00, 0x00, 0x00, 0x31, 0x01, 0x03, IAC, EOR };

      processor.listen (stream);

      assertEquals (3, recorder.commands.size (), "DO, WILL e NOP");
      assertEquals (1, recorder.subcommands.size ());
      assertEquals (2, recorder.records.size ());
      assertEquals (0, recorder.data.size ());
    }

    @Test
    @DisplayName ("o resultado independe de como o stream e fatiado pelo socket")
    void chunkingDoesNotChangeResult ()
    {
      byte[] stream = { IAC, DO, SB_TN3270E, IAC, WILL, SB_TN3270E, //
                        IAC, SB, SB_TN3270E, 0x08, 0x02, IAC, SE, //
                        0x03, 0x00, 0x00, 0x00, 0x00, 0x31, 0x01, 0x03, IAC, EOR };

      Recorder whole = new Recorder ();
      new TelnetProcessor (whole).listen (stream);

      for (int chunkSize = 1; chunkSize <= stream.length; chunkSize++)
      {
        Recorder chunked = new Recorder ();
        TelnetProcessor chunkedProcessor = new TelnetProcessor (chunked);

        for (int ptr = 0; ptr < stream.length; ptr += chunkSize)
          chunkedProcessor.listen (
              Arrays.copyOfRange (stream, ptr, Math.min (ptr + chunkSize, stream.length)));

        assertEquals (whole.summary (), chunked.summary (),
            "divergiu com blocos de " + chunkSize + " byte(s)");
      }
    }
  }

  // ---------------------------------------------------------------------------------//
  private static class Recorder implements TelnetCommandProcessor
  // ---------------------------------------------------------------------------------//
  {
    final List<byte[]> data = new ArrayList<> ();
    final List<byte[]> records = new ArrayList<> ();
    final List<byte[]> commands = new ArrayList<> ();
    final List<byte[]> subcommands = new ArrayList<> ();

    @Override
    public void processData (byte[] buffer, int length)
    {
      data.add (Arrays.copyOf (buffer, length));
    }

    @Override
    public void processRecord (byte[] buffer, int length)
    {
      records.add (Arrays.copyOf (buffer, length));
    }

    @Override
    public void processTelnetCommand (byte[] buffer, int length)
    {
      commands.add (Arrays.copyOf (buffer, length));
    }

    @Override
    public void processTelnetSubcommand (byte[] buffer, int length)
    {
      subcommands.add (Arrays.copyOf (buffer, length));
    }

    boolean isEmpty ()
    {
      return data.isEmpty () && records.isEmpty () && commands.isEmpty ()
          && subcommands.isEmpty ();
    }

    String summary ()
    {
      StringBuilder text = new StringBuilder ();
      append (text, "DATA", data);
      append (text, "REC ", records);
      append (text, "CMD ", commands);
      append (text, "SUB ", subcommands);
      return text.toString ();
    }

    private void append (StringBuilder text, String label, List<byte[]> buffers)
    {
      for (byte[] buffer : buffers)
        text.append (label).append (' ').append (java.util.HexFormat.of ().formatHex (buffer))
            .append ('\n');
    }
  }
}
