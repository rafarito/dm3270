package com.bytezone.dm3270.screen;

import com.bytezone.dm3270.attributes.TerminalColor;

/*
 * A superficie onde uma posicao de tela se desenha.
 *
 * Sao exatamente as cinco operacoes que ScreenPosition.draw e doGraphics usavam do
 * javafx.scene.canvas.GraphicsContext - nada mais. Estreitar o contrato a esse minimo e o
 * que tira o tipo do JavaFX de dentro do modelo de tela: ScreenPosition passa a falar em
 * TerminalColor e em coordenadas, e quem sabe desenhar de verdade e FxScreenCanvas.
 *
 * A conversao de cor acontece na implementacao, nao aqui.
 */
// -----------------------------------------------------------------------------------//
public interface ScreenCanvas
// -----------------------------------------------------------------------------------//
{
  void setFill (TerminalColor color);

  void setStroke (TerminalColor color);

  void fillRect (double x, double y, double width, double height);

  void fillText (String text, double x, double y);

  void strokeLine (double x1, double y1, double x2, double y2);
}
