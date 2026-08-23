package com.bytezone.dm3270.commands;

/*
 * Para onde vao as linhas do console do host.
 *
 * SystemMessage recorta a mensagem do console em linhas da largura da tela e entrega apenas
 * as que ainda nao foram processadas. Quem as recebe, hoje, e um ConsoleLog - que e um
 * TextArea do JavaFX com um parser de codigo de mensagem em cima. Recortar e responsabilidade
 * do protocolo; exibir nao e.
 *
 * Sao dois metodos porque o host manda dois formatos diferentes, reconhecidos por caminhos
 * diferentes de checkSystemMessage: as linhas do IPL e as linhas do console propriamente
 * dito. O contrato preserva essa distincao em vez de esconde-la atras de um flag.
 */
// -----------------------------------------------------------------------------------//
public interface ConsoleLines
// -----------------------------------------------------------------------------------//
{
  // As linhas do IPL, reconhecidas pelo codigo de mensagem no inicio de cada uma.
  void addLines1 (String[] lines, int firstLine, int lastLine);

  // As linhas do console, reconhecidas pelo horario.
  void addLines2 (String[] lines, int firstLine, int lastLine);
}
