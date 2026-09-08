package com.bytezone.dm3270.streams;

import com.bytezone.dm3270.commands.Command;

/*
 * Para onde o servidor de mainframe entrega um comando que acabou de chegar do cliente.
 *
 * Esta interface morava em application, e era a UNICA coisa viva que streams nomeava de la -
 * o outro import, o de Console em SpyServer, estava morto havia tempo. Por causa dela o
 * pacote que le bytes de um socket apontava de volta para o composition root, e o ciclo
 * mutuo application <-> streams sobreviveu a tres ondas com o rotulo "depende do composition
 * root". Nao dependia: dependia de a porta estar declarada no lugar certo.
 *
 * Quem a consome e o MainframeServer, que guarda um Mainframe num campo e chama
 * receiveCommand dentro do Executor da interface. Pela regra desta refatoracao a porta se
 * declara no pacote que CONSOME - e isso, e so isso, que inverte a dependencia -, entao ela
 * mora aqui e application.MainframeStage a implementa. E o mesmo corte de SessionDisplay na
 * onda 3, e o mesmo de ReportContext no passo 4.
 *
 * O nome nao mudou. MainframeStage e uma janela, mas o que o servidor precisa dela e uma
 * coisa so: receber o comando montado. Chamar a porta de Mainframe descreve o papel, e
 * setStage (Mainframe) continua legivel do lado de quem monta o grafo.
 */
// -----------------------------------------------------------------------------------//
public interface Mainframe
// -----------------------------------------------------------------------------------//
{
  public void receiveCommand (Command command);
}
