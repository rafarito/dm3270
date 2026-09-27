package com.bytezone.dm3270.display;

import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.screen.ScreenPosition;

/*
 * O que a ScreenSelection usa da tela - quatro metodos, medidos, e nada alem.
 *
 * Ate este commit a selecao recebia a Screen concreta, 1.109 linhas, e era um dos DOIS
 * vazamentos de "this" do construtor da Screen que ainda nomeavam a classe em vez de uma
 * porta (o outro e o FontManager). Os nove restantes ja recebiam CursorHost, FieldHost,
 * KeyboardState, ScreenTarget ou PluginHost.
 *
 * E de pacote, e o motivo de quem a implementa NAO ser a Screen importa: metodo de interface
 * e implicitamente publico, e redrawRange/redrawSelection sao de pacote na Screen. "Screen
 * implements SelectionHost" os tornaria publicos numa classe publica. Quem implementa e uma
 * classe interna privada da Screen, que so delega - o mesmo desenho do Console.Stages.
 */
// -----------------------------------------------------------------------------------//
interface SelectionHost
// -----------------------------------------------------------------------------------//
{
  ScreenDimensions getScreenDimensions ();

  ScreenPosition getScreenPosition (int position);

  void redrawRange (int from, int to);

  void redrawSelection (int oldMin, int oldMax, int newMin, int newMax);
}
