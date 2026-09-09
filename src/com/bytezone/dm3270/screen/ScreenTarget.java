package com.bytezone.dm3270.screen;

import java.util.Optional;

import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.commands.ReadStructuredFieldCommand;
import com.bytezone.dm3270.commands.SystemMessage;
import com.bytezone.dm3270.commands.WriteControlCharacter;
import com.bytezone.dm3270.filetransfer.TransferManager;

/*
 * O que a pilha de protocolo pode pedir a uma tela.
 *
 * Antes, Buffer.process recebia a classe concreta Screen - 1.010 linhas que estendem
 * javafx.scene.canvas.Canvas, constroem o grafo de objetos da aplicacao inteira no
 * construtor e sao ao mesmo tempo widget, modelo, gerenciador de janelas e hub de eventos.
 * As 25 implementacoes de process espalhadas por commands, structuredfields, telnet,
 * extended e filetransfer dependiam de tudo isso para mexer em algumas dezenas de bytes.
 *
 * Este contrato e a fronteira. Estende DisplayScreen - a abstracao do buffer de tela, que
 * ja existia no projeto e nao era usada aqui - e acrescenta as operacoes que o protocolo
 * realmente executa. O inventario foi levantado do codigo, nao imaginado: commands usa 24
 * membros, structuredfields usa um (setReplyMode), filetransfer usa um
 * (getTransferManager), e telnet, extended e replyfield nao usam nenhum.
 *
 * Duas travessias foram estreitadas de proposito, porque devolver o objeto inteiro so
 * disfarcaria o acoplamento:
 *
 *   getFieldManager () -> getFieldCount () e getFieldAt (int)
 *       O protocolo usava o FieldManager apenas para contar campos e achar um campo por
 *       posicao. FieldManager, por sua vez, importa database e plugins e sobe uma thread
 *       SQLite no construtor - nada disso tem a ver com processar um comando 3270.
 *
 *   getPluginsStage () -> processPluginAuto ()
 *       WriteCommand pedia a Stage do JavaFX so para chamar um metodo dela. Agora pede o
 *       resultado.
 *
 * SOBRE O TAMANHO: 24 membros e uma interface gorda, e nao ha ISP nenhum em finge-la
 * pequena. Quebrar em interfaces de papel menores nao reduziria acoplamento algum aqui,
 * porque Java nao permite estreitar o tipo do parametro ao sobrescrever um metodo - toda
 * implementacao de process continuaria vendo o contrato inteiro. O ganho real desta etapa
 * esta em OUTRO lugar: nenhuma classe de protocolo conhece mais Screen, FieldManager ou
 * PluginsStage, e passa a ser possivel escrever um dublê de tela para testar comandos sem
 * toolkit grafico. A decomposicao de Screen, que e o que de fato encolhe este contrato,
 * vem depois - quando cada responsabilidade tiver um dono, os grupos abaixo viram tipos.
 */
// -----------------------------------------------------------------------------------//
public interface ScreenTarget extends DisplayScreen, KeyboardState
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  // Teclado e estado de entrada
  // ---------------------------------------------------------------------------------//

  void lockKeyboard (String keyName);

  void restoreKeyboard ();

  void resetInsertMode ();

  // ---------------------------------------------------------------------------------//
  // Campos da tela
  // ---------------------------------------------------------------------------------//

  int getFieldCount ();

  Optional<Field> getFieldAt (int position);

  void buildFields (WriteControlCharacter wcc);

  void eraseAllUnprotected ();

  void resetModified ();

  Cursor getScreenCursor ();

  // ---------------------------------------------------------------------------------//
  // Montagem da resposta ao host
  // ---------------------------------------------------------------------------------//

  AIDCommand readBuffer ();

  AIDCommand readModifiedFields (byte type);

  void setReplyMode (byte replyMode, byte[] replyTypes);

  void checkRecording ();

  // ---------------------------------------------------------------------------------//
  // Comandos TSO
  // ---------------------------------------------------------------------------------//

  boolean isTSOCommandScreen ();

  Field getTSOCommandField ();

  void addTSOCommand (String command);

  // ---------------------------------------------------------------------------------//
  // Sinais do WriteControlCharacter
  // ---------------------------------------------------------------------------------//

  void soundAlarm ();

  void startPrinter ();

  void resetPartition ();

  // ---------------------------------------------------------------------------------//
  // Ciclo de vida da tela
  // ---------------------------------------------------------------------------------//

  void setCurrentScreen (ScreenOption value);

  void draw ();

  void setIsConsole ();

  // ---------------------------------------------------------------------------------//
  // Colaboradores que o protocolo precisa alcancar
  // ---------------------------------------------------------------------------------//

  SystemMessage getSystemMessage ();

  /*
   * Monta a resposta a um Read Partition (Query). Substitui getTelnetState (): o protocolo
   * pedia o estado da negociacao telnet apenas para construir este comando a partir dele,
   * e era o unico ponto em que o modelo de tela precisava nomear o pacote streams.
   */
  ReadStructuredFieldCommand buildQueryReply ();

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

  /*
   * Roda os plugins automaticos e devolve a resposta que eles produziram, se produziram.
   * Substitui getPluginsStage (): o protocolo quer o resultado, nao a janela.
   */
  AIDCommand processPluginAuto ();
}
