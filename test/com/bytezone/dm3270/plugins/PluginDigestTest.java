package com.bytezone.dm3270.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings ("deprecation")
// -----------------------------------------------------------------------------------//
@DisplayName ("PluginDigest - o MD5 e o hexadecimal da API de plugins")
class PluginDigestTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("md5 de valores conhecidos, em maiusculas")
  void md5DeValoresConhecidos ()
  // ---------------------------------------------------------------------------------//
  {
    assertEquals ("D41D8CD98F00B204E9800998ECF8427E", PluginDigest.md5 (new byte[0]));
    assertEquals ("900150983CD24FB0D6963F7D28E17F72",
                  PluginDigest.md5 ("abc".getBytes (StandardCharsets.US_ASCII)));
  }

  // 0x0F testa zero a esquerda e 0xFF testa byte sem sinal em maiusculas.
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("toHex: dois digitos por byte, maiusculas, sem sinal")
  void toHexDoisDigitosPorByte ()
  // ---------------------------------------------------------------------------------//
  {
    assertEquals ("", PluginDigest.toHex (new byte[0]));
    assertEquals ("000F10FF",
                  PluginDigest.toHex (new byte[] { 0x00, 0x0F, 0x10, (byte) 0xFF }));
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o digito 10 vira A, e nao o caractere seguinte ao 9")
  void oDigitoDezViraA ()
  // ---------------------------------------------------------------------------------//
  {
    assertEquals ("AA", PluginDigest.toHex (new byte[] { (byte) 0xAA }));
    assertEquals ("9AA9", PluginDigest.toHex (new byte[] { (byte) 0x9A, (byte) 0xA9 }));
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("toHex cobre os 256 valores, sempre com dois caracteres")
  void toHexCobreOs256Valores ()
  // ---------------------------------------------------------------------------------//
  {
    byte[] todos = new byte[256];
    for (int i = 0; i < 256; i++)
      todos[i] = (byte) i;

    String hex = PluginDigest.toHex (todos);

    assertEquals (512, hex.length ());
    assertEquals ("000102", hex.substring (0, 6));
    assertEquals ("FDFEFF", hex.substring (506));
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o DefaultPlugin delega os dois sem mudar o resultado")
  void oDefaultPluginDelegaSemMudarOResultado ()
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = "dm3270".getBytes (StandardCharsets.US_ASCII);
    DefaultPlugin plugin = new TestPlugin ();

    assertEquals (PluginDigest.md5 (buffer), plugin.getMD5 (buffer));
    assertEquals (PluginDigest.toHex (buffer), DefaultPlugin.toHex (buffer));
  }

  // ---------------------------------------------------------------------------------//
  private static final class TestPlugin extends DefaultPlugin
  // ---------------------------------------------------------------------------------//
  {
  }
}
