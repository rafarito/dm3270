package com.bytezone.dm3270.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.IdentityHashMap;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.bytezone.dm3270.attributes.ColorAttribute;
import com.bytezone.dm3270.attributes.TerminalColor;

/*
 * Caracterizacao do pool de ScreenContext.
 *
 * Existe por causa de uma armadilha: ScreenContext.matches compara as cores por
 * IDENTIDADE (==), nao por equals. Isso so funciona hoje porque toda cor vem da paleta
 * de ColorAttribute, que e um array de constantes - e nesse array tres posicoes apontam
 * para o MESMO objeto TerminalColor.WHITE_SMOKE (Neutral1, Neutral2 e White).
 *
 * Consequencia pratica: pedir um contexto com colors[0] e depois com colors[15] devolve
 * o mesmo ScreenContext, e o pool cresce uma vez so.
 *
 * Estes testes nasceram ANTES da troca de javafx.scene.paint.Color por TerminalColor,
 * justamente para que a substituicao fosse verificavel: era preciso provar que o aliasing
 * sobreviveu. Um enum com 16 constantes distintas, que seria o refactor obvio, teria
 * quebrado a equivalencia sem quebrar nada visivelmente - o pool ganharia entradas a mais e
 * matches passaria a responder diferente. Continuam valendo como guarda contra qualquer
 * mexida futura na paleta.
 *
 * Nao ha toolkit JavaFX envolvido, e agora nem tipo do JavaFX: TerminalColor e neutro.
 *
 * O pool de ContextManager e um campo static, compartilhado entre todas as instancias e
 * que so cresce. Por isso os testes abaixo verificam sempre relacoes de identidade e
 * variacoes de tamanho, nunca o tamanho absoluto - assim continuam validos qualquer que
 * seja a ordem de execucao da suite.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("ScreenContext - pool e identidade de cor")
class ScreenContextPoolingTest
// -----------------------------------------------------------------------------------//
{
  private static final byte NO_HIGHLIGHT = 0;

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("aliasing da paleta")
  class Palette
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("os 16 slots apontam para apenas 14 objetos distintos")
    void sixteenSlotsAreFourteenObjects ()
    {
      Set<TerminalColor> distinct = java.util.Collections
          .newSetFromMap (new IdentityHashMap<TerminalColor, Boolean> ());
      for (TerminalColor color : ColorAttribute.colors)
        distinct.add (color);

      assertEquals (16, ColorAttribute.colors.length, "a paleta tem 16 posicoes");
      assertEquals (14, distinct.size (),
          "Neutral1, Neutral2 e White compartilham o mesmo TerminalColor.WHITE_SMOKE");
    }

    @Test
    @DisplayName ("Neutral1, Neutral2 e White sao o mesmo objeto")
    void neutralsShareOneInstance ()
    {
      assertSame (ColorAttribute.colors[0], ColorAttribute.colors[7], "Neutral1 vs Neutral2");
      assertSame (ColorAttribute.colors[0], ColorAttribute.colors[15], "Neutral1 vs White");
      assertSame (TerminalColor.WHITE_SMOKE, ColorAttribute.colors[0],
          "todos sao WHITE_SMOKE");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("matches compara por identidade")
  class Matching
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("aceita a mesma instancia de cor")
    void acceptsSameInstance ()
    {
      ScreenContext context = new ScreenContext (ColorAttribute.colors[1],
          ColorAttribute.colors[8], NO_HIGHLIGHT, false, null);

      assertTrue (context.matches (ColorAttribute.colors[1], ColorAttribute.colors[8],
          NO_HIGHLIGHT, false));
    }

    @Test
    @DisplayName ("recusa uma cor igual por valor mas de outra instancia")
    void rejectsEqualButDistinctInstance ()
    {
      TerminalColor sameValue = TerminalColor.rgb (245, 245, 245);   // igual a WHITE_SMOKE

      assertEquals (TerminalColor.WHITE_SMOKE, sameValue,
          "as duas cores sao iguais por valor");
      assertNotSame (TerminalColor.WHITE_SMOKE, sameValue, "mas nao sao o mesmo objeto");

      ScreenContext context = new ScreenContext (TerminalColor.WHITE_SMOKE,
          ColorAttribute.colors[8], NO_HIGHLIGHT, false, null);

      assertFalse (
          context.matches (sameValue, ColorAttribute.colors[8], NO_HIGHLIGHT, false),
          "matches usa ==, entao valor igual nao basta");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("reaproveitamento no pool")
  class Pooling
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("slots que compartilham a cor reaproveitam o mesmo contexto")
    void aliasedSlotsShareOneContext ()
    {
      ContextManager manager = new ContextManager ();

      ScreenContext viaNeutral1 = manager.getScreenContext (ColorAttribute.colors[0],
          ColorAttribute.colors[8], NO_HIGHLIGHT, false);
      ScreenContext viaNeutral2 = manager.getScreenContext (ColorAttribute.colors[7],
          ColorAttribute.colors[8], NO_HIGHLIGHT, false);
      ScreenContext viaWhite = manager.getScreenContext (ColorAttribute.colors[15],
          ColorAttribute.colors[8], NO_HIGHLIGHT, false);

      assertSame (viaNeutral1, viaNeutral2, "Neutral2 reaproveita o contexto de Neutral1");
      assertSame (viaNeutral1, viaWhite, "White reaproveita o contexto de Neutral1");
    }

    @Test
    @DisplayName ("pedir a mesma combinacao duas vezes nao cria contexto novo")
    void repeatedRequestIsStable ()
    {
      ContextManager manager = new ContextManager ();

      ScreenContext first = manager.getScreenContext (ColorAttribute.colors[2],
          ColorAttribute.colors[8], NO_HIGHLIGHT, false);
      ScreenContext second = manager.getScreenContext (ColorAttribute.colors[2],
          ColorAttribute.colors[8], NO_HIGHLIGHT, false);

      assertSame (first, second);
    }

    @Test
    @DisplayName ("mudar so o primeiro plano reaproveita fundo, highlight e intensidade")
    void setForegroundKeepsTheRest ()
    {
      ContextManager manager = new ContextManager ();

      ScreenContext base = manager.getScreenContext (ColorAttribute.colors[1],
          ColorAttribute.colors[8], NO_HIGHLIGHT, true);
      ScreenContext red = manager.setForeground (base, ColorAttribute.colors[2]);

      assertSame (ColorAttribute.colors[2], red.foregroundColor);
      assertSame (base.backgroundColor, red.backgroundColor);
      assertEquals (base.highlight, red.highlight);
      assertEquals (base.highIntensity, red.highIntensity);

      assertSame (red, manager.setForeground (base, ColorAttribute.colors[2]),
          "a segunda chamada reaproveita o contexto criado na primeira");
    }

    @Test
    @DisplayName ("voltar ao primeiro plano original devolve o contexto original")
    void setForegroundRoundTrip ()
    {
      ContextManager manager = new ContextManager ();

      ScreenContext base = manager.getScreenContext (ColorAttribute.colors[4],
          ColorAttribute.colors[8], NO_HIGHLIGHT, false);
      ScreenContext other = manager.setForeground (base, ColorAttribute.colors[6]);

      assertSame (base, manager.setForeground (other, ColorAttribute.colors[4]));
    }

    @Test
    @DisplayName ("highlight e intensidade tambem entram na chave do pool")
    void highlightAndIntensityArePartOfTheKey ()
    {
      ContextManager manager = new ContextManager ();

      ScreenContext plain = manager.getScreenContext (ColorAttribute.colors[5],
          ColorAttribute.colors[8], NO_HIGHLIGHT, false);
      ScreenContext highlighted = manager.setHighlight (plain, (byte) 0xF1);
      ScreenContext intense = manager.setHighIntensity (plain, true);

      assertNotSame (plain, highlighted, "highlight diferente e contexto diferente");
      assertNotSame (plain, intense, "intensidade diferente e contexto diferente");
      assertSame (plain, manager.setHighlight (highlighted, NO_HIGHLIGHT),
          "desfazer o highlight volta ao contexto original");
    }

    @Test
    @DisplayName ("o contexto padrao e o primeiro do pool: Neutral1 sobre Black")
    void defaultContextIsNeutralOnBlack ()
    {
      ContextManager manager = new ContextManager ();
      ScreenContext defaultContext = manager.getDefaultScreenContext ();

      assertSame (ColorAttribute.colors[0], defaultContext.foregroundColor);
      assertSame (ColorAttribute.colors[8], defaultContext.backgroundColor);
      assertEquals (NO_HIGHLIGHT, defaultContext.highlight);
      assertFalse (defaultContext.highIntensity);
    }
  }
}
