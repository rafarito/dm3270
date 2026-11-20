package com.bytezone.dm3270.attributes;

// Cor 3270 independente de toolkit grafico (convertida para JavaFX em FxPalette).
// ScreenContext.matches compara por ==: preserve estas instancias e os valores RGB exatos.
// -----------------------------------------------------------------------------------//
public record TerminalColor (int red, int green, int blue, double opacity)
// -----------------------------------------------------------------------------------//
{
  public static final TerminalColor WHITE_SMOKE = rgb (245, 245, 245);
  public static final TerminalColor DODGER_BLUE = rgb (30, 144, 255);
  public static final TerminalColor RED = rgb (255, 0, 0);
  public static final TerminalColor PINK = rgb (255, 192, 203);
  public static final TerminalColor LIME = rgb (0, 255, 0);
  public static final TerminalColor TURQUOISE = rgb (64, 224, 208);
  public static final TerminalColor YELLOW = rgb (255, 255, 0);
  public static final TerminalColor BLACK = rgb (0, 0, 0);
  public static final TerminalColor DARK_BLUE = rgb (0, 0, 139);
  public static final TerminalColor ORANGE = rgb (255, 165, 0);
  public static final TerminalColor PURPLE = rgb (128, 0, 128);
  public static final TerminalColor PALE_GREEN = rgb (152, 251, 152);
  public static final TerminalColor PALE_TURQUOISE = rgb (175, 238, 238);
  public static final TerminalColor GREY = rgb (128, 128, 128);

  // ---------------------------------------------------------------------------------//
  public TerminalColor
  // ---------------------------------------------------------------------------------//
  {
    if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255)
      throw new IllegalArgumentException (
          String.format ("componente fora de 0..255: (%d, %d, %d)", red, green, blue));

    if (opacity < 0 || opacity > 1)
      throw new IllegalArgumentException ("opacidade fora de 0..1: " + opacity);
  }

  // ---------------------------------------------------------------------------------//
  public static TerminalColor rgb (int red, int green, int blue)
  // ---------------------------------------------------------------------------------//
  {
    return new TerminalColor (red, green, blue, 1.0);
  }

  // Mantem o formato 0xrrggbbaa de javafx.scene.paint.Color.toString (usado em logs).
  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    return String.format ("0x%02x%02x%02x%02x", red, green, blue,
        Math.round (opacity * 255));
  }
}
