package com.bytezone.dm3270.streams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.commands.WriteCommand;
import com.bytezone.dm3270.display.HeadlessScreenTarget;
import com.bytezone.dm3270.runtime.Source;
import com.bytezone.dm3270.runtime.TerminalFunction;

/*
 * O TelnetListener roda sem toolkit grafico.
 *
 * Ate o commit anterior isto era impossivel: os dois caminhos abaixo chamavam
 * Platform.runLater direto, e Platform.runLater sem toolkit lanca IllegalStateException. Nao
 * havia teste nenhum do TelnetListener por esse motivo - a classe que transforma bytes do
 * socket em comandos so podia ser exercitada com uma janela aberta.
 *
 * Com o Executor injetado, um teste passa Runnable::run e os dois caminhos executam na hora,
 * na propria thread. E o que transforma o parametro novo em rede: sem estes testes ele seria
 * so uma linha a menos no placar do ArchUnit.
 *
 * O QUE ESTES TESTES NAO SAO: uma afirmacao de que a entrega deixou de ser assincrona na
 * aplicacao. La o composition root passa Platform::runLater, e o agendamento e o mesmo de
 * antes. Aqui a entrega e sincrona porque o teste escolheu assim.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("TelnetListener - os dois caminhos que passavam pela thread grafica")
class TelnetListenerHeadlessTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  private static byte[] wire (byte... command)
  // ---------------------------------------------------------------------------------//
  {
    return new WriteCommand (command, 0, command.length).getTelnetData ();
  }

  /*
   * close () escreve o resumo da sessao na tela quando o socket cai. Era a UNICA coisa que o
   * TelnetListener pedia a tela como widget, e a razao de existir a porta SessionDisplay.
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o resumo do telnet chega a tela quando a conexao cai")
  void closeWritesTheSummary ()
  // ---------------------------------------------------------------------------------//
  {
    TelnetState telnetState = new TelnetState ();
    HeadlessScreenTarget screen = new HeadlessScreenTarget ();

    TelnetListener listener = new TelnetListener (screen, TerminalFunction.TERMINAL,
        telnetState, Runnable::run);

    listener.close ();

    assertEquals (1, screen.calls.stream ()
        .filter (c -> c.startsWith ("displayText:")).count ());
    assertTrue (screen.calls.contains ("displayText:" + telnetState.getSummary ()));
  }

  /*
   * No modo Terminal um registro 3270 e processado pela tela, e era esse process () que ia
   * para a fila do JavaFX. Um Write com uma order de texto escreve no buffer de verdade -
   * o HeadlessScreenTarget usa um Pen e um vetor de ScreenPosition reais -, entao o teste
   * consegue ler de volta o que o comando desenhou.
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("um registro 3270 e processado pela tela sem passar por toolkit nenhum")
  void terminalRecordReachesTheScreen ()
  // ---------------------------------------------------------------------------------//
  {
    TelnetState telnetState = new TelnetState ();
    HeadlessScreenTarget screen = new HeadlessScreenTarget ();

    TelnetListener listener = new TelnetListener (screen, TerminalFunction.TERMINAL,
        telnetState, Runnable::run);

    // Erase Write com WCC de reset, uma SBA para a posicao 0 e o texto "AB" em EBCDIC
    listener.listen (Source.SERVER, wire (Command.ERASE_WRITE_F5, (byte) 0x00, //
                                          (byte) 0x11, (byte) 0x40, (byte) 0x40, //
                                          (byte) 0xC1, (byte) 0xC2),
                     LocalDateTime.now (), true);

    assertTrue (screen.getScreenText ().startsWith ("AB"), screen.getScreenText ());
  }

  /*
   * Um listener atende varios registros seguidos, e cada Erase Write limpa o buffer antes de
   * escrever. Serve para provar que o caminho e reentrante quando a entrega e sincrona - com
   * Platform.runLater os dois process () aconteciam na fila, um depois do outro.
   */
  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("o mesmo listener aceita varios registros em sequencia")
  void severalRecordsInARow ()
  // ---------------------------------------------------------------------------------//
  {
    TelnetState telnetState = new TelnetState ();
    HeadlessScreenTarget screen = new HeadlessScreenTarget ();

    TelnetListener listener = new TelnetListener (screen, TerminalFunction.TERMINAL,
        telnetState, Runnable::run);

    listener.listen (Source.SERVER, wire (Command.ERASE_WRITE_F5, (byte) 0x00, //
                                          (byte) 0x11, (byte) 0x40, (byte) 0x40, (byte) 0xC1),
                     LocalDateTime.now (), true);
    listener.listen (Source.SERVER, wire (Command.ERASE_WRITE_F5, (byte) 0x00, //
                                          (byte) 0x11, (byte) 0x40, (byte) 0x40, (byte) 0xC2),
                     LocalDateTime.now (), true);

    assertTrue (screen.getScreenText ().startsWith ("B"), screen.getScreenText ());
  }
}
