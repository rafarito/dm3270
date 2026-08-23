package com.bytezone.dm3270.attributes;

public abstract class ColorAttribute extends Attribute
{
  public static final byte COLOR_NEUTRAL1 = 0x00;
  public static final byte COLOR_BLUE = (byte) 0xF1;
  public static final byte COLOR_RED = (byte) 0xF2;
  public static final byte COLOR_PINK = (byte) 0xF3;
  public static final byte COLOR_GREEN = (byte) 0xF4;
  public static final byte COLOR_TURQUOISE = (byte) 0xF5;
  public static final byte COLOR_YELLOW = (byte) 0xF6;
  public static final byte COLOR_NEUTRAL2 = (byte) 0xF7;
  public static final byte COLOR_BLACK = (byte) 0xF8;
  public static final byte COLOR_DEEP_BLUE = (byte) 0xF9;
  public static final byte COLOR_ORANGE = (byte) 0xFA;
  public static final byte COLOR_PURPLE = (byte) 0xFB;
  public static final byte COLOR_PALE_GREEN = (byte) 0xFC;
  public static final byte COLOR_PALE_TURQUOISE = (byte) 0xFD;
  public static final byte COLOR_GREY = (byte) 0xFE;
  public static final byte COLOR_WHITE = (byte) 0xFF;

  static String[] colorNames =
      { "Neutral1", "Blue", "Red", "Pink", "Green", "Turquoise", "Yellow", "Neutral2",
        "Black", "Deep blue", "Orange", "Purple", "Pale green", "Pale turquoise", "Grey",
        "White" };

  // Neutral1, Neutral2 e White apontam de proposito para a MESMA instancia: sao 16 slots
  // para 14 objetos. ScreenContext.matches compara cor por identidade (==), e o pool de
  // ContextManager depende desse aliasing - ver TerminalColor.
  public static final TerminalColor[] colors = //
      { TerminalColor.WHITE_SMOKE,     // Neutral1
        TerminalColor.DODGER_BLUE,     // or DEEPSKYBLUE, SKYBLUE, LIGHTSKYBLUE
        TerminalColor.RED,             //
        TerminalColor.PINK,            //
        TerminalColor.LIME,            //
        TerminalColor.TURQUOISE,       //
        TerminalColor.YELLOW,          //
        TerminalColor.WHITE_SMOKE,     // Neutral2 - mesma instancia de Neutral1
        TerminalColor.BLACK,           //
        TerminalColor.DARK_BLUE,       //
        TerminalColor.ORANGE,          //
        TerminalColor.PURPLE,          //
        TerminalColor.PALE_GREEN,      //
        TerminalColor.PALE_TURQUOISE,  //
        TerminalColor.GREY,            //
        TerminalColor.WHITE_SMOKE      // White - mesma instancia de Neutral1
  };

  protected final TerminalColor color;

  public static String getName (TerminalColor searchColor)
  {
    int count = 0;
    for (TerminalColor color : colors)
    {
      if (color == searchColor)
        return colorNames[count];
      ++count;
    }
    return searchColor.toString ();
  }

  public ColorAttribute (AttributeType type, byte byteType, byte value)
  {
    super (type, byteType, value);
    color = colors[value & 0x0F];
  }

  public TerminalColor getColor ()
  {
    return color;
  }

  public static String colorName (byte value)
  {
    return colorNames[value & 0x0F];
  }

  @Override
  public String toString ()
  {
    return String.format ("%-12s : %02X %-12s", name (), attributeValue, //
                          colorName (attributeValue));
  }
}