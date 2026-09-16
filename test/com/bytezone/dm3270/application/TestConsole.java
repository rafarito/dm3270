package com.bytezone.dm3270.application;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import com.bytezone.dm3270.plugins.PluginsStage;
import com.bytezone.dm3270.plugins.RecordingPluginsStage;

import javafx.scene.control.MenuItem;

/*
 * O arreio do caminho de lancamento: o Console real, com as tres costuras do passo 11
 * substituidas por gravadores.
 *
 * O CONSOLE E REAL. start (Stage), startSelectedFunction e stop () sao os de producao, sem
 * uma linha alterada - o que muda e de onde vem o PluginsStage, de onde vem o OptionStage, e
 * para onde vai o alerta. Tudo o mais que o Console faz acontece de verdade.
 *
 * O PREFS FICA NULO DENTRO DO CONSOLE, e isso e deliberado. Console.prefs so e atribuido em
 * init () (Console.java:63), que depende de getParameters () e portanto de
 * Application.launch - inalcancavel num teste. Medido: o campo sobrevive nulo, porque os usos
 * dele ou estao em init, ou estao dentro das duas fabricas que este arreio substitui, ou
 * estao atras de "if (screen != null)" em savePreferences (:301).
 *
 * O LIMITE QUE ISSO IMPOE, e esta escrito aqui em vez de escondido: a asserticao de que
 * savePreferences nao grava FontName/FontSize prova que a guarda do screen nulo segurou, e
 * nao que a chave ficou ausente num no vivo. Quando alguma asserticao precisar do no vivo
 * dentro do Console, a costura seria um construtor de pacote so para o prefs - e so entao.
 *
 * TODOS OS GRAVADORES ESCREVEM NA MESMA LISTA. E o que faz cada caso afirmar a ORDEM entre
 * colaboradores diferentes, e nao so o que cada um recebeu.
 */
// -----------------------------------------------------------------------------------//
class TestConsole extends Console
// -----------------------------------------------------------------------------------//
{
  final List<String> calls = new ArrayList<> ();
  final List<String> alerts = new ArrayList<> ();

  // O que o usuario responde ao alerta de erro. Console.java:175 so reabre a janela de
  // opcoes quando showAlert devolve true.
  boolean alertAnswer = true;

  private final Preferences prefs;
  private final Path pluginsDirectory;

  RecordingPluginsStage recordedPluginsStage;
  RecordingOptionStage recordedOptionStage;
  MenuItem menuItemGivenToTheOptionStage;

  // ---------------------------------------------------------------------------------//
  TestConsole (Preferences prefs, Path pluginsDirectory)
  // ---------------------------------------------------------------------------------//
  {
    this.prefs = prefs;
    this.pluginsDirectory = pluginsDirectory;
  }

  /*
   * Aponta o PluginsStage para um @TempDir. Sem isto a rede varreria a pasta plugins/ da
   * maquina de quem roda a suite - que esta no .gitignore, aqui tem dois JARs e num CI limpo
   * nao existe.
   */
  // ---------------------------------------------------------------------------------//
  @Override
  PluginsStage createPluginsStage ()
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("createPluginsStage");
    recordedPluginsStage = new RecordingPluginsStage (prefs, pluginsDirectory);

    return recordedPluginsStage;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  OptionStage createOptionStage (MenuItem pluginsEditMenuItem)
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("createOptionStage");
    menuItemGivenToTheOptionStage = pluginsEditMenuItem;
    recordedOptionStage = new RecordingOptionStage (prefs, pluginsEditMenuItem, calls);

    return recordedOptionStage;
  }

  /*
   * Dm3270Utility.showAlert chama alert.showAndWait (), que numa suite nao falha: TRAVA.
   */
  // ---------------------------------------------------------------------------------//
  @Override
  boolean showAlert (String message)
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("showAlert");
    alerts.add (message);

    return alertAnswer;
  }

  /*
   * Dispara o gatilho que o Console injetou no OptionStage - o mesmo Runnable que o botao
   * Connect dispara na aplicacao. E assim que a rede alcanca startSelectedFunction sem que
   * ele deixe de ser privado.
   */
  // ---------------------------------------------------------------------------------//
  void connect ()
  // ---------------------------------------------------------------------------------//
  {
    recordedOptionStage.onConnect.run ();
  }
}
