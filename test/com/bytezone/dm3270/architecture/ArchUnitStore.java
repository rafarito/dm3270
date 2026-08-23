package com.bytezone.dm3270.architecture;

import com.tngtech.archunit.ArchConfiguration;

/*
 * Aponta o baseline congelado do ArchUnit para test/archunit-baseline.
 *
 * Por padrao o FreezingArchRule grava num diretorio archunit_store solto na raiz do
 * projeto, que ninguem versiona por acidente. O baseline precisa ser versionado: e ele que
 * distingue "violacao que ja existia" de "violacao nova", e o diff dele e o placar da
 * refatoracao.
 *
 * A configuracao e feita por codigo, e nao por um archunit.properties, porque este projeto
 * nao declara <testResources> no pom - o diretorio test so publica as classes compiladas.
 */
// -----------------------------------------------------------------------------------//
final class ArchUnitStore
// -----------------------------------------------------------------------------------//
{
  private static final String STORE_PATH = "test/archunit-baseline";

  // ---------------------------------------------------------------------------------//
  private ArchUnitStore ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // ---------------------------------------------------------------------------------//
  static void configure ()
  // ---------------------------------------------------------------------------------//
  {
    ArchConfiguration configuration = ArchConfiguration.get ();

    configuration.setProperty ("freeze.store.default.path", STORE_PATH);
    configuration.setProperty ("freeze.store.default.allowStoreCreation", "true");
    configuration.setProperty ("freeze.refreeze", refreeze ());
  }

  /*
   * Por padrao o baseline so encolhe: violacao resolvida sai sozinha, violacao nova quebra a
   * build.
   *
   * Ha um caso legitimo em que uma violacao ANTIGA aparece como nova, e ele acontece
   * exatamente durante uma refatoracao: o FreezingArchRule identifica cada violacao pelo
   * texto, e o texto inclui a assinatura completa do metodo. Trocar o tipo de um parametro -
   * de javafx.scene.paint.Color para TerminalColor, por exemplo - reescreve a descricao da
   * mesma dependencia, que entao nao casa mais com a entrada guardada.
   *
   * Para esses casos, e SOMENTE para eles:
   *
   *     mvn test -Darchunit.freeze.refreeze=true -Dtest=LayeringTest
   *
   * A absorcao deixa de ser automatica e passa a ser um ato deliberado, com o diff do
   * baseline visivel no commit. Se o diff mostrar mais do que a reescrita esperada, e porque
   * havia regressao de verdade escondida ali.
   */
  // ---------------------------------------------------------------------------------//
  private static String refreeze ()
  // ---------------------------------------------------------------------------------//
  {
    return Boolean.getBoolean ("archunit.freeze.refreeze") ? "true" : "false";
  }
}
