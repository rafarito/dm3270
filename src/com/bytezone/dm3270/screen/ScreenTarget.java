package com.bytezone.dm3270.screen;

import java.util.Optional;

import com.bytezone.dm3270.commands.WriteControlCharacter;
import com.bytezone.dm3270.filetransfer.TransferManager;

/*
 * O que a pilha de protocolo pode pedir a uma tela: o parametro de todo Buffer.process.
 * Implementam a Screen e, nos testes, o HeadlessScreenTarget.
 *
 * O CONTRATO E A SOMA DE PAPEIS. Cada grupo de metodos com um consumidor proprio e uma
 * interface em screen, e esta os estende todos:
 *
 *   DisplayScreen       o buffer de tela - Pen, posicoes, dimensoes. Todas as orders.
 *   KeyboardState       isKeyboardLocked (). Tambem e a porta das abas do assistant.
 *   WriteControlTarget  os seis sinais do WCC. So WriteControlCharacter.
 *   HostReplyTarget     ler o buffer, os campos modificados e o Query; o modo de resposta.
 *                       ReadCommand, SetReplyModeSF, ReadPartitionQuery.
 *   TsoCommandTarget    o campo de comando do TSO. So AIDCommand.
 *   ApplicationHooks    gravacao, plugins automaticos, mensagens de sistema. So
 *                       WriteCommand - e o que o ciclo C2 do plano tira de la.
 *   ConsoleSwitch       setIsConsole (). So SystemMessage.
 *
 * O que fica declarado aqui e o nucleo que os dois comandos grandes, WriteCommand e
 * AIDCommand, usam juntos e cruzado: travar o teclado, montar e consultar os campos, o
 * cursor, trocar de tela e redesenhar. Parti-lo daria papeis com os mesmos dois consumidores,
 * que nao dizem nada a mais do que este tipo. E aqui tambem fica getTransferManager (), com o
 * TODO que registra a aresta que ele sustenta.
 *
 * O LIMITE, que e de Java e nao do projeto: um override de process nao pode estreitar o tipo
 * do parametro, entao toda implementacao de Buffer.process continua vendo o contrato
 * inteiro. Os papeis servem a quem NAO e override - WriteControlCharacter.process e o campo
 * do SystemMessage pedem so o seu -, a documentacao de quem usa o que, e a quem for tirar um
 * grupo daqui. O ScreenTargetContractTest congela a soma.
 *
 * Duas travessias foram estreitadas quando este contrato nasceu, porque devolver o objeto
 * inteiro so disfarcaria o acoplamento:
 *
 *   getFieldManager () -> getFieldCount () e getFieldAt (int)
 *       O protocolo usava o FieldManager apenas para contar campos e achar um campo por
 *       posicao. FieldManager importa database e plugins e sobe uma thread SQLite no
 *       construtor - nada disso tem a ver com processar um comando 3270.
 *
 *   getPluginsStage () -> processPluginAuto ()
 *       WriteCommand pedia a Stage do JavaFX so para chamar um metodo dela. Agora pede o
 *       resultado (ver ApplicationHooks).
 */
// -----------------------------------------------------------------------------------//
public interface ScreenTarget extends DisplayScreen, KeyboardState, WriteControlTarget,
    HostReplyTarget, TsoCommandTarget, ApplicationHooks, ConsoleSwitch
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  // Teclado
  // ---------------------------------------------------------------------------------//

  void lockKeyboard (String keyName);

  // ---------------------------------------------------------------------------------//
  // Campos da tela
  // ---------------------------------------------------------------------------------//

  int getFieldCount ();

  Optional<Field> getFieldAt (int position);

  void buildFields (WriteControlCharacter wcc);

  void eraseAllUnprotected ();

  Cursor getScreenCursor ();

  // ---------------------------------------------------------------------------------//
  // Ciclo de vida da tela
  // ---------------------------------------------------------------------------------//

  void setCurrentScreen (ScreenOption value);

  void draw ();

  // ---------------------------------------------------------------------------------//
  // Transferencia de arquivos
  // ---------------------------------------------------------------------------------//

  /*
   * TODO: esta e a ultima aresta do modelo de tela para fora do protocolo, e sustenta UM dos
   * tres ciclos mutuos acidentais que restam - filetransfer <-> screen.
   * FileTransferOutboundSF.process pede o gerenciador a tela porque quem o possui e a Screen,
   * que possui tudo, e porque quem despacha o comando montado a partir de bytes so tem a tela
   * na mao.
   *
   * ATE O PASSO 10 ESTE TODO DIZIA "dois ciclos", E ESTAVA ERRADO - o mesmo erro estava no
   * LayeringTest e no documento de handoff. A medicao aresta por aresta: commands <-> filetransfer
   * e sustentado por quatro construcoes que NAO TOCAM o ScreenTarget - as duas fabricas de
   * structured field em WriteStructuredFieldCommand e ReadStructuredFieldCommand, as tres
   * "new ReadStructuredFieldCommand (buffer)" dentro do proprio FileTransferOutboundSF, e a
   * leitura da constante AIDCommand.AID_ENTER no TransferMenu. Tirar getTransferManager () daqui
   * derruba um ciclo, nao dois.
   *
   * A etiqueta dizia "onda 3" e envelheceu: a onda 3 nao o tirou, e o passo 6 tambem nao.
   *
   * TRES SAIDAS JA FORAM MEDIDAS E RECUSADAS, e ficam registradas para ninguem reavaliar:
   *
   *   1. interface-marcador com cast - troca a violacao de camada por algo pior;
   *   2. Buffer.process recebendo um contexto de sessao - refactor de assinatura em 25
   *      implementacoes, com o golden master no caminho;
   *   3. uma porta estreita declarada AQUI, que e o padrao desta refatoracao e foi o que
   *      derrubou o ciclo do reporter no passo 4. Medida no passo 6, e NAO SERVE: os quatro
   *      metodos que FileTransferOutboundSF chama - openTransfer, getTransfer, process e
   *      closeTransfer - sao package-private, devolvem Optional<Transfer> e recebem
   *      FileTransferOutboundSF. A porta nomearia filetransfer de qualquer jeito.
   *
   * Sai quando o composition root assumir a fiacao e injetar o gerenciador em quem precisa.
   */
  TransferManager getTransferManager ();
}
