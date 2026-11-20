package com.bytezone.dm3270.architecture;

import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

// Regras de arquitetura: violacoes legadas ficam congeladas em test/archunit-baseline.
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

  private static final String[] UI_PACKAGES =
      { "com.bytezone.dm3270.application..", "com.bytezone.dm3270.assistant..",
        "com.bytezone.dm3270.console..", "com.bytezone.dm3270.display..",
        "com.bytezone.dm3270.plugins..", "com.bytezone.reporter.application.." };

  private static final String[] SCREEN_MODEL = { "com.bytezone.dm3270.screen.." };

  private static final String[] DATASET_DOMAIN = { "com.bytezone.dm3270.datasets.." };

  private static final String[] SESSION = { "com.bytezone.dm3270.session.." };

  private static final String[] STREAMS = { "com.bytezone.dm3270.streams.." };

  private static final String[] PROTOCOL_PACKAGES =
      { "com.bytezone.dm3270.buffers..", "com.bytezone.dm3270.commands..",
        "com.bytezone.dm3270.orders..", "com.bytezone.dm3270.telnet..",
        "com.bytezone.dm3270.extended..", "com.bytezone.dm3270.structuredfields..",
        "com.bytezone.dm3270.replyfield..", "com.bytezone.dm3270.attributes.." };

  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule protocolDoesNotKnowJavaFx =                                     //
      noClasses ().that ().resideInAnyPackage (PROTOCOL_PACKAGES)                       //
          .should ().dependOnClassesThat ().resideInAnyPackage ("javafx..")             //
          .because ("a pilha de protocolo tem de rodar headless");

  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule protocolDoesNotDependOnDisplay =                                //
      noClasses ().that ().resideInAnyPackage (PROTOCOL_PACKAGES)                       //
          .should ().dependOnClassesThat ()                                             //
          .resideInAnyPackage ("com.bytezone.dm3270.display..")                         //
          .because ("o protocolo fala com uma abstracao de tela, nao com o widget");

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

  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule displayDoesNotDependOnDatabase =                                //
      noClasses ().that ().resideInAnyPackage ("com.bytezone.dm3270.display..")         //
          .should ().dependOnClassesThat ()                                             //
          .resideInAnyPackage ("com.bytezone.dm3270.database..")                        //
          .because ("a persistencia entra por uma porta injetada, nao construida aqui");

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

  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule sessionDoesNotKnowJavaFx =                                      //
      noClasses ().that ().resideInAnyPackage (SESSION)                                 //
          .should ().dependOnClassesThat ().resideInAnyPackage ("javafx..")             //
          .because ("a conversa gravada e dominio, e a linha de tabela mora em application");

  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule streamsDoesNotKnowJavaFx =                                      //
      noClasses ().that ().resideInAnyPackage (STREAMS)                                 //
          .should ().dependOnClassesThat ().resideInAnyPackage ("javafx..")             //
          .because ("quem le bytes de um socket nao decide em que thread a tela desenha");

  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule streamsDoesNotDependOnApplication =                             //
      noClasses ().that ().resideInAnyPackage (STREAMS)                                 //
          .should ().dependOnClassesThat ()                                             //
          .resideInAnyPackage ("com.bytezone.dm3270.application..")                     //
          .because ("quem le bytes de um socket nao conhece o composition root");

  // Exclui telnet porque TelnetCommand e subcomandos dependem legitimamente de TelnetState.
  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule buffersAndCommandsDoNotKnowStreams =                            //
      noClasses ().that ()                                                              //
          .resideInAnyPackage ("com.bytezone.dm3270.buffers..",                         //
                               "com.bytezone.dm3270.commands..")                        //
          .should ().dependOnClassesThat ().resideInAnyPackage (STREAMS)                //
          .because ("o bloco de bytes e o comando 3270 nao conhecem a sessao telnet");

  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule displayDoesNotDependOnApplication =                              //
      noClasses ().that ().resideInAnyPackage ("com.bytezone.dm3270.display..")         //
          .should ().dependOnClassesThat ()                                             //
          .resideInAnyPackage ("com.bytezone.dm3270.application..")                     //
          .because ("a composicao da aplicacao pertence ao composition root");

  // Nomeia as tres classes em vez de prefixo "Launch*" porque Launcher usa Application.launch.
  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule theLaunchDecisionDoesNotKnowJavaFx =                            //
      noClasses ().that ()                                                              //
          .haveSimpleName ("LaunchCoordinator")                                       //
          .or ().haveSimpleName ("LaunchTarget")                                      //
          .or ().haveSimpleName ("LaunchRequest")                                     //
          .should ().dependOnClassesThat ().resideInAnyPackage ("javafx..")             //
          .because ("a decisao do lancamento tem de rodar sem toolkit grafico");

  // ---------------------------------------------------------------------------------//
  @ArchTest
  static final ArchRule uiIsTheOnlyPlaceThatKnowsJavaFx = FreezingArchRule.freeze (     //
      noClasses ().that ().resideOutsideOfPackages (UI_PACKAGES)                        //
          .should ().dependOnClassesThat ().resideInAnyPackage ("javafx..")             //
          .because ("JavaFX so pode vazar para os pacotes de interface"));

  // Usa contagem para evitar baseline gigante do beFreeOfCycles; exige baixar se reduzir.
  // Os 9 restantes: 5 inerentes ao 3270, 3 residuais (filetransfer/telnet) e record<->text.
  private static final int MAX_MUTUAL_CYCLES = 9;

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
