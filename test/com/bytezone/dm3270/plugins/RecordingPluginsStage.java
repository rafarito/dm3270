package com.bytezone.dm3270.plugins;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import com.bytezone.dm3270.screen.ScreenDimensions;

/*
 * A ponte que deixa um teste de FORA do pacote plugins construir um PluginsStage, e o
 * gravador do unico ponto em que ele fala com a tela durante a construcao dela.
 *
 * POR QUE A PONTE EXISTE. O construtor publico PluginsStage (Preferences) delega a
 * "this (prefs, Paths.get (PLUGINS_DIR).toAbsolutePath ())" - a pasta plugins/ da maquina de
 * quem roda a suite, que esta no .gitignore e portanto tem conteudo diferente em cada
 * checkout. O passo 9 abriu o construtor de PACOTE que recebe a pasta (commit 79735885), e e
 * por ele que o PluginsStageDispatchTest aponta para um @TempDir. Mas aquele construtor e
 * package-private, e o ScreenConstructionTest mora em display: esta subclasse publica e o
 * minimo para reexporta-lo, sem alargar nada em src/.
 *
 * O QUE ELA GRAVA, e por que isso importa. Screen.java:181 termina o construtor com
 * "pluginsStage.setScreen (this)", e PluginsStage.setScreen:143 chama de volta
 * "screen.getScreenDimensions ()" - ou seja, o host consome a tela AINDA DENTRO do construtor
 * dela. Isso so funciona porque setCurrentScreen (ScreenOption.DEFAULT) roda tres linhas
 * antes, em :178, e nenhum comentario do codigo diz que essa ordem e obrigatoria. Gravar o
 * valor visto aqui e o que torna a restricao verificavel.
 *
 * O super.setScreen e chamado de proposito: a subclasse grava o despacho e PRESERVA a
 * semantica, que e o idioma do RecordingCursor do ConsoleKeyPressTest.
 */
// -----------------------------------------------------------------------------------//
public class RecordingPluginsStage extends PluginsStage
// -----------------------------------------------------------------------------------//
{
  public final List<String> calls = new ArrayList<> ();

  // O que o host enxergou quando perguntou as dimensoes de dentro do construtor da Screen.
  public ScreenDimensions dimensionsSeenDuringConstruction;

  // ---------------------------------------------------------------------------------//
  public RecordingPluginsStage (Preferences prefs, Path pluginsDirectory)
  // ---------------------------------------------------------------------------------//
  {
    super (prefs, pluginsDirectory);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void setScreen (PluginHost screen)
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("setScreen");
    dimensionsSeenDuringConstruction = screen.getScreenDimensions ();

    super.setScreen (screen);
  }
}
