package com.bytezone.dm3270.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import com.bytezone.dm3270.datasets.DatasetStore;
import com.bytezone.dm3270.plugins.RecordingPluginsStage;
import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.streams.TelnetState;
import com.bytezone.dm3270.testing.JavaFxToolkit;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/*
 * O MOUSE DA Screen, pela entrada que o usuario usa: eventos disparados no proprio Canvas.
 *
 * Os tres handlers (press, drag, release) nao tinham rede. A ScreenSelectionTest vigia a
 * selecao com um SelectionHost de mentira, e nada vigiava a traducao de coordenada em posicao
 * nem o que o press faz com o cursor. Esta classe existe para o ciclo C3 poder tirar os
 * handlers da Screen sem mudar nada disso.
 *
 * O que se observa e publico ou de pacote: getScreenPositions () (o flag isSelected de cada
 * posicao), getScreenCursor () e getScreenSelection (). Nenhum campo, nenhuma reflexao - o
 * refactor que vem a seguir move exatamente os campos.
 *
 * Fica de fora o requestFocus () do press: sem Scene, o Canvas nao ganha foco, e montar uma
 * Scene so para isto mediria o JavaFX, nao a Screen.
 */
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("Screen - o mouse seleciona e move o cursor")
class ScreenMouseTest
// -----------------------------------------------------------------------------------//
{
  private static final ScreenDimensions MODEL_2 = new ScreenDimensions (24, 80);

  @TempDir
  private Path pluginsDirectory;

  private Preferences prefs;
  private RecordingPluginsStage pluginsStage;
  private Screen screen;

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void createScreen ()
  // ---------------------------------------------------------------------------------//
  {
    prefs = Preferences.userRoot ().node ("dm3270-test/" + UUID.randomUUID ());
    pluginsStage =
        JavaFxToolkit.onFxThread ( () -> new RecordingPluginsStage (prefs, pluginsDirectory));
    screen = JavaFxToolkit.onFxThread ( () -> new Screen (MODEL_2, null, prefs,
        TerminalFunction.TERMINAL, pluginsStage, null, new TelnetState (), DatasetStore.NONE));
  }

  // ---------------------------------------------------------------------------------//
  @AfterEach
  void removePreferencesNode () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    pluginsStage.closeClassLoader ();
    prefs.removeNode ();
    prefs.flush ();
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("arrastar marca as posicoes entre o press e o release, e so elas")
  void dragSelectsTheRange ()
  // ---------------------------------------------------------------------------------//
  {
    fire (MouseEvent.MOUSE_PRESSED, 10, 2);
    fire (MouseEvent.MOUSE_DRAGGED, 12, 2);
    fire (MouseEvent.MOUSE_RELEASED, 15, 2);

    assertEquals (List.of (170, 171, 172, 173, 174, 175), selectedPositions ());
    assertTrue (screen.getScreenSelection ().hasSelection ());
    assertFalse (screen.getScreenSelection ().isActive ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("durante o arrasto a selecao esta ativa")
  void theSelectionIsActiveWhileDragging ()
  // ---------------------------------------------------------------------------------//
  {
    fire (MouseEvent.MOUSE_PRESSED, 10, 2);
    fire (MouseEvent.MOUSE_DRAGGED, 12, 3);

    assertTrue (screen.getScreenSelection ().isActive ());
    assertEquals (170, selectedPositions ().get (0));
    assertEquals (252, selectedPositions ().get (selectedPositions ().size () - 1));
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("um press novo apaga a selecao anterior")
  void aNewPressClearsThePreviousSelection ()
  // ---------------------------------------------------------------------------------//
  {
    fire (MouseEvent.MOUSE_PRESSED, 10, 2);
    fire (MouseEvent.MOUSE_RELEASED, 15, 2);

    fire (MouseEvent.MOUSE_PRESSED, 40, 5);

    assertEquals (List.of (440), selectedPositions ());
    assertFalse (screen.getScreenSelection ().hasSelection ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("fora da tela, a coordenada e presa na ultima linha e coluna")
  void coordinatesAreClampedToTheScreen ()
  // ---------------------------------------------------------------------------------//
  {
    fireAt (MouseEvent.MOUSE_PRESSED, -500, -500);
    fireAt (MouseEvent.MOUSE_RELEASED, 1e6, 1e6);

    List<Integer> selected = selectedPositions ();
    assertEquals (MODEL_2.size, selected.size ());
    assertEquals (0, selected.get (0));
    assertEquals (MODEL_2.size - 1, selected.get (selected.size () - 1));
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("com o cursor visivel, o press o move para a posicao clicada")
  void aVisibleCursorFollowsThePress ()
  // ---------------------------------------------------------------------------------//
  {
    onFx ( () -> screen.getScreenCursor ().setVisible (true));

    fire (MouseEvent.MOUSE_PRESSED, 10, 2);

    assertEquals (170, screen.getScreenCursor ().getLocation ());
  }

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("com o cursor invisivel, o press seleciona mas nao o move")
  void anInvisibleCursorStaysPut ()
  // ---------------------------------------------------------------------------------//
  {
    onFx ( () -> screen.getScreenCursor ().setVisible (false));
    int before = screen.getScreenCursor ().getLocation ();

    fire (MouseEvent.MOUSE_PRESSED, 10, 2);

    assertEquals (before, screen.getScreenCursor ().getLocation ());
    assertEquals (List.of (170), selectedPositions ());
  }

  // ---------------------------------------------------------------------------------//
  private void fire (EventType<MouseEvent> type, int column, int row)
  // ---------------------------------------------------------------------------------//
  {
    // o meio da celula, para que o truncamento de (int) nao dependa da fonte da maquina
    FontDetails font = screen.getFontManager ().getFontDetails ();
    double x = MODEL_2.xOffset + column * font.width + font.width / 2.0;
    double y = MODEL_2.yOffset + row * font.height + font.height / 2.0;
    fireAt (type, x, y);
  }

  // ---------------------------------------------------------------------------------//
  private void fireAt (EventType<MouseEvent> type, double x, double y)
  // ---------------------------------------------------------------------------------//
  {
    MouseEvent event = new MouseEvent (type, x, y, x, y, MouseButton.PRIMARY, 1, false,
        false, false, false, true, false, false, false, false, false, null);
    onFx ( () -> Event.fireEvent (screen, event));
  }

  // ---------------------------------------------------------------------------------//
  private List<Integer> selectedPositions ()
  // ---------------------------------------------------------------------------------//
  {
    List<Integer> selected = new ArrayList<> ();
    for (int i = 0; i < MODEL_2.size; i++)
      if (screen.getScreenPositions ()[i].isSelected ())
        selected.add (i);
    return selected;
  }

  // ---------------------------------------------------------------------------------//
  private static void onFx (Runnable action)
  // ---------------------------------------------------------------------------------//
  {
    JavaFxToolkit.onFxThread ( () ->
    {
      action.run ();
      return null;
    });
  }
}
