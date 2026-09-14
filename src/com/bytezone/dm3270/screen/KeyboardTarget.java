package com.bytezone.dm3270.screen;

/*
 * O que o tratador de teclado faz com a tela.
 *
 * O ConsoleKeyPress guardava a classe Screen inteira - 1.094 linhas que estendem
 * javafx.scene.canvas.Canvas e montam o grafo de objetos da aplicacao no construtor - e usava
 * cinco metodos dela, mais o isKeyboardLocked () que ja tinha porta. Enquanto fosse assim, as
 * 213 linhas de despacho de tecla nao tinham como ter teste: nenhum teste instancia uma Screen.
 *
 * Estende KeyboardState em vez de redeclarar isKeyboardLocked (), pelo mesmo motivo que o
 * ScreenTarget faz: quem ja tinha o estado do teclado continua tendo.
 *
 * clearSelection () existe aqui, e nao um getScreenSelection (), de proposito. A ScreenSelection
 * mora em display, importa javafx.animation.PauseTransition e guarda uma Screen - devolve-la
 * poria a view dentro do modelo de tela e quebraria screenModelDoesNotDependOnTheView. Os tres
 * sitios que a chamavam faziam todos a mesma coisa: limpar.
 *
 * A porta e declarada aqui, e nao em application com o consumidor, porque quem a implementa e
 * display.Screen - e display nao pode depender de application. E a mesma razao pela qual
 * KeyboardState e AidSender moram neste pacote.
 */
// -----------------------------------------------------------------------------------//
public interface KeyboardTarget extends KeyboardState
// -----------------------------------------------------------------------------------//
{
  Cursor getScreenCursor ();

  void clearSelection ();

  void copySelection ();

  void pasteText ();

  void toggleInsertMode ();
}
