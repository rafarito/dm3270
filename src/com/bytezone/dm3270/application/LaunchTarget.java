package com.bytezone.dm3270.application;

import java.nio.file.Path;
import java.util.Optional;

import com.bytezone.dm3270.runtime.TerminalFunction;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.session.Session;
import com.bytezone.dm3270.utilities.Site;

/*
 * O que o lancamento precisa MANDAR FAZER depois de decidir o que o usuario pediu.
 *
 * Ate este commit nao havia fronteira nenhuma: o startSelectedFunction era um switch de 92
 * linhas em que a DECISAO - qual funcao, o que falta preencher, que mensagem de erro dar, em
 * que ordem - e a CONSTRUCAO - Screen, ConsolePane, SpyPane, Scene, WindowSaver, ReplayStage,
 * MainframeStage - estavam trancadas juntas no mesmo metodo. A consequencia esta medida no
 * cabecalho do ConsoleLaunchErrorsTest: os quatro ramos que RECUSAM o lancamento tinham teste,
 * e os quatro caminhos felizes nao tinham nenhum, porque construir de verdade abre arquivo
 * SQLite no diretorio corrente, mostra janela e abre socket para um host.
 *
 * A porta e declarada aqui, no pacote que a consome - o padrao desta refatoracao, escrito no
 * ConsoleKeyTarget. O que ela garante e uma linha so, e e ela que importa: NENHUM metodo
 * abaixo nomeia Screen, ConsolePane, SpyPane, ReplayStage, MainframeStage, WindowSaver, Scene
 * ou Stage. Com isso a decisao passa a rodar sem toolkit grafico, e os quatro caminhos felizes
 * ficam observaveis por um gravador.
 *
 * O QUE ISSO NAO ENTREGA, e e honesto dizer antes: os caminhos felizes ficam observaveis como
 * DECISAO e como ORDEM, nao como EFEITO. Ninguem consegue ainda afirmar que o ConsolePane
 * nasceu, que a janela apareceu ou que o socket abriu - o bloqueio para isso sao duas
 * instrucoes, "new ConsolePane (screen, ...)" e "new SpyPane (screen, ...)", que recebem a
 * Screen concreta. Reduzir o bloqueio a essas duas linhas e o que este passo deixa de heranca.
 *
 * SOBRE O TAMANHO: doze metodos e uma interface gorda, e nao ha ISP nenhum em finge-la
 * pequena. Sao exatamente as doze coisas que os quatro ramos fazem. Quebra-la em papeis
 * menores nao cortaria acoplamento algum, porque o coordenador continuaria precisando dos
 * doze. O ScreenTarget, ao contrario, e a soma de papeis porque la cada grupo de metodos tem
 * um consumidor diferente; aqui o consumidor e um so.
 *
 * QUEM A IMPLEMENTA, e por que nao e o Console diretamente. Metodo de interface e implicitamente
 * publico, entao "Console implements LaunchTarget" tornaria estes doze metodos PUBLICOS numa
 * classe publica - passivo de doze, contra os seis membros de pacote que o passo 11 abriu e
 * contabilizou. O implementador e uma classe interna NAO-ESTATICA do Console, que alcanca os
 * campos e metodos privados dele sem alargar nada. Em Java 21 isso usa nestmates: o javac nao
 * emite ponte sintetica, e a prova por javap continua legivel.
 *
 * AS TRES COISAS QUE A PORTA PRESERVA DE PROPOSITO, e cada uma tem caso de teste:
 *
 *   1. as dimensoes alternativas sao ESTADO do implementador, e nao valor passado por
 *      lancamento. Por isso applyModel e useAlternateScreenDimensions nao devolvem nada, e
 *      showConsole/showSpy nao as recebem. Congelar isso num valor corrigiria o item 1 do
 *      BACKLOG-DEFEITOS.md - um modelo invalido herda a dimensao do lancamento anterior -, e
 *      a Regra 1 diz que defeito achado vai para o backlog, nao para o codigo;
 *
 *   2. showConsole e connectConsole sao SEPARADOS porque so o ramo TERMINAL conecta. Fundi-los
 *      faria o replay abrir socket;
 *
 *   3. hideOptions e launchRequest sao dois metodos, nessa ordem, porque o Console esconde a
 *      janela ANTES de ler os widgets. Nenhuma asserticao de hoje pega essa inversao, e a
 *      Regra 1 a protege do mesmo jeito.
 *
 * E UMA QUE ELA CORRIGE DE DESENHO, sem mudar comportamento: showMainframe recebe a porta TCP.
 * O MAINFRAME_EMULATOR_PORT e o DEFAULT_MAINFRAME sao um par acoplado - a porta da constante E
 * a porta do emulador -, e reparti-los entre duas classes seria defeito latente. Os dois andam
 * juntos, e a porta carrega o numero.
 */
// -----------------------------------------------------------------------------------//
interface LaunchTarget
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  // A janela de abertura, e o alerta de erro
  // ---------------------------------------------------------------------------------//

  void hideOptions ();

  void showOptions ();

  LaunchRequest launchRequest ();

  /*
   * Devolve false quando o usuario dispensa o alerta sem confirmar. A janela de opcoes so
   * reabre quando a resposta e true, e esse curto-circuito e comportamento observavel - ha
   * caso dedicado no ConsoleLaunchErrorsTest.
   */
  boolean alert (String message);

  // ---------------------------------------------------------------------------------//
  // A consulta tardia que so o ramo do replay faz
  // ---------------------------------------------------------------------------------//

  /*
   * O nome do servidor so aparece depois de a sessao gravada ser carregada, entao a consulta e
   * tardia por natureza e nao cabe no LaunchRequest. Esta escrito assim no proprio
   * LaunchRequest.
   */
  Optional<Site> findServerSite (String siteName);

  // ---------------------------------------------------------------------------------//
  // O estado que sobrevive entre lancamentos na mesma JVM
  // ---------------------------------------------------------------------------------//

  /*
   * Negocia o modelo de terminal do site e guarda as dimensoes alternativas correspondentes.
   * Preserva o item 1 do backlog por inteiro, inclusive a ordem: configura primeiro, reclama
   * depois.
   */
  void applyModel (Site serverSite);

  void useAlternateScreenDimensions (ScreenDimensions screenDimensions);

  // ---------------------------------------------------------------------------------//
  // A construcao propriamente dita
  // ---------------------------------------------------------------------------------//

  /*
   * Monta a tela e o painel de console e mostra a janela principal. O site pode ser NULO: e o
   * ramo do replay que nao encontrou o servidor, e e o que decide se ha banco de dados.
   */
  void showConsole (TerminalFunction function, Site serverSite);

  void connectConsole ();

  /*
   * Precisa vir depois de showConsole no ramo do replay: a janela de replay recebe a tela que
   * o showConsole acabou de construir.
   */
  void showReplay (Session session, Path path);

  /*
   * A tela do modo espiao nasce SEM site - os dois ramos que chamam isto passam null para o
   * construtor da tela, e portanto nao abrem banco de dados. Os dois Site aqui sao as duas
   * pontas que o painel mostra.
   */
  void showSpy (TerminalFunction function, Site serverSite, Site clientSite);

  void showMainframe (int port);
}
