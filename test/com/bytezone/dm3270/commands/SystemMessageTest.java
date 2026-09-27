package com.bytezone.dm3270.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bytezone.dm3270.display.HeadlessScreenTarget;
import com.bytezone.dm3270.utilities.Dm3270Utility;

/*
 * A unica coisa que o SystemMessage pede a tela: avisar que ela virou console.
 *
 * Isso so acontece num caminho estreito - um Write sem erase com tres ordens (SBA, SF,
 * texto), logo depois de um Write com duas, e com um texto de exatamente 1.600 caracteres
 * que comeca pelo IEA371I do IPL. O caminho nao tinha teste; e ele que prova que o
 * SystemMessage alcanca a tela, qualquer que seja o tipo pelo qual a guarda.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("SystemMessage - reconhecimento do console de IPL")
class SystemMessageTest
// -----------------------------------------------------------------------------------//
{
  private static final int WRITE = 0x01;
  private static final int WCC = 0xC2;
  private static final int SBA = 0x11;
  private static final int SF = 0x1D;
  private static final int AT_0_HIGH = 0x40;
  private static final int AT_0_LOW = 0x40;
  private static final int PROTECTED = 0x20;

  private static final String IPL_TEXT =
      "  IEA371I SYS1.IPLPARM ON DEVICE 0A82 SELECTED FOR IPL PARAMETERS ";
  private static final String OTHER_TEXT = "  IEA371I NOTHING TO SEE HERE ";

  private final HeadlessScreenTarget screen = new HeadlessScreenTarget ();

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o IEA371I de IPL abre o log do console e marca a tela como console")
  void iplMessageMarksTheScreenAsConsole ()
  // ---------------------------------------------------------------------------------//
  {
    writeConsoleOutput (IPL_TEXT);

    int opened = screen.calls.indexOf ("openConsoleLog");
    int marked = screen.calls.indexOf ("setIsConsole");
    assertEquals (opened + 1, marked, screen.calls.toString ());
    assertEquals (1, screen.calls.stream ().filter ("setIsConsole"::equals).count ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("sem o SELECTED FOR IPL a tela nao vira console")
  void otherMessageLeavesTheScreenAlone ()
  // ---------------------------------------------------------------------------------//
  {
    writeConsoleOutput (OTHER_TEXT);

    assertFalse (screen.calls.contains ("setIsConsole"), screen.calls.toString ());
    assertFalse (screen.calls.contains ("openConsoleLog"), screen.calls.toString ());
  }

  // ---------------------------------------------------------------------------------//
  private void writeConsoleOutput (String prefix)
  // ---------------------------------------------------------------------------------//
  {
    // o Write de duas ordens que arma o reconhecimento: SBA e texto
    process (bytes (WRITE, WCC, SBA, AT_0_HIGH, AT_0_LOW, 0xC1));

    ByteArrayOutputStream out = new ByteArrayOutputStream ();
    out.writeBytes (bytes (WRITE, WCC, SBA, AT_0_HIGH, AT_0_LOW, SF, PROTECTED));
    out.writeBytes (ebcdic (String.format ("%-1600s", prefix)));
    process (out.toByteArray ());
  }

  // ---------------------------------------------------------------------------------//
  private void process (byte[] buffer)
  // ---------------------------------------------------------------------------------//
  {
    Command.getCommand (buffer, 0, buffer.length).process (screen);
  }

  // ---------------------------------------------------------------------------------//
  private static byte[] ebcdic (String text)
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = text.getBytes (Charset.forName (Dm3270Utility.EBCDIC));
    assertEquals (1600, buffer.length);
    return buffer;
  }

  // ---------------------------------------------------------------------------------//
  private static byte[] bytes (int... values)
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = new byte[values.length];
    for (int i = 0; i < values.length; i++)
      buffer[i] = (byte) values[i];
    return buffer;
  }
}
