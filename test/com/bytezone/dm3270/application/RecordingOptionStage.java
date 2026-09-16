package com.bytezone.dm3270.application;

import java.util.List;
import java.util.prefs.Preferences;

import javafx.scene.control.MenuItem;

/*
 * O OptionStage real, com o gatilho do lancamento capturado e as transicoes de janela
 * gravadas.
 *
 * O QUE ELE NAO SUBSTITUI, e e essa a escolha central do arreio: getLaunchRequest (),
 * findServerSite () e savePreferences () NAO sao sobrescritos. O pedido que o Console recebe
 * e montado pelos widgets REAIS, a partir das preferencias que o teste semeia - o mesmo
 * caminho que SiteListStage:114-122 percorre na aplicacao. Um duble que fabricasse o
 * LaunchRequest afirmaria o que o duble inventou, e nao o que o usuario ve.
 *
 * setOnConnect (Runnable) e sobrescrito porque e por ele que o gatilho fica acessivel: o
 * Console injeta "this::startSelectedFunction" em Console.java:81, e com isso o metodo
 * privado ja e alcancavel sem que nenhuma visibilidade precise ser aberta. O super e chamado,
 * entao o botao Connect continua ligado do mesmo jeito.
 *
 * POR QUE show () NAO E SOBRESCRITO, e isto contradiz o plano deste passo, que dizia para
 * sobrescreve-lo: javafx.stage.Stage.show () e FINAL - conferido com javap no
 * javafx-graphics-21.0.7. Window.hide () nao e, mas gravar so metade seria pior. A gravacao
 * vai pela showingProperty (), que e observavel e dispara na thread do JavaFX, de forma
 * SINCRONA dentro do proprio show () - e por isso a ordem na lista compartilhada continua
 * sendo a ordem real das chamadas.
 *
 * A CONSEQUENCIA, e ela e honesta: a janela de opcoes APARECE de verdade enquanto o caso
 * roda, e o @AfterEach a fecha. Nao ha como suprimi-la sem mexer no Console, porque o metodo
 * e final. Num ambiente headless rode sob xvfb-run, como o resto da suite de JavaFX.
 *
 * Todos os gravadores escrevem na MESMA lista do TestConsole, e e isso que faz cada caso
 * afirmar tambem a ORDEM entre colaboradores diferentes. E o idioma dos tres gravadores do
 * ConsoleKeyPressTest.
 */
// -----------------------------------------------------------------------------------//
class RecordingOptionStage extends OptionStage
// -----------------------------------------------------------------------------------//
{
  private final List<String> calls;

  // O gatilho que o Console injetou: e ele que dispara startSelectedFunction sem que o
  // metodo precise deixar de ser privado.
  Runnable onConnect;

  // ---------------------------------------------------------------------------------//
  RecordingOptionStage (Preferences prefs, MenuItem pluginsEditMenuItem, List<String> calls)
  // ---------------------------------------------------------------------------------//
  {
    super (prefs, pluginsEditMenuItem);

    this.calls = calls;

    showingProperty ().addListener ( (obs, wasShowing, isShowing) ->
        calls.add (isShowing ? "optionStage.show" : "optionStage.hide"));
  }

  // ---------------------------------------------------------------------------------//
  @Override
  void setOnConnect (Runnable action)
  // ---------------------------------------------------------------------------------//
  {
    calls.add ("optionStage.setOnConnect");
    onConnect = action;

    super.setOnConnect (action);
  }
}
