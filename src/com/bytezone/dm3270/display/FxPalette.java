package com.bytezone.dm3270.display;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.bytezone.dm3270.attributes.TerminalColor;

import javafx.scene.paint.Color;

/**
 * Converte {@link TerminalColor} em {@code Color} do JavaFX (bit-a-bit via Color.rgb).
 * O cache evita alocacoes por celula a cada redesenho da tela.
 */
// -----------------------------------------------------------------------------------//
public final class FxPalette
// -----------------------------------------------------------------------------------//
{
  private static final Map<TerminalColor, Color> cache = new ConcurrentHashMap<> ();

  // ---------------------------------------------------------------------------------//
  private FxPalette ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // ---------------------------------------------------------------------------------//
  public static Color toFx (TerminalColor color)
  // ---------------------------------------------------------------------------------//
  {
    return cache.computeIfAbsent (color,
        c -> Color.rgb (c.red (), c.green (), c.blue (), c.opacity ()));
  }
}
