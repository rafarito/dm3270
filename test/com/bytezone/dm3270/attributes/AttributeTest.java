package com.bytezone.dm3270.attributes;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.dm3270.attributes.Attribute.AttributeType;

// -----------------------------------------------------------------------------------//
@DisplayName ("Attribute - atributos de campo e estendidos")
class AttributeTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("fabrica Attribute.getAttribute()")
  class Factory
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("cria o atributo certo para cada codigo suportado")
    void createsSupportedAttributes ()
    {
      assertInstanceOf (ResetAttribute.class,
          Attribute.getAttribute (Attribute.XA_RESET, (byte) 0x00).orElseThrow ());
      assertInstanceOf (StartFieldAttribute.class,
          Attribute.getAttribute (Attribute.XA_START_FIELD, (byte) 0x60).orElseThrow ());
      assertInstanceOf (ExtendedHighlight.class,
          Attribute.getAttribute (Attribute.XA_HIGHLIGHTING, (byte) 0x01).orElseThrow ());
      assertInstanceOf (ForegroundColor.class,
          Attribute.getAttribute (Attribute.XA_FGCOLOR, (byte) 0xF4).orElseThrow ());
      assertInstanceOf (BackgroundColor.class,
          Attribute.getAttribute (Attribute.XA_BGCOLOR, (byte) 0xF8).orElseThrow ());
    }

    @ParameterizedTest (name = "codigo {0} nao implementado")
    @ValueSource (ints = { 0x43, 0xC1, 0xC2, 0x46 })
    @DisplayName ("devolve Optional vazio para atributos nao implementados")
    void returnsEmptyForUnimplemented (int code)
    {
      assertTrue (Attribute.getAttribute ((byte) code, (byte) 0x00).isEmpty ());
    }

    @Test
    @DisplayName ("devolve Optional vazio para codigo desconhecido")
    void returnsEmptyForUnknown ()
    {
      Optional<Attribute> attribute = Attribute.getAttribute ((byte) 0x77, (byte) 0x00);
      assertTrue (attribute.isEmpty ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("comportamento comum")
  class CommonBehaviour
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("pack escreve o par codigo/valor")
    void packsCodeAndValue ()
    {
      Attribute attribute = new ForegroundColor ((byte) 0xF4);
      byte[] buffer = new byte[4];

      int next = attribute.pack (buffer, 1);

      assertEquals (3, next);
      assertArrayEquals (new byte[] { 0x00, Attribute.XA_FGCOLOR, (byte) 0xF4, 0x00 },
          buffer);
    }

    @Test
    @DisplayName ("matches compara o codigo contra uma lista")
    void matchesAgainstList ()
    {
      Attribute attribute = new ForegroundColor ((byte) 0xF4);

      assertTrue (attribute.matches (Attribute.XA_FGCOLOR));
      assertTrue (attribute.matches (Attribute.XA_BGCOLOR, Attribute.XA_FGCOLOR));
      assertFalse (attribute.matches (Attribute.XA_BGCOLOR, Attribute.XA_HIGHLIGHTING));
      assertFalse (attribute.matches ());
    }

    @Test
    @DisplayName ("expoe valor e tipo")
    void exposesValueAndType ()
    {
      Attribute attribute = new ExtendedHighlight ((byte) 0x02);

      assertEquals ((byte) 0x02, attribute.getAttributeValue ());
      assertEquals (AttributeType.HIGHLIGHT, attribute.getAttributeType ());
    }

    @ParameterizedTest (name = "{0} -> {1}")
    @CsvSource ({ "0x00, Reset", "0x41, Highlight", "0x42, Foreground", "0x45, Background",
                  "0xC0, Start Field", "0x77, Unknown" })
    @DisplayName ("getTypeName nomeia os codigos")
    void namesTypeCodes (String hex, String expected)
    {
      byte code = (byte) Integer.decode (hex).intValue ();
      assertEquals (expected, Attribute.getTypeName (code));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("ColorAttribute")
  class Colors
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("indexa a cor pelos 4 bits baixos do valor")
    void indexesByLowNibble ()
    {
      assertEquals (ColorAttribute.colors[4],
          new ForegroundColor (ColorAttribute.COLOR_GREEN).getColor ());
      assertEquals (ColorAttribute.colors[0],
          new ForegroundColor (ColorAttribute.COLOR_NEUTRAL1).getColor ());
      assertEquals (ColorAttribute.colors[15],
          new BackgroundColor (ColorAttribute.COLOR_WHITE).getColor ());
    }

    @ParameterizedTest (name = "{0} -> {1}")
    @CsvSource ({ "0xF1, Blue", "0xF2, Red", "0xF4, Green", "0xF8, Black", "0xFF, White",
                  "0x00, Neutral1" })
    @DisplayName ("colorName nomeia as cores do 3270")
    void namesColors (String hex, String expected)
    {
      assertEquals (expected,
          ColorAttribute.colorName ((byte) Integer.decode (hex).intValue ()));
    }

    @Test
    @DisplayName ("ha exatamente 16 cores")
    void hasSixteenColors ()
    {
      assertEquals (16, ColorAttribute.colors.length);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("ExtendedHighlight")
  class Highlight
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("valor zero e exibido como Reset")
    void zeroIsReset ()
    {
      assertTrue (new ExtendedHighlight ((byte) 0x00).toString ().contains ("Reset"));
    }

    @ParameterizedTest (name = "{0} -> {1}")
    @CsvSource ({ "0x01, Blink", "0x02, Reverse video", "0x04, Underscore" })
    @DisplayName ("nomeia os destaques conhecidos")
    void namesHighlights (String hex, String expected)
    {
      ExtendedHighlight highlight =
          new ExtendedHighlight ((byte) Integer.decode (hex).intValue ());

      assertTrue (highlight.toString ().contains (expected), highlight.toString ());
    }
  }
}
