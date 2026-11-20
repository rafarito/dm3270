package com.bytezone.dm3270.streams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.commands.WriteCommand;
import com.bytezone.dm3270.display.HeadlessScreenTarget;
import com.bytezone.dm3270.runtime.Source;
import com.bytezone.dm3270.runtime.TerminalFunction;

// -----------------------------------------------------------------------------------//
@DisplayName ("TelnetListener - os dois caminhos que passavam pela thread grafica")
class TelnetListenerHeadlessTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  private static byte[] wire (byte... command)
  // ---------------------------------------------------------------------------------//
  {
    return new WriteCommand (command, 0, command.length).getTelnetData ();
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o resumo do telnet chega a tela quando a conexao cai")
  void closeWritesTheSummary ()
  // ---------------------------------------------------------------------------------//
  {
    TelnetState telnetState = new TelnetState ();
    HeadlessScreenTarget screen = new HeadlessScreenTarget ();

    TelnetListener listener = new TelnetListener (screen, TerminalFunction.TERMINAL,
        telnetState, Runnable::run);

    listener.close ();

    assertEquals (1, screen.calls.stream ()
        .filter (c -> c.startsWith ("displayText:")).count ());
    assertTrue (screen.calls.contains ("displayText:" + telnetState.getSummary ()));
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("um registro 3270 e processado pela tela sem passar por toolkit nenhum")
  void terminalRecordReachesTheScreen ()
  // ---------------------------------------------------------------------------------//
  {
    TelnetState telnetState = new TelnetState ();
    HeadlessScreenTarget screen = new HeadlessScreenTarget ();

    TelnetListener listener = new TelnetListener (screen, TerminalFunction.TERMINAL,
        telnetState, Runnable::run);

    // Erase Write com WCC de reset, uma SBA para a posicao 0 e o texto "AB" em EBCDIC
    listener.listen (Source.SERVER, wire (Command.ERASE_WRITE_F5, (byte) 0x00, //
                                          (byte) 0x11, (byte) 0x40, (byte) 0x40, //
                                          (byte) 0xC1, (byte) 0xC2),
                     LocalDateTime.now (), true);

    assertTrue (screen.getScreenText ().startsWith ("AB"), screen.getScreenText ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o mesmo listener aceita varios registros em sequencia")
  void severalRecordsInARow ()
  // ---------------------------------------------------------------------------------//
  {
    TelnetState telnetState = new TelnetState ();
    HeadlessScreenTarget screen = new HeadlessScreenTarget ();

    TelnetListener listener = new TelnetListener (screen, TerminalFunction.TERMINAL,
        telnetState, Runnable::run);

    listener.listen (Source.SERVER, wire (Command.ERASE_WRITE_F5, (byte) 0x00, //
                                          (byte) 0x11, (byte) 0x40, (byte) 0x40, (byte) 0xC1),
                     LocalDateTime.now (), true);
    listener.listen (Source.SERVER, wire (Command.ERASE_WRITE_F5, (byte) 0x00, //
                                          (byte) 0x11, (byte) 0x40, (byte) 0x40, (byte) 0xC2),
                     LocalDateTime.now (), true);

    assertTrue (screen.getScreenText ().startsWith ("B"), screen.getScreenText ());
  }
}
