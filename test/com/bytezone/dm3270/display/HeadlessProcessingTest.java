package com.bytezone.dm3270.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.commands.WriteCommand;

/*
 * Comandos 3270 executados de verdade, sem interface grafica.
 *
 * Ate a onda de desacoplamento isto era impossivel. Buffer.process pedia a Screen concreta -
 * 1.010 linhas que estendem Canvas e constroem banco SQLite, tres janelas e um class loader
 * de plugins no construtor. Nao havia como executar um Write e verificar o que ele escreveu.
 * Era essa a razao pela qual display, application, assistant e console estavam fora do PIT:
 * nao "faltou escopo", eram intestaveis.
 *
 * Agora um HeadlessScreenTarget basta, e o buffer de tela dentro dele e o real - o mesmo Pen
 * e o mesmo vetor de ScreenPosition que a aplicacao usa. Os bytes abaixo sao fluxo 3270
 * legitimo, montado a mao, e o que se verifica e o texto que sobra na tela.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("Processamento headless de comandos 3270")
class HeadlessProcessingTest
// -----------------------------------------------------------------------------------//
{
  private static final byte ERASE_WRITE = 0x05;
  private static final byte WRITE = 0x01;

  private static final byte WCC_RESET_KEYBOARD = (byte) 0xC2;
  private static final byte WCC_ALARM = (byte) 0xC4;

  private static final byte SBA = 0x11;              // Set Buffer Address
  private static final byte SF = 0x1D;               // Start Field
  private static final byte IC = 0x13;               // Insert Cursor

  // Byte de atributo do Start Field. O bit 0x20 e o de protecao.
  private static final int UNPROTECTED = 0x00;
  private static final int PROTECTED = 0x20;

  /*
   * Enderecos de buffer. No formato de 12 bits que os hosts usam, os dois bytes carregam
   * seis bits cada: posicao = (b1 & 0x3F) << 6 | (b2 & 0x3F), e o bit alto de b1 tem de
   * estar ligado para nao cair no formato de 14 bits. Os valores vem da tabela de
   * BufferAddress.
   */
  private static final int AT_0_LOW = 0x40;          // posicao 0
  private static final int AT_0_HIGH = 0x40;
  private static final int AT_80_HIGH = 0xC1;        // posicao 80 = 1 * 64 + 16
  private static final int AT_80_LOW = 0x50;

  private final HeadlessScreenTarget screen = new HeadlessScreenTarget ();

  // ---------------------------------------------------------------------------------//
  private static byte[] bytes (int... values)
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = new byte[values.length];
    for (int i = 0; i < values.length; i++)
      buffer[i] = (byte) values[i];
    return buffer;
  }

  /*
   * Texto em EBCDIC. 0xC1 e 'A', 0x81 e 'a', 0x40 e espaco - a tabela nao e contigua, entao
   * os testes usam bytes explicitos em vez de aritmetica sobre caracteres.
   */
  // ---------------------------------------------------------------------------------//
  private void process (int... data)
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = bytes (data);
    Command.getCommand (buffer, 0, buffer.length).process (screen);
  }

  // ---------------------------------------------------------------------------------//
  private String line (int row)
  // ---------------------------------------------------------------------------------//
  {
    String[] lines = screen.getScreenText ().split ("\n");
    return lines[row];
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("Write escreve na tela")
  class Writing
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("um texto no inicio da tela aparece na primeira linha")
    void writesTextAtOrigin ()
    {
      // Erase Write, WCC, SBA 0000, "HI"
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW, 0xC8, 0xC9);

      assertTrue (line (0).startsWith ("HI"), "linha 0 = [" + line (0) + "]");
    }

    @Test
    @DisplayName ("o endereco de buffer posiciona o texto na linha certa")
    void bufferAddressPositionsText ()
    {
      // SBA para a posicao 80 (inicio da linha 1 numa tela de 80 colunas)
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_80_HIGH, AT_80_LOW, 0xC1, 0xC2);

      assertTrue (line (1).startsWith ("AB"), "linha 1 = [" + line (1) + "]");
      assertFalse (line (0).startsWith ("AB"), "a linha 0 deve ter ficado vazia");
    }

    @Test
    @DisplayName ("Erase Write limpa o que estava antes")
    void eraseWriteClearsPreviousContent ()
    {
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW, 0xC8, 0xC9);
      assertTrue (line (0).startsWith ("HI"));

      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW, 0xC1);

      assertTrue (line (0).startsWith ("A"), "linha 0 = [" + line (0) + "]");
      assertFalse (line (0).startsWith ("HI"), "o conteudo anterior tinha de sair");
      assertTrue (screen.calls.contains ("clearScreen"));
    }

    @Test
    @DisplayName ("Write sem erase preserva o que ja estava na tela")
    void writeKeepsPreviousContent ()
    {
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW, 0xC8, 0xC9);
      process (WRITE, WCC_RESET_KEYBOARD, SBA, AT_80_HIGH, AT_80_LOW, 0xC1);

      assertTrue (line (0).startsWith ("HI"), "linha 0 = [" + line (0) + "]");
      assertTrue (line (1).startsWith ("A"), "linha 1 = [" + line (1) + "]");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("orders")
  class Orders
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("Start Field ocupa uma posicao da tela")
    void startFieldOccupiesAPosition ()
    {
      // SF com atributo protegido, seguido de texto
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW, SF, 0xF0, 0xC1,
          0xC2);

      // getScreenText marca a posicao do atributo com % e o texto vem depois
      assertTrue (line (0).startsWith ("%AB"), "linha 0 = [" + line (0) + "]");
    }

    @Test
    @DisplayName ("Insert Cursor marca a posicao pedida pelo host")
    void insertCursorRecordsThePosition ()
    {
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_80_HIGH, AT_80_LOW, IC);

      assertEquals (80, screen.getInsertedCursorPosition (),
          "o host pediu o cursor no inicio da linha 1");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("WriteControlCharacter")
  class ControlCharacter
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o bit de reset do teclado destrava o teclado")
    void resetUnlocksTheKeyboard ()
    {
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW, 0xC1);

      assertFalse (screen.isKeyboardLocked (), "o WCC pediu para destravar");
      assertTrue (screen.calls.contains ("restoreKeyboard"));
    }

    @Test
    @DisplayName ("o bit de alarme dispara o alarme")
    void alarmBitSoundsTheAlarm ()
    {
      process (ERASE_WRITE, WCC_ALARM, SBA, AT_0_HIGH, AT_0_LOW, 0xC1);

      assertTrue (screen.calls.contains ("soundAlarm"));
    }

    @Test
    @DisplayName ("o comando trava o teclado antes de escrever")
    void writeLocksTheKeyboardFirst ()
    {
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW, 0xC1);

      assertEquals ("setCurrentScreen:DEFAULT", screen.calls.get (0),
          "escolher a geometria vem primeiro");
      assertEquals ("lockKeyboard:Erase Write", screen.calls.get (1),
          "e travar o teclado vem antes de limpar e escrever");
      assertEquals ("clearScreen", screen.calls.get (2));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("tela alternativa")
  class AlternateScreen
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("Erase Write Alternate troca a geometria da tela")
    void alternateChangesDimensions
        ()
    {
      assertEquals (24, screen.getScreenDimensions ().rows);

      // Erase Write Alternate
      process (0x0D, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW, 0xC1);

      assertEquals (43, screen.getScreenDimensions ().rows,
          "a tela alternativa deste dublê tem 43 linhas");
      assertTrue (screen.calls.contains ("setCurrentScreen:ALTERNATE"));
    }
  }

  /*
   * Os campos da tela, e os dois ramos de WriteCommand.process que dependem deles.
   *
   * Estes testes nao existiam porque nao podiam existir: getFieldCount deste dublê devolvia
   * 0 fixo, ja que o FieldManager exigia a Screen concreta e subia uma thread SQLite no
   * construtor. Com a porta FieldHost e o DatasetStore injetado, o FieldManager e real aqui -
   * e os ramos guardados por `getFieldCount () > 0` passaram a ser percorridos.
   */
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("os campos da tela")
  class Fields
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("um Start Field cria um campo de verdade")
    void startFieldCreatesAField ()
    {
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW,
               SF, UNPROTECTED, 0xC1, 0xC2);

      assertTrue (screen.getFieldCount () > 0,
          "esperava ao menos um campo, veio " + screen.getFieldCount ());
    }

    @Test
    @DisplayName ("uma tela sem Start Field nao tem campo nenhum")
    void screenWithoutStartFieldHasNoFields ()
    {
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW, 0xC8, 0xC9);

      assertEquals (0, screen.getFieldCount (), "texto solto nao forma campo");
    }

    @Test
    @DisplayName ("getFieldAt encontra o campo que cobre a posicao")
    void fieldAtFindsTheCoveringField ()
    {
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW,
               SF, UNPROTECTED, 0xC1, 0xC2, 0xC3);

      assertTrue (screen.getFieldAt (2).isPresent (),
          "a posicao 2 esta dentro do campo que comeca em 0");
    }

    @Test
    @DisplayName ("o campo desprotegido e o campo inicial do cursor")
    void unprotectedFieldBecomesTheHomeField ()
    {
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW,
               SF, UNPROTECTED, 0xC1);

      assertTrue (screen.getHomeField ().isPresent (), "esperava um campo inicial");
    }

    @Test
    @DisplayName ("um campo protegido nao serve de campo inicial")
    void protectedFieldIsNotAHomeField ()
    {
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW,
               SF, PROTECTED, 0xC1);

      assertTrue (screen.getFieldCount () > 0, "o campo protegido existe");
      assertFalse (screen.getHomeField ().isPresent (),
          "mas nao pode ser o campo inicial do cursor");
    }

    @Test
    @DisplayName ("havendo campo e teclado livre, o comando chega a checkRecording")
    void recordingBranchIsReached ()
    {
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW,
               SF, UNPROTECTED, 0xC1);

      assertTrue (screen.calls.contains ("checkRecording"),
          "calls = " + screen.calls);
    }

    @Test
    @DisplayName ("havendo campo e teclado livre, o comando chega a processPluginAuto")
    void pluginAutoBranchIsReached ()
    {
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW,
               SF, UNPROTECTED, 0xC1);

      assertTrue (screen.calls.contains ("processPluginAuto"),
          "calls = " + screen.calls);
    }

    @Test
    @DisplayName ("sem campo nenhum, os dois ramos nao sao percorridos")
    void bothBranchesAreSkippedWithoutFields ()
    {
      process (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW, 0xC8);

      assertFalse (screen.calls.contains ("checkRecording"), "calls = " + screen.calls);
      assertFalse (screen.calls.contains ("processPluginAuto"), "calls = " + screen.calls);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o comando montado")
  class Parsing
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("Erase Write vira um WriteCommand com as orders na ordem")
    void commandCarriesItsOrders ()
    {
      byte[] buffer =
          bytes (ERASE_WRITE, WCC_RESET_KEYBOARD, SBA, AT_0_HIGH, AT_0_LOW, 0xC8, 0xC9);
      Command command = Command.getCommand (buffer, 0, buffer.length);

      assertTrue (command instanceof WriteCommand, "veio " + command.getClass ());

      List<String> lines = List.of (command.toString ().split ("\n"));
      assertTrue (lines.get (0).contains ("Erase Write"), lines.get (0));
    }
  }
}
