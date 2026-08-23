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

/*
 * A prova de que tirar o JavaFX da paleta nao mudou um pixel.
 *
 * ColorAttribute deixou de usar javafx.scene.paint.Color e passou a usar TerminalColor, um
 * tipo neutro. A conversao para o tipo do JavaFX acontece em FxPalette, no momento de
 * desenhar. Para que o desenho continue identico, duas coisas precisam valer:
 *
 * 1. Cada TerminalColor da paleta tem exatamente os mesmos componentes da constante JavaFX
 *    que substituiu. E o que a maior parte deste teste verifica, cor por cor.
 *
 * 2. FxPalette.toFx devolve uma cor igual - nao necessariamente a MESMA instancia - da
 *    constante original. Para o GraphicsContext isso basta: ele le os componentes. A
 *    identidade so importava dentro do modelo, e la quem responde e TerminalColor.
 *
 * Color.rgb (r, g, b, opacity) constroi a cor como r / 255.0, a mesma expressao que o
 * JavaFX usa nas suas constantes nomeadas - por isso a igualdade e exata, e nao aproximada.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("Paleta - fidelidade entre TerminalColor e as cores do JavaFX")
class PaletteFidelityTest
// -----------------------------------------------------------------------------------//
{
  /*
   * O par a par entre a paleta neutra e as constantes JavaFX que estavam em ColorAttribute
   * antes da troca. Esta lista e o contrato: se alguem mexer numa cor de um lado sem mexer
   * do outro, o teste acusa.
   */
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

      // igualdade de valor, nao de instancia: e o que o GraphicsContext le
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
      // ColorAttribute.getName cai neste toString para cores fora da paleta, e
      // ScreenContext.toString imprime o resultado no log
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
      // ScreenPosition.draw converte tres vezes por posicao, para as 1.920 posicoes da
      // tela, a cada redesenho: sem cache seriam milhares de objetos descartaveis
      assertSame (FxPalette.toFx (TerminalColor.LIME), FxPalette.toFx (TerminalColor.LIME));
    }

    @Test
    @DisplayName ("cores iguais por valor compartilham a conversao")
    void equalColoursShareTheConversion ()
    {
      // o cache e por valor, nao por identidade: TerminalColor e um record
      assertSame (FxPalette.toFx (TerminalColor.WHITE_SMOKE),
          FxPalette.toFx (TerminalColor.rgb (245, 245, 245)));
    }
  }
}
