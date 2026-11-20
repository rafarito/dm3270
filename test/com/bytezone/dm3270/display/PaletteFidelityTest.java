package com.bytezone.dm3270.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.bytezone.dm3270.attributes.ColorAttribute;
import com.bytezone.dm3270.attributes.TerminalColor;

import javafx.scene.paint.Color;

// Color.rgb usa r/255.0 como as constantes do JavaFX, por isso a igualdade e exata.
// -----------------------------------------------------------------------------------//
@DisplayName ("Paleta - fidelidade entre TerminalColor e as cores do JavaFX")
class PaletteFidelityTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  private static Stream<Arguments> palette ()
  // ---------------------------------------------------------------------------------//
  {
    return Stream.of (                                                    //
        Arguments.of ("Neutral1", TerminalColor.WHITE_SMOKE, Color.WHITESMOKE),
        Arguments.of ("Blue", TerminalColor.DODGER_BLUE, Color.DODGERBLUE),
        Arguments.of ("Red", TerminalColor.RED, Color.RED),
        Arguments.of ("Pink", TerminalColor.PINK, Color.PINK),
        Arguments.of ("Green", TerminalColor.LIME, Color.LIME),
        Arguments.of ("Turquoise", TerminalColor.TURQUOISE, Color.TURQUOISE),
        Arguments.of ("Yellow", TerminalColor.YELLOW, Color.YELLOW),
        Arguments.of ("Black", TerminalColor.BLACK, Color.BLACK),
        Arguments.of ("Deep blue", TerminalColor.DARK_BLUE, Color.DARKBLUE),
        Arguments.of ("Orange", TerminalColor.ORANGE, Color.ORANGE),
        Arguments.of ("Purple", TerminalColor.PURPLE, Color.PURPLE),
        Arguments.of ("Pale green", TerminalColor.PALE_GREEN, Color.PALEGREEN),
        Arguments.of ("Pale turquoise", TerminalColor.PALE_TURQUOISE, Color.PALETURQUOISE),
        Arguments.of ("Grey", TerminalColor.GREY, Color.GREY));
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("componentes")
  class Components
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "{0}")
    @DisplayName ("a cor neutra tem os componentes da constante JavaFX original")
    @MethodSource ("com.bytezone.dm3270.display.PaletteFidelityTest#palette")
    void componentsMatch (String name, TerminalColor neutral, Color fx)
    {
      assertEquals (Math.round (fx.getRed () * 255), neutral.red (), name + " - vermelho");
      assertEquals (Math.round (fx.getGreen () * 255), neutral.green (), name + " - verde");
      assertEquals (Math.round (fx.getBlue () * 255), neutral.blue (), name + " - azul");
      assertEquals (fx.getOpacity (), neutral.opacity (), name + " - opacidade");
    }

    @ParameterizedTest (name = "{0}")
    @DisplayName ("a conversao reconstroi a cor original componente a componente")
    @MethodSource ("com.bytezone.dm3270.display.PaletteFidelityTest#palette")
    void conversionIsExact (String name, TerminalColor neutral, Color fx)
    {
      Color converted = FxPalette.toFx (neutral);

      assertEquals (fx, converted, name + " - a cor convertida diverge da original");
      assertEquals (fx.getRed (), converted.getRed (), name + " - vermelho");
      assertEquals (fx.getGreen (), converted.getGreen (), name + " - verde");
      assertEquals (fx.getBlue (), converted.getBlue (), name + " - azul");
      assertEquals (fx.getOpacity (), converted.getOpacity (), name + " - opacidade");
    }

    @ParameterizedTest (name = "{0}")
    @DisplayName ("toString mantem o formato do JavaFX")
    @MethodSource ("com.bytezone.dm3270.display.PaletteFidelityTest#palette")
    void textMatches (String name, TerminalColor neutral, Color fx)
    {
      // ColorAttribute.getName e ScreenContext.toString dependem deste formato.
      assertEquals (fx.toString (), neutral.toString (), name);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("a paleta de ColorAttribute")
  class Palette
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "{0}")
    @DisplayName ("o nome de cada cor continua o mesmo")
    @MethodSource ("com.bytezone.dm3270.display.PaletteFidelityTest#palette")
    void namesAreUnchanged (String name, TerminalColor neutral, Color fx)
    {
      assertEquals (name, ColorAttribute.getName (neutral));
    }

    @Test
    @DisplayName ("os 16 slots continuam na mesma ordem")
    void slotOrderIsUnchanged ()
    {
      Color[] expected = { Color.WHITESMOKE, Color.DODGERBLUE, Color.RED, Color.PINK,
                           Color.LIME, Color.TURQUOISE, Color.YELLOW, Color.WHITESMOKE,
                           Color.BLACK, Color.DARKBLUE, Color.ORANGE, Color.PURPLE,
                           Color.PALEGREEN, Color.PALETURQUOISE, Color.GREY,
                           Color.WHITESMOKE };

      assertEquals (expected.length, ColorAttribute.colors.length);

      for (int i = 0; i < expected.length; i++)
        assertEquals (expected[i], FxPalette.toFx (ColorAttribute.colors[i]),
            "slot " + i + " mudou de cor");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("cache de conversao")
  class Caching
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("converter a mesma cor duas vezes devolve a mesma instancia")
    void conversionIsCached ()
    {
      // Evita milhar de alocacoes por redesenho (3 conversoes x 1920 posicoes).
      assertSame (FxPalette.toFx (TerminalColor.LIME), FxPalette.toFx (TerminalColor.LIME));
    }

    @Test
    @DisplayName ("cores iguais por valor compartilham a conversao")
    void equalColoursShareTheConversion ()
    {
      assertSame (FxPalette.toFx (TerminalColor.WHITE_SMOKE),
          FxPalette.toFx (TerminalColor.rgb (245, 245, 245)));
    }
  }
}
