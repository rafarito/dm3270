package com.bytezone.dm3270.runtime;

/*
 * Em que modo a aplicacao foi aberta.
 *
 * Era um enum aninhado dentro de Console - a classe Application do JavaFX - e por isso
 * display, session e streams importavam o pacote application so para saber em que modo
 * estavam. Nao ha nada de composicao da aplicacao num modo de execucao: e um dado, escolhido
 * no OptionStage e carregado adiante.
 *
 * Aqui, num pacote sem dependencia nenhuma, ele pode ser lido por qualquer camada sem
 * arrastar a janela principal atras. Tirar o enum de application desfez o ciclo mutuo
 * application <-> session por inteiro: era a unica coisa que session importava de la.
 *
 * O nome ganhou o prefixo por um motivo pratico: havia dois enums chamados Function no
 * projeto, este e o TN3270ExtendedSubcommand.Function - que e outra coisa completamente, as
 * funcoes negociadas do TN3270E (BIND_IMAGE, RESPONSES, SYSREQ). Ler codigo com os dois no
 * mesmo arquivo era ambiguo.
 *
 * Os nomes das constantes nao mudaram, e nada as persiste: a preferencia "Function" gravada
 * pelo Console guarda a string do OptionStage, nao o enum.
 */
// -----------------------------------------------------------------------------------//
public enum TerminalFunction
// -----------------------------------------------------------------------------------//
{
  SPY, REPLAY, TERMINAL, TEST
}
