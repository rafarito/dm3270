package com.bytezone.dm3270.architecture;

import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;


/*
 * As regras de arquitetura da refatoracao, como teste.
 *
 * O diagnostico que motivou este trabalho apontou tres problemas estruturais: a camada de
 * protocolo depende da GUI, nao existe camada nenhuma (25 ciclos mutuos entre pacotes) e as
 * classes mais problematicas sao as que nao tem teste. As regras abaixo descrevem o destino.
 *
 * As regras de dependencia comecam CONGELADAS. O FreezingArchRule grava as violacoes atuais
 * em test/archunit-baseline e passa a cobrar apenas o que for novo: hoje nada quebra, mas
 * nenhuma violacao inedita entra sem quebrar a build. Conforme cada onda da refatoracao
 * elimina violacoes, o baseline encolhe sozinho - o arquivo versionado e o placar do
 * progresso, e o diff dele conta o que cada commit realmente destravou.
 *
 * Quando uma regra chegar a zero violacao, troque FreezingArchRule.freeze (regra) pela
 * propria regra: dali em diante ela passa a valer integralmente.
 *
 * A contagem de ciclos, no fim do arquivo, e a excecao: e um numero, nao um baseline
 * congelado, pelos motivos explicados la.
 */
// -----------------------------------------------------------------------------------//
@AnalyzeClasses (packages = "com.bytezone",
    importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringTest
// -----------------------------------------------------------------------------------//
{
  static
  {
    ArchUnitStore.configure ();
  }

  // Os pacotes que podem legitimamente conhecer JavaFX. Todo o resto e dominio, protocolo
  // ou persistencia, e nao deveria ter a menor ideia de que existe uma interface grafica.
  private static final String[] UI_PACKAGES =
      { "com.bytezone.dm3270.application..", "com.bytezone.dm3270.assistant..",
        "com.bytezone.dm3270.console..", "com.bytezone.dm3270.display..",
        "com.bytezone.dm3270.plugins..", "com.bytezone.reporter.application.." };

  // O modelo de tela: o buffer, os campos, o cursor, a paleta e as portas por onde o
  // protocolo fala com a tela. Saiu de dentro de display, que ficou sendo so a view.
  private static final String[] SCREEN_MODEL = { "com.bytezone.dm3270.screen.." };

  // O dominio dos datasets: o que a tela observa e o que o banco grava. Saiu de dentro de
  // database na onda 3, e database passou a depender dele.
  private static final String[] DATASET_DOMAIN = { "com.bytezone.dm3270.datasets.." };

  // A pilha que transforma bytes do socket em estrutura. E o que precisa rodar headless.
  private static final String[] PROTOCOL_PACKAGES =
      { "com.bytezone.dm3270.buffers..", "com.bytezone.dm3270.commands..",
        "com.bytezone.dm3270.orders..", "com.bytezone.dm3270.telnet..",
        "com.bytezone.dm3270.extended..", "com.bytezone.dm3270.structuredfields..",
        "com.bytezone.dm3270.replyfield..", "com.bytezone.dm3270.attributes.." };

  /*
   * A regra central do trabalho todo - e a primeira a valer integralmente.
   *
   * A causa raiz do acoplamento era Buffer.process (Screen): ela obrigava 25 implementacoes
   * espalhadas pelo protocolo a importar JavaFX. Comecou em 58 violacoes; a onda de
   * desacoplamento levou a 31, e tres commits fecharam a conta - o item de menu morto do
   * SystemMessage, o dialogo de PROFILE que morava em commands e a criacao do ConsoleLog.
   *
   * NAO ESTA CONGELADA, de proposito. Chegou a zero, entao vale inteira: qualquer classe de
   * buffers, commands, orders, telnet, extended, structuredfields, replyfield ou attributes
   * que voltar a tocar em JavaFX quebra a build, sem baseline nenhum para absorver. E a
   * garantia permanente de que a pilha que transforma bytes em estrutura roda headless.
   */
  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule protocolDoesNotKnowJavaFx =                                     //
      noClasses ().that ().resideInAnyPackage (PROTOCOL_PACKAGES)                       //
          .should ().dependOnClassesThat ().resideInAnyPackage ("javafx..")             //
          .because ("a pilha de protocolo tem de rodar headless");

  /*
   * A segunda regra a valer integralmente, e a que da nome ao trabalho.
   *
   * Comecou em 163 violacoes, todas apontando para nove tipos: ScreenTarget, DisplayScreen,
   * ScreenContext, ContextManager, ScreenDimensions, Pen, Field, Cursor e ScreenOption.
   * Nenhuma apontava para Screen, FieldManager ou ScreenWatcher. Ou seja: o protocolo nunca
   * quis o widget - queria o modelo de tela, que por acidente historico morava no mesmo
   * pacote que o widget.
   *
   * Separar os dois pacotes zerou a regra de uma vez. NAO ESTA CONGELADA: display e agora
   * so a view JavaFX, e nenhuma classe de protocolo pode voltar a nomea-la.
   */
  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule protocolDoesNotDependOnDisplay =                                //
      noClasses ().that ().resideInAnyPackage (PROTOCOL_PACKAGES)                       //
          .should ().dependOnClassesThat ()                                             //
          .resideInAnyPackage ("com.bytezone.dm3270.display..")                         //
          .because ("o protocolo fala com uma abstracao de tela, nao com o widget");

  /*
   * As duas regras que sustentam a separacao. Enquanto valerem, o modelo de tela roda
   * headless e pode ser extraido para um modulo proprio no dia em que isso interessar.
   *
   * Nenhuma das duas e congelada, porque as duas nascem em zero.
   */
  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule screenModelDoesNotKnowJavaFx =                                  //
      noClasses ().that ().resideInAnyPackage (SCREEN_MODEL)                            //
          .should ().dependOnClassesThat ().resideInAnyPackage ("javafx..")             //
          .because ("o modelo de tela e a fronteira do headless");

  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule screenModelDoesNotDependOnTheView =                             //
      noClasses ().that ().resideInAnyPackage (SCREEN_MODEL)                            //
          .should ().dependOnClassesThat ()                                             //
          .resideInAnyPackage ("com.bytezone.dm3270.display..")                         //
          .because ("o modelo nao conhece o widget que o desenha");

  /*
   * A quarta regra a valer integralmente. Comecou em 46 violacoes, de duas causas bem
   * diferentes, e as duas cairam.
   *
   * A primeira era encanamento: o FieldManager - cuja responsabilidade e agrupar posicoes de
   * tela em campos - abria o arquivo do banco, subia uma thread e enfileirava um OPEN dentro
   * do proprio construtor, e o ScreenWatcher montava requests e os empurrava na mesma fila.
   * Isso acabou na fase 2: as duas recebem um DatasetStore pronto, e quem decide se existe
   * banco e o Console, que e o composition root.
   *
   * A segunda era modelagem, e sobrou em 35: mencoes a Dataset e Member, os dois DTOs de
   * acesso direto a campo. O DatabaseThread lia dataset.tracks, member.vv e mais duas dezenas
   * de campos para montar o SQL. A leitura de entao era que move-los exigiria tornar 28
   * campos publicos - trocar uma violacao de camada por uma pior de encapsulamento.
   *
   * Nao era o caso. O que os prendia ao pacote database nao era o dominio: era o
   * DatabaseThread. Dar acessores de leitura aos dois tipos, mover o binding do
   * PreparedStatement para DatasetMapper e MemberMapper e fechar TODOS os campos como private
   * deixou a mudanca de pacote trivial - e o encapsulamento ficou melhor, nao pior. Dataset,
   * Member, DatasetStore e StoreListener foram para com.bytezone.dm3270.datasets, e o pacote
   * database passou a depender do dominio em vez do contrario.
   *
   * O ganho e real e nao de placar: display alcancava, em tempo de compilacao, um pacote com
   * Thread, BlockingQueue e JDBC dentro. Hoje nomeia dois objetos de valor e duas interfaces.
   *
   * NAO ESTA CONGELADA. As duas regras seguintes protegem o dominio novo, para que ele nao
   * vire um segundo lugar onde a persistencia mora.
   */
  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule displayDoesNotDependOnDatabase =                                //
      noClasses ().that ().resideInAnyPackage ("com.bytezone.dm3270.display..")         //
          .should ().dependOnClassesThat ()                                             //
          .resideInAnyPackage ("com.bytezone.dm3270.database..")                        //
          .because ("a persistencia entra por uma porta injetada, nao construida aqui");

  /*
   * O dominio dos datasets e o que a tela observa e o que o banco grava, e nao pode conhecer
   * nenhum dos dois. Se um dia alguem precisar de um DatabaseRequest la dentro, o certo e
   * ampliar a porta DatasetStore - nao importar a fila.
   *
   * A regra nasce em zero e nunca foi congelada. java.sql.Date nao a viola: e tipo do JDK, e
   * os quatro acessores que o devolvem existem porque createdSQL e created podem divergir, o
   * que esta explicado em Dataset.
   */
  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule datasetsDomainDoesNotKnowPersistence =                          //
      noClasses ().that ().resideInAnyPackage (DATASET_DOMAIN)                          //
          .should ().dependOnClassesThat ()                                             //
          .resideInAnyPackage ("com.bytezone.dm3270.database..")                        //
          .because ("o dominio nao conhece quem o persiste");

  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule datasetsDomainDoesNotKnowJavaFx =                               //
      noClasses ().that ().resideInAnyPackage (DATASET_DOMAIN)                          //
          .should ().dependOnClassesThat ().resideInAnyPackage ("javafx..")             //
          .because ("o dominio dos datasets tem de rodar headless, como o modelo de tela");

  /*
   * A terceira regra a valer integralmente.
   *
   * Comecou em 14 violacoes, de tres causas: os eventos de teclado, que foram para o pacote
   * screen porque descrevem estado do modelo de tela; Console.Function, que virou
   * runtime.TerminalFunction porque um modo de execucao e um dado e nao a composicao da
   * aplicacao; e o ConsolePane, que a Screen guardava inteiro para chamar tres metodos.
   *
   * NAO ESTA CONGELADA. O pacote application e o composition root: ele monta o grafo de
   * objetos e por isso conhece todo mundo. Ninguem conhece ele de volta - com uma excecao
   * documentada, streams, que ainda usa Mainframe e Console em MainframeServer e SpyServer.
   */
  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule displayDoesNotDependOnApplication =                              //
      noClasses ().that ().resideInAnyPackage ("com.bytezone.dm3270.display..")         //
          .should ().dependOnClassesThat ()                                             //
          .resideInAnyPackage ("com.bytezone.dm3270.application..")                     //
          .because ("a composicao da aplicacao pertence ao composition root");

  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule uiIsTheOnlyPlaceThatKnowsJavaFx = FreezingArchRule.freeze (     //
      noClasses ().that ().resideOutsideOfPackages (UI_PACKAGES)                        //
          .should ().dependOnClassesThat ().resideInAnyPackage ("javafx..")             //
          .because ("JavaFX so pode vazar para os pacotes de interface"));

  /*
   * O placar dos ciclos.
   *
   * O diagnostico contou 25 ciclos mutuos entre pacotes - pares A -> B e B -> A - com
   * display participando de 9. Esta e a metrica que a refatoracao promete derrubar.
   *
   * Aqui a verificacao e uma contagem, e nao um FreezingArchRule, de proposito: congelar
   * beFreeOfCycles gerava um baseline de quase 7.000 linhas, porque a regra lista cada
   * dependencia de cada ciclo. Um arquivo desse tamanho nao serve de placar - o diff nao e
   * legivel e ninguem percebe o que mudou. Um numero unico e.
   *
   * Zero nao e a meta realista. commands e o modelo de tela se referenciam por natureza do
   * 3270: um ReadCommand pede a tela que produza um AIDCommand, e AIDCommand e um comando
   * de protocolo. A meta e eliminar os ciclos acidentais - display para database, plugins e
   * application - e deixar documentados os poucos inerentes a camada de protocolo.
   *
   * Ao fim de cada onda, baixe MAX_MUTUAL_CYCLES para o valor alcancado. O teste tambem
   * falha se a contagem ficar ABAIXO do limite, para forcar essa atualizacao: progresso que
   * ninguem registra e progresso que se perde na proxima regressao.
   *
   * O ponto de partida medido foi 24, e nao os 25 do diagnostico. A diferenca e de
   * metodo: o relatorio contou declaracoes de import, enquanto o ArchUnit le as
   * dependencias reais do bytecode - o que inclui tipos de campo e de assinatura que nao
   * aparecem como import, e exclui import declarado mas nao usado. As duas listas se
   * sobrepoem quase todo; a do bytecode e a que vale como placar, porque e a que descreve o
   * acoplamento que o compilador realmente impoe.
   *
   * SOBRE O 24 DE AGORA, que subiu de 23: e o unico aumento desta refatoracao, e tem uma
   * causa so, nomeada. Separar o modelo de tela da view criou um pacote novo, e tres ciclos
   * que estavam escondidos DENTRO de display passaram a ser contados duas vezes - uma pela
   * view, outra pelo modelo. Dois foram cortados antes do split, de proposito:
   *
   *   screen <-> plugins   Field.getPluginField foi para o FieldManager
   *   screen <-> streams   getTelnetState () virou buildQueryReply ()
   *
   * O terceiro nao da para cortar aqui: ScreenTarget.getTransferManager () existe porque a
   * Screen possui o gerenciador de transferencias, como possui tudo o mais, e
   * FileTransferOutboundSF o guarda num campo e despacha sobre ele - nao ha o que estreitar.
   * Sai na onda 3, quando o composition root passar a injetar o gerenciador em quem precisa.
   * Esta marcado com TODO na propria ScreenTarget.
   *
   * O acoplamento nao cresceu: o conjunto de dependencias entre tipos e o mesmo de antes do
   * split. Cresceu a contagem, porque ha um pacote a mais para conta-la.
   *
   * E ja voltou a 23: tirar Console.Function de dentro do pacote application desfez o ciclo
   * application <-> session por inteiro, porque o enum era a unica coisa que session
   * importava de la.
   *
   * Depois caiu a 20. O ConsolePane era o UNICO tipo do pacote application que assistant,
   * display, filetransfer e plugins importavam - e cada um o guardava inteiro, 432 linhas de
   * JavaFX, para usar dois ou tres metodos. Substitui-lo pelas portas AidSender e ConsoleView
   * desfez tres ciclos de uma vez: application <-> assistant, application <-> display e
   * application <-> plugins.
   *
   * Sobra application <-> streams, de MainframeServer e SpyServer, que usam Mainframe e
   * Console. E o unico caminho de volta para o composition root que ainda existe.
   *
   * E caiu a 19. O ConsoleLogStage recebia uma Screen no construtor e nao fazia nada com
   * ela - nem guardava. Era a UNICA aresta de console para display, entao apagar o parametro
   * desfez o ciclo inteiro. E o corte mais barato que esta refatoracao encontrou, e so
   * apareceu porque a medicao foi feita aresta por aresta em vez de por tamanho de classe.
   *
   * E caiu a 18. O PluginsStage guardava a Screen inteira e pedia a ela o FieldManager a
   * cada chamada, so para converter os campos no formato da API de plugins. A porta
   * plugins.PluginHost esta declarada no lado que consome - e isso que inverte a dependencia
   * - e a traducao Field -> PluginField saiu do FieldManager para plugins.PluginFields, onde
   * mora quem define o formato. plugins deixou de nomear display.
   *
   * E caiu a 17. O TelnetListener usa a tela de dois jeitos: como ScreenTarget, para entregar
   * cada comando montado ao process (), e uma vez so como widget, em close (), para escrever
   * o resumo da sessao quando o socket cai. Por causa dessa unica chamada o pacote streams
   * inteiro nomeava display. streams.SessionDisplay estende ScreenTarget e acrescenta
   * displayText (); a TerminalFunction, a outra coisa que o construtor pedia ao widget, passou
   * a vir de quem constroi. SpyServer so repassava a tela e seguiu junto.
   *
   * E caiu a 16. O ScreenWatcher produzia assistant.TableDataset - o modelo de linha da
   * TableView, feito de StringProperty - e os tres dialogos de transferencia liam duas datas
   * dele. Era a causa UNICA de filetransfer nomear assistant, e portanto do ciclo
   * assistant <-> filetransfer. Agora o ScreenWatcher produz datasets.DatasetSummary, o mesmo
   * dado sem JavaFX, e a conversao para linha de tabela acontece em assistant.TableDatasets,
   * na borda da interface.
   *
   * E caiu a 15. O ScreenWatcher, o ScreenChangeListener e o TSOCommandListener foram para o
   * pacote com.bytezone.dm3270.watch. Eram a razao inteira de filetransfer nomear display -
   * quatro classes importavam o observador para ler o dataset selecionado e o campo de comando
   * TSO -, e display precisa nomear o observador de volta, porque o FieldManager e quem o
   * constroi e quem dispara o screenChanged.
   *
   * Mover sozinho nao resolveria: o ScreenWatcher levaria o FieldManager junto e o ciclo
   * reapareceria como watch <-> display. O que corta e a porta watch.ScreenFields, sete
   * metodos declarados do lado que consome e implementados pelo FieldManager. Agora display
   * nomeia watch numa direcao so.
   *
   * Este e o passo que a Regra 5 mandava adiar ate o acoplamento cair de verdade: mover o
   * ScreenWatcher antes de a regra de banco chegar a zero teria feito displayDoesNotDependOnDatabase
   * ler zero sem que uma aresta fosse cortada. A ordem foi a inversa, e por isso as duas
   * medidas valem.
   */
  private static final int MAX_MUTUAL_CYCLES = 15;

  // ---------------------------------------------------------------------------------//
  @ArchTest
  static void mutualPackageCyclesDoNotGrow (JavaClasses classes)
  // ---------------------------------------------------------------------------------//
  {
    if (classes.size () < 100)
      throw new AssertionError (
          "esperava o projeto inteiro importado, veio " + classes.size () + " classes");

    List<String> cycles = PackageCycles.mutualCycles (classes);

    if (cycles.size () > MAX_MUTUAL_CYCLES)
      throw new AssertionError (String.format (
          "os ciclos mutuos entre pacotes subiram de %d para %d.%nCiclos:%n  %s",
          MAX_MUTUAL_CYCLES, cycles.size (), String.join ("\n  ", cycles)));

    if (cycles.size () < MAX_MUTUAL_CYCLES)
      throw new AssertionError (String.format (
          "os ciclos mutuos cairam de %d para %d - baixe MAX_MUTUAL_CYCLES para %d "
              + "e registre o ganho no commit.%nCiclos restantes:%n  %s",
          MAX_MUTUAL_CYCLES, cycles.size (), cycles.size (), String.join ("\n  ", cycles)));
  }
}
