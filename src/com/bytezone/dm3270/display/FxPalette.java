package com.bytezone.dm3270.display;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.bytezone.dm3270.attributes.TerminalColor;

import javafx.scene.paint.Color;

/*
 * Converte a cor neutra do protocolo na cor do JavaFX, na hora de desenhar.
 *
 * E a unica ponte entre TerminalColor e javafx.scene.paint.Color. Isolar a conversao
 * aqui e o que permite que attributes, e adiante todo o modelo de tela, sejam
 * compilados e testados sem toolkit grafico.
 *
 * Color.rgb (r, g, b, opacity) constroi a cor como r / 255.0, exatamente a mesma expressao
 * que o JavaFX usa para definir suas constantes nomeadas - entao a cor produzida aqui tem
 * componentes bit a bit iguais aos da constante que TerminalColor substituiu.
 * PaletteFidelityTest verifica isso cor por cor.
 *
 * O cache nao e otimizacao prematura: ScreenPosition.draw faz tres conversoes por posicao e
 * e chamado para as 1.920 posicoes da tela em cada redesenho. Sem cache, cada redesenho
 * alocaria milhares de objetos Color descartaveis.
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
