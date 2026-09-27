package com.bytezone.dm3270.display;

import java.util.function.Supplier;

import com.bytezone.dm3270.screen.Cursor;
import com.bytezone.dm3270.screen.ScreenDimensions;

import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseEvent;

/*
 * O mouse da tela: press, drag e release viram selecao na ScreenSelection e, com o cursor
 * visivel, movem o cursor. Saiu da Screen (ciclo C3 do PLANO-SOLID-2) sem mudar uma linha
 * de comportamento - o ScreenMouseTest vigia.
 *
 * A fonte e as dimensoes chegam por Supplier, e nao por valor, porque a Screen instala esta
 * classe ANTES de construir o FontManager (a ordem do construtor e vigiada pelo
 * ScreenConstructionTest), e porque a dimensao corrente muda a cada troca de particao. Os
 * handlers antigos liam os dois campos no momento do evento; os Supplier fazem o mesmo.
 */
// -----------------------------------------------------------------------------------//
final class ScreenMouseInput
// -----------------------------------------------------------------------------------//
{
  private final Canvas canvas;
  private final Cursor cursor;
  private final ScreenSelection screenSelection;
  private final Supplier<FontDetails> fontDetails;
  private final Supplier<ScreenDimensions> screenDimensions;

  // ---------------------------------------------------------------------------------//
  ScreenMouseInput (Canvas canvas, Cursor cursor, ScreenSelection screenSelection,
      Supplier<FontDetails> fontDetails, Supplier<ScreenDimensions> screenDimensions)
  // ---------------------------------------------------------------------------------//
  {
    this.canvas = canvas;
    this.cursor = cursor;
    this.screenSelection = screenSelection;
    this.fontDetails = fontDetails;
    this.screenDimensions = screenDimensions;
  }

  // ---------------------------------------------------------------------------------//
  void install ()
  // ---------------------------------------------------------------------------------//
  {
    canvas.setOnMousePressed (this::handleMousePressed);
    canvas.setOnMouseDragged (this::handleMouseDragged);
    canvas.setOnMouseReleased (this::handleMouseReleased);
  }

  // ---------------------------------------------------------------------------------//
  private void handleMousePressed (MouseEvent event)
  // ---------------------------------------------------------------------------------//
  {
    // Clear any previous selection
    screenSelection.clearSelection ();

    int position = mouseToPosition (event.getX (), event.getY ());
    if (position >= 0)
    {
      // Move cursor first, then start selection so selection visual takes priority
      if (cursor.isVisible ())
        cursor.moveTo (position);

      screenSelection.startSelection (position);
    }

    canvas.requestFocus ();
  }

  // ---------------------------------------------------------------------------------//
  private void handleMouseDragged (MouseEvent event)
  // ---------------------------------------------------------------------------------//
  {
    int position = mouseToPosition (event.getX (), event.getY ());
    if (position >= 0)
      screenSelection.extendSelection (position);
  }

  // ---------------------------------------------------------------------------------//
  private void handleMouseReleased (MouseEvent event)
  // ---------------------------------------------------------------------------------//
  {
    int position = mouseToPosition (event.getX (), event.getY ());
    if (position >= 0)
      screenSelection.endSelection (position);
  }

  // ---------------------------------------------------------------------------------//
  private int mouseToPosition (double mouseX, double mouseY)
  // ---------------------------------------------------------------------------------//
  {
    FontDetails fontDetails = this.fontDetails.get ();
    if (fontDetails == null)
      return -1;

    ScreenDimensions dims = screenDimensions.get ();

    int col = (int) ((mouseX - dims.xOffset) / fontDetails.width);
    int row = (int) ((mouseY - dims.yOffset) / fontDetails.height);

    // Clamp to valid range
    col = Math.max (0, Math.min (dims.columns - 1, col));
    row = Math.max (0, Math.min (dims.rows - 1, row));

    return row * dims.columns + col;
  }
}
