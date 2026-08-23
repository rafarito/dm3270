package com.bytezone.dm3270.telnet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.dm3270.buffers.Buffer;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.streams.TelnetState;
import com.bytezone.dm3270.telnet.TelnetCommand.CommandName;
import com.bytezone.dm3270.telnet.TelnetCommand.CommandType;

// -----------------------------------------------------------------------------------//
@DisplayName ("TelnetCommand - negociacao de opcoes telnet")
class TelnetCommandTest
// -----------------------------------------------------------------------------------//
{
  private TelnetState telnetState;

  @BeforeEach
  void setUp ()
  {
    telnetState = new TelnetState ();
  }

  // process() nao usa a tela em nenhum ramo, por isso null basta aqui
  private static final com.bytezone.dm3270.display.Screen NO_SCREEN = null;

  private TelnetCommand command (int... bytes)
  {
    byte[] buffer = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++)
      buffer[i] = (byte) bytes[i];

    return new TelnetCommand (telnetState, buffer);
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("comandos de dois bytes")
  class TwoByteCommands
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("IAC NOP e um no-op sem tipo")
    void noOp ()
    {
      TelnetCommand telnetCommand = command (0xFF, 0xF1);

      assertEquals (CommandName.NO_OP, telnetCommand.commandName ());
      assertNull (telnetCommand.commandType ());
      assertEquals ("NoOp", telnetCommand.getName ());
    }

    @Test
    @DisplayName ("IAC IP interrompe o processo")
    void interruptProcess ()
    {
      TelnetCommand telnetCommand = command (0xFF, 0xF4);

      assertEquals (CommandName.INTERRUPT_PROCESS, telnetCommand.commandName ());
      assertNull (telnetCommand.commandType ());
    }

    @ParameterizedTest (name = "IAC {0}")
    @ValueSource (ints = { 0xF0, 0xF5, 0xF6, 0xF9, 0xEF, 0x00 })
    @DisplayName ("qualquer outro comando de dois bytes e recusado")
    void rejectsUnknownTwoByteCommand (int command)
    {
      assertThrows (IllegalArgumentException.class, () -> command (0xFF, command));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("comandos de tres bytes")
  class ThreeByteCommands
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "{0} -> {1}")
    @CsvSource ({ "0xFD, DO", "0xFE, DONT", "0xFB, WILL", "0xFC, WONT" })
    @DisplayName ("os quatro verbos de negociacao")
    void mapsVerbs (int verb, CommandName expected)
    {
      assertEquals (expected, command (0xFF, verb, 0x28).commandName ());
    }

    @ParameterizedTest (name = "tipo {0} -> {1}")
    @CsvSource ({ "0x18, TERMINAL_TYPE", "0x19, EOR", "0x00, BINARY",
                  "0x28, TN3270_EXTENDED" })
    @DisplayName ("os quatro tipos de opcao conhecidos")
    void mapsTypes (int type, CommandType expected)
    {
      assertEquals (expected, command (0xFF, 0xFD, type).commandType ());
    }

    @ParameterizedTest (name = "tipo {0}")
    @ValueSource (ints = { 0x03, 0x20, 0x27, 0x7F })
    @DisplayName ("um tipo desconhecido nao interrompe o parsing")
    void unknownTypeIsTolerated (int type)
    {
      // o codigo apenas registra no console: houve relato de FB 03, FD 20 e FD 27
      TelnetCommand telnetCommand = command (0xFF, 0xFD, type);

      assertEquals (CommandName.DO, telnetCommand.commandName ());
      assertNull (telnetCommand.commandType ());
    }

    @ParameterizedTest (name = "verbo {0}")
    @ValueSource (ints = { 0xF1, 0xF4, 0x00, 0xFF })
    @DisplayName ("um verbo desconhecido e recusado")
    void rejectsUnknownVerb (int verb)
    {
      assertThrows (IllegalArgumentException.class, () -> command (0xFF, verb, 0x28));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("tamanhos invalidos")
  class InvalidLengths
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("um buffer de quatro bytes e recusado")
    void tooLong ()
    {
      IllegalArgumentException e = assertThrows (IllegalArgumentException.class,
                                                () -> command (0xFF, 0xFD, 0x28, 0x00));

      assertEquals ("Buffer incorrect length", e.getMessage ());
    }

    @Test
    @DisplayName ("um buffer de um byte estoura ao ler o comando")
    void tooShort ()
    {
      assertThrows (ArrayIndexOutOfBoundsException.class, () -> command (0xFF));
    }

    @Test
    @DisplayName ("o comprimento pode ser menor que o buffer")
    void explicitLength ()
    {
      // o construtor de 3 argumentos permite reaproveitar um buffer maior
      byte[] buffer = { (byte) 0xFF, (byte) 0xF1, 0x00, 0x00 };

      TelnetCommand telnetCommand = new TelnetCommand (telnetState, buffer, 2);

      assertEquals (CommandName.NO_OP, telnetCommand.commandName ());
      assertEquals (2, telnetCommand.size ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("resposta a um DO do mainframe")
  class RespondsToDo
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "DO {1} com preferencia ligada -> WILL")
    @CsvSource ({ "0x28, TN3270_EXTENDED", "0x18, TERMINAL_TYPE", "0x19, EOR",
                  "0x00, BINARY" })
    @DisplayName ("aceita a opcao quando a preferencia esta ligada")
    void acceptsWhenPreferred (int type, CommandType expected)
    {
      TelnetCommand telnetCommand = command (0xFF, 0xFD, type);

      telnetCommand.process (NO_SCREEN);

      assertEquals (expected, telnetCommand.commandType ());
      assertArrayEquals (new byte[] { (byte) 0xFF, (byte) 0xFB, (byte) type },
                         reply (telnetCommand));
    }

    @Test
    @DisplayName ("recusa o TN3270E quando a preferencia esta desligada")
    void refusesExtendedWhenNotPreferred ()
    {
      telnetState.setDo3270Extended (false);
      TelnetCommand telnetCommand = command (0xFF, 0xFD, 0x28);

      telnetCommand.process (NO_SCREEN);

      assertArrayEquals (new byte[] { (byte) 0xFF, (byte) 0xFC, 0x28 },
                         reply (telnetCommand));
      assertFalse (telnetState.does3270Extended ());
    }

    @Test
    @DisplayName ("recusa binario, EOR e tipo de terminal quando desligados")
    void refusesTheOthersWhenNotPreferred ()
    {
      telnetState.setDo3270Extended (false);       // senao o OR mascara o resultado
      telnetState.setDoBinary (false);
      telnetState.setDoEOR (false);
      telnetState.setDoTerminalType (false);

      for (int type : new int[] { 0x00, 0x19, 0x18 })
      {
        TelnetCommand telnetCommand = command (0xFF, 0xFD, type);
        telnetCommand.process (NO_SCREEN);

        assertArrayEquals (new byte[] { (byte) 0xFF, (byte) 0xFC, (byte) type },
                           reply (telnetCommand));
      }

      assertFalse (telnetState.doesBinary ());
      assertFalse (telnetState.doesEOR ());
      assertFalse (telnetState.doesTerminalType ());
    }

    @Test
    @DisplayName ("aceitar a opcao tambem grava o estado negociado")
    void acceptingUpdatesState ()
    {
      command (0xFF, 0xFD, 0x28).process (NO_SCREEN);

      assertTrue (telnetState.does3270Extended ());
    }

    @Test
    @DisplayName ("um DO de tipo desconhecido e sempre recusado")
    void unknownTypeIsRefused ()
    {
      TelnetCommand telnetCommand = command (0xFF, 0xFD, 0x27);

      telnetCommand.process (NO_SCREEN);

      // reply[1] comeca como WONT e nenhum ramo o troca
      assertArrayEquals (new byte[] { (byte) 0xFF, (byte) 0xFC, 0x27 },
                         reply (telnetCommand));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("resposta a um WILL do mainframe")
  class RespondsToWill
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("confirma com DO quando a opcao ja esta negociada")
    void confirmsWithDo ()
    {
      telnetState.setDoes3270Extended (true);
      TelnetCommand telnetCommand = command (0xFF, 0xFB, 0x28);

      telnetCommand.process (NO_SCREEN);

      assertArrayEquals (new byte[] { (byte) 0xFF, (byte) 0xFD, 0x28 },
                         reply (telnetCommand));
    }

    @Test
    @DisplayName ("responde DONT quando a opcao nao estava negociada")
    void refusesWithDont ()
    {
      TelnetCommand telnetCommand = command (0xFF, 0xFB, 0x28);

      telnetCommand.process (NO_SCREEN);

      assertArrayEquals (new byte[] { (byte) 0xFF, (byte) 0xFE, 0x28 },
                         reply (telnetCommand));
      // mesmo respondendo DONT, o estado passa a true
      assertTrue (telnetState.does3270Extended ());
    }

    @ParameterizedTest (name = "WILL {1}")
    @CsvSource ({ "0x18, TERMINAL_TYPE", "0x19, EOR", "0x00, BINARY" })
    @DisplayName ("as outras opcoes tambem ligam o estado")
    void turnsStateOn (int type, CommandType expected)
    {
      telnetState.setDo3270Extended (false);
      TelnetCommand telnetCommand = command (0xFF, 0xFB, type);

      telnetCommand.process (NO_SCREEN);

      assertEquals (expected, telnetCommand.commandType ());
      assertArrayEquals (new byte[] { (byte) 0xFF, (byte) 0xFE, (byte) type },
                         reply (telnetCommand));
    }

    @Test
    @DisplayName ("um WILL de tipo desconhecido e recusado com DONT")
    void unknownTypeIsRefusedWithDont ()
    {
      // reply[1] comeca como DONT: uma opcao que nao reconhecemos nao pode ser aceita
      TelnetCommand telnetCommand = command (0xFF, 0xFB, 0x27);

      telnetCommand.process (NO_SCREEN);

      assertArrayEquals (new byte[] { (byte) 0xFF, (byte) 0xFE, 0x27 },
                         reply (telnetCommand));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("resposta a DONT e WONT")
  class RespondsToRefusals
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "{0} desliga a opcao")
    @ValueSource (ints = { 0xFE, 0xFC })            // DONT, WONT
    @DisplayName ("desligam o estado de cada opcao sem responder nada")
    void turnStateOff (int verb)
    {
      telnetState.setDoes3270Extended (true);
      telnetState.setDoesBinary (true);
      telnetState.setDoesEOR (true);
      telnetState.setDoesTerminalType (true);

      for (int type : new int[] { 0x28, 0x00, 0x19, 0x18 })
      {
        TelnetCommand telnetCommand = command (0xFF, verb, type);
        telnetCommand.process (NO_SCREEN);

        assertFalse (telnetCommand.getReply ().isPresent (),
                     "recusa nao deveria gerar resposta");
      }

      assertFalse (telnetState.does3270Extended ());
      assertFalse (telnetState.doesBinary ());
      assertFalse (telnetState.doesEOR ());
      assertFalse (telnetState.doesTerminalType ());
    }

    @Test
    @DisplayName ("uma recusa de tipo desconhecido nao muda nada")
    void unknownTypeIsIgnored ()
    {
      telnetState.setDoes3270Extended (true);

      command (0xFF, 0xFC, 0x27).process (NO_SCREEN);

      assertTrue (telnetState.does3270Extended ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("serializacao e texto")
  class Serialisation
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("getTelnetData devolve os bytes originais, sem escape nem EOR")
    void telnetDataIsVerbatim ()
    {
      byte[] buffer = { (byte) 0xFF, (byte) 0xFD, 0x28 };

      TelnetCommand telnetCommand = new TelnetCommand (telnetState, buffer);

      assertArrayEquals (buffer, telnetCommand.getTelnetData ());
      assertSame (telnetCommand.getData (), telnetCommand.getTelnetData ());
    }

    @Test
    @DisplayName ("toString junta verbo e tipo")
    void describesBoth ()
    {
      assertEquals ("DO TN3270_EXTENDED", command (0xFF, 0xFD, 0x28).toString ());
      assertEquals ("WONT BINARY", command (0xFF, 0xFC, 0x00).toString ());
    }

    @Test
    @DisplayName ("sem tipo o toString deixa o segundo campo vazio")
    void describesWithoutType ()
    {
      assertEquals ("NO_OP ", command (0xFF, 0xF1).toString ());
      assertEquals ("DO ", command (0xFF, 0xFD, 0x27).toString ());
    }

    @Test
    @DisplayName ("getName repete o toString fora do caso NoOp")
    void nameFallsBackToToString ()
    {
      TelnetCommand telnetCommand = command (0xFF, 0xFD, 0x28);

      assertEquals (telnetCommand.toString (), telnetCommand.getName ());
    }
  }

  // ---------------------------------------------------------------------------------//
  private static byte[] reply (TelnetCommand telnetCommand)
  // ---------------------------------------------------------------------------------//
  {
    Buffer buffer = telnetCommand.getReply ()
        .orElseThrow ( () -> new AssertionError ("nenhuma resposta foi gerada"));

    return buffer.getData ();
  }
}
