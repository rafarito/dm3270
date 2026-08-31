package com.bytezone.dm3270.session;

/*
 * Avisa que a sessao identificou um dos dois lados da conversa.
 *
 * A Session possuia um Label do JavaFX e o entregava pronto, por getHeaderLabel (), para que o
 * ReplayStage e o SpyPane o estilizassem e o pendurassem no alto da janela. Um widget dentro
 * do acumulado de uma conversa - e um Platform.runLater junto, para escrever nele a partir da
 * thread do socket.
 *
 * O METODO NAO TEM ARGUMENTO, e isso e deliberado. O texto era montado DENTRO do runLater,
 * lendo getServerName () e getClientName () no momento da execucao e nao no do agendamento.
 * Passar a String pronta aqui mudaria o quadro intermediario quando os dois lados fossem
 * reconhecidos entre duas pulsacoes da interface: o primeiro aviso pintaria o texto antigo.
 * Sem argumento, quem ouve continua lendo getHeaderText () la dentro, como antes.
 *
 * E ADDHEADERLISTENER DISPARA NA HORA se a sessao ja tiver nome. E o que faz o modo Replay -
 * onde a sessao inteira e carregada antes de a janela existir - mostrar o cabecalho
 * preenchido, sem que o modo Spy passe a nascer escrito "Unknown : Unknown", que e o que
 * aconteceria se a janela simplesmente lesse o texto na construcao.
 */
// -----------------------------------------------------------------------------------//
public interface SessionHeaderListener
// -----------------------------------------------------------------------------------//
{
  void headerChanged ();
}
