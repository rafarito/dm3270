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

  // A pilha que transforma bytes do socket em estrutura. E o que precisa rodar headless.
  private static final String[] PROTOCOL_PACKAGES =
      { "com.bytezone.dm3270.buffers..", "com.bytezone.dm3270.commands..",
        "com.bytezone.dm3270.orders..", "com.bytezone.dm3270.telnet..",
        "com.bytezone.dm3270.extended..", "com.bytezone.dm3270.structuredfields..",
        "com.bytezone.dm3270.replyfield..", "com.bytezone.dm3270.attributes.." };

  /*
   * A regra central do trabalho todo.
   *
   * A causa raiz do acoplamento e Buffer.process (Screen): ela obriga 25 implementacoes
   * espalhadas pelo protocolo a importar JavaFX. Enquanto esta regra tiver violacao, nao da
   * para testar um comando 3270 sem um toolkit grafico ativo.
   */
  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule protocolDoesNotKnowJavaFx = FreezingArchRule.freeze (           //
      noClasses ().that ().resideInAnyPackage (PROTOCOL_PACKAGES)                       //
          .should ().dependOnClassesThat ().resideInAnyPackage ("javafx..")             //
          .because ("a pilha de protocolo tem de rodar headless"));

  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule protocolDoesNotDependOnDisplay = FreezingArchRule.freeze (      //
      noClasses ().that ().resideInAnyPackage (PROTOCOL_PACKAGES)                       //
          .should ().dependOnClassesThat ()                                             //
          .resideInAnyPackage ("com.bytezone.dm3270.display..")                         //
          .because ("o protocolo fala com uma abstracao de tela, nao com o widget"));

  /*
   * FieldManager, cuja responsabilidade e agrupar posicoes de tela em campos, cria o
   * arquivo do banco, sobe uma thread e abre uma conexao SQLite. E a origem desta aresta.
   */
  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule displayDoesNotDependOnDatabase = FreezingArchRule.freeze (      //
      noClasses ().that ().resideInAnyPackage ("com.bytezone.dm3270.display..")         //
          .should ().dependOnClassesThat ()                                             //
          .resideInAnyPackage ("com.bytezone.dm3270.database..")                        //
          .because ("a persistencia entra por uma porta injetada, nao construida aqui"));

  /*
   * Screen guarda um PluginsStage e importa Console.Function e ConsolePane - ou seja, a
   * tela conhece a janela que a contem e o gerenciador de plugins que ela dispara.
   */
  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule displayDoesNotDependOnApplication = FreezingArchRule.freeze (   //
      noClasses ().that ().resideInAnyPackage ("com.bytezone.dm3270.display..")         //
          .should ().dependOnClassesThat ()                                             //
          .resideInAnyPackage ("com.bytezone.dm3270.application..")                     //
          .because ("a composicao da aplicacao pertence ao composition root"));

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
   */
  private static final int MAX_MUTUAL_CYCLES = 23;

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
